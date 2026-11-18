# The portal on a local Kubernetes cluster (kind)

The whole portal runs in a one-node [kind](https://kind.sigs.k8s.io/) cluster: Keycloak, the backend, the frontend, the AI service, ChromaDB, Gitea, two PostgreSQL databases and Mailpit, behind an ingress controller. It is for trying the deployment on a laptop. **HTTP only: there is no TLS anywhere in this setup.** Do not expose it to a network.

```
browser ──80──▶ ingress-nginx ─┬─ portal.baracel.local  /      → portal-frontend (nginx)
                               │                         /api   → portal-backend ──▶ ai-service ──▶ chroma
                               ├─ auth.baracel.local            → keycloak ──▶ keycloak-postgres      └──▶ ollama (on the Docker host)
                               └─ git.baracel.local             → gitea         portal-backend ──▶ portal-postgres (portal, gitea, ai_service)
```
The ai-service has no Ingress: only the backend calls it, as `http://ai-service:8000`.

## Prerequisites
Docker (running), [kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) 0.30 or newer, `kubectl` 1.31 or newer (Kustomize is built in), `python3`, `curl`, `openssl`. About 8 GB of free memory and 12 GB of disk. Port 80 and 443 on this machine must be free. Tested with kind 0.30.0 and kubectl 1.37 on Linux.

## Hosts file
Add one line, with administrator rights (`/etc/hosts` on Linux and macOS, `C:\Windows\System32\drivers\etc\hosts` on Windows):
```
127.0.0.1 portal.baracel.local auth.baracel.local git.baracel.local
```
Without it a browser cannot open the three names. The scripts do not need it (they use `curl --resolve`).

## First run
```bash
scripts/kind-up.sh        # cluster, ingress, images, secrets, manifests, Gitea set-up. Safe to run again.
scripts/kind-smoke.sh     # one PASS, FAIL or SKIP line per check; exit code 1 if anything FAILS
scripts/kind-seed.sh      # demo users check and a small knowledge base for the Sales collections
```
* `scripts/kind-up.sh` creates `infra/.env` with random secrets if it does not exist (never overwritten, values never printed). To use your own values copy `infra/.env.example` and fill every required line: `kind-secrets.sh` refuses to run when one is empty or still `change-me…`.
* The **first start takes 10 to 30 minutes**: the AI service downloads the embedding model (BAAI/bge-m3, about 2.3 GB) into a volume. It is not in the image. The wait loop prints the download progress. Later starts take a minute or two.
* Options: `KIND_LLM_PROVIDER=fake` (instant canned answers, no Ollama), `KIND_SKIP_BUILD=1` (reuse the images), `OLLAMA_HOST_ADDR=<ip>`, `KIND_WAIT_MINUTES=40`.

## URLs and test logins
| What | URL |
|---|---|
| Portal | http://portal.baracel.local/ |
| Keycloak (admin console `/admin/master/console/`) | http://auth.baracel.local/ |
| Gitea | http://git.baracel.local/ |
| Mail (Mailpit) | `kubectl -n baracel-portal port-forward svc/mailpit 8025` then http://localhost:8025 |

Demo users (password `ChangeMe123!`, **development only**): `aisha.rahman` (Sales), `omar.haddad` (Manager, five direct reports), `lena.fischer` (HR), `priya.nair` (Engineer), `daniel.okoye` (Admin), `sara.qureshi` (Employee). The Keycloak admin login and the other secrets are in `infra/.env` (read the file yourself; the scripts never print them).

## Reloading one service
```bash
scripts/kind-reload.sh backend      # or frontend, ai-service, keycloak
```
It rebuilds the image, runs `kind load docker-image`, restarts the Deployment and waits. After you change the provider switch in a ConfigMap, reload the service that reads it (the ai-service for `AI_LLM_PROVIDER`).

## Reading logs
```bash
kubectl -n baracel-portal get pods
kubectl -n baracel-portal logs deploy/portal-backend -f
kubectl -n baracel-portal logs deploy/ai-service -f        # the model download progress is here on the first start
kubectl -n baracel-portal logs deploy/keycloak
kubectl -n ingress-nginx logs deploy/ingress-nginx-controller
```
Logs never contain passwords, tokens or message text (the AI service and the backend are written that way, and the scripts do not print secrets).

## Resetting data
* Everything: `scripts/kind-down.sh` (deletes the cluster and all its volumes), then `scripts/kind-up.sh`.
* One database or volume: `kubectl -n baracel-portal scale statefulset/portal-postgres --replicas=0`, `kubectl -n baracel-portal delete pvc data-portal-postgres-0`, scale back to 1 and run `scripts/kind-up.sh`. (The embedding model volume is `ai-models`, keep it to avoid the 2.3 GB download.)

## Finding the Docker host address for Ollama
Ollama runs on the Docker host (it needs the GPU), not in the cluster. The Service `ollama` points at the host through an Endpoints object; the address is **one setting, `OLLAMA_HOST_ADDR`**, rendered into `infra/k8s/overlays/local/ollama-endpoints.yaml.tmpl` by `scripts/kind-up.sh`.
* **Linux**: `kind-up.sh` finds it itself: the gateway of the docker network `kind`: `docker network inspect kind -f '{{range .IPAM.Config}}{{.Gateway}} {{end}}'` (usually `172.18.0.1`). Ollama must listen on that address, not only on 127.0.0.1: start it with `OLLAMA_HOST=0.0.0.0` (systemd: `sudo systemctl edit ollama`, add `Environment="OLLAMA_HOST=0.0.0.0"`, restart) and allow port 11434 from the docker network if a firewall is on.
* **macOS and Windows (Docker Desktop)**: `docker run --rm alpine getent hosts host.docker.internal` prints the address; export it: `OLLAMA_HOST_ADDR=<that ip> scripts/kind-up.sh`.
* Check from the cluster: `kubectl -n baracel-portal exec deploy/ai-service -- python -c "import urllib.request; print(urllib.request.urlopen('http://ollama:11434/api/tags').status)"`.
* No Ollama? `KIND_LLM_PROVIDER=fake scripts/kind-up.sh` switches the AI service to the fake LLM: deterministic answers that cite the retrieved sources. `kind-smoke.sh` then says so and marks the Ollama check as SKIP.

## How the issuer works (one issuer for everybody)
The browser, the backend, the ai-service, Gitea and Keycloak itself must all see the same issuer, `http://auth.baracel.local/realms/baracel`, because token signatures and the `iss` claim are checked against it.
* Keycloak runs with `KC_HOSTNAME=http://auth.baracel.local`, `KC_HTTP_ENABLED=true` and `KC_PROXY_HEADERS=xforwarded` (it sits behind the ingress).
* Inside the cluster the three `*.baracel.local` names resolve to the ingress controller through a **CoreDNS rewrite** (`scripts/kind-up.sh` adds `rewrite name auth.baracel.local ingress-nginx-controller.ingress-nginx.svc.cluster.local` and two more to the `coredns` ConfigMap). I chose it over `hostAliases` because one rewrite covers every pod, including Gitea's own calls to Keycloak, and it needs no IP address (the controller's cluster IP changes with every cluster, and `hostAliases` would have to be repeated in every pod spec).
* The realm file `keycloak/realm/baracel-realm.json` is not edited: the Keycloak image build rewrites the redirect URIs and web origins of `portal-web` and `gitea` to `portal.baracel.local` and `git.baracel.local` (see `infra/k8s/images/keycloak/Dockerfile`).
* Gitea: `ROOT_URL=http://git.baracel.local/`; webhooks may go to `portal-backend.baracel-portal.svc.cluster.local`; `scripts/kind-gitea.sh` creates the admin, the API token (stored in the backend Secret), the organisation and repositories, the webhook to the backend's cluster service name and the OAuth source `baracel` with the cluster issuer. Re-running it updates stored values from an older run.

