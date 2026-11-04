#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
backend_dir="$repo_root/backend/Remembrall"
collection_dir="$repo_root/test/bruno"
reports_dir="$collection_dir/reports"
jar="$backend_dir/build/libs/Remembrall-0.0.1-SNAPSHOT.jar"

if [[ ! -f "$jar" ]]; then
  echo "Backend JAR is missing. Run compileTestJava bootJar first." >&2
  exit 1
fi
mkdir -p "$reports_dir"

# Refuse to run against an unrelated local server already listening on the CI port.
node -e '
  const server = require("node:net").createServer();
  server.once("error", () => {
    console.error("Port 8080 is already in use; refusing to test an existing server.");
    process.exit(1);
  });
  server.listen(8080, "127.0.0.1", () => server.close());
'

cd "$backend_dir"
java -jar "$jar" \
  --spring.config.additional-location="file:$repo_root/.github/ci/application-ci.properties" \
  --server.port=8080 > "$reports_dir/backend.log" 2>&1 &
backend_pid=$!

cleanup() {
  kill "$backend_pid" 2>/dev/null || true
  wait "$backend_pid" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

ready=false
deadline=$((SECONDS + 180))
while ((SECONDS < deadline)); do
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    echo "Backend exited before becoming healthy." >&2
    tail -n 100 "$reports_dir/backend.log"
    exit 1
  fi
  if curl --silent --fail --max-time 2 \
      http://127.0.0.1:8080/actuator/health > "$reports_dir/health.json" \
      && node -e 'const fs = require("node:fs"); process.exit(JSON.parse(fs.readFileSync(process.argv[1], "utf8")).status === "UP" ? 0 : 1)' \
        "$reports_dir/health.json"; then
    ready=true
    break
  fi
  sleep 2
done

if [[ "$ready" != true ]]; then
  echo "Backend did not become healthy within the startup deadline." >&2
  tail -n 100 "$reports_dir/backend.log"
  exit 1
fi

cd "$collection_dir"
# Run sequentially: login, consent changes, isolation checks, and logout share runtime variables.
# Do not add --bail: cleanup requests should also run when an earlier assertion fails.
# Explicitly select basic folders: 07-save-confirm-flow needs real AI credentials.
bru run 00-health 01-auth-public 02-auth-session 03-consent 03-device \
  04-location-event 06-auth-consent 07-personal-save-basic 99-auth-cleanup \
  --env ci --noproxy \
  --reporter-junit "$reports_dir/ci.xml" \
  --reporter-skip-all-headers --reporter-skip-body
