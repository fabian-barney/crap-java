#!/usr/bin/env python3
"""Print the root Maven project version without invoking the build."""

import xml.etree.ElementTree as ElementTree
from pathlib import Path


namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ElementTree.parse(Path("pom.xml")).getroot()
version = root.findtext("m:version", namespaces=namespace)
if not version:
    raise ValueError("Root Maven project version is missing")
print(version)
