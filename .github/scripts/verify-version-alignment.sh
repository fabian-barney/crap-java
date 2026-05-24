#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: verify-version-alignment.sh [--expected-version <version>]

Validates that the root Maven POM version and Gradle plugin version resolve to
the same single-line value after stripping formatting noise. When
--expected-version is provided, the sanitized version must also match it
exactly.

Set MAVEN_VERSION_OUTPUT or GRADLE_VERSION_OUTPUT to override the default file-
based version sources for local dry-runs of malformed version scenarios.
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
  if [ ! -f pom.xml ]; then
    echo "::error::Missing root pom.xml." >&2
    exit 1
  fi
  perl -0ne '
    if (m{<project\b.*?<artifactId>\s*[^<]+?\s*</artifactId>\s*<version>\s*([^<]+?)\s*</version>}s) {
      print $1;
      exit 0;
    }
    exit 1;
  ' pom.xml || {
    echo "::error::Unable to locate the root Maven project.version in pom.xml." >&2
    exit 1
  }
}

read_gradle_version_output() {
  if [ "${GRADLE_VERSION_OUTPUT+x}" = x ]; then
    printf '%s' "$GRADLE_VERSION_OUTPUT"
    return
  fi
  perl -0ne '
    if (m{^\s*version\s*=\s*["'\'']([^"'\''\r\n]+)["'\'']\s*$}m) {
      print $1;
      exit 0;
    }
    exit 1;
  ' gradle-plugin/build.gradle.kts 2>/dev/null || perl -0ne '
    if (m{^\s*version\s*=\s*["'\'']([^"'\''\r\n]+)["'\'']\s*$}m) {
      print $1;
      exit 0;
    }
    exit 1;
  ' gradle-plugin/build.gradle 2>/dev/null || {
    echo "::error::Unable to locate the Gradle project.version assignment in gradle-plugin/build.gradle.kts or gradle-plugin/build.gradle." >&2
    exit 1
  }
}

main() {
  local expected_version=""
  local expected_version_requested=false
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
        expected_version_requested=true
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

  if [ "$expected_version_requested" = true ]; then
    expected_version="$(normalize_single_line "Expected version" "$expected_version")"
    if [ "$maven_version" != "$expected_version" ]; then
      echo "::error::Resolved project version ${maven_version} does not match expected version ${expected_version}." >&2
      exit 1
    fi
  fi

  echo "Validated version alignment: ${maven_version}"
}

main "$@"
