"""Synthetic multi-session continuity, health and recovery contracts."""

import json
from datetime import timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any

import pytest
from test_phase1417 import authorization
from test_phase1418 import control, policy, request
from test_phase1418b import changed
from test_phase1419 import commands

from strategy_engine.backtest.dataset import digest
from strategy_engine.phase14.engine import FIVE
from strategy_engine.phase1418.portfolio import Control, Policy
from strategy_engine.phase1419.scheduler import Calendar, Configuration
from strategy_engine.phase1420 import multi
from strategy_engine.phase1420.multi import (
    ZERO,
    Day,
    Limits,
    Rejected,
    acknowledge,
    acknowledgments,
    head,
    run,
)


def fixture(
    offsets: tuple[int, ...] = (0, 1), hours: int = 0
) -> tuple[tuple[Day, ...], Policy, Control, Calendar, Limits]:
    p = policy()
    days = []
    sessions = []
    for offset in offsets:
        r = request(p, strategy=1 if hours else 5)
        s = r.data.series
        delta = timedelta(days=offset, hours=hours)
        session = s.sessions[0].model_copy(
            update={"start": s.sessions[0].start + delta, "end": s.sessions[0].end + delta}
        )
        series = s.model_copy(
            update={
                "bars": tuple(b.model_copy(update={"start": b.start + delta}) for b in s.bars),
                "sessions": (session,),
                "from_inclusive": s.from_inclusive + delta,
                "to_exclusive": s.to_exclusive + delta,
                "as_of": s.as_of + delta,
            }
        )
        data = r.data.model_copy(
            update={
                "series": series,
                "dataset_sha256": digest(series.model_dump_json()),
                "acquired_at": series.as_of,
            }
        )
        r = r.model_copy(
            update={"data": data, "authorization": authorization(r.qualification, data, p.base)}
        )
        config = Configuration(
            name="synthetic-multi-day",
            trading_date=session.start.date(),
            cutoff=session.end - FIVE,
            authorization=control(p).authorization_evidence,
            prior_session_resolved=True,
        )
        days.append(Day(requests=(r,), config=config, commands=commands((r,))))
        sessions.append(session)
    cal = Calendar(
        version="SYNTHETIC_MULTI.v1",
        source_hash=days[0].requests[0].data.series.calendar_hash,
        sessions=tuple(sessions),
    )
    return (
        tuple(days),
        p,
        control(p),
        cal,
        Limits(cumulative_drawdown=Decimal(100), daily_reset_authorization="e" * 64),
    )


