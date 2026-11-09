#!/usr/bin/env bash
# Installs trusted local files only. Does not start containers, timer, or edit Nginx.
set -euo pipefail
[[ "$EUID" == 0 && $# == 1 ]] || { echo 'sudo bash bootstrap.sh /reviewed/config.json'; exit 1; }
source_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[[ -f "$1" && ! -L "$1" && ! -e /etc/remembrall-container/config.json ]] || exit 1
docker compose version >/dev/null
python3 - "$source_dir" "$1" <<'PY'
import json,sys
from pathlib import Path
sys.path.insert(0,sys.argv[1])
from pull import validate_config
config=validate_config(json.loads(Path(sys.argv[2]).read_text()))
assert config['enabled'] is False, 'First install must be disabled'
for key in ['app_config','fcm_config']:
    file=Path(config[key])
    assert file.is_file() and file.stat().st_uid == config['uid'], 'Check application secret file ownership'
PY
install -d -m 755 /usr/local/lib/remembrall-container /etc/remembrall-container
install -d -m 700 /var/lib/remembrall-container
install -m 600 "$1" /etc/remembrall-container/config.json
install -m 644 "$source_dir/pull.py" "$source_dir/compose.yaml" /usr/local/lib/remembrall-container/
install -m 644 "$source_dir/remembrall-pull.service" "$source_dir/remembrall-pull.timer" /etc/systemd/system/
install -m 644 "$source_dir/maintenance.conf" /etc/remembrall-container/maintenance.conf
systemd-analyze verify /etc/systemd/system/remembrall-pull.service /etc/systemd/system/remembrall-pull.timer
systemctl daemon-reload
echo 'Installed disabled. Existing backend, DB and Nginx were not changed. Timer is not enabled.'
echo 'Before first deployment: include /etc/remembrall-container/maintenance.conf; inside Nginx location /, then nginx -t and reload.'
