#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: verify-version-alignment.sh [--expected-version <version>]

Validates that Maven and Gradle resolve to the same single-line version after
stripping formatting noise. When --expected-version is provided, the sanitized
version must also match it exactly.

Set MAVEN_VERSION_OUTPUT or GRADLE_VERSION_OUTPUT to inject raw command output
for local dry-runs of malformed version scenarios.
EOF
}

strip_formatting_noise() {
  printf '%s' "$1" \
    | tr -d '\r' \
    | sed -E $'s/\x1B\\[[0-9;?]*[ -/]*[@-~]//g'
}

normalize_single_line() {
  local label="$1"
  local raw_value="$2"
  local sanitized_value
  local non_empty_line_count
  local normalized_value

  sanitized_value="$(strip_formatting_noise "$raw_value")"
  non_empty_line_count="$(
    printf '%s\n' "$sanitized_value" | awk '
      {
        line = $0
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
        if (length(line) > 0) {
          count++
        }
      }
      END {
        print count + 0
      }
    '
  )"

  if [ "$non_empty_line_count" -ne 1 ]; then
    echo "::error::${label} must resolve to exactly one non-empty line after stripping formatting noise." >&2
    printf 'Sanitized output for %s:\n%s\n' "$label" "$sanitized_value" >&2
    exit 1
  fi

  normalized_value="$(
    printf '%s\n' "$sanitized_value" | awk '
      {
        line = $0
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
        if (length(line) > 0) {
          print line
          exit
        }
      }
    '
  )"

  if [ -z "$normalized_value" ]; then
    echo "::error::${label} resolved to an empty value after stripping formatting noise." >&2
    exit 1
  fi

  printf '%s\n' "$normalized_value"
}

read_maven_version_output() {
  if [ "${MAVEN_VERSION_OUTPUT+x}" = x ]; then
    printf '%s' "$MAVEN_VERSION_OUTPUT"
    return
  fi
  mvn help:evaluate -Dexpression=project.version -q -DforceStdout
}

read_gradle_version_output() {
  chmod +x gradle-plugin/gradlew
  if [ "${GRADLE_VERSION_OUTPUT+x}" = x ]; then
    printf '%s' "$GRADLE_VERSION_OUTPUT"
    return
  fi
  (
    cd gradle-plugin
    ./gradlew -q properties | awk -F': ' '/^version:/ {print $2; exit}'
  )
}

main() {
  local expected_version=""
  local maven_version
  local gradle_version

  while [ "$#" -gt 0 ]; do
    case "$1" in
      --expected-version)
        if [ "$#" -lt 2 ]; then
          echo "::error::Missing value for --expected-version." >&2
          usage >&2
          exit 1
        fi
        expected_version="$2"
        shift 2
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        echo "::error::Unknown argument: $1" >&2
        usage >&2
        exit 1
        ;;
    esac
  done

  maven_version="$(normalize_single_line "Maven project.version" "$(read_maven_version_output)")"
  gradle_version="$(normalize_single_line "Gradle project.version" "$(read_gradle_version_output)")"

  if [ "$maven_version" != "$gradle_version" ]; then
    echo "::error::Maven and Gradle versions disagree: Maven=${maven_version}, Gradle=${gradle_version}." >&2
    exit 1
  fi

  if [ -n "$expected_version" ]; then
    expected_version="$(normalize_single_line "Expected version" "$expected_version")"
    if [ "$maven_version" != "$expected_version" ]; then
      echo "::error::Resolved project version ${maven_version} does not match expected version ${expected_version}." >&2
      exit 1
    fi
  fi

  echo "Validated version alignment: ${maven_version}"
}

main "$@"
