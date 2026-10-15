"""Synthetic explicit-clock scheduler tests; never broker operations."""

import json
from datetime import UTC, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any

import pytest
from test_phase1418 import control, policy, request
from test_phase1418b import changed

from strategy_engine.phase14.engine import FIVE
from strategy_engine.phase1418.portfolio import Control, Policy, Request
from strategy_engine.phase1419.scheduler import (
    EXECUTION_HASH,
    RISK_HASH,
    Calendar,
    Command,
    Completed,
    Configuration,
    Engine,
    Opening,
    run,
)


def fixture(
    symbols: tuple[str, ...] = ("SBIN",), strategy: int = 5
) -> tuple[tuple[Request, ...], Policy, Control, Calendar, Configuration, tuple[Command, ...]]:
    p = policy()
    rs = tuple(request(p, s, strategy) for s in symbols)
    series = rs[0].data.series
    cal = Calendar(
        version="SYNTHETIC.v1", source_hash=series.calendar_hash, sessions=series.sessions
    )
    cfg = Configuration(
        name="synthetic-day",
        trading_date=series.sessions[0].start.date(),
        cutoff=series.sessions[0].end - FIVE,
        authorization=control(p).authorization_evidence,
        prior_session_resolved=True,
    )
    return rs, p, control(p), cal, cfg, commands(rs)


def commands(rs: tuple[Request, ...]) -> tuple[Command, ...]:
    unique = {r.data.series.symbol: r for r in rs}
    session = rs[0].data.series.sessions[0]
    events = [
        Command(kind="preflight", at=session.start - FIVE),
        Command(kind="open", at=session.start),
    ]
    for i, bar in enumerate(rs[0].data.series.bars):
        last = bar.end == session.end
        events.append(
            Command(
                kind="close" if last else "step",
                at=bar.end,
                completed=tuple(
                    Completed(
                        symbol=s,
                        instrument_token=r.data.series.instrument_token,
                        bar=r.data.series.bars[i],
                    )
                    for s, r in unique.items()
                ),
                openings=()
                if last
                else tuple(
                    Opening(
                        symbol=s,
                        instrument_token=r.data.series.instrument_token,
                        price=r.data.series.bars[i + 1].open,
                    )
                    for s, r in unique.items()
                ),
            )
        )
    return tuple(events)


def events(path: Path) -> list[dict[str, Any]]:
    return [
        e
        for row in json.loads((path / "session-events.json").read_bytes())
        for e in row["portfolio_events"]
    ]


def test_complete_session_accounting_and_replay(tmp_path: Path) -> None:
    args = fixture(("TCS", "SBIN"))
    result = run(*args, tmp_path)
    assert result["complete"] and result["state"] == "COMPLETED"
    assert result["risk_hash"] == RISK_HASH and result["execution_hash"] == EXECUTION_HASH
    assert not result["account"]["positions"] and result["account"]["reserved"] == "0"
    assert Decimal(result["account"]["cash"]) == Decimal(10000) + Decimal(
        result["account"]["realized"]
    )
    fills = [e for e in events(tmp_path) if e["state"] == "FILLED"]
    assert len(fills) == 2 and all(
        e["details"]["quantity"] == 10 and e["details"]["price"] == "102" for e in fills
    )
    assert run(*args, tmp_path, verify_only=True) == result
    with pytest.raises(ValueError, match="CANNOT_REOPEN"):
        run(*args, tmp_path)
    assert len(result["reports"]) == 18 and result["genuine_qualified"] == 0


@pytest.mark.parametrize(
    "fault",
    [
        "risk_version",
        "risk_hash",
        "execution_version",
        "execution_hash",
        "prior",
        "authorization",
        "qualification",
        "calendar",
        "holiday",
        "capital",
        "halt",
    ],
)
def test_readiness_fail_closed(tmp_path: Path, fault: str) -> None:
    rs, p, ctl, cal, cfg, cs = fixture()
    if fault in {"risk_version", "execution_version"}:
        cfg = cfg.model_copy(update={fault: "legacy"})
    elif fault in {"risk_hash", "execution_hash"}:
        cfg = cfg.model_copy(update={fault: "0" * 64})
    elif fault == "prior":
        cfg = cfg.model_copy(update={"prior_session_resolved": False})
    elif fault == "authorization":
        ctl = ctl.model_copy(update={"enabled": False})
    elif fault == "qualification":
        q = rs[0].qualification.model_copy(update={"software_hash": "0" * 64})
        rs = (rs[0].model_copy(update={"qualification": q}),)
    elif fault == "calendar":
        cal = cal.model_copy(update={"source_hash": "0" * 64})
    elif fault == "holiday":
        cal = cal.model_copy(update={"sessions": (), "holidays": (cfg.trading_date,)})
    elif fault == "capital":
        p = p.model_copy(update={"base": p.base.model_copy(update={"capital": None})})
    else:
        ctl = ctl.model_copy(update={"paper_halt": True})
    result = run(rs, p, ctl, cal, cfg, cs[:1], tmp_path)
    assert result["state"] == "PAPER_HALTED" and not result["complete"]
    assert not result["account"]["positions"]


