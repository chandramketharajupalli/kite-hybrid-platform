"""Bounded offline faults over existing scheduler, journals and monitoring contracts."""

import json
import shutil
from datetime import datetime, timedelta
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any

from strategy_engine.phase14.engine import PRECISION
from strategy_engine.phase1416.imports import canonical, external, read_json, sha
from strategy_engine.phase1418.portfolio import Control, Policy, Request
from strategy_engine.phase1419 import scheduler
from strategy_engine.phase1420 import multi
from strategy_engine.phase1424.analytics import MONITORING_POLICY, monitoring, seal

POLICY = {
    "version": "Phase1425FaultPolicy.v1",
    "scope": "SYNTHETIC_FIXTURE",
    "max_scenarios": 64,
    "clearance_procedure": None,
    "matching": MONITORING_POLICY,
    "independent_rollback_resistance": False,
}
CRASHES = {
    "crash_before_session": "CREATED",
    "crash_after_readiness": "READY",
    "crash_before_reservation": "RUNNING",
    "crash_after_reservation": "RESERVED",
    "crash_after_fill": "FILLED",
    "crash_during_eod": "EOD_LIQUIDATION",
}
FAULTS = tuple(CRASHES) + (
    "missing_eod_liquidity", "missing_candle", "duplicate_candle", "duplicate_session",
    "clock_rollback", "risk_policy_mismatch", "execution_policy_mismatch",
    "corrupt_journal", "truncated_journal", "missing_completion_report",
    "missing_monitoring_alert", "duplicate_alert", "late_alert",
    "invalid_alert_acknowledgment", "complete_authority_rollback",
)
ALERT_CATEGORIES = {
    **dict.fromkeys(CRASHES, "RECOVERY_REQUIRED"),
    "missing_eod_liquidity": "EOD_RECONCILIATION_FAILED",
    "missing_candle": "DATA_GAP", "duplicate_candle": "DATA_GAP",
    "duplicate_session": "SESSION_DUPLICATE", "clock_rollback": "CLOCK_ROLLBACK",
    "risk_policy_mismatch": "POLICY_MISMATCH", "execution_policy_mismatch": "POLICY_MISMATCH",
    "corrupt_journal": "JOURNAL_CORRUPTED", "truncated_journal": "JOURNAL_CORRUPTED",
    "missing_completion_report": "REPORT_MISSING",
    "missing_monitoring_alert": "MONITORING_FAILURE", "duplicate_alert": "MONITORING_FAILURE",
    "late_alert": "MONITORING_FAILURE", "invalid_alert_acknowledgment": "INVALID_ACK",
    "complete_authority_rollback": "INDEPENDENT_AUTHORITY_REQUIRED",
}
SessionArgs = tuple[
    tuple[Request, ...], Policy, Control,
    scheduler.Calendar, scheduler.Configuration, tuple[scheduler.Command, ...],
]
MultiArgs = tuple[
    tuple[multi.Day, ...], Policy, Control, scheduler.Calendar, multi.Limits,
]


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValueError(code)


def instant(value: str) -> datetime:
    at = datetime.fromisoformat(value)
    require(at.tzinfo is not None and at.utcoffset() == timedelta(0), "UTC_REQUIRED")
    return at


def authorize(
    request: dict[str, Any], trusted_approvals: dict[str, str], *, run_id: str,
    incident_id: str, evidence: str, now: datetime,
) -> dict[str, Any]:
    """Validate explicit caller-supplied synthetic approval; never mint approval here."""
    fields = {
        "recovery_request_id", "run_id", "incident_id", "requested_action",
        "evidence_fingerprint", "authority_identity", "authorization_timestamp",
        "expiry", "allowed_scope", "approval_fingerprint",
    }
    require(set(request) == fields, "AUTHORITY_FIELDS")
    body = {k: v for k, v in request.items() if k != "approval_fingerprint"}
    digest = sha(canonical(body))
    require(
        request["approval_fingerprint"] == digest
        and trusted_approvals.get(request["recovery_request_id"]) == digest,
        "UNTRUSTED_APPROVAL",
    )
    require(request["authority_identity"].startswith("synthetic:"), "SYNTHETIC_IDENTITY")
    require(request["run_id"] == run_id and request["incident_id"] == incident_id,
            "AUTHORITY_BINDING")
    require(request["evidence_fingerprint"] == evidence, "AUTHORITY_EVIDENCE")
    require(request["requested_action"] == "EVALUATE_REPLAY"
            and request["allowed_scope"] == ["SYNTHETIC_REPLAY"], "AUTHORITY_SCOPE")
    at, expiry = instant(request["authorization_timestamp"]), instant(request["expiry"])
    require(now.tzinfo is not None and at <= now < expiry, "AUTHORITY_EXPIRED_OR_FUTURE")
    return {"replay_authorized": True, "halt_clearance": False, "operational_authority": False}


