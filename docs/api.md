# Baracel portal API (Phase 1)

Base URL `http://localhost:8080`. Machine-readable contract: [`openapi.json`](openapi.json) (regenerate: see `docs/run.md`).

**Auth.** Every endpoint needs `Authorization: Bearer <Keycloak access token>` (audience `portal-api`), except
`GET /actuator/health` and `POST /api/integrations/gitea/webhook` (HMAC signature instead).
Errors are RFC 7807 `application/problem+json`; `type` is `https://portal.baracel.example/problems/<slug>`
(`pending-approval`, `forbidden`, `not-found`, `validation`, `invalid-signature`, `insufficient-balance`, `leave-overlap`, ...).
A person whose status is `PENDING_APPROVAL` can only call `GET /api/me`; everything else answers 403 `pending-approval`.
Permission strings below (for example `leave:approve`) are checked by the server. Hide UI from `GET /api/me` → `permissions`.
Dates are ISO `yyyy-MM-dd`, timestamps are UTC instants. Paging is `page` (from 0) and `size` (max 100).

## Every screen (sidebar, header)
| Endpoint | Notes |
|---|---|
| `GET /api/me` | id, code, name, email, jobTitle, department, status, `roles`, `permissions`, manager `{id,name}`, `signInMethods` (google, github, facebook, password with `linked` and `mfa`, read from the Keycloak Admin API), `unreadNotifications` |
| `GET /api/notifications` | latest 50 |
| `POST /api/notifications/{id}/read` | |

## Dashboard (02)
| Endpoint | Notes |
|---|---|
| `GET /api/dashboard` | `annualLeave{left,entitled}`, `openTasks{count,dueThisWeek}`, `nextDue[4]`, `pullRequestsAwaitingReview`, `outToday[{name,typeName,to,backOn}]`, `announcements[5]` |
| `GET /api/announcements` | |
| `POST /api/announcements` | `employee:manage` (HR) `{title, body?, postedBy?}` |

