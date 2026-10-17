"""Synthetic evidence and approval tests, reusing frozen data validators."""

from datetime import UTC, datetime, timedelta
from typing import Any

import pytest
import verify_phase1436 as v
from verify_phase1435 import Candle, Mapping, completeness, digest, mappings, retention

NOW = datetime(2026, 1, 2, 4, tzinfo=UTC)


@pytest.mark.parametrize("text,summary,expected", [
    (None, None, "UNAVAILABLE"), (None, "reported topic", "SECONDARY_SUMMARY"),
    ("Exact synthetic text", None, "USER_SUPPLIED_RESPONSE_TEXT"),
])
def test_missing_original(text: str | None, summary: str | None, expected: str) -> None:
    assert v.classify({"version": "Phase1436Source.v1", "exact_text": text,
                       "summary": summary, "ticket": "unverified"}, set()) == expected


def test_review_binds_body_and_custody() -> None:
    review = {"raw_sha256": "fixture", "exact_text_sha256": digest("fixture"),
              "authenticity": "VERIFIED", "kind": "ORIGINAL_CORRESPONDENCE_VERIFIED"}
    source: dict[str, Any] = {"version": "Phase1436Source.v1", "exact_text": "fixture",
                              "raw_sha256": "fixture", "review": review, "sender": "fixture",
                              "recipient": "fixture", "sent_at": NOW.isoformat(),
                              "custody_reference": "synthetic"}
    assert v.classify(source, {digest(review)}) == "ORIGINAL_CORRESPONDENCE_VERIFIED"
    source["exact_text"] = "changed"
    with pytest.raises(ValueError, match="BODY_BINDING"):
        v.classify(source, {digest(review)})


@pytest.mark.parametrize("classification,clauses,known,result", [
    ("SECONDARY_SUMMARY", ["PERMIT"], True, "UNVERIFIED"),
    ("USER_SUPPLIED_RESPONSE_TEXT", ["RESTRICT"], True, "UNVERIFIED"),
    ("ORIGINAL_CORRESPONDENCE_VERIFIED", ["PERMIT", "RESTRICT"], True, "RESTRICTED"),
    ("ORIGINAL_CORRESPONDENCE_VERIFIED", ["PERMIT"], False, "PERMITTED_WITH_CONDITIONS"),
    ("ORIGINAL_CORRESPONDENCE_VERIFIED", ["AMBIGUOUS"], True, "UNVERIFIED"),
])
def test_permission(classification: str, clauses: list[str], known: bool, result: str) -> None:
    assert v.permission(classification, clauses, known) == result


def fixture() -> tuple[dict[str, Any], dict[str, Any]]:
    scope = {k: "synthetic" for k in ("compliance_fingerprint", "endpoints", "categories",
             "universe_sha256", "credential_custody", "storage_location", "retention_policy",
             "deletion_policy", "calendar_controls", "corporate_action_controls", "audit_policy",
             "date_windows", "instrument_resolution")}
    s: dict[str, Any] = dict(scope, compliance_classification="ORIGINAL_CORRESPONDENCE_VERIFIED",
                             permission="EXPLICITLY_PERMITTED", maximum_requests=1,
                             maximum_response_bytes=10, maximum_total_bytes=10, requests_per_second=1)
    p: dict[str, Any] = {"version": "Phase1436GenuineDataAcquisitionApproval.v1",
                         "Gate_A": "AUTHORIZED", "Gate_B": "NOT_AUTHORIZED",
                         "scopes": {"instrument_master": s, "historical_candles": dict(s)}}
    approval = {"status": "APPROVED", "packet": digest(p), "operation": "historical_candles",
                "issued_at": NOW.isoformat(), "expires_at": (NOW + timedelta(hours=1)).isoformat()}
    return p, approval


@pytest.mark.parametrize("fault", ["absent", "wrong_hash", "wrong_operation", "expired",
                                    "credential", "retention", "budget", "gate", "compliance"])
def test_approval_rejection(fault: str) -> None:
    packet, decision = fixture()
    scope = packet["scopes"]["historical_candles"]
    if fault == "credential":
        scope["credential_custody"] = None
    elif fault == "retention":
        scope["retention_policy"] = None
    elif fault == "budget":
        scope["maximum_requests"] = 0
    elif fault == "gate":
        packet["Gate_A"] = "NOT_AUTHORIZED"
    elif fault == "compliance":
        scope["compliance_classification"] = "SECONDARY_SUMMARY"
    decision["packet"] = digest(packet)
    if fault == "wrong_operation":
        decision["operation"] = "instrument_master"
    if fault == "expired":
        decision["expires_at"] = NOW.isoformat()
    with pytest.raises(ValueError):
        v.validate_scope(packet, "wrong" if fault == "wrong_hash" else digest(packet),
                         "historical_candles", None if fault == "absent" else decision,
                         {digest(decision)}, NOW)


@pytest.mark.parametrize("fault", ["reused", "duplicate_symbol", "missing", "stale"])
def test_mapping_reuse(fault: str) -> None:
    rows = [Mapping("NSE", "SYNTH", 1, NOW, "fixture")]
    if fault == "missing":
        rows = []
    if fault == "duplicate_symbol":
        rows *= 2
    with pytest.raises(ValueError):
        mappings(rows, ("SYNTH",), {1: ("NSE", "OTHER")} if fault == "reused" else {},
                 NOW + timedelta(days=2) if fault == "stale" else NOW, timedelta(days=1), "fixture")


@pytest.mark.parametrize("fault", ["missing", "duplicate", "outside", "calendar", "actions"])
def test_candle_contracts(fault: str) -> None:
    from decimal import Decimal

    rows = [Candle(NOW, *[Decimal(1)] * 5)]
    if fault == "missing":
        rows = []
    if fault == "duplicate":
        rows *= 2
    with pytest.raises(ValueError):
        completeness(rows, (NOW + timedelta(hours=1),) if fault == "outside" else (NOW,),
                     fault != "calendar", fault != "actions")


@pytest.mark.parametrize("seconds,authorized", [(None, True), (1, False), (1, True)])
def test_retention(seconds: int | None, authorized: bool) -> None:
    p: dict[str, Any] = {"version": "Phase1435Retention.v1", "authorized": authorized,
                         "source_sha256": "fixture", "maximum_seconds": seconds}
    for key in ("category", "location", "deletion_trigger", "deletion_verification",
                "backup_policy", "derived_output_policy"):
        p[key] = "synthetic"
    with pytest.raises(ValueError):
        retention(p, NOW, NOW - timedelta(seconds=2))


def test_offline_and_no_performance() -> None:
    import ast
    import inspect

    from verify_phase1435 import readiness

    tree = ast.parse(inspect.getsource(v))
    assert {n.module for n in ast.walk(tree) if isinstance(n, ast.ImportFrom)} <= {
        "datetime", "typing", "verify_phase1435"}
    assert not any(isinstance(n, ast.Import) for n in ast.walk(tree))
    assert readiness()["genuine_performance"] is None
    assert readiness()["qualified_pairs"] == readiness()["real_kite_requests"] == 0