def inventory(directory: Path) -> dict[str, str]:
    return {str(p.relative_to(directory)).replace("\\", "/"): sha(p.read_bytes())
            for p in sorted(directory.rglob("*")) if p.is_file()}


def verify_evidence(
    directory: Path, records: list[dict[str, str]], *, required: set[str],
) -> dict[str, Any]:
    """Verify sealed external files, JSON syntax and exact mandatory inventory; no repair."""
    directory = external(directory)
    require(len(records) <= 10000, "EVIDENCE_BUDGET")
    names = [r["path"] for r in records]
    require(len(names) == len(set(names)), "DUPLICATE_EVIDENCE")
    require(set(names) == required, "INCOMPLETE_EVIDENCE")
    for row in records:
        path = directory / row["path"]
        require(directory in path.resolve().parents and not path.is_symlink(), "EVIDENCE_PATH")
        require(path.is_file(), "MISSING_EVIDENCE")
        require(path.stat().st_size <= 64 * 1024 * 1024, "EVIDENCE_BUDGET")
        require(sha(path.read_bytes()) == row["sha256"], "MODIFIED_EVIDENCE")
        if path.suffix == ".json":
            read_json(path, 64 * 1024 * 1024)
    return {"scope": "SYNTHETIC_FIXTURE", "verified": len(records), "repair": False}


def registry(
    run_id: str, session_id: str, at: datetime,
    injection_times: dict[str, datetime] | None = None,
) -> list[dict[str, Any]]:
    require(at.tzinfo is not None, "SCENARIO_TIME")
    return [seal({
        "version": "Phase1425Scenario.v1", "scenario_id": name,
        "run_id": run_id, "session_id": session_id, "fault_category": name,
        "injection_timestamp": (injection_times or {}).get(name, at).isoformat(),
        "injection_point": CRASHES.get(name, name),
        "expected_state_transition": "RECOVERY_PENDING" if name in CRASHES else
        "UNDETECTABLE_LOCAL_ROLLBACK" if name == "complete_authority_rollback" else "FAIL_CLOSED",
        "expected_alert_category": ALERT_CATEGORIES[name],
        "expected_recovery_behavior": "NO_AUTOMATIC_HALT_CLEARANCE",
        "synthetic_ground_truth_fingerprint": sha(canonical({"fault": name, "run": run_id})),
        "fault_policy_fingerprint": sha(canonical(POLICY)),
        "scope": "SYNTHETIC_FIXTURE",
    }) for name in FAULTS]


def account_check(account: dict[str, Any], opening: Decimal) -> None:
    with localcontext(PRECISION):
        d = Decimal
        require(d(account["cash"]) >= d(account["reserved"]) >= 0, "CASH_RESERVATION")
        require(d(account["cash"]) + d(account["marked_value"]) == d(account["equity"]),
                "EQUITY_RECONCILIATION")
        require(opening + d(account["realized"]) + d(account["unrealized"])
                == d(account["equity"]), "CASH_CREATION")


def session_check(directory: Path, result: dict[str, Any], opening: Decimal) -> dict[str, Any]:
    rows = json.loads((directory / "session-events.json").read_bytes())
    events = [event for row in rows for event in row["portfolio_events"]]
    account_check(result["account"], opening)
    for event in events:
        account_check(event["account"], opening)
    counts = {state: sum(e["state"] == state for e in events)
              for state in ("RESERVED", "FILLED", "CLOSED", "CANCELLED")}
    # The fixture is one signal/position; replay must not duplicate any accounting operation.
    require(all(counts[k] <= 1 for k in counts), "DUPLICATE_REPLAY")
    if result["complete"]:
        require(not result["account"]["positions"] and not result["account"]["reservations"],
                "FALSE_COMPLETION")
    return {"invariants": "PASS", "events_checked": len(events), "counts": counts,
            "account": result["account"]}


