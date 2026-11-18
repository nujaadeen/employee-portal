#!/usr/bin/env bash
# The two things that run on this machine OUTSIDE the cluster and die with a reboot or a closed session:
#   Ollama bound to the kind network's gateway address (so pods can reach it; not 0.0.0.0), and the ngrok tunnel.
#   scripts/kind-host-services.sh start | stop | status
set -euo pipefail
source "$(dirname "$0")/lib/kind-common.sh"
OLLAMA_PID=/tmp/baracel-ollama.pid
gw() { docker network inspect kind -f '{{range .IPAM.Config}}{{.Gateway}} {{end}}' | tr ' ' '\n' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' | head -1; }
up() { [ -f "$1" ] && kill -0 "$(cat "$1")" 2>/dev/null; }
case "${1:-status}" in
start)
  need docker ollama
  G=$(gw); [ -n "$G" ] || die "the docker network kind does not exist: run scripts/kind-up.sh"
  if curl -s -m2 "http://$G:11434/api/tags" >/dev/null 2>&1; then log "Ollama already answers on $G:11434"; else
    OLLAMA_HOST="$G:11434" setsid nohup ollama serve > /tmp/baracel-ollama.log 2>&1 < /dev/null & echo $! > "$OLLAMA_PID"
    for _ in $(seq 1 20); do curl -s -m2 "http://$G:11434/api/tags" >/dev/null 2>&1 && break; sleep 1; done
    curl -s -m2 "http://$G:11434/api/tags" >/dev/null 2>&1 || die "Ollama did not start (/tmp/baracel-ollama.log)"
    log "Ollama started on $G:11434"
  fi
  [ -f "$ROOT/infra/public.env" ] && "$ROOT/scripts/kind-public.sh" start || log "no infra/public.env: the public tunnel is not started"
  ;;
stop)
  up "$OLLAMA_PID" && { kill "$(cat "$OLLAMA_PID")"; rm -f "$OLLAMA_PID"; log "Ollama stopped"; }
  "$ROOT/scripts/kind-public.sh" stop
  ;;
status)
  G=$(gw || true)
  curl -s -m2 "http://$G:11434/api/tags" >/dev/null 2>&1 && echo "Ollama: answers on $G:11434" || echo "Ollama: not reachable on ${G:-?}:11434"
  "$ROOT/scripts/kind-public.sh" status
  ;;
*) echo "usage: $0 start|stop|status"; exit 1;;
esac
