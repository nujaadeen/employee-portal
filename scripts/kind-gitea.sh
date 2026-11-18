#!/usr/bin/env bash
# Sets Gitea up for the portal with the CLUSTER values: admin user, API token (stored in the backend Secret), organisation and repositories,
# the webhook to the backend's cluster service name, and the OAuth2 source "baracel" that signs in through the Keycloak issuer.
# Idempotent. Passwords and tokens are passed over stdin or files, never on a command line, and never printed.
set -euo pipefail
source "$(dirname "$0")/lib/kind-common.sh"
need kubectl curl python3
cluster_exists || die "cluster $KIND_CLUSTER does not exist"
ADMIN=baracel-admin
ADMIN_PW=$(kn get secret gitea-secrets -o jsonpath='{.data.GITEA_ADMIN_PASSWORD}' | base64 -d)
OIDC_SECRET=$(kn get secret gitea-secrets -o jsonpath='{.data.GITEA_OIDC_CLIENT_SECRET}' | base64 -d)
HOOK_SECRET=$(kn get secret gitea-secrets -o jsonpath='{.data.GITEA_WEBHOOK_SECRET}' | base64 -d)
HOOK_URL="http://portal-backend.${NS}.svc.cluster.local:8080/api/integrations/gitea/webhook"
GITEA="http://${GIT_HOST}"
gexec() { kn exec -i deploy/gitea -- "$@"; }

log "Gitea: admin user"
if gexec gitea admin user list 2>/dev/null | awk 'NR>1{print $2}' | grep -qx "$ADMIN"; then log "  $ADMIN exists"; else
  printf '%s' "$ADMIN_PW" | gexec sh -c 'read -r PW; gitea admin user create --admin --username baracel-admin --email admin@baracel.example --must-change-password=false --password "$PW"' >/dev/null
  log "  created $ADMIN"
fi

log "Gitea: API token for the portal backend"
CURRENT=$(kn get secret backend-secrets -o jsonpath='{.data.GITEA_TOKEN}' | base64 -d 2>/dev/null || true)
code=000
[ -n "$CURRENT" ] && code=$(hcurl -s -o /dev/null -w '%{http_code}' -H "Authorization: token $CURRENT" "$GITEA/api/v1/user" || true)
TOKEN="$CURRENT"; CHANGED=0
if [ "$code" != "200" ]; then
  TOKEN=$(gexec gitea admin user generate-access-token --username "$ADMIN" --token-name "portal-$(date +%s)" --scopes all --raw | tr -d '\r\n')
  [ -n "$TOKEN" ] || die "Gitea did not return a token"
  tmp=$(mktemp); chmod 600 "$tmp"
  python3 - "$TOKEN" > "$tmp" <<'PY'
import base64, json, sys
print(json.dumps({"data": {"GITEA_TOKEN": base64.b64encode(sys.argv[1].encode()).decode()}}))
PY
  kn patch secret backend-secrets --type merge --patch-file "$tmp" >/dev/null; rm -f "$tmp"
  CHANGED=1; log "  new token stored in the backend Secret"
else log "  stored token still works"; fi

api() { local m=$1 p=$2; shift 2; hcurl -s -X "$m" -H "Authorization: token $TOKEN" -H 'Content-Type: application/json' "$@" "$GITEA/api/v1$p"; }

log "Gitea: organisation, repositories, webhooks"
api POST /orgs -d '{"username":"baracel"}' >/dev/null || true       # exists: Gitea answers 422, ignored
for repo in otp-service portal; do
  api POST /orgs/baracel/repos -d "{\"name\":\"$repo\",\"auto_init\":true,\"default_branch\":\"main\"}" >/dev/null || true
  # the portal backend must get the cluster URL: remove hooks that point elsewhere (an older run), add the right one
  hooks=$(api GET "/repos/baracel/$repo/hooks")
  has=0
  for row in $(printf '%s' "$hooks" | python3 -c '
import json, sys
for h in json.load(sys.stdin): print(h["id"], h["config"].get("url", ""))' | tr ' ' '|'); do
    id=${row%%|*}; url=${row#*|}
    if [ "$url" = "$HOOK_URL" ]; then has=1; else api DELETE "/repos/baracel/$repo/hooks/$id" >/dev/null; fi
  done
  if [ "$has" = 0 ]; then
    python3 - "$HOOK_URL" "$HOOK_SECRET" > /tmp/hook.json <<'PY'
import json, sys
print(json.dumps({"type": "gitea", "active": True, "config": {"url": sys.argv[1], "content_type": "json", "secret": sys.argv[2]},
  "events": ["push", "create", "pull_request", "pull_request_review_approved", "pull_request_review_rejected", "pull_request_review_comment", "pull_request_review_request", "status"]}))
PY
    chmod 600 /tmp/hook.json
    api POST "/repos/baracel/$repo/hooks" --data @/tmp/hook.json >/dev/null; rm -f /tmp/hook.json
    log "  webhook on baracel/$repo -> $HOOK_URL"
  fi
done

log "Gitea: sign in through Keycloak (OAuth2 source baracel, issuer $ISSUER)"
DISCOVER="$ISSUER/.well-known/openid-configuration"
id=$(gexec gitea admin auth list 2>/dev/null | awk '$2=="baracel"{print $1}' | head -1 || true)
if [ -n "$id" ]; then
  printf '%s' "$OIDC_SECRET" | gexec sh -c "read -r S; gitea admin auth update-oauth --id $id --name baracel --provider openidConnect --key gitea --secret \"\$S\" --auto-discover-url '$DISCOVER' --scopes openid --scopes email --scopes profile" >/dev/null
  log "  updated source $id"
else
  printf '%s' "$OIDC_SECRET" | gexec sh -c "read -r S; gitea admin auth add-oauth --name baracel --provider openidConnect --key gitea --secret \"\$S\" --auto-discover-url '$DISCOVER' --scopes openid --scopes email --scopes profile" >/dev/null
  log "  added source baracel"
fi

if [ "$CHANGED" = 1 ]; then
  log "restarting the backend to pick up the Gitea token"
  kn rollout restart deployment/portal-backend >/dev/null
  kn rollout status deployment/portal-backend --timeout=600s >/dev/null
fi
log "Gitea is set up"
