"""Deterministic identity helpers for normalized CycloneDX documents."""

from __future__ import annotations

import json
import uuid
from typing import Any


SERIAL_NAMESPACE = uuid.uuid5(
    uuid.NAMESPACE_URL,
    "https://github.com/fabian-barney/crap-java#cyclonedx",
)


def canonicalize(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: canonicalize(value[key]) for key in sorted(value)}
    if isinstance(value, list):
        items = [canonicalize(item) for item in value]
        return sorted(
            items,
            key=lambda item: json.dumps(item, sort_keys=True, separators=(",", ":")),
        )
    return value


def deterministic_serial_number_from_canonical(document: dict[str, Any]) -> str:
    identity_document = dict(document)
    identity_document.pop("serialNumber", None)
    identity = json.dumps(
        identity_document,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return f"urn:uuid:{uuid.uuid5(SERIAL_NAMESPACE, identity)}"


def deterministic_serial_number(document: dict[str, Any]) -> str:
    return deterministic_serial_number_from_canonical(canonicalize(document))
