#!/usr/bin/env bash
# Run ONLY inside the disposable, network-disabled container documented below.
set -euo pipefail
[[ "$EUID" == 0 && -f /.dockerenv && -d /workspace/.github/deploy ]] || exit 1
temporary="$(mktemp -d)"
trap 'rm -rf "$temporary" /opt/fixture/remembrall-deploy /etc/remembrall /etc/nginx/remembrall' EXIT
mkdir -p "$temporary/bin" /etc/remembrall /etc/nginx/remembrall
export TEST_LOG="$temporary/commands" TEST_MODE=success TEST_SHA
TEST_SHA="$(printf a%.0s {1..40})"
cat > "$temporary/bin/dispatch" <<'MOCK'
#!/bin/bash
name="${0##*/}"
echo "$name $*" >> "$TEST_LOG"
case "$name" in
  sleep|flock) exit 0 ;;
  ss) [[ "$TEST_MODE" != occupied ]] || echo 'LISTEN 0 128 127.0.0.1:8081 *:*'; exit 0 ;;
  systemctl)
    [[ "$1" != is-active ]] || exit 1
    exit 0 ;;
  nginx)
    if [[ "$TEST_MODE" == nginx-fail ]] && grep -q ':8081' /etc/nginx/remembrall/active-location.conf; then exit 1; fi
    exit 0 ;;
  docker)
    if [[ "$*" == *pg_dump* ]]; then [[ "$TEST_MODE" != backup-fail ]] || exit 1; echo backup; else
      if [[ "$TEST_MODE" == drain-pending ]]; then echo 1; else echo 0; fi
    fi ;;
  curl)
    if [[ "$*" == *api/me/consents* ]]; then echo 401
    elif [[ "$*" == *https://* ]]; then
      [[ "$TEST_MODE" != public-fail ]] || exit 22
      printf 'HTTP/1.1 200\r\nX-Remembrall-Release: %s\r\n\r\n{"status":"UP"}\n' "$TEST_SHA"
    else [[ "$TEST_MODE" != unhealthy ]] || exit 22; echo '{"status":"UP"}'; fi ;;
  *) exit 1 ;;
esac
MOCK
chmod +x "$temporary/bin/dispatch"
for command in systemctl nginx docker curl ss sleep flock; do ln -s dispatch "$temporary/bin/$command"; done
export PATH="$temporary/bin:$PATH"
root=/opt/fixture/remembrall-deploy
incoming="$TEST_SHA-1-1"
reset() {
  rm -rf "$root"
  mkdir -p "$root/incoming/$incoming" "$root/releases" "$root/slots/blue" "$root/slots/green" "$root/backups"
  printf 'legacy\n' > "$root/active-slot"
  printf 'proxy_pass http://127.0.0.1:8080;\n' > /etc/nginx/remembrall/active-location.conf
  printf 'fixture\n' > "$root/incoming/$incoming/Remembrall-0.0.1-SNAPSHOT.jar"
  (cd "$root/incoming/$incoming" && sha256sum Remembrall-0.0.1-SNAPSHOT.jar > SHA256SUMS)
  touch "$temporary/app.properties" "$temporary/firebase.json"
  cat > /etc/remembrall/deploy.conf <<EOF
DEPLOY_ROOT=$root
APP_CONFIG=$temporary/app.properties
FCM_CONFIG=$temporary/firebase.json
PUBLIC_HEALTH_URL=https://api.example.test/actuator/health
DB_CONTAINER=fixture
DB_USER=fixture
DB_NAME=fixture
RECALL_MODEL=fixture-model
EOF
  chmod 600 /etc/remembrall/deploy.conf
  : > "$TEST_LOG"
}
run() { bash /workspace/.github/deploy/apply.sh "$TEST_SHA" "$incoming" fixture-model > "$temporary/output" 2>&1; }
old_retained() {
  [[ "$(cat "$root/active-slot")" == legacy ]]
  grep -q ':8080' /etc/nginx/remembrall/active-location.conf
  ! grep -q '^systemctl stop remembrall$' "$TEST_LOG"
}
for mode in backup-fail occupied unhealthy nginx-fail public-fail; do
  reset; export TEST_MODE="$mode"
  if run; then echo "FAIL: $mode should block deployment"; cat "$temporary/output"; exit 1; fi
  old_retained
  echo "PASS: $mode retains old traffic and service"
done
reset; export TEST_MODE=success
run
[[ "$(cat "$root/active-slot")" == green ]]
grep -q "$TEST_SHA" /etc/nginx/remembrall/active-location.conf
grep -q '^systemctl stop remembrall$' "$TEST_LOG"
[[ -s "$root/backups/$incoming.dump" ]]
echo 'PASS: successful cutover uses checksum-validated artifact and stops drained service'
reset; export TEST_MODE=drain-pending
run
[[ "$(cat "$root/active-slot")" == green ]]
! grep -q '^systemctl stop remembrall$' "$TEST_LOG"
echo 'PASS: unfinished jobs retain old process after cutover'
reset; export TEST_MODE=success
printf invalid > "$root/incoming/$incoming/Remembrall-0.0.1-SNAPSHOT.jar"
if run; then echo 'FAIL: corrupt artifact accepted'; exit 1; fi
old_retained
echo 'PASS: corrupt artifact rejected before starting new slot'
reset
if bash /workspace/.github/deploy/apply.sh "$TEST_SHA" "$incoming" wrong-model > "$temporary/output" 2>&1; then exit 1; fi
old_retained
echo 'PASS: production model mismatch blocks deployment'
