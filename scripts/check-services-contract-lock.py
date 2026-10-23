#!/usr/bin/env python3
"""Verify the exact voluntary UNI-01 desktop-only codec copy without network access."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
lock = json.loads((root / "docs/intensive-2026-10-22/UNI-01-SERVICES-LOCK.json").read_text())
expected = {
    "desktop/src/main/java/org/jarvis/contracts/v1/RequestEnvelope.java",
    "desktop/src/main/java/org/jarvis/contracts/v1/ContractSupport.java",
}
assert lock["repository"] == "bubth720/JARVIS-UNIFIED"
assert lock["commit"] == "d947dadf73f8dec91543a3f86a1aa2ffef0b0d2f"
assert set(lock["files"]) == expected
for relative, digest in lock["files"].items():
    assert hashlib.sha256((root / relative).read_bytes()).hexdigest() == digest, relative
print(json.dumps({"verified": len(expected), "scope": "desktop_jvm_only", "commit": lock["commit"]}))
