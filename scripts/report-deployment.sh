#!/usr/bin/env bash
# Reports a Deployment Portal step back to the backend, signed the way
# DeploymentCallbackVerifier expects:
#   X-TrackFlow-Signature: sha256=hex(HMAC_SHA256(secret, "<unix-seconds>.<body>"))
#
# Usage: report-deployment.sh STATUS [MESSAGE]
# Env:   CALLBACK_URL, CALLBACK_SECRET, DEPLOYMENT_ID, RUN_ID, RUN_URL
set -euo pipefail

status=$1
message=${2:-}
body=$(jq -nc --arg s "$status" --arg m "$message" --arg id "${RUN_ID:-}" --arg url "${RUN_URL:-}" \
  '{status: $s, message: $m, runId: $id, runUrl: $url}')
ts=$(date +%s)
sig=$(printf '%s.%s' "$ts" "$body" | openssl dgst -sha256 -hmac "$CALLBACK_SECRET" -r | cut -d' ' -f1)

echo "-> $status: $message"
curl -fsS --retry 4 --retry-all-errors --retry-delay 5 --max-time 20 \
  -X POST "${CALLBACK_URL%/}/api/v1/deployments/$DEPLOYMENT_ID/events" \
  -H 'Content-Type: application/json' \
  -H "X-TrackFlow-Timestamp: $ts" \
  -H "X-TrackFlow-Signature: sha256=$sig" \
  --data-binary "$body"
