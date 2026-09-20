#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source_validator="$repository_root/.github/scripts/verify-release-source.sh"
version_validator="$repository_root/.github/scripts/verify-version-alignment.sh"
candidate_validator="$repository_root/.github/scripts/prepare-release-candidate.sh"
required_check_waiter="$repository_root/.github/scripts/wait-for-required-check.sh"
tag_creator="$repository_root/.github/scripts/create-signed-release-tag.sh"
tag_signer="$repository_root/.github/scripts/gpg-release-sign.sh"
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

tag_passphrase="automated-release-test-passphrase"
cat > "$test_root/protected-gpg-batch.txt" <<EOF
Key-Type: RSA
Key-Length: 2048
Key-Usage: sign
Name-Real: automated release test
Name-Email: automated-release@example.com
Expire-Date: 0
Passphrase: $tag_passphrase
%commit
EOF
gpg --batch --pinentry-mode loopback --generate-key "$test_root/protected-gpg-batch.txt" >/dev/null 2>&1
tag_fingerprint="$(gpg --batch --with-colons --list-secret-keys automated-release@example.com \
  | awk -F: '$1 == "fpr" { print $10; exit }')"
tag_public_key="$test_root/automated-release-key.asc"
gpg --batch --armor --export "$tag_fingerprint" > "$tag_public_key"

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

add_local_remote() {
  local repository_path="$1"
  local remote_path="$test_root/$(basename "$repository_path")-remote.git"
  git init -q --bare "$remote_path"
  git -C "$repository_path" remote add origin "$remote_path"
  git -C "$repository_path" push -q -u origin main
}

write_candidate_pom() {
  local path="$1"
  local version="$2"
  cat > "$path/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>test</groupId>
  <artifactId>release-candidate</artifactId>
  <version>$version</version>
</project>
EOF
}

candidate_modules() {
  local version="$1"
  printf '%s\n' \
    "core/pom.xml parent.version=$version" \
    "cli/pom.xml parent.version=$version" \
    "maven-plugin/pom.xml parent.version=$version" \
    "gradle-plugin/pom.xml parent.version=$version"
}

new_candidate_repository() {
  local name="$1"
  local parent_version="$2"
  local current_version="$3"
  local path
  path="$(new_repository "$name")"
  write_candidate_pom "$path" "$parent_version"
  printf '# Changelog\n\n## %s - 2026-09-20\n\n- Test release.\n' "$parent_version" > "$path/CHANGELOG.md"
  git -C "$path" add pom.xml CHANGELOG.md
  git -C "$path" commit -q -m "Prepare parent"
  write_candidate_pom "$path" "$current_version"
  printf '# Changelog\n\n## %s - 2026-09-20\n\n- Test release.\n' "$current_version" > "$path/CHANGELOG.md"
  git -C "$path" add pom.xml CHANGELOG.md
  git -C "$path" commit -q --allow-empty -m "Prepare candidate"
  printf '%s\n' "$path"
}

validate_candidate() {
  local path="$1"
  local current_version="$2"
  local branch="$3"
  local protected_branches="$4"
  local output_file="$path/candidate-output"
  (
    cd "$path"
    GITHUB_SHA="$(git rev-parse HEAD)" \
    GITHUB_REF_NAME="$branch" \
    GITHUB_RUN_ATTEMPT="${TEST_RUN_ATTEMPT:-1}" \
    GITHUB_OUTPUT="$output_file" \
    RELEASE_PROTECTED_BRANCHES="$protected_branches" \
    RELEASE_EXISTING_RELEASE_STATE="${TEST_RELEASE_STATE:-none}" \
    RELEASE_SIGNING_FINGERPRINT="$fingerprint" \
    RELEASE_SIGNING_PUBLIC_KEY="$public_key" \
    RELEASE_MAIN_REF=refs/heads/main \
    RELEASE_HOTFIX_REF_PREFIX=refs/heads/release/hotfix/ \
    MAVEN_VERSION_OUTPUT="$current_version" \
    MAVEN_MODULE_VERSION_OUTPUTS="$(candidate_modules "$current_version")" \
    GRADLE_VERSION_OUTPUT="$current_version" \
      bash "$candidate_validator"
  )
}

