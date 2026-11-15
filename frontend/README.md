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

Environment (`.env.example`): `VITE_API_URL`, `VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID`. The Gitea address comes from `GET /api/config`. Defaults equal the example, so `.env` is optional.

## Layout
- `src/ui/` shared components, one per file (AppShell, Card, Button, Modal ...). `/styleguide` (dev only) shows all of them.
- `src/features/<screen>/` one folder per screen. `src/layout/` shell and route guards. `src/auth/` OIDC (tokens in memory only), `useMe`, `<Can>`, `usePermission`.
- `src/api/` the typed client. `schema.d.ts` is generated, never edited. The bearer token, 401 handling and the pending-approval state live in `client.ts` only.
- Design tokens: `design/tokens.css` is imported through the Vite alias `@design`. Components use only CSS variables; `npm run lint` fails on a raw hex colour.

## Rules worth knowing
- Hide what the user may not use with `<Can permission="...">`; the backend still answers 403.
- A 403 with problem type `pending-approval` shows the Waiting for approval page.

## Phase 2 screens (AI assistant, Knowledge base, Connected accounts)
* `src/features/ai`: the chat reads the answer as server-sent events with `fetch` and `sse.ts` (EventSource cannot send the bearer token). `useChatStream` keeps the state (tokens, sources, draft, errors), **Stop generating** aborts the request and keeps the partial text (the server saves it as STOPPED), and a retry control follows errors. The live region announces only "The assistant is writing an answer", never every token. `[1]` citations link to the matching source chip. The draft card edits headline, caption and hashtags (`PATCH /api/ai/drafts/{id}`, LinkedIn limit 3000 characters, hint at 1300). `PublishPanel` shows channels from `GET /api/ai/access`, asks for a confirmation with the exact text, and shows one result per channel (`publishResult.ts`). A person without a publish permission only sees Copy text.
* `src/features/accounts`: LinkedIn cards, Connect (redirects to the URL the backend returns), the return flag (`?linkedin=...`) and the publishing history with paging.
* `src/features/knowledge`: stats, collections, detail panel with role chips, the Add source dialog (PDF upload with progress through `XMLHttpRequest`, Drive folder, website) and Indexing activity. Jobs are polled every 3 seconds while one runs; React Query stops when the page is left.

**Tests.** `npm test` has the stream parser (split chunks, error event, abort), the publish result mapping, the character counter, citations, source validation, permission based navigation, the publish panel and the accounts page. The Playwright specs `ai.spec.ts` and `knowledge.spec.ts` need the AI service running with the **fake LLM** (see `docs/run.md`, "AI end-to-end tests"): answers are then instant and deterministic.
