Follow CLAUDE.md. Add a local Kubernetes deployment of the whole portal using kind, with a smoke test. Do not change application behaviour. Only touch infra/k8s/, scripts/, the Dockerfiles for backend, frontend and ai-service (create them if missing), and docs. This repository has Phases 1 and 2 only: Gitea is the only Git provider and there is no GitHub code, so do not add any. Read docs/run.md, docs/api.md and the ai-service docs first. For kind, ingress-nginx and Kustomize read their current official documentation instead of relying on memory, and pin image and chart versions. Keep docs/progress.md as a checklist of the 8 steps and commit after each.

1. IMAGES
- Multi-stage Dockerfiles: backend (Java 21 JRE, non-root, Actuator health), frontend (Vite build served by nginx, runtime config from a config.json so one image works anywhere) and ai-service (slim Python, non-root, uvicorn, healthcheck). The embedding model must not be baked into the image: download it on first start into a PersistentVolumeClaim cache and show the download progress in the logs. Add .dockerignore files. Keycloak uses the stock image with the realm export and the Baracel login theme baked into a thin custom image or mounted from a ConfigMap, whichever the theme size allows.

2. CLUSTER SCRIPTS
- scripts/kind-up.sh creates the cluster from infra/k8s/kind-config.yaml (one node with the ingress-ready label and extraPortMappings for 80 and 443), installs ingress-nginx, builds and loads the images with kind load docker-image, creates the Secrets, applies the manifests and waits until everything is ready. Safe to run twice. scripts/kind-down.sh deletes the cluster. scripts/kind-reload.sh <service> rebuilds and restarts one service.
- scripts/kind-secrets.sh creates the Secrets from infra/.env. It refuses to run if a required value is empty and never prints values.

3. MANIFESTS (Kustomize, base plus overlays/local)
- Namespace baracel-portal. StatefulSets with PersistentVolumeClaims for the portal Postgres and the Keycloak Postgres. Deployments for Keycloak, portal-backend, portal-frontend, ai-service and Gitea, each with readiness and liveness probes, resource requests and limits that fit a laptop, and its own ServiceAccount. ChromaDB data and the embedding model cache get their own PVCs.
- Ingress hosts: portal.baracel.local (frontend on /, backend on /api), auth.baracel.local (Keycloak) and git.baracel.local (Gitea). The ai-service is not exposed through the ingress: only the backend calls it, through the cluster service name.
- ConfigMaps for non-secret settings (issuer URL, CORS origin, ai-service URL, Ollama base URL, provider switches, AI_LLM_PROVIDER, AI_EMBEDDING settings). Secrets for passwords, client secrets, the Gitea token and webhook secret, and the portal-backend-ai client secret.
- Ollama is reached at the host through a Service with an Endpoints or ExternalName entry for the Docker host address. Make the address one setting and document how to find it on Linux, macOS and Windows.

4. KEYCLOAK ISSUER AND HOSTNAMES
- The browser, the backend and the ai-service must all see the same issuer, http://auth.baracel.local/realms/baracel. Configure the Keycloak hostname settings for that. Inside the cluster make auth.baracel.local resolve to the ingress controller (CoreDNS rewrite or hostAliases) and document which one you used and why. Keep HTTP only in this local overlay and say so.
- Import the baracel realm on first start. The portal-web redirect URIs and web origins use portal.baracel.local. The ai-service validates service tokens against the same issuer.
- Gitea ROOT_URL and its webhook allowed hosts must accept calls to the backend service name inside the cluster. Update the stored Gitea settings of the portal to use the cluster values.

5. SMOKE TEST
- scripts/kind-smoke.sh runs after kind-up.sh and prints one PASS or FAIL line per check, exiting non-zero on any FAIL: all pods ready; the frontend answers at http://portal.baracel.local/; GET /api/config works; the Keycloak discovery document is reachable from the host and from inside the backend pod and the ai-service pod; an unauthenticated call to a protected API path returns 401; a service token from portal-dev-cli works against the backend; the backend reaches the ai-service health endpoint; the ai-service reaches Ollama (use the fake LLM provider setting for this check if Ollama is not running, and say which mode was tested); an AI chat request through the backend returns a streamed answer; a user without access to a collection never gets text from it (reuse the Phase 2 access test).

6. SEED DATA
- scripts/kind-seed.sh loads the demo users and roles for each role (Employee, Manager with direct reports, HR, Sales, Engineer, Admin) and a small knowledge base for the Sales collection, so the screens are not empty. It only runs against the local cluster and refuses to run if the Keycloak issuer is not the local one.

7. DOCS
- Write docs/kind.md: prerequisites, hosts file, first run, URLs and test logins, reloading one service, reading logs, resetting data, finding the Docker host address for Ollama, and troubleshooting for common failures (port 80 in use, image not loaded, issuer mismatch, Postgres volume left from an older run, embedding model still downloading, Ollama not reachable from the cluster). Update docs/run.md to point to it.

8. VERIFY
- Run scripts/kind-down.sh, then scripts/kind-up.sh from scratch, then scripts/kind-smoke.sh and scripts/kind-seed.sh. Then log in through the Baracel Keycloak theme at http://portal.baracel.local as the Manager and as the Sales user.

DONE WHEN
- kind-up.sh followed by kind-smoke.sh prints PASS for every check. If a check was skipped or ran in a reduced mode, state which one and why. Never report a skipped check as passed.
- Screenshots (Playwright) of the Manager's employee list, the Sales AI chat streaming an answer, and the Roles screen as Admin.
- No secret appears in any committed file, image layer or log.