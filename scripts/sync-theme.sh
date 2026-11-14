#!/usr/bin/env bash
# Copies the design tokens, the logos and the self-hosted fonts into the Keycloak login theme.
# Run after changing design/tokens.css or the logo. Fonts come from the npm packages in frontend/ (run `npm ci` there first).
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
res="$root/keycloak/themes/baracel/login/resources"
mkdir -p "$res/css" "$res/img" "$res/fonts"
cp "$root/design/tokens.css" "$res/css/tokens.css"
cp "$root/design/logo/baracel-logo-transparent.png" "$root/design/logo/baracel-logo-dark.png" "$res/img/"
fonts="$root/frontend/node_modules/@fontsource"
for f in inter/files/inter-latin-400-normal.woff2 inter/files/inter-latin-500-normal.woff2 inter/files/inter-latin-600-normal.woff2 \
         space-grotesk/files/space-grotesk-latin-600-normal.woff2 space-grotesk/files/space-grotesk-latin-700-normal.woff2; do
  [ -f "$fonts/$f" ] || { echo "missing $fonts/$f (run npm ci in frontend/)" >&2; exit 1; }
  cp "$fonts/$f" "$res/fonts/"
done
echo "Theme resources synced into $res"
