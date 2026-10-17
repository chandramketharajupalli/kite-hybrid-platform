"""Synthetic evidence only; no messages, artifacts or protected files are mutated."""

import hashlib
from datetime import UTC, datetime, timedelta
from typing import Any

import pytest
import verify_phase1437 as v
from verify_phase1435 import digest, readiness
from verify_phase1436 import permission

NOW = datetime(2026, 1, 2, tzinfo=UTC)


def source() -> tuple[bytes, dict[str, Any], set[str]]:
    raw = b"synthetic message"
    metadata = {"sender": "fixture", "recipient": "fixture", "sent_at": NOW.isoformat(),
                "observation_receipt": "synthetic", "message_id": "fixture", "ticket_reference": None}
    review = {"raw_sha256": hashlib.sha256(raw).hexdigest(), "body_sha256": digest("synthetic"),
              "metadata_sha256": digest(metadata), "authenticity": "VERIFIED"}
    return raw, {"version": "Phase1437Custody.v1", "user_supplied": True,
                 "review": review, "exact_body": "synthetic", "metadata": metadata,
                 "read_only_original": True, "access_policy": "fixture"}, {digest(review)}


def test_missing_original_preserves_user_text_classification() -> None:
    _, record, _ = source()
    result = v.custody(None, record, set())
    assert result["classification"] == "USER_SUPPLIED_RESPONSE_TEXT"
    assert result["original"] == "ORIGINAL_CUSTODY_UNVERIFIED"


def test_verified_fixture() -> None:
    raw, record, trusted = source()
    assert v.custody(raw, record, trusted)["classification"] == "ORIGINAL_CORRESPONDENCE_VERIFIED"


@pytest.mark.parametrize("fault", ["raw", "body", "sender", "review", "read_only"])
def test_custody_failures(fault: str) -> None:
    raw, record, trusted = source()
    if fault == "raw":
        raw += b"changed"
    elif fault == "body":
        record["exact_body"] = "changed"
    elif fault == "sender":
        record["metadata"]["sender"] = "changed"
    elif fault == "review":
        trusted = set()
    else:
        record["read_only_original"] = False
    with pytest.raises(ValueError):
        v.custody(raw, record, trusted)


def test_missing_message_id_is_explicit_gap() -> None:
    raw, record, _ = source()
    record["metadata"]["message_id"] = None
    record["review"]["metadata_sha256"] = digest(record["metadata"])
    assert "message_id" in v.custody(raw, record, {digest(record["review"])})["missing"]


def test_conflict_and_no_contract_override() -> None:
    assert permission("ORIGINAL_CORRESPONDENCE_VERIFIED", ["PERMIT", "RESTRICT"], True) == "RESTRICTED"
    assert permission("USER_SUPPLIED_RESPONSE_TEXT", ["PERMIT"], True) == "UNVERIFIED"


def policy() -> dict[str, Any]:
    p: dict[str, Any] = {k: "synthetic" for k in ("source", "purpose", "location", "deletion_trigger",
        "deletion_authority", "backup_handling", "recovery_handling", "audit_policy",
        "policy_evidence", "authority_fingerprint")}
    p.update(version="Phase1437DataRetentionPolicy.v1", status="AUTHORIZED",
             maximum_lifetime_seconds=60, allowed_operations=["process"])
    return p


@pytest.mark.parametrize("fault", ["duration", "authority", "permanent", "expiry", "backup",
                                    "recover_expired", "policy"])
def test_lifecycle_fail_closed(fault: str) -> None:
    p = policy()
    if fault == "duration":
        p["maximum_lifetime_seconds"] = None
    if fault == "authority":
        p["authority_fingerprint"] = None
    op = {"permanent": "persist", "backup": "backup", "recover_expired": "recover"}.get(fault, "process")
    if fault == "recover_expired":
        p["allowed_operations"].append("recover")
    with pytest.raises(ValueError):
        v.lifecycle(p, "wrong" if fault == "policy" else digest(p), NOW,
                    NOW + timedelta(seconds=60) if fault in ("expiry", "recover_expired") else NOW, op)


def receipt(p: dict[str, Any]) -> dict[str, Any]:
    return {"version": "Phase1437DeletionReceipt.v1", "id": "synthetic-1", "policy": digest(p),
            "object": "synthetic-object", "copy": "primary", "status": "COMPLETED",
            "verification_evidence": "fixture", "completed_at": NOW.isoformat()}


@pytest.mark.parametrize("fault", ["tamper", "duplicate", "interrupted", "backup_missing",
                                    "policy", "owner", "object"])
def test_deletion_receipts(fault: str) -> None:
    p = policy()
    if fault == "owner":
        p["status"] = "NOT_AUTHORIZED"
    row = receipt(p)
    if fault == "interrupted":
        row["status"] = "INTERRUPTED"
    if fault == "object":
        row["object"] = "wrong"
    trusted = {digest(row)}
    if fault == "tamper":
        row["verification_evidence"] = "modified"
    with pytest.raises(ValueError):
        v.deletion_receipts(p, "wrong" if fault == "policy" else digest(p), "synthetic-object",
                            {"primary", "backup"} if fault == "backup_missing" else {"primary"},
                            [row, row] if fault == "duplicate" else [row], trusted)


def test_complete_synthetic_receipt_and_valid_lifetime() -> None:
    p = policy()
    v.lifecycle(p, digest(p), NOW, NOW + timedelta(seconds=59), "process")
    row = receipt(p)
    v.deletion_receipts(p, digest(p), "synthetic-object", {"primary"}, [row], {digest(row)})


@pytest.mark.parametrize("fault", ["missing", "wrong_packet", "wrong_scope", "wrong_decision"])
def test_acquisition_separation(fault: str) -> None:
    p = {"version": "Phase1437InstrumentMasterApproval.v1"}
    decision = {"status": "APPROVED", "packet": digest(p), "operation": "candles"}
    with pytest.raises(ValueError):
        v.approval(p, "wrong" if fault == "wrong_packet" else digest(p),
                   "candles" if fault == "wrong_scope" else "master",
                   None if fault == "missing" else decision, {digest(decision)}, NOW)


def test_no_network_no_performance() -> None:
    import ast
    import inspect

    tree = ast.parse(inspect.getsource(v))
    assert {n.module for n in ast.walk(tree) if isinstance(n, ast.ImportFrom)} <= {
        "datetime", "typing", "verify_phase1435"}
    assert {a.name for n in ast.walk(tree) if isinstance(n, ast.Import) for a in n.names} == {"hashlib"}
    assert readiness()["genuine_performance"] is None
    assert readiness()["qualified_pairs"] == readiness()["real_kite_requests"] == 0
