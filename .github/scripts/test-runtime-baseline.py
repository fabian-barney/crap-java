#!/usr/bin/env python3
"""Regression coverage for rejecting incompatible distributable archives."""

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile


spec = importlib.util.spec_from_file_location("baseline", Path(__file__).with_name("verify-runtime-baseline.py"))
baseline = importlib.util.module_from_spec(spec)
spec.loader.exec_module(baseline)


class RuntimeBaselineTest(unittest.TestCase):
    def archive(self, directory, major=65, extra=None, descriptor=None):
        path = Path(directory) / "artifact.jar"
        with ZipFile(path, "w") as archive:
            archive.writestr("media/barney/crap/core/Sample.class", b"\xca\xfe\xba\xbe\x00\x00" + major.to_bytes(2, "big"))
            for name in extra or []:
                archive.writestr(name, b"")
            if descriptor is not None:
                archive.writestr("META-INF/maven/plugin.xml", descriptor)
        return path

    def test_accepts_baseline_and_shared_annotations(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.archive(directory, extra=["com/fasterxml/jackson/annotation/JsonInclude.class"])
            self.assertEqual([], baseline.verify_archive(path))

    def test_rejects_incompatible_bytecode(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertIn("Java 21 bytecode", baseline.verify_archive(self.archive(directory, major=69))[0])

    def test_rejects_jackson_2_implementations(self):
        for package in ("core", "databind", "dataformat"):
            with self.subTest(package=package), tempfile.TemporaryDirectory() as directory:
                path = self.archive(directory, extra=[f"com/fasterxml/jackson/{package}/Sample.class"])
                self.assertIn("Jackson 2", baseline.verify_archive(path)[0])

    def test_checks_published_plugin_requirements(self):
        with tempfile.TemporaryDirectory() as directory:
            descriptor = "<plugin><requiredJavaVersion>21</requiredJavaVersion>"
            descriptor += "<requiredMavenVersion>3.9.16</requiredMavenVersion></plugin>"
            self.assertEqual([], baseline.verify_archive(self.archive(directory, descriptor=descriptor), True))
            incompatible = descriptor.replace("3.9.16", "3.9.0").replace(">21<", ">25<")
            self.assertEqual(2, len(baseline.verify_archive(self.archive(directory, descriptor=incompatible), True)))

    def test_checks_gradle_variant_requirements(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "module.json"
            variants = [{"name": name, "attributes": {"org.gradle.jvm.version": 21}}
                        for name in ("apiElements", "runtimeElements")]
            path.write_text(json.dumps({"variants": variants}))
            self.assertEqual([], baseline.verify_gradle_metadata(path))
            variants[0]["attributes"]["org.gradle.jvm.version"] = 25
            path.write_text(json.dumps({"variants": variants}))
            self.assertEqual(1, len(baseline.verify_gradle_metadata(path)))
            path.write_text(json.dumps({"variants": []}))
            self.assertEqual(2, len(baseline.verify_gradle_metadata(path)))


if __name__ == "__main__":
    unittest.main()
