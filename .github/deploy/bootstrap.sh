#!/usr/bin/env bash
# Explicit one-time operator action. Never invoked automatically from CI.
set -euo pipefail
[[ "$EUID" == 0 && $# == 1 ]] || { echo 'sudo bash bootstrap.sh /path/to/reviewed-deploy.conf'; exit 1; }
directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source_config="$1"
[[ -f "$source_config" && ! -L "$source_config" ]] || exit 1
[[ ! -e /etc/remembrall/deploy.conf && ! -e /etc/nginx/remembrall/active-location.conf && ! -e /etc/systemd/system/remembrall@.service ]] || {
  echo 'Already provisioned; inspect existing configuration instead of overwriting it.' >&2; exit 1;
}
source "$source_config"
[[ "$DEPLOY_ROOT" =~ ^/(home|opt|srv)/[A-Za-z0-9_./-]+/remembrall-deploy$ && "$DEPLOY_ROOT" != *..* ]] || exit 1
[[ ! -e "$DEPLOY_ROOT" && -f "$APP_CONFIG" && -f "$FCM_CONFIG" ]] || exit 1
systemctl is-active --quiet remembrall
curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'
install -d -m 755 /etc/remembrall /etc/nginx/remembrall "$DEPLOY_ROOT" "$DEPLOY_ROOT/releases" "$DEPLOY_ROOT/slots" "$DEPLOY_ROOT/slots/blue" "$DEPLOY_ROOT/slots/green"
install -d -m 700 -o ubuntu -g ubuntu "$DEPLOY_ROOT/incoming"
install -d -m 700 "$DEPLOY_ROOT/backups"
install -m 600 "$source_config" /etc/remembrall/deploy.conf
printf 'SERVER_PORT=8080\n' > /etc/remembrall/blue.env
printf 'SERVER_PORT=8081\n' > /etc/remembrall/green.env
chmod 600 /etc/remembrall/blue.env /etc/remembrall/green.env
printf 'legacy\n' > "$DEPLOY_ROOT/active-slot"
printf 'proxy_pass http://127.0.0.1:8080;\nadd_header X-Remembrall-Release "legacy" always;\n' > /etc/nginx/remembrall/active-location.conf
chmod 644 /etc/nginx/remembrall/active-location.conf
install -m 644 "$directory/remembrall@.service" /etc/systemd/system/remembrall@.service
install -m 755 "$directory/apply.sh" /usr/local/sbin/remembrall-deploy
systemd-analyze verify /etc/systemd/system/remembrall@.service
systemctl daemon-reload
echo 'Provisioned inactive slots. Existing backend and Nginx have not been restarted.'
echo 'Review Nginx location /: replace its proxy_pass line with include /etc/nginx/remembrall/active-location.conf;'
echo 'Then run nginx -t and reload nginx. Do not enable automatic deployment before completing the checklist.'
