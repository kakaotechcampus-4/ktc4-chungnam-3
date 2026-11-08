#!/usr/bin/env bash
set -euo pipefail
umask 077
for key in DEPLOY_HOST DEPLOY_USER DEPLOY_SSH_KEY DEPLOY_KNOWN_HOSTS DEPLOY_ROOT RELEASE_SHA; do
  [[ -n "${!key:-}" ]] || { echo "Missing $key" >&2; exit 1; }
done
[[ "$DEPLOY_HOST" =~ ^[A-Za-z0-9][A-Za-z0-9.-]*$ && "$DEPLOY_USER" =~ ^[a-z_][a-z0-9_-]*$ ]] || exit 1
[[ "$RELEASE_SHA" =~ ^[0-9a-f]{40}$ ]] || exit 1
[[ "$DEPLOY_ROOT" =~ ^/(home|opt|srv)/[A-Za-z0-9_./-]+/remembrall-deploy$ && "$DEPLOY_ROOT" != *..* ]] || exit 1
run_id="${GITHUB_RUN_ID:-0}-${GITHUB_RUN_ATTEMPT:-1}"
[[ "$run_id" =~ ^[0-9]+-[0-9]+$ ]] || exit 1
# Refuse to publish an old queued revision over newer main.
latest="$(git ls-remote origin refs/heads/main | awk '{print $1}')"
[[ "$latest" == "$RELEASE_SHA" ]] || { echo 'A newer main revision exists; deployment refused.' >&2; exit 1; }
(cd release && sha256sum -c SHA256SUMS)
model="$(node --input-type=module -e 'import {readFileSync} from "node:fs"; import {validateReceipt} from "./test/external/run.mjs"; process.stdout.write(validateReceipt(JSON.parse(readFileSync("release/EXTERNAL_RESULT.json","utf8")),process.env.RELEASE_SHA));')"
temporary="$(mktemp -d)"
trap 'rm -f "$temporary/key" "$temporary/known_hosts"; rmdir "$temporary"' EXIT
printf '%s\n' "$DEPLOY_SSH_KEY" > "$temporary/key"
printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > "$temporary/known_hosts"
target="$DEPLOY_USER@$DEPLOY_HOST"
options=(-i "$temporary/key" -o BatchMode=yes -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$temporary/known_hosts" -o ConnectTimeout=10)
incoming="$RELEASE_SHA-$run_id"
ssh "${options[@]}" "$target" "mkdir $DEPLOY_ROOT/incoming/$incoming"
scp "${options[@]}" release/Remembrall-0.0.1-SNAPSHOT.jar release/SHA256SUMS "$target:$DEPLOY_ROOT/incoming/$incoming/"
ssh "${options[@]}" "$target" "sudo -n /usr/local/sbin/remembrall-deploy $RELEASE_SHA $incoming $model"
