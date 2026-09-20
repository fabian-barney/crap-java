#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
version_validator="$repository_root/.github/scripts/verify-version-alignment.sh"
source_validator="$repository_root/.github/scripts/verify-release-source.sh"
expected_fingerprint="${RELEASE_SIGNING_FINGERPRINT:-E6BB1FB6EE83EEAB7B408C6B5CA409BD8EE61724}"
repository="${GITHUB_REPOSITORY:-}"
release_commit="${GITHUB_SHA:-$(git rev-parse HEAD)}"
source_branch="${GITHUB_REF_NAME:-}"
run_attempt="${GITHUB_RUN_ATTEMPT:-1}"
output_file="${GITHUB_OUTPUT:-}"

error() {
  echo "::error::$*" >&2
  exit 1
}

write_output() {
  if [[ -n "$output_file" ]]; then
    printf '%s=%s\n' "$1" "$2" >> "$output_file"
  fi
}

read_parent_version() {
  local python_command=python3
  if [[ -n "${MSYSTEM:-}" ]]; then
    python_command=python
  fi
  git show "${release_commit}^1:pom.xml" | "$python_command" -c '
import sys
import xml.etree.ElementTree as element_tree

project = element_tree.parse(sys.stdin).getroot()
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
version = project.findtext("m:version", namespaces=namespace)
if not version or not version.strip():
    raise SystemExit("Unable to read the first-parent Maven project version.")
print(version.strip())
'
}

