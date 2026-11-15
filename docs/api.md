# Baracel portal API (Phase 1)

Base URL `http://localhost:8080`. Machine-readable contract: [`openapi.json`](openapi.json) (regenerate: see `docs/run.md`).

**Auth.** Every endpoint needs `Authorization: Bearer <Keycloak access token>` (audience `portal-api`), except
`GET /actuator/health` and `POST /api/integrations/gitea/webhook` (HMAC signature instead).
Errors are RFC 7807 `application/problem+json`; `type` is `https://portal.baracel.example/problems/<slug>`
(`pending-approval`, `forbidden`, `not-found`, `validation`, `invalid-signature`, `insufficient-balance`, `leave-overlap`, ...).
A person whose status is `PENDING_APPROVAL` can only call `GET /api/me`; everything else answers 403 `pending-approval`.
Permission strings below (for example `leave:approve`) are checked by the server. Hide UI from `GET /api/me` → `permissions`.
In `openapi.json` every response and request field is `required` unless it is documented as nullable or optional (type `[x, "null"]`).
Dates are ISO `yyyy-MM-dd`, timestamps are UTC instants. Paging is `page` (from 0) and `size` (max 100).

> Prompt 03 added: project members and repositories, `/api/config`, reset access, custom roles, paged notifications with read-all, MFA state, required fields in the contract, and `activityOrder`. They are marked **new** below.

## Every screen (sidebar, header)
| Endpoint | Notes |
|---|---|
| `GET /api/me` | id, code, name, email, jobTitle, department, status, `roles`, `permissions`, manager `{id,name}`, `signInMethods` (google, github, facebook, password: `{key,label,linked,mfa}` with `mfa` = `ENABLED`, `DISABLED` or `PROVIDER_MANAGED`, read from the Keycloak Admin API), `unreadNotifications` |
| `GET /api/config` **new** | any approved signed-in person: `{appName, giteaUrl, leaveTypes[{code,name,defaultDays}]}`. No secrets |
| `GET /api/notifications?unread=&page=&size=` **new shape** | `{items, total, unreadCount, page, size}`; `size` default 20, max 100; newest first |
| `POST /api/notifications/{id}/read` | |
| `POST /api/notifications/read-all` **new** | marks only your own as read, `{updated}` |

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
| `POST /api/employees/{id}/reset-access` **new** | `employee:manage`. Sends Keycloak's UPDATE_PASSWORD + VERIFY_EMAIL mail, 204, audited. Only `access:manage` may do it for an administrator |
| `POST /api/employees/{id}/approve` | `employee:manage` | `{role, managerId?, jobTitle?, department?}` → ACTIVE and Keycloak role. HR may assign EMPLOYEE, SALES, ENGINEER, MANAGER; HR and ADMIN need `access:manage` |

