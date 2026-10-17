"""Offline fault experiments reuse prior validated synthetic fixture constructors."""

import ast
import inspect
import json
import os
import subprocess
import sys
from datetime import timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any

import pytest
from test_phase1418b import changed
from test_phase1419 import commands, fixture
from test_phase1420 import fixture as multi_fixture

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1419 import scheduler
from strategy_engine.phase1420 import multi
from strategy_engine.phase1424.analytics import monitoring
from strategy_engine.phase1425 import reliability as rel


def approval(args: rel.SessionArgs, name: str) -> tuple[dict[str, Any], dict[str, str]]:
    at = args[-1][0].at
    body: dict[str, Any] = {
        "recovery_request_id": "synthetic-request:" + name,
        "run_id": sha(canonical([r.model_dump(mode="json") for r in args[0]])),
        "incident_id": name, "requested_action": "EVALUATE_REPLAY",
        "evidence_fingerprint": sha(canonical([c.model_dump(mode="json") for c in args[-1]])),
        "authority_identity": "synthetic:offline-test-owner",
        "authorization_timestamp": at.isoformat(), "expiry": (at + timedelta(hours=1)).isoformat(),
        "allowed_scope": ["SYNTHETIC_REPLAY"],
    }
    fingerprint = sha(canonical(body))
    return body | {"approval_fingerprint": fingerprint}, {body["recovery_request_id"]: fingerprint}


def eod_fixture() -> rel.SessionArgs:
    rs, p, ctl, cal, cfg, _ = fixture()
    rs = (changed(rs[0], 5, volume=0),)
    return rs, p, ctl, cal, cfg, commands(rs)


def halted_multi() -> rel.MultiArgs:
    days, p, ctl, cal, limits = multi_fixture()
    r = changed(days[0].requests[0], 5, volume=0)
    days = (days[0].model_copy(update={"requests": (r,), "commands": commands((r,))}), days[1])
    return days, p, ctl, cal, limits


def monitoring_fixture() -> dict[str, Any]:
    return dict(json.loads((ROOT / "research/phase-14.24/generation-1/"
                           "synthetic-exposure-monitoring.json").read_bytes()))


def experiment(name: str, directory: Path) -> dict[str, Any]:
    args = eod_fixture() if name == "missing_eod_liquidity" else fixture()
    request, trusted = approval(args, name)
    return rel.session_case(name, args, directory, request=request, approvals=trusted)


SESSION_CASES = tuple(rel.CRASHES) + (
    "missing_eod_liquidity", "missing_candle", "duplicate_candle", "clock_rollback",
    "risk_policy_mismatch", "execution_policy_mismatch", "corrupt_journal",
    "truncated_journal", "missing_completion_report",
)


@pytest.mark.parametrize("name", SESSION_CASES)
def test_scheduler_faults_and_replay(name: str, tmp_path: Path) -> None:
    a = experiment(name, tmp_path / "first")
    b = experiment(name, tmp_path / "second")
    assert a == b and a["test_outcome"] == "PASS"
    if name in rel.CRASHES:
        assert a["accounting"]["counts"] == {
            "RESERVED": 1, "FILLED": 1, "CLOSED": 1, "CANCELLED": 0,
        }
    if name == "missing_eod_liquidity":
        assert a["accounting"]["account"]["positions"]["SBIN"]["quantity"] == 10


@pytest.mark.parametrize("fault", ["missing", "untrusted", "hash", "expired", "future",
                                   "scope", "run", "incident", "evidence", "identity", "action"])
def test_authorization_rejected(fault: str) -> None:
    args = fixture()
    request, trusted = approval(args, "crash_after_fill")
    original = dict(request)
    at = args[-1][0].at
    if fault == "missing":
        request.pop("authority_identity")
    elif fault == "untrusted":
        trusted = {}
    elif fault == "hash":
        request["approval_fingerprint"] = "0" * 64
    else:
        updates: dict[str, Any] = {
            "expired": ("expiry", at.isoformat()),
            "future": ("authorization_timestamp", (at + timedelta(minutes=1)).isoformat()),
            "scope": ("allowed_scope", ["CLEAR_HALT"]), "run": ("run_id", "other"),
            "incident": ("incident_id", "other"), "evidence": ("evidence_fingerprint", "0" * 64),
            "identity": ("authority_identity", "real-owner"),
            "action": ("requested_action", "CLEAR_HALT"),
        }
        key, value = updates[fault]
        request[key] = value
        request["approval_fingerprint"] = sha(canonical({k: v for k, v in request.items()
                                                       if k != "approval_fingerprint"}))
        trusted[request["recovery_request_id"]] = request["approval_fingerprint"]
    with pytest.raises(ValueError):
        rel.authorize(request, trusted, run_id=original["run_id"],
                      incident_id=original["incident_id"],
                      evidence=original["evidence_fingerprint"],
                      now=at)


