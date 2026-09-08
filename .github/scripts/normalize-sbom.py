#!/usr/bin/env python3
"""Normalize a CycloneDX JSON SBOM for deterministic release publication."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def canonicalize(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: canonicalize(value[key]) for key in sorted(value)}
    if isinstance(value, list):
        items = [canonicalize(item) for item in value]
        return sorted(items, key=lambda item: json.dumps(item, sort_keys=True, separators=(",", ":")))
    return value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("sbom", type=Path)
    parser.add_argument("source_date_epoch", type=int)
    args = parser.parse_args()

    document = json.loads(args.sbom.read_text(encoding="utf-8"))
    document.pop("serialNumber", None)
    metadata = document.setdefault("metadata", {})
    metadata["timestamp"] = datetime.fromtimestamp(
        args.source_date_epoch, timezone.utc
    ).isoformat(timespec="seconds").replace("+00:00", "Z")
    normalized = canonicalize(document)
    args.sbom.write_text(
        json.dumps(normalized, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


if __name__ == "__main__":
    main()
