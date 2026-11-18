#!/usr/bin/env bash
# Demo data for the local cluster: checks that the demo users exist for every role and loads a small knowledge base for the Sales collections.
# Runs only against the local kind cluster and only when the Keycloak issuer is the local one.
set -euo pipefail
source "$(dirname "$0")/lib/kind-common.sh"
need kubectl curl python3
cluster_exists || die "cluster $KIND_CLUSTER does not exist: run scripts/kind-up.sh"
[ "$(kubectl config current-context)" = "$KIND_CONTEXT" ] || warn "your current kubectl context is $(kubectl config current-context); this script uses $KIND_CONTEXT only"
disc=$(hcurl -s --max-time 20 "http://$AUTH_HOST/realms/baracel/.well-known/openid-configuration" || true)
got=$(printf '%s' "$disc" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("issuer",""))' 2>/dev/null || true)
[ "$got" = "$ISSUER" ] || die "refusing to seed: the Keycloak issuer is '${got:-unreachable}', not the local $ISSUER"
[ "$(kn get configmap portal-config -o jsonpath='{.data.ISSUER_URL}')" = "$ISSUER" ] || die "refusing to seed: the cluster is not configured with the local issuer"

PW='ChangeMe123!'
token_for() { hcurl -s --max-time 20 -d grant_type=password -d client_id=portal-dev-cli -d "username=$1" --data-urlencode "password=$PW" "$ISSUER/protocol/openid-connect/token" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("access_token",""))'; }
api() { local tok=$1 m=$2 p=$3; shift 3; hcurl -s --max-time 60 -X "$m" -H "Authorization: Bearer $tok" "$@" "http://$PORTAL_HOST$p"; }

log "demo users and roles (imported with the Keycloak realm, matched by the portal's local seed)"
declare -a PEOPLE=("sara.qureshi:Employee" "omar.haddad:Manager" "lena.fischer:HR" "aisha.rahman:Sales" "priya.nair:Engineer" "daniel.okoye:Admin")
for pr in "${PEOPLE[@]}"; do
  u=${pr%%:*}; role=${pr#*:}
  t=$(token_for "$u"); [ -n "$t" ] || die "cannot sign in as $u: is the realm imported?"
  info=$(api "$t" GET /api/me | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["name"]+" | "+d["status"]+" | roles "+",".join(d["roles"])+" | "+str(len(d["permissions"]))+" permissions")')
  printf '    %-9s %-14s %s\n' "$role" "$u" "$info"
done
t=$(token_for omar.haddad); team=$(api "$t" GET '/api/employees?scope=team' | python3 -c 'import sys,json; d=json.load(sys.stdin); print(", ".join(e["name"] for e in d["items"]))')
log "the Manager's direct reports: $team"

log "knowledge base for the Sales collections (product, brand, campaign)"
D=$(token_for daniel.okoye); [ -n "$D" ] || die "no token for daniel.okoye"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
python3 "$ROOT/scripts/lib/kind_seed_docs.py" "$tmp"
declare -a JOBS=()
for f in "$tmp"/*.pdf; do
  base=$(basename "$f"); col=${base%%__*}; name=${base#*__}
  cp "$f" "$tmp/$name"
  res=$(hcurl -s --max-time 120 -X POST -H "Authorization: Bearer $D" -F "file=@$tmp/$name;type=application/pdf" "http://$PORTAL_HOST/api/knowledge/collections/$col/documents")
  job=$(printf '%s' "$res" | python3 -c 'import sys,json; print(json.load(sys.stdin)["jobId"])') || die "upload of $name to $col failed: $res"
  JOBS+=("$job|$col/$name")
done
for j in "${JOBS[@]}"; do
  id=${j%%|*}; label=${j#*|}; st=""
  for _ in $(seq 1 90); do st=$(api "$D" GET "/api/knowledge/jobs/$id" | python3 -c 'import sys,json; print(json.load(sys.stdin)["status"])'); case "$st" in DONE|FAILED|PARTIAL) break;; esac; sleep 2; done
  printf '    %-45s %s\n' "$label" "$st"
  [ "$st" = DONE ] || die "indexing $label ended as '$st'"
done
api "$D" GET /api/knowledge/collections | python3 -c '
import sys, json
for c in json.load(sys.stdin)["collections"]: print("    %-12s %2d documents  %s" % (c["code"], c["documents"], c["status"]))'
log "seed done"
