"""Read-only preparation verification. Does not launch processes or run tests."""

import hashlib
import json
from pathlib import Path
from typing import Any

ROOT = Path("D:/Yogendra/kite-hybrid-platform")
CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-M/generation-1")
BASELINE = "265fe3aa48aa1d9c8f4bbe1a1579a0d0aebbf9c5"


def canonical(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def read_ref(name: str) -> str:
    path = ROOT / ".git" / name
    if path.exists():
        value = path.read_text(encoding="ascii").strip()
        if value.startswith("ref: "):
            return read_ref(value[5:])
        return value
    for line in (ROOT / ".git" / "packed-refs").read_text(encoding="ascii").splitlines():
        if line and not line.startswith(("#", "^")):
            value, ref = line.split(" ", 1)
            if ref == name:
                return value
    raise ValueError("MISSING_REF")


def verify() -> dict[str, object]:
    assert (ROOT / ".git" / "HEAD").read_text().strip() == "ref: refs/heads/develop"
    assert read_ref("HEAD") == read_ref("refs/remotes/origin/develop") == BASELINE
    inputs = json.loads((CUSTODY / "verification-inputs.json").read_bytes())
    for name, expected in inputs["hashes"].items():
        path = Path(name)
        for parent in (path, *path.parents):
            assert not parent.lstat().st_file_attributes & 0x400, str(parent)
        assert digest(path.read_bytes()) == expected, name
    for name in inputs["historical_missing"]:
        assert not Path(name).exists(), name
    assert len(inputs["historical_missing"]) == 73
    inventory = json.loads((CUSTODY / "expected-inventory.json").read_bytes())
    assert len(inventory["nodes"]) == len(set(inventory["nodes"])) == 46
    for name, expected in inventory["source_hashes"].items():
        assert digest((ROOT / name).read_bytes()) == expected
    approval = json.loads(
        (ROOT / "research/phase-14.44em/generation-1/pytest-approval.json").read_bytes()
    )
    assert approval["test_inventory"] == inventory["nodes"]
    assert approval["status"] == "NOT_AUTHORIZED"
    for field in ("owner_identity", "authentication_evidence", "approval_timestamp", "expiry",
                  "runner_fingerprint", "cleanup_authority", "revocation", "replay_controls"):
        assert approval[field] is None
    policy = json.loads((CUSTODY / "pure-policy.json").read_bytes())
    assert policy["passed"] == 32 and policy["failed"] == 0
    assert not policy["subprocess_created"] and policy["original46_executed"] == 0
    assert json.loads((CUSTODY / "schema-validation.json").read_bytes())["status"] == "PASS"
    return {
        "contract": "Phase1444EMPreparationReplay.v1",
        "baseline": BASELINE,
        "inputs_fingerprint": digest(canonical(inputs)),
        "classification": "PHASE_14_44EM_PARTIAL",
        "baseline_files": 1806,
        "policy_cases_passed": 32,
        "schema": "PASS",
        "original46": "NOT_EXECUTED",
        "original18": "NOT_EXECUTED",
        "original31": "NOT_EXECUTED",
        "historical_missing": 73,
        "ruff": "NOT_EXECUTED",
        "strict_mypy": "NOT_EXECUTED",
        "native_activation": False,
        "real_kite_requests": 0,
        "execution_authorization": "NOT_AUTHORIZED",
        "containment": "UNVERIFIED",
    }
