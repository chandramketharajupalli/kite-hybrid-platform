"""Synthetic contract tests; no genuine data, network or acquisition execution."""

from dataclasses import replace
from datetime import UTC, datetime, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any

import pytest
import verify_phase1435 as v

NOW = datetime(2026, 1, 2, 4, tzinfo=UTC)


def source() -> dict[str, Any]:
    return {"version": "Phase1435Compliance.v1", "source_sha256": "synthetic",
            "exact_text": "SYNTHETIC explicit fixture permission", "sender": "synthetic",
            "message_time": NOW.isoformat(), "custody_reference": "fixture",
            "authenticity_review": "VERIFIED", "permission": "EXPLICIT", "conflicts": []}


@pytest.mark.parametrize("field,value", [
    ("source_sha256", None), ("sender", None), ("exact_text", None),
    ("authenticity_review", "UNVERIFIED"), ("permission", "AMBIGUOUS"),
    ("conflicts", ["contradictory statement"]), ("version", "unknown"),
])
def test_compliance_fail_closed(field: str, value: Any) -> None:
    record = source()
    record[field] = value
    with pytest.raises(ValueError):
        v.compliance(record, {"synthetic"})


def test_explicit_synthetic_source() -> None:
    v.compliance(source(), {"synthetic"})
    with pytest.raises(ValueError):
        v.compliance(source(), set())


def policy() -> dict[str, Any]:
    return {"version": "Phase1435Retention.v1", "authorized": True,
            "source_sha256": "synthetic", "maximum_seconds": 60, "category": "fixture",
            "location": "fixture", "deletion_trigger": "expiry",
            "deletion_verification": "custodian receipt", "backup_policy": "none",
            "derived_output_policy": "separate approval"}


@pytest.mark.parametrize("field,value", [
    ("authorized", False), ("maximum_seconds", None), ("maximum_seconds", 0),
    ("maximum_seconds", True), ("source_sha256", None), ("backup_policy", None),
])
def test_retention_unknown_rejected(field: str, value: Any) -> None:
    record = policy()
    record[field] = value
    with pytest.raises(ValueError):
        v.retention(record, NOW, NOW)


def test_expiry_and_future() -> None:
    v.retention(policy(), NOW, NOW - timedelta(seconds=59))
    for stamp in (NOW - timedelta(seconds=60), NOW + timedelta(seconds=1)):
        with pytest.raises(ValueError):
            v.retention(policy(), NOW, stamp)


def rows() -> list[v.Mapping]:
    return [v.Mapping("NSE", "SYNTH-A", 1, NOW, "fixture"),
            v.Mapping("NSE", "SYNTH-B", 2, NOW, "fixture")]


@pytest.mark.parametrize("fault", ["missing", "duplicate", "exchange", "stale",
                                    "future", "source", "reused", "reorder"])
def test_mapping_failures(fault: str) -> None:
    data = rows()
    previous: dict[int, tuple[str, str]] = {}
    if fault == "missing":
        data.pop()
    elif fault == "duplicate":
        data[1] = replace(data[1], token=1)
    elif fault == "exchange":
        data[0] = replace(data[0], exchange="BSE")
    elif fault == "stale":
        data[0] = replace(data[0], observed=NOW - timedelta(days=1))
    elif fault == "future":
        data[0] = replace(data[0], observed=NOW + timedelta(seconds=1))
    elif fault == "source":
        data[0] = replace(data[0], source_sha256="changed")
    elif fault == "reused":
        previous[1] = ("NSE", "OTHER")
    else:
        data.reverse()
    with pytest.raises(ValueError):
        v.mappings(data, ("SYNTH-A", "SYNTH-B"), previous, NOW, timedelta(days=1), "fixture")


def test_mapping_identity_preserved() -> None:
    v.mappings(rows(), ("SYNTH-A", "SYNTH-B"), {1: ("NSE", "SYNTH-A")},
               NOW, timedelta(days=1), "fixture")


def candles() -> list[v.Candle]:
    return [v.Candle(NOW + timedelta(minutes=5 * n), Decimal(10), Decimal(12),
                     Decimal(9), Decimal(11), Decimal(0)) for n in range(2)]


@pytest.mark.parametrize("fault", ["missing", "duplicate", "outside", "order", "ohlcv",
                                    "nonfinite", "calendar", "actions", "timezone"])
def test_completeness_failures(fault: str) -> None:
    data = candles()
    expected = tuple(row.timestamp for row in data)
    if fault == "missing":
        data.pop()
    elif fault == "duplicate":
        data.append(data[0])
    elif fault == "outside":
        data[1] = replace(data[1], timestamp=NOW + timedelta(hours=8))
    elif fault == "order":
        data.reverse()
    elif fault == "ohlcv":
        data[0] = replace(data[0], low=Decimal(13))
    elif fault == "nonfinite":
        data[0] = replace(data[0], close=Decimal("NaN"))
    elif fault == "timezone":
        data[0] = replace(data[0], timestamp=NOW.replace(tzinfo=None))
    with pytest.raises(ValueError):
        v.completeness(data, expected, fault != "calendar", fault != "actions")


def test_zero_volume_is_observed_not_imputed() -> None:
    data = candles()
    assert v.completeness(data, tuple(x.timestamp for x in data), True, True)["observed"] == 2


def test_approval_and_wrong_fingerprint() -> None:
    packet: dict[str, Any] = {"version": "Phase1435GenuineDataAcquisitionApproval.v1"}
    with pytest.raises(ValueError, match="PACKET_CHANGED"):
        v.acquisition(packet, "wrong", None, set(), NOW)
    with pytest.raises(ValueError, match="ACQUISITION_NOT_AUTHORIZED"):
        v.acquisition(packet, v.digest(packet), None, set(), NOW)
    decision = {"status": "APPROVED", "packet_fingerprint": v.digest(packet)}
    with pytest.raises(ValueError, match="GOVERNANCE_NOT_AUTHORIZED"):
        v.acquisition(packet, v.digest(packet), decision, {v.digest(decision)}, NOW)


def test_no_genuine_performance_or_implicit_network() -> None:
    import ast
    import inspect

    tree = ast.parse(inspect.getsource(v))
    imports = {node.module for node in ast.walk(tree) if isinstance(node, ast.ImportFrom)}
    imports |= {alias.name for node in ast.walk(tree) if isinstance(node, ast.Import)
                for alias in node.names}
    assert imports <= {"hashlib", "json", "dataclasses", "datetime", "decimal", "typing"}
    report = v.readiness()
    assert report["genuine_performance"] is None and report["qualified_pairs"] == 0
    assert report["real_kite_requests"] == 0 and report["operational"] == "NO_GO"
    assert v.digest(report) == v.digest(v.readiness())


def test_preserved_exception() -> None:
    import hashlib

    root = Path(__file__).resolve().parents[2]
    assert hashlib.sha256((root / "docs/operations/phase-14.22-plan.md").read_bytes()).hexdigest() == (
        "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31")
