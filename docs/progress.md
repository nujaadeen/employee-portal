# Progress

## Prompt 01 (backend, realm, infra): done
See the report in the earlier session. `mvn verify` passes, stack runs.

## Prompt 02 (frontend and login theme)
- [x] 1. Project setup (Vite, tokens alias, src/ui kit, styleguide, hex lint)
- [x] 2. Auth (PKCE, in-memory tokens, typed API client, useMe, Can)
- [x] 3. App shell and navigation, status pages
- [x] 4. Screens
- [x] 5. Keycloak login theme
- [x] 6. Tests (Vitest, Playwright)
- [x] 7. Visual check
- [x] 8. Docs

## Prompt 03 (backend gaps)
- [x] 1. Project members
- [x] 2. Project repositories
- [x] 3. Public config
- [x] 4. Reset access and New role
- [x] 5. Notifications paging and read-all
- [x] 6. MFA state enum
- [x] 7. Required fields in the contract
- [x] 8. Activity order
- [x] Docs: api.md, openapi.json, backend-gaps.md, mvn verify (123 tests)

## Prompt 04 (frontend follow-up)
- [x] 1. Regenerated API types, removed the non-null assertions
- [x] 2. Create task: members picker, add/remove members
- [x] 3. Branch and Link PR: repositories select
- [x] 4. giteaUrl from /api/config, remove VITE_GITEA_URL
- [x] 5. Reset access, New role, Delete role, toasts
- [x] 6. Notifications: unreadCount, Unread only, Load more, Mark all as read
- [x] 7. MFA states with text and icon
- [x] 8. Activity order note
- [x] Tests (36 Vitest), Playwright (14), screenshots (06 board, 09 roles, 12 notifications), docs

## Prompt 05 (AI service)
- [x] 1. Project (ai-service/, uv lock, layout, Dockerfile)
- [x] 2. Infrastructure (chroma, ollama, ollama-init, ai-db-init, ai-service, .env.example)
- [x] 3. Keycloak client portal-backend-ai, scopes, audience mapper
- [x] 4. Security and health (JWKS validation, scopes, problem responses, request id, /healthz, /readyz)
- [x] 5. Providers (bge-m3, Ollama, OpenAI-compatible, fake, Chroma)
- [x] 6. Ingestion (PDF, Drive, web, reindex, jobs, incremental, replace, delete)
- [x] 7. Chat (retrieval filter, prompt, SSE, drafts with one retry)
- [x] 8. Tests (29 pytest) and evals
- [x] 9. Docs (README, docs/ai-service.md, OpenAPI, run.md)

## Prompt 06 (Spring: AI service, knowledge base, LinkedIn)
- [x] 1. AI client (HttpAiAssistantClient, SSE relay, resilience, rate limit, feedback, draft edit)
- [ ] 2. Knowledge base admin
- [ ] 3. Social connections (LinkedIn)
- [ ] 4. Publishing
- [ ] 5. Config
- [ ] 6. Tests
- [ ] 7. Contract and docs
- [ ] 8. Run it