@pytest.mark.parametrize(
    "fault",
    [
        "incomplete",
        "duplicate",
        "rollback",
        "missing",
        "identity",
        "price",
        "early_open",
        "no_preflight",
    ],
)
def test_invalid_clock_and_observations(tmp_path: Path, fault: str) -> None:
    rs, p, ctl, cal, cfg, original = fixture()
    cs = list(original)
    if fault == "incomplete":
        cs[2] = cs[2].model_copy(
            update={"completed": (cs[2].completed[0].model_copy(update={"complete": False}),)}
        )
    elif fault == "duplicate":
        cs[2] = cs[2].model_copy(update={"completed": cs[2].completed * 2})
    elif fault == "rollback":
        cs[2] = cs[2].model_copy(update={"at": cs[1].at - FIVE})
    elif fault == "missing":
        cs[2] = cs[2].model_copy(update={"completed": ()})
    elif fault == "identity":
        cs[2] = cs[2].model_copy(
            update={
                "completed": (cs[2].completed[0].model_copy(update={"instrument_token": "999"}),)
            }
        )
    elif fault == "price":
        cs[2] = cs[2].model_copy(
            update={"openings": (cs[2].openings[0].model_copy(update={"price": Decimal(1)}),)}
        )
    elif fault == "early_open":
        cs[1] = cs[1].model_copy(update={"at": cs[1].at - timedelta(minutes=1)})
    else:
        cs = cs[1:]
    result = run(rs, p, ctl, cal, cfg, tuple(cs), tmp_path)
    assert result["state"] == "PAPER_HALTED" and not result["complete"]
    assert not any(e["state"] == "FILLED" for e in events(tmp_path))


def test_cutoff_and_all_six_strategies(tmp_path: Path) -> None:
    rs, p, ctl, cal, cfg, _ = fixture()
    rs = tuple(request(p, strategy=i) for i in range(6))
    cfg = cfg.model_copy(update={"cutoff": rs[0].data.series.bars[3].end})
    result = run(rs, p, ctl, cal, cfg, commands(rs), tmp_path)
    assert result["complete"] and not any(e["state"] == "FILLED" for e in events(tmp_path))
    decisions = json.loads((tmp_path / "session-signals.json").read_bytes())
    assert any(d["rejection"] == "ENTRY_CUTOFF" for d in decisions)
    assert {r.definition.identifier for r in rs} == {f"S{i:02}" for i in range(1, 7)}


def test_deterministic_order_and_conflict(tmp_path: Path) -> None:
    rs, p, ctl, cal, cfg, _ = fixture(("TCS", "SBIN"))
    rs = rs + (request(p, "SBIN", 4),)
    left = run(rs, p, ctl, cal, cfg, commands(rs), tmp_path / "a")
    right = run(tuple(reversed(rs)), p, ctl, cal, cfg, commands(rs), tmp_path / "b")
    assert left == right
    assert max(len(e["account"]["positions"]) for e in events(tmp_path / "a")) <= 2


@pytest.mark.parametrize("volume", [0, 100, 10000])
def test_future_volume_does_not_resize_earlier_fill(tmp_path: Path, volume: int) -> None:
    rs, p, ctl, cal, cfg, _ = fixture()
    rs = (changed(rs[0], 4, volume=volume),)
    result = run(rs, p, ctl, cal, cfg, commands(rs), tmp_path)
    fill = next(e for e in events(tmp_path) if e["state"] == "FILLED")
    assert fill["timestamp"] == rs[0].data.series.bars[4].start.isoformat()
    assert fill["details"]["quantity"] == 10 and fill["details"]["price"] == "102"
    assert result["complete"]