def test_valid_approval_never_clears_halt() -> None:
    args = fixture()
    r, trusted = approval(args, "crash_after_fill")
    assert rel.authorize(r, trusted, run_id=r["run_id"], incident_id=r["incident_id"],
                         evidence=r["evidence_fingerprint"], now=args[-1][0].at) == {
        "replay_authorized": True, "halt_clearance": False, "operational_authority": False,
    }


def test_explicit_approval_required(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="EXPLICIT_APPROVAL"):
        rel.session_case("crash_after_fill", fixture(), tmp_path)
    assert not list(tmp_path.iterdir())


def test_cross_session_cash_drawdown_and_halt(tmp_path: Path) -> None:
    normal = rel.continuity(multi_fixture(), tmp_path / "normal")
    assert len(normal["sessions"]) == 2
    assert Decimal(normal["state"]["cash"]) == Decimal(10000) + Decimal(
        normal["state"]["cumulative_net"])
    assert normal["daily"][0]["realized"] == normal["daily"][1]["realized"]
    assert Decimal(normal["state"]["maximum_drawdown"]) > 0
    halted = rel.continuity(halted_multi(), tmp_path / "halted")
    assert halted["state"]["halted"] and len(halted["sessions"]) == 1
    assert halted["state"]["positions"]["SBIN"]["quantity"] == 10


@pytest.mark.parametrize("fault", ["report", "journal", "policy", "clock"])
def test_halted_evidence_failure_does_not_clear_positions(tmp_path: Path, fault: str) -> None:
    args = eod_fixture()
    result = scheduler.run(*args, tmp_path)
    snapshot = result["account"]
    if fault == "report":
        (tmp_path / "summary.json").unlink()
    elif fault == "journal":
        next((tmp_path / "journal").glob("*.json")).write_bytes(b"{}")
    elif fault == "policy":
        args = (*args[:4], args[4].model_copy(update={"risk_hash": "0" * 64}), args[5])
    else:
        args = (*args[:5], (args[5][0].model_copy(update={"at": args[5][0].at
                                                       - timedelta(seconds=1)}),) + args[5][1:])
    before = rel.inventory(tmp_path)
    with pytest.raises((ValueError, KeyError)):
        scheduler.run(*args, tmp_path, verify_only=True)
    assert rel.inventory(tmp_path) == before
    assert snapshot["halted"] and snapshot["positions"]["SBIN"]["quantity"] == 10


def test_duplicate_session_rejected(tmp_path: Path) -> None:
    days, p, ctl, cal, limits = multi_fixture()
    with pytest.raises(multi.Rejected, match="SESSION_DUPLICATE"):
        multi.run((days[0], days[0]), p, ctl, cal, limits, tmp_path)


def test_complete_authority_rollback_limitation(tmp_path: Path) -> None:
    report = rel.rollback_counterexample(multi_fixture(), tmp_path)
    assert report["reexecution_accepted"] and not report["independent_rollback_resistance"]


def test_missing_duplicate_late_and_ground_truth() -> None:
    data = monitoring_fixture()
    report = rel.detection(data["ground_truth"], data["alerts"])
    m = report["measurement"]
    assert (m["true_positives"], m["false_positives"], m["false_negatives"]) == (2, 3, 8)
    assert len(m["duplicate_alerts"]) == len(m["late_alerts"]) == 1
    assert len(report["missed_critical_incidents"]) == 8
    assert report["certification"] == "BLOCKED"
    assert not report["failure_detection_is_successful_alerting"]
    assert rel.detection(data["ground_truth"], [])["measurement"]["false_negatives"] == 10
    altered = dict(data["ground_truth"]) | {"fingerprint": "0" * 64}
    with pytest.raises(ValueError, match="GROUND_TRUTH_HASH"):
        rel.detection(altered, data["alerts"])


