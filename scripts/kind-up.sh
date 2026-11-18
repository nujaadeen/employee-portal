#!/usr/bin/env bash
# Creates (or updates) the local Kubernetes cluster with the whole portal. Safe to run twice.
#   scripts/kind-up.sh
# Options (environment):
#   KIND_SKIP_BUILD=1        do not rebuild the images (they must exist locally)
#   KIND_LLM_PROVIDER=fake   ai-service uses the fake LLM (instant canned answers, no Ollama). Default: ollama
#   OLLAMA_HOST_ADDR=<ip>    address of the Docker host as the cluster sees it (default: found automatically)
#   KIND_WAIT_MINUTES=40     how long to wait for everything to be ready (the first start downloads a 2.3 GB model)
set -euo pipefail
source "$(dirname "$0")/lib/kind-common.sh"
need docker kind kubectl python3 curl openssl

# ---- 1. preflight ----
docker info >/dev/null 2>&1 || die "Docker is not running"
if ! cluster_exists && (ss -ltn 2>/dev/null | awk '{print $4}' | grep -Eq '(^|:)(80|443)$'); then
  die "port 80 or 443 on this machine is in use, so the cluster cannot publish them. Stop what uses it (ss -ltnp | grep ':80 ') and run again."
fi
for h in "$PORTAL_HOST" "$AUTH_HOST" "$GIT_HOST"; do
  if ! getent hosts "$h" 2>/dev/null | grep -q '127.0.0.1'; then HOSTS_MISSING=1; fi
done
[ -n "${HOSTS_MISSING:-}" ] && warn "your hosts file does not map the baracel.local names. Add this line (needs administrator rights):  127.0.0.1 $PORTAL_HOST $AUTH_HOST $GIT_HOST   (the scripts work without it; a browser does not)"

# ---- 2. secrets file ----
"$ROOT/scripts/kind-init-env.sh"

# ---- 3. cluster ----
if cluster_exists; then log "cluster $KIND_CLUSTER exists"; else
  log "creating the kind cluster"
  kind create cluster --config "$ROOT/infra/k8s/kind-config.yaml" --wait 120s
fi
k cluster-info >/dev/null

# ---- 4. ingress-nginx (pinned, vendored; see infra/k8s/ingress-nginx/README.md for its retired status) ----
log "installing ingress-nginx $INGRESS_NGINX_VERSION"
k apply -f "$INGRESS_MANIFEST" >/dev/null
k wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=240s >/dev/null
k wait --namespace ingress-nginx --for=condition=complete job --selector=app.kubernetes.io/component=admission-webhook --timeout=240s >/dev/null 2>&1 || true

# ---- 5. CoreDNS: the three host names resolve to the ingress controller INSIDE the cluster ----
# Why a CoreDNS rewrite and not hostAliases: hostAliases need the controller's IP in every pod spec (it changes with every cluster), and
# every new workload would need them again. The rewrite is one place and covers all pods, including Gitea's own calls to Keycloak.
log "CoreDNS: $AUTH_HOST, $PORTAL_HOST and $GIT_HOST -> the ingress controller"
k -n kube-system get configmap coredns -o json | python3 -c '
import json, sys
cm = json.load(sys.stdin)
core = cm["data"]["Corefile"]
if "baracel-rewrite" not in core:
    target = "ingress-nginx-controller.ingress-nginx.svc.cluster.local"
    rules = "    # baracel-rewrite\n" + "".join(f"    rewrite name {h} {target}\n" for h in sys.argv[1:])
    core = core.replace("    kubernetes ", rules + "    kubernetes ", 1)
    cm["data"]["Corefile"] = core
    json.dump(cm, sys.stdout)
else:
    sys.exit(3)' "$AUTH_HOST" "$PORTAL_HOST" "$GIT_HOST" > /tmp/coredns-new.json && {
  k apply -f /tmp/coredns-new.json >/dev/null; k -n kube-system rollout restart deployment/coredns >/dev/null
  k -n kube-system rollout status deployment/coredns --timeout=120s >/dev/null; } || log "CoreDNS already has the rewrite"
rm -f /tmp/coredns-new.json

# ---- 6. images ----
if [ -z "${KIND_SKIP_BUILD:-}" ]; then
  for svc in keycloak frontend ai-service backend; do
    log "building ${IMAGE[$svc]}"
    ( cd "$ROOT" && DOCKER_BUILDKIT=1 docker build -q -f "${DOCKERFILE[$svc]}" -t "${IMAGE[$svc]}" . >/dev/null )
  done
