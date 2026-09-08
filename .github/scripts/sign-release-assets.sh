#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <release-assets-directory>" >&2
  exit 2
fi
if [[ -z "${MAVEN_GPG_PASSPHRASE:-}" ]]; then
  echo "MAVEN_GPG_PASSPHRASE is required." >&2
  exit 2
fi

assets_directory="$(cd "$1" && pwd)"
mapfile -t payloads < <(
  find "$assets_directory" -maxdepth 1 -type f \
    \( -name '*.jar' -o -name '*.cdx.json' \) -printf '%f\n' | sort
)
if [[ ${#payloads[@]} -ne 5 ]]; then
  echo "Expected five release payloads, found ${#payloads[@]}." >&2
  exit 1
fi

(
  cd "$assets_directory"
  sha256sum "${payloads[@]}" > SHA256SUMS
  sha512sum "${payloads[@]}" > SHA512SUMS
)

signing_key_arguments=()
if [[ -n "${RELEASE_SIGNING_FINGERPRINT:-}" ]]; then
  signing_key_arguments=(--local-user "$RELEASE_SIGNING_FINGERPRINT")
fi
for asset in "${payloads[@]}" SHA256SUMS SHA512SUMS; do
  printf '%s' "$MAVEN_GPG_PASSPHRASE" | gpg --batch --yes --armor --detach-sign \
      --pinentry-mode loopback \
      --passphrase-fd 0 \
      "${signing_key_arguments[@]}" \
      --output "$assets_directory/${asset}.asc" \
      "$assets_directory/$asset"
done
