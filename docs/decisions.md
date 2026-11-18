# Decisions

Choices made where the brief was open or ambiguous. The simplest option that fits the screens was taken.

## Stack
- **Spring Boot 4.1.2** (latest stable GA at build time; 4.2 is still RC), Java 21, Maven, springdoc 3.2.0, Jackson 3 (`tools.jackson`), Testcontainers 2.
- **Keycloak 26.8.2.** Version 26.8 checks an audience on the Admin REST API, so the `portal-service` client carries the built-in `kc-admin-api` scope. Without it every Admin API call is a 401.
- No Lombok. JPA entities use public fields and plain `UUID`/`Long` foreign keys instead of associations. DTOs are records.
- PostgreSQL is published on host port **5433** because 5432 was already used on the development machine.

## Identity and access
- Employees have a random UUID `id` and a unique `keycloak_id` (the JWT `sub`). Seed rows use the Keycloak user id as `id` too. People are looked up by `sub` only, never through Keycloak's database.
- The permission cache lives in `AccessService`; it is cleared when a matrix is saved.
- `permission` has extra UI columns (`label`, `hint`, `ui_group`, `ui_row`, `ui_column`) so the matrix screen can be drawn from the API. `role_permission` has a surrogate id.
- Added permission **`ai:knowledge:hr`** (granted only to ADMIN, which has everything) so the "HR policies" collection can be gated like the others. The matrix editor can grant it to HR.
- **`role_collection` is not a table.** `knowledge_collection.permission` holds the permission that unlocks each collection and `allowedCollections` is computed from the caller's permissions in `AiService`. A second mapping table would be a second source of truth.
- "HR approves everyone, a manager only direct reports" is decided by the permission `employee:records`, not by a role name. Same rule for who sees all leave approvals.
- Only a caller with `access:manage` may assign HR or ADMIN (invite or approve). Otherwise 403.
- ADMIN cannot remove `access:manage` from itself through the matrix (400 `lockout`).
- `employee.primary_role` is an extra column. It is the role HR chose at invite or approval and is shown in lists. Tokens and authorisation still come from Keycloak realm roles. Roles changed directly in Keycloak do not update this column.
- A `DEACTIVATED` employee gets 403 `deactivated`. Deactivating does not yet disable the Keycloak user.
- MFA for Admin and HR (shown on screen 09) and Google "work domain only" are not configured in the realm. The brief does not ask for them. Add an OTP conditional flow and `hostedDomain` when real domains exist.
- Social-login first-login flow: the built-in "first broker login" flow is embedded in the realm file with the *Review Profile* step disabled. New users get the default role `EMPLOYEE` through `default-roles-baracel`.
- Swagger UI and `/v3/api-docs` also need a JWT (the brief lists only two public endpoints).

## Employees, leave
- The seed has the 13 people named in the brief, not 48. Counts on screen come from the data (`statusCounts`).
- `scope=directory` hides PENDING_APPROVAL and DEACTIVATED people. `scope=team` shows every status.
- `GET /api/leave/requests?scope=approvals` returns PENDING by default. `status=ALL` returns history (the screen shows an approved row).
- Pending requests are reserved against the balance. The year of a request is the year of its start date. No half days. Unpaid has no limit.
- A submitted request notifies the employee's manager. If there is no manager nobody is notified (HR is not looked up).
- The leave calendar shows pending and approved leave of the caller's department.
- "Due this week" on the dashboard counts open tasks due on or before the coming Sunday (overdue included).
- The daily job runs at 00:05 UTC (`portal.leave.status-job-cron`). It only moves ACTIVE to ON_LEAVE and ON_LEAVE to ACTIVE.
- Mail problems are logged and never fail a request. Tests switch mail off with `portal.mail.enabled=false`.

## Tasks, Gitea
- Non-members of a project get **404**, not 403, for the project and its tasks.
- A new task goes into the active sprint unless a sprint is given. The task number comes from a locked `project.task_seq`.
- Task keys are matched case-insensitively (`feature/bar-142-...` is lower case) with `[A-Z]{2,6}-[0-9]+` and are only acted on when the task exists.
- A bad or missing webhook signature is **401**. With no secret configured every call is rejected (fail closed).
- Gitea review events are recognised by the `review` object in the payload; `pull_request_sync`, `_label`, `_assign` and `_review_request` are handled as pull-request events. Each approved-review delivery adds one to `approvals`, so a redelivered event counts twice.
- "PRs awaiting my review" comes from `pr_review_request` rows (review requested by Gitea events, closed by a review or when the PR closes), matched to the person through `gitea_username`.
- `GET /api/repos` lists the repositories registered for the caller's projects. A repository is registered the first time it is used in a branch or link-pr call (checked against Gitea). `?refresh=true` refreshes default branch and URL from Gitea.

