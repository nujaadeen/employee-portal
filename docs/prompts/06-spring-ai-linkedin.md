Follow CLAUDE.md including the Phase 2 rules. Connect the Spring backend to the AI service (ai-service/, docs/ai-service.md) and add knowledge base management and real LinkedIn publishing. Do not change ai-service/ or frontend/. If the AI service has a gap, write it in docs/ai-service-gaps.md and work around it. Keep docs/progress.md as a checklist of the 8 steps and commit after each. Look at design/screens/08-ai-assistant.png, 10-knowledge-base.png and 11-social-accounts.png first, so the APIs return what these screens show.

1. AI CLIENT
- Add HttpAiAssistantClient implementing AiAssistantPort, selected by ai.mode=http (the stub stays for tests and ai.mode=stub). Settings: ai.service.url, timeouts, and the Spring OAuth2 client registration for client portal-backend-ai (client credentials, token cached and refreshed).
- Streaming: POST /api/ai/conversations/{id}/messages with Accept text/event-stream returns server-sent events (token, sources, draft, done, error) by relaying the AI service stream (WebClient for this client only, the rest stays MVC). Save the final assistant message, its sources and its draft when done arrives. If the browser disconnects, cancel the upstream call and save the partial text with status STOPPED. Without that Accept header the endpoint returns plain JSON as in Phase 1.
- On every call send allowed_collections computed from the user's permissions (the codes product, brand, campaign, hr-policies, engineering), the last 10 messages as history, and the user's id, name and roles.
- Resilience: timeouts, a circuit breaker, and a clear 503 problem type ai-unavailable. No automatic retry of chat calls.
- Rate limit chat to 20 messages per minute per user (429 with a Retry-After header).
- POST /api/ai/messages/{id}/feedback with rating UP or DOWN and an optional comment, stored in ai_feedback, for the caller's own conversations only.
- PATCH /api/ai/drafts/{id}: the draft owner edits headline, caption and hashtags before publishing. Validate platform limits (LinkedIn caption at most 3000 characters).

2. KNOWLEDGE BASE ADMIN (new permission ai:knowledge:manage, given to ADMIN)
- Spring owns the collection catalogue and who may use each collection. Endpoints: GET /api/knowledge/collections (merges Spring's catalogue and role access with the AI service's counts and status); POST /api/knowledge/collections (code of 3 to 40 characters in [a-z0-9-], name; the code never changes); PUT /api/knowledge/collections/{code}/roles (replace the role list, audit logged, clears the permission cache); DELETE /api/knowledge/collections/{code} (only when it has no documents).
- Proxy to the AI service, with the Spring-side permission check: POST /api/knowledge/collections/{code}/documents (multipart, stream it through without loading the file in memory, enforce a size limit), POST .../sources/drive, POST .../sources/web, POST .../reindex, GET .../documents, DELETE .../documents/{id}, GET /api/knowledge/jobs/{id}.
- Keep knowledge_collection.doc_count in step with the AI service after each finished job.

3. SOCIAL CONNECTIONS (LinkedIn)
- Tables: social_connection (owner employee id or null for company, provider, account_urn, display_name, access_token_enc, refresh_token_enc, expires_at, scopes, status CONNECTED, EXPIRED or REVOKED) and social_oauth_state (random state, user, purpose, expires after 10 minutes, single use).
- Encrypt tokens with AES-GCM using a key from SOCIAL_TOKEN_KEY (with a key id so it can be rotated). Tokens are never returned by any API and never logged.
- Endpoints: GET /api/social/connections (the caller's own connections plus the company connections the caller may use, no secrets); POST /api/social/linkedin/connect?target=personal|company returns the LinkedIn authorisation URL with a state value; GET /api/social/linkedin/callback validates the state, exchanges the code, reads the member profile (or, for the company target, the organisations the user administers), stores the connection and redirects to the frontend /accounts page with a result flag; DELETE /api/social/connections/{id} (own connections, and company ones only with access:manage). Refresh tokens when LinkedIn issues them and mark a connection EXPIRED when refresh fails.
- Read LinkedIn's current official documentation before writing this code: scopes for personal posting versus company page posting, the Posts API path, the required version header and the author URN formats. Do not rely on memory. Company page posting may not be approved for our app yet, so keep it behind social.linkedin.company-enabled, and personal posting behind social.linkedin.enabled.

4. PUBLISHING (replaces the Phase 1 simulation)
- POST /api/ai/drafts/{id}/publish with channel codes. For a LinkedIn channel with a CONNECTED connection and the feature switch on, create a text post through LinkedIn's API with the draft's headline, caption and hashtags, and store the returned post id and URL in publish_log.
- Result per channel: PUBLISHED, FAILED (reason TOKEN_EXPIRED, RATE_LIMITED, REJECTED or UNKNOWN_ERROR), SIMULATED (only when LinkedIn is switched off, for local development), or UNKNOWN (timeout or an unclear response). Never retry automatically when the result is UNKNOWN, and tell the user to check LinkedIn.
- Idempotent: one publish per (draft, channel). A double click or a repeated request returns the first result.
- Permissions as before: social:publish:company for company channels and social:publish:personal for personal ones, and a user can publish only through their own personal connection.
- Instagram and Facebook channels report status COMING_LATER in GET /api/ai/access and the publish endpoint answers 422 channel-not-available for them.
- GET /api/social/history?page=&size= returns the caller's publish_log entries (when, channel, headline, result, link).

5. CONFIG
- Add to infra/.env.example and backend config: AI_SERVICE_URL, PORTAL_BACKEND_AI_CLIENT_SECRET, LINKEDIN_CLIENT_ID, LINKEDIN_CLIENT_SECRET, LINKEDIN_REDIRECT_URI (http://localhost:8080/api/social/linkedin/callback), SOCIAL_TOKEN_KEY, social.linkedin.enabled, social.linkedin.company-enabled.
- Seed the knowledge_collection rows and role_collection rows so the codes match the ones the AI service uses.

6. TESTS
- WireMock for the AI service (token, stream, 503, slow response) and for LinkedIn (token exchange, profile, create post 201, 401, 429, timeout). Cover: allowed_collections sent for Aisha is product, brand and campaign, and for Lena is hr-policies only; Aisha gets 403 on every knowledge admin endpoint; the stream is saved on done and a cancelled stream saves partial text; the circuit breaker returns ai-unavailable; a replayed or expired OAuth state is rejected; tokens never appear in any API response or log output (serialise the responses and search them); a double publish posts once; an UNKNOWN result is not retried; a user cannot publish through someone else's connection; Instagram returns 422.

7. CONTRACT AND DOCS
- Re-export docs/openapi.json with required fields marked. Update docs/api.md, docs/run.md and docs/decisions.md.

8. RUN IT
- With the stack and the AI service up and ai.mode=http, show real output of: a streamed chat call as aisha.rahman with sources only from her collections; the same product question as lena.fischer returning no product source; a PDF uploaded as daniel.okoye through Spring that becomes READY and then shows in GET /api/knowledge/collections; a publish with LinkedIn switched off returning SIMULATED. If LinkedIn test credentials are in .env, also do one real personal post, otherwise say you could not.

DONE WHEN mvn verify passes, the items in step 8 are shown, and you report what you ran and what you could not run.