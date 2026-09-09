#!/usr/bin/env bash
set -euo pipefail

expected_fingerprint="${RELEASE_SIGNING_FINGERPRINT:-E6BB1FB6EE83EEAB7B408C6B5CA409BD8EE61724}"
public_key="${RELEASE_SIGNING_PUBLIC_KEY:-.github/release-signing-key.asc}"
tag="${RELEASE_TAG:-${GITHUB_REF_NAME:-}}"
repository="${GITHUB_REPOSITORY:-}"
main_ref="${RELEASE_MAIN_REF:-refs/remotes/origin/main}"
hotfix_ref_prefix="${RELEASE_HOTFIX_REF_PREFIX:-refs/remotes/origin/release/hotfix/}"

if [[ -z "$tag" || ! "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::Release tag must be a stable semantic version such as v1.0.0." >&2
  exit 1
fi
if [[ ! -f "$public_key" ]]; then
  echo "::error::Missing committed release signing key: $public_key" >&2
  exit 1
fi
if [[ "$(git cat-file -t "refs/tags/$tag" 2>/dev/null || true)" != tag ]]; then
  echo "::error::Release tag $tag must be annotated." >&2
  exit 1
fi

verification_home="$(mktemp -d)"
if [[ -n "${MSYSTEM:-}" ]]; then
  export MSYS2_ENV_CONV_EXCL="${MSYS2_ENV_CONV_EXCL:+${MSYS2_ENV_CONV_EXCL};}GNUPGHOME"
fi
cleanup() {
  if [[ -n "$verification_home" && -d "$verification_home" ]]; then
    rm -rf -- "$verification_home"
  fi
}
trap cleanup EXIT

key_fingerprint="$({
  gpg --batch --homedir "$verification_home" --with-colons \
    --import-options show-only --import "$public_key" 2>/dev/null
} | awk -F: '$1 == "fpr" { print $10; exit }')"
if [[ "$key_fingerprint" != "$expected_fingerprint" ]]; then
  echo "::error::Committed signing key fingerprint is $key_fingerprint, expected $expected_fingerprint." >&2
  exit 1
fi
gpg --batch --homedir "$verification_home" --import "$public_key" >/dev/null 2>&1
verify_output="$(GNUPGHOME="$verification_home" git verify-tag --raw "$tag" 2>&1)" || {
  echo "::error::Release tag $tag does not have a valid signature from the committed key." >&2
  printf '%s\n' "$verify_output" >&2
  exit 1
}
if ! grep -Fq "[GNUPG:] VALIDSIG $expected_fingerprint " <<<"$verify_output"; then
  echo "::error::Release tag $tag was not signed by $expected_fingerprint." >&2
  exit 1
fi

branch_name_from_ref() {
  local ref="$1"
  ref="${ref#refs/remotes/origin/}"
  printf '%s\n' "${ref#refs/heads/}"
}

is_protected_branch() {
  local branch="$1"
  if [[ -n "${RELEASE_PROTECTED_BRANCHES:-}" ]]; then
    grep -Fxq "$branch" <<<"$RELEASE_PROTECTED_BRANCHES"
    return
  fi
  if [[ -z "$repository" || -z "${GH_TOKEN:-}" ]]; then
    echo "::error::GITHUB_REPOSITORY and GH_TOKEN are required to verify branch protection." >&2
    return 1
  fi
  local encoded_branch
  local rule_types
  encoded_branch="$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$branch")"
  rule_types="$(gh api "repos/$repository/rules/branches/$encoded_branch" --jq '.[].type')" || return 1
  for required_rule in deletion non_fast_forward pull_request; do
    if ! grep -Fxq "$required_rule" <<<"$rule_types"; then
      return 1
    fi
  done
}

tag_commit="$(git rev-parse "$tag^{}")"
main_branch="$(branch_name_from_ref "$main_ref")"
if git merge-base --is-ancestor "$tag_commit" "$main_ref"; then
  if ! is_protected_branch "$main_branch"; then
    echo "::error::Release source branch $main_branch is not protected." >&2
    exit 1
  fi
  echo "Validated signed release tag $tag from protected $main_branch."
  exit 0
fi

previous_stable_tag="$(
  git tag --list 'v*' --sort=-v:refname \
    | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' \
    | grep -Fxv "$tag" \
    | head -n 1 \
    || true
)"
if [[ -z "$previous_stable_tag" ]]; then
  echo "::error::An isolated hotfix release requires a preceding stable release tag." >&2
  exit 1
fi

stale_hotfix_branches=()
while IFS= read -r hotfix_ref; do
  [[ -n "$hotfix_ref" ]] || continue
  if ! git merge-base --is-ancestor "$tag_commit" "$hotfix_ref"; then
    continue
  fi
  hotfix_branch="$(branch_name_from_ref "$hotfix_ref")"
  if ! is_protected_branch "$hotfix_branch"; then
    continue
  fi
  if ! git merge-base --is-ancestor "$previous_stable_tag^{}" "$hotfix_ref"; then
    stale_hotfix_branches+=("$hotfix_branch")
    continue
  fi
  echo "Validated signed release tag $tag from protected $hotfix_branch based on $previous_stable_tag."
  exit 0
done < <(git for-each-ref --format='%(refname)' "$hotfix_ref_prefix")

if [[ ${#stale_hotfix_branches[@]} -gt 0 ]]; then
  echo "::error::Protected hotfix source does not contain latest stable tag $previous_stable_tag: ${stale_hotfix_branches[*]}." >&2
  exit 1
fi
echo "::error::Release tag $tag is not reachable from protected $main_branch or an eligible protected release/hotfix/** branch." >&2
exit 1