def test_invalid_alert_acknowledgment(tmp_path: Path) -> None:
    result = rel.continuity(halted_multi(), tmp_path)
    with pytest.raises(ValueError):
        multi.acknowledge(tmp_path, "0" * 64, halted_multi()[0][0].commands[-1].at, "a" * 64)
    assert result["state"]["halted"]
    data = monitoring_fixture()
    with pytest.raises((ValueError, KeyError)):
        monitoring(data["ground_truth"], data["alerts"], sha(canonical(data["alerts"])),
                   acknowledgments=[{"alert_id": "0" * 64}])


@pytest.mark.parametrize("fault", ["missing", "modified", "duplicate", "json", "json_keys",
                                   "missing_receipt", "count", "completion"])
def test_operational_evidence_rejection(tmp_path: Path, fault: str) -> None:
    path = tmp_path / "completion.json"
    path.write_bytes(b'{"complete":true}')
    (tmp_path / "test-receipt.json").write_bytes(b'{"tests":1,"failures":0}')
    required = {"completion.json", "test-receipt.json"}
    records = [{"path": n, "sha256": h} for n, h in rel.inventory(tmp_path).items()]
    assert rel.verify_evidence(tmp_path, records, required=required)["verified"] == 2
    if fault == "missing":
        path.unlink()
    elif fault in {"modified", "completion"}:
        path.write_bytes(b'{"complete":false}')
    elif fault == "duplicate":
        records.append(records[0])
    elif fault in {"json", "json_keys"}:
        path.write_bytes(b"{bad" if fault == "json" else b'{"complete":true,"complete":false}')
        records = [{"path": n, "sha256": h} for n, h in rel.inventory(tmp_path).items()]
    elif fault == "missing_receipt":
        (tmp_path / "test-receipt.json").unlink()
    else:
        records.pop()
    before = rel.inventory(tmp_path)
    with pytest.raises(ValueError):
        rel.verify_evidence(tmp_path, records, required=required)
    assert rel.inventory(tmp_path) == before


def test_no_broker_dependencies_and_registry() -> None:
    for node in ast.walk(ast.parse(inspect.getsource(rel))):
        modules = [a.name for a in node.names] if isinstance(node, ast.Import) else (
            [node.module or ""] if isinstance(node, ast.ImportFrom) else [])
        assert not any(x in m.lower() for m in modules
                       for x in ("kiteconnect", "requests", "socket", "http", "smtp"))
    args = fixture()
    rows = rel.registry("synthetic-run", "synthetic-session", args[-1][0].at)
    assert len(rows) == len({r["scenario_id"] for r in rows}) == 21
    assert rows == rel.registry("synthetic-run", "synthetic-session", args[-1][0].at)