def session_case(
    name: str, args: SessionArgs, directory: Path, *,
    request: dict[str, Any] | None = None, approvals: dict[str, str] | None = None,
) -> dict[str, Any]:
    """Explicit fault interface; only external synthetic fixtures may enter this evaluator."""
    external(directory)
    require(name in FAULTS, "UNKNOWN_SCENARIO")
    rs, p, ctl, cal, cfg, commands = args
    require(all(r.data.series.synthetic for r in rs), "SYNTHETIC_ONLY")
    run_id = sha(canonical([r.model_dump(mode="json") for r in rs]))
    if name in CRASHES:
        require(request is not None and approvals is not None, "EXPLICIT_APPROVAL_REQUIRED")
        assert request is not None and approvals is not None
        authorize(request, approvals, run_id=run_id, incident_id=name,
                  evidence=sha(canonical([c.model_dump(mode="json") for c in commands])),
                  now=commands[0].at)
        try:
            scheduler.run(*args, directory, crash=CRASHES[name])
        except InterruptedError:
            pass
        else:
            raise ValueError("FAULT_NOT_INJECTED")
        result = scheduler.run(*args, directory, resume=True)
        require(result["complete"], "RECOVERY_INCOMPLETE")
    else:
        cs = list(commands)
        if name == "missing_candle":
            cs[2] = cs[2].model_copy(update={"completed": ()})
        elif name == "duplicate_candle":
            cs[2] = cs[2].model_copy(update={"completed": cs[2].completed * 2})
        elif name == "clock_rollback":
            cs[2] = cs[2].model_copy(update={"at": cs[1].at - timedelta(seconds=1)})
        elif name in {"risk_policy_mismatch", "execution_policy_mismatch"}:
            key = "risk_hash" if name == "risk_policy_mismatch" else "execution_hash"
            cfg = cfg.model_copy(update={key: "0" * 64})
        elif name not in {"corrupt_journal", "truncated_journal", "missing_completion_report",
                          "missing_eod_liquidity"}:
            raise ValueError("WRONG_SCENARIO_INTERFACE")
        args = rs, p, ctl, cal, cfg, tuple(cs)
        result = scheduler.run(*args, directory)
    check = session_check(directory, result, p.base.capital or Decimal(0))
    before = inventory(directory)
    require(scheduler.run(*args, directory, verify_only=True) == result, "REPLAY_DIFFERENCE")
    require(inventory(directory) == before, "REPLAY_MUTATION")
    rejection = None
    if name in {"corrupt_journal", "truncated_journal", "missing_completion_report"}:
        if name == "corrupt_journal":
            next((directory / "journal").glob("*.json")).write_bytes(b"{broken")
        elif name == "truncated_journal":
            sorted((directory / "journal").glob("*.json"))[-1].unlink()
        else:
            (directory / "summary.json").unlink()
        damaged = inventory(directory)
        try:
            scheduler.run(*args, directory, verify_only=True)
        except ValueError as exc:
            rejection = str(exc)
        require(rejection is not None, "CORRUPTION_ACCEPTED")
        require(inventory(directory) == damaged, "SILENT_REPAIR")
    else:
        try:
            scheduler.run(*args, directory, resume=True)
        except ValueError as exc:
            rejection = str(exc)
        require(rejection is not None, "DUPLICATE_SESSION_ACCEPTED")
    halted = result["state"] == "PAPER_HALTED"
    if name not in CRASHES and name not in {
        "corrupt_journal", "truncated_journal", "missing_completion_report",
    }:
        require(halted and not result["complete"], "HALT_LOST")
    return {"scenario_id": name, "scope": "SYNTHETIC_FIXTURE", "test_outcome": "PASS",
            "recovery": "RECOVERED" if name in CRASHES else "BLOCKED",
            "halted": halted, "halt_clearance": False, "replay_rejection": rejection,
            "accounting": check, "evidence": inventory(directory),
            "sealed_evidence_before_fault": before,
            "evidence_rejection": rejection if name in {
                "corrupt_journal", "truncated_journal", "missing_completion_report"} else None,
            "complete": result["complete"], "result_fingerprint": sha(canonical(result))}


def continuity(args: MultiArgs, directory: Path) -> dict[str, Any]:
    require(all(r.data.series.synthetic for d in args[0] for r in d.requests), "SYNTHETIC_ONLY")
    result = multi.run(*args, directory)
    require(multi.run(*args, directory, verify_only=True, expected_head=result["head"])
            == result, "CROSS_SESSION_REPLAY")
    for left, right in zip(result["sessions"], result["sessions"][1:], strict=False):
        require(left["closing_state"] == right["starting_state"], "CARRY_FORWARD")
    if result["state"]["halted"]:
        require(len(result["sessions"]) == 1 and not result["complete"], "HALT_ADVANCED")
        last = result["alerts"][-1]
        ack = multi.acknowledge(directory, last["alert_id"], args[0][0].commands[-1].at, "a" * 64)
        require(not ack["resume_authorized"], "ACK_AUTHORIZED_RECOVERY")
        try:
            multi.run(*args, directory, resume=True, expected_head=result["head"])
        except multi.Rejected:
            pass
        else:
            raise ValueError("HALT_CLEARED")
        require(multi.run(*args, directory, verify_only=True, expected_head=result["head"])
                == result, "ACK_CHANGED_HALT")
    return result


