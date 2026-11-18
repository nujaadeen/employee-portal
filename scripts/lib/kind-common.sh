#!/usr/bin/env bash
# Shared settings and helpers for the kind scripts. Source it; do not run it. Never prints secret values.

KIND_CLUSTER="${KIND_CLUSTER:-baracel}"
KIND_CONTEXT="kind-${KIND_CLUSTER}"
NS="baracel-portal"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/infra/.env}"

PORTAL_HOST="portal.baracel.local"
AUTH_HOST="auth.baracel.local"
GIT_HOST="git.baracel.local"
ISSUER="http://${AUTH_HOST}/realms/baracel"
INGRESS_NGINX_VERSION="1.15.1"
INGRESS_MANIFEST="$ROOT/infra/k8s/ingress-nginx/deploy-controller-v${INGRESS_NGINX_VERSION}.yaml"

# service name -> image, Dockerfile
declare -A IMAGE=( [backend]=baracel/portal-backend:local [frontend]=baracel/portal-frontend:local [ai-service]=baracel/ai-service:local [keycloak]=baracel/keycloak:local )
declare -A DOCKERFILE=( [backend]=backend/Dockerfile [frontend]=frontend/Dockerfile [ai-service]=ai-service/Dockerfile.slim [keycloak]=infra/k8s/images/keycloak/Dockerfile )
declare -A DEPLOYMENT=( [backend]=portal-backend [frontend]=portal-frontend [ai-service]=ai-service [keycloak]=keycloak )

if [ -t 1 ]; then C_OK=$'\e[32m'; C_BAD=$'\e[31m'; C_WARN=$'\e[33m'; C_OFF=$'\e[0m'; else C_OK=; C_BAD=; C_WARN=; C_OFF=; fi
log()  { printf '==> %s\n' "$*"; }
warn() { printf '%s!! %s%s\n' "$C_WARN" "$*" "$C_OFF" >&2; }
die()  { printf '%sERROR: %s%s\n' "$C_BAD" "$*" "$C_OFF" >&2; exit 1; }

need() { for c in "$@"; do command -v "$c" >/dev/null 2>&1 || die "$c is required but not installed"; done; }
k()    { kubectl --context "$KIND_CONTEXT" "$@"; }
kn()   { kubectl --context "$KIND_CONTEXT" -n "$NS" "$@"; }

cluster_exists() { kind get clusters 2>/dev/null | grep -qx "$KIND_CLUSTER"; }

# curl that reaches the ingress on this machine whether or not the hosts file has the baracel.local names
hcurl() { curl --resolve "${PORTAL_HOST}:80:127.0.0.1" --resolve "${AUTH_HOST}:80:127.0.0.1" --resolve "${GIT_HOST}:80:127.0.0.1" "$@"; }

# reads KEY from infra/.env without sourcing the file (no code runs) and without printing it
env_value() {
  [ -f "$ENV_FILE" ] || return 1
  local line; line=$(grep -E "^${1}=" "$ENV_FILE" | tail -1 || true)
  line=${line#*=}; line=${line%$'\r'}
  line=${line#\"}; line=${line%\"}; line=${line#\'}; line=${line%\'}
  printf '%s' "$line"
}
