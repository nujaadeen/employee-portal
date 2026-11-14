# Running Baracel locally (backend, Keycloak, Gitea, Mailpit)

Needs Docker with Compose, Java 21 and Maven.

## 1. Start the infrastructure
```bash
cd infra
cp .env.example .env        # optional: fill in secrets. Compose has dev defaults, so it also works without .env
docker compose up -d        # postgres, mailpit, keycloak (imports the realm), gitea
docker compose ps           # all four should be "healthy"
```
| Service | URL | Notes |
|---|---|---|
| Keycloak | http://localhost:8180 | admin console login `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` (default `admin` / `change-me-admin`), realm `baracel` |
| Gitea | http://localhost:3000 | |
| Mailpit | http://localhost:8025 (SMTP 1025) | all mail from Keycloak, Gitea and Spring lands here |
| PostgreSQL | localhost:5433 | databases `portal`, `keycloak`, `gitea`; user `postgres` |

The realm is imported from `keycloak/realm/baracel-realm.json` on the first start. The import skips an existing realm, so after editing the file reset Keycloak's database:
`docker compose stop keycloak && docker compose exec postgres psql -U postgres -c 'DROP DATABASE keycloak' -c 'CREATE DATABASE keycloak' && docker compose up -d keycloak`.

**Login theme.** The realm sets `loginTheme: baracel`, and the theme lives in `keycloak/themes/baracel/login/`. (Correction to the earlier note: a missing or empty theme folder does not fall back, Keycloak answers 500 on the login page. The theme must be there.)

## 2. Run the backend
```bash
cd backend
export PORTAL_SERVICE_CLIENT_SECRET=change-me-service-secret   # same value as infra/.env
export GITEA_TOKEN=...                                          # optional, see "Gitea" below
export GITEA_WEBHOOK_SECRET=dev-webhook-secret                  # optional
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run                # local = demo seed data
```
Settings come from environment variables: `PORTAL_DB_URL` (default `jdbc:postgresql://localhost:5433/portal`), `PORTAL_DB_USER`, `PORTAL_DB_PASSWORD`, `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_BASE_URL`, `PORTAL_SERVICE_CLIENT_SECRET`, `GITEA_BASE_URL`, `GITEA_TOKEN`, `GITEA_WEBHOOK_SECRET`, `MAIL_HOST`, `MAIL_PORT`, `PORTAL_CORS_ORIGINS`.
Without the `local` profile no demo data is loaded and an empty system starts with the first sign-ups pending HR approval.
To reload the demo data: `docker compose exec postgres psql -U postgres -c 'DROP DATABASE portal' -c 'CREATE DATABASE portal'` and restart.

## 3. Get a token and call the API
```bash
scripts/get-token.sh aisha.rahman                         # prints an access token (client portal-dev-cli)
TOKEN=$(scripts/get-token.sh omar.haddad)
curl -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/employees?scope=team'
```

## 4. Tests and the API contract
```bash
cd backend
mvn verify       # unit tests + MockMvc integration tests on PostgreSQL in Testcontainers (needs Docker)
```
`mvn verify` also rewrites `docs/openapi.json` (test `OpenApiExportIT`). To regenerate only the contract:
`mvn -q test -Dtest=OpenApiExportIT`.

## Dev users (password `ChangeMe123!`)
**DEV ONLY. Remove these users (and the `portal-dev-cli` client) before any production use.**

| User | Roles | Reports to |
|---|---|---|
| aisha.rahman | SALES | Omar Haddad |
| omar.haddad | MANAGER | Ruth Mensah |
| ruth.mensah | MANAGER | - |
| lena.fischer | HR | Ruth Mensah |
| daniel.okoye | ADMIN, ENGINEER, MANAGER | Ruth Mensah |
| priya.nair, tom.becker | ENGINEER | Daniel Okoye |
| sara.qureshi, mei.tan | EMPLOYEE | Ruth Mensah |
| karim.aziz, nadia.hussein, joel.mathews, hana.saleh | SALES | Omar Haddad |

Everyone also has EMPLOYEE (the realm default role). Reporting lines live in the portal database seed, not in Keycloak.

## Social sign-in
Put real OAuth client ids and secrets for Google, GitHub and Facebook in `infra/.env` (placeholders otherwise). Redirect URI for each provider: `http://localhost:8180/realms/baracel/broker/<google|github|facebook>/endpoint`. New social users are created without a profile review page, get the EMPLOYEE role, and stay `PENDING_APPROVAL` in the portal until HR approves them (`POST /api/employees/{id}/approve`).

