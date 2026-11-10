#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_REF:-}" == refs/heads/main && "${GITHUB_SHA:-}" =~ ^[0-9a-f]{40}$ ]]
[[ "${GITHUB_RUN_NUMBER:-}" =~ ^[0-9]+$ ]]
[[ "$(uname -m)" == aarch64 ]] || { echo 'Native ARM64 runner required'; exit 1; }
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root/release"
sha256sum -c SHA256SUMS
model="$(RELEASE_SHA="$GITHUB_SHA" node --input-type=module -e '
  import {readFileSync} from "node:fs";
  import {validateReceipt} from "../test/external/run.mjs";
  console.log(validateReceipt(JSON.parse(readFileSync("EXTERNAL_RESULT.json")),process.env.RELEASE_SHA));')"
jar_sha="$(sha256sum Remembrall-0.0.1-SNAPSHOT.jar | cut -d ' ' -f 1)"
image="ghcr.io/${GITHUB_REPOSITORY_OWNER,,}/remembrall-backend"
tag="$image:sha-$GITHUB_SHA"
docker build --platform linux/arm64 -f "$root/.github/container/Dockerfile" \
  --label "org.opencontainers.image.source=https://github.com/$GITHUB_REPOSITORY" \
  --label "org.opencontainers.image.revision=$GITHUB_SHA" \
  --label "io.remembrall.external-model=$model" --label "io.remembrall.external-gate=passed" \
  --label "io.remembrall.jar-sha256=$jar_sha" --label "io.remembrall.sequence=$GITHUB_RUN_NUMBER" \
  -t "$tag" .
bash "$root/.github/container/smoke.sh" "$tag"
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "$GITHUB_ACTOR" --password-stdin
trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
docker push "$tag"
digest="$(docker image inspect "$tag" --format '{{index .RepoDigests 0}}')"
[[ "$digest" == "$image@sha256:"* ]]
printf '### Published ARM64 image\n`%s`\n\nContainer smoke passed. This is not a server deployment result.\n' "$digest" >> "$GITHUB_STEP_SUMMARY"
if [[ "${PROMOTE_DEPLOYMENT:-false}" == true ]]; then
  latest="$(git -C "$root" ls-remote origin refs/heads/main | cut -f1)"
  [[ "$latest" == "$GITHUB_SHA" ]] || { echo 'A newer main exists; deployment channel not changed'; exit 0; }
  docker tag "$tag" "$image:deploy-main"
  docker push "$image:deploy-main"
  echo 'Deployment channel updated. The server will apply it only when its local switch is enabled.' >> "$GITHUB_STEP_SUMMARY"
fi
