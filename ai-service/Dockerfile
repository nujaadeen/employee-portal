# CUDA-capable base: the embedding model (and the reranker) use the GPU when the container has one,
# and fall back to the CPU when it does not.
FROM nvidia/cuda:12.6.3-cudnn-runtime-ubuntu24.04

ENV DEBIAN_FRONTEND=noninteractive \
    UV_LINK_MODE=copy \
    UV_COMPILE_BYTECODE=1 \
    UV_PYTHON_PREFERENCE=only-system \
    PYTHONUNBUFFERED=1 \
    PATH="/app/.venv/bin:$PATH"

RUN apt-get update \
 && apt-get install -y --no-install-recommends python3.12 python3.12-venv ca-certificates \
 && rm -rf /var/lib/apt/lists/*
COPY --from=ghcr.io/astral-sh/uv:0.12.7 /uv /usr/local/bin/uv

WORKDIR /app
# Dependencies first, so code changes do not reinstall torch
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen --no-dev --extra ml --no-install-project
COPY ai_service ./ai_service
COPY alembic ./alembic
COPY alembic.ini entrypoint.sh ./
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen --no-dev --extra ml && chmod +x entrypoint.sh

EXPOSE 8000
ENTRYPOINT ["./entrypoint.sh"]