@pytest.mark.parametrize(
    "kind", ["stop", "target", "collision", "missing_liquidity", "missing_eod"]
)
def test_exits_and_halt(tmp_path: Path, kind: str) -> None:
    rs, p, ctl, cal, cfg, _ = fixture()
    updates = (
        {"low": Decimal(90)}
        if kind == "stop"
        else {"high": Decimal(110)}
        if kind == "target"
        else {"low": Decimal(90), "high": Decimal(110)}
        if kind == "collision"
        else {"volume": 0}
        if kind == "missing_liquidity"
        else {}
    )
    rs = (changed(rs[0], 5, **updates),)
    cs = commands(rs)
    if kind == "missing_eod":
        cs = cs[:-1] + (cs[-1].model_copy(update={"completed": ()}),)
    result = run(rs, p, ctl, cal, cfg, cs, tmp_path)
    if kind.startswith("missing"):
        assert (
            result["state"] == "PAPER_HALTED"
            and result["account"]["positions"]["SBIN"]["quantity"] == 10
        )
        assert not any(e["state"] == "CLOSED" for e in events(tmp_path))
        with pytest.raises(ValueError, match="CANNOT_REOPEN"):
            run(rs, p, ctl, cal, cfg, cs, tmp_path, resume=True)
    else:
        close = next(e for e in events(tmp_path) if e["state"] == "CLOSED")
        assert close["details"]["reason"] == ("TARGET" if kind == "target" else "STOP")
        assert close["timestamp"] == cs[-1].at.isoformat() and result["complete"]


@pytest.mark.parametrize(
    "crash",
    ["CREATED", "READY", "RUNNING", "RESERVED", "FILLED", "EOD_LIQUIDATION", "EXIT_PENDING"],
)
def test_crash_restart_atomic_commands(tmp_path: Path, crash: str) -> None:
    args = fixture()
    with pytest.raises(InterruptedError):
        run(*args, tmp_path, crash=crash)
    result = run(*args, tmp_path, resume=True)
    assert result["complete"] and len([e for e in events(tmp_path) if e["state"] == "FILLED"]) == 1
    assert run(*args, tmp_path, verify_only=True) == result


@pytest.mark.parametrize(
    "fault",
    [
        "journal",
        "missing_event",
        "report",
        "calendar",
        "dataset",
        "policy",
        "qualification",
        "execution",
        "owner",
    ],
)
def test_replay_rejects_corruption_and_binding(tmp_path: Path, fault: str) -> None:
    rs, p, ctl, cal, cfg, cs = fixture()
    run(rs, p, ctl, cal, cfg, cs, tmp_path)
    if fault == "journal":
        (tmp_path / "journal/000002.json").write_bytes(b"{}")
    elif fault == "missing_event":
        (tmp_path / "journal/000002.json").unlink()
    elif fault == "report":
        (tmp_path / "positions.json").unlink()
    elif fault == "calendar":
        cal = cal.model_copy(update={"version": "changed"})
    elif fault == "dataset":
        rs = (changed(rs[0], 5, volume=3000),)
    elif fault == "policy":
        cfg = cfg.model_copy(update={"risk_hash": "0" * 64})
    elif fault == "qualification":
        rs = (
            rs[0].model_copy(
                update={
                    "qualification": rs[0].qualification.model_copy(update={"run_id": "0" * 64})
                }
            ),
        )
    elif fault == "execution":
        cfg = cfg.model_copy(update={"execution_version": "old"})
    else:
        (tmp_path / "session.lock").write_bytes(b"owner")
    with pytest.raises((ValueError, KeyError)):
        run(rs, p, ctl, cal, cfg, cs, tmp_path, verify_only=True)


def test_ist_utc_early_close_and_no_broker_imports() -> None:
    import ast
    import inspect

    from strategy_engine.phase1419 import scheduler

    args = fixture()
    engine = Engine(*args[:5])
    assert engine.session is not None
    assert engine.session.start.tzinfo == UTC
    assert (
        engine.session.start.astimezone(__import__("zoneinfo").ZoneInfo("Asia/Kolkata")).strftime(
            "%H:%M"
        )
        == "09:15"
    )
    assert engine.session.end - engine.session.start == 6 * FIVE
    tree = ast.parse(inspect.getsource(scheduler))
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            assert not any(
                x in (node.module or "").lower() for x in ("kite", "requests", "http", "socket")
            )


def test_incomplete_checkpoint_not_completed(tmp_path: Path) -> None:
    rs, p, ctl, cal, cfg, cs = fixture()
    partial = run(rs, p, ctl, cal, cfg, cs[:3], tmp_path)
    assert (
        partial["checkpointed"]
        and not partial["complete"]
        and not (tmp_path / "summary.json").exists()
    )
    assert run(rs, p, ctl, cal, cfg, cs, tmp_path, resume=True)["complete"]


def test_terminal_state_cannot_be_changed() -> None:
    args = fixture()
    engine = Engine(*args[:5])
    for c in args[5]:
        engine.apply(c)
    before = engine.output()
    with pytest.raises(ValueError, match="TERMINAL"):
        engine.apply(args[5][-1])
    assert engine.state == "COMPLETED" and engine.output() == before


def test_special_session_uses_declared_times(tmp_path: Path) -> None:
    from test_phase1417 import authorization

    from strategy_engine.backtest.dataset import digest

    rs, p, ctl, cal, cfg, _ = fixture(strategy=1)
    r = rs[0]
    delta = timedelta(hours=8)
    s = r.data.series
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
        update={"series": series, "dataset_sha256": digest(series.model_dump_json())}
    )
    r = r.model_copy(
        update={"data": data, "authorization": authorization(r.qualification, data, p.base)}
    )
    cal = cal.model_copy(update={"sessions": (session,), "version": "SYNTHETIC_SPECIAL.v1"})
    cfg = cfg.model_copy(update={"cutoff": session.end - FIVE})
    result = run((r,), p, ctl, cal, cfg, commands((r,)), tmp_path)
    assert result["complete"] and not result["account"]["positions"]
    rows = json.loads((tmp_path / "session-events.json").read_bytes())
    assert rows[1]["timestamp"] == session.start.isoformat()
    assert rows[-1]["timestamp"] == session.end.isoformat()


def test_missing_entry_opening_cancels_and_halts(tmp_path: Path) -> None:
    rs, p, ctl, cal, cfg, cs = fixture()
    cs = tuple(c.model_copy(update={"openings": ()}) if i == 5 else c for i, c in enumerate(cs))
    result = run(rs, p, ctl, cal, cfg, cs, tmp_path)
    assert result["state"] == "PAPER_HALTED" and result["account"]["reserved"] == "0"
    assert not any(e["state"] == "FILLED" for e in events(tmp_path))
    assert any(e["state"] == "CANCELLED" for e in events(tmp_path))


def test_future_price_range_does_not_change_entry(tmp_path: Path) -> None:
    rs, p, ctl, cal, cfg, cs = fixture()
    run(rs, p, ctl, cal, cfg, cs, tmp_path / "a")
    altered = (changed(rs[0], 4, high=Decimal(120), low=Decimal(80), close=Decimal(110)),)
    run(altered, p, ctl, cal, cfg, commands(altered), tmp_path / "b")
    a, b = [next(e for e in events(tmp_path / n) if e["state"] == "FILLED") for n in ("a", "b")]
    assert a["details"] == b["details"] and a["timestamp"] == b["timestamp"]


def test_duplicate_start_never_clears_halt(tmp_path: Path) -> None:
    rs, p, ctl, cal, cfg, cs = fixture()
    bad = cs[:2] + (cs[1].model_copy(update={"at": cs[1].at + timedelta(seconds=1)}),) + cs[2:]
    result = run(rs, p, ctl, cal, cfg, bad, tmp_path)
    assert result["state"] == "PAPER_HALTED" and not result["complete"]
    assert not any(e["state"] == "FILLED" for e in events(tmp_path))


def test_shared_history_budget_is_per_symbol(monkeypatch: pytest.MonkeyPatch) -> None:
    from strategy_engine.phase1419 import scheduler

    _, p, ctl, cal, cfg, _ = fixture()
    rs = tuple(request(p, strategy=i) for i in range(6))
    monkeypatch.setattr(scheduler, "MAX_BARS", 6)
    assert len(Engine(rs, p, ctl, cal, cfg).requests) == 6
    monkeypatch.setattr(scheduler, "MAX_BARS", 5)
    with pytest.raises(ValueError, match="RESOURCE_LIMIT"):
        Engine(rs, p, ctl, cal, cfg)
