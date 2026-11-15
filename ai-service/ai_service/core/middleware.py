import logging
import re
import time
import uuid

from starlette.types import ASGIApp, Message, Receive, Scope, Send

from ai_service.core.logging import request_id_var

log = logging.getLogger("ai_service.access")
_SAFE = re.compile(r"^[A-Za-z0-9._-]{8,64}$")


class RequestIdMiddleware:
    """Pure ASGI middleware (does not buffer streaming responses). Echoes or creates X-Request-Id."""

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        incoming = dict(scope["headers"]).get(b"x-request-id", b"").decode()
        rid = incoming if _SAFE.match(incoming) else uuid.uuid4().hex
        scope.setdefault("state", {})["request_id"] = rid
        token = request_id_var.set(rid)
        started = time.perf_counter()
        status = 0

        async def send_wrapper(message: Message) -> None:
            nonlocal status
            if message["type"] == "http.response.start":
                status = message["status"]
                message.setdefault("headers", []).append((b"x-request-id", rid.encode()))
            await send(message)

        try:
            await self.app(scope, receive, send_wrapper)
        finally:
            log.info("%s %s -> %s in %dms", scope["method"], scope["path"], status, (time.perf_counter() - started) * 1000)
            request_id_var.reset(token)