def test_two_sessions_cash_fees_and_readonly_replay(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    args = fixture()
    result = run(*args, tmp_path)
    assert result["complete"] and len(result["sessions"]) == 2
    a, b = result["sessions"]
    assert b["starting_state"] == a["closing_state"]
    assert Decimal(result["state"]["cash"]) == Decimal(10000) + Decimal(
        result["state"]["cumulative_net"]
    )
    assert Decimal(result["state"]["cash"]) == Decimal("9997.83041920")
    assert Decimal(result["state"]["cumulative_fees"]) == Decimal("2.16958080")
    assert result["state"]["high_water"] == "10000"
    assert Decimal(result["state"]["maximum_drawdown"]) == Decimal("2.16958080")
    assert result["losing_sessions"] == 2 and result["genuine_qualified"] == 0

    def forbidden(*args: Any, **kwargs: Any) -> None:
        raise AssertionError("completed session executed again")

    monkeypatch.setattr(multi, "execute", forbidden)
    assert run(*args, tmp_path, verify_only=True, expected_head=result["head"]) == result


@pytest.mark.parametrize("offsets,hours", [((4, 7), 0), ((0, 2), 0), ((0, 1), 8)])
def test_weekend_holiday_special_sessions(
    tmp_path: Path, offsets: tuple[int, ...], hours: int
) -> None:
    args = fixture(offsets, hours)
    result = run(*args, tmp_path)
    assert result["complete"] and len(result["sessions"]) == 2


@pytest.mark.parametrize(
    "fault", ["missing", "duplicate", "order", "risk", "execution", "limits", "reset"]
)
def test_sequence_and_readiness(tmp_path: Path, fault: str) -> None:
    days, p, ctl, cal, limits = fixture()
    if fault == "missing":
        r = run(days[:1], p, ctl, cal, limits, tmp_path)
        assert not r["complete"] and r["state"]["halted"]
        assert r["alerts"][-1]["reason"] == "SESSION_MISSING"
        return
    if fault == "duplicate":
        days = (days[0], days[0])
    elif fault == "order":
        days = tuple(reversed(days))
    elif fault in ("risk", "execution"):
        days = (
            days[0].model_copy(
                update={"config": days[0].config.model_copy(update={fault + "_hash": "0" * 64})}
            ),
            days[1],
        )
        r = run(days, p, ctl, cal, limits, tmp_path)
        assert r["state"]["halted"] and len(r["sessions"]) == 1
        return
    elif fault == "limits":
        limits = limits.model_copy(update={"cumulative_drawdown": None})
    else:
        limits = limits.model_copy(update={"daily_reset_authorization": None})
    with pytest.raises(Rejected) as e:
        run(days, p, ctl, cal, limits, tmp_path)
    assert e.value.alert["severity"] == "CRITICAL"


@pytest.mark.parametrize("fault", ["drawdown", "eod"])
def test_persistent_halt_and_acknowledgment(tmp_path: Path, fault: str) -> None:
    days, p, ctl, cal, limits = fixture()
    if fault == "drawdown":
        limits = limits.model_copy(update={"cumulative_drawdown": Decimal("0.5")})
    else:
        r = changed(days[0].requests[0], 5, volume=0)
        days = (days[0].model_copy(update={"requests": (r,), "commands": commands((r,))}), days[1])
    result = run(days, p, ctl, cal, limits, tmp_path)
    assert not result["complete"] and result["state"]["halted"] and len(result["sessions"]) == 1
    if fault == "eod":
        assert result["state"]["positions"]["SBIN"]["quantity"] == 10
    last = result["alerts"][-1]
    a = acknowledge(tmp_path, last["alert_id"], days[0].commands[-1].at, "a" * 64)
    assert a == acknowledge(tmp_path, last["alert_id"], days[0].commands[-1].at, "a" * 64)
    assert len(acknowledgments(tmp_path)) == 1 and not a["resume_authorized"]
    with pytest.raises(Rejected, match="TERMINAL"):
        run(days, p, ctl, cal, limits, tmp_path, resume=True, expected_head=result["head"])


@pytest.mark.parametrize(
    "crash", ["before_first", "before_record", "after_record", "during_carry", "after_completed"]
)
def test_crash_boundaries(tmp_path: Path, crash: str, monkeypatch: pytest.MonkeyPatch) -> None:
    args = fixture()
    with pytest.raises(InterruptedError):
        run(*args, tmp_path, crash=crash)
    original = multi.execute
    calls = []

    def counted(day: Day, *a: Any, **kw: Any) -> dict[str, Any]:
        calls.append(day.config.trading_date)
        return original(day, *a, **kw)

    monkeypatch.setattr(multi, "execute", counted)
    r = run(*args, tmp_path, resume=True, expected_head=head(tmp_path))
    assert r["complete"] and len(calls) == (2 if crash == "before_first" else 1)


@pytest.mark.parametrize(
    "fault",
    [
        "record",
        "outcome",
        "report",
        "alert",
        "calendar",
        "policy",
        "execution",
        "dataset",
        "software",
        "rollback",
    ],
)
def test_replay_rejects_corruption(
    tmp_path: Path, fault: str, monkeypatch: pytest.MonkeyPatch
) -> None:
    days, p, ctl, cal, limits = fixture()
    r = run(days, p, ctl, cal, limits, tmp_path)
    if fault == "record":
        (tmp_path / "records/0000.json").unlink()
    elif fault == "outcome":
        (tmp_path / "outcomes/0000.json").write_bytes(b"{}")
    elif fault == "report":
        (tmp_path / "summary.json").unlink()
    elif fault == "alert":
        summary = json.loads((tmp_path / "summary.json").read_bytes())
        summary["alerts"].pop()
        (tmp_path / "summary.json").write_text(json.dumps(summary), encoding="utf-8")
    elif fault == "calendar":
        cal = cal.model_copy(update={"version": "other"})
    elif fault == "policy":
        ctl = ctl.model_copy(update={"policy_hash": "0" * 64})
    elif fault == "execution":
        days = (
            days[0].model_copy(
                update={"config": days[0].config.model_copy(update={"execution_hash": "0" * 64})}
            ),
            days[1],
        )
    elif fault == "dataset":
        days = (
            days[0].model_copy(update={"requests": (changed(days[0].requests[0], 4, volume=500),)}),
            days[1],
        )
    elif fault == "software":
        monkeypatch.setattr(multi, "software", lambda: {"changed": "0" * 64})
    else:
        tmp_path = tmp_path / "empty-rollback"
    with pytest.raises(Rejected):
        run(days, p, ctl, cal, limits, tmp_path, verify_only=True, expected_head=r["head"])


def test_daily_reset_does_not_reset_cumulative_loss(tmp_path: Path) -> None:
    args = fixture()
    r = run(*args, tmp_path)
    assert r["daily"][0]["realized"] == r["daily"][1]["realized"]
    assert Decimal(r["state"]["cumulative_net"]) == 2 * Decimal(r["daily"][0]["realized"])
    assert len({a["alert_id"] for a in r["alerts"]}) == len(r["alerts"])
    assert all(a["severity"] == "INFO" for a in r["alerts"])
    assert head(tmp_path) != ZERO


def test_no_broker_or_notification_dependency() -> None:
    import ast
    import inspect

    tree = ast.parse(inspect.getsource(multi))
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            assert not any(
                x in (node.module or "").lower()
                for x in ("kite", "requests", "http", "socket", "slack", "smtp")
            )


def test_positive_high_water_and_missing_report(tmp_path: Path) -> None:
    days, p, ctl, cal, limits = fixture()
    r = changed(
        days[0].requests[0],
        5,
        open=Decimal(110),
        high=Decimal(111),
        low=Decimal(109),
        close=Decimal(110),
    )
    days = (days[0].model_copy(update={"requests": (r,), "commands": commands((r,))}), days[1])
    result = run(days, p, ctl, cal, limits, tmp_path)
    assert result["complete"] and Decimal(result["state"]["high_water"]) > 10000
    assert Decimal(result["state"]["maximum_drawdown"]) > 0 and result["winning_sessions"] == 1
    assert len(result["reports"]) == 12
    (tmp_path / "alerts.json").unlink()
    with pytest.raises(Rejected, match="REPORT"):
        run(days, p, ctl, cal, limits, tmp_path, verify_only=True, expected_head=result["head"])


def test_ack_anchor_detects_missing_history(tmp_path: Path) -> None:
    from strategy_engine.phase1416.imports import sha

    args = fixture()
    r = run(*args, tmp_path)
    acknowledge(tmp_path, r["alerts"][-1]["alert_id"], args[0][-1].commands[-1].at, "a" * 64)
    h = sha((tmp_path / "acks/0000.json").read_bytes())
    assert multi.alert_view(tmp_path, h)[-1]["acknowledgment"] == "ACKNOWLEDGED"
    (tmp_path / "acks/0000.json").unlink()
    with pytest.raises(ValueError, match="ACK_EXTERNAL_HEAD"):
        multi.alert_view(tmp_path, h)


@pytest.mark.parametrize("severity", ["INFO", "WARNING", "ERROR", "CRITICAL"])
def test_alert_severity_and_stable_identity(severity: str) -> None:
    a = multi.alert("a" * 64, "2026-10-05", "2026-10-05T00:00:00+00:00", "TEST", "b" * 64, severity)
    assert a == multi.alert(
        "a" * 64, "2026-10-05", "2026-10-05T00:00:00+00:00", "TEST", "b" * 64, severity
    )
    assert a["severity"] == severity and a["acknowledgment"] == "UNACKNOWLEDGED"


def test_entry_fee_halts_competing_same_tick_entry(tmp_path: Path) -> None:
    days, p, ctl, cal, limits = fixture()
    rs = (days[0].requests[0], request(p, "TCS"))
    days = (days[0].model_copy(update={"requests": rs, "commands": commands(rs)}), days[1])
    limits = limits.model_copy(update={"cumulative_drawdown": Decimal("0.1")})
    result = run(days, p, ctl, cal, limits, tmp_path)
    outcome = multi.unpack(tmp_path / "outcomes/0000.json")
    events = [
        e for row in outcome["reports"]["session-events.json"] for e in row["portfolio_events"]
    ]
    assert result["state"]["halted"] and len(result["sessions"]) == 1
    assert len([e for e in events if e["state"] == "FILLED"]) == 1
    assert any(e["state"] == "CANCELLED" for e in events)


def test_shifted_fixture_roundtrip_validates_provenance() -> None:
    for days in (fixture()[0], fixture((4, 7))[0], fixture(hours=8)[0]):
        for day in days:
            assert Day.model_validate_json(day.model_dump_json()) == day