is_greater_version() {
  local candidate="$1"
  local baseline="$2"
  local candidate_major candidate_minor candidate_patch
  local baseline_major baseline_minor baseline_patch
  IFS=. read -r candidate_major candidate_minor candidate_patch <<< "$candidate"
  IFS=. read -r baseline_major baseline_minor baseline_patch <<< "$baseline"
  (( 10#$candidate_major > 10#$baseline_major )) && return 0
  (( 10#$candidate_major < 10#$baseline_major )) && return 1
  (( 10#$candidate_minor > 10#$baseline_minor )) && return 0
  (( 10#$candidate_minor < 10#$baseline_minor )) && return 1
  (( 10#$candidate_patch > 10#$baseline_patch ))
}

validate_changelog() {
  local version="$1"
  local python_command=python3
  if [[ -n "${MSYSTEM:-}" ]]; then
    python_command=python
  fi
  "$python_command" - "$version" CHANGELOG.md <<'PY'
import datetime
import re
import sys

version = sys.argv[1]
path = sys.argv[2]
pattern = re.compile(rf"^## {re.escape(version)} - (\d{{4}}-\d{{2}}-\d{{2}})$")
matches = []
with open(path, encoding="utf-8") as changelog:
    for line in changelog:
        match = pattern.fullmatch(line.rstrip("\n\r"))
        if match:
            matches.append(match.group(1))
if len(matches) != 1:
    raise SystemExit(f"Expected exactly one dated changelog heading for {version}; found {len(matches)}.")
datetime.date.fromisoformat(matches[0])
PY
}

is_protected_branch() {
  local branch="$1"
  if [[ -n "${RELEASE_PROTECTED_BRANCHES:-}" ]]; then
    grep -Fxq "$branch" <<< "$RELEASE_PROTECTED_BRANCHES"
    return
  fi
  [[ -n "$repository" && -n "${GH_TOKEN:-}" ]] \
    || error "GITHUB_REPOSITORY and GH_TOKEN are required to verify branch protection."
  local encoded_branch
  encoded_branch="$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$branch")"
  local rule_types
  rule_types="$(gh api "repos/$repository/rules/branches/$encoded_branch" --jq '.[].type')" || return 1
  local required_rule
  for required_rule in deletion non_fast_forward pull_request; do
    grep -Fxq "$required_rule" <<< "$rule_types" || return 1
  done
}

release_state() {
  if [[ -n "${RELEASE_EXISTING_RELEASE_STATE:-}" ]]; then
    printf '%s\n' "$RELEASE_EXISTING_RELEASE_STATE"
    return
  fi
  local api_error
  local state
  api_error="$(mktemp)"
  if state="$(gh api "repos/$repository/releases/tags/$1" \
    --jq 'if .draft then "draft" else "published" end' 2>"$api_error")"; then
    rm -f "$api_error"
    printf '%s\n' "$state"
    return
  fi
  if grep -Fq '(HTTP 404)' "$api_error"; then
    rm -f "$api_error"
    printf '%s\n' none
    return
  fi
  cat "$api_error" >&2
  rm -f "$api_error"
  error "Unable to determine whether GitHub release $1 already exists."
}

validate_retry_tag() {
  local tag="$1"
  [[ "$run_attempt" =~ ^[0-9]+$ ]] || error "GITHUB_RUN_ATTEMPT must be numeric."
  (( run_attempt > 1 )) || error "Release tag $tag already exists on the initial workflow attempt."
  [[ "$(git rev-parse "$tag^{}")" == "$release_commit" ]] \
    || error "Existing release tag $tag does not point to $release_commit."
  RELEASE_TAG="$tag" RELEASE_SIGNING_FINGERPRINT="$expected_fingerprint" \
    bash "$source_validator"
}

main() {
  [[ -n "$source_branch" ]] || error "GITHUB_REF_NAME is required."
  [[ "$source_branch" == main || "$source_branch" =~ ^release/hotfix/[^/]+$ ]] \
    || error "Release source must be main or a single-segment release/hotfix/<slug> branch."
  [[ "$(git rev-parse HEAD)" == "$release_commit" ]] \
    || error "Checked-out commit does not match GITHUB_SHA $release_commit."
  git rev-parse "${release_commit}^1" >/dev/null 2>&1 \
    || error "Release candidate must have a first parent."

  local current_version
  local parent_version
  current_version="$(bash "$version_validator" --print-version)"
  parent_version="$(read_parent_version)"
  if [[ "$current_version" == "$parent_version" ]]; then
    write_output release false
    echo "No release candidate: project version remains $current_version."
    exit 0
  fi

  [[ "$current_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] \
    || error "Release version must be a stable semantic version: $current_version."
  [[ "$parent_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] \
    || error "First-parent version must be a stable semantic version: $parent_version."
  is_greater_version "$current_version" "$parent_version" \
    || error "Release version $current_version must be greater than first-parent version $parent_version."
  validate_changelog "$current_version"
  is_protected_branch "$source_branch" \
    || error "Release source branch $source_branch is not protected."

  local source_ref="refs/remotes/origin/$source_branch"
  if ! git show-ref --verify --quiet "$source_ref"; then
    source_ref="refs/heads/$source_branch"
  fi
  git merge-base --is-ancestor "$release_commit" "$source_ref" \
    || error "Release commit $release_commit is not reachable from $source_branch."

  local release_tag="v$current_version"
  local previous_stable_tag
  previous_stable_tag="$({
    git tag --list 'v*' --sort=-v:refname \
      | grep -E '^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$' \
      | grep -Fxv "$release_tag" \
      | head -n 1
  } || true)"
  if [[ -n "$previous_stable_tag" ]]; then
    is_greater_version "$current_version" "${previous_stable_tag#v}" \
      || error "Release version $current_version must be greater than latest stable tag $previous_stable_tag."
  fi
  if [[ "$source_branch" != main ]]; then
    [[ -n "$previous_stable_tag" ]] \
      || error "An isolated hotfix release requires a preceding stable release tag."
    git merge-base --is-ancestor "$previous_stable_tag^{}" "$source_ref" \
      || error "Hotfix source $source_branch does not contain latest stable tag $previous_stable_tag."
  fi

  local existing_tag=false
  if git show-ref --verify --quiet "refs/tags/$release_tag"; then
    existing_tag=true
    validate_retry_tag "$release_tag"
  fi

  local existing_release
  existing_release="$(release_state "$release_tag")"
  case "$existing_release" in
    none)
      ;;
    draft)
      [[ "$existing_tag" == true && "$run_attempt" -gt 1 ]] \
        || error "A draft GitHub release already exists for $release_tag outside a valid rerun."
      ;;
    published)
      error "GitHub release $release_tag is already published."
      ;;
    *)
      error "Unknown GitHub release state for $release_tag: $existing_release."
      ;;
  esac

  write_output release true
  write_output version "$current_version"
  write_output tag "$release_tag"
  write_output commit "$release_commit"
  write_output source_branch "$source_branch"
  echo "Validated release candidate $release_tag at $release_commit from $source_branch."
}

main "$@"
