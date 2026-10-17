"""Synthetic alert assurance and explicit recovery authority experiments."""

import inspect
import os
import shutil
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any

import pytest
from test_phase1419 import fixture as session_fixture
from test_phase1420 import fixture as multi_fixture
from test_phase1425 import experiment, halted_multi, monitoring_fixture

from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1419 import scheduler
from strategy_engine.phase1425 import reliability
from strategy_engine.phase1426.alerts import AcceptedEvent, AlertLedger, assure, emitted
from strategy_engine.phase1426.authority import ZERO, Approval, Authority, Witness, chain

AT = datetime(2026, 10, 5, 4, tzinfo=UTC)
TRUTH = "11308eecb93adfd1c3c9cd5a3ac393eb8f1111afdd3f7d9aed2e539f664a836d"


def events() -> tuple[AcceptedEvent, ...]:
    result = []
    for incident in monitoring_fixture()["ground_truth"]["incidents"]:
        body = dict(
            version="Phase1426AcceptedIncident.v1",
            scope="SYNTHETIC_FIXTURE",
            incident_id=incident["incident_id"],
            run_id=incident["run_id"],
            session_date=incident["session_date"],
            category=incident["category"],
            occurred_at=incident["start"],
            accepted_at="2026-10-05T04:00:05Z",
            incident_evidence=sha(canonical(incident)),
        )
        result.append(AcceptedEvent.model_validate(body | {"fingerprint": sha(canonical(body))}))
    return tuple(result)


def evaluate(alerts: list[dict[str, Any]], **updates: Any) -> dict[str, Any]:
    truth = monitoring_fixture()["ground_truth"]
    args = dict(
        independent_truth=truth,
        expected_truth=TRUTH,
        events=events(),
        alerts=alerts,
        expected_alert_hash=sha(canonical(alerts)),
        run_id="a" * 64,
        session_date="2026-10-05",
        monitoring_policy=truth["policy_hash"],
        paper_halt=True,
    )
    args.update(updates)
    return assure(**args)


def setup_authority(
    root: Path, observed: Witness | None = None
) -> tuple[Authority, Witness, str, dict[str, Any]]:
    state = observed or Witness(
        run_id="a" * 64,
        session_sequence=0,
        highest_event_sequence=0,
        journal_head="c" * 64,
        authority_epoch=0,
        paper_halt=True,
        last_accepted_recovery_action="NONE",
    )
    authority = Authority(root / "local", root / "witness", {})
    head = authority.initialize(state)
    body = dict(
        version="Phase1426Recovery.v1",
        recovery_request_id="1" * 64,
        run_id=state.run_id,
        incident_id="2" * 64,
        authority_identity="synthetic:owner",
        authorized_action="RESUME_SYNTHETIC_REPLAY",
        authorization_scope="SYNTHETIC_RECOVERY_ONLY",
        issued_timestamp="2026-10-05T03:59:00Z",
        expiry_timestamp="2026-10-05T05:00:00Z",
        evidence_fingerprint="3" * 64,
        current_trusted_head_fingerprint=head,
        one_use_consumption_identity="4" * 64,
    )
    approval = body | {"approval_fingerprint": sha(canonical(body))}
    Approval.model_validate(approval)
    authority.trusted_approvals = {
        approval["recovery_request_id"]: approval["approval_fingerprint"]
    }
    return authority, state, head, approval


def completed(state: Witness) -> Witness:
    return state.model_copy(
        update={
            "highest_event_sequence": state.highest_event_sequence + 1,
            "journal_head": sha(canonical(state.model_dump(mode="json"))),
            "last_accepted_recovery_action": "RESUME_SYNTHETIC_REPLAY",
        }
    )


def recover(
    a: Authority, state: Witness, head: str, approval: dict[str, Any] | None, **updates: Any
) -> dict[str, Any]:
    args: dict[str, Any] = dict(
        expected_head=head,
        observed=state,
        run_id=state.run_id,
        incident_id="2" * 64,
        evidence="3" * 64,
        now=AT,
        action=completed,
    )
    args.update(updates)
    return a.recover(approval, **args)