def rollback_counterexample(args: MultiArgs, directory: Path) -> dict[str, Any]:
    """All mutable artifacts here are synthetic, external and owned by this invocation."""
    external(directory)
    require(all(r.data.series.synthetic for d in args[0] for r in d.requests), "SYNTHETIC_ONLY")
    original, snapshot, restored = (directory / n for n in ("original", "snapshot", "restored"))
    try:
        multi.run(*args, original, crash="before_first")
    except InterruptedError:
        pass
    require(multi.head(original) == multi.ZERO, "COUNTEREXAMPLE_SETUP")
    shutil.copytree(original, snapshot)
    completed = multi.run(*args, original, resume=True, expected_head=multi.ZERO)
    shutil.copytree(snapshot, restored)
    replayed = multi.run(*args, restored, resume=True, expected_head=multi.ZERO)
    require(completed == replayed, "COUNTEREXAMPLE_CHANGED")
    return {"scenario_id": "complete_authority_rollback", "scope": "SYNTHETIC_FIXTURE",
            "test_outcome": "PASS", "control_status": "LIMITATION_DEMONSTRATED",
            "independent_rollback_resistance": False, "reexecution_accepted": True,
            "operational": "NO_GO", "remediation": "Independent durable authority required"}


def detection(truth: dict[str, Any], alerts: list[dict[str, Any]]) -> dict[str, Any]:
    report = monitoring(truth, alerts, sha(canonical(alerts)))
    missed = report["missed_critical"]
    return {"scope": "SYNTHETIC_FIXTURE", "measurement": report,
            "monitoring_evidence": "INCOMPLETE" if missed else "COMPLETE",
            "certification": "BLOCKED" if missed else "SYNTHETIC_ONLY",
            "missed_critical_incidents": missed, "halt_clearance": False,
            "remediation": [f"Restore and independently retest alert for {i}" for i in missed],
            "failure_detection_is_successful_alerting": False}


def assessment(outcomes: list[dict[str, Any]], monitor: dict[str, Any]) -> dict[str, Any]:
    require(0 < len(outcomes) <= 64, "SCENARIO_BUDGET")
    recovered = sum(o.get("recovery") == "RECOVERED" for o in outcomes)
    attempts = sum(o["scenario_id"] in CRASHES for o in outcomes)
    passed = all(o["test_outcome"] == "PASS" for o in outcomes)
    return {
        "scope": "SYNTHETIC_FIXTURE", "engineering": "PARTIAL",
        "classification": "PHASE_14_25_PARTIAL", "mandatory_test_results_pass": passed,
        "metrics": {"scenario_execution_count": len(outcomes),
                    "successful_recovery_count": recovered,
                    "failed_recovery_count": attempts - recovered,
                    "recovery_success_fraction": str(Decimal(recovered) / attempts)
                    if attempts else None,
                    "duplicate_replay_rejection_count": sum(
                        bool(o.get("replay_rejection")) and not o.get("evidence_rejection")
                        for o in outcomes),
                    "reconciliation_failure_count": sum(o.get("accounting", {}).get("invariants")
                                                        == "FAIL" for o in outcomes),
                    "incomplete_session_count": sum(o.get("complete") is False for o in outcomes),
                    "halt_persistence_success_count": sum(o.get("halted") is True
                                                          for o in outcomes)},
        "monitoring": monitor, "genuine_strategy": "BLOCKED", "genuine_qualified": 0,
        "operational": "NO_GO", "Gate_A": "NOT_AUTHORIZED", "Gate_B": "NOT_AUTHORIZED",
        "Stage_B": "ZERO_CALL_ABORT", "Phase12": "BLOCK", "H1_H2": "FROZEN",
        "July": "SEALED", "real_kite_requests": 0, "halt_clearance": False,
        "limitations": ["Complete local-authority rollback remains undetectable",
                        "Injected missed critical alerts remain unresolved",
                        "Synthetic process exceptions do not prove OS/power-loss durability",
                        "No production uptime, SLA, MTBF or MTTR evidence"],
    }
