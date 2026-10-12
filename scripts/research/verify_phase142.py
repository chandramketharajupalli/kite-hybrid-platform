"""Read-only Phase14.2 evidence verification; never import a provider or rewrite evidence."""

import hashlib
import importlib.util
import json
import subprocess
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
BASELINE = "88e70b0f682f00c0383e76cfe5e623a8961d9112"
PLAN = "dd1937807d1eedf800bcb8b9ad5a7d7851f6af05789c5c459d596366dda79ef7"
DIRECTORY = ROOT / "research/phase-14.2/generation-1"


def sha(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def canonical(value: Any) -> bytes:
    return json.dumps(
        value, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
    ).encode()


def verify() -> dict[str, Any]:
    # Reuse the exact sealed verifier and its path-scoped Phase14.0A reconciliation.
    path = ROOT / "scripts/research/verify_phase141.py"
    spec = importlib.util.spec_from_file_location("phase141_preservation", path)
    if spec is None or spec.loader is None:
        raise ValueError("PARENT_VERIFIER_UNAVAILABLE")
    parent = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(parent)
    parent.__dict__["BASELINE"] = BASELINE
    previous = parent.verify()
    if sha((ROOT / "docs/operations/phase-14.2-plan.md").read_bytes()) != PLAN:
        raise ValueError("FROZEN_PLAN_CHANGED")
    inv = json.loads((ROOT / "research/phase-14.1/generation-1/git-inventory.json").read_bytes())
    for name in inv["new_files"]:
        blob = subprocess.check_output(["git", "cat-file", "blob", f"{BASELINE}:{name}"], cwd=ROOT)
        if blob != (ROOT / name).read_bytes():
            raise ValueError(f"PARENT_COMMITTED_BYTES_CHANGED:{name}")
    ledger_path = DIRECTORY / "resolution-ledger.json"
    if ledger_path.exists():
        old = json.loads((ROOT / "research/phase-14.1/generation-1/resolution-ledger.json").read_bytes())
        new = json.loads(ledger_path.read_bytes())
        # Both phases actually have no supplied master. Preserve every field/ordinal/symbol.
        if new != old or len(new) != 1115:
            raise ValueError("ACTUAL_UNAVAILABLE_LEDGER_CHANGED")
    quality_path = DIRECTORY / "candle-quality.json"
    if quality_path.exists():
        quality = json.loads(quality_path.read_bytes())
        if quality["actual"] != {
            "status": "NOT_EVALUATED", "reason": "NO_CANDLES_SUPPLIED", "synthetic": False,
            "received": 0, "accepted": 0, "content_hash": None, "performance": None,
        }:
            raise ValueError("UNSUPPORTED_ACTUAL_DATA_CLAIM")
        for case, result in quality["synthetic_examples"].items():
            if result["synthetic"] is not True:
                raise ValueError("SYNTHETIC_LABEL_MISSING")
            expected = "OFFLINE_CHECKS_PASSED" if case == "valid" else "QUARANTINED"
            if result["status"] != expected or case != "valid" and result["accepted"] != 0:
                raise ValueError("QUALITY_REPORT_MISMATCH")
    entries = 0
    manifest_path = DIRECTORY / "evidence-manifest.json"
    fingerprint = None
    if manifest_path.exists():
        envelope = json.loads(manifest_path.read_bytes())
        manifest = envelope["manifest"]
        fingerprint = sha(canonical(manifest))
        if fingerprint != envelope["fingerprint"]:
            raise ValueError("ENVELOPE_MISMATCH")
        for group in ("baseline_raw_sha256", "artifacts_raw_sha256"):
            for name, expected in manifest[group].items():
                if sha((ROOT / name).read_bytes()) != expected:
                    raise ValueError(f"HASH_MISMATCH:{name}")
                entries += 1
    return {
        "result": "PASS", "baseline": BASELINE, "parent_preservation": previous,
        "phase141_committed_paths": len(inv["new_files"]), "plan_raw_sha256": PLAN,
        "phase142_hash_entries": entries, "phase142_fingerprint": fingerprint,
        "real_kite_requests_by_verifier": 0,
    }


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True, indent=2))
