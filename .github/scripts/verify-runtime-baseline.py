#!/usr/bin/env python3
"""Verify the runtime contract of the actual distributable archives."""

import argparse
import json
import xml.etree.ElementTree as ElementTree
from pathlib import Path
from zipfile import ZipFile


PROJECT_CLASSES = "media/barney/crap/"
JACKSON_2_IMPLEMENTATIONS = (
    "com/fasterxml/jackson/core/",
    "com/fasterxml/jackson/databind/",
    "com/fasterxml/jackson/dataformat/",
)


def verify_archive(path: Path, maven_plugin: bool = False) -> list[str]:
    failures = []
    with ZipFile(path) as archive:
        classes = [name for name in archive.namelist()
                   if name.startswith(PROJECT_CLASSES) and name.endswith(".class")]
        if not classes:
            failures.append(f"{path}: no project classes found")
        for name in classes:
            header = archive.read(name)[:8]
            if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(header[6:8], "big") != 65:
                failures.append(f"{path}: {name} does not contain Java 21 bytecode")
        for name in archive.namelist():
            if name.startswith(JACKSON_2_IMPLEMENTATIONS):
                failures.append(f"{path}: Jackson 2 implementation remains: {name}")
        if maven_plugin:
            descriptor = ElementTree.fromstring(archive.read("META-INF/maven/plugin.xml"))
            if descriptor.findtext("requiredJavaVersion") != "21":
                failures.append(f"{path}: Maven plugin does not require Java 21")
            if descriptor.findtext("requiredMavenVersion") != "3.9.16":
                failures.append(f"{path}: Maven plugin does not require Maven 3.9.16")
    return failures


def verify_gradle_metadata(path: Path) -> list[str]:
    variants = {variant["name"]: variant for variant in json.loads(path.read_text())["variants"]}
    return [f"{path}: {name} does not require Java 21"
            for name in ("apiElements", "runtimeElements")
            if variants.get(name, {}).get("attributes", {}).get("org.gradle.jvm.version") != 21]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gradle-metadata", type=Path)
    arguments = parser.parse_args()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = ElementTree.parse("pom.xml").getroot().findtext("m:version", namespaces=namespace)
    paths = [Path(f"{module}/target/crap-java-{module}-{version}.jar")
             for module in ("core", "cli", "maven-plugin")]
    paths.append(Path(f"gradle-plugin/build/libs/crap-java-gradle-plugin-{version}.jar"))
    failures = []
    for path in paths:
        failures.extend(verify_archive(path, path.parent.parent.name == "maven-plugin"))
    if arguments.gradle_metadata:
        failures.extend(verify_gradle_metadata(arguments.gradle_metadata))
    if failures:
        raise SystemExit("\n".join(failures))
    print("Verified Java 21 bytecode, Maven requirements, and absence of Jackson 2 implementations.")


if __name__ == "__main__":
    main()
