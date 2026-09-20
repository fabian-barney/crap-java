#!/usr/bin/env bash
set -euo pipefail

repository="${GITHUB_REPOSITORY:-}"
commit="${RELEASE_COMMIT:-${GITHUB_SHA:-}}"
check_name="${RELEASE_REQUIRED_CHECK:-verify / required}"
max_attempts="${RELEASE_CHECK_MAX_ATTEMPTS:-60}"
interval_seconds="${RELEASE_CHECK_INTERVAL_SECONDS:-30}"

error() {
  echo "::error::$*" >&2
  exit 1
}

[[ -n "$repository" && -n "$commit" && -n "${GH_TOKEN:-}" ]] \
  || error "GITHUB_REPOSITORY, RELEASE_COMMIT or GITHUB_SHA, and GH_TOKEN are required."
[[ "$max_attempts" =~ ^[1-9][0-9]*$ ]] || error "RELEASE_CHECK_MAX_ATTEMPTS must be a positive integer."
[[ "$interval_seconds" =~ ^[0-9]+$ ]] || error "RELEASE_CHECK_INTERVAL_SECONDS must be a non-negative integer."

for (( attempt = 1; attempt <= max_attempts; attempt++ )); do
  response="$(gh api --method GET "repos/$repository/commits/$commit/check-runs" \
    -f check_name="$check_name" -f filter=latest)" \
    || error "Unable to query required check $check_name for $commit."
  python_command=python3
  if [[ -n "${MSYSTEM:-}" ]]; then
    python_command=python
  fi
  parsed="$($python_command -c '
import json
import sys

name = sys.argv[1]
document = json.load(sys.stdin)
check = next((item for item in document.get("check_runs", []) if item.get("name") == name), {})
print(f"{check.get('"'"'status'"'"', '"'"'missing'"'"')}\t{check.get('"'"'conclusion'"'"') or '"'"''"'"'}")
' "$check_name" <<< "$response")" || error "Required-check response was not valid JSON."
  IFS=$'\t' read -r status conclusion <<< "$parsed"

  if [[ "$status" == completed ]]; then
    if [[ "$conclusion" == success ]]; then
      echo "Required check $check_name succeeded for $commit."
      exit 0
    fi
    error "Required check $check_name completed with conclusion ${conclusion:-unknown} for $commit."
  fi
  if (( attempt < max_attempts )); then
    echo "Waiting for required check $check_name on $commit (state: $status, attempt $attempt/$max_attempts)."
    sleep "$interval_seconds"
  fi
done

error "Timed out waiting for required check $check_name on $commit."
