Follow CLAUDE.md. Build the Phase 1 frontend of the Baracel Employee Portal and the Baracel Keycloak login theme. The backend, realm and seed data already exist (see docs/api.md, docs/openapi.json, docs/run.md). Do not change backend code. If you find a backend gap, write it in docs/backend-gaps.md and work around it.

First read design/BRAND.md and design/tokens.css and open every PNG in design/screens/. Files: 01-login, 02-dashboard, 03-leave, 04-employees, 05-employees-manager, 06-tasks-board, 07-task-detail, 08-ai-assistant, 09-roles-access. The design/html/ folder holds the same screens as plain HTML for exact spacing. The logo is in design/logo/. Keep docs/progress.md as a checklist of the 8 steps below, and commit after each step.

1. PROJECT SETUP (frontend/)
- Vite, React, TypeScript strict, React Router, TanStack Query, oidc-client-ts, lucide-react for icons (outline, 20px), Vitest and Testing Library, Playwright.
- Import design/tokens.css through a Vite alias (@design) so there is one source of truth. Load Inter, Space Grotesk and JetBrains Mono. Dark theme is the default, light theme through data-theme=light, with the toggle in the sidebar footer, remembered in localStorage (wrap storage access in try/catch). Add a lint check that fails if a component contains a raw hex colour.
- Build shared components in src/ui/, one per file, matching BRAND.md: AppShell (sidebar and content, drawer on phones), SidebarNav, PageHeader, Card, SummaryList, StatusPill, SegmentedTabs, Button, IconButton, Field, Select, Modal, ConfirmDialog, Toggle, Avatar, InfoNote, EmptyState, Skeleton. Add a /styleguide route in dev builds that shows each one.
- Env: VITE_API_URL=http://localhost:8080, VITE_OIDC_AUTHORITY=http://localhost:8180/realms/baracel, VITE_OIDC_CLIENT_ID=portal-web. Commit frontend/.env.example. App runs on port 5173.

2. AUTH
- Authorization Code flow with PKCE using client portal-web. Redirect to the Keycloak login page, handle the callback route, silent token renewal, logout (with Keycloak end-session redirect). Keep tokens in memory, not in localStorage.
- A typed API client generated from docs/openapi.json (openapi-typescript and openapi-fetch), with the bearer token added in one place. 401 triggers a fresh login. 403 with problem type pending-approval shows the Waiting for approval page.
- useMe() from GET /api/me, and a <Can permission="..."> component and usePermission() hook. Hide navigation and buttons the user is not allowed to use. The backend stays the authority.

3. APP SHELL AND NAVIGATION
- Sidebar as in the screens, grouped WORKSPACE, PEOPLE, WORK, ASSISTANT, ADMIN, shown by permission. Leave out the "Task detail" item (it exists in the screens only to show the page). The bell shows the unread count and opens a list from /api/notifications. User footer shows name, role and a profile link.
- Pages: Waiting for approval, 403, 404 and a generic error page, all in the same style.

4. SCREENS (match each PNG in layout, spacing, colour and copy, using real API data)
- Dashboard: 02-dashboard.png, from /api/dashboard.
- Leave: 03-leave.png. Balances, team calendar (month and week), request form with live working-day count and balance after, and the approvals table shown only to users with leave:approve. Approve and reject need a confirm dialog and an optional note. Show server validation errors next to the field.
- Employees: 04-employees.png and 05-employees-manager.png. Use tabs by permission: Directory (everyone), My team (needs employee:team, the default tab for a Manager), All employees (needs employee:records). My team lists ONLY what GET /api/employees?scope=team returns, which is the manager's direct reports. The profile panel shows sign-in methods and MFA state. Invite employee and Edit record appear only for HR. Pending approvals show for HR with an Approve action that asks for role and manager.
- Tasks: 06-tasks-board.png, a board with drag and drop between columns (keyboard accessible too) calling the status endpoint, with Gitea branch and PR chips on cards. 07-task-detail.png opens when a card is clicked: subtasks, comments, activity, details, and the Gitea panel with Create branch and Link PR.
- AI assistant: 08-ai-assistant.png. Conversation list, chat thread, source chips, draft ad preview, the Publish to channels picker (only channels the user may use are enabled), Schedule shown as disabled with a Phase 2 hint, and the right panel from /api/ai/access. Phase 1 talks to the backend stub. Add a clear "Simulated: nothing was posted" note after publishing.
- Roles and access: 09-roles-access.png, shown only to users with access:manage. Role list, permission matrix with a Save button, and the sign-in methods toggles.
- Every list and page has loading, empty and error states. Layout must work down to a 390px wide phone.

5. KEYCLOAK LOGIN THEME (keycloak/themes/baracel/login/)
- Replace Keycloak's default look for the whole login flow. Do not edit built-in themes. Check the Keycloak docs for the exact version used in infra/docker-compose.yml and follow its theme structure.
- Files: theme.properties (parent=keycloak, own styles), template.ftl (page frame: left brand panel with logo and text, right card), login.ftl, register.ftl, login-reset-password.ftl, login-update-password.ftl, login-verify-email.ftl, login-config-totp.ftl, error.ftl, info.ftl, messages/messages_en.properties, resources/css/baracel.css, resources/img (logo).
- Match design/screens/01-login.png: dark navy brand panel on the left with the logo, headline and three bullets; on the right a card titled Welcome back, with Sign in and Create account tabs (links to the login and registration pages), buttons Continue with Google, GitHub and Facebook built from the realm's social providers, an or divider, email and password fields, Keep me signed in, Forgot password, and the primary Sign in button. Show a note under the form that new social sign-ups wait for HR approval. Show Keycloak error and info messages in the card using the status colours.
- Styles use the CSS variables from tokens.css. Add scripts/sync-theme.sh that copies design/tokens.css and the logo into the theme's resources, and run it. Real labels, visible focus, mobile layout.
- Confirm the realm uses loginTheme baracel, restart Keycloak, and check every page in a browser.

6. TESTS
- Vitest: permission-based navigation, the Employees tabs for Aisha, Omar and Lena, the leave form calculations display.
- Playwright against the running stack: sign in through the Baracel Keycloak page as aisha.rahman (SALES), omar.haddad (MANAGER) and lena.fischer (HR). Check: Aisha has no My team tab and no approvals table, and cannot see Roles and access; Omar sees My team with exactly Aisha Rahman, Karim Aziz, Nadia Hussein, Joel Mathews and Hana Saleh; Lena sees All employees; a leave request flows from Aisha to Omar's approvals and back; the AI draft can be published to two channels as Aisha. Take screenshots of each screen into frontend/e2e/screenshots/.

7. VISUAL CHECK
- For every screen and for the Keycloak login page, take a screenshot at 1440px and compare it with the PNG in design/screens/. List each difference you found and fix it, or record why not in docs/decisions.md.

8. DOCS
- Update docs/run.md with: how to start the stack, the backend and the frontend, the dev users, and how to rebuild the theme. Add frontend/README.md.

DONE WHEN
- npm run build, npm run lint and npm test pass, and the Playwright run passes.
- You show screenshots of the Baracel login page and of the Manager's My team tab.
- Report what you ran and what you could not run.