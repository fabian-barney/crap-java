#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source_validator="$repository_root/.github/scripts/verify-release-source.sh"
version_validator="$repository_root/.github/scripts/verify-version-alignment.sh"
test_root="$(mktemp -d)"
gnupg_directory="$test_root/gnupg"
mkdir "$gnupg_directory"
chmod 0700 "$gnupg_directory" 2>/dev/null || true
export GNUPGHOME="$gnupg_directory"
if [[ -n "${MSYSTEM:-}" ]]; then
  export MSYS2_ENV_CONV_EXCL="${MSYS2_ENV_CONV_EXCL:+${MSYS2_ENV_CONV_EXCL};}GNUPGHOME"
fi

cleanup() {
  if [[ -n "$test_root" && -d "$test_root" ]]; then
    rm -rf -- "$test_root"
  fi
}
trap cleanup EXIT

cat > "$test_root/gpg-batch.txt" <<'EOF'
Key-Type: RSA
Key-Length: 2048
Key-Usage: sign
Name-Real: release control test
Name-Email: release-control@example.com
Expire-Date: 0
%no-protection
%commit
EOF
gpg --batch --generate-key "$test_root/gpg-batch.txt" >/dev/null 2>&1
fingerprint="$(gpg --batch --with-colons --list-secret-keys release-control@example.com \
  | awk -F: '$1 == "fpr" { print $10; exit }')"
public_key="$test_root/release-control-key.asc"
gpg --batch --armor --export "$fingerprint" > "$public_key"

new_repository() {
  local name="$1"
  local path="$test_root/$name"
  git init -q -b main "$path"
  git -C "$path" config user.name "Release Control Test"
  git -C "$path" config user.email release-control@example.com
  git -C "$path" commit -q --allow-empty -m initial
  printf '%s\n' "$path"
}

commit_change() {
  git -C "$1" commit -q --allow-empty -m "$2"
}

sign_tag() {
  git -C "$1" -c user.signingkey="$fingerprint" tag -s "$2" -m "$2"
}

validate_source() {
  local path="$1"
  local tag="$2"
  local protected_branches="$3"
  (
    cd "$path"
    RELEASE_TAG="$tag" \
    RELEASE_SIGNING_FINGERPRINT="$fingerprint" \
    RELEASE_SIGNING_PUBLIC_KEY="$public_key" \
    RELEASE_MAIN_REF=refs/heads/main \
    RELEASE_HOTFIX_REF_PREFIX=refs/heads/release/hotfix/ \
    RELEASE_PROTECTED_BRANCHES="$protected_branches" \
      bash "$source_validator"
  )
}

expect_failure() {
  local label="$1"
  shift
  if "$@" >"$test_root/unexpected-success.log" 2>&1; then
    echo "Expected failure: $label" >&2
    cat "$test_root/unexpected-success.log" >&2
    exit 1
  fi
}

main_repository="$(new_repository main-source)"
sign_tag "$main_repository" v1.0.0
validate_source "$main_repository" v1.0.0 main >/dev/null

unsigned_repository="$(new_repository unsigned-tag)"
git -C "$unsigned_repository" tag -a v1.0.0 -m v1.0.0
expect_failure "unsigned annotated tag" \
  validate_source "$unsigned_repository" v1.0.0 main

hotfix_repository="$(new_repository valid-hotfix)"
sign_tag "$hotfix_repository" v0.9.0
git -C "$hotfix_repository" switch -q -c release/hotfix/fix
commit_change "$hotfix_repository" hotfix
sign_tag "$hotfix_repository" v0.9.1
validate_source "$hotfix_repository" v0.9.1 $'main\nrelease/hotfix/fix' >/dev/null
expect_failure "unprotected hotfix branch" \
  validate_source "$hotfix_repository" v0.9.1 main

stale_repository="$(new_repository stale-hotfix)"
sign_tag "$stale_repository" v0.9.0
commit_change "$stale_repository" stable
sign_tag "$stale_repository" v0.9.1
git -C "$stale_repository" switch -q -c release/hotfix/stale v0.9.0
commit_change "$stale_repository" stale-hotfix
sign_tag "$stale_repository" v0.9.2
expect_failure "stale hotfix ancestry" \
  validate_source "$stale_repository" v0.9.2 $'main\nrelease/hotfix/stale'

aligned_modules=$'core/pom.xml parent.version=1.0.0\ncli/pom.xml parent.version=1.0.0\nmaven-plugin/pom.xml parent.version=1.0.0\ngradle-plugin/pom.xml parent.version=1.0.0'
MAVEN_VERSION_OUTPUT=1.0.0 \
MAVEN_MODULE_VERSION_OUTPUTS="$aligned_modules" \
GRADLE_VERSION_OUTPUT=1.0.0 \
  bash "$version_validator" --expected-version 1.0.0 >/dev/null

misaligned_modules="${aligned_modules/maven-plugin\/pom.xml parent.version=1.0.0/maven-plugin\/pom.xml parent.version=0.9.0}"
validate_partial_version() {
  MAVEN_VERSION_OUTPUT=1.0.0 \
  MAVEN_MODULE_VERSION_OUTPUTS="$misaligned_modules" \
  GRADLE_VERSION_OUTPUT=1.0.0 \
    bash "$version_validator" --expected-version 1.0.0
}
expect_failure "partial Maven module version bump" validate_partial_version

echo "Release control tests passed."
