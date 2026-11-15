import io
import time
from collections.abc import AsyncIterator
from pathlib import Path
from typing import Any

import httpx
import pytest
import pytest_asyncio
from joserfc import jwt
from joserfc.jwk import KeySet, RSAKey
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas
from sqlalchemy.ext.asyncio import create_async_engine

from ai_service.container import Services, assemble
from ai_service.core import security
from ai_service.core.config import Settings
from ai_service.db.models import Base
from ai_service.main import create_app
from ai_service.providers.llm import FakeLlm
from ai_service.providers.vectorstore import ChromaVectorStore
from tests.fakes import CountingEmbedder, FakeDrive, FakeWeb

ISSUER = "http://localhost:8180/realms/baracel"
_KEY = RSAKey.generate_key(2048, parameters={"kid": "test-key"})
_OTHER_KEY = RSAKey.generate_key(2048, parameters={"kid": "other-key"})


def make_token(
    scopes: str = "ai:invoke", *, aud: str = "ai-service", exp_in: int = 600, key: Any = _KEY, issuer: str = ISSUER
) -> str:
    now = int(time.time())
    claims = {
        "iss": issuer,
        "aud": aud,
        "sub": "svc",
        "azp": "portal-backend-ai",
        "scope": scopes,
        "iat": now,
        "exp": now + exp_in,
    }
    return jwt.encode({"alg": "RS256", "kid": key.kid}, claims, key)


def auth(scopes: str = "ai:invoke") -> dict[str, str]:
    return {"Authorization": f"Bearer {make_token(scopes)}"}


def pdf_bytes(*pages: str) -> bytes:
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    for text in pages:
        y = 800
        for line in text.split("\n"):
            c.drawString(50, y, line[:110])
            y -= 16
        c.showPage()
    c.save()
    return buf.getvalue()


def chat_body(message: str, allowed: list[str], **extra: Any) -> dict[str, Any]:
    return {
        "conversation_id": "conv-1",
        "message": message,
        "history": [],
        "user": {"id": "u1", "name": "Aisha Rahman", "roles": ["SALES"]},
        "allowed_collections": allowed,
        **extra,
    }


@pytest.fixture
def settings(tmp_path: Path) -> Settings:
    return Settings(
        database_url=f"sqlite+aiosqlite:///{tmp_path / 'test.db'}",
        chroma_mode="ephemeral",
        embedding_provider="fake",
        ai_llm_provider="fake",
        upload_dir=str(tmp_path / "uploads"),
        ai_max_upload_mb=1,
        oidc_issuer=ISSUER,
        retrieval_min_score=0.2,
        _env_file=None,  # type: ignore[call-arg]
    )


@pytest_asyncio.fixture
async def services(settings: Settings) -> AsyncIterator[Services]:
    security.reset_jwks_cache()
    security._cache.keys = KeySet.import_key_set({"keys": [_KEY.as_dict(private=False)]})
    security._cache.fetched_at = time.monotonic() + 10_000
    engine = create_async_engine(settings.database_url)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    store = ChromaVectorStore.ephemeral()
    store.reset()
    svc = assemble(
        settings, engine=engine, embedder=CountingEmbedder(), store=store, llm=FakeLlm(), drive=FakeDrive(), web=FakeWeb()
    )
    yield svc


@pytest_asyncio.fixture
async def client(settings: Settings, services: Services) -> AsyncIterator[httpx.AsyncClient]:
    app = create_app(settings, services)
    async with app.router.lifespan_context(app):
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as c:
            yield c


async def upload(client: httpx.AsyncClient, services: Services, collection: str, name: str, data: bytes) -> dict[str, Any]:
    r = await client.post(
        f"/v1/collections/{collection}/documents", headers=auth("ai:ingest"), files={"file": (name, data, "application/pdf")}
    )
    assert r.status_code == 202, r.text
    await services.worker.wait_idle()
    return dict(r.json())
