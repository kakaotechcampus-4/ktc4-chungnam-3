#!/usr/bin/env bash
set -euo pipefail
umask 077

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
backend="$root/backend/Remembrall"
reports="$root/test/harness/reports"
mkdir -p "$reports"
for command in docker java node setsid curl; do command -v "$command" >/dev/null || { echo "Missing: $command" >&2; exit 1; }; done
docker info >/dev/null

# This runner never accepts an existing DB or target URL, including a production one.
container="bruno-regression-$(node -e 'process.stdout.write(require("crypto").randomBytes(8).toString("hex"))')"
export BRUNO_HARNESS_TOKEN
BRUNO_HARNESS_TOKEN="$(node -e 'process.stdout.write(require("crypto").randomBytes(32).toString("hex"))')"
password="$(node -e 'process.stdout.write(require("crypto").randomBytes(24).toString("hex"))')"
temporary="$(mktemp -d)"
backend_pid=""
cleanup() {
  status=$?
  trap - EXIT INT TERM
  if [[ -f "$temporary/backend.pid" ]]; then
    java_pid="$(cat "$temporary/backend.pid")"
    if [[ "$java_pid" =~ ^[0-9]+$ ]] && ps -p "$java_pid" -o args= | grep -Fq 'com.ktc.chungnam3.remembrall.bruno.BrunoHarness'; then
      kill -TERM "$java_pid" 2>/dev/null || true
    fi
  fi
  if [[ -n "$backend_pid" ]]; then
    kill -TERM -- "-$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  fi
  # Only the unique container created by this invocation is removed.
  if [[ "$(docker inspect -f '{{index .Config.Labels "purpose"}}' "$container" 2>/dev/null || true)" == bruno-regression ]]; then
    docker rm -fv "$container" >/dev/null || true
  fi
  rm -f "$temporary/local.bru" "$temporary/backend.pid"
  rmdir "$temporary" 2>/dev/null || true
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

docker run -d --name "$container" --label purpose=bruno-regression \
  -e POSTGRES_DB=bruno_regression -e POSTGRES_USER=bruno_regression -e POSTGRES_PASSWORD="$password" \
  -p 127.0.0.1::5432 pgvector/pgvector:pg16 >/dev/null
db_port="$(docker port "$container" 5432/tcp | sed -n 's/127.0.0.1://p')"
for ((i=0;i<60;i++)); do
  if docker exec "$container" pg_isready -U bruno_regression >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$container" pg_isready -U bruno_regression >/dev/null
port="$(node -e 'let s=require("net").createServer();s.listen(0,"127.0.0.1",()=>{process.stdout.write(String(s.address().port));s.close();})')"

export SPRING_CONFIG_ADDITIONAL_LOCATION="file:$root/.github/ci/application-ci.properties"
export SPRING_CONFIG_IMPORT=
export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$db_port/bruno_regression"
export SPRING_DATASOURCE_USERNAME=bruno_regression SPRING_DATASOURCE_PASSWORD="$password"
export RECALL_MEMBERCOOLDOWN=0s
export BRUNO_HARNESS_PIDFILE="$temporary/backend.pid"
cd "$backend"
setsid bash ./gradlew brunoHarness --args="--server.port=$port" --no-daemon --console=plain > "$reports/backend.log" 2>&1 &
backend_pid=$!
ready=false
for ((i=0;i<240;i++)); do
  if ! kill -0 "$backend_pid" 2>/dev/null; then break; fi
  if curl -fsS --max-time 2 "http://127.0.0.1:$port/actuator/health" 2>/dev/null | node -e 'let s="";process.stdin.on("data",d=>s+=d);process.stdin.on("end",()=>{try{process.exit(JSON.parse(s).status==="UP"?0:1)}catch{process.exit(1)}})'; then ready=true; break; fi
  sleep 1
done
if [[ "$ready" != true ]]; then tail -n 100 "$reports/backend.log"; exit 1; fi
cat > "$temporary/local.bru" <<EOF
vars {
  baseUrl: http://127.0.0.1:$port
  controlToken: $BRUNO_HARNESS_TOKEN
}
EOF

if [[ -n "${BRUNO_CLI_JS:-}" ]]; then
  cli=(node "$BRUNO_CLI_JS")
elif command -v bru >/dev/null; then
  cli=(bru)
else
  cli=(npx --yes --package @usebruno/cli@4.3.0 bru)
fi
"${cli[@]}" --version | grep -Eq '(^|[^0-9])4\.3\.0([^0-9]|$)' || { echo 'Bruno CLI 4.3.0 required' >&2; exit 1; }
repeat="${BRUNO_REPEAT:-2}"
[[ "$repeat" =~ ^[1-3]$ ]] || { echo 'BRUNO_REPEAT must be 1..3' >&2; exit 1; }
failed=0
for ((round=1;round<=repeat;round++)); do
  for collection in bruno-place-list bruno-auth-device bruno-analysis-lifecycle bruno-recall-retry; do
    echo "Running $collection (round $round)"
    cd "$root/test/$collection"
    if ! "${cli[@]}" run --env-file "$temporary/local.bru" --noproxy \
        --reporter-junit "$reports/$collection-$round.xml" --reporter-skip-all-headers --reporter-skip-body \
        > "$reports/$collection-$round.log" 2>&1; then failed=1; fi
    tail -n 20 "$reports/$collection-$round.log"
  done
done
exit "$failed"