## Tests
- Integration tests run MockMvc against PostgreSQL in Testcontainers with the real Flyway migrations and the demo seed, the real security filter chain and the real JWT converter. Only the decoder is replaced: test tokens look like `user~aisha.rahman`. Keycloak and Gitea are replaced by fakes. The clock is fixed at 13 November 2026 so the seed dates line up with the screens.
- Each test class rolls back its changes.

## Demo seed (profile `local` and tests only, `db/seed/V1000__local_seed.sql`)
- Flyway only loads `db/seed` when the `local` profile (or tests) is active. Production migrations are in `db/migration`.
- Screens are not consistent with each other (Sara is "Onboarding" on one and has comp-off on another; the dashboard's tasks differ from the board's). The board and the employee list were followed. Aisha has five extra open tasks outside Sprint 23 so her "7 open tasks" holds.
- The employee code sequence restarts at 200 in the seed so new sign-ups do not clash with `EMP-0142` and `EMP-0157`.

## Frontend and login theme (prompt 02)
- Tokens: `design/tokens.css` is imported via the Vite alias `@design`; the Keycloak theme gets a copy through `scripts/sync-theme.sh`. Fonts are self-hosted (@fontsource) so no Google request is made.
- Tokens live in memory. After a full page reload the browser is sent through Keycloak again (instant with the SSO cookie). Only the short login `state` uses sessionStorage.
- The real logo (wordmark PNG) replaces the stand-in mark of the screens. In the light theme the navy-background PNG is used because the white text would vanish.
- Employees default tab: HR (employee:records) lands on All employees, a manager on My team, everyone else on Directory.
- The matrix shows permissions inherited from EMPLOYEE as ticked and not editable, because every person also holds EMPLOYEE; only the role's own permissions are saved.
- Sign-in method hints on the Roles screen are neutral ("Sign in with a Google account"). The screen's "Work domain only", "Needs HR approval" and "MFA required" are not configured in the realm, so they are not shown as facts.
- Keyboard moves on the board: Alt + Left/Right on a card's title. Mouse: drag and drop.
- Notifications open as a popover from the bell and mark one item read per click.

