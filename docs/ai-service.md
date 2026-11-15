# AI service (Phase 2)

`ai-service/` is a FastAPI RAG service. The Spring backend is its only client. Run instructions and configuration are in
`ai-service/README.md`; the machine-readable contract is `docs/ai-service-openapi.json`.

## Security model

1. **Who may call it.** Every route except `/healthz` and `/readyz` needs a Keycloak access token that is verified against
   the realm's JWKS: signature, issuer (`OIDC_ISSUER`), expiry and audience `ai-service`. Keys are cached for 10 minutes
   and refetched once when a token does not verify (key rotation). A person's token (audience `portal-api`) is refused:
   401 `Invalid audience`.
2. **Scopes.** `ai:invoke` for chat; `ai:ingest` for every route that manages documents. The scopes are *optional* scopes of
   the client `portal-backend-ai`, so the caller asks for the one it needs and a chat token cannot ingest. 401 means
   no or bad token, 403 `insufficient-scope` means a valid token without the scope. Errors are `application/problem+json`.
3. **Who may see which collection** is decided by Spring only. Each chat call lists `allowed_collections`. The vector
   store has one Chroma collection per code and the query only opens the allowed ones (plus a metadata filter on
   `collection IN allowed` inside the query). An empty list means no retrieval at all and the model is told so. A final
   check drops, and logs, any hit outside the list. The service has no notion of roles or users beyond what is sent.
4. **Prompt injection.** Retrieved text is placed in a block `<retrieved_context untrusted="true">` in the last user
   message. The system prompt declares it data that is never an instruction. A document cannot close the block
   (the closing tag is neutralised). A test proves a document that says "ignore all previous instructions" never reaches
   the system prompt.
5. **No sensitive logging.** Logs hold request ids, user ids, collection codes, counts and timings, never message text,
   document text or tokens. Validation errors list field names, never values.
6. **Web crawling** is same-domain only, obeys robots.txt, has a page limit, and refuses private addresses unless
   `WEB_ALLOW_PRIVATE=true`. Uploads are checked for content type, `%PDF-` magic bytes and size.

## How Spring gets a token

Client `portal-backend-ai` (confidential, service account, secret `PORTAL_BACKEND_AI_CLIENT_SECRET`). Request the scope you need:

```bash
curl -s -d grant_type=client_credentials -d client_id=portal-backend-ai \
  --data-urlencode "client_secret=$PORTAL_BACKEND_AI_CLIENT_SECRET" -d "scope=ai:invoke" \
  http://localhost:8180/realms/baracel/protocol/openid-connect/token
```

The token has `aud: ai-service`, `azp: portal-backend-ai` and `scope: ai:invoke`. Cache it until shortly before `exp`.
`scripts/get-ai-token.sh [scope]` does the same for development.

## Chat

`POST /v1/chat`, scope `ai:invoke`.

```json
{
  "conversation_id": "demo-1",
  "message": "How do I pair the Lantern Pro with my phone?",
  "history": [{"role": "user", "content": "..."}, {"role": "assistant", "content": "..."}],
  "user": {"id": "b0000000-0000-4000-8000-000000000001", "name": "Aisha Rahman", "roles": ["SALES"]},
  "allowed_collections": ["product"],
  "mode": "auto",
  "language": "en",
  "platform": "LINKEDIN"
}
```

`mode`: `auto` (a message about a post, ad, caption or a social network becomes a draft), `answer` or `social_post`.
`history`: roles `user` and `assistant` only; the last 6 turns are used. `language` and `platform` are optional.

JSON answer (real output, `allowed_collections: ["brand"]`, nothing about pairing is there):

```json
{"conversation_id": "demo-2", "intent": "answer", "language": "en",
 "text": "I don't know because I couldn't find information on pairing the Lantern Pro with a phone in our documents. ...",
 "sources": [], "draft": null}
```

With `["product"]` the answer cites `[1]` and `sources` holds
`{"index": 1, "title": "Lantern Pro user guide.pdf", "collection": "product", "page": 1, "source_ref": "...", "score": 0.6741}`.
`source_ref` is the file name, Drive file id or URL.

