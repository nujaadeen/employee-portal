#!/bin/sh
# Runs when the container starts (nginx image hook). Puts the runtime settings into the built bundle and into /config.json.
# Settings come from environment variables: VITE_API_URL, VITE_OIDC_AUTHORITY, VITE_OIDC_CLIENT_ID.
set -eu
html=/usr/share/nginx/html
# values are escaped for sed: the delimiter is |
esc() { printf '%s' "$1" | sed -e 's/[\\&|]/\\&/g'; }
api=$(esc "$VITE_API_URL"); auth=$(esc "$VITE_OIDC_AUTHORITY"); cid=$(esc "$VITE_OIDC_CLIENT_ID")
for f in "$html"/assets/*.js; do
  # idempotent: placeholders exist only in a fresh image; after a restart the container filesystem is fresh again
  sed -i -e "s|__VITE_API_URL__|$api|g" -e "s|__VITE_OIDC_AUTHORITY__|$auth|g" -e "s|__VITE_OIDC_CLIENT_ID__|$cid|g" "$f"
done
printf '{"apiUrl":"%s","oidcAuthority":"%s","oidcClientId":"%s"}\n' "$VITE_API_URL" "$VITE_OIDC_AUTHORITY" "$VITE_OIDC_CLIENT_ID" > "$html/config.json"
echo "frontend runtime config: api=$VITE_API_URL authority=$VITE_OIDC_AUTHORITY client=$VITE_OIDC_CLIENT_ID"
