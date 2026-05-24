#!/usr/bin/env bash
set -euo pipefail

if [ -z "${MAVEN_GPG_PRIVATE_KEY:-}" ]; then
  echo "::error::MAVEN_GPG_PRIVATE_KEY is required." >&2
  exit 1
fi

if [ -z "${MAVEN_GPG_PASSPHRASE:-}" ]; then
  echo "::error::MAVEN_GPG_PASSPHRASE is required." >&2
  exit 1
fi

if [ -z "${GITHUB_ENV:-}" ]; then
  echo "::error::GITHUB_ENV is required." >&2
  exit 1
fi

generate_env_delimiter_suffix() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen
    return
  fi

  if [ -r /proc/sys/kernel/random/uuid ]; then
    cat /proc/sys/kernel/random/uuid
    return
  fi

  if command -v python3 >/dev/null 2>&1; then
    python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
    return
  fi

  echo "::error::Unable to generate a unique GITHUB_ENV delimiter because uuidgen, /proc/sys/kernel/random/uuid, and python3 are unavailable." >&2
  exit 1
}

normalized_key="$(mktemp)"
candidate_key="$(mktemp)"
temp_files=("$normalized_key" "$candidate_key")

cleanup() {
  local path
  for path in "${temp_files[@]}"; do
    if [ -n "$path" ] && [ -e "$path" ]; then
      rm -f "$path"
    fi
  done
}

trap cleanup EXIT

printf '%s' "$MAVEN_GPG_PRIVATE_KEY" > "$candidate_key"
import_succeeded=0

for decode_depth in 0 1 2 3; do
  candidate_home="$(mktemp -d)"
  if GNUPGHOME="$candidate_home" gpg --batch --import "$candidate_key" >/dev/null 2>&1; then
    export GNUPGHOME="$candidate_home"
    echo "GNUPGHOME=${GNUPGHOME}" >> "$GITHUB_ENV"
    rm -f "$candidate_key"
    candidate_key=""
    import_succeeded=1
    break
  fi

  if [ "$decode_depth" -eq 3 ]; then
    rm -rf "$candidate_home"
    break
  fi

  next_key="$(mktemp)"
  rm -rf "$candidate_home"
  if ! base64 --decode "$candidate_key" > "$next_key" 2>/dev/null; then
    rm -f "$next_key"
    break
  fi
  rm -f "$candidate_key"
  temp_files+=("$next_key")
  candidate_key="$next_key"
done

if [ "$import_succeeded" -ne 1 ]; then
  echo "::error::MAVEN_GPG_PRIVATE_KEY is not importable as raw or repeatedly base64-decoded OpenPGP private key material after three decode attempts. Ensure the secret contains the private key itself or a base64-wrapped copy of it." >&2
  exit 1
fi

gpg --batch --pinentry-mode loopback --passphrase "$MAVEN_GPG_PASSPHRASE" \
  --armor --export-secret-keys > "$normalized_key"

if [ ! -s "$normalized_key" ]; then
  echo "::error::Failed to export an armored private key after importing MAVEN_GPG_PRIVATE_KEY." >&2
  exit 1
fi

while IFS= read -r line; do
  echo "::add-mask::$line"
done < "$normalized_key"

gpg_env_delimiter="EOF_$(generate_env_delimiter_suffix)"
{
  echo "MAVEN_GPG_PRIVATE_KEY<<${gpg_env_delimiter}"
  cat "$normalized_key"
  echo "${gpg_env_delimiter}"
} >> "$GITHUB_ENV"
