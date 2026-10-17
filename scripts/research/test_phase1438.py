"""Synthetic custody fixtures only; no real correspondence or external services."""

import hashlib
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

import pytest
import verify_phase1438 as v
from verify_phase1435 import digest, readiness
from verify_phase1437 import lifecycle


def message(tmp_path: Path) -> tuple[Path, str]:
    p = tmp_path / "synthetic.eml"
    p.write_bytes(b"From: fixture@example.invalid\r\nTo: owner@example.invalid\r\n"
                  b"Date: Fri, 2 Jan 2026 12:00:00 +0530\r\nMessage-ID: <fixture>\r\n"
                  b"\r\nSynthetic body only.\r\n")
    return p, hashlib.sha256(p.read_bytes()).hexdigest()


def test_original_integrity_not_authenticity(tmp_path: Path) -> None:
    p, sha = message(tmp_path)
    before = p.read_bytes()
    result = v.inspect_original(p, {p}, sha, set())
    assert p.read_bytes() == before and result["raw_sha256"] == sha
    assert result["sender_authenticity"] == "UNVERIFIED"
    assert "X-Ticket-ID" in result["missing_headers"]


@pytest.mark.parametrize("fault", ["bytes", "duplicate", "path", "sender"])
def test_custody_rejections(tmp_path: Path, fault: str) -> None:
    p, sha = message(tmp_path)
    if fault == "bytes":
        p.write_bytes(p.read_bytes() + b"changed")
    with pytest.raises(ValueError):
        v.inspect_original(p, set() if fault == "path" else {p}, sha,
                           {sha} if fault == "duplicate" else set(),
                           "different@example.invalid" if fault == "sender" else None)


def test_missing_headers(tmp_path: Path) -> None:
    p = tmp_path / "missing.eml"
    p.write_bytes(b"\r\nSynthetic body without headers")
    result = v.inspect_original(p, {p}, hashlib.sha256(p.read_bytes()).hexdigest(), set())
    assert set(result["missing_headers"]) == {"From", "To", "Date", "Message-ID", "X-Ticket-ID"}


def test_ambiguous_sender(tmp_path: Path) -> None:
    p, _ = message(tmp_path)
    p.write_bytes(b"From: second@example.invalid\r\n" + p.read_bytes())
    with pytest.raises(ValueError, match="AMBIGUOUS_HEADERS"):
        v.inspect_original(p, {p}, hashlib.sha256(p.read_bytes()).hexdigest(), set())


def test_missing_original_and_copy() -> None:
    result = v.compare_answers(None, None)
    assert len(result) == 14 and all(x["comparison"] == "UNVERIFIED" for x in result)


def test_exact_differences_are_not_normalized() -> None:
    original = {n: f"synthetic {n}\r\n" for n in range(1, 15)}
    supplied = dict(original)
    supplied[4] = supplied[4].replace("\r\n", "\n")
    result = v.compare_answers(original, supplied)
    assert result[3]["comparison"] == "DIFFERENT"
    assert sum(x["comparison"] == "EXACT_MATCH" for x in result) == 13


@pytest.mark.parametrize("size", [0, 13, 15])
def test_incomplete_response(size: int) -> None:
    with pytest.raises(ValueError):
        v.compare_answers({n: "synthetic" for n in range(1, size + 1)}, None)


def retention() -> dict[str, Any]:
    return {"version": "Phase1438RetentionDecision.v1", "source_authenticity": "ORIGINAL_CORRESPONDENCE_VERIFIED",
            "permission": "EXPLICITLY_PERMITTED", "conflicts": [], "duration_seconds": 60,
            "exact_source": "synthetic", "purpose": "fixture", "location": "fixture",
            "deletion_trigger": "expiry", "backup_treatment": "none", "derived_output_handling": "separate"}


@pytest.mark.parametrize("field,value", [("duration_seconds", None), ("duration_seconds", 0),
    ("conflicts", ["permanent copies restricted"]), ("backup_treatment", None),
    ("derived_output_handling", None), ("permission", "RESTRICTED"),
    ("source_authenticity", "USER_SUPPLIED_RESPONSE_TEXT")])
def test_retention_unresolved(field: str, value: Any) -> None:
    record = retention()
    record[field] = value
    with pytest.raises(ValueError):
        v.retention_ready(record, digest(record))


def test_expired_temporary_record() -> None:
    p: dict[str, Any] = {k: "synthetic" for k in ("source", "purpose", "location", "deletion_trigger",
        "deletion_authority", "backup_handling", "recovery_handling", "audit_policy",
        "policy_evidence", "authority_fingerprint")}
    p.update(version="Phase1437DataRetentionPolicy.v1", status="AUTHORIZED",
             maximum_lifetime_seconds=1, allowed_operations=["process"])
    now = datetime(2026, 1, 2, tzinfo=UTC)
    with pytest.raises(ValueError, match="EXPIRED"):
        lifecycle(p, digest(p), now - timedelta(seconds=1), now, "process")


@pytest.mark.parametrize("fault", ["fingerprint", "missing", "wrong_scope", "untrusted"])
def test_invalid_acquisition(fault: str) -> None:
    p = {"version": "Phase1438InstrumentMasterApproval.v1", "status": "NOT_AUTHORIZED",
         "execution_enabled": False}
    decision = {"kind": "master", "packet": digest(p), "status": "APPROVED"}
    with pytest.raises(ValueError):
        v.approval(p, "wrong" if fault == "fingerprint" else digest(p),
                   "candles" if fault == "wrong_scope" else "master",
                   None if fault == "missing" else decision, set())


def test_no_network_no_performance() -> None:
    import ast
    import inspect

    tree = ast.parse(inspect.getsource(v))
    assert {n.module for n in ast.walk(tree) if isinstance(n, ast.ImportFrom)} <= {
        "email", "email.parser", "pathlib", "typing", "verify_phase1435"}
    assert {a.name for n in ast.walk(tree) if isinstance(n, ast.Import) for a in n.names} == {"hashlib", "stat"}
    assert readiness()["genuine_performance"] is None and readiness()["real_kite_requests"] == 0
