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
