#!/usr/bin/env bash
# Adds (or brings up to date) the AI service client scopes (ai:invoke, ai:ingest) and the client portal-backend-ai in a
# Keycloak whose realm was imported BEFORE they existed. A fresh Keycloak volume gets them from
# keycloak/realm/baracel-realm.json by itself. Safe to run twice. Definitions are read from the realm file.
set -euo pipefail
export KC="${KEYCLOAK_URL:-http://localhost:8180}"
export KC_ADMIN="${KEYCLOAK_ADMIN:-admin}"
export KC_PASS="${KEYCLOAK_ADMIN_PASSWORD:-change-me-admin}"
export AI_SECRET="${PORTAL_BACKEND_AI_CLIENT_SECRET:-change-me-backend-ai-secret}"
export REALM_FILE="$(dirname "$0")/../keycloak/realm/baracel-realm.json"

python3 - <<'PY'
import json, os, urllib.error, urllib.parse, urllib.request

kc = os.environ["KC"]

def call(method, path, body=None, token=None, form=None):
    data = urllib.parse.urlencode(form).encode() if form else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(kc + path, data=data, method=method)
    req.add_header("Content-Type", "application/x-www-form-urlencoded" if form else "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, None

_, tok = call("POST", "/realms/master/protocol/openid-connect/token", form={
    "grant_type": "password", "client_id": "admin-cli", "username": os.environ["KC_ADMIN"], "password": os.environ["KC_PASS"]})
token = tok["access_token"]
realm = json.load(open(os.environ["REALM_FILE"]))
scopes = [s for s in realm["clientScopes"] if s["name"].startswith("ai:")]
client = next(c for c in realm["clients"] if c["clientId"] == "portal-backend-ai")
client["secret"] = os.environ["AI_SECRET"]

for s in scopes:
    status, _ = call("POST", "/admin/realms/baracel/client-scopes", s, token)
    print(f"client scope {s['name']}: HTTP {status} (201 created, 409 already there)")
status, _ = call("POST", "/admin/realms/baracel/clients", client, token)
print(f"client portal-backend-ai: HTTP {status} (201 created, 409 already there)")

# Make sure the scopes are OPTIONAL (the caller asks for the one it needs), also on a client created by an older version.
_, found = call("GET", "/admin/realms/baracel/clients?clientId=portal-backend-ai", token=token)
cid = found[0]["id"]
_, all_scopes = call("GET", "/admin/realms/baracel/client-scopes", token=token)
for s in all_scopes:
    if s["name"].startswith("ai:"):
        call("DELETE", f"/admin/realms/baracel/clients/{cid}/default-client-scopes/{s['id']}", token=token)
        status, _ = call("PUT", f"/admin/realms/baracel/clients/{cid}/optional-client-scopes/{s['id']}", token=token)
        print(f"{s['name']} is an optional scope of portal-backend-ai: HTTP {status}")
PY