## Leave (03)
| Endpoint | Permission | Notes |
|---|---|---|
| `GET /api/leave/balances` | `leave:request` | per type: entitled, used, pending, left (Unpaid has no limit) |
| `GET /api/leave/preview?type=&from=&to=` | `leave:request` | the "Working days / Balance after / Overlaps" box |
| `POST /api/leave/requests` | `leave:request` | `{type: ANNUAL\|SICK\|COMP_OFF\|PARENTAL\|UNPAID, from, to, reason?}`. 400 `invalid-dates`, `no-working-days`; 409 `leave-overlap`, `insufficient-balance` |
| `DELETE /api/leave/requests/{id}` | `leave:request` | cancels your own PENDING request |
| `GET /api/leave/requests?scope=mine\|approvals&status=` | `leave:request`; approvals need `leave:approve` | approvals: a manager sees direct reports, anyone with `employee:records` (HR) sees all. `status` is `PENDING` (default), `ALL` or one status (the screen's history rows use `ALL`) |
| `POST /api/leave/requests/{id}/approve` `/reject` | `leave:approve` | `{note?}`. Managers only for direct reports, nobody for their own request |
| `GET /api/leave/calendar?month=2026-11` | `leave:request` | pending and approved leave of the caller's department |

## Employees (04, 05)
| Endpoint | Permission | Notes |
|---|---|---|
| `GET /api/employees?scope=directory&q=&status=&department=&page=&size=` | `employee:directory` | name, jobTitle, department, email only |
| `GET /api/employees?scope=team` | `employee:team` | only the caller's direct reports (from the token). Adds code, role, status, joinedOn, `annualLeaveLeft` |
| `GET /api/employees?scope=all` | `employee:records` | everyone, full records, plus `statusCounts` for the chips (All, Active, On leave, Onboarding) |
| `GET /api/employees/{id}` | self, direct report, or `employee:records` | `{employee, roles?, signInMethods?, recentLeave}`; `roles` and `signInMethods` only for HR |
| `PATCH /api/employees/{id}` | `employee:manage` | jobTitle, department, managerId, phone, location, giteaUsername, status, joinedOn |
| `POST /api/employees/invite` | `employee:manage` | creates the employee as ONBOARDING and the Keycloak user (VERIFY_EMAIL + UPDATE_PASSWORD email) |
| `POST /api/employees/{id}/approve` | `employee:manage` | `{role, managerId?, jobTitle?, department?}` → ACTIVE and Keycloak role. HR may assign EMPLOYEE, SALES, ENGINEER, MANAGER; HR and ADMIN need `access:manage` |

## Tasks (06, 07)
| Endpoint | Permission | Notes |
|---|---|---|
| `GET /api/projects` | `task:view` | only projects you are a member of |
| `GET /api/projects/{key}/board?sprint=` | `task:view` | `columns[BACKLOG, IN_PROGRESS, IN_REVIEW, DONE]` with cards `{task, git}`, `taskCount`, `linkedToGitea`, `lastSyncedAt`. Default sprint is the active one |
| `GET /api/tasks?assignee=me&dueBefore=&open=true` | `task:view` | |
| `GET /api/tasks/{key}` | `task:view` | `{task{subtasks, comments, activity, reporter, sprint}, git{webhookLive, links, commits}}` |
| `POST /api/tasks` | `task:create` | `{projectKey, title, description?, priority?, assigneeId?, dueDate?, label?, sprintId?}` → key `BAR-158` |
| `PATCH /api/tasks/{key}` | `task:edit` | |
| `PATCH /api/tasks/{key}/status` | `task:edit` | `{status}` |
| `POST /api/tasks/{key}/subtasks`, `PATCH/DELETE .../subtasks/{id}` | `task:edit` | |
| `POST /api/tasks/{key}/comments` | `task:edit` | |
| `POST /api/tasks/{key}/git/branch` | `task:edit` | `{repo?}` creates `feature/bar-142-slug` from the default branch (502 `gitea-unavailable` if Gitea fails) |
| `POST /api/tasks/{key}/git/link-pr` | `task:edit` | `{repo, prNumber}` |
| `GET /api/repos?refresh=` | `repo:view` | repositories of your projects |
| `POST /api/integrations/gitea/webhook` | none (HMAC) | `X-Gitea-Signature` = hex HMAC-SHA256 of the raw body with `GITEA_WEBHOOK_SECRET`; push, create, pull_request*, review and status events |

Non-members get 404 for a project or task, so names do not leak.

## AI assistant (08), Phase 1 stub
| Endpoint | Notes |
|---|---|
| `GET /api/ai/conversations`, `POST /api/ai/conversations` | your own conversations |
| `GET /api/ai/conversations/{id}` | messages with `sources` and `draft` |
| `POST /api/ai/conversations/{id}/messages` | `{message}` → `{userMessage, assistantMessage{text, sources[{title,collection}], draft?}}`. A message containing "ad" or "post" returns the sample draft |
| `GET /api/ai/access` | right-hand panel: `roles`, `collections[{code,name,docCount,allowed}]`, `channels[{code,type,connected,canPublish}]`, `companyChannels`, `personalChannels` |
| `POST /api/ai/drafts/{id}/publish` | `{channels:[codes]}`. Needs `social:publish:company` or `social:publish:personal` per channel. Phase 1 only writes `publish_log` with status `SIMULATED` |

All AI endpoints need `ai:chat`.

## Roles and access (09), `access:manage` (ADMIN)
| Endpoint | Notes |
|---|---|
| `GET /api/access/roles` | `[{name, people}]` (people from Keycloak) |
| `GET /api/access/roles/{role}/matrix` | `permissions[{key, label, hint, group, row, column(VIEW\|CREATE\|EDIT\|APPROVE\|PUBLISH), granted}]` |
| `PUT /api/access/roles/{role}/matrix` | `{permissions:[keys]}`, audit logged, clears the permission cache |
| `GET /api/access/identity-providers`, `PUT ...` | `[{alias, displayName, enabled, editable}]`; PUT `{providers:[{alias, enabled}]}` through the Keycloak Admin API |
| `GET /api/access/settings` | `{defaultRoleForNewSignUps, approver, sessionLengthHours}` (read-only) |