fi
for svc in "${!IMAGE[@]}"; do docker image inspect "${IMAGE[$svc]}" >/dev/null 2>&1 || die "image ${IMAGE[$svc]} not found: run without KIND_SKIP_BUILD"; done
log "loading the images into the cluster (kind load docker-image)"
kind load docker-image --name "$KIND_CLUSTER" "${IMAGE[@]}"
# third-party images the cluster would otherwise download again, when they are already here
for img in postgres:16.14 gitea/gitea:1.24.6-rootless chromadb/chroma:1.5.9 axllent/mailpit:v1.32; do
  docker image inspect "$img" >/dev/null 2>&1 && kind load docker-image --name "$KIND_CLUSTER" "$img" >/dev/null 2>&1 || true
done

# ---- 7. secrets, Ollama endpoint, manifests ----
"$ROOT/scripts/kind-secrets.sh"

OLLAMA_PORT="${OLLAMA_PORT:-11434}"
if [ -z "${OLLAMA_HOST_ADDR:-}" ]; then
  OLLAMA_HOST_ADDR=$(docker network inspect kind -f '{{range .IPAM.Config}}{{.Gateway}} {{end}}' 2>/dev/null | tr ' ' '\n' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' | head -1 || true)
fi
[ -n "$OLLAMA_HOST_ADDR" ] || die "cannot find the Docker host address. Export OLLAMA_HOST_ADDR=<ip> (see docs/kind.md)"
log "Ollama is expected on the Docker host at $OLLAMA_HOST_ADDR:$OLLAMA_PORT"
sed -e "s|@OLLAMA_HOST_ADDR@|$OLLAMA_HOST_ADDR|" -e "s|@OLLAMA_PORT@|$OLLAMA_PORT|" "$ROOT/infra/k8s/overlays/local/ollama-endpoints.yaml.tmpl" > /tmp/ollama-endpoints.yaml

log "applying the manifests (Kustomize overlay local)"
if [ -n "${KIND_LLM_PROVIDER:-}" ]; then
  # one-off override of the provider switch without editing the overlay
  k kustomize "$ROOT/infra/k8s/overlays/local" | sed "s|AI_LLM_PROVIDER: ollama|AI_LLM_PROVIDER: ${KIND_LLM_PROVIDER}|; s|AI_LLM_PROVIDER: \"ollama\"|AI_LLM_PROVIDER: \"${KIND_LLM_PROVIDER}\"|" | k apply -f - >/dev/null
else
  k apply -k "$ROOT/infra/k8s/overlays/local" >/dev/null
fi
k apply -f /tmp/ollama-endpoints.yaml >/dev/null; rm -f /tmp/ollama-endpoints.yaml
# configuration changes are picked up at start: restart what reads the ConfigMap when the pods are older than the change
log "provider switch: AI_LLM_PROVIDER=$(kn get configmap portal-config -o jsonpath='{.data.AI_LLM_PROVIDER}')"

# ---- 8. wait ----
log "waiting for everything to be ready (the first start downloads the embedding model into a volume: this can take 10 to 30 minutes)"
deadline=$(( $(date +%s) + ${KIND_WAIT_MINUTES:-40} * 60 ))
while :; do
  not_ready=$(kn get pods --no-headers 2>/dev/null | awk '{split($2,a,"/"); if (a[1]!=a[2] || $3!="Running") print $1" "$2" "$3}' || true)
  total=$(kn get pods --no-headers 2>/dev/null | wc -l)
  if [ "$total" -ge 9 ] && [ -z "$not_ready" ]; then break; fi
  [ "$(date +%s)" -lt "$deadline" ] || { kn get pods; die "timed out waiting for the pods (see docs/kind.md, Troubleshooting)"; }
  progress=$(kn logs deploy/ai-service --tail=1 2>/dev/null | grep -o 'embedding model download.*' || true)
  printf '    %s pods, %s not ready yet%s\n' "$total" "$(printf '%s\n' "$not_ready" | grep -c . || true)" "${progress:+ | ai-service: $progress}"
  sleep 20
done
log "all pods are ready"

# ---- 9. Gitea: admin, token, repositories, webhook, Keycloak sign-in ----
"$ROOT/scripts/kind-gitea.sh"

cat <<EOF

The portal is up.
  Portal        http://$PORTAL_HOST/
  Keycloak      http://$AUTH_HOST/        (admin console: /admin/master/console/)
  Gitea         http://$GIT_HOST/
  Next          scripts/kind-smoke.sh   then   scripts/kind-seed.sh
  Test logins   aisha.rahman (Sales), omar.haddad (Manager), lena.fischer (HR), priya.nair (Engineer), daniel.okoye (Admin): password ChangeMe123! (dev only)
EOF
