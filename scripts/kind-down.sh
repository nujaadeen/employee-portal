#!/usr/bin/env bash
# Deletes the kind cluster. All cluster data (databases, Gitea repositories, the embedding model cache) goes with it.
set -euo pipefail
source "$(dirname "$0")/lib/kind-common.sh"
need kind
if cluster_exists; then log "deleting cluster $KIND_CLUSTER"; kind delete cluster --name "$KIND_CLUSTER"; else log "cluster $KIND_CLUSTER does not exist"; fi
