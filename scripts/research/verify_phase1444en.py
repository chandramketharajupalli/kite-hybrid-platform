"""Read-only preparation verification and pure infrastructure-policy checks."""

import hashlib
import json
from pathlib import Path
from typing import Any

ROOT = Path("D:/Yogendra/kite-hybrid-platform")
CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-N/generation-1")
BASELINE = "9bd13a1c36e9696c22c86bbb2a28a0bddf9748ef"


def canonical(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def read_ref(name: str) -> str:
    path = ROOT / ".git" / name
    if path.exists():
        value = path.read_text(encoding="ascii").strip()
        return read_ref(value[5:]) if value.startswith("ref: ") else value
    for line in (ROOT / ".git" / "packed-refs").read_text(encoding="ascii").splitlines():
        if line and not line.startswith(("#", "^")):
            value, ref = line.split(" ", 1)
            if ref == name:
                return value
    raise ValueError("MISSING_REF")


def check_packet(packet: dict[str, Any], inventory: list[str]) -> None:
    assert packet["status"] == "NOT_AUTHORIZED"
    assert packet["baseline"] == BASELINE
    assert packet["selection"] == "DEFER_PENDING_APPROVED_INFRASTRUCTURE"
    assert packet["test_ids"] == inventory
    assert len(inventory) == len(set(inventory)) == 46
    for field in (
        "provider", "guest_image_fingerprint", "runtime_closure_fingerprint",
        "qualification_commands", "cleanup_authority", "owner_identity",
        "authentication_evidence", "approval_timestamp", "expiry", "revocation",
        "replay_controls",
    ):
        assert packet[field] is None
    for field in ("filesystem", "network", "credentials", "process", "resources"):
        assert packet["enforcement"][field] == "UNVERIFIED"
    assert packet["original46"] == packet["original18"] == packet["original31"] == "NOT_EXECUTED"
    assert not any(packet["activity"].values())


def execution_authorized(_packet: dict[str, Any]) -> bool:
    return False


def verify() -> dict[str, object]:
    assert (ROOT / ".git" / "HEAD").read_text().strip() == "ref: refs/heads/develop"
    assert read_ref("HEAD") == read_ref("refs/remotes/origin/develop") == BASELINE
    inputs = json.loads((CUSTODY / "verification-inputs.json").read_bytes())
    for name, expected in inputs["hashes"].items():
        path = Path(name)
        for ancestor in (path, *path.parents):
            assert not ancestor.lstat().st_file_attributes & 0x400, str(ancestor)
        assert digest(path.read_bytes()) == expected, name
    assert len(inputs["historical_missing"]) == 73
    assert all(not Path(name).exists() for name in inputs["historical_missing"])
    inventory = json.loads((CUSTODY / "expected-inventory.json").read_bytes())
    packet = json.loads(
        (ROOT / "research/phase-14.44en/generation-1/infrastructure-approval.json").read_bytes()
    )
    check_packet(packet, inventory["nodes"])
    assert not execution_authorized(packet)
    assessment = json.loads(
        (ROOT / "research/phase-14.44en/generation-1/infrastructure-assessment.json").read_bytes()
    )
    assert assessment["selection"]["selection_status"] == packet["selection"]
    assert len(assessment["candidates"]) == 5
    assert assessment["host"]["docker_backend"]["status"] == "UNVERIFIED"
    for contract in ("guest_image", "runtime"):
        assert assessment[contract]["status"] == "UNVERIFIED"
    receipts = json.loads((CUSTODY / "preparation-validation.json").read_bytes())
    assert receipts["status"] == "PASS"
    return {
        "contract": "Phase1444ENPreparationReplay.v1",
        "baseline": BASELINE,
        "inputs_fingerprint": digest(canonical(inputs)),
        "selection": packet["selection"],
        "classification": "PHASE_14_44EN_INFRASTRUCTURE_SELECTION_READY",
        "baseline_count": 1818,
        "historical_missing": 73,
        "newline_reconciliations": 13,
        "original46": "NOT_EXECUTED",
        "original18": "NOT_EXECUTED",
        "original31": "NOT_EXECUTED",
        "approval": "NOT_AUTHORIZED",
        "guest_provisioned": False,
        "remote_connected": False,
        "real_kite_requests": 0,
    }
