"""Read-only deterministic verification; never runs qualification or database tests."""

import hashlib
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-F/generation-1")


def fingerprint(value: Any) -> str:
    return hashlib.sha256(
        json.dumps(
            value, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
        ).encode()
    ).hexdigest()


def verify() -> dict[str, Any]:
    binding = json.loads((CUSTODY / "replay-inputs.json").read_bytes())
    for name, expected in binding["hashes"].items():
        path = Path(name)
        if not path.is_absolute():
            path = ROOT / path
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError("EVIDENCE_HASH_MISMATCH: " + name)
    baseline = json.loads((CUSTODY / "baseline-hashes.json").read_bytes())
    for name, expected in baseline.items():
        if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != expected:
            raise ValueError("BASELINE_CHANGED: " + name)
    if len(baseline) != 1721:
        raise ValueError("BASELINE_COUNT")
    return {
        "contract": "Phase1444EFDeterministicReport.v1",
        "baseline": "8b3e52dd0a60455cb64de2027e88e87b0c41bde8",
        "baseline_fingerprint": fingerprint(baseline),
        "binding_fingerprint": fingerprint(binding),
        "original31": "NOT_EXECUTED",
        "historical_missing_receipts": 73,
        "isolation": "DEFER_PENDING_APPROVED_ISOLATION_HOST",
        "gate_a": "NOT_AUTHORIZED",
        "gate_b": "NOT_AUTHORIZED",
        "operational": "NO_GO",
        "real_kite_requests": 0,
    }


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True, separators=(",", ":"), allow_nan=False))
