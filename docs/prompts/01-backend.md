Follow CLAUDE.md. Build Phase 1 of the Baracel Employee Portal backend, the Keycloak realm and the local infrastructure. Do NOT build the frontend or the Keycloak login theme (the next prompt does). First read design/BRAND.md and look at every PNG in design/screens/, so each API returns exactly the data its screen shows.

Keep docs/progress.md as a checklist of the 10 steps below, work step by step, and commit after each step.

1. INFRASTRUCTURE (infra/)
- docker-compose.yml with: PostgreSQL 16 (databases portal, keycloak, gitea), Keycloak (latest stable 26.x image, start-dev, port 8180, realm imported from keycloak/realm/, keycloak/themes mounted at /opt/keycloak/themes), Gitea (port 3000), Mailpit (SMTP 1025, UI 8025) used by Keycloak and Spring for mail. Add healthchecks.
- infra/.env.example with: GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, FACEBOOK_CLIENT_ID, FACEBOOK_CLIENT_SECRET, KEYCLOAK_ADMIN, KEYCLOAK_ADMIN_PASSWORD, PORTAL_SERVICE_CLIENT_SECRET, GITEA_TOKEN, GITEA_WEBHOOK_SECRET. Ignore .env in git.

2. KEYCLOAK REALM (keycloak/realm/baracel-realm.json, secrets as ${ENV_VAR} placeholders)
- Realm baracel. Login theme baracel (the folder arrives in the next prompt; document the fallback). Registration, verify email, forgot password and brute-force protection on. Mail goes to Mailpit.
- Realm roles: EMPLOYEE (default role), MANAGER, HR, SALES, ENGINEER, ADMIN.
- Clients: portal-web (public, Authorization Code + PKCE, redirect http://localhost:5173/*, web origin http://localhost:5173), portal-service (confidential, service account allowed to view and manage users and read the realm, used by Spring for the Admin API), portal-dev-cli (public, direct access grants, for the dev token script only, mark it dev-only).
- Identity providers google, github, facebook, with keys from env vars. First-login flow: create the user without a profile review page, and give them the EMPLOYEE role.
- Tokens carry realm roles, email, name, preferred_username, and audience portal-api.
- Dev users, password ChangeMe123! (dev only, note in docs/run.md that they must be removed for production). Roles: aisha.rahman SALES; omar.haddad MANAGER; ruth.mensah MANAGER; lena.fischer HR; daniel.okoye ADMIN, ENGINEER, MANAGER; priya.nair, tom.becker ENGINEER; sara.qureshi, mei.tan EMPLOYEE; karim.aziz, nadia.hussein, joel.mathews, hana.saleh SALES.

3. SPRING BOOT PROJECT (backend/)
- Maven, Java 21, package com.baracel.portal, port 8080. Dependencies: web, validation, data-jpa, security, oauth2-resource-server, actuator, flyway, postgresql, mail, springdoc, testcontainers, spring-security-test. Use records for DTOs, no Lombok.
- Config from environment variables. CORS for http://localhost:5173. Validate JWTs against http://localhost:8180/realms/baracel.

4. IDENTITY AND SECURITY
- JwtAuthenticationConverter: authorities are the permission strings of the user's realm roles (loaded from the access module, cached, cleared when the matrix changes), plus ROLE_<name>. A user's permissions are the union of their roles.
- Just-in-time provisioning: on the first authenticated call for a JWT sub with no employee row, create the employee (email and name from the token, status PENDING_APPROVAL, code EMP-0001 style from a sequence). A PENDING_APPROVAL user can call only GET /api/me. Every other call returns 403 with problem type pending-approval.
- GET /api/me returns id, name, email, job title, roles, permissions, status, manager, linked sign-in methods (read from the Keycloak Admin API with portal-service) and the unread notification count.

5. ACCESS MODULE (Roles and access screen)
- Tables permission(module, action), role_permission. Seed the matrix below, matching the Sales column in the screen.
  EMPLOYEE: profile:read-own, profile:edit-own, leave:request, employee:directory, task:view, task:create, task:edit, ai:chat.
  MANAGER adds: leave:approve (direct reports only, enforced in the service), employee:team.
  HR adds: leave:approve (everyone), employee:team, employee:records, employee:manage.
  SALES adds: ai:knowledge:product, ai:knowledge:brand, ai:knowledge:campaign, social:publish:company, social:publish:personal.
  ENGINEER adds: repo:view, ai:knowledge:engineering.
  ADMIN: everything, plus access:manage.
- Endpoints: GET /api/access/roles (name, user count from Keycloak); GET and PUT /api/access/roles/{role}/matrix (ADMIN only, audit logged); GET and PUT /api/access/identity-providers (read and enable or disable through the Keycloak Admin API, ADMIN only); GET /api/access/settings (default role for new sign-ups, approver, session length, read-only).

6. EMPLOYEE AND LEAVE MODULES
- employee table: id, keycloak_id (unique), code, first_name, last_name, email, job_title, department, manager_id (self reference), status (ACTIVE, ON_LEAVE, ONBOARDING, PENDING_APPROVAL, DEACTIVATED), joined_on, phone, location, gitea_username.
- GET /api/employees?scope=directory|team|all&q=&status=&department=&page=
  directory: needs employee:directory. Returns name, job title, department and email only.
  team: needs employee:team. Returns ONLY the caller's direct reports (manager_id = caller), computed on the server from the token. Callers cannot pass a manager id. Include role, status, joined date and leave balance.
  all: needs employee:records. Returns everyone with full records.
- GET /api/employees/{id}: the person themselves, a direct report of the caller, or HR. PATCH: HR only. POST /api/employees/invite (HR): creates the employee as ONBOARDING and the Keycloak user with required actions and an email. POST /api/employees/{id}/approve (HR): sets role, manager and status ACTIVE, and updates the Keycloak realm role. HR may assign EMPLOYEE, SALES, ENGINEER and MANAGER. Only ADMIN may assign HR or ADMIN.
- Leave tables: leave_type (Annual 30, Sick 10, Comp-off 3, Parental 5, Unpaid), leave_balance (employee, type, year, entitled, used), leave_request (employee, type, from, to, days, reason, status PENDING, APPROVED, REJECTED, CANCELLED, decided_by, decided_at, note), public_holiday.
- Working days exclude the weekend (config, default Saturday and Sunday) and public holidays.
- Endpoints: GET /api/leave/balances; POST /api/leave/requests (check balance, overlap with own requests, date order); DELETE /api/leave/requests/{id} (cancel own pending); GET /api/leave/requests?scope=mine|approvals (approvals: a manager sees pending requests of direct reports, HR sees all); POST /api/leave/requests/{id}/approve and /reject (needs leave:approve; a manager only for direct reports; nobody approves their own request); GET /api/leave/calendar?month=2026-11 (who is out in the caller's department).
- A daily scheduled job sets ON_LEAVE and back to ACTIVE from approved leave dates.
- Notifications: notification table, GET /api/notifications, POST /api/notifications/{id}/read. Create one when a request is submitted, approved or rejected, and send the email through Mailpit.

7. TASK MODULE (board and detail screens)
- Tables: project (key BAR), project_member, sprint, task (key BAR-142 style from a per-project sequence, title, description, status BACKLOG, IN_PROGRESS, IN_REVIEW, DONE, priority, assignee, reporter, due date, label, sprint), subtask, comment, activity.
- Endpoints: GET /api/projects; GET /api/projects/{key}/board?sprint=; POST /api/tasks; PATCH /api/tasks/{key}; PATCH /api/tasks/{key}/status; subtask and comment endpoints; GET /api/tasks/{key} (detail with subtasks, activity, Gitea links); GET /api/tasks?assignee=me&dueBefore=.
- Users see only projects they are a member of.

8. GITEA MODULE
- Config: gitea.base-url, gitea.token, gitea.webhook-secret. Tables project_repository, task_git_link (task, repo, branch, pr_number, pr_state OPEN, MERGED, CLOSED, checks_status, last_sha) and task_commit.
- POST /api/integrations/gitea/webhook: verify the X-Gitea-Signature HMAC-SHA256 of the raw body, reject if wrong. Handle push, create, pull_request, pull_request_review and status events. Find task keys with the pattern [A-Z]{2,6}-[0-9]+ in the branch name, PR title and body, and commit messages. Create or update the git link and commit rows, and add an activity entry. When a PR opens, move IN_PROGRESS to IN_REVIEW. When a PR merges, move IN_REVIEW to DONE. Make these two transitions configurable.
- GiteaClient (RestClient, behind an interface with a fake for tests): create a branch named feature/<key-lowercase>-<slug> from the default branch, list a project's repositories, fetch PR checks.
- Endpoints: POST /api/tasks/{key}/git/branch; POST /api/tasks/{key}/git/link-pr (repo and PR number); GET /api/repos (needs repo:view).

9. AI MODULE (stub in Phase 1)
- Tables: ai_conversation, ai_message, knowledge_collection (code, name, doc_count), role_collection, social_channel (code, name, type COMPANY or PERSONAL, provider INSTAGRAM, LINKEDIN or FACEBOOK, connected, owner), publish_log.
- Interface AiAssistantPort { AiReply chat(AiRequest) }.
  AiRequest: conversationId, userMessage, history, user (id, name, roles), allowedCollections.
  AiReply: text, sources (title, collection), optional draft (kind SOCIAL_POST, headline, caption, hashtags, suggested channels).
- StubAiAssistant is the only implementation in Phase 1 (setting ai.mode=stub). When the message mentions an ad or a post it returns the sample draft from the AI screen. Otherwise it returns a short canned answer. Sources always come from the allowed collections only. Do NOT write an HTTP client for the FastAPI service. That is Phase 2.
- allowedCollections is computed only in Spring from the role permissions. The AI service never decides access.
- Endpoints: GET and POST /api/ai/conversations; GET /api/ai/conversations/{id}; POST /api/ai/conversations/{id}/messages; GET /api/ai/access (allowed and denied collections and connected channels, for the right-hand panel); POST /api/ai/drafts/{id}/publish with channel codes. Publishing needs social:publish:company or social:publish:personal depending on the channel type. In Phase 1 it only writes a publish_log row with status SIMULATED and does not call any social network. Every publish is logged with the user.

10. DASHBOARD, SEED DATA, CONTRACT
- GET /api/dashboard: annual leave left, open task count and the next four due tasks, PRs awaiting my review (from Gitea review events, matched through gitea_username), who is out today, announcements (announcement table, HR creates).
- Seed data for the local profile, so the screens look like the PNGs: the people above with matching names and Keycloak users; reporting lines: Ruth Mensah manages Omar Haddad, Lena Fischer, Sara Qureshi, Daniel Okoye and Mei Tan; Omar Haddad manages Aisha Rahman, Karim Aziz, Nadia Hussein, Joel Mathews and Hana Saleh; Daniel Okoye manages Priya Nair and Tom Becker. Add Sprint 23 with the 11 tasks, subtasks, git links and the leave data shown in the screens (dates in November 2026).
- Add an audit_log table for access and HR changes.
- Export the OpenAPI contract to docs/openapi.json with a documented command. Write docs/api.md (endpoints grouped by screen), docs/run.md, docs/decisions.md.
- scripts/get-token.sh <username> prints an access token through portal-dev-cli.

DONE WHEN
- docker compose up -d starts everything, and mvn verify passes with Testcontainers.
- Tests prove: Aisha cannot approve leave (403) and cannot call scope=team or scope=all; Omar's scope=team returns exactly his 5 reports and no one else; Ruth's team never includes Omar's reports; Lena's scope=all returns everyone; a manager cannot approve the leave of someone who does not report to them; a webhook with a wrong signature is rejected and one with a right signature links a PR to BAR-142; the stub never returns a source from a collection the user lacks.
- You ran the stack and showed the output of these curl calls with real tokens: GET /api/me for Aisha, GET /api/employees?scope=team for Omar, and the 403 for Aisha.
- Report what you ran and what you could not run.