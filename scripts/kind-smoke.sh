#!/usr/bin/env bash
# Smoke test of the kind cluster. Run after scripts/kind-up.sh. One line per check: PASS, FAIL or SKIP. Exit code 1 if any check FAILS.
# A SKIP means the check could not run in this setup; it is never counted as a pass.
set -uo pipefail
source "$(dirname "$0")/lib/kind-common.sh"
need kubectl curl python3
cluster_exists || die "cluster $KIND_CLUSTER does not exist: run scripts/kind-up.sh"

FAILS=0; SKIPS=0; PASSES=0
pass() { printf '%sPASS%s  %s\n' "$C_OK" "$C_OFF" "$*"; PASSES=$((PASSES+1)); }
fail() { printf '%sFAIL%s  %s\n' "$C_BAD" "$C_OFF" "$*"; FAILS=$((FAILS+1)); }
skip() { printf '%sSKIP%s  %s\n' "$C_WARN" "$C_OFF" "$*"; SKIPS=$((SKIPS+1)); }

PW='ChangeMe123!'   # the documented dev password of the demo users in the realm file (dev only)
token_for() {       # access token of a demo user through the client portal-dev-cli (not printed)
  hcurl -s --max-time 20 -d grant_type=password -d client_id=portal-dev-cli -d "username=$1" --data-urlencode "password=$PW" \
    "http://$AUTH_HOST/realms/baracel/protocol/openid-connect/token" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null
}
api() { local tok=$1 m=$2 p=$3; shift 3; hcurl -s --max-time 60 -X "$m" -H "Authorization: Bearer $tok" "$@" "http://$PORTAL_HOST$p"; }
code() { local tok=$1 m=$2 p=$3; shift 3; hcurl -s -o /dev/null -w '%{http_code}' --max-time 60 -X "$m" ${tok:+-H "Authorization: Bearer $tok"} "$@" "http://$PORTAL_HOST$p"; }
# HTTP GET from inside a pod, using what the image has: bash for the JRE image, python for the AI service
pod_get_bash()   { kn exec "deploy/$1" -- bash -c "exec 3<>/dev/tcp/$2/$3; printf 'GET $4 HTTP/1.0\r\nHost: $2\r\n\r\n' >&3; cat <&3"; }
pod_get_python() { kn exec "deploy/$1" -- python -c "import urllib.request,sys; r=urllib.request.urlopen('$2', timeout=10); sys.stdout.write(str(r.status)+' '+r.read().decode()[:4000])"; }

LLM_MODE=$(kn get configmap portal-config -o jsonpath='{.data.AI_LLM_PROVIDER}' 2>/dev/null || echo unknown)
echo "kind smoke test (cluster $KIND_CLUSTER, ai-service LLM mode: $LLM_MODE)"

# 1. pods
notready=$(kn get pods --no-headers | awk '{split($2,a,"/"); if (a[1]!=a[2] || $3!="Running") print $1}')
n=$(kn get pods --no-headers | wc -l)
if [ -z "$notready" ] && [ "$n" -ge 9 ]; then pass "all $n pods are ready"; else fail "pods not ready: $(echo $notready | tr '\n' ' ')"; fi

# 2. frontend
body=$(hcurl -s --max-time 20 "http://$PORTAL_HOST/" || true)
if printf '%s' "$body" | grep -q 'id="root"'; then pass "frontend answers at http://$PORTAL_HOST/"; else fail "frontend does not answer at http://$PORTAL_HOST/"; fi
cfg=$(hcurl -s --max-time 20 "http://$PORTAL_HOST/config.json" || true)
if printf '%s' "$cfg" | grep -q "\"oidcAuthority\":\"$ISSUER\""; then pass "frontend runtime config points at the issuer $ISSUER"; else fail "frontend /config.json does not name the issuer"; fi

# 3. Keycloak discovery: host, backend pod, ai-service pod
disc=$(hcurl -s --max-time 20 "$ISSUER/.well-known/openid-configuration" || true)
if printf '%s' "$disc" | grep -q "\"issuer\":\"$ISSUER\""; then pass "Keycloak discovery from the host, issuer is $ISSUER"; else fail "Keycloak discovery from the host (issuer mismatch or unreachable)"; fi
out=$(pod_get_bash portal-backend "$AUTH_HOST" 80 /realms/baracel/.well-known/openid-configuration 2>&1 || true)
if printf '%s' "$out" | grep -q "\"issuer\":\"$ISSUER\""; then pass "Keycloak discovery from inside the backend pod"; else fail "Keycloak discovery from inside the backend pod: $(printf '%s' "$out" | head -c 120)"; fi
out=$(pod_get_python ai-service "$ISSUER/.well-known/openid-configuration" 2>&1 || true)
if printf '%s' "$out" | grep -q "\"issuer\":\"$ISSUER\""; then pass "Keycloak discovery from inside the ai-service pod"; else fail "Keycloak discovery from inside the ai-service pod: $(printf '%s' "$out" | head -c 120)"; fi

