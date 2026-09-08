#!/usr/bin/env python3
"""Validate the release bundle's names and CycloneDX component contracts."""

from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path


def expected_timestamp() -> str:
    epoch = int(os.environ["SOURCE_DATE_EPOCH"])
    return datetime.fromtimestamp(epoch, timezone.utc).isoformat(
        timespec="seconds"
    ).replace("+00:00", "Z")


def verify_sbom(path: Path, component_name: str, version: str) -> None:
    document = json.loads(path.read_text(encoding="utf-8"))
    if document.get("bomFormat") != "CycloneDX" or document.get("specVersion") != "1.6":
        raise ValueError(f"{path.name} is not CycloneDX 1.6 JSON")
    if "serialNumber" in document:
        raise ValueError(f"{path.name} contains a random BOM serial number")
    metadata = document.get("metadata", {})
    if metadata.get("timestamp") != expected_timestamp():
        raise ValueError(f"{path.name} has a non-reproducible timestamp")
    component = metadata.get("component", {})
    if component.get("name") != component_name or component.get("version") != version:
        raise ValueError(f"{path.name} identifies the wrong component")
    components = document.get("components", [])
    if not components or not document.get("dependencies"):
        raise ValueError(f"{path.name} has no dependency inventory")
    if not any(candidate.get("licenses") for candidate in components):
        raise ValueError(f"{path.name} contains no available dependency license data")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("assets_directory", type=Path)
    parser.add_argument("version")
    args = parser.parse_args()
    assets = args.assets_directory

    expected_payloads = {
        f"crap-java-{args.version}.jar",
        f"crap-java-core-{args.version}.cdx.json",
        f"crap-java-cli-{args.version}.cdx.json",
        f"crap-java-maven-plugin-{args.version}.cdx.json",
        f"crap-java-gradle-plugin-{args.version}.cdx.json",
    }
    actual_payloads = {
        path.name
        for path in assets.iterdir()
        if path.suffix == ".jar" or path.name.endswith(".cdx.json")
    }
    if actual_payloads != expected_payloads:
        raise ValueError(f"Release payload mismatch: {sorted(actual_payloads)}")

    components = {
        "core": "crap-java-core",
        "cli": "crap-java-cli",
        "maven-plugin": "crap-java-maven-plugin",
        "gradle-plugin": "crap-java-gradle-plugin",
    }
    for suffix, component_name in components.items():
        verify_sbom(
            assets / f"crap-java-{suffix}-{args.version}.cdx.json",
            component_name,
            args.version,
        )


if __name__ == "__main__":
    main()
