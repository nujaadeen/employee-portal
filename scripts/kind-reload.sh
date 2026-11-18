#!/usr/bin/env bash
# Rebuilds one service image, loads it into the cluster and restarts the Deployment.
#   scripts/kind-reload.sh backend | frontend | ai-service | keycloak
set -euo pipefail
source "$(dirname "$0")/lib/kind-common.sh"
svc=${1:-}
[ -n "$svc" ] && [ -n "${IMAGE[$svc]:-}" ] || die "usage: $0 <backend|frontend|ai-service|keycloak>"
need docker kind kubectl
cluster_exists || die "cluster $KIND_CLUSTER does not exist: run scripts/kind-up.sh"
log "building ${IMAGE[$svc]}"
( cd "$ROOT" && DOCKER_BUILDKIT=1 docker build -q -f "${DOCKERFILE[$svc]}" -t "${IMAGE[$svc]}" . >/dev/null )
log "loading it into the cluster"
kind load docker-image --name "$KIND_CLUSTER" "${IMAGE[$svc]}"
kn rollout restart "deployment/${DEPLOYMENT[$svc]}"
kn rollout status "deployment/${DEPLOYMENT[$svc]}" --timeout=900s
log "$svc reloaded"
