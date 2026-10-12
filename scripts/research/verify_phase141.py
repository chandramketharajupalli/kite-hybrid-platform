"""Read-only, path-scoped Phase14/14.0A/14.1 preservation and ledger verification."""

import csv
import hashlib
import importlib.util
import json
import subprocess
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
BASELINE = "712db352a3e74440e7625f1af5772cc5755908aa"
PLAN = "0203c612aec6750c1a80932f655587576000a88bb10ab0d877310809ad1b0b3f"
RESEARCH = ROOT / "research/phase-14.1/generation-1"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical(value: Any) -> bytes:
    return json.dumps(
        value, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
    ).encode()


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", *args], cwd=ROOT)


def envelope(path: str, key: str) -> dict[str, Any]:
    value = json.loads((ROOT / path).read_bytes())
    if sha(canonical(value[key])) != value["fingerprint"]:
        raise ValueError(f"ENVELOPE_MISMATCH:{path}")
    return dict(value[key])


def universe() -> dict[str, Any]:
    def rows(path: str) -> list[dict[str, str]]:
        with (ROOT / path).open(encoding="utf-8-sig", newline="") as stream:
            return list(csv.DictReader(stream))

    source = rows("input/phase14/universe(1).csv")
    unique = []
    duplicates = []
    seen = set()
    for line, row in enumerate(source, 2):
        if row["exchange"] != "NSE" or row["enabled"].strip().casefold() != "true":
            raise ValueError("INVALID_UNIVERSE_ROW")
        key = (row["exchange"].strip().casefold(), row["symbol"].strip().casefold())
        if key in seen:
            duplicates.append({"source_row": str(line), "symbol": row["symbol"]})
        else:
            seen.add(key)
            unique.append(row)
    if (
        unique != rows("input/phase14/phase14_unique_universe.csv")
        or unique != rows("config/universes/phase14_unique_universe.csv")
        or duplicates != rows("input/phase14/phase14_removed_duplicates.csv")
        or (len(source), len(duplicates), len(unique)) != (1140, 25, 1115)
    ):
        raise ValueError("UNIVERSE_RECONCILIATION_FAILED")
    ledger_path = RESEARCH / "resolution-ledger.json"
    if ledger_path.exists():
        ledger = json.loads(ledger_path.read_bytes())
        if len(ledger) != len(unique):
            raise ValueError("LEDGER_COUNT_MISMATCH")
        for i, (entry, row) in enumerate(zip(ledger, unique, strict=True), 1):
            if (
                entry["source_record"] != i
                or entry["symbol"] != row["symbol"]
                or entry["exchange"] != "NSE"
                or entry["status"] != "MASTER_UNAVAILABLE"
                or entry["instrument_token"] is not None
                or entry["instrument_id"] is not None
                or entry["master_provenance"] is not None
                or entry["candidates"] != []
            ):
                raise ValueError("UNSUPPORTED_ACTUAL_RESOLUTION_CLAIM")
    return {"source_rows": 1140, "duplicates": 25, "unique": 1115}


def verify() -> dict[str, Any]:
    if git("branch", "--show-current").decode().strip() != "develop":
        raise ValueError("BRANCH_CHANGED")
    for ref in ("HEAD", "origin/develop"):
        if git("rev-parse", ref).decode().strip() != BASELINE:
            raise ValueError("BASELINE_CHANGED")
    if git("diff", "--cached", "--name-only"):
        raise ValueError("UNEXPECTED_STAGING")
    if sha((ROOT / "docs/operations/phase-14.1-plan.md").read_bytes()) != PLAN:
        raise ValueError("PLAN_CHANGED")
    old_path = "research/phase-14.0/generation-1/evidence-manifest.json"
    old = envelope(old_path, "manifest")
    rec = envelope("research/phase-14.0a/generation-1/reconciliation.json", "reconciliation")
    if sha((ROOT / old_path).read_bytes()) != rec["original_manifest"]["raw_sha256"]:
        raise ValueError("ORIGINAL_MANIFEST_CHANGED")
    accepted = {}
    if len(rec["files"]) != 13:
        raise ValueError("RECONCILIATION_SCOPE_CHANGED")
    for entry in rec["files"]:
        path = entry["path"]
        work = (ROOT / path).read_bytes()
        blob = git("cat-file", "blob", f"{BASELINE}:{path}")
        if (
            entry["original_manifest_sha256"] != old["after_sha256"][path]
            or sha(blob) != entry["committed_blob_sha256"]
            or sha(blob) != entry["normalized_content_sha256"]
            or work.replace(b"\r\n", b"\n") != blob
            or sha(work) not in (entry["original_manifest_sha256"], sha(blob))
        ):
            raise ValueError(f"NON_NEWLINE_CHANGE:{path}")
        accepted[path] = sha(blob)
    for path, expected in old["after_sha256"].items():
        actual = sha((ROOT / path).read_bytes())
        if actual != expected and actual != accepted.get(path):
            raise ValueError(f"ORIGINAL_ARTIFACT_CHANGED:{path}")
    # Reuse the unchanged historical function; only its obsolete Git ref is rebound in memory.
    verifier = ROOT / "scripts/research/verify_phase140.py"
    spec = importlib.util.spec_from_file_location("phase140_preservation", verifier)
    if spec is None or spec.loader is None:
        raise ValueError("PRESERVATION_VERIFIER_UNAVAILABLE")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.__dict__["BASELINE"] = BASELINE
    protected = module.verify()
    current_path = RESEARCH / "evidence-manifest.json"
    checked = 0
    if current_path.exists():
        current = envelope(str(current_path.relative_to(ROOT)), "manifest")
        for group in ("baseline_raw_sha256", "artifacts_raw_sha256"):
            for path, expected in current[group].items():
                if sha((ROOT / path).read_bytes()) != expected:
                    raise ValueError(f"PHASE141_HASH_MISMATCH:{path}")
                checked += 1
    return {
        "result": "PASS", "baseline": BASELINE, "plan_sha256": PLAN,
        "phase14_artifacts": len(old["after_sha256"]), "reconciled_paths": len(accepted),
        "protected": protected["checked"], "universe": universe(),
        "phase141_hash_entries": checked, "real_kite_requests_by_verifier": 0,
    }


if __name__ == "__main__":
    print(json.dumps(verify(), indent=2, sort_keys=True))