def build_reports(directory: Path) -> dict[str, Any]:
    a = fixture()
    cs = a[-1]
    # Declared clocks precede observation of results; never derive truth from alerts.
    times = dict.fromkeys(rel.FAULTS, cs[-1].at + timedelta(seconds=1))
    times.update({
        "crash_before_session": cs[0].at, "crash_after_readiness": cs[0].at,
        "crash_before_reservation": cs[1].at, "crash_after_reservation": cs[5].at,
        "crash_after_fill": cs[5].at, "crash_during_eod": cs[-1].at,
        "missing_eod_liquidity": cs[-1].at, "missing_candle": cs[2].at,
        "duplicate_candle": cs[2].at, "clock_rollback": cs[1].at - timedelta(seconds=1),
        "risk_policy_mismatch": cs[0].at, "execution_policy_mismatch": cs[0].at,
        "duplicate_session": cs[0].at,
    })
    ledger = rel.registry("synthetic-reliability-run", "synthetic-session", cs[0].at, times)
    outcomes = [experiment(name, directory / name) for name in SESSION_CASES]
    normal = rel.continuity(multi_fixture(), directory / "continuity")
    halted = rel.continuity(halted_multi(), directory / "halt-continuity")
    days, p, ctl, cal, limits = multi_fixture()
    try:
        multi.run((days[0], days[0]), p, ctl, cal, limits, directory / "duplicate")
    except multi.Rejected as exc:
        duplicate = {"scenario_id": "duplicate_session", "scope": "SYNTHETIC_FIXTURE",
                     "test_outcome": "PASS", "replay_rejection": str(exc)}
    else:
        raise AssertionError("duplicate accepted")
    outcomes.append(duplicate)
    outcomes.append(rel.rollback_counterexample(multi_fixture(), directory / "rollback"))
    data = monitoring_fixture()
    monitor = rel.detection(data["ground_truth"], data["alerts"])
    assert monitor["measurement"]["false_negatives"] == 8
    assert len(monitor["measurement"]["duplicate_alerts"]) == 1
    assert len(monitor["measurement"]["late_alerts"]) == 1
    for name in ("missing_monitoring_alert", "duplicate_alert", "late_alert"):
        outcomes.append({"scenario_id": name, "scope": "SYNTHETIC_FIXTURE",
                         "test_outcome": "PASS", "control_status": "MONITORING_LIMITATION",
                         "measurement_fingerprint": sha(canonical(monitor))})
    test_invalid_alert_acknowledgment(directory / "invalid-ack")
    outcomes.append({"scenario_id": "invalid_alert_acknowledgment", "scope": "SYNTHETIC_FIXTURE",
                     "test_outcome": "PASS", "halt_clearance": False})
    assert {o["scenario_id"] for o in outcomes} == set(rel.FAULTS)
    integrity = []
    for outcome in outcomes:
        if "sealed_evidence_before_fault" not in outcome:
            continue
        name = outcome["scenario_id"]
        records = [{"path": n, "sha256": h}
                   for n, h in outcome["sealed_evidence_before_fault"].items()]
        try:
            rel.verify_evidence(directory / name, records, required={r["path"] for r in records})
        except ValueError as exc:
            integrity.append({"scenario_id": name, "status": "REJECTED", "reason": str(exc)})
    assert len(integrity) == 3
    summary = rel.assessment(outcomes, monitor)
    assert summary["metrics"]["duplicate_replay_rejection_count"] == 13
    summary["metrics"]["evidence_verification_failure_count"] = len(integrity)
    summary["metrics"]["critical_incident_detection_fraction"] = monitor["measurement"]["recall"]
    return {
        "scope": "SYNTHETIC_FIXTURE", "scenario_ledger": ledger, "recovery_outcomes": outcomes,
        "halt_continuity": halted, "incident_detection": monitor,
        "missed_critical_incidents": monitor["missed_critical_incidents"],
        "accounting_reconciliation": [o["accounting"] for o in outcomes if "accounting" in o],
        "cross_session_continuity": normal, "evidence_integrity": integrity,
        "engineering_reliability": summary,
        "genuine_strategy_readiness": {k: "BLOCKED" for k in (
            "instrument_mappings", "authorized_history", "calendar_provenance",
            "corporate_actions", "qualified_pairs")},
        "operational_readiness": {k: "NOT_AUTHORIZED" for k in (
            "terms", "Gate_A", "Gate_B", "credential_custody", "production_isolation",
            "risk_limits", "independent_rollback", "external_logging", "owner_approval")},
        "final_certification": "PHASE_14_25_PARTIAL",
    }


def test_report_replays(tmp_path: Path) -> None:
    left = build_reports(tmp_path / "left")
    assert left == build_reports(tmp_path / "right")
    clocks = {r["scenario_id"]: r["injection_timestamp"] for r in left["scenario_ledger"]}
    assert clocks["crash_after_fill"] > clocks["crash_after_readiness"]


def test_halt_survives_fresh_python_process(tmp_path: Path) -> None:
    result = scheduler.run(*eod_fixture(), tmp_path)
    env = os.environ.copy()
    env["PYTHONDONTWRITEBYTECODE"] = "1"
    env["PYTHONPATH"] = os.pathsep.join(
        (str(Path(__file__).parent), str(ROOT / "apps/strategy-engine/src")))
    script = (
        "import sys,json; from pathlib import Path; from test_phase1425 import eod_fixture; "
        "from strategy_engine.phase1419.scheduler import run; "
        "print(json.dumps(run(*eod_fixture(),Path(sys.argv[1]),verify_only=True),sort_keys=True))"
    )
    replay = subprocess.run([sys.executable, "-B", "-c", script, str(tmp_path)],
                            env=env, capture_output=True, check=True)
    assert json.loads(replay.stdout) == result
    assert result["account"]["halted"] and result["account"]["positions"]
