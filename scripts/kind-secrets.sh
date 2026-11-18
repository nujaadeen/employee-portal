#!/usr/bin/env bash
# Creates the Kubernetes Secrets from infra/.env. Refuses to run when a required value is empty (or still a "change-me" placeholder).
# Never prints a value. Safe to run again: Secrets are updated in place; a real GITEA_TOKEN already in the cluster is kept.
set -euo pipefail
source "$(dirname "$0")/lib/kind-common.sh"
need kubectl
[ -f "$ENV_FILE" ] || die "infra/.env not found. Run scripts/kind-init-env.sh (random secrets) or copy infra/.env.example and fill it."

REQUIRED=(POSTGRES_PASSWORD KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD PORTAL_SERVICE_CLIENT_SECRET PORTAL_BACKEND_AI_CLIENT_SECRET GITEA_OIDC_CLIENT_SECRET GITEA_WEBHOOK_SECRET GITEA_ADMIN_PASSWORD SOCIAL_TOKEN_KEY)
bad=()
for key in "${REQUIRED[@]}"; do
  v=$(env_value "$key" || true)
  if [ -z "$v" ]; then bad+=("$key is empty or missing")
  elif [[ "$v" == change-me* ]]; then bad+=("$key is still a change-me placeholder"); fi
done
if [ "${#bad[@]}" -gt 0 ]; then
  printf '%s\n' "${bad[@]}" >&2
  die "refusing to create Secrets: fix these values in infra/.env (scripts/kind-init-env.sh generates a complete file)"
fi
# the key must be 32 bytes in base64 (AES-256)
[ "$(printf '%s' "$(env_value SOCIAL_TOKEN_KEY)" | base64 -d 2>/dev/null | wc -c)" -eq 32 ] || die "SOCIAL_TOKEN_KEY must be 32 bytes, base64 (openssl rand -base64 32)"

k create namespace "$NS" --dry-run=client -o yaml | k apply -f - >/dev/null

# create or update one Secret. Values go through a temporary env file (mode 600, removed at once), never through the command line.
apply_secret() {
  local name=$1; shift
  local tmp; tmp=$(mktemp); chmod 600 "$tmp"
  for key in "$@"; do printf '%s=%s\n' "${key%%:*}" "$(eval "${key#*:}")" >> "$tmp"; done
  kn create secret generic "$name" --from-env-file="$tmp" --dry-run=client -o yaml | kn apply -f - >/dev/null
  rm -f "$tmp"
  echo "secret/$name applied"
}
v()  { env_value "$1"; }
opt() { local x; x=$(env_value "$1" || true); printf '%s' "${x:-unset}"; }   # optional social sign-in values: the realm file needs some value

existing_token=""
if kn get secret backend-secrets >/dev/null 2>&1; then existing_token=$(kn get secret backend-secrets -o jsonpath='{.data.GITEA_TOKEN}' | base64 -d 2>/dev/null || true); fi
TOKEN_VALUE=${existing_token:-pending-bootstrap}   # replaced by scripts/kind-gitea.sh once Gitea runs

apply_secret postgres-credentials  "POSTGRES_PASSWORD:v POSTGRES_PASSWORD"
apply_secret keycloak-secrets      "KEYCLOAK_ADMIN:v KEYCLOAK_ADMIN" "KEYCLOAK_ADMIN_PASSWORD:v KEYCLOAK_ADMIN_PASSWORD"
apply_secret keycloak-realm-secrets "PORTAL_SERVICE_CLIENT_SECRET:v PORTAL_SERVICE_CLIENT_SECRET" "PORTAL_BACKEND_AI_CLIENT_SECRET:v PORTAL_BACKEND_AI_CLIENT_SECRET" \
  "GITEA_OIDC_CLIENT_SECRET:v GITEA_OIDC_CLIENT_SECRET" "GOOGLE_CLIENT_ID:opt GOOGLE_CLIENT_ID" "GOOGLE_CLIENT_SECRET:opt GOOGLE_CLIENT_SECRET" \
  "GITHUB_CLIENT_ID:opt GITHUB_CLIENT_ID" "GITHUB_CLIENT_SECRET:opt GITHUB_CLIENT_SECRET" "FACEBOOK_CLIENT_ID:opt FACEBOOK_CLIENT_ID" "FACEBOOK_CLIENT_SECRET:opt FACEBOOK_CLIENT_SECRET"
apply_secret backend-secrets       "PORTAL_SERVICE_CLIENT_SECRET:v PORTAL_SERVICE_CLIENT_SECRET" "PORTAL_BACKEND_AI_CLIENT_SECRET:v PORTAL_BACKEND_AI_CLIENT_SECRET" \
  "GITEA_WEBHOOK_SECRET:v GITEA_WEBHOOK_SECRET" "SOCIAL_TOKEN_KEY:v SOCIAL_TOKEN_KEY" "GITEA_TOKEN:printf %s \"\$TOKEN_VALUE\""
apply_secret gitea-secrets         "GITEA_ADMIN_PASSWORD:v GITEA_ADMIN_PASSWORD" "GITEA_OIDC_CLIENT_SECRET:v GITEA_OIDC_CLIENT_SECRET" "GITEA_WEBHOOK_SECRET:v GITEA_WEBHOOK_SECRET"
