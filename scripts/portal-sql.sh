#!/usr/bin/env bash
# Runs one SQL statement on the cloud's MySQL server from inside the cluster
# (the database is not reachable from the internet). Credentials come from the
# namespace's backend-secrets Secret. Used by .github/workflows/provision.yml.
#
# Usage: portal-sql.sh NAMESPACE SQL
# Env:   DB_HOST
set -euo pipefail

ns=$1
sql=$2
job="db-sql-$(date +%s)"

jq -n --arg job "$job" --arg host "$DB_HOST" --arg sql "$sql" '{
  apiVersion: "batch/v1", kind: "Job",
  metadata: {name: $job},
  spec: {
    backoffLimit: 2, ttlSecondsAfterFinished: 600,
    template: {spec: {
      restartPolicy: "Never",
      securityContext: {runAsNonRoot: true, runAsUser: 999, runAsGroup: 999,
                        seccompProfile: {type: "RuntimeDefault"}},
      containers: [{
        name: "mysql", image: "mysql:8.4",
        command: ["sh", "-c", "mysql --ssl-mode=REQUIRED -h \"$DB_HOST\" -u \"$DB_USERNAME\" -e \"$SQL\""],
        env: [
          {name: "DB_HOST", value: $host},
          {name: "SQL", value: $sql},
          {name: "DB_USERNAME", valueFrom: {secretKeyRef: {name: "backend-secrets", key: "DB_USERNAME"}}},
          {name: "MYSQL_PWD", valueFrom: {secretKeyRef: {name: "backend-secrets", key: "DB_PASSWORD"}}}
        ],
        resources: {requests: {cpu: "50m", memory: "64Mi"}, limits: {memory: "256Mi"}},
        securityContext: {allowPrivilegeEscalation: false, capabilities: {drop: ["ALL"]}}
      }]
    }}
  }}' | kubectl -n "$ns" apply -f -

for _ in $(seq 1 60); do
  done_=$(kubectl -n "$ns" get job "$job" -o jsonpath='{.status.succeeded}{"/"}{.status.failed}')
  case "$done_" in
    1/*) echo "SQL ok: $sql"; exit 0 ;;
    */3) break ;;
  esac
  sleep 5
done
kubectl -n "$ns" logs "job/$job" --tail=20 || true
echo "::error::SQL failed or timed out: $sql"
exit 1