Social post (`intent: "social_post"`): `draft` = `{headline, caption, hashtags, platform, language, sources[]}`.
The caption is at most 3000 characters on LinkedIn (aim 600 to 1300), 2200 on Instagram, 5000 on Facebook. The model output is validated
against the schema and limits; if invalid it is asked once more, then the call fails with 502 `invalid-draft`.

### Server-Sent Events

Send `Accept: text/event-stream`. Events, in this order:

```
event: token    data: {"text": "To"}              (many; for a draft, one short intro)
event: sources  data: {"sources": [ ... ]}
event: draft    data: {"draft": { ... }}          (social posts only)
event: done     data: {"conversation_id": "demo-1", "intent": "answer", "language": "en", "elapsed_ms": 28888}
event: error    data: {"code": "invalid-draft", "detail": "..."}      (instead of the rest, ends the stream)
```

## Ingestion

All need `ai:ingest`. Collection codes match `^[a-z][a-z0-9_-]{1,39}$` (the codes Spring uses: `product`, `brand`,
`campaign`, `engineering`). Every ingest call returns `202 {"job_id": "...", "document_id": "..."}`; poll `GET /v1/jobs/{id}`:
`status` QUEUED, RUNNING, DONE, PARTIAL (some files failed, listed in `failures`) or FAILED; `progress` 0 to 100.

| Call | Notes |
|---|---|
| `POST /v1/collections/{code}/documents` | multipart field `file`, PDF only (415 otherwise or when the bytes are not a PDF, 413 over `AI_MAX_UPLOAD_MB`). Same file name = same document: changed content replaces the old chunks, unchanged content is skipped |
| `POST .../sources/drive` | `{"folder_id": "...", "recursive": true, "force": false}`. PDFs and Google Docs (exported as text). Incremental by `modifiedTime`. Share the folder with the service account's e-mail |
| `POST .../sources/web` | `{"urls": [...], "follow_links": false, "max_pages": 20}`. Same domain only (422 `different-domain`) |
| `POST .../reindex` | re-embeds every document of the collection (for example after changing the embedding model) |
| `GET /v1/collections` | `[{"code": "product", "documents": 1, "chunks": 1, "last_sync": "...", "status": "READY"}]` |
| `GET .../documents`, `DELETE .../documents/{id}` | deleting removes the chunks and the stored PDF |

Pipeline: extract (PyMuPDF per page, trafilatura for web), clean, chunk (about 500 tokens, 80 overlap, on paragraph
boundaries, page numbers kept), detect language, embed in batches, upsert. Chunk metadata: `collection`, `document_id`,
`source_type`, `source_ref`, `title`, `page`, `chunk_index`, `language`, `updated_at`. One job at a time per collection; a file that
fails is recorded (`document.status = FAILED` with a short reason) and does not fail the job.

## Providers

* Embeddings: `BAAI/bge-m3` through sentence-transformers, normalised, fp16 on CUDA, loaded once at start-up.
* LLM: `ollama` (default, streaming, JSON-schema output), `openai_compatible` (any `/chat/completions` API: set
  `AI_LLM_PROVIDER=openai_compatible`, `OPENAI_BASE_URL`, `OPENAI_API_KEY`, `OPENAI_MODEL`; nothing else changes), `fake`.
* Vector store: Chroma, cosine distance.

## Evals

`ai-service/evals/run_evals.py` uploads generated sample PDFs, asks 12 golden questions with all collections allowed and
prints hit rate at 3 and at 8 and latency. Last run (bge-m3 on an RTX 3050 4 GB, qwen2.5:7b-instruct through Ollama):
hit rate @3 100 %, @8 100 %, answer latency median 14.7 s, p95 30 s (a 4 GB card runs the 7B model partly on the CPU).

## Not done in this phase

OCR for scanned PDFs, Drive deletions (a file removed from Drive stays indexed until deleted here), authentication of the
Gitea-style users inside the AI service (by design: Spring decides), token streaming for social drafts (they are validated first).
