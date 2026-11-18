# baracel-portal Helm chart

Deploys the Baracel Employee Portal: Keycloak (realm and Baracel login theme baked into the image), the Spring backend, the React frontend (nginx), the FastAPI AI service, ChromaDB, Gitea, two PostgreSQL StatefulSets and Mailpit, plus the Ingress for `portal`, `auth` and `git`. The AI service has no Ingress: only the backend calls it.

## Install on kind
```bash
KIND_DEPLOY=helm scripts/kind-up.sh        # cluster, ingress, images, secrets, helm install, wait, helm test, Gitea set-up
scripts/kind-smoke.sh && scripts/kind-seed.sh
```
By hand (cluster, images and Secrets already there):
```bash
helm upgrade --install baracel-portal infra/helm/baracel-portal -n baracel-portal \
  -f infra/helm/baracel-portal/values-kind.yaml --set ollama.hostAddress=<host ip>
helm -n baracel-portal test baracel-portal
```

## Design
| Practice | How |
|---|---|
| No secrets in the chart | Passwords, client secrets and tokens are read from Secrets that must exist (`existingSecrets.*`; `scripts/kind-secrets.sh` creates them for kind). `values.schema.json` makes the names required. |
| Validated input | `values.schema.json` (hosts, scheme, enums for providers). `helm lint` fails on `global.scheme=ftp`. `ollama.hostAddress` is required when Ollama is enabled (clear `fail` message). |
| Labels | `app.kubernetes.io/{name,instance,version,managed-by,part-of}` and `helm.sh/chart` on everything; selectors use only `name` and `instance`. |
| Naming | `<release>-portal-backend` etc.; `resourcePrefix: ""` gives bare names (what the kind scripts use). |
| Rolling on config change | Pods carry `checksum/config` of the ConfigMap, so a changed value restarts what reads it. |
| Hardened pods | per component `runAsNonRoot` and a numeric user, `seccompProfile: RuntimeDefault`, `allowPrivilegeEscalation: false`, all capabilities dropped, no service account token mounted, one ServiceAccount per component. |
| Probes and resources | startup, readiness and liveness on every workload (the AI service gets 30 minutes for the first model download); requests and limits that fit a laptop, all overridable. |
| State | StatefulSets with `volumeClaimTemplates` for both databases; PVCs for Chroma, Gitea, the model cache and the uploads, annotated `helm.sh/resource-policy: keep` so an uninstall does not delete data. `Recreate` strategy where a ReadWriteOnce volume is used. |
| Optional | `podDisruptionBudget.enabled`, `networkPolicy.enabled` (only the intended callers reach the databases, the AI service and Chroma), `ingress.tls.*`, `global.imagePullSecrets`, `nodeSelector`, `tolerations`, `affinity`. |
| Ollama on the host | A selector-less Service plus an EndpointSlice. One setting: `ollama.hostAddress`. |
| `helm test` | A pod calls the frontend, backend and AI service health endpoints from inside the cluster. |

## Values you will touch
`global.hosts.*` and `global.scheme`, `config.ai.llmProvider` (`ollama`, `openai_compatible`, `fake`), `config.springProfile` (`local` loads the demo seed, empty in production), `ollama.hostAddress`, `*.image.*`, `*.resources`, `*.persistence.*`, `existingSecrets.*`. See `values.yaml` (every key is commented).

## Not covered
Gitea's admin user, API token, repositories, webhook and OAuth source are created by `scripts/kind-gitea.sh` (it needs the running Gitea and writes the token into the backend Secret). TLS certificates are not issued by the chart. Keycloak runs `start-dev` (HTTP only) by default: change `keycloak.args` and the image for production. ingress-nginx is retired upstream (see `infra/k8s/ingress-nginx/README.md`); only the annotations in `templates/ingress.yaml` are controller specific.
