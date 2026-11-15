import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from ai_service.api import chat, collections, health
from ai_service.container import Services, build_services
from ai_service.core.config import Settings, get_settings
from ai_service.core.errors import install_error_handlers
from ai_service.core.logging import configure_logging
from ai_service.core.middleware import RequestIdMiddleware

log = logging.getLogger(__name__)


def create_app(settings: Settings | None = None, services: Services | None = None) -> FastAPI:
    settings = settings or get_settings()
    configure_logging(settings.log_level)

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        # Models are loaded once here (embedding model, optional reranker), not per request.
        svc = services or build_services(settings)
        app.state.services = svc
        if settings.run_worker:
            await svc.worker.start()
        log.info("ai-service ready: llm=%s", svc.llm.name)
        try:
            yield
        finally:
            await svc.worker.stop()
            await svc.engine.dispose()

    app = FastAPI(title="Baracel AI service", version="0.1.0", lifespan=lifespan)
    app.add_middleware(RequestIdMiddleware)
    install_error_handlers(app)
    app.include_router(health.router)
    app.include_router(collections.router)
    app.include_router(chat.router)
    return app


def app_factory() -> FastAPI:
    return create_app()
