#!/usr/bin/env bash
# Argo CD in the kind cluster: it deploys the portal's Helm chart from a local OCI registry (GitOps for the laptop).
#   scripts/kind-argocd.sh install        Argo CD (pinned), the local chart registry, the project and the repository
#   scripts/kind-argocd.sh push           package infra/helm/baracel-portal and push it to the registry (version from Chart.yaml)
#   scripts/kind-argocd.sh deploy [--mode local|public]   create or update the Application baracel-portal (values for the current mode) and wait until Synced and Healthy
#   scripts/kind-argocd.sh adopt          take over a release installed with `helm` (no downtime): remove Helm's release records, then deploy
#   scripts/kind-argocd.sh gitops <gitops-repo-url> [local|public]   hand control to the separate GitOps repo (app of apps); private repos: export GITOPS_TOKEN (and GITOPS_USER)
#   scripts/kind-argocd.sh status | ui    status of the Application | how to open the UI (port-forward, initial password command)
set -euo pipefail
source "$(dirname "$0")/lib/kind-common.sh"
ARGOCD_VERSION="${ARGOCD_VERSION:-v3.6.1}"
CHART_DIR="$ROOT/infra/helm/baracel-portal"
REG_PORT=5001
REG_INTERNAL="chart-registry.chart-registry.svc.cluster.local:5000/charts"
ka() { kubectl --context "$KIND_CONTEXT" -n argocd "$@"; }
cmd=${1:-help}; shift || true
MODE_FORCED=""; while [ $# -gt 0 ]; do case "$1" in --mode) MODE_FORCED=$2; shift 2;; *) shift;; esac; done

chart_version() { python3 -c 'import re,sys; print(re.search(r"^version:\s*(\S+)", open(sys.argv[1]).read(), re.M).group(1))' "$CHART_DIR/Chart.yaml"; }
gateway() { docker network inspect kind -f '{{range .IPAM.Config}}{{.Gateway}} {{end}}' | tr ' ' '\n' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' | head -1; }
pub_value() { [ -f "$ROOT/infra/public.env" ] || return 1; grep -E "^${1}=" "$ROOT/infra/public.env" | tail -1 | cut -d= -f2-; }

case "$cmd" in
install)
  need kubectl
  cluster_exists || die "cluster $KIND_CLUSTER does not exist"
  log "Argo CD $ARGOCD_VERSION"
  k create namespace argocd --dry-run=client -o yaml | k apply -f - >/dev/null
  k apply -n argocd --server-side --force-conflicts -f "https://raw.githubusercontent.com/argoproj/argo-cd/$ARGOCD_VERSION/manifests/install.yaml" >/dev/null
  # a laptop does not need Dex (single sign-on) or notifications
  ka scale deployment argocd-dex-server argocd-notifications-controller --replicas=0 >/dev/null
  log "local chart registry"
  k apply -f "$ROOT/infra/argocd/registry.yaml" >/dev/null
  k -n chart-registry rollout status deployment/chart-registry --timeout=180s >/dev/null
  for d in argocd-server argocd-repo-server argocd-redis argocd-applicationset-controller; do ka rollout status deployment/$d --timeout=300s >/dev/null; done
  ka rollout status statefulset/argocd-application-controller --timeout=300s >/dev/null
  k apply -f "$ROOT/infra/argocd/project.yaml" -f "$ROOT/infra/argocd/repository.yaml" >/dev/null
  log "Argo CD is ready. Next: push, then adopt (or deploy)"
  ;;
push)
  need helm kubectl
  v=$(chart_version)
  tmp=$(mktemp -d); trap 'rm -rf "$tmp"; kill $PF 2>/dev/null || true' EXIT
  helm package "$CHART_DIR" --destination "$tmp" >/dev/null
  k -n chart-registry port-forward svc/chart-registry $REG_PORT:5000 >/dev/null 2>&1 & PF=$!
  for _ in $(seq 1 20); do curl -sf "http://localhost:$REG_PORT/v2/" >/dev/null && break; sleep 1; done
  helm push "$tmp/baracel-portal-$v.tgz" "oci://localhost:$REG_PORT/charts" --plain-http 2>&1 | grep -E "Pushed|Digest" || true
  log "chart baracel-portal $v is in the registry ($REG_INTERNAL)"
  ;;