## Tasks (06, 07)
| Endpoint | Permission | Notes |
|---|---|---|
| `GET /api/projects` | `task:view` | only projects you are a member of |
| `GET /api/projects/{key}/members` **new** | `task:view`, caller is a member | `[{id,name,jobTitle,initials}]` |
| `POST /api/projects/{key}/members` **new** | `employee:manage`, `access:manage`, or a manager (`employee:team`) who is a member | `{employeeId}`; 201 with the new list; 409 `already-a-member`; audited |
| `DELETE /api/projects/{key}/members/{employeeId}` **new** | same | 204; audited |
| `GET /api/projects/{key}/repos` **new** | `task:view`, caller is a member | repositories linked to the project: `[{projectId,fullName,defaultBranch,htmlUrl}]` |
| `GET /api/projects/{key}/board?sprint=` | `task:view` | `columns[BACKLOG, IN_PROGRESS, IN_REVIEW, DONE]` with cards `{task, git}`, `taskCount`, `linkedToGitea`, `lastSyncedAt`. Default sprint is the active one |
| `GET /api/tasks?assignee=me&dueBefore=&open=true` | `task:view` | |
| `GET /api/tasks/{key}?activityOrder=asc\|desc` | `task:view` | `{task{subtasks, comments, activity, reporter, sprint}, git{webhookLive, links, commits}}`. Activity is newest first by default (`desc`) |
| `POST /api/tasks` | `task:create` | `{projectKey, title, description?, priority?, assigneeId?, dueDate?, label?, sprintId?}` → key `BAR-158` |
| `PATCH /api/tasks/{key}` | `task:edit` | |
| `PATCH /api/tasks/{key}/status` | `task:edit` | `{status}` |
| `POST /api/tasks/{key}/subtasks`, `PATCH/DELETE .../subtasks/{id}` | `task:edit` | |
| `POST /api/tasks/{key}/comments` | `task:edit` | |
| `POST /api/tasks/{key}/git/branch` | `task:edit` | `{repo?}` (must be linked to the task's project, else 400 `repo-not-linked`) creates `feature/bar-142-slug` from the default branch (502 `gitea-unavailable` if Gitea fails) |
| `POST /api/tasks/{key}/git/link-pr` | `task:edit` | `{repo, prNumber}` |
| `GET /api/repos?refresh=` | `repo:view` | repositories of your projects |
| `POST /api/integrations/gitea/webhook` | none (HMAC) | `X-Gitea-Signature` = hex HMAC-SHA256 of the raw body with `GITEA_WEBHOOK_SECRET`; push, create, pull_request*, review and status events |

Non-members get 404 for a project or task, so names do not leak.

## AI assistant (08)
`portal.ai.mode=stub` (default, canned answers) or `http` (the FastAPI service, `docs/ai-service.md`). The endpoints are the same in both modes. All need `ai:chat`.

| Endpoint | Notes |
|---|---|
| `GET /api/ai/conversations`, `POST /api/ai/conversations` | your own conversations |
| `GET /api/ai/conversations/{id}` | messages with `status` (COMPLETE, STOPPED or FAILED), `sources[{title, collection, page?, sourceRef?}]` and `draft?` |
| `POST /api/ai/conversations/{id}/messages` | `{message}`. Without an `Accept: text/event-stream` header: JSON `{userMessage, assistantMessage}`. With it: server-sent events (below). 20 messages per minute per person, then `429 rate-limited` with `Retry-After`. `503 ai-unavailable` when the AI service is down, slow or its circuit breaker is open. Chat calls are never retried automatically |
| `POST /api/ai/messages/{id}/feedback` | `{rating: UP or DOWN, comment?}` on an assistant answer of your own conversation; one per person and message (sending again replaces it) |
| `PATCH /api/ai/drafts/{id}` | the draft owner edits `{headline?, caption?, hashtags?}`. Platform limits: a LinkedIn caption has at most 3000 characters (`400 caption-too-long`). `409 draft-published` once it was published |
| `GET /api/ai/access` | `roles`, `collections[{code,name,docCount,allowed}]`, `channels[{code,name,type,provider,connected,canPublish,status,accountName?}]`, `companyChannels`, `personalChannels`. Channel `status`: CONNECTED, SIMULATED (LinkedIn switched off, local development), NOT_CONNECTED, EXPIRED, NOT_AVAILABLE (company page not enabled yet), COMING_LATER (Instagram, Facebook) |
| `POST /api/ai/drafts/{id}/publish` | `{channels:[codes]}`. See Publishing below |

**Streaming events** (`Accept: text/event-stream`), in this order: `token` `{text}` (many), `sources` `{sources}`, `draft` `{draft}` (social posts, sent after it was saved so it has its `id`), `done` `{conversationId, userMessage, assistantMessage}` (the saved messages, with ids), or `error` `{code, detail}` (for example `ai-unavailable`). The final answer, its sources and its draft are saved when `done` arrives. If the browser disconnects first, the upstream call is cancelled and the text received so far is saved with status STOPPED. An open circuit breaker answers a plain `503 ai-unavailable` before the stream starts. In stub mode the same events are simulated.

`allowed_collections` is computed by Spring from the caller's permissions (`ai:knowledge:<code>`) on every call: Aisha (SALES) gets `product, brand, campaign`; Lena (HR) `hr-policies`; Priya (ENGINEER) `engineering`. The last 10 messages are sent as history.

## Knowledge base (10), `ai:knowledge:manage` (ADMIN)
Spring owns the catalogue and who may use each collection; the AI service owns documents and indexing. Everything below returns 403 without the permission.

| Endpoint | Notes |
|---|---|
| `GET /api/knowledge/collections` | `{aiServiceAvailable, collections[{code,name,documents,chunks,status,failedDocuments,lastSync,sourceTypes[],roles[],allEmployees}]}`. Status READY, INDEXING, FAILED, EMPTY or UNKNOWN (AI service down: the last known counts are shown) |
| `POST /api/knowledge/collections` | `{code, name}`; code 3 to 40 characters, lowercase letters, digits and hyphens, starting with a letter; never changes. 201, creates the permission `ai:knowledge:<code>` (ADMIN only), audit logged |
| `PUT /api/knowledge/collections/{code}/roles` | `{roles:[...]}` replaces the roles that may search the collection (ADMIN always stays). Audit logged, clears the permission cache |
| `DELETE /api/knowledge/collections/{code}` | only when the AI service holds no documents (`409 collection-not-empty`) |
| `POST /api/knowledge/collections/{code}/documents` | multipart `file` (PDF), streamed through, 25 MB limit (`413 file-too-large`). 202 `{jobId, documentId, status}` |
| `POST .../sources/drive` | `{folderId, recursive?, force?}` → 202 |
| `POST .../sources/web` | `{urls[], followLinks?, maxPages?, force?}` → 202. Same domain only (422 `different-domain`) |
| `POST .../reindex` | 202 |
| `GET .../documents`, `DELETE .../documents/{id}` | documents of the collection with status and error |
| `GET /api/knowledge/jobs/{id}` | `{id, collection, kind, status, progress, error, failures[], createdAt, finishedAt}`. A finished job refreshes `knowledge_collection.doc_count` |
| `GET /api/knowledge/activity` | the last 20 jobs started here: label, who started it, status and progress (the AI service has no job list; see `docs/ai-service-gaps.md`) |

AI service problems pass through with their status and type (for example 422 `different-domain`); an unreachable service is `503 ai-unavailable`.

## Connected accounts and publishing (11)
| Endpoint | Notes |
|---|---|
| `GET /api/social/connections` | your own connections plus the company one if you may use it: `[{id, provider, type PERSONAL or COMPANY, displayName, status CONNECTED/EXPIRED, expiresAt, profileUrl, canDisconnect, canReconnect}]`. Never any token |
| `POST /api/social/linkedin/connect?target=personal\|company` | `{authorizationUrl, state}`. Personal needs `social:publish:personal`; company needs `access:manage`. `409 linkedin-disabled` / `linkedin-company-disabled` when the switch is off. The state is random, bound to you, single use and valid for 10 minutes |
| `GET /api/social/linkedin/callback` | LinkedIn sends the browser here (no bearer token). Redirects to `<frontend>/accounts?linkedin=connected\|cancelled\|error&target=personal\|company[&reason=invalid-state\|no-organization\|linkedin-error]` |
| `DELETE /api/social/connections/{id}` | your own; the company one needs `access:manage`. Someone else's is 404. The tokens are wiped |
| `GET /api/social/history?page=&size=` | your publish log: `{items[{id,createdAt,channel,channelName,headline,status,reason,message,postUrl}], page, size, total}` |

**Publishing** `POST /api/ai/drafts/{id}/publish` `{channels}` returns one result per channel: `{channel, status, reason?, message?, postUrl?}`.
* `PUBLISHED` (with `postUrl`), `FAILED` (`reason` TOKEN_EXPIRED, RATE_LIMITED, REJECTED or UNKNOWN_ERROR), `SIMULATED` (LinkedIn switched off: nothing is sent) or `UNKNOWN` (timeout or unclear answer: check LinkedIn; never retried automatically).
* One result per (draft, channel): asking again returns the first result, so a double click posts once. After a definite `FAILED` (nothing was posted) a new attempt is allowed.
* `403` when you lack `social:publish:company` / `social:publish:personal` or the channel is someone else's personal one; `404` for a draft that is not yours; `409 channel-not-connected`; `422 channel-not-available` for Instagram and Facebook (status COMING_LATER) and for the company page while it is not enabled. All channels are checked before any is published.

## Roles and access (09), `access:manage` (ADMIN)
| Endpoint | Notes |
|---|---|
| `GET /api/access/roles` | `[{name, description, builtIn, people}]` (people from Keycloak); built-in roles first, then custom ones |
| `POST /api/access/roles` **new** | `{name, description?}`. Name: capital letters and underscores, 3 to 30 characters, not used yet (400 `invalid-role-name`, 409 `role-exists`). Creates the Keycloak realm role and an empty permission set. 201, audited |
| `DELETE /api/access/roles/{role}` **new** | custom roles only (400 `built-in-role` otherwise) and only when nobody has the role (409 `role-in-use`). 204, audited |
| `GET /api/access/roles/{role}/matrix` | `permissions[{key, label, hint, group, row, column(VIEW\|CREATE\|EDIT\|APPROVE\|PUBLISH), granted}]` |
| `PUT /api/access/roles/{role}/matrix` | `{permissions:[keys]}`, audit logged, clears the permission cache |
| `GET /api/access/identity-providers`, `PUT ...` | `[{alias, displayName, enabled, editable}]`; PUT `{providers:[{alias, enabled}]}` through the Keycloak Admin API |
| `GET /api/access/settings` | `{defaultRoleForNewSignUps, approver, sessionLengthHours}` (read-only) |
