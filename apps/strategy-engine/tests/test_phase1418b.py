"""Causal execution regressions use only synthetic fixtures."""

import json
from decimal import Decimal
from pathlib import Path
from typing import Any

import pytest
from test_phase1418 import control, journal, policy, request

from strategy_engine.backtest.dataset import digest
from strategy_engine.phase14.engine import FIVE
from strategy_engine.phase1415.execution import (
    EXECUTION_VERSION,
    decision_timing,
    execute,
    execution_fingerprint,
    liquidity_bar,
)
from strategy_engine.phase1418.portfolio import Request, run

D = Decimal


def changed(r: Request, index: int, **updates: Any) -> Request:
    bars = tuple(
        b.model_copy(update=updates) if i == index else b for i, b in enumerate(r.data.series.bars)
    )
    series = r.data.series.model_copy(update={"bars": bars})
    data = r.data.model_copy(
        update={"series": series, "dataset_sha256": digest(series.model_dump_json())}
    )
    return r.model_copy(
        update={
            "data": data,
            "authorization": r.authorization.model_copy(
                update={"dataset_hash": data.dataset_sha256}
            ),
        }
    )


def fills(path: Path) -> list[dict[str, Any]]:
    return [e for e in journal(path) if e["state"] == "FILLED"]


@pytest.mark.parametrize("volume", [0, 1, 100, 10000, 999999])
def test_exact_future_volume_invariance(tmp_path: Path, volume: int) -> None:
    p = policy()
    r = changed(request(p), 4, volume=volume)
    result = run((r,), p, control(p), tmp_path)
    f = fills(tmp_path)[0]
    assert f["timestamp"] == r.data.series.bars[4].start.isoformat()
    assert f["details"]["quantity"] == 10 and f["details"]["price"] == "102"
    assert r.data.series.bars[4].end == r.data.series.bars[4].start + FIVE
    timing = f["details"]["timing"]
    assert timing["liquidity_observed_at"] == timing["quantity_decided_at"] == f["timestamp"]
    assert timing["liquidity_candle_start"] == r.data.series.bars[3].start.isoformat()
    assert timing["execution_policy_fingerprint"] == execution_fingerprint()
    assert result["simulation_complete"] and result["account"]["reserved"] == "0"


@pytest.mark.parametrize("volume,expected", [(0, 0), (100, 1), (500, 5), (10000, 10)])
def test_completed_liquidity_sizes_fixed_order(tmp_path: Path, volume: int, expected: int) -> None:
    p = policy()
    r = changed(request(p), 3, volume=volume)
    run((r,), p, control(p), tmp_path)
    f = fills(tmp_path)
    assert (f[0]["details"]["quantity"] if f else 0) == expected
    if f:
        reserved = next(e for e in journal(tmp_path) if e["state"] == "RESERVED")
        assert reserved["details"]["quantity"] == f[0]["details"]["quantity"]
        assert reserved["timestamp"] == f[0]["timestamp"]


def test_timing_missing_stale_and_missing_volume() -> None:
    r = request(policy())
    series = r.data.series
    at = series.bars[4].start
    assert liquidity_bar(series, at) == series.bars[3]
    with pytest.raises(ValueError, match="MISSING"):
        liquidity_bar(series.model_copy(update={"bars": series.bars[:3]}), at)
    with pytest.raises(ValueError, match="SESSION"):
        liquidity_bar(series, series.sessions[0].start)
    bad = changed(r, 3, volume=None)
    with pytest.raises(ValueError):
        liquidity_bar(bad.data.series, at)
    t = decision_timing(series, at)
    assert t["signal_completed_at"] == t["order_created_at"] == t["fill_confirmed_at"]


@pytest.mark.parametrize(
    "updates,reason,price",
    [
        ({"low": D(90), "high": D(110)}, "STOP", D("99.96")),
        ({"open": D(90), "low": D(89), "close": D(90)}, "STOP", D(90)),
        ({"high": D(110)}, "TARGET", D("104.04")),
        ({"open": D(110), "high": D(111), "low": D(109), "close": D(110)}, "TARGET", D("104.04")),
        ({}, "EOD", D(102)),
    ],
)
def test_exits_confirm_only_at_completed_bar(
    tmp_path: Path, updates: dict[str, Any], reason: str, price: Decimal
) -> None:
    p = policy()
    r = changed(request(p), 5, **updates)
    result = run((r,), p, control(p), tmp_path)
    e = next(e for e in journal(tmp_path) if e["state"] == "CLOSED")
    assert e["timestamp"] == r.data.series.bars[5].end.isoformat()
    assert e["details"]["reason"] == reason and D(e["details"]["price"]) == price
    assert result["account"]["positions"] == {} and result["account"]["reserved"] == "0"
    assert D(result["account"]["cash"]) == D(10000) + D(result["account"]["realized"])