deploy|adopt)
  need kubectl python3
  v=$(chart_version)
  if [ "$cmd" = adopt ]; then
    log "taking over the Helm release (the running pods are not touched)"
    kn delete secret -l owner=helm,name=baracel-portal --ignore-not-found >/dev/null
  fi
  llm=$(kn get configmap portal-config -o jsonpath='{.data.AI_LLM_PROVIDER}')
  emb=$(kn get configmap portal-config -o jsonpath='{.data.EMBEDDING_PROVIDER}')
  mode=local
  if [ -n "$MODE_FORCED" ]; then mode=$MODE_FORCED
  elif [ "$(kn get ingress portal-frontend -o jsonpath='{.spec.rules[0].host}' 2>/dev/null)" != "portal.baracel.local" ] && [ -f "$ROOT/infra/public.env" ]; then mode=public; fi
  [ "$mode" = public ] && files="values-kind.yaml, values-public.yaml" || files="values-kind.yaml"
  vals=$(python3 - "$llm" "$emb" "$(gateway)" "$mode" "$(pub_value NGROK_DOMAIN || true)" "$(pub_value PUBLIC_MODE || echo basic)" <<'PY'
import sys
llm, emb, gw, mode, dom, pm = sys.argv[1:7]
lines = [f"        ollama: {{ hostAddress: \"{gw}\" }}", f"        config: {{ ai: {{ llmProvider: {llm}, embeddingProvider: {emb} }} }}"]
if mode == "public":
    lines.append(f"        global: {{ hosts: {{ portal: {dom}, auth: {dom} }} }}")
    if pm == "open":
        lines.append("        ingress: { basicAuth: { secretName: \"\" } }")
print("\n".join(lines))
PY
)
  log "Application baracel-portal: chart $v, values mode $mode (LLM $llm)"
  sed -e "s|@CHART_REPO@|$REG_INTERNAL|" -e "s|@CHART_VERSION@|$v|" -e "s|@VALUE_FILES@|$files|" "$ROOT/infra/argocd/application.yaml.tmpl" \
    | python3 -c 'import sys; t=sys.stdin.read(); print(t.replace("@VALUES_OBJECT@", sys.argv[1]))' "$vals" | k apply -f - >/dev/null
  ka patch application baracel-portal --type merge -p "{\"operation\":{\"sync\":{\"revision\":\"$v\"}}}" >/dev/null 2>&1 || true
  for _ in $(seq 1 90); do
    s=$(ka get application baracel-portal -o jsonpath='{.status.sync.status}/{.status.health.status}' 2>/dev/null || true)
    [ "$s" = "Synced/Healthy" ] && break; sleep 5
  done
  echo "Application baracel-portal: $s"
  [ "$s" = "Synced/Healthy" ] || { ka get application baracel-portal -o jsonpath='{range .status.conditions[*]}{.type}: {.message}{"\n"}{end}' | cut -c1-300; die "not Synced and Healthy yet (scripts/kind-argocd.sh status)"; }
  ;;
gitops)
  need kubectl
  url=${2:-}; env=${3:-local}; [ -n "$url" ] || die "usage: kind-argocd.sh gitops <gitops-repo-url> [local|public]"
  GR="$ROOT/../baracel-portal-gitops"; [ -d "$GR/bootstrap" ] || die "expected the GitOps repo checkout at $GR"
  if [ -n "${GITOPS_TOKEN:-}" ]; then
    # read access to a private repo (the application repo and the GitOps repo): a Secret in the cluster, never in Git; the token is not printed
    envf=$(mktemp); chmod 600 "$envf"; trap 'rm -f "$envf"' EXIT
    { echo "type=git"; echo "url=$url"; echo "username=${GITOPS_USER:-git}"; echo "password=$GITOPS_TOKEN"; } > "$envf"
    k -n argocd create secret generic gitops-repo --from-env-file="$envf" --dry-run=client -o yaml | k apply -f - >/dev/null
    k -n argocd label secret gitops-repo argocd.argoproj.io/secret-type=repository --overwrite >/dev/null
    log "repository credentials stored (Secret gitops-repo)"
  fi
  log "project and root application (environment $env)"
  sed "s|@GITOPS_REPO@|$url|" "$GR/bootstrap/project.yaml" | k apply -f - >/dev/null
  sed -e "s|@GITOPS_REPO@|$url|" -e "s|@ENV@|$env|" "$GR/bootstrap/root.yaml" | k apply -f - >/dev/null
  for _ in $(seq 1 60); do
    s=$(ka get application baracel-portal -o jsonpath='{.spec.source.repoURL}' 2>/dev/null || true)
    [ "$s" = "https://github.com/nujaadeen/employee-portal.git" ] && break; sleep 5
  done
  echo "root: $(ka get application root -o jsonpath='{.status.sync.status}/{.status.health.status}' 2>/dev/null)"
  ka get application baracel-portal -o jsonpath='portal: {.status.sync.status}/{.status.health.status}{"\n"}{range .status.conditions[*]}{.type}: {.message}{"\n"}{end}' | cut -c1-300
  ;;
status)
  ka get application baracel-portal -o jsonpath='sync: {.status.sync.status}  health: {.status.health.status}  revision: {.status.sync.revision}{"\n"}'
  ka get application baracel-portal -o jsonpath='{range .status.resources[?(@.status!="Synced")]}OutOfSync: {.kind}/{.name}{"\n"}{end}'
  ;;
ui)
  cat <<EOF
Open the Argo CD UI:
  kubectl --context $KIND_CONTEXT -n argocd port-forward svc/argocd-server 8443:443      # then https://localhost:8443  (accept the self-signed certificate)
  user: admin    password: run   kubectl --context $KIND_CONTEXT -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d; echo
EOF
  ;;
*) sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
