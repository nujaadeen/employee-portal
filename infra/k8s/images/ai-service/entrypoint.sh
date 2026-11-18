#!/bin/sh
# 1. make sure the embedding model is in the cache volume (first start: download, with progress in the log)
# 2. migrate the database  3. start the API
set -eu
if [ "${EMBEDDING_PROVIDER:-sentence_transformers}" = "sentence_transformers" ]; then
  python /app/k8s/prefetch_model.py "${EMBEDDING_MODEL:-BAAI/bge-m3}"
  export HF_HUB_OFFLINE=1   # the files are local now: no more calls to the hub while serving
fi
alembic upgrade head
exec uvicorn ai_service.main:app_factory --factory --host 0.0.0.0 --port 8000
