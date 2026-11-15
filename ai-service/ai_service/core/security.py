"""Bearer token validation against the realm JWKS, and per-route scope checks."""

import asyncio
import logging
import time
from dataclasses import dataclass, field
from typing import Any

import httpx
from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from joserfc import jwt
from joserfc.errors import JoseError
from joserfc.jwk import KeySet

from ai_service.core.config import Settings, get_settings
from ai_service.core.errors import ProblemError

log = logging.getLogger(__name__)
_bearer = HTTPBearer(auto_error=False)
JWKS_TTL = 600.0


@dataclass
class JwksCache:
    keys: KeySet | None = None
    fetched_at: float = 0.0
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)

    async def get(self, settings: Settings, force: bool = False) -> KeySet:
        async with self.lock:
            if force or self.keys is None or time.monotonic() - self.fetched_at > JWKS_TTL:
                try:
                    async with httpx.AsyncClient(timeout=5.0) as client:
                        resp = await client.get(settings.jwks_url)
                        resp.raise_for_status()
                    self.keys = KeySet.import_key_set(resp.json())
                    self.fetched_at = time.monotonic()
                except (httpx.HTTPError, ValueError) as exc:
                    if self.keys is None:
                        raise ProblemError(503, "identity-provider-unavailable", "Cannot load signing keys") from exc
                    log.warning("JWKS refresh failed, using cached keys")
            assert self.keys is not None
            return self.keys


_cache = JwksCache()


@dataclass(frozen=True)
class Principal:
    subject: str
    client_id: str
    scopes: frozenset[str]


def _unauthorized(detail: str) -> ProblemError:
    return ProblemError(401, "unauthorized", detail, {"WWW-Authenticate": "Bearer"})


async def _decode(token: str, settings: Settings) -> dict[str, Any]:
    registry = jwt.JWTClaimsRegistry(
        leeway=settings.oidc_leeway_seconds,
        iss={"essential": True, "value": settings.oidc_issuer},
        aud={"essential": True, "value": settings.oidc_audience},
        exp={"essential": True},
    )
    for attempt in (0, 1):
        keys = await _cache.get(settings, force=attempt == 1)
        try:
            decoded = jwt.decode(token, keys, algorithms=["RS256"])  # only RS256: no alg confusion
            registry.validate(decoded.claims)  # issuer, audience, expiry
            return dict(decoded.claims)
        except (JoseError, ValueError, KeyError):
            if attempt == 0:
                continue  # keys may have rotated: refetch once
            raise _unauthorized("Invalid, expired or wrong-audience token") from None
    raise _unauthorized("Invalid or expired token")


async def authenticate(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
    settings: Settings = Depends(get_settings),
) -> Principal:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise _unauthorized("Missing bearer token")
    claims = await _decode(credentials.credentials, settings)
    return Principal(
        subject=str(claims.get("sub", "")),
        client_id=str(claims.get("azp", claims.get("client_id", ""))),
        scopes=frozenset(str(claims.get("scope", "")).split()),
    )


def require_scope(scope: str):  # type: ignore[no-untyped-def]
    async def dependency(request: Request, principal: Principal = Depends(authenticate)) -> Principal:
        if scope not in principal.scopes:
            raise ProblemError(403, "insufficient-scope", f"Scope {scope} is required")
        request.state.principal = principal
        return principal

    return dependency


def reset_jwks_cache() -> None:
    global _cache
    _cache = JwksCache()