@pytest.mark.parametrize("index", range(2, 10))
def test_eight_historical_misses(index: int) -> None:
    old = monitoring_fixture()
    incident = events()[index]
    assert incident.incident_id in old["monitoring"]["missed_critical"]
    result = evaluate([emitted(e) for e in events()])
    assert incident.incident_id in {m["incident_id"] for m in result["matches"]}
    assert result["timely_monitored_alerts"] == 10 and result["missed_critical_alerts"] == 0


def test_historical_metrics_and_independent_emitter() -> None:
    old = monitoring_fixture()["monitoring"]
    assert (old["true_positives"], old["false_positives"], old["false_negatives"]) == (2, 3, 8)
    assert len(old["duplicate_alerts"]) == len(old["late_alerts"]) == 1
    assert tuple(inspect.signature(emitted).parameters) == ("event",)
    assert evaluate([])["evaluator_missing_alert_detection_count"] == 10
    assert not evaluate([])["certification_eligible"]


@pytest.mark.parametrize("fault", ["missing", "late", "duplicate", "false_positive", "reorder"])
def test_alert_classifications(fault: str) -> None:
    alerts = [emitted(e) for e in events()]
    if fault == "missing":
        alerts.pop()
    elif fault == "reorder":
        alerts.reverse()
    else:
        row = dict(alerts[0])
        row.pop("alert_id")
        row["timestamp"] = "2026-10-05T04:01:10Z" if fault == "late" else "2026-10-05T04:00:10Z"
        if fault == "false_positive":
            row["incident_id"] = "f" * 64
        row["alert_id"] = sha(canonical(row))
        if fault == "late":
            alerts[0] = row
        else:
            alerts.append(row)
    result = evaluate(alerts)
    assert result["missed_critical_alerts"] == int(fault in {"missing", "late"})
    assert len(result["late_alerts"]) == int(fault == "late")
    assert len(result["duplicate_alerts"]) == int(fault == "duplicate")
    assert result["false_positives"] == int(fault in {"late", "duplicate", "false_positive"})
    assert result["paper_halt_required"] and not result["halt_clearance"]


@pytest.mark.parametrize("fault", ["policy", "truth", "alert_hash", "corrupt", "competing"])
def test_assurance_rejects(fault: str) -> None:
    alerts = [emitted(e) for e in events()]
    updates: dict[str, Any] = {}
    if fault == "policy":
        updates["monitoring_policy"] = ZERO
    elif fault == "truth":
        updates["expected_truth"] = ZERO
    elif fault == "alert_hash":
        updates["expected_alert_hash"] = ZERO
    elif fault == "corrupt":
        alerts[0]["severity"] = "INFO"
    else:
        alerts.append(alerts[0])
    with pytest.raises(ValueError):
        evaluate(alerts, **updates)


@pytest.mark.parametrize("fault", ["missing", "truncated", "corrupt", "reordered", "changed_event"])
def test_alert_ledger_faults(tmp_path: Path, fault: str) -> None:
    ledger = AlertLedger(tmp_path / "alerts")
    head = ZERO
    for event in events()[:2]:
        head = ledger.accept(event, head)
    first = ledger.directory / "records/000000.json"
    last = ledger.directory / "records/000001.json"
    if fault == "missing":
        first.unlink()
    elif fault == "truncated":
        last.unlink()
    elif fault == "corrupt":
        first.write_bytes(b"{")
    elif fault == "reordered":
        one, two = first.read_bytes(), last.read_bytes()
        first.write_bytes(two)
        last.write_bytes(one)
    else:
        altered = events()[0].model_copy(update={"incident_evidence": ZERO})
        with pytest.raises(ValueError):
            ledger.accept(altered, head)
        return
    with pytest.raises(ValueError):
        ledger.snapshot(head)


def test_alert_restart_idempotency_and_halt(tmp_path: Path) -> None:
    ledger = AlertLedger(tmp_path / "alerts")
    head = ledger.accept(events()[0], ZERO)
    restarted = AlertLedger(ledger.directory)
    assert restarted.accept(events()[0], head) == head
    assert len(restarted.snapshot(head)) == 1
    assert restarted.snapshot(head)[0]["paper_halt"] is True


