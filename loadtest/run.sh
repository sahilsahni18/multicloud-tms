#!/usr/bin/env bash
# Finds TrackFlow's failure point on a local replica of one cloud backend pod.
#
#   backend : the production image (backend/Dockerfile), 1 vCPU, 768 MiB (the pod's memory limit)
#   MySQL   : 8.4, 2 vCPU, 1 GiB (like RDS db.t4g.micro)
#   data    : demo seed + loadtest/seed.sql (3,000 tickets, 9,000 comments)
#   load    : k6 constant arrival rate, one 60 s step per rate in RATES
#
# Usage: loadtest/run.sh <label> [rates...]     e.g. loadtest/run.sh baseline 20 40 60 80
#        REUSE=1 loadtest/run.sh <label> [rates...]  adds steps to a running environment
#        IMAGE=<tag> loadtest/run.sh <label> [rates...] tests a prebuilt backend image
# Results: loadtest/results/<label>/step-<rate>.json and summary.csv
set -euo pipefail

label=${1:?label, e.g. baseline}; shift
rates=${*:-"20 40 60 80 100 120 140 160 180 200"}
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/.." && pwd)
out="$here/results/$label"
net=tf-loadtest
mkdir -p "$out"
# Docker on Windows (Git Bash) needs Windows paths for bind mounts.
winpath() { if command -v cygpath >/dev/null; then cygpath -w "$1"; else echo "$1"; fi; }
export MSYS_NO_PATHCONV=1

if [ "${REUSE:-0}" != 1 ]; then
image=${IMAGE:-trackflow-backend:loadtest}
if [ -z "${IMAGE:-}" ]; then
  echo "== image"
  docker build -q -t "$image" "$(winpath "$root/backend")" >/dev/null
fi

echo "== database"
docker rm -f lt-db lt-api >/dev/null 2>&1 || true
docker network create "$net" >/dev/null 2>&1 || true
docker run -d --name lt-db --network "$net" --cpus 2 --memory 1g \
  -e MYSQL_ROOT_PASSWORD=loadtest-root -e MYSQL_DATABASE=trackflow \
  -e MYSQL_USER=trackflow -e MYSQL_PASSWORD=loadtest-pw mysql:8.4 >/dev/null
until docker exec lt-db mysqladmin ping -h127.0.0.1 -uroot -ploadtest-root --silent >/dev/null 2>&1; do sleep 2; done
sleep 10

echo "== backend (1 vCPU, 768 MiB)"
docker run -d --name lt-api --network "$net" --cpus 1 --memory 768m -p 18081:8080 \
  -e SPRING_PROFILES_ACTIVE=cloud,demo-data \
  -e "DB_URL=jdbc:mysql://lt-db:3306/trackflow?sslMode=REQUIRED" \
  -e DB_USERNAME=trackflow -e DB_PASSWORD=loadtest-pw -e DB_POOL_SIZE=10 \
  -e "JWT_SECRET=$(openssl rand -base64 64 | tr -d '\r\n')" \
  -e CORS_ALLOWED_ORIGINS=http://localhost \
  -e AUTH_RATE_LIMIT_PER_MINUTE=10 \
  "$image" >/dev/null
until curl -fsS -m 3 http://localhost:18081/actuator/health >/dev/null 2>&1; do sleep 3; done

echo "== seed"
docker exec -i lt-db mysql -utrackflow -ploadtest-pw trackflow < "$here/seed.sql" 2>/dev/null
docker exec lt-db mysql -utrackflow -ploadtest-pw -N -e "SELECT COUNT(*) FROM tickets; SELECT COUNT(*) FROM comments;" trackflow 2>/dev/null | paste -sd' ' | sed 's/^/tickets, comments: /'

echo "== warm-up (JIT, connection pool)"
docker run --rm --network "$net" -v "$(winpath "$here"):/scripts" -v "$(winpath "$out"):/results" \
  grafana/k6:1.3.0 run -q -e BASE_URL=http://lt-api:8080 -e RATE=10 -e DURATION=30s /scripts/trackflow.js >/dev/null

echo "rate,rps,p50_ms,p95_ms,p99_ms,server_errors,dropped" > "$out/summary.csv"
fi
# Failure point: p95 > 500 ms, >= 1% server errors, or dropped requests.
failures=0
for rate in $rates; do
  sleep 20 # let the token bucket refill: setup() logs in 4 users per step
  docker run --rm --network "$net" -v "$(winpath "$here"):/scripts" -v "$(winpath "$out"):/results" \
    grafana/k6:1.3.0 run -q -e BASE_URL=http://lt-api:8080 -e RATE="$rate" -e DURATION=60s /scripts/trackflow.js \
    | tail -1 | tee -a "$out/raw.jsonl"
  python - "$(winpath "$out/step-$rate.json")" >> "$out/summary.csv" <<'PY'
import json, sys
r = json.load(open(sys.argv[1]))
print(f"{r['rate']},{r['rps']:.1f},{r['p50']:.0f},{r['p95']:.0f},{r['p99']:.0f},{r['serverErrors']:.4f},{r['dropped']}")
PY
  docker stats --no-stream --format "  api cpu={{.CPUPerc}} mem={{.MemUsage}}" lt-api
  if tail -1 "$out/summary.csv" | awk -F, '{exit !($4 > 500 || $6 >= 0.01 || $7 > 0)}'; then
    failures=$((failures + 1)); echo "  step $rate failed"
    [ "$failures" -ge 2 ] && { echo "  two failing steps in a row: stopping"; break; }
  else
    failures=0
  fi
done

echo "== done: $out/summary.csv"
cat "$out/summary.csv"
