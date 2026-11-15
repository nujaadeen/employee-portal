"""Access rules: retrieval is filtered by allowed_collections inside the vector query."""

import httpx

from ai_service.container import Services
from ai_service.providers.llm import FakeLlm
from tests.conftest import auth, chat_body, pdf_bytes, upload

ENG = "The deployment runbook says rotate the signing keys with the vault rotate command every ninety days."
PROD = "The Baracel product guide explains how the lantern device pairs over bluetooth in three steps."


async def _seed(client: httpx.AsyncClient, svc: Services) -> None:
    await upload(client, svc, "engineering", "runbook.pdf", pdf_bytes(ENG))
    await upload(client, svc, "product", "guide.pdf", pdf_bytes(PROD))


async def test_engineering_text_never_reaches_a_user_without_the_collection(
    client: httpx.AsyncClient, services: Services
) -> None:
    await _seed(client, services)
    # the query matches the engineering chunk exactly
    r = await client.post("/v1/chat", json=chat_body(ENG, ["product"]), headers=auth())
    assert r.status_code == 200
    data = r.json()
    assert all(s["collection"] == "product" for s in data["sources"])
    assert "engineering" not in r.text.lower().replace("engineering", "", 0) or all(
        s["collection"] != "engineering" for s in data["sources"]
    )
    llm = services.llm
    assert isinstance(llm, FakeLlm)
    for call in llm.calls:  # the retrieved context never held it (the user's own question may repeat it)
        block = call[-1].content.split("</retrieved_context>")[0]
        assert "signing keys" not in block and "signing keys" not in call[0].content


async def test_allowed_engineering_gets_the_engineering_source(client: httpx.AsyncClient, services: Services) -> None:
    await _seed(client, services)
    r = await client.post("/v1/chat", json=chat_body(ENG, ["engineering", "product"]), headers=auth())
    sources = r.json()["sources"]
    assert sources[0]["collection"] == "engineering" and sources[0]["title"]
    assert sources[0]["page"] == 1 and sources[0]["source_ref"] == "runbook.pdf" and 0 < sources[0]["score"] <= 1


async def test_empty_allowed_collections_returns_no_sources_and_the_model_is_told(
    client: httpx.AsyncClient, services: Services
) -> None:
    await _seed(client, services)
    r = await client.post("/v1/chat", json=chat_body(ENG, []), headers=auth())
    assert r.status_code == 200 and r.json()["sources"] == []
    assert "do not know" in r.json()["text"].lower()
    llm = services.llm
    assert isinstance(llm, FakeLlm)
    last = llm.calls[-1][-1].content
    assert "No documents are available" in last and "signing keys" not in last.split("</retrieved_context>")[0]


async def test_unknown_or_malformed_collection_codes(client: httpx.AsyncClient, services: Services) -> None:
    await _seed(client, services)
    r = await client.post("/v1/chat", json=chat_body(ENG, ["does-not-exist"]), headers=auth())
    assert r.status_code == 200 and r.json()["sources"] == []
    r = await client.post("/v1/chat", json=chat_body(ENG, ["../etc", "Product"]), headers=auth())
    assert r.status_code == 422
