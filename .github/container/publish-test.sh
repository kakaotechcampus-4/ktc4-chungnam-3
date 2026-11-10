#!/usr/bin/env bash
# Manual non-main testing only. Never calls publish.sh or promotes deploy-main.
set -euo pipefail
[[ "${GITHUB_EVENT_NAME:-}" == workflow_dispatch && "${GITHUB_REF:-}" == refs/heads/* && "$GITHUB_REF" != refs/heads/main ]]
[[ "${GITHUB_SHA:-}" =~ ^[0-9a-f]{40}$ && "${GITHUB_RUN_ID:-}" =~ ^[0-9]+$ && "${GITHUB_RUN_ATTEMPT:-}" =~ ^[0-9]+$ ]]
[[ "$(uname -m)" == aarch64 ]] || { echo 'Native ARM64 runner required'; exit 1; }
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root/release"
test -s Remembrall-0.0.1-SNAPSHOT.jar
rm -f GHCR_TEST_RESULT.json
# Dockerfile requires a receipt file, but this explicitly does NOT claim a live API gate pass.
python3 - <<'PY'
import json,os
with open('EXTERNAL_RESULT.json','w') as stream:
    json.dump({'sha':os.environ['GITHUB_SHA'],'status':'not-run','stage':'branch-container-test',
               'externalApiTested':False},stream)
PY
jar_sha="$(sha256sum Remembrall-0.0.1-SNAPSHOT.jar | cut -d ' ' -f 1)"
image="ghcr.io/${GITHUB_REPOSITORY_OWNER,,}/remembrall-backend-test"
tag="$image:test-$GITHUB_SHA-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
docker build --platform linux/arm64 -f "$root/.github/container/Dockerfile" \
  --label "org.opencontainers.image.source=https://github.com/$GITHUB_REPOSITORY" \
  --label "org.opencontainers.image.revision=$GITHUB_SHA" \
  --label "io.remembrall.external-gate=not-run" --label "io.remembrall.test-only=true" \
  --label "io.remembrall.jar-sha256=$jar_sha" -t "$tag" .
bash "$root/.github/container/smoke.sh" "$tag"
trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "$GITHUB_ACTOR" --password-stdin
docker push "$tag"
digest="$(docker image inspect "$tag" --format '{{index .RepoDigests 0}}')"
[[ "$digest" == "$image@"* && "${digest#"$image@"}" =~ ^sha256:[0-9a-f]{64}$ ]]
printf '### Test-only GHCR image uploaded\nTag: `%s`\n\nDigest: `%s`\n\n' "$tag" "$digest" >> "$GITHUB_STEP_SUMMARY"
docker pull --platform linux/arm64 "$digest"
bash "$root/.github/container/smoke.sh" "$digest"
export TEST_IMAGE_TAG="$tag" TEST_IMAGE_DIGEST="$digest"
python3 - <<'PY'
import json,os
with open('GHCR_TEST_RESULT.json','w') as stream:
    json.dump({'status':'passed','sha':os.environ['GITHUB_SHA'],'tag':os.environ['TEST_IMAGE_TAG'],
               'image':os.environ['TEST_IMAGE_DIGEST'],'architecture':'arm64',
               'externalApiTested':False,'productionChannelChanged':False,
               'checks':['container-start','health-up','unauthenticated-401','registry-push','digest-pull','pulled-image-start']},stream)
PY
printf 'PASS: local and GHCR digest images started against disposable databases.\n\nLive external APIs were not tested. No production channel or server was changed.\n' >> "$GITHUB_STEP_SUMMARY"
