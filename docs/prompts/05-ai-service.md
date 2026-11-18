Follow CLAUDE.md including the Phase 2 rules. Build the AI service in ai-service/. It is a separate FastAPI RAG service that the Spring backend calls. Do NOT change backend/ or frontend/ (the next prompts do). You may edit infra/ and keycloak/realm/ as described below. First read the AI module section of docs/api.md and the AiAssistantPort in backend/, so the service contract matches what Spring already sends and expects. Keep docs/progress.md as a checklist of the 9 steps and commit after each step.

1. PROJECT
- Python 3.12, FastAPI, uvicorn, pydantic v2 and pydantic-settings, SQLAlchemy 2 with Alembic (Postgres database ai_service), httpx, chromadb client, sentence-transformers, PyMuPDF, trafilatura, google-api-python-client and google-auth, authlib or python-jose for JWT, pytest, ruff, mypy. Lock dependencies with uv.
- Layout from CLAUDE.md. Dockerfile on a CUDA-capable base so embeddings use the GPU, falling back to CPU.

2. INFRASTRUCTURE
- Extend infra/docker-compose.yml with: chroma (persistent volume, not published on the host), ollama (GPU reservation, volume; pull qwen2.5:7b-instruct with an init container or a documented one-line command), ai-service (port 8000 bound to localhost only), and the ai_service database in Postgres.
- Add to infra/.env.example: AI_LLM_PROVIDER=ollama, OLLAMA_URL, OLLAMA_MODEL=qwen2.5:7b-instruct, EMBEDDING_MODEL=BAAI/bge-m3, CHROMA_URL, GOOGLE_SA_KEY_PATH, AI_MAX_UPLOAD_MB=25, and the secret for the new Keycloak client.

3. KEYCLOAK
- In keycloak/realm/baracel-realm.json add a confidential client portal-backend-ai with a service account, scopes ai:invoke and ai:ingest, and an audience mapper for ai-service. Document in docs/ai-service.md how Spring gets a token with client credentials.

4. SECURITY AND HEALTH
- A dependency that validates the bearer token against the realm JWKS (issuer, audience ai-service, expiry) and checks the scope per route. Errors are JSON problem responses. Add a request-id middleware.
- GET /healthz (process alive) and GET /readyz (database, Chroma and Ollama reachable). Both open.

5. PROVIDERS (an interface and an implementation each)
- EmbeddingProvider: BAAI/bge-m3 through sentence-transformers, batched, normalised vectors, loaded once in the app lifespan, device chosen automatically.
- LlmProvider: Ollama chat with streaming, temperature, max tokens, timeout and JSON-schema output. Add a second implementation for any OpenAI-compatible API, selected by AI_LLM_PROVIDER=openai_compatible with base URL, key and model from environment, so a paid model replaces Ollama by configuration only. Also add AI_LLM_PROVIDER=fake, a deterministic provider with canned answers and drafts that cite the retrieved sources, for fast end-to-end tests, and document it.
- VectorStore: Chroma, one Chroma collection per portal collection code, cosine distance. Metadata per chunk: collection, document_id, source_type, title, page, chunk_index, language, updated_at.

6. INGESTION
- Tables: document (id, collection, source_type PDF, DRIVE or WEB, source_ref, title, content_hash, status PENDING, INDEXING, READY or FAILED, error, chunk_count, updated_at) and ingest_job (id, collection, kind, status, progress, error, created_at).
- Endpoints (scope ai:invoke is not enough, they need ai:ingest): POST /v1/collections/{code}/documents (multipart PDF, size limit from env, content type and magic-byte check); POST /v1/collections/{code}/sources/drive (folder id, recursive, PDFs and Google Docs exported as text); POST /v1/collections/{code}/sources/web (list of URLs, same domain only, obey robots.txt, page limit); POST /v1/collections/{code}/reindex; GET /v1/collections (code, documents, chunks, last_sync, status); GET /v1/collections/{code}/documents; DELETE /v1/collections/{code}/documents/{id}; GET /v1/jobs/{id}.
- Jobs run in a background worker, one at a time per collection, and report progress. A file that fails is recorded and does not fail the whole job.
- Pipeline: extract text (PyMuPDF per page, trafilatura for web pages), clean it, split into chunks of about 500 tokens with 80 tokens overlap on paragraph boundaries, keep page numbers, detect language, embed in batches, upsert. Re-ingesting a changed document replaces its old chunks. An unchanged content_hash is skipped. Deleting a document deletes its chunks. Drive sync is incremental by modified time.

7. CHAT (scope ai:invoke)
- POST /v1/chat. Body: conversation_id, message, history (last N turns), user (id, name, roles), allowed_collections (list), mode (auto, answer or social_post), language (optional), platform (optional, default LINKEDIN). It returns JSON, or Server-Sent Events when the Accept header is text/event-stream, with events token, sources, draft, done and error.
- Retrieval: embed the query and query Chroma with a metadata filter on collection IN allowed_collections inside the vector query. Take the top 8, optionally rerank with BAAI/bge-reranker-v2-m3 behind a flag, drop chunks under a similarity threshold, cap the context size. An empty allowed_collections gives no context, and the model is told so.
- Prompt: the system prompt carries the Baracel brand voice (plain, warm, no jargon), answers in the user's language, uses only the retrieved context for company facts, cites sources as [1], [2], and says it does not know when the context lacks the answer. Retrieved text goes in a clearly delimited data block declared untrusted, and instructions inside it are ignored.
- Intent: with mode auto, decide between a normal answer and a social post request. For a social post return a structured draft: headline, caption, hashtags, platform, language, and the sources used. Respect platform limits (a LinkedIn caption is at most 3000 characters, aim for 600 to 1300). Validate the JSON against the schema and retry once if it is invalid.
- Each source in the reply has title, collection, page, source_ref and score. Never return text from a collection that is not in allowed_collections.

8. TESTS AND EVALS
- pytest with a fake LLM, a deterministic fake embedder and an ephemeral Chroma. Cover: 401 without a token and 403 with the wrong scope; a user whose allowed_collections lacks "engineering" never receives an engineering chunk even when the query matches it exactly; empty allowed_collections returns no sources; PDF ingest, re-ingest changed and unchanged, and delete; Drive and web ingest against fakes; SSE event order; invalid draft JSON retried once; instruction text inside a document is not obeyed (the fake LLM asserts how the prompt is built).
- evals/: a small golden set (question, expected collection, expected source title) with sample PDFs generated in the repo, and a script that prints hit rate at 3 and at 8 and p95 latency. It is not part of the normal test run.

9. DOCS
- ai-service/README.md (run, configuration, API), docs/ai-service.md (request and response examples and the security model), the OpenAPI file at docs/ai-service-openapi.json, and docs/run.md updated.

DONE WHEN
- docker compose up -d starts everything including the GPU Ollama, and pytest and ruff pass.
- You ran the service and show the real output of: a PDF upload followed by GET /v1/collections; a chat call with allowed_collections ["product"] that streams tokens and returns sources; the same question with allowed_collections ["brand"] returning no product source; and the 401 and 403 cases. Use a token from the portal-backend-ai client.
- Report what you ran and what you could not run, for example Google Drive if no key is configured.