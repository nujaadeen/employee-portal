# Backend gaps found while building the frontend

The frontend does not change backend code. Each gap below has a workaround in the UI.

1. **No "project members" endpoint.** The Create task dialog needs candidate assignees. Workaround: it lists the company directory (`GET /api/employees?scope=directory`) and the server rejects a non-member with `not-a-member`, which the dialog shows next to the Assignee field.
2. **Branch and PR dialogs need a repository name, but `GET /api/repos` needs `repo:view`.** Sales people may create branches (`task:edit`) yet cannot list repositories. Workaround: the repository field is a text box pre-filled from the task's existing Git link; people with `repo:view` get a list.
3. **The board has no Gitea host.** The "gitea.baracel.internal" chip on the board screen needs the host. Workaround: the chip uses `VITE_GITEA_URL` (default `http://localhost:3000`).
4. **No "Reset access" or "New role" endpoints.** Screens 04 and 09 show these buttons. They are left out of the UI.
5. **No unread-only or paged notifications, no mark-all-read.** The bell lists the latest 50 and marks one at a time.
6. **MFA state is only known for the password method** (`signInMethods[].mfa`). Social methods show Linked or Not linked only.
7. **The generated API types make every field optional** because the Spring DTOs have no `@NotNull`/`@Schema(requiredMode)`. The frontend uses non-null assertions where the data is always present. Marking record components as required in springdoc would give stricter types.
8. **Activity feed is newest first;** screen 07 reads oldest to newest in places. The UI keeps the server order.