## What was not changed
The application code is the same as for Docker Compose. Two things differ only in configuration: the frontend image takes its three settings at container start (the bundle is built with placeholders that the start script replaces, and `/config.json` shows them), and the backend runs with the `local` Spring profile, which loads the demo seed (people, Sprint 23, leave) so the Keycloak users have portal records. The ingress controller is ingress-nginx 1.15.1, which the Kubernetes project has retired (see `infra/k8s/ingress-nginx/README.md`): fine for a laptop, not for anything else.

## Troubleshooting
| Symptom | Cause and fix |
|---|---|
| `kind-up.sh`: "port 80 or 443 … in use" | Something listens on 80 or 443 (`ss -ltnp \| grep -E ':(80\|443) '`). Stop it, or free the port, then run again. kind publishes both on 127.0.0.1. |
| A pod shows `ErrImagePull` or `ImagePullBackOff` for `baracel/...:local` | The image is not in the cluster. `scripts/kind-up.sh` (without `KIND_SKIP_BUILD`) builds and loads it, or `scripts/kind-reload.sh <service>`. The tag `:local` is never pulled from a registry. |
| Login fails with an issuer or "invalid_issuer" error, or the backend answers 401 to a valid token | The token's issuer differs from `http://auth.baracel.local/realms/baracel`. Open the portal at `http://portal.baracel.local` (not an IP or localhost), check `kubectl -n baracel-portal get cm portal-config -o yaml` and `scripts/kind-smoke.sh` (the discovery checks). |
| Keycloak or the backend crash after a rerun, "password authentication failed" | A Postgres volume from an older run keeps the old password. Reset the data (see above) or `scripts/kind-down.sh`. The init SQL (databases portal, gitea, ai_service) only runs on an empty volume. |
| `ai-service` is `0/1` for a long time | The embedding model is still downloading (about 2.3 GB). `kubectl -n baracel-portal logs deploy/ai-service` shows `embedding model download: … %`. It has up to 30 minutes. If the log shows a network error, the download resumes on the next restart. |
| The AI chat answers "assistant unavailable", or `ai-service` is not ready with `llm` false | Ollama is not reachable from the cluster (it listens only on 127.0.0.1, or the address is wrong). See "Finding the Docker host address", or use `KIND_LLM_PROVIDER=fake`. |
| `chroma` crashes with `invalid type … expected u16 for key "PORT"` | Kubernetes injected `CHROMA_PORT=tcp://…` from the Service. The manifest sets `enableServiceLinks: false` for this reason; keep it. |
| The browser cannot open `portal.baracel.local` | The hosts line is missing (see above). |
