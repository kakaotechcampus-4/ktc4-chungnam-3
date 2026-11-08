#!/usr/bin/env bash
# Install this script root-owned at /usr/local/sbin/remembrall-deploy.
set -euo pipefail
umask 077
[[ "$EUID" == 0 && $# == 3 && "$1" =~ ^[0-9a-f]{40}$ && "$2" =~ ^[0-9a-f]{40}-[0-9]+-[0-9]+$ && "$3" =~ ^[A-Za-z0-9._/-]+$ ]] || exit 1
sha="$1"; incoming_name="$2"; verified_model="$3"
[[ "$incoming_name" == "$sha-"* ]] || exit 1
config=/etc/remembrall/deploy.conf
[[ -f "$config" && ! -L "$config" && "$(stat -c %u "$config")" == 0 && "$(stat -c %a "$config")" == 600 ]] || exit 1
# Trusted root-owned shell/environment file. It contains paths, not API keys.
source "$config"
[[ "${RECALL_MODEL:-}" == "$verified_model" ]] || { echo 'Production model differs from the externally verified model' >&2; exit 1; }
[[ "$DEPLOY_ROOT" =~ ^/(home|opt|srv)/[A-Za-z0-9_./-]+/remembrall-deploy$ && "$DEPLOY_ROOT" != *..* ]] || exit 1
for value in "$DB_CONTAINER" "$DB_USER" "$DB_NAME"; do [[ "$value" =~ ^[A-Za-z0-9_-]+$ ]] || exit 1; done
[[ "$PUBLIC_HEALTH_URL" =~ ^https://[A-Za-z0-9.-]+/actuator/health$ ]] || exit 1
[[ -f "$APP_CONFIG" && -f "$FCM_CONFIG" ]] || exit 1
exec 9>"$DEPLOY_ROOT/deploy.lock"
flock -n 9 || { echo 'Another deployment is active'; exit 1; }
active_file=/etc/nginx/remembrall/active-location.conf
[[ -f "$active_file" && ! -L "$active_file" ]] || exit 1
active="$(cat "$DEPLOY_ROOT/active-slot")"
case "$active" in
  legacy) next=green; port=8081; previous_unit=remembrall ;;
  blue) next=green; port=8081; previous_unit=remembrall@blue ;;
  green) next=blue; port=8080; previous_unit=remembrall@green ;;
  *) exit 1 ;;
esac
unit="remembrall@$next"
if systemctl is-active --quiet "$unit" || ss -lntH | awk '{print $4}' | grep -Eq ":$port$"; then
  echo 'Inactive slot is occupied. Finish draining the previous deployment first.' >&2; exit 1
fi
incoming="$DEPLOY_ROOT/incoming/$incoming_name"
[[ -d "$incoming" && ! -L "$incoming" ]] || exit 1
jar=Remembrall-0.0.1-SNAPSHOT.jar
[[ -f "$incoming/$jar" && ! -L "$incoming/$jar" && -f "$incoming/SHA256SUMS" && ! -L "$incoming/SHA256SUMS" ]] || exit 1
grep -Eq '^[0-9a-f]{64}  Remembrall-0\.0\.1-SNAPSHOT\.jar$' "$incoming/SHA256SUMS"
[[ "$(wc -l < "$incoming/SHA256SUMS")" == 1 ]] || exit 1
(cd "$incoming" && sha256sum -c SHA256SUMS)
release="$DEPLOY_ROOT/releases/$sha"
if [[ -e "$release" ]]; then
  [[ -d "$release" && ! -L "$release" ]] || exit 1
  cmp "$release/SHA256SUMS" "$incoming/SHA256SUMS"
  (cd "$release" && sha256sum -c SHA256SUMS)
else
  install -d -m 755 "$release"
  install -m 644 "$incoming/$jar" "$release/$jar"
  install -m 644 "$incoming/SHA256SUMS" "$release/SHA256SUMS"
fi
# Migration rollback is not automatic; releases must be backward-compatible.
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > "$DEPLOY_ROOT/backups/$incoming_name.dump"
[[ -s "$DEPLOY_ROOT/backups/$incoming_name.dump" ]] || exit 1
backup="$(mktemp /etc/nginx/remembrall/.previous.XXXXXX)"
cp -p "$active_file" "$backup"
switched=false
committed=false
rollback() {
  result=$?
  trap - EXIT INT TERM
  if [[ "$committed" != true ]]; then
    if [[ "$switched" == true ]]; then
      cp -p "$backup" "$active_file"
      nginx -t && systemctl reload nginx || echo 'URGENT: Nginx rollback requires operator attention' >&2
    fi
    systemctl stop "$unit" 2>/dev/null || true
    systemctl disable "$unit" 2>/dev/null || true
  fi
  rm -f "$backup"
  rm -f "$incoming/$jar" "$incoming/SHA256SUMS"
  rmdir "$incoming" 2>/dev/null || true
  exit "$result"
}
trap rollback EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
ln -sfn "$release/$jar" "$DEPLOY_ROOT/slots/$next/app.jar"
systemctl start "$unit"
healthy=false
for ((i=0;i<90;i++)); do
  if curl -fsS --max-time 3 "http://127.0.0.1:$port/actuator/health" | grep -q '"status":"UP"'; then healthy=true; break; fi
  sleep 2
done
[[ "$healthy" == true ]] || { echo 'New slot failed health check; old slot retained' >&2; exit 1; }
# Safe unauthenticated contract check; never create/delete production data here.
status="$(curl -sS --max-time 5 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/api/me/consents")"
[[ "$status" == 401 ]] || exit 1
candidate="$(mktemp /etc/nginx/remembrall/.candidate.XXXXXX)"
printf 'proxy_pass http://127.0.0.1:%s;\nadd_header X-Remembrall-Release "%s" always;\n' "$port" "$sha" > "$candidate"
chmod 644 "$candidate"
mv "$candidate" "$active_file"
switched=true
nginx -t
systemctl reload nginx
verified=false
for ((i=0;i<10;i++)); do
  headers="$(curl -fsS --max-time 8 -D - "$PUBLIC_HEALTH_URL")" || { sleep 2; continue; }
  if grep -qi "^X-Remembrall-Release: $sha" <<< "$headers" && grep -q '"status":"UP"' <<< "$headers"; then verified=true; break; fi
  sleep 2
done
[[ "$verified" == true ]] || { echo 'Public health/version check failed; rolling back' >&2; exit 1; }
systemctl enable "$unit"
printf '%s\n' "$next" > "$DEPLOY_ROOT/active-slot.next"
mv "$DEPLOY_ROOT/active-slot.next" "$DEPLOY_ROOT/active-slot"
committed=true
echo "Traffic switched to $next, revision $sha"
# Conservative, DB-wide drain: do not kill outstanding analysis/recall work.
quiet=0
for ((i=0;i<120;i++)); do
  count="$(docker exec "$DB_CONTAINER" psql -X -qAt -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" -c \
    "SELECT (SELECT count(*) FROM content WHERE analysis_status='ANALYZING') + (SELECT count(*) FROM recall_execution WHERE status IN ('PENDING','RUNNING'));")" || break
  if [[ "$count" == 0 ]]; then quiet=$((quiet+1)); else quiet=0; fi
  if ((quiet>=3)); then
    systemctl stop "$previous_unit"
    systemctl disable "$previous_unit"
    echo 'Previous slot drained and stopped'
    exit 0
  fi
  sleep 5
done
echo 'New version is serving. Previous slot retained for unfinished work; review before next deployment.'