create_release_tag() {
  local path="$1"
  local tag="$2"
  local passphrase="$3"
  local attempt="${4:-1}"
  local expected_fingerprint="${5:-$tag_fingerprint}"
  (
    cd "$path"
    MAVEN_GPG_PASSPHRASE="$passphrase" \
    RELEASE_TAG="$tag" \
    RELEASE_COMMIT="$(git rev-parse HEAD)" \
    GITHUB_RUN_ATTEMPT="$attempt" \
    RELEASE_SIGNING_FINGERPRINT="$expected_fingerprint" \
    RELEASE_SIGNING_PUBLIC_KEY="$tag_public_key" \
    RELEASE_MAIN_REF=refs/heads/main \
    RELEASE_HOTFIX_REF_PREFIX=refs/heads/release/hotfix/ \
    RELEASE_PROTECTED_BRANCHES=main \
      bash "$tag_creator"
  )
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

expect_failure_containing() {
  local label="$1"
  local expected_message="$2"
  shift 2
  if "$@" >"$test_root/unexpected-success.log" 2>&1; then
    echo "Expected failure: $label" >&2
    cat "$test_root/unexpected-success.log" >&2
    exit 1
  fi
  if ! grep -Fq "$expected_message" "$test_root/unexpected-success.log"; then
    echo "Expected failure message for $label: $expected_message" >&2
    cat "$test_root/unexpected-success.log" >&2
    exit 1
  fi
}

grep -Fq '      - "release/hotfix/*"' "$repository_root/.github/workflows/release.yml"
if grep -Fq '      - "release/**"' "$repository_root/.github/workflows/release.yml"; then
  echo "Release workflow must only trigger for supported hotfix branch names." >&2
  exit 1
fi

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

printed_version="$(
  MAVEN_VERSION_OUTPUT=1.0.0 \
  MAVEN_MODULE_VERSION_OUTPUTS="$aligned_modules" \
  GRADLE_VERSION_OUTPUT=1.0.0 \
    bash "$version_validator" --print-version
)"
if [[ "$printed_version" != 1.0.0 ]]; then
  echo "Expected --print-version to emit only the aligned version." >&2
  exit 1
fi

unchanged_repository="$(new_candidate_repository unchanged-version 1.0.0 1.0.0)"
validate_candidate "$unchanged_repository" 1.0.0 main main >/dev/null
grep -Fxq 'release=false' "$unchanged_repository/candidate-output"

candidate_repository="$(new_candidate_repository valid-candidate 1.0.0 1.0.1)"
validate_candidate "$candidate_repository" 1.0.1 main main >/dev/null
grep -Fxq 'release=true' "$candidate_repository/candidate-output"
grep -Fxq 'version=1.0.1' "$candidate_repository/candidate-output"
grep -Fxq 'tag=v1.0.1' "$candidate_repository/candidate-output"

missing_parent_pom_repository="$(new_repository missing-parent-pom)"
write_candidate_pom "$missing_parent_pom_repository" 1.0.1
printf '# Changelog\n\n## 1.0.1 - 2026-09-20\n\n- Test release.\n' \
  > "$missing_parent_pom_repository/CHANGELOG.md"
git -C "$missing_parent_pom_repository" add pom.xml CHANGELOG.md
git -C "$missing_parent_pom_repository" commit -q -m "Prepare candidate"
expect_failure_containing "missing first-parent POM" \
  "::error::Unable to read the first-parent Maven project version." \
  validate_candidate "$missing_parent_pom_repository" 1.0.1 main main

