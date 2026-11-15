from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse
from sqlalchemy import text

from ai_service.api.deps import get_services
from ai_service.container import Services

router = APIRouter(tags=["health"])


@router.get("/healthz")
async def healthz() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/readyz")
async def readyz(svc: Services = Depends(get_services)) -> JSONResponse:
    checks: dict[str, bool] = {}
    try:
        async with svc.maker() as s:
            await s.execute(text("select 1"))
        checks["database"] = True
    except Exception:
        checks["database"] = False
    try:
        checks["chroma"] = await svc.store.ping()
    except Exception:
        checks["chroma"] = False
    checks["llm"] = await svc.llm.ping()
    ok = all(checks.values())
    return JSONResponse({"status": "ready" if ok else "not-ready", "checks": checks}, status_code=200 if ok else 503)
