#!/usr/bin/env bash
set -euo pipefail
set +x

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
signer="$repository_root/.github/scripts/gpg-release-sign.sh"
source_validator="$repository_root/.github/scripts/verify-release-source.sh"
expected_fingerprint="${RELEASE_SIGNING_FINGERPRINT:-E6BB1FB6EE83EEAB7B408C6B5CA409BD8EE61724}"
release_tag="${RELEASE_TAG:-}"
release_commit="${RELEASE_COMMIT:-${GITHUB_SHA:-}}"
run_attempt="${GITHUB_RUN_ATTEMPT:-1}"

error() {
  echo "::error::$*" >&2
  exit 1
}

[[ "$release_tag" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] \
  || error "RELEASE_TAG must be a stable semantic version tag."
[[ -n "$release_commit" ]] || error "RELEASE_COMMIT or GITHUB_SHA is required."
[[ -n "${MAVEN_GPG_PASSPHRASE:-}" ]] || error "MAVEN_GPG_PASSPHRASE is required."
[[ "$run_attempt" =~ ^[0-9]+$ ]] || error "GITHUB_RUN_ATTEMPT must be numeric."
[[ "$(git rev-parse HEAD)" == "$release_commit" ]] \
  || error "Checked-out commit does not match release commit $release_commit."

secret_fingerprint="$(gpg --batch --with-colons --list-secret-keys "$expected_fingerprint" 2>/dev/null \
  | awk -F: '$1 == "fpr" { print $10; exit }')"
[[ "$secret_fingerprint" == "$expected_fingerprint" ]] \
  || error "Imported release secret key does not match expected fingerprint $expected_fingerprint."

if git show-ref --verify --quiet "refs/tags/$release_tag"; then
  (( run_attempt > 1 )) || error "Release tag $release_tag already exists on the initial workflow attempt."
  [[ "$(git rev-parse "$release_tag^{}")" == "$release_commit" ]] \
    || error "Existing release tag $release_tag does not point to $release_commit."
  RELEASE_TAG="$release_tag" RELEASE_SIGNING_FINGERPRINT="$expected_fingerprint" \
    bash "$source_validator"
  echo "Reusing verified release tag $release_tag for workflow attempt $run_attempt."
  exit 0
fi

git -c user.name=github-actions -c user.email=41898282+github-actions[bot]@users.noreply.github.com \
  -c user.signingkey="$expected_fingerprint" -c gpg.program="$signer" \
  tag -s "$release_tag" "$release_commit" -m "Release $release_tag"

RELEASE_TAG="$release_tag" RELEASE_SIGNING_FINGERPRINT="$expected_fingerprint" \
  bash "$source_validator"
git push origin "refs/tags/$release_tag:refs/tags/$release_tag"
echo "Created, verified, and pushed signed release tag $release_tag."