misaligned_candidate="$(new_candidate_repository misaligned-candidate 1.0.0 1.0.1)"
validate_misaligned_candidate() {
  (
    export MAVEN_MODULE_VERSION_OUTPUTS="$(candidate_modules 1.0.1)"
    export MAVEN_VERSION_OUTPUT=1.0.1
    export GRADLE_VERSION_OUTPUT=1.0.2
    cd "$misaligned_candidate"
    GITHUB_SHA="$(git rev-parse HEAD)" \
    GITHUB_REF_NAME=main \
    GITHUB_RUN_ATTEMPT=1 \
    RELEASE_PROTECTED_BRANCHES=main \
    RELEASE_EXISTING_RELEASE_STATE=none \
      bash "$candidate_validator"
  )
}
expect_failure "misaligned release candidate" validate_misaligned_candidate

prerelease_repository="$(new_candidate_repository prerelease-version 1.0.0 1.0.1-rc.1)"
expect_failure "prerelease version" \
  validate_candidate "$prerelease_repository" 1.0.1-rc.1 main main

downgrade_repository="$(new_candidate_repository downgrade-version 1.0.0 0.9.9)"
expect_failure "non-increasing release version" \
  validate_candidate "$downgrade_repository" 0.9.9 main main

missing_changelog_repository="$(new_candidate_repository missing-changelog 1.0.0 1.0.1)"
printf '# Changelog\n\n## Unreleased\n' > "$missing_changelog_repository/CHANGELOG.md"
git -C "$missing_changelog_repository" add CHANGELOG.md
git -C "$missing_changelog_repository" commit -q --amend --no-edit
expect_failure "missing release changelog heading" \
  validate_candidate "$missing_changelog_repository" 1.0.1 main main

unprotected_repository="$(new_candidate_repository unprotected-source 1.0.0 1.0.1)"
expect_failure "unprotected release source" \
  validate_candidate "$unprotected_repository" 1.0.1 main protected-main

invalid_hotfix_repository="$(new_candidate_repository invalid-hotfix-name 1.0.0 1.0.1)"
expect_failure "invalid hotfix branch shape" \
  validate_candidate "$invalid_hotfix_repository" 1.0.1 release/hotfix/too/deep release/hotfix/too/deep

tag_collision_repository="$(new_candidate_repository tag-collision 1.0.0 1.0.1)"
git -C "$tag_collision_repository" tag -a v1.0.1 -m v1.0.1
expect_failure "initial release tag collision" \
  validate_candidate "$tag_collision_repository" 1.0.1 main main

published_collision_repository="$(new_candidate_repository published-collision 1.0.0 1.0.1)"
TEST_RELEASE_STATE=published expect_failure "published release collision" \
  validate_candidate "$published_collision_repository" 1.0.1 main main

draft_collision_repository="$(new_candidate_repository draft-collision 1.0.0 1.0.1)"
TEST_RELEASE_STATE=draft expect_failure "unexpected draft release collision" \
  validate_candidate "$draft_collision_repository" 1.0.1 main main

retry_candidate_repository="$(new_candidate_repository retry-candidate 1.0.0 1.0.1)"
sign_tag "$retry_candidate_repository" v1.0.1
TEST_RUN_ATTEMPT=2 TEST_RELEASE_STATE=draft \
  validate_candidate "$retry_candidate_repository" 1.0.1 main main >/dev/null

different_retry_repository="$(new_candidate_repository different-retry-commit 1.0.0 1.0.1)"
git -C "$different_retry_repository" switch -q --detach HEAD^ >/dev/null
sign_tag "$different_retry_repository" v1.0.1
git -C "$different_retry_repository" switch -q main
TEST_RUN_ATTEMPT=2 expect_failure "retry tag on a different commit" \
  validate_candidate "$different_retry_repository" 1.0.1 main main

mock_bin="$test_root/mock-bin"
mkdir "$mock_bin"
cat > "$mock_bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
count=0
if [[ -f "$MOCK_GH_COUNTER" ]]; then
  count="$(cat "$MOCK_GH_COUNTER")"
