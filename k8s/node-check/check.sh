#!/bin/sh
set -eu

: "${NODE_NAME:?NODE_NAME must be set by the Downward API}"
CHECK_URL="${CHECK_URL:-http://frontend/healthz}"
CHECK_INTERVAL="${CHECK_INTERVAL:-30}"
case "$CHECK_INTERVAL" in
  ''|*[!0-9]*|0) echo 'CHECK_INTERVAL must be a positive integer' >&2; exit 1 ;;
esac
[ "$CHECK_INTERVAL" -gt 0 ] || { echo 'CHECK_INTERVAL must be positive' >&2; exit 1; }
trap 'exit 0' TERM INT

# 从当前节点上的 Pod 检查 Service 的 DNS 与 HTTP 可达性。
# FAIL 表示本次检查失败，不表示已证实节点硬件或 kubelet 故障。
while :; do
  checked_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  if wget -q -T 5 -O /dev/null "$CHECK_URL"; then
    result=PASS
  else
    result=FAIL
  fi
  printf '{"node":"%s","checkedAt":"%s","check":"frontend-service-http","result":"%s"}\n' \
    "$NODE_NAME" "$checked_at" "$result"
  date +%s > /tmp/node-check-heartbeat
  [ "${CHECK_ONCE:-false}" = true ] && exit 0
  sleep "$CHECK_INTERVAL" &
  wait $! || true
done
