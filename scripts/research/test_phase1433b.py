"""Synthetic decision checks; never acquire artifacts or mutate real custody."""

import copy
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import pytest
import verify_phase1433b as policy
from verify_phase1433b import RETRY, digest, validate

ROOT = Path(__file__).resolve().parents[2]
NOW = datetime(2026, 10, 17, 12, tzinfo=timezone.utc)


def fixture() -> tuple[dict[str, Any], dict[str, Any]]:
    packet = json.loads((ROOT / "research/phase-14.33A/generation-1/retry-authorization.json")
                        .read_bytes())
    decision = {
        "version": "Phase1433BRetryOwnerDecision.v1", "decision": "APPROVED",
        "retry_packet_fingerprint": RETRY, "scope": copy.deepcopy(packet["manifest"]),
        "owner_identity": "SYNTHETIC_TEST_ONLY", "owner_evidence_sha256": "a" * 64,
        "issued_at": "2026-10-17T11:00:00+00:00",
        "expires_at": "2026-10-17T13:00:00+00:00",
        "installation_authorization": "NOT_AUTHORIZED",
    }
    return packet, decision


def test_synthetic_valid_decision_only() -> None:
    packet, decision = fixture()
    validate(packet, decision, {digest(decision)}, NOW)


def test_absent_approval() -> None:
    packet, _ = fixture()
    with pytest.raises(ValueError, match="OWNER_APPROVAL_ABSENT"):
        validate(packet, None, set(), NOW)


def test_untrusted_approval() -> None:
    packet, decision = fixture()
    with pytest.raises(ValueError, match="OWNER_EVIDENCE_UNTRUSTED"):
        validate(packet, decision, set(), NOW)


@pytest.mark.parametrize("field,value", [
    ("version", "unknown"), ("decision", "PENDING"),
    ("retry_packet_fingerprint", "0" * 64), ("owner_identity", ""),
    ("owner_evidence_sha256", "invalid"), ("installation_authorization", "APPROVED"),
    ("expires_at", "2026-10-17T12:00:00+00:00"),
    ("issued_at", "2026-10-17T12:30:00+00:00"),
    ("expires_at", "2026-10-19T13:00:00+00:00"),
    ("issued_at", "2026-10-17T11:00:00"),
])
def test_wrong_decision(field: str, value: str) -> None:
    packet, decision = fixture()
    decision[field] = value
    with pytest.raises(ValueError):
        validate(packet, decision, {digest(decision)}, NOW)


@pytest.mark.parametrize("field", [
    "quarantine_destination", "receipt_custody_destination", "acl_procedure_sha256",
    "artifacts", "source_hosts", "max_total_bytes", "max_requests", "redirects",
    "retries", "request_timeout_seconds", "attestation_fingerprint",
])
def test_scope_mismatch(field: str) -> None:
    packet, decision = fixture()
    decision["scope"][field] = None
    with pytest.raises(ValueError, match="SCOPE_MISMATCH"):
        validate(packet, decision, {digest(decision)}, NOW)


def test_altered_packet() -> None:
    packet, decision = fixture()
    packet["manifest"]["max_requests"] = 9
    with pytest.raises(ValueError, match="PACKET_CHANGED"):
        validate(packet, decision, {digest(decision)}, NOW)


def test_deterministic_decision() -> None:
    _, decision = fixture()
    assert digest(decision) == digest(json.loads(json.dumps(decision)))


def synthetic_custody(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> dict[str, Any]:
    """Replace pins only inside synthetic tests; never create approved real paths."""
    import hashlib

    packet, decision = fixture()
    body = packet["manifest"]
    q, receipts = tmp_path / "synthetic", tmp_path / "receipts"
    q.mkdir()
    receipts.mkdir()
    body["quarantine_destination"] = str(q)
    body["receipt_custody_destination"] = str(receipts)
    for index, artifact in enumerate(body["artifacts"]):
        raw = f"SYNTHETIC_NOT_A_WHEEL_{index}".encode()
        (q / artifact["filename"]).write_bytes(raw)
        artifact["expected_sha256"] = hashlib.sha256(raw).hexdigest()
        artifact["expected_bytes"] = len(raw)
    packet["fingerprint"] = digest(body)
    monkeypatch.setattr(policy, "RETRY", packet["fingerprint"])
    decision["scope"] = copy.deepcopy(body)
    decision["retry_packet_fingerprint"] = packet["fingerprint"]
    rows = [{
        "version": "Phase1433BArtifactReceipt.v1", "filename": a["filename"],
        "retry_packet_fingerprint": packet["fingerprint"],
        "decision_fingerprint": digest(decision), "source_url": a["source_url"],
        "ordinal": index, "redirects": 0, "retries": 0, "status": 200,
        "complete": True, "content_encoding": "identity",
        "started_at": "2026-10-17T11:10:00+00:00",
        "finished_at": "2026-10-17T11:10:01+00:00", "elapsed_seconds": 1,
        "raw_sha256": a["expected_sha256"], "bytes": a["expected_bytes"],
    } for index, a in enumerate(body["artifacts"], 1)]
    acl = {
        "retry_packet_fingerprint": packet["fingerprint"],
        "decision_fingerprint": digest(decision),
        "procedure_sha256": body["acl_procedure_sha256"],
        "quarantine_destination": str(q), "receipt_custody_destination": str(receipts),
        "owner_group_unchanged": True, "protected": True,
        "exact_approved_rules": True, "no_sacl_write": True,
    }
    return {"packet": packet, "decision": decision, "rows": rows, "acl": acl}


def certify_fixture(f: dict[str, Any]) -> dict[str, Any]:
    import hashlib

    raw = [json.dumps(row, sort_keys=True).encode() for row in f["rows"]]
    return policy.certify(
        f["packet"], f["decision"], {digest(f["decision"])}, NOW,
        raw, {hashlib.sha256(b).hexdigest() for b in raw}, f["acl"], {digest(f["acl"])},
    )


def test_synthetic_certification_replay(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    f = synthetic_custody(tmp_path, monkeypatch)
    first = certify_fixture(f)
    f["rows"].reverse()
    assert first == certify_fixture(f)
    assert first["installation_authorization"] == "NOT_AUTHORIZED"


@pytest.mark.parametrize("field,value", [
    ("source_url", "https://unapproved.invalid/a"), ("redirects", 1),
    ("retries", 1), ("elapsed_seconds", 21), ("complete", False),
    ("bytes", 9999), ("raw_sha256", "0" * 64), ("ordinal", 9),
    ("decision_fingerprint", "0" * 64), ("version", "unknown"),
])
def test_retry_receipt_rejection(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, field: str, value: Any,
) -> None:
    f = synthetic_custody(tmp_path, monkeypatch)
    f["rows"][0][field] = value
    with pytest.raises(ValueError):
        certify_fixture(f)


@pytest.mark.parametrize("change", ["missing_receipt", "duplicate", "partial", "extra", "acl"])
def test_retry_custody_rejection(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, change: str,
) -> None:
    f = synthetic_custody(tmp_path, monkeypatch)
    if change == "missing_receipt":
        f["rows"].pop()
    elif change == "duplicate":
        f["rows"][1] = f["rows"][0]
    elif change == "acl":
        f["acl"]["protected"] = False
    else:
        directory = Path(f["packet"]["manifest"]["quarantine_destination"])
        path = directory / ("unexpected.part" if change == "extra" else f["rows"][0]["filename"])
        path.write_bytes(b"partial")
    with pytest.raises(ValueError):
        certify_fixture(f)
