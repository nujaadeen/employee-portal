# Backend gaps found while building the frontend

All eight gaps are fixed by prompt 03 (see docs/api.md), and the frontend no longer contains any workaround (prompt 04).

1. ~~No "project members" endpoint.~~ **Fixed:** `GET/POST /api/projects/{key}/members`, `DELETE /api/projects/{key}/members/{employeeId}`. Task create and update still reject a non-member assignee (`not-a-member`).
2. ~~Branch and PR dialogs need a repository name, but `GET /api/repos` needs `repo:view`.~~ **Fixed:** `GET /api/projects/{key}/repos` (members with `task:view`). Branch and PR link calls accept only repositories linked to the task's project (`repo-not-linked`).
3. ~~The board has no Gitea host.~~ **Fixed:** `GET /api/config` returns `giteaUrl` (setting `gitea.public-url`), `appName` and the leave types.
4. ~~No "Reset access" or "New role" endpoints.~~ **Fixed:** `POST /api/employees/{id}/reset-access`, `POST /api/access/roles`, `DELETE /api/access/roles/{role}`.
5. ~~No unread-only or paged notifications, no mark-all-read.~~ **Fixed:** `GET /api/notifications?unread&page&size` returns `{items,total,unreadCount,page,size}` (the response is no longer a bare array); `POST /api/notifications/read-all`.
6. ~~MFA state is only known for the password method.~~ **Fixed:** `signInMethods[].mfa` is `ENABLED`, `DISABLED` or `PROVIDER_MANAGED`.
7. ~~The generated API types make every field optional.~~ **Fixed:** every record component is `required` in `docs/openapi.json` unless marked `@Nullable`, which is optional and accepts null.
8. ~~Activity feed is newest first only.~~ **Fixed:** `GET /api/tasks/{key}?activityOrder=asc|desc` (default `desc`).

## Prompt 07 (AI, publishing, knowledge base)
- **Which model answers.** The sample screen 08 shows a badge "Ollama · local model". No endpoint says which provider or model is in use (`GET /api/config` has only the app name, the Gitea address and the leave types), so the badge is not shown. Workaround: none needed for function. Suggested: add `aiProvider` and `aiModel` to `GET /api/config`.
- **Roles list for the Knowledge base.** `GET /api/access/roles` needs `access:manage`; a person with only `ai:knowledge:manage` would get 403, so the role chips fall back to the six built-in roles (custom roles are then missing). Suggested: allow `ai:knowledge:manage` on that endpoint, or add the role names to `GET /api/knowledge/collections`.
- **Per-source information** (Drive folder name, "synced 2 days ago", "Stale") is not available, see `docs/ai-service-gaps.md`. The detail panel groups the documents by source type instead.
- **No endpoint for a draft's publish state.** After a reload the draft card does not know it was already published, so the edit form is open and a later edit answers `409 draft-published`, shown as an error under the form. Suggested: add `published` (or the results) to `DraftDto`.
