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