## Gitea
Gitea has no users on first start. For a local round trip:
```bash
cd infra
docker compose exec -u git gitea gitea admin user create --admin --username baracel-admin --password 'ChangeMe123!' --email admin@baracel.example --must-change-password=false
docker compose exec -u git gitea gitea admin user generate-access-token --username baracel-admin --token-name portal --scopes all --raw   # -> GITEA_TOKEN
curl -X POST http://localhost:3000/api/v1/orgs -H "Authorization: token $GITEA_TOKEN" -H 'Content-Type: application/json' -d '{"username":"baracel"}'
curl -X POST http://localhost:3000/api/v1/orgs/baracel/repos -H "Authorization: token $GITEA_TOKEN" -H 'Content-Type: application/json' -d '{"name":"otp-service","auto_init":true}'
curl -X POST http://localhost:3000/api/v1/repos/baracel/otp-service/hooks -H "Authorization: token $GITEA_TOKEN" -H 'Content-Type: application/json' \
  -d '{"type":"gitea","active":true,"config":{"url":"http://host.docker.internal:8080/api/integrations/gitea/webhook","content_type":"json","secret":"<GITEA_WEBHOOK_SECRET>"},"events":["push","create","pull_request","pull_request_review_approved","pull_request_review_rejected","pull_request_review_comment","pull_request_review_request","status"]}'
```
Restart the backend with `GITEA_TOKEN` and `GITEA_WEBHOOK_SECRET` set. Put a task key such as `BAR-142` in the branch name, PR title or commit message. The seed registers `baracel/otp-service` and `baracel/portal` for project BAR.

## Gitea single sign-on (same login as the portal)
The realm has a client `gitea` (secret `GITEA_OIDC_CLIENT_SECRET`), and Gitea has an OAuth2 source named `baracel`. On the Gitea login page choose **Sign in with baracel** and use a portal account (for example `aisha.rahman` / `ChangeMe123!`). The first sign-in creates the Gitea user with the same username (`preferred_username`), which is what `gitea_username` in the portal should match.
One-time setup after a fresh Gitea volume (the compose file already sets auto-registration):
```bash
cd infra
docker compose exec -u git gitea gitea admin auth add-oauth --name baracel --provider openidConnect \
  --key gitea --secret "$GITEA_OIDC_CLIENT_SECRET" \
  --auto-discover-url http://host.docker.internal:8180/realms/baracel/.well-known/openid-configuration \
  --scopes openid --scopes email --scopes profile
```
(default secret without `.env`: `change-me-gitea-oidc-secret`). Gitea reaches Keycloak as `host.docker.internal`; Keycloak runs with `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` so token calls work from the container while the browser keeps using `localhost:8180`. Anyone who can sign in to the realm can create a Gitea account this way; the local `baracel-admin` account still works for the API token.
After editing the realm file, reset Keycloak's database (see above) so the import runs again.

## Troubleshooting
- Keycloak Admin calls answer 401: the `portal-service` client needs the `kc-admin-api` scope (it is in the realm file) and `PORTAL_SERVICE_CLIENT_SECRET` must match the realm.
- `GET /api/me` shows `signInMethods: []`: Keycloak was not reachable or the client secret is wrong. The rest of the API keeps working.
- Port 5432 busy: the compose Postgres uses 5433.

## Frontend (prompt 02)
```bash
# 1. infrastructure and backend as above (backend with SPRING_PROFILES_ACTIVE=local)
# 2. frontend
cd frontend && npm ci && npm run dev          # http://localhost:5173
```
Sign in as any dev user above. The Keycloak login page is the Baracel theme.

**Rebuild the login theme.** Edit files in `keycloak/themes/baracel/login/`. `scripts/sync-theme.sh` copies `design/tokens.css`, the logos and the fonts into `resources/` (run it after changing tokens or logos; it needs `npm ci` in `frontend/` for the fonts). Keycloak in `start-dev` does not cache themes, so a browser refresh shows the change. For a cached setup restart Keycloak: `docker compose restart keycloak`.

**Checks.** `cd frontend && npm run build && npm run lint && npm test`. End-to-end: start the stack and the backend on a freshly seeded database, then `npm run e2e`. The leave test books a different future day on every run (and uses up one annual day of Aisha), so reset the portal database now and then:
`docker compose exec postgres psql -U postgres -c 'DROP DATABASE portal' -c 'CREATE DATABASE portal'` and restart the backend.

**Prompt 04 notes.** The backend must include the gap fixes (members, config, roles, notifications). Creating or deleting roles from the portal needs the Keycloak service account to hold `manage-realm` (in the realm file; assign it by hand on an already imported realm). The frontend takes the Gitea address from `GET /api/config` (`GITEA_PUBLIC_URL` on the backend), so there is no `VITE_GITEA_URL` any more.
