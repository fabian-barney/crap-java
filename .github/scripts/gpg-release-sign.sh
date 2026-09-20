#!/usr/bin/env bash
set -euo pipefail
set +x

if [[ -z "${MAVEN_GPG_PASSPHRASE:-}" ]]; then
  echo "::error::MAVEN_GPG_PASSPHRASE is required for release tag signing." >&2
  exit 1
fi

release_passphrase="$MAVEN_GPG_PASSPHRASE"
unset MAVEN_GPG_PASSPHRASE
exec 3< <(printf '%s\n' "$release_passphrase")
unset release_passphrase
exec gpg --batch --no-tty --pinentry-mode loopback --passphrase-fd 3 "$@"
