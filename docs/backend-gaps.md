# Backend gaps found while building the frontend

All eight gaps are fixed by prompt 03 (see docs/api.md). The frontend still contains its workarounds; the follow-up frontend prompt removes them.

1. ~~No "project members" endpoint.~~ **Fixed:** `GET/POST /api/projects/{key}/members`, `DELETE /api/projects/{key}/members/{employeeId}`. Task create and update still reject a non-member assignee (`not-a-member`).
2. ~~Branch and PR dialogs need a repository name, but `GET /api/repos` needs `repo:view`.~~ **Fixed:** `GET /api/projects/{key}/repos` (members with `task:view`). Branch and PR link calls accept only repositories linked to the task's project (`repo-not-linked`).
3. ~~The board has no Gitea host.~~ **Fixed:** `GET /api/config` returns `giteaUrl` (setting `gitea.public-url`), `appName` and the leave types.
4. ~~No "Reset access" or "New role" endpoints.~~ **Fixed:** `POST /api/employees/{id}/reset-access`, `POST /api/access/roles`, `DELETE /api/access/roles/{role}`.
5. ~~No unread-only or paged notifications, no mark-all-read.~~ **Fixed:** `GET /api/notifications?unread&page&size` returns `{items,total,unreadCount,page,size}` (the response is no longer a bare array); `POST /api/notifications/read-all`.
6. ~~MFA state is only known for the password method.~~ **Fixed:** `signInMethods[].mfa` is `ENABLED`, `DISABLED` or `PROVIDER_MANAGED`.
7. ~~The generated API types make every field optional.~~ **Fixed:** every record component is `required` in `docs/openapi.json` unless marked `@Nullable`, which is optional and accepts null.
8. ~~Activity feed is newest first only.~~ **Fixed:** `GET /api/tasks/{key}?activityOrder=asc|desc` (default `desc`).