@pytest.mark.parametrize(
    "fault",
    [
        "missing",
        "expired",
        "future",
        "run",
        "incident",
        "action",
        "scope",
        "evidence",
        "head",
        "version",
        "corrupt",
        "untrusted",
    ],
)
def test_invalid_approval(tmp_path: Path, fault: str) -> None:
    a, state, head, approval = setup_authority(tmp_path)
    fields = {
        "run": ("run_id", ZERO),
        "incident": ("incident_id", ZERO),
        "action": ("authorized_action", "CLEAR_HALT"),
        "scope": ("authorization_scope", "ANY"),
        "evidence": ("evidence_fingerprint", ZERO),
        "head": ("current_trusted_head_fingerprint", ZERO),
        "version": ("version", "unknown"),
    }
    if fault in fields:
        key, value = fields[fault]
        approval[key] = value
        approval["approval_fingerprint"] = sha(
            canonical({k: v for k, v in approval.items() if k != "approval_fingerprint"})
        )
        a.trusted_approvals[approval["recovery_request_id"]] = approval["approval_fingerprint"]
    if fault == "corrupt":
        approval["approval_fingerprint"] = ZERO
    if fault == "untrusted":
        a.trusted_approvals = {}
    now = AT + timedelta(hours=2) if fault == "expired" else AT
    if fault == "future":
        now = AT - timedelta(hours=2)
    with pytest.raises(ValueError):
        recover(a, state, head, None if fault == "missing" else approval, now=now)
    assert not list(a.local.glob("*.json"))
    assert a.verify(head, state)["status"] == "READY"


def test_consume_before_mutation_and_replay(tmp_path: Path) -> None:
    a, state, head, approval = setup_authority(tmp_path)

    def action(consumed: Witness) -> Witness:
        rows, _ = chain(a.witness / "records")
        assert rows[-1]["status"] == "CONSUMED" and len(list(a.local.glob("*.json"))) == 1
        return completed(consumed)

    result = recover(a, state, head, approval, action=action)
    new = Witness.model_validate(result["state"])
    restarted = Authority(a.local, a.witness, a.trusted_approvals)
    assert restarted.verify(result["head"], new)["status"] == "COMPLETED"
    with pytest.raises(ValueError):
        recover(restarted, new, result["head"], approval)
    assert new.paper_halt and not result["halt_clearance"]


@pytest.mark.parametrize(
    "crash",
    [
        "before_consumption",
        "after_local_consumption",
        "after_consumption",
        "during_recovery",
        "ambiguous_acknowledgment",
    ],
)
def test_crash_consumption(tmp_path: Path, crash: str) -> None:
    a, state, head, approval = setup_authority(tmp_path)
    with pytest.raises(InterruptedError):
        recover(a, state, head, approval, crash=crash)
    restarted = Authority(a.local, a.witness, a.trusted_approvals)
    if crash == "before_consumption":
        assert recover(restarted, state, head, approval)["consumed"]
    else:
        rows, latest = chain(a.witness / "records")
        with pytest.raises(ValueError):
            recover(restarted, Witness.model_validate(rows[-1]["state"]), latest, approval)


def test_competing_consumers(tmp_path: Path) -> None:
    a, state, head, approval = setup_authority(tmp_path)

    def attempt(_: int) -> bool:
        try:
            recover(a, state, head, approval)
            return True
        except ValueError:
            return False

    with ThreadPoolExecutor(max_workers=2) as pool:
        assert sorted(pool.map(attempt, range(2))) == [False, True]
    assert len(list(a.local.glob("*.json"))) == 1


@pytest.mark.parametrize("fault", ["journal", "halt", "epoch", "approval", "missing_consumption"])
def test_surviving_witness_rejects_rollback(tmp_path: Path, fault: str) -> None:
    a, state, head, approval = setup_authority(tmp_path)
    result = recover(a, state, head, approval)
    new = Witness.model_validate(result["state"])
    if fault == "approval":
        with pytest.raises(ValueError):
            recover(a, state, head, approval)
        return
    if fault == "missing_consumption":
        next(a.local.glob("*.json")).unlink()
    else:
        updates: dict[str, Any] = {
            "journal": {"journal_head": state.journal_head, "highest_event_sequence": 0},
            "halt": {"paper_halt": False},
            "epoch": {"authority_epoch": 0},
        }
        new = new.model_copy(update=updates[fault])
    with pytest.raises(ValueError):
        a.verify(result["head"], new)


