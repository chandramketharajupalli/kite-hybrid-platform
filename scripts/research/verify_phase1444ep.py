"""Pure pending-contract checks and non-launching preparation verification."""

import hashlib
import json
from pathlib import Path
from typing import Any

ROOT = Path("D:/Yogendra/kite-hybrid-platform")
CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-P/generation-1")
BASELINE = "4e542529bacb39d34ebcd439d338e08c60c6ac0e"


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


def check_pending(owner: dict[str, Any], packet: dict[str, Any], families: dict[str, Any]) -> None:
    assert owner["baseline"] == packet["baseline"] == BASELINE
    assert owner["status"] == "PENDING_OWNER_DECISION"
    for value in owner["decision"].values():
        assert value is None
    assert owner["technical_recommendation"] == "DEFER_PENDING_APPROVED_PROVIDER"
    assert packet["status"] == "NOT_AUTHORIZED"
    assert packet["scope"]["status"] == "UNAPPROVED_SCOPE"
    assert all(value is None for value in packet["scope"]["exact"].values())
    assert packet["scope_fingerprint"] == digest(canonical(packet["scope"]))
    assert all(value is None for value in packet["real_authorization"].values())
    assert packet["family_approvals"] == {"A": "NOT_AUTHORIZED", "B": "NOT_AUTHORIZED", "C": "NOT_AUTHORIZED"}
    for family, count in (("A", 46), ("B", 18), ("C", 31)):
        assert packet["families"][family]["test_ids"] == families[family]
        assert len(families[family]) == len(set(families[family])) == count
        assert packet["families"][family]["execution"] == "NOT_EXECUTED"
    budget = packet["budget"]
    assert budget["status"] == "PROPOSED_NOT_APPROVED"
    for field in ("maximum_cost", "currency", "billing_period", "cleanup_authority", "deadline"):
        assert budget[field] is None
    ceilings = {"vcpu": 2, "memory_bytes": 4294967296, "storage_bytes": 34359738368,
                "output_bytes": 67108864, "execution_seconds": 300, "guests": 1,
                "retries": 0, "cleanup_within_seconds": 3600}
    assert set(budget["proposed"]) == set(ceilings)
    for key, ceiling in ceilings.items():
        value = budget["proposed"][key]
        assert type(value) is int
        assert (0 if key == "retries" else 1) <= value <= ceiling
    assert all(value == "UNVERIFIED" for value in packet["containment_status"].values())
    assert not any(packet["activity"].values())


def execution_authorized(_packet: dict[str, Any]) -> bool:
    return False


def verify() -> dict[str, object]:
    assert (ROOT / ".git" / "HEAD").read_text().strip() == "ref: refs/heads/develop"
    assert read_ref("HEAD") == read_ref("refs/remotes/origin/develop") == BASELINE
    inputs = json.loads((CUSTODY / "verification-inputs.json").read_bytes())
    for name, expected in inputs["hashes"].items():
        path = Path(name)
        for parent in (path, *path.parents):
            assert not parent.lstat().st_file_attributes & 0x400
        assert digest(path.read_bytes()) == expected, name
    assert len(inputs["historical_missing"]) == 73
    assert all(not Path(name).exists() for name in inputs["historical_missing"])
    owner = json.loads((ROOT / "research/phase-14.44ep/generation-1/owner-selection.json").read_bytes())
    packet = json.loads((ROOT / "research/phase-14.44ep/generation-1/provisioning-approval.json").read_bytes())
    families = json.loads((CUSTODY / "separate-family-scopes.json").read_bytes())
    check_pending(owner, packet, families)
    assert not execution_authorized(packet)
    receipt = json.loads((CUSTODY / "preparation-validation.json").read_bytes())
    assert receipt["status"] == receipt["schema"] == receipt["syntax"] == "PASS"
    return {"contract": "Phase1444EPPreparationReplay.v1", "baseline": BASELINE,
            "classification": "PHASE_14_44EP_OWNER_SELECTION_PACKET_READY",
            "inputs_fingerprint": digest(canonical(inputs)), "baseline_count": 1842,
            "owner_selection": "PENDING_OWNER_DECISION", "provisioning": "NOT_AUTHORIZED",
            "provider_decision": "DEFER_PENDING_APPROVED_PROVIDER", "historical_missing": 73,
            "original46": "NOT_EXECUTED", "original18": "NOT_EXECUTED",
            "original31": "NOT_EXECUTED", "real_kite_requests": 0}
