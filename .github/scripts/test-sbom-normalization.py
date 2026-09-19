#!/usr/bin/env python3
"""Regression tests for deterministic, attestable CycloneDX normalization."""

from __future__ import annotations

import copy
import json
import os
import subprocess
import sys
import tempfile
import unittest
import uuid
from pathlib import Path


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
NORMALIZER = SCRIPT_DIRECTORY / "normalize-sbom.py"
VERIFIER = SCRIPT_DIRECTORY / "verify-release-assets.py"
SOURCE_DATE_EPOCH = 1_789_108_029


def sample_document(component_name: str = "crap-java-core") -> dict[str, object]:
    return {
        "specVersion": "1.6",
        "bomFormat": "CycloneDX",
        "serialNumber": "urn:uuid:00000000-0000-4000-8000-000000000000",
        "version": 1,
        "metadata": {
            "component": {
                "version": "1.0.1",
                "name": component_name,
                "type": "library",
            },
            "timestamp": "2099-01-01T00:00:00Z",
        },
        "components": [
            {
                "version": "2.0.0",
                "name": "second",
                "type": "library",
                "licenses": [{"license": {"id": "Apache-2.0"}}],
            },
            {
                "name": "first",
                "type": "library",
                "version": "1.0.0",
                "licenses": [{"license": {"id": "MIT"}}],
            },
        ],
        "dependencies": [
            {"dependsOn": ["second", "first"], "ref": component_name},
        ],
    }


def normalize(path: Path, epoch: int = SOURCE_DATE_EPOCH) -> dict[str, object]:
    subprocess.run(
        [sys.executable, "-B", str(NORMALIZER), str(path), str(epoch)],
        check=True,
    )
    return json.loads(path.read_text(encoding="utf-8"))


class SbomNormalizationTest(unittest.TestCase):
    def test_equivalent_documents_are_byte_identical_and_attestable(self) -> None:
        first = sample_document()
        second = copy.deepcopy(first)
        second["serialNumber"] = "urn:uuid:11111111-1111-4111-8111-111111111111"
        second["components"] = list(reversed(second["components"]))
        second["dependencies"][0]["dependsOn"].reverse()

        with tempfile.TemporaryDirectory() as directory:
            first_path = Path(directory) / "first.json"
            second_path = Path(directory) / "second.json"
            first_path.write_text(json.dumps(first), encoding="utf-8")
            second_path.write_text(json.dumps(second), encoding="utf-8")

            normalized = normalize(first_path)
            normalize(second_path)

            self.assertEqual(first_path.read_bytes(), second_path.read_bytes())
            serial_number = normalized["serialNumber"]
            self.assertTrue(serial_number.startswith("urn:uuid:"))
            self.assertEqual(5, uuid.UUID(serial_number.removeprefix("urn:uuid:")).version)
            self.assertTrue(
                all(normalized.get(field) for field in ("bomFormat", "specVersion", "serialNumber"))
            )
            self.assertEqual("2026-09-11T06:27:09Z", normalized["metadata"]["timestamp"])

    def test_distinct_component_documents_have_distinct_serial_numbers(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            core_path = Path(directory) / "core.json"
            cli_path = Path(directory) / "cli.json"
            core_path.write_text(json.dumps(sample_document()), encoding="utf-8")
            cli_path.write_text(
                json.dumps(sample_document("crap-java-cli")),
                encoding="utf-8",
            )

            core = normalize(core_path)
            cli = normalize(cli_path)

            self.assertNotEqual(core["serialNumber"], cli["serialNumber"])

    def test_release_verifier_accepts_attestable_serials_and_rejects_missing_one(self) -> None:
        components = {
            "core": "crap-java-core",
            "cli": "crap-java-cli",
            "maven-plugin": "crap-java-maven-plugin",
            "gradle-plugin": "crap-java-gradle-plugin",
        }
        with tempfile.TemporaryDirectory() as directory:
            assets = Path(directory)
            (assets / "crap-java-1.0.1.jar").write_bytes(b"test archive")
            for suffix, component_name in components.items():
                sbom = assets / f"crap-java-{suffix}-1.0.1.cdx.json"
                sbom.write_text(
                    json.dumps(sample_document(component_name)),
                    encoding="utf-8",
                )
                normalize(sbom)

            environment = os.environ.copy()
            environment["SOURCE_DATE_EPOCH"] = str(SOURCE_DATE_EPOCH)
            verification_command = [
                sys.executable,
                "-B",
                str(VERIFIER),
                str(assets),
                "1.0.1",
            ]
            subprocess.run(verification_command, check=True, env=environment)

            core_sbom = assets / "crap-java-core-1.0.1.cdx.json"
            core_document = json.loads(core_sbom.read_text(encoding="utf-8"))
            core_document.pop("serialNumber")
            core_sbom.write_text(json.dumps(core_document), encoding="utf-8")
            rejected = subprocess.run(
                verification_command,
                check=False,
                capture_output=True,
                env=environment,
                text=True,
            )

            self.assertNotEqual(0, rejected.returncode)
            self.assertIn("not recognized as CycloneDX by actions/attest", rejected.stderr)


if __name__ == "__main__":
    unittest.main()