def test_complete_authority_rollback_counterexample(tmp_path: Path) -> None:
    a, state, head, approval = setup_authority(tmp_path / "original")
    shutil.copytree(tmp_path / "original", tmp_path / "rolled_back")
    first = recover(a, state, head, approval)
    restored = Authority(
        tmp_path / "rolled_back/local", tmp_path / "rolled_back/witness", a.trusted_approvals
    )
    second = recover(restored, state, head, approval)
    assert first == second  # All witnesses and authority restored: reuse is possible.


def test_halt_clearance_rejected(tmp_path: Path) -> None:
    a, state, head, approval = setup_authority(tmp_path)
    with pytest.raises(ValueError, match="HALT_CLEARANCE_FORBIDDEN"):
        recover(
            a,
            state,
            head,
            approval,
            action=lambda s: completed(s).model_copy(update={"paper_halt": False}),
        )
    assert chain(a.witness / "records")[0][-1]["status"] == "CONSUMED"


@pytest.mark.parametrize(
    "name",
    ["crash_after_reservation", "crash_after_fill", "crash_during_eod", "missing_eod_liquidity"],
)
def test_existing_accounting_contracts(tmp_path: Path, name: str) -> None:
    result = experiment(name, tmp_path / name)
    assert result["test_outcome"] == "PASS"


def test_cross_session_halt_and_acknowledgment(tmp_path: Path) -> None:
    result = reliability.continuity(halted_multi(), tmp_path / "multi")
    assert result


def test_no_broker_dependency() -> None:
    from strategy_engine.phase1426 import alerts, authority

    for module in (alerts, authority):
        source = inspect.getsource(module)
        assert all(
            word not in source for word in ("import requests", "import socket", "kiteconnect")
        )


@pytest.mark.parametrize("crash", ["before_record", "after_record"])
def test_alert_crash_no_silent_adoption(tmp_path: Path, crash: str) -> None:
    ledger = AlertLedger(tmp_path / "alerts")
    with pytest.raises(InterruptedError):
        ledger.accept(events()[0], ZERO, crash=crash)
    if crash == "before_record":
        assert ledger.snapshot(ZERO) == []
    else:
        with pytest.raises(ValueError, match="TRUSTED_HEAD_MISMATCH"):
            AlertLedger(ledger.directory).snapshot(ZERO)


def test_fresh_process_restart(tmp_path: Path) -> None:
    a, state, head, approval = setup_authority(tmp_path)
    result = recover(a, state, head, approval)
    payload = dict(
        local=str(a.local), witness=str(a.witness), trust=a.trusted_approvals, result=result
    )
    data = tmp_path / "restart-input.json"
    data.write_bytes(canonical(payload))
    code = """import json,sys
from pathlib import Path
from strategy_engine.phase1426.authority import Authority,Witness
d=json.loads(Path(sys.argv[1]).read_bytes());r=d['result']
a=Authority(Path(d['local']),Path(d['witness']),d['trust'])
assert a.verify(r['head'],Witness.model_validate(r['state']))['state']['paper_halt']
print('HALT_PERSISTED')
"""
    process = subprocess.run(
        [sys.executable, "-B", "-c", code, str(data)],
        capture_output=True,
        env=os.environ | {"PYTHONDONTWRITEBYTECODE": "1"},
    )
    assert process.returncode == 0 and process.stdout.strip() == b"HALT_PERSISTED"


def scheduler_recovery(directory: Path) -> dict[str, Any]:
    args = session_fixture()
    session = directory / "session"
    with pytest.raises(InterruptedError):
        scheduler.run(*args, session, crash="FILLED")
    before = reliability.inventory(session / "journal")
    observed = Witness(
        run_id=sha(canonical([r.model_dump(mode="json") for r in args[0]])),
        session_sequence=0,
        highest_event_sequence=len(before),
        journal_head=sha(canonical(before)),
        authority_epoch=0,
        paper_halt=True,
        last_accepted_recovery_action="NONE",
    )
    a, state, head, approval = setup_authority(directory / "authority", observed)
    checked: dict[str, Any] = {}

    def action(consumed: Witness) -> Witness:
        assert chain(a.witness / "records")[0][-1]["status"] == "CONSUMED"
        assert len(list(a.local.glob("*.json"))) == 1
        assert consumed.journal_head == sha(canonical(reliability.inventory(session / "journal")))
        result = scheduler.run(*args, session, resume=True)
        checked.update(
            reliability.session_check(session, result, args[1].base.capital or Decimal(0))
        )
        assert scheduler.run(*args, session, verify_only=True) == result
        return consumed.model_copy(
            update={
                "highest_event_sequence": len(reliability.inventory(session / "journal")),
                "journal_head": sha(canonical(reliability.inventory(session / "journal"))),
                "last_accepted_recovery_action": "RESUME_SYNTHETIC_REPLAY",
            }
        )

    result = recover(a, state, head, approval, action=action)
    with pytest.raises(ValueError):
        recover(a, Witness.model_validate(result["state"]), result["head"], approval, action=action)
    assert checked["counts"] == {"RESERVED": 1, "FILLED": 1, "CLOSED": 1, "CANCELLED": 0}
    return dict(
        authority=result,
        accounting=checked,
        original_journal=before,
        note="Synthetic callback adapter; existing scheduler replay contracts remain unchanged",
    )


