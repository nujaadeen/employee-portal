Follow CLAUDE.md. The backend gaps in docs/backend-gaps.md are now fixed (see docs/api.md and the new docs/openapi.json). Update the frontend to use the new endpoints and remove every workaround. Do not change backend code. Commit after each item and keep docs/progress.md updated.

1. Regenerate the API types from docs/openapi.json. Remove the non-null assertions that existed only because every field was optional, fix the type errors this exposes, and make npm run build pass.
2. Create task dialog: the Assignee picker uses GET /api/projects/{key}/members. Managers with the right permission see add and remove member actions on the project.
3. Branch and Link PR dialogs: the repository field becomes a select filled from GET /api/projects/{key}/repos.
4. The Gitea chip on the board and the Open in Gitea links use giteaUrl from GET /api/config. Remove VITE_GITEA_URL.
5. Employees: add the Reset access button for users with employee:manage, with a confirm dialog that says an email will be sent. Roles and access: add New role (name and description, with the server's validation errors) and Delete for custom roles. Show success and error toasts.
6. Notifications: the bell shows unreadCount from the server, has an Unread only toggle, loads more on scroll or with a Load more button, and has Mark all as read.
7. Sign-in methods: show MFA on, MFA off, or Managed by provider from the new enum, with text and an icon, not colour alone.
8. Task detail: the activity list keeps newest first. Do not edit design/, which is read-only. Add a short note in docs/decisions.md that the sample screen's activity order was only illustrative.

Update or add Vitest tests for items 2, 5 and 6, and rerun the Playwright flow. Take new screenshots of the board, Roles and access, and the notification list. Mark the gaps as fixed in docs/backend-gaps.md. Done when build, lint, unit tests and Playwright all pass. Report what you ran and what you could not run.