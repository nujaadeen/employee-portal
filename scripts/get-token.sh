#!/usr/bin/env bash
# Prints an access token for a dev user through the dev-only client portal-dev-cli.
# Usage: scripts/get-token.sh aisha.rahman [password]
set -euo pipefail
user="${1:?usage: get-token.sh <username> [password]}"
pass="${2:-ChangeMe123!}"
kc="${KEYCLOAK_URL:-http://localhost:8180}"
curl -fsS "$kc/realms/baracel/protocol/openid-connect/token" \
  -d client_id=portal-dev-cli -d grant_type=password \
  --data-urlencode "username=$user" --data-urlencode "password=$pass" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])'