@pytest.mark.parametrize("volume", [0, 1, 100])
def test_failed_exit_preserves_earlier_fill(tmp_path: Path, volume: int) -> None:
    p = policy()
    r = changed(request(p), 5, volume=volume)
    result = run((r,), p, control(p), tmp_path)
    assert fills(tmp_path)[0]["details"]["quantity"] == 10
    assert result["halted"] and not result["simulation_complete"]
    assert result["account"]["positions"]["SBIN"]["quantity"] == 10
    assert not any(e["state"] == "CLOSED" for e in journal(tmp_path))
    assert result["account"]["reserved"] == "0"


def test_executor_all_or_none_and_unresolved() -> None:
    p = policy()
    r = changed(request(p), 3, volume=100)
    out = execute(r.data.series, r.definition, r.settings, r.costs)
    assert not out.trades and out.no_fills
    assert out.entry_decisions[0]["decision"] == "REJECTED"
    assert out.entry_decisions[0]["fill_confirmed_at"] == ""
    r = changed(request(p), 5, volume=0)
    out = execute(r.data.series, r.definition, r.settings, r.costs)
    assert not out.trades and len(out.unresolved) == 1
    assert out.unresolved[0].quantity == 10 and out.unresolved[0].entry == 102
    assert out.execution_policy_hash == execution_fingerprint()
    timing = out.entry_decisions[0]
    assert timing["decision"] == "FILLED" and timing["quantity"] == "10"
    assert timing["liquidity_observed_at"] == timing["fill_confirmed_at"]
    assert timing["fill_confirmed_at"] == r.data.series.bars[4].start.isoformat()


def test_future_high_low_does_not_resize_entry(tmp_path: Path) -> None:
    p = policy()
    for label, updates in [("plain", {}), ("collision", {"low": D(90), "high": D(110)})]:
        run((changed(request(p), 4, **updates),), p, control(p), tmp_path / label)
    left, right = [fills(tmp_path / s)[0] for s in ("plain", "collision")]
    assert left["details"] == right["details"] and left["timestamp"] == right["timestamp"]


def test_policy_replay_and_crash(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    with pytest.raises(InterruptedError):
        run((r,), p, control(p), tmp_path, interrupt_after=5)
    a = run((r,), p, control(p), tmp_path)
    assert run((r,), p, control(p), tmp_path) == a
    path = tmp_path / "portfolio-plan.json"
    original = path.read_bytes()
    value = json.loads(original)
    assert value["execution_policy_version"] == EXECUTION_VERSION
    value["execution_policy_hash"] = "0" * 64
    path.write_text(json.dumps(value))
    with pytest.raises(ValueError, match="INCOMPATIBLE_CHECKPOINT"):
        run((r,), p, control(p), tmp_path)
    path.write_bytes(original)
    first = tmp_path / "journal/000000.json"
    first.write_bytes(b"{}")
    with pytest.raises(ValueError, match="JOURNAL_REPLAY"):
        run((r,), p, control(p), tmp_path)


@pytest.mark.parametrize("version", [None, "legacy", "future"])
def test_paper_audit_incompatible_version(version: str | None) -> None:
    from strategy_engine.phase1417.paper import Audit

    with pytest.raises(ValueError, match="INCOMPATIBLE_EXECUTION"):
        Audit(
            {"execution_policy_version": version, "execution_policy_hash": execution_fingerprint()}
        )


def test_old_paper_checkpoint_rejected(tmp_path: Path) -> None:
    from strategy_engine.phase1417.paper import simulate

    p = policy()
    r = request(p)
    checkpoint = tmp_path / "paper-run.json"
    raw = b'{"binding":{"schema":"Phase1417Paper.v1"}}'
    checkpoint.write_bytes(raw)
    with pytest.raises(ValueError, match="INCOMPATIBLE_EXECUTION"):
        simulate(
            r.qualification,
            r.data,
            r.definition,
            r.settings,
            r.costs,
            p.base,
            r.authorization,
            tmp_path,
        )
    assert checkpoint.read_bytes() == raw and list(tmp_path.iterdir()) == [checkpoint]
