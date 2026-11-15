#!/bin/sh
# Apply database migrations, then start the API. No reload, one process: the embedding model is loaded once per process.
set -e
alembic upgrade head
exec uvicorn ai_service.main:app_factory --factory --host 0.0.0.0 --port 8000