### Visual check (1440px screenshots in frontend/e2e/screenshots against design/screens)
Fixed after comparing: sidebar logo and group spacing; person names in tables showed grey; calendar cells outside the month were invisible; Parental bar was red (red means failure); AI page showed realm-internal roles and a bell the screen does not have; AI role chips; HR written "Hr"; login social buttons were alphabetical and "Keep me signed in" unchecked; matrix row labels were upper case and Sales showed none of the inherited EMPLOYEE permissions; sign-in methods order; Requests to approve now lists pending first and only the five latest decisions; Employees side panel width and toolbar wrapping; mobile sideways scroll caused by visually hidden text.
Left as is, with reasons:
- Date fields use the browser's native date picker (format follows the browser locale, "11/14/2026"), not "19 Nov 2026". Native pickers are accessible and keyboard friendly.
- Dummy data differs (13 people not 48, today's real date, different task assignees, "Overdue" instead of "Due today"). The code does what the data says.
- Team table order is the server's (by first name), screen 05 lists Karim before Hana.
- Login tabs are 44px high (touch target rule), the PNG shows 40px. The login email and password fields are empty, the PNG shows demo values.
- Screen 04 shows "Reset access", screen 09 "New role", the board "gitea.baracel.internal": no backend endpoint (see docs/backend-gaps.md). (Update: now fixed, the chip shows the host of `giteaUrl` from `GET /api/config`.)
- Matrix has extra rows the screen does not have (HR records, one row per knowledge collection, Roles and access) because each is a real permission.
- Task detail "Activity" is newest first (server order); the screen mixes order.
- AI header chip says "Simulated assistant · Phase 1" instead of "Ollama · local model".
- "Schedule" is disabled with a Phase 2 hint.

## Task and Gitea notifications
Besides leave, these create a bell notification and send an email (Mailpit locally) through the same notification service. The person who caused the event is never notified.
- Task assigned or reassigned (also at creation): the assignee.
- Comment on a task: the assignee and the reporter.
- Gitea review requested: the reviewer (matched by `gitea_username`).
- Gitea review approved or changes requested: the PR author and the task's assignee. Plain review comments do not notify.
- Pull request opened, or merged: the task's assignee and reporter. The text says when the task moved to In review or Done.
Recipients without a matching portal person (for example a Gitea user whose `gitea_username` is not set) are skipped.

## Backend gaps (prompt 03)
- **Project members.** Adding or removing is allowed with `employee:manage` or `access:manage` (HR, ADMIN), or for a manager (`employee:team`) who is a member of that project. The rule uses permissions, not role names. A person who is not a member gets 404 on the list (like the rest of the project); someone without the right to change members gets 403. Removing a member does not unassign their tasks.
- **Project repositories.** Repositories are linked to a project in `project_repository` (seed or database). There is still no endpoint to link a new one, and branch or PR calls no longer register an unknown repository on first use. This replaces the earlier "register on first use" behaviour.
- **Config.** `GET /api/config` needs only a signed-in, approved person. `gitea.public-url` is separate from `gitea.base-url` because inside Docker the two differ.
- **Reset access.** Sends Keycloak's execute-actions email (UPDATE_PASSWORD, VERIFY_EMAIL) and answers 204 with no body. "HR cannot do it for ADMIN users" is decided by permission: the target's roles are read from Keycloak and if they grant `access:manage`, the caller needs `access:manage` too. If Keycloak is unreachable the answer is 502 `keycloak-unavailable`.
- **Custom roles.** Stored in `custom_role` (migration V7) next to the Keycloak realm role. Names match `^[A-Z_]{3,30}$` and must not exist as a built-in, custom or Keycloak realm role. Delete works for custom roles only, and only when Keycloak reports no user with the role; it also removes the role's permission rows. A custom role is not offered in invite or approve (HR still assigns the four everyday roles; HR and ADMIN need `access:manage`), but it can be given permissions in the matrix and is honoured from the token. `GET /api/access/roles` now also returns `description` and `builtIn`.
- **Notifications.** The list response is now a page object. `size` above 100 is lowered to 100, below 1 raised to 1, and a negative `page` is treated as 0. This changes the response shape, so the frontend's bell must be updated.
- **MFA.** One shared `SignInMethodInfo` type is used by `/api/me` and the employee detail. OTP or WebAuthn (including passkeys) counts as ENABLED. The former `SignInMethod` and `SignInMethodDto` schemas are gone.
- **Required fields.** The OpenAPI customizer reads the Java records: a component is required unless annotated `@Nullable` (`com.baracel.portal.common.web.Nullable`). Nullable scalars become `type: [x, "null"]`. A nullable reference to another schema stays optional but is not marked nullable. Schemas whose every field is optional (six request bodies) carry no `required` list because the JSON writer drops an empty list. The contract test lists them explicitly.
- **Activity order.** The default stays newest first (`desc`), as before and as the server stores it. `asc` gives the oldest first, which suits a chronological reading of the feed. The ordering is by timestamp, then id, so entries written in the same millisecond keep their creation order. The parameter is case-insensitive; anything else is 400 `invalid-order`.
- **Keycloak permission for roles.** Creating and deleting realm roles needs the `manage-realm` role for the `portal-service` account (added to `baracel-realm.json`; on an already imported realm assign it in Keycloak under Clients, portal-service, Service account roles, or reset the Keycloak database). Tests use a fake Keycloak, so this was found by a live check.
- **Local migrations.** The demo seed is Flyway version 1000, so migration V7 on an already seeded local database needs `spring.flyway.out-of-order=true`; it is set in `application-local.yml` only.

## Frontend follow-up (prompt 04)
- **Activity order.** The task detail keeps the activity feed newest first and asks for it explicitly (`activityOrder=desc`). The sample screen `design/screens/07-task-detail.png` lists the entries in a mixed order that is only illustrative; `design/` is read-only and was not changed. The backend also supports `asc` if a chronological view is wanted later.
- **Toasts.** One `ToastProvider` shows short success and error messages for reset access, roles, members, branch and PR actions, invitations and approvals. Errors stay until closed. Field errors from the server (for example an invalid role name) are shown next to the field as well.
- **Members actions** are offered to people with `employee:manage`, `access:manage` or `employee:team`; the server still decides (a manager outside the project gets 403, shown as an error toast).
- **Gitea links** (Open in Gitea, PR and commit links, the host chip) are built from `giteaUrl` in `GET /api/config`, not from URLs stored by the backend.
- **MFA** shows "MFA on", "MFA off" or "Managed by provider", each with its own icon and text.
- **Notifications** use pages of 20 with a Load more button, an Unread only switch and Mark all as read. The badge uses the server's `unreadCount`.

## AI service (prompt 05)
- **Optional scopes.** `ai:invoke` and `ai:ingest` are *optional* client scopes of `portal-backend-ai`, so Spring asks for the scope it needs and a chat token cannot manage documents. The realm file now lists `clientScopes`, because Keycloak drops its built-in scopes (profile, roles, ...) when a realm import contains only some; the built-in definitions were exported from Keycloak 26.8.2 and copied in.
- **Existing Keycloak volumes** are not changed by a realm file edit (the import skips an existing realm). `scripts/apply-ai-client.sh` adds the scopes and client through the Admin API. Nothing was dropped or reset.
- **Ingest scope on reads.** All `/v1/collections*` and `/v1/jobs` routes, including GET, need `ai:ingest` (they expose document titles and counts).
- **Intent detection** in `auto` mode is a keyword check (post, ad, caption, LinkedIn, Instagram, Facebook, social media, announcement); the caller can force `answer` or `social_post`. One fewer model call per message.
- **Draft streaming.** A social draft is validated against its JSON schema and the platform limit before anything is sent, so it is not streamed token by token: one intro token, then `sources`, `draft`, `done`.
- **Similarity threshold 0.45.** With bge-m3 unrelated text scored about 0.37 and related text 0.5 to 0.7. At 0.30 an unrelated brand chunk was passed to the model and it answered from general knowledge; 0.45 plus a stricter prompt gives a clear "I do not know".
- **Token estimate.** Chunk sizes use about 4 characters per token, so chunking does not depend on a model tokenizer or a download.
- **Failed documents** stay in the `document` table with status FAILED and a short reason, so the admin screen can show them.
- **Hosts without a GPU in Docker.** The compose file reserves the GPU for `ollama` and `ai-service`; `docker-compose.cpu.yml` removes the reservation. On the development machine the NVIDIA Container Toolkit is not installed, so the verification run used host Ollama and the service from source (see the report).
- **authlib.jose** is used for JWT checks; it prints a deprecation notice (joserfc is its successor). Moving is a small follow-up.

## Spring: AI service, knowledge base and LinkedIn (prompt 06)
- **Property names.** The mode stays `portal.ai.mode` (`stub` or `http`, env `AI_MODE`), with the service settings under `portal.ai.service.*`. The LinkedIn switches are `social.linkedin.enabled` and `social.linkedin.company-enabled` as asked.
- **Collection access is the permission matrix.** "Roles with access to a collection" are the roles holding the permission `ai:knowledge:<code>`. One source of truth: the Roles and access screen and the Knowledge base screen edit the same rows, and the permission cache is cleared on every change. No separate `role_collection` table. ADMIN always keeps access. Creating a collection creates its permission; deleting removes it. The code of the HR collection is now `hr-policies` (migration V8); a new collection code must start with a letter because the AI service requires it.
- **Own circuit breaker.** A 40-line breaker (5 failures in a row open it for 30 s, then one trial call) instead of a library, because Resilience4j had no Spring Boot 4 starter at the time. It covers chat and the knowledge calls. A 4xx from the AI service counts as "the service answered"; a rejected service token (401 or 403) is treated as unavailable and logged as a configuration error.
- **OAuth2 client.** Spring Security's OAuth2 client with the client credentials grant, two registrations of the same Keycloak client (`ai:invoke` for chat, `ai:ingest` for knowledge management), so a chat call never carries the ingest scope. Spring caches the token and renews it before it expires.
- **Streaming.** One `WebClient` (webflux) reads the upstream stream; the rest of the application stays MVC and the browser gets an `SseEmitter`. The relay saves in its own transactions, because it runs on other threads. When the browser disconnects the subscription is disposed, which closes the upstream connection. The draft event is held back until the draft is saved so the browser gets its id. In stub mode the same events are simulated so the frontend has one contract.
- **No HTTP/2 upgrade.** The JDK HTTP client is pinned to HTTP/1.1 for the AI service and LinkedIn; the cleartext upgrade attempt broke against test servers and uvicorn does not speak h2c.
- **Publishing states.** IN_PROGRESS exists only inside the database (a crashed request leaves it, it is reported as UNKNOWN and never retried). A 5xx from LinkedIn, a timeout, a reset connection and a 201 without a post id are UNKNOWN, because the post may exist. 401 is FAILED TOKEN_EXPIRED (the connection becomes EXPIRED), 429 RATE_LIMITED, 400/403/422 REJECTED, any other 4xx UNKNOWN_ERROR. A definite FAILED may be tried again (one claim wins when two requests race); everything else returns the first result.
- **LinkedIn facts used (checked against the official documentation on 2026-11-15).** Authorisation `https://www.linkedin.com/oauth/v2/authorization`, token `https://www.linkedin.com/oauth/v2/accessToken`, member id from `GET https://api.linkedin.com/v2/userinfo` (scopes `openid profile`; `sub` is the id, the author is `urn:li:person:{sub}`); personal posting needs `w_member_social`, company posting `w_organization_social` (author `urn:li:organization:{id}`; the member must be ADMINISTRATOR, found with `GET /rest/organizationAcls?q=roleAssignee&role=ADMINISTRATOR&state=APPROVED`, scope `r_organization_admin`). Posts: `POST https://api.linkedin.com/rest/posts` with `Linkedin-Version: YYYYMM` and `X-Restli-Protocol-Version: 2.0.0`, answer 201 with the post URN in `x-restli-id`; post URL `https://www.linkedin.com/feed/update/{urn}/`. Access tokens last 60 days; refresh tokens exist only for approved partners, so a connection without one becomes EXPIRED and the person reconnects. The Posts API version defaults to `202610` (version 202511 is retired on 2026-11-16). The commentary uses LinkedIn's "little" text format: the characters `| { } @ [ ] ( ) < > # \ * _ ~` are escaped with a backslash and hashtags are written as `#word`. LinkedIn does not accept a post over 3000 characters (checked after escaping).
- **Not done.** Revoking the token at LinkedIn on disconnect (the token is only wiped here), media posts, scheduling, Instagram and Facebook (COMING_LATER), and removing the personal channel rows created on first use.
- **Callback is public.** `GET /api/social/linkedin/callback` has no bearer token (the browser comes from LinkedIn). The one-time state, bound to the person who started the connect, is the only credential; a wrong, replayed or expired state is refused without calling LinkedIn.
- **Secrets.** The LinkedIn client secret was found in DEBUG logs of Spring's form converter during tests; the token request is now sent as pre-encoded bytes so it is not logged. A test searches all log output and all API responses for tokens.

## Frontend: AI, publishing, knowledge base, accounts (prompt 07)
- **fetch for the stream.** `EventSource` cannot send an Authorization header, so the chat uses `fetch` and a small parser (`sse.ts`). Leaving the page or pressing Stop aborts the request; the backend then saves the partial answer as STOPPED and the page shows it from the server.
- **Retry sends the question again**, so after an interrupted answer the conversation holds the question twice. Keeping one copy would need an endpoint to retry a message; not asked for.
- **Publish needs a confirmation** that shows the exact text, and unsaved edits are saved first, so what was confirmed is what is posted. A double click sends one request (a lock in the component, and the backend is idempotent as well). After PUBLISHED, SIMULATED or UNKNOWN the draft becomes read-only and that channel cannot be picked again; after a definite FAILED it can.
- **A person without a publish permission sees no picker**, only Copy text, instead of a picker full of disabled channels.
- **Knowledge base role chips** use the role list from `GET /api/access/roles` (all roles, custom ones too) and fall back to the six built-in roles when that endpoint is refused (see `docs/backend-gaps.md`). ADMIN is always on, like on the server.
- **Upload progress** uses `XMLHttpRequest`, because `fetch` cannot report upload progress. The 25 MB limit is a constant that mirrors `AI_MAX_UPLOAD_MB`; the server still decides.
- **Polling** is React Query's `refetchInterval` (3 s) while the activity list holds a QUEUED or RUNNING job. It stops by itself when the job ends or the page is left.

### Visual check against screens 08, 10 and 11 (1440 px)
Compared `e2e/screenshots/08-ai-assistant-aisha.png`, `10-knowledge-base-daniel.png`, `11-social-accounts-aisha.png` and `11-social-accounts-connected-aisha.png` with the PNGs in `design/screens/`.
| Screen | Difference | Decision |
|---|---|---|
| 08 | The "Ollama · local model" badge is missing | No endpoint tells the model (`docs/backend-gaps.md`). The old "Simulated assistant · Phase 1" pill is gone. |
| 08 | The draft card has editable fields under the art, the design shows static text | Asked for in the prompt (edit before publishing). The art keeps the sample look and follows the headline. |
| 08 | The right panel lists every channel with its state; the design shows "Company accounts (3)" and "Personal accounts" | More honest now that channels have their own state (Coming later, Simulated, Not connected). |
| 08 | No Schedule button | Removed as asked. |
| 08 | Long conversation titles are cut with an ellipsis (full title as tooltip) | The first version wrapped over the next item. A real bug found by the screenshot. |
| 10 | Role chips and role toggles are 44 px high (the design about 30 px) | The 44 px touch target rule in BRAND.md. |
| 10 | The right panel groups documents by source type ("4 PDF files"), the design lists a Drive folder, PDFs and a website with sync times | The AI service has no per-source record (`docs/ai-service-gaps.md`); the website rows are shown per URL. |
| 10 | The table shows "No documents" for an empty collection | The design has no empty collection. |
| 11 | In development (LinkedIn switched off) the cards say Simulated and have no Connect button | Connecting is refused by the backend then (`409 linkedin-disabled`). The connected look is checked with `11-social-accounts-connected-aisha.png` (API answers faked in the browser). |
| 11 | "Access renews automatically" is not claimed | LinkedIn gives refresh tokens only to approved partners; the card says when access expires. |

## Local Kubernetes with kind (prompt 10)
- **ingress-nginx is retired.** The Kubernetes project archived it on 2026-03-24 (no more releases or security fixes). The task asks for it, so the cluster uses the last release, controller v1.15.1, vendored unchanged in `infra/k8s/ingress-nginx/` with its digests. It is acceptable for an HTTP-only laptop cluster and must not be exposed. Only the Ingress objects and one step of `kind-up.sh` would change for a Gateway API controller.
- **CoreDNS rewrite, not hostAliases**, makes `auth.baracel.local`, `portal.baracel.local` and `git.baracel.local` resolve to the ingress controller inside the cluster: one place, covers every pod (also Gitea calling Keycloak), needs no IP address.
- **One issuer, `http://auth.baracel.local/realms/baracel`**, for the browser, backend, ai-service, Gitea and Keycloak (`KC_HOSTNAME`, `KC_PROXY_HEADERS=xforwarded`, `KC_HTTP_ENABLED`). HTTP only, said in the manifests and in `docs/kind.md`.
- **The realm file is not edited.** The Keycloak image build rewrites the `portal-web` and `gitea` URLs (`localhost:5173` to `portal.baracel.local`, `localhost:3000` to `git.baracel.local`) with `sed` and fails the build if a `localhost` is left. The theme (208 KB) is baked into the same thin image, so no ConfigMap is needed.
- **Frontend runtime config.** The application reads `VITE_*` values at build time and the task forbids changing application code. The image is therefore built with placeholders (`__VITE_API_URL__` …) and an nginx start hook replaces them from the container environment and writes `/config.json` (a copy for inspection; the app does not read it yet). One image works anywhere. Making `src/env.ts` read `config.json` would be a small follow-up in the application.
- **Backend profile `local`** in the cluster, so the demo seed matches the Keycloak users; without it every sign-in would be a pending approval. It is the local overlay only.
- **Embedding model on a volume.** The slim ai-service image has CPU-only torch (the locked version from the CPU index, about 1.8 GB image) and no model. `prefetch_model.py` downloads BAAI/bge-m3 into the `ai-models` volume with a progress line every 5 seconds, then the service runs with `HF_HUB_OFFLINE=1`. The startup probe allows 30 minutes.
- **Secrets** are created from `infra/.env` through temporary env files (mode 600), never on a command line or in output. `kind-secrets.sh` refuses empty values and `change-me` placeholders. `kind-init-env.sh` writes a random `.env` when none exists. The Gitea token is created after Gitea starts and patched into the backend Secret by `kind-gitea.sh`.
- **Mailpit** is in the cluster although the task did not list it: Keycloak, the backend and Gitea are configured to send mail, and without it invitations and notifications would fail. It has no Ingress.
- **`enableServiceLinks: false` on chroma.** A Service named `chroma` injects `CHROMA_PORT=tcp://…`, which Chroma reads as its own PORT setting and panics on.
- **LLM modes.** The overlay default is `ollama` on the Docker host through a Service and Endpoints (`OLLAMA_HOST_ADDR`, found from the docker network gateway). `KIND_LLM_PROVIDER=fake` switches to the fake provider; `kind-smoke.sh` states the mode and reports the Ollama check as SKIP, never PASS, when it did not test Ollama.