def test_authorized_scheduler_replay(tmp_path: Path) -> None:
    assert scheduler_recovery(tmp_path)["accounting"]["invariants"] == "PASS"


def test_one_alert_cannot_satisfy_two_incidents() -> None:
    truth = monitoring_fixture()["ground_truth"]
    one = dict(truth["incidents"][0])
    two = one | {"incident_id": "e" * 64}
    truth["incidents"] = [one, two]
    truth["fingerprint"] = sha(canonical({k: v for k, v in truth.items() if k != "fingerprint"}))
    event = events()[0]
    result = evaluate(
        [emitted(event)],
        independent_truth=truth,
        expected_truth=truth["fingerprint"],
        events=(event,),
    )
    assert result["timely_monitored_alerts"] == 1 and result["missed_critical_alerts"] == 1


def build_reports(directory: Path) -> dict[str, Any]:
    ledger = AlertLedger(directory / "alerts")
    head = ZERO
    accepted = events()
    for event in accepted:
        head = ledger.accept(event, head)
    alerts = [r["alert"] for r in ledger.snapshot(head)]
    a, state, witness_head, approval = setup_authority(directory / "authority")
    recovery = recover(a, state, witness_head, approval)
    decisions = []
    for fault in ("journal", "halt", "epoch", "approval", "missing_consumption"):
        test_surviving_witness_rejects_rollback(directory / ("rollback-" + fault), fault)
        decisions.append({"scenario": fault, "result": "REJECTED_WITH_SURVIVING_WITNESS"})
    for fault in (
        "missing",
        "expired",
        "future",
        "run",
        "incident",
        "action",
        "scope",
        "evidence",
        "head",
        "version",
        "corrupt",
        "untrusted",
    ):
        test_invalid_approval(directory / ("approval-" + fault), fault)
        decisions.append({"scenario": fault, "result": "APPROVAL_REJECTED_BEFORE_MUTATION"})
    test_complete_authority_rollback_counterexample(directory / "complete-rollback")
    test_competing_consumers(directory / "competing")
    test_consume_before_mutation_and_replay(directory / "one-use")
    report = dict(
        scope="SYNTHETIC_FIXTURE",
        historical=monitoring_fixture()["monitoring"],
        accepted_events=[e.model_dump(mode="json") for e in accepted],
        alerts=alerts,
        alert_head=head,
        assurance=evaluate(alerts),
        missing_alert=evaluate(alerts[:-1]),
        recovery=recovery,
        recovery_decisions=decisions,
        competing_consumers={"accepted": 1, "rejected": 1},
        replay_rejection="PASS",
        accounting=scheduler_recovery(directory / "scheduler-recovery"),
        halted_continuity=reliability.continuity(halted_multi(), directory / "halted-multi"),
        normal_continuity=reliability.continuity(multi_fixture(), directory / "normal-multi"),
        authority_policy="Phase1426Authority.v1",
        complete_rollback="REUSE_POSSIBLE_WHEN_ALL_AUTHORITY_AND_WITNESSES_ROLL_BACK",
        independent_custody=False,
        operational="NO_GO",
        genuine_qualified=0,
        gate_a="NOT_AUTHORIZED",
        gate_b="NOT_AUTHORIZED",
        real_kite_requests=0,
    )
    return report | {"fingerprint": sha(canonical(report))}


def test_deterministic_reports(tmp_path: Path) -> None:
    assert build_reports(tmp_path / "one") == build_reports(tmp_path / "two")
