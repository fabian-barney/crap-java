#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <version>" >&2
  exit 2
fi

version="$1"
verification_root="$(mktemp -d)"
central_base="https://repo.maven.apache.org/maven2/media/barney"

download_with_retry() {
  local url="$1"
  local destination="$2"
  local attempts=40
  local delay_seconds=15
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl --fail --location --silent --show-error \
      --connect-timeout 15 --max-time 60 \
      --output "$destination" "$url"; then
      return 0
    fi
    if ((attempt == attempts)); then
      echo "Timed out waiting for $url" >&2
      return 1
    fi
    sleep "$delay_seconds"
  done
}

for artifact in crap-java-core crap-java-cli crap-java-maven-plugin crap-java-gradle-plugin; do
  artifact_base="${central_base}/${artifact}/${version}/${artifact}-${version}"
  for suffix in .jar -sources.jar -javadoc.jar; do
    file_name="${artifact}-${version}${suffix}"
    download_with_retry "${artifact_base}${suffix}" "$verification_root/$file_name"
    download_with_retry "${artifact_base}${suffix}.asc" "$verification_root/${file_name}.asc"
    gpg --batch --verify "$verification_root/${file_name}.asc" "$verification_root/$file_name"
  done
done

plugin_portal_url="https://plugins.gradle.org/plugin/media.barney.crap-java/${version}"
download_with_retry "$plugin_portal_url" "$verification_root/gradle-plugin-portal.html"
grep --fixed-strings "${version}" "$verification_root/gradle-plugin-portal.html" >/dev/null