# 4. protected path without a token, then with a service token
c=$(code "" GET /api/me); if [ "$c" = "401" ]; then pass "unauthenticated /api/me returns 401"; else fail "unauthenticated /api/me returned $c, expected 401"; fi
AISHA=$(token_for aisha.rahman); LENA=$(token_for lena.fischer); DANIEL=$(token_for daniel.okoye)
if [ -z "$AISHA" ] || [ -z "$LENA" ] || [ -z "$DANIEL" ]; then fail "tokens from portal-dev-cli for the demo users"; else
  pass "tokens from portal-dev-cli for the demo users"
  who=$(api "$AISHA" GET /api/me | python3 -c 'import sys,json; print(json.load(sys.stdin).get("name",""))' 2>/dev/null)
  if [ "$who" = "Aisha Rahman" ]; then pass "a portal-dev-cli token is accepted by the backend (GET /api/me: $who)"; else fail "GET /api/me with a portal-dev-cli token: '$who'"; fi
  cfgapi=$(api "$AISHA" GET /api/config | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("giteaUrl",""))' 2>/dev/null)
  if [ "$cfgapi" = "http://$GIT_HOST" ]; then pass "GET /api/config works (giteaUrl $cfgapi)"; else fail "GET /api/config: giteaUrl '$cfgapi', expected http://$GIT_HOST"; fi
fi

# 5. backend -> ai-service
out=$(pod_get_bash portal-backend ai-service 8000 /healthz 2>&1 || true)
if printf '%s' "$out" | grep -q '"status":"ok"'; then pass "the backend reaches the ai-service health endpoint"; else fail "the backend does not reach the ai-service: $(printf '%s' "$out" | head -c 120)"; fi

# 6. ai-service -> LLM
ready=$(pod_get_python ai-service http://127.0.0.1:8000/readyz 2>&1 || true)
if printf '%s' "$ready" | grep -q '"llm":true'; then pass "the ai-service LLM provider is ready (mode: $LLM_MODE)"; else fail "the ai-service LLM provider is not ready (mode: $LLM_MODE): $(printf '%s' "$ready" | head -c 160)"; fi
if [ "$LLM_MODE" = "ollama" ]; then
  out=$(pod_get_python ai-service http://ollama:11434/api/tags 2>&1 || true)
  if printf '%s' "$out" | grep -q '^200'; then pass "the ai-service reaches Ollama on the Docker host"; else fail "the ai-service cannot reach Ollama (docs/kind.md: Ollama not reachable from the cluster)"; fi
else
  skip "the ai-service reaches Ollama: not tested, the cluster runs the $LLM_MODE LLM provider instead"
fi

# 7. a streamed chat answer through the backend
if [ -n "${AISHA:-}" ]; then
  conv=$(api "$AISHA" POST /api/ai/conversations -H 'Content-Type: application/json' -d '{"title":"smoke"}' | python3 -c 'import sys,json; print(json.load(sys.stdin).get("id",""))' 2>/dev/null)
  stream=$(hcurl -sN --max-time 180 -X POST -H "Authorization: Bearer $AISHA" -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
    -d '{"message":"How do I pair the Lantern Pro with my phone?"}' "http://$PORTAL_HOST/api/ai/conversations/$conv/messages" || true)
  tokens=$(printf '%s' "$stream" | grep -c '^event:\s*token' || true)
  if [ "$tokens" -ge 1 ] && printf '%s' "$stream" | grep -q '^event:\s*done'; then pass "a chat request through the backend streams an answer ($tokens token events, then done; LLM mode $LLM_MODE)"; else fail "no streamed answer through the backend: $(printf '%s' "$stream" | head -c 200)"; fi
fi

# 8. access: a person without the collection never gets text from it
if [ -n "${DANIEL:-}" ]; then
  tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
  MARK="Zebraquartz$(date +%s)"
  python3 "$ROOT/scripts/lib/minipdf.py" "$tmp/smoke-access.pdf" "The $MARK codename belongs to the secret Lantern battery supplier agreement.|Only people with product knowledge may read about $MARK."
  up=$(hcurl -s --max-time 60 -X POST -H "Authorization: Bearer $DANIEL" -F "file=@$tmp/smoke-access.pdf;type=application/pdf" "http://$PORTAL_HOST/api/knowledge/collections/product/documents")
  job=$(printf '%s' "$up" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("jobId",""))' 2>/dev/null); doc=$(printf '%s' "$up" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("documentId",""))' 2>/dev/null)
  st=""; for _ in $(seq 1 60); do st=$(api "$DANIEL" GET "/api/knowledge/jobs/$job" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("status",""))' 2>/dev/null); [ "$st" = DONE ] || [ "$st" = FAILED ] && break; sleep 2; done
  if [ "$st" != DONE ]; then fail "access test: the marker document was not indexed (job status '$st')"; else
    ask() { # ask <token> -> prints the JSON of the assistant message (non streaming)
      local c; c=$(api "$1" POST /api/ai/conversations -H 'Content-Type: application/json' -d '{}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
      api "$1" POST "/api/ai/conversations/$c/messages" -H 'Content-Type: application/json' -d "{\"message\":\"What is the $MARK codename about?\"}"
    }
    sources_of() { python3 -c 'import sys,json; m=json.load(sys.stdin)["assistantMessage"]; print(m["content"]+"|"+",".join(s["collection"]+"/"+s["title"] for s in m["sources"]))' 2>/dev/null; }
    a=$(ask "$AISHA" | sources_of); l=$(ask "$LENA" | sources_of)
    if printf '%s' "$a" | grep -q 'product/smoke-access.pdf'; then pass "access: Sales (Aisha) gets the product document as a source"; else fail "access: Aisha got no source from the product collection: $a"; fi
    if printf '%s' "$l" | grep -qi "product/\|$MARK\|battery supplier"; then fail "access: Lena (HR, no product access) received product text or a product source: $l"; else pass "access: Lena (HR) gets no source and no text from the product collection"; fi
  fi
  [ -n "$doc" ] && api "$DANIEL" DELETE "/api/knowledge/collections/product/documents/$doc" >/dev/null
fi

echo "---- $PASSES passed, $FAILS failed, $SKIPS skipped (a skipped check is not a pass)"
[ "$FAILS" -eq 0 ]
