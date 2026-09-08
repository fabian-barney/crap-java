#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <release-assets-directory> <version>" >&2
  exit 2
fi

assets_directory="$(cd "$1" && pwd)"
version="$2"
python3 .github/scripts/verify-release-assets.py "$assets_directory" "$version"

(
  cd "$assets_directory"
  sha256sum --check --strict SHA256SUMS
  sha512sum --check --strict SHA512SUMS
)

mapfile -t signed_assets < <(
  find "$assets_directory" -maxdepth 1 -type f \
    \( -name '*.jar' -o -name '*.cdx.json' -o -name 'SHA256SUMS' -o -name 'SHA512SUMS' \) \
    -printf '%f\n' | sort
)
if [[ ${#signed_assets[@]} -ne 7 ]]; then
  echo "Expected seven signed assets, found ${#signed_assets[@]}." >&2
  exit 1
fi
for asset in "${signed_assets[@]}"; do
  signature="$assets_directory/${asset}.asc"
  if [[ ! -f "$signature" ]]; then
    echo "Missing detached signature: ${asset}.asc" >&2
    exit 1
  fi
  gpg --batch --verify "$signature" "$assets_directory/$asset"
done
