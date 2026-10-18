"""Read-only preference verification; no execution or approval capability."""

import hashlib
import json
from pathlib import Path
from typing import Any

ROOT = Path("D:/Yogendra/kite-hybrid-platform")
CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-Q/generation-1")
BASELINE = "b912ba3345ef02b297c9248f2cf727996d048b07"


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


def check_pending(preference: dict[str, Any], approval: dict[str, Any]) -> None:
    assert preference["contract"] == "Phase1444EQOwnerInfrastructurePreference.v1"
    assert preference["baseline"] == approval["baseline"] == BASELINE
    assert preference["submitted_choices"] == {"infrastructure_option": "PENDING", "workload_scope": "NONE"}
    assert preference["effective_owner_selected_provider"] is None
    assert preference["effective_owner_selected_workload_families"] is None
    expected = {
        "owner_selection_status": "PENDING_OWNER_SELECTION",
        "authentication_status": "NOT_ESTABLISHED",
        "technical_recommendation": "DEFER_PENDING_APPROVED_PROVIDER",
        "provider_selection_scope": "SCOPE_NOT_APPROVED",
        "provisioning_authorization": "NOT_AUTHORIZED",
        "execution_authorization": "NOT_AUTHORIZED",
    }
    for key, value in expected.items():
        assert preference[key] == value
    assert preference["explicit_instruction"] == "No infrastructure or workload family has been selected."
    assert preference["provenance_kind"] == "USER_SUBMITTED_REVIEW_CHOICES_ONLY"
    assert all(value is None for value in preference["unknown_authority_fields"].values())
    assert approval["status"] == "NOT_AUTHORIZED"
    assert approval["scope_status"] == "SCOPE_NOT_APPROVED"
    assert all(value is None for value in approval["effective_authority"].values())
    assert approval["preference_fingerprint"] == digest(canonical(preference))
    for key, count in (("A", 46), ("B", 18), ("C", 31)):
        family = approval["families"][key]
        assert len(family["test_ids"]) == len(set(family["test_ids"])) == count
        assert family["approval"] == "NOT_AUTHORIZED" and family["execution"] == "NOT_EXECUTED"
    assert all(value == "UNVERIFIED" for value in approval["containment"].values())
    assert not any(approval["activity"].values())


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
    preference = json.loads((ROOT / "research/phase-14.44eq/generation-1/owner-preference.json").read_bytes())
    approval = json.loads((ROOT / "research/phase-14.44eq/generation-1/provider-approval.json").read_bytes())
    check_pending(preference, approval)
    original = json.loads((ROOT / "research/phase-14.44ep/generation-1/provisioning-approval.json").read_bytes())
    for key in ("A", "B", "C"):
        assert approval["families"][key]["test_ids"] == original["families"][key]["test_ids"]
        assert approval["families"][key]["source_hashes"] == original["families"][key]["source_hashes"]
    assert not execution_authorized(approval)
    validation = json.loads((CUSTODY / "preparation-validation.json").read_bytes())
    assert validation["status"] == validation["schema"] == validation["syntax"] == "PASS"
    return {"contract": "Phase1444EQPreparationReplay.v1", "baseline": BASELINE,
            "classification": "PHASE_14_44EQ_PREFERENCE_SCOPE_READY",
            "inputs_fingerprint": digest(canonical(inputs)), "baseline_count": 1854,
            "submitted_choices": preference["submitted_choices"],
            "effective_provider": None, "effective_families": None,
            "owner_selection": "PENDING_OWNER_SELECTION", "authentication": "NOT_ESTABLISHED",
            "provisioning": "NOT_AUTHORIZED", "execution": "NOT_AUTHORIZED",
            "original46": "NOT_EXECUTED", "original18": "NOT_EXECUTED",
            "original31": "NOT_EXECUTED", "historical_missing": 73, "real_kite_requests": 0}
