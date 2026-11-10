#!/usr/bin/env bash
# Use an empty disposable DB and deliberately invalid API keys. No paid calls.
set -euo pipefail
[[ $# == 1 ]] || exit 1
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
suffix="$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')"
network="image-smoke-$suffix"; db="image-db-$suffix"; app="image-app-$suffix"
cleanup() { docker rm -fv "$app" "$db" >/dev/null 2>&1 || true; docker network rm "$network" >/dev/null 2>&1 || true; }
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
docker network create "$network" >/dev/null
docker run -d --name "$db" --network "$network" -e POSTGRES_DB=image_smoke \
  -e POSTGRES_USER=image_smoke -e POSTGRES_PASSWORD=smoke-only pgvector/pgvector:pg16 >/dev/null
for ((i=0;i<60;i++)); do docker exec "$db" pg_isready -U image_smoke >/dev/null 2>&1 && break; sleep 1; done
docker exec "$db" pg_isready -U image_smoke >/dev/null
docker run -d --name "$app" --network "$network" --user "$(id -u):$(id -g)" --read-only \
  --tmpfs /tmp:rw,nosuid,size=128m --cap-drop ALL --security-opt no-new-privileges \
  -p 127.0.0.1::8080 -v "$root/.github/ci/application-ci.properties:/run/ci.properties:ro" \
  "$1" --spring.config.additional-location=file:/run/ci.properties --spring.config.import= \
  --server.address=0.0.0.0 --spring.datasource.url="jdbc:postgresql://$db:5432/image_smoke" \
  --spring.datasource.username=image_smoke --spring.datasource.password=smoke-only >/dev/null
port="$(docker port "$app" 8080/tcp | sed -n 's/127.0.0.1://p')"
ready=false
for ((i=0;i<90;i++)); do
  if curl -fsS --max-time 2 "http://127.0.0.1:$port/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then ready=true; break; fi
  sleep 2
done
[[ "$ready" == true ]] || { docker logs --tail 70 "$app"; exit 1; }
[[ "$(curl -sS --max-time 5 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/api/me/consents")" == 401 ]]
echo 'PASS: container starts with empty pgvector DB, health UP and protected API 401'