fi
count=$((count + 1))
printf '%s\n' "$count" > "$MOCK_GH_COUNTER"
sed -n "${count}p" "$MOCK_GH_RESPONSES"
EOF
chmod +x "$mock_bin/gh"

wait_for_check() {
  local responses="$1"
  local max_attempts="$2"
  local response_file="$test_root/check-responses"
  local counter_file="$test_root/check-counter"
  printf '%s\n' "$responses" > "$response_file"
  rm -f "$counter_file"
  PATH="$mock_bin:$PATH" \
  MOCK_GH_RESPONSES="$response_file" \
  MOCK_GH_COUNTER="$counter_file" \
  GH_TOKEN=test-token \
  GITHUB_REPOSITORY=test/repository \
  RELEASE_COMMIT=0123456789abcdef \
  RELEASE_CHECK_MAX_ATTEMPTS="$max_attempts" \
  RELEASE_CHECK_INTERVAL_SECONDS=0 \
    bash "$required_check_waiter"
}

wait_for_check $'{"check_runs":[]}\n{"check_runs":[{"name":"verify / required","status":"in_progress","conclusion":null}]}\n{"check_runs":[{"name":"verify / required","status":"completed","conclusion":"success"}]}' 3 >/dev/null
expect_failure "failed required check" \
  wait_for_check '{"check_runs":[{"name":"verify / required","status":"completed","conclusion":"failure"}]}' 1
expect_failure "cancelled required check" \
  wait_for_check '{"check_runs":[{"name":"verify / required","status":"completed","conclusion":"cancelled"}]}' 1
expect_failure "required check timeout" wait_for_check '{"check_runs":[]}' 2

automated_tag_repository="$(new_repository automated-tag)"
add_local_remote "$automated_tag_repository"
tag_output="$test_root/tag-output"
create_release_tag "$automated_tag_repository" v1.1.0 "$tag_passphrase" >"$tag_output" 2>&1
git -C "$automated_tag_repository" verify-tag v1.1.0 >/dev/null 2>&1
if grep -Fq "$tag_passphrase" "$tag_output"; then
  echo "Tag-signing output exposed the passphrase." >&2
  exit 1
fi
expect_failure "initial attempt with existing tag" \
  create_release_tag "$automated_tag_repository" v1.1.0 "$tag_passphrase" 1
create_release_tag "$automated_tag_repository" v1.1.0 "$tag_passphrase" 2 >/dev/null
commit_change "$automated_tag_repository" "different release commit"
expect_failure "rerun tag on a different commit" \
  create_release_tag "$automated_tag_repository" v1.1.0 "$tag_passphrase" 2

gpgconf --kill gpg-agent >/dev/null 2>&1 || true
wrong_passphrase_repository="$(new_repository wrong-passphrase)"
add_local_remote "$wrong_passphrase_repository"
expect_failure "wrong release signing passphrase" \
  create_release_tag "$wrong_passphrase_repository" v1.1.1 wrong-passphrase

missing_passphrase_repository="$(new_repository missing-passphrase)"
add_local_remote "$missing_passphrase_repository"
expect_failure "missing release signing passphrase" \
  create_release_tag "$missing_passphrase_repository" v1.1.2 ''

fingerprint_mismatch_repository="$(new_repository fingerprint-mismatch)"
add_local_remote "$fingerprint_mismatch_repository"
expect_failure "release signing fingerprint mismatch" \
  create_release_tag "$fingerprint_mismatch_repository" v1.1.3 "$tag_passphrase" 1 \
    0000000000000000000000000000000000000000

signature_mismatch_repository="$(new_repository signature-mismatch)"
add_local_remote "$signature_mismatch_repository"
sign_tag "$signature_mismatch_repository" v1.1.4
expect_failure "existing tag signed by another key" \
  create_release_tag "$signature_mismatch_repository" v1.1.4 "$tag_passphrase" 2

echo "Release control tests passed."
