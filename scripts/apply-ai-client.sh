#!/usr/bin/env bash
# Adds the AI service client scopes (ai:invoke, ai:ingest) and the client portal-backend-ai to a Keycloak whose realm was
# imported BEFORE they existed. A fresh Keycloak volume gets them from keycloak/realm/baracel-realm.json by itself.
# Safe to run twice: existing items are left alone. Definitions are read from the realm file (one source of truth).
set -euo pipefail
KC="${KEYCLOAK_URL:-http://localhost:8180}"
ADMIN="${KEYCLOAK_ADMIN:-admin}"
PASS="${KEYCLOAK_ADMIN_PASSWORD:-change-me-admin}"
SECRET="${PORTAL_BACKEND_AI_CLIENT_SECRET:-change-me-backend-ai-secret}"
REALM_FILE="$(dirname "$0")/../keycloak/realm/baracel-realm.json"

TOKEN=$(curl -sf -d grant_type=password -d client_id=admin-cli -d "username=$ADMIN" --data-urlencode "password=$PASS" \
  "$KC/realms/master/protocol/openid-connect/token" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
api() { curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' "$@"; }

python3 - "$REALM_FILE" "$SECRET" > /tmp/apply-ai-client.json <<'PY'
import json, sys
realm = json.load(open(sys.argv[1]))
scopes = [s for s in realm["clientScopes"] if s["name"].startswith("ai:")]
client = next(c for c in realm["clients"] if c["clientId"] == "portal-backend-ai")
client["secret"] = sys.argv[2]
print(json.dumps({"scopes": scopes, "client": client}))
PY

python3 -c '
import json
d = json.load(open("/tmp/apply-ai-client.json"))
for i, s in enumerate(d["scopes"]): open(f"/tmp/apply-ai-scope-{i}.json", "w").write(json.dumps(s))
open("/tmp/apply-ai-client-body.json", "w").write(json.dumps(d["client"]))
print(len(d["scopes"]))' > /tmp/apply-ai-count

for f in /tmp/apply-ai-scope-*.json; do
  echo "client scope $(python3 -c "import json;print(json.load(open('$f'))['name'])"): HTTP $(api -X POST -d @"$f" "$KC/admin/realms/baracel/client-scopes") (201 created, 409 already there)"
done
echo "client portal-backend-ai: HTTP $(api -X POST -d @/tmp/apply-ai-client-body.json "$KC/admin/realms/baracel/clients") (201 created, 409 already there)"
rm -f /tmp/apply-ai-*
