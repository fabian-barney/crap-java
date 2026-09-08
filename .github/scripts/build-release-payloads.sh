#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <empty-output-directory>" >&2
  exit 2
fi
if [[ ! "${SOURCE_DATE_EPOCH:-}" =~ ^[0-9]+$ ]]; then
  echo "SOURCE_DATE_EPOCH must be set to the release commit timestamp." >&2
  exit 2
fi

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
output_directory="$1"
if [[ "$output_directory" != /* ]]; then
  output_directory="$repository_root/$output_directory"
fi
cd "$repository_root"

version="$(python3 .github/scripts/project-version.py)"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]]; then
  echo "Unable to resolve a release version: $version" >&2
  exit 1
fi

mvn -B -ntp -P'!quality-gates-all,release' \
  -DskipTests \
  -Dgpg.skip=true \
  -Dcentral.skipPublishing=true \
  -Dproject.build.outputTimestamp="$SOURCE_DATE_EPOCH" \
  clean package

chmod +x gradle-plugin/gradlew
(
  cd gradle-plugin
  ./gradlew --no-daemon clean assemble sourcesJar javadocJar cyclonedxDirectBom -x test
)

mkdir -p "$output_directory"
if find "$output_directory" -mindepth 1 -maxdepth 1 -print -quit | grep -q .; then
  echo "Output directory must be empty: $output_directory" >&2
  exit 2
fi
output_directory="$(cd "$output_directory" && pwd)"

declare -A payload_sources=(
  ["crap-java-${version}.jar"]="cli/target/crap-java-cli-${version}.jar"
  ["crap-java-core-${version}.cdx.json"]="core/target/bom.json"
  ["crap-java-cli-${version}.cdx.json"]="cli/target/bom.json"
  ["crap-java-maven-plugin-${version}.cdx.json"]="maven-plugin/target/bom.json"
  ["crap-java-gradle-plugin-${version}.cdx.json"]="gradle-plugin/build/reports/sbom/crap-java-gradle-plugin-${version}.cdx.json"
)

for payload in "${!payload_sources[@]}"; do
  source_path="${payload_sources[$payload]}"
  if [[ ! -f "$source_path" ]]; then
    echo "Missing release payload source: $source_path" >&2
    exit 1
  fi
  install -m 0644 "$source_path" "$output_directory/$payload"
done

for sbom in "$output_directory"/*.cdx.json; do
  python3 .github/scripts/normalize-sbom.py "$sbom" "$SOURCE_DATE_EPOCH"
done
