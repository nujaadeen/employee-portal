#!/usr/bin/env bash
# Prints a service access token for the AI service (client portal-backend-ai, client credentials). Dev helper.
# Spring does the same call: POST <issuer>/protocol/openid-connect/token with grant_type=client_credentials.
set -euo pipefail
KC="${KEYCLOAK_URL:-http://localhost:8180}"
curl -sf -d grant_type=client_credentials -d client_id=portal-backend-ai \
  --data-urlencode "client_secret=${PORTAL_BACKEND_AI_CLIENT_SECRET:-change-me-backend-ai-secret}" \
  "$KC/realms/baracel/protocol/openid-connect/token" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])'
