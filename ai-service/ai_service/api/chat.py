import json
from collections.abc import AsyncIterator

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from ai_service.api.deps import get_services
from ai_service.api.schemas import ChatRequest, ChatResponse
from ai_service.container import Services
from ai_service.core.security import require_scope

router = APIRouter(prefix="/v1", tags=["chat"], dependencies=[Depends(require_scope("ai:invoke"))])


@router.post("/chat", response_model=ChatResponse, responses={200: {"content": {"text/event-stream": {}}}})
async def chat(req: ChatRequest, request: Request, svc: Services = Depends(get_services)) -> ChatResponse | StreamingResponse:
    if "text/event-stream" in request.headers.get("accept", ""):

        async def stream() -> AsyncIterator[str]:
            try:
                async for name, data in svc.chat.events(req):
                    yield f"event: {name}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
            except Exception as exc:  # last resort: tell the client, never leak details
                yield f"event: error\ndata: {json.dumps({'code': 'internal-error', 'detail': type(exc).__name__})}\n\n"

        return StreamingResponse(
            stream(), media_type="text/event-stream", headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}
        )
    return await svc.chat.respond(req)
