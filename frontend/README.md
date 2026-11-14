# Baracel portal frontend

React + TypeScript (strict) + Vite, React Router, TanStack Query, oidc-client-ts, lucide-react.

```bash
npm ci
npm run dev        # http://localhost:5173 (port is fixed)
npm run build      # type-check + production build
npm run lint       # eslint + "no raw hex colour in src/" check
npm test           # Vitest + Testing Library
npm run e2e        # Playwright against the running stack (see docs/run.md); screenshots go to e2e/screenshots/
npm run api:types  # regenerate src/api/schema.d.ts from ../docs/openapi.json
```

Environment (`.env.example`): `VITE_API_URL`, `VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID`, `VITE_GITEA_URL`. Defaults equal the example, so `.env` is optional.

## Layout
- `src/ui/` shared components, one per file (AppShell, Card, Button, Modal ...). `/styleguide` (dev only) shows all of them.
- `src/features/<screen>/` one folder per screen. `src/layout/` shell and route guards. `src/auth/` OIDC (tokens in memory only), `useMe`, `<Can>`, `usePermission`.
- `src/api/` the typed client. `schema.d.ts` is generated, never edited. The bearer token, 401 handling and the pending-approval state live in `client.ts` only.
- Design tokens: `design/tokens.css` is imported through the Vite alias `@design`. Components use only CSS variables; `npm run lint` fails on a raw hex colour.

## Rules worth knowing
- Hide what the user may not use with `<Can permission="...">`; the backend still answers 403.
- A 403 with problem type `pending-approval` shows the Waiting for approval page.
