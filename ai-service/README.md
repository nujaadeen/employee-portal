# Baracel AI service

A separate FastAPI service that answers questions and drafts social posts from Baracel's own documents (RAG).
**Only the Spring backend calls it.** The browser never does. It never decides who may see what: Spring sends
`allowed_collections` on every chat call and the service filters retrieval by that list.

```
Spring backend --(service token, client portal-backend-ai)--> ai-service --> Chroma (vectors)
                                                                 |--> Ollama or any OpenAI-compatible API (LLM)
                                                                 |--> Postgres (documents, jobs)
```

Layout (`ai_service/`): `api` (routers, schemas), `core` (config, security, logging, errors), `rag` (extract, chunking,
ingest, retrieval, prompts, chat), `providers` (embeddings, llm, vector store), `jobs` (background worker), `db` (models).

## Run it

Everything in Docker (needs the NVIDIA Container Toolkit for the GPU, otherwise add the CPU override):

```bash
cd infra
docker compose up -d                                   # GPU
docker compose -f docker-compose.yml -f docker-compose.cpu.yml up -d    # no GPU
```

From source (fast loop; Chroma is published on 127.0.0.1:8001 by the host override):

```bash
cd infra && docker compose -f docker-compose.yml -f docker-compose.host-ai.yml up -d postgres chroma keycloak ai-db-init
cd ../ai-service
uv sync --extra ml                                     # the "ml" extra is torch + sentence-transformers
export DATABASE_URL=postgresql+psycopg://postgres:postgres@localhost:5433/ai_service \
       CHROMA_URL=http://localhost:8001 UPLOAD_DIR=$PWD/data/uploads OLLAMA_URL=http://localhost:11434
uv run alembic upgrade head
uv run uvicorn ai_service.main:app_factory --factory --port 8000
```

A token: `scripts/get-ai-token.sh "ai:invoke ai:ingest"` (client credentials, client `portal-backend-ai`).
If your Keycloak volume is older than this client, run `scripts/apply-ai-client.sh` once.

## Configuration (environment variables only)

| Variable | Default | Meaning |
|---|---|---|
| `DATABASE_URL` | `postgresql+psycopg://postgres:postgres@localhost:5433/ai_service` | documents and jobs |
| `OIDC_ISSUER` | `http://localhost:8180/realms/baracel` | must equal the `iss` of tokens |
| `OIDC_JWKS_URL` | issuer + `/protocol/openid-connect/certs` | where to fetch signing keys (inside Docker: `http://keycloak:8180/...`) |
| `OIDC_AUDIENCE` | `ai-service` | required `aud` |
| `CHROMA_URL` | `http://localhost:8001` | Chroma server |
| `AI_LLM_PROVIDER` | `ollama` | `ollama`, `openai_compatible` or `fake` |
| `OLLAMA_URL`, `OLLAMA_MODEL` | `http://localhost:11434`, `qwen2.5:7b-instruct` | |
| `OPENAI_BASE_URL`, `OPENAI_API_KEY`, `OPENAI_MODEL` | empty | for `openai_compatible` |
| `EMBEDDING_PROVIDER` | `sentence_transformers` | or `fake` (hashing embedder for tests) |
| `EMBEDDING_MODEL`, `EMBEDDING_DEVICE` | `BAAI/bge-m3`, `auto` | `auto` uses CUDA when present |
| `RERANKER_ENABLED`, `RERANKER_MODEL` | `false`, `BAAI/bge-reranker-v2-m3` | optional second pass |
| `RETRIEVAL_TOP_K`, `RETRIEVAL_MIN_SCORE` | `8`, `0.45` | cosine similarity threshold (bge-m3: related 0.5 and up, unrelated about 0.35) |
| `RETRIEVAL_MAX_CONTEXT_CHARS`, `CHAT_HISTORY_TURNS` | `9000`, `6` | |
| `AI_MAX_UPLOAD_MB` | `25` | PDF upload limit |
| `GOOGLE_SA_KEY_PATH` | empty | service account key file for Drive; empty disables Drive |
| `UPLOAD_DIR` | `./data/uploads` | stored PDFs (needed for reindex) |
| `WEB_PAGE_LIMIT`, `WEB_ALLOW_PRIVATE` | `30`, `false` | crawler limits; private addresses blocked unless allowed (dev only) |

## API (details and examples in `docs/ai-service.md`, contract in `docs/ai-service-openapi.json`)

| Route | Scope |
|---|---|
| `GET /healthz`, `GET /readyz` | open |
| `POST /v1/chat` (JSON, or SSE with `Accept: text/event-stream`) | `ai:invoke` |
| `POST /v1/collections/{code}/documents` (multipart PDF) | `ai:ingest` |
| `POST /v1/collections/{code}/sources/drive`, `/sources/web`, `/reindex` | `ai:ingest` |
| `GET /v1/collections`, `GET /v1/collections/{code}/documents`, `DELETE /v1/collections/{code}/documents/{id}`, `GET /v1/jobs/{id}` | `ai:ingest` |

## Checks

```bash
uv run pytest          # 29 tests, fake LLM, fake embedder, ephemeral Chroma, SQLite: no network, no GPU
uv run ruff check . && uv run mypy ai_service
uv run python scripts/export_openapi.py            # writes docs/ai-service-openapi.json
uv run python evals/run_evals.py --token "$(../scripts/get-ai-token.sh 'ai:invoke ai:ingest')"   # needs a running service
```

`AI_LLM_PROVIDER=fake` (with `EMBEDDING_PROVIDER=fake`) gives a deterministic stack with canned answers that cite the
retrieved sources and valid drafts. Use it for fast end-to-end tests of Spring and the frontend without a GPU.
