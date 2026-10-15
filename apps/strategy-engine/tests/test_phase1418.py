"""Deterministic synthetic portfolio regression fixtures; no approved trading limits."""

import json
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any

import pytest
from test_phase14 import tariff
from test_phase1417 import authorization, paper_data, proof, risk_fixture

from strategy_engine.backtest.dataset import digest
from strategy_engine.phase14.engine import FIVE, Settings
from strategy_engine.phase1415.paper import event
from strategy_engine.phase1415.strategies import registry
from strategy_engine.phase1417.paper import Risk
from strategy_engine.phase1418.canonical_policy import VERSION
from strategy_engine.phase1418.portfolio import Control, Coordinator, Policy, Request, run

D = Decimal


def policy(**updates: Any) -> Policy:
    base = risk_fixture().model_copy(update={"simultaneous_positions": 4})
    return Policy(
        fingerprint_version=VERSION,
        base=base,
        strategy_exposure=D(10000),
        sector_exposure=D(10000),
        sectors={"SBIN": "BANK", "TCS": "IT", "INFY": "IT"},
        sector_evidence="d" * 64,
        correlation_positions=4,
    ).model_copy(update=updates)


def request(
    p: Policy,
    symbol: str = "SBIN",
    strategy: int = 5,
    prices: tuple[int, ...] = (100, 100, 100, 102, 102, 102),
) -> Request:
    data = paper_data(prices)
    series = data.series.model_copy(update={"symbol": symbol})
    data = data.model_copy(
        update={"series": series, "dataset_sha256": digest(series.model_dump_json())}
    )
    definition = registry()[strategy]
    q = proof(p.base)
    q = q.model_copy(
        update={
            "result": q.result.model_copy(
                update={
                    "symbol": symbol,
                    "strategy_id": definition.identifier,
                    "strategy_version": definition.version,
                    "strategy_hash": definition.fingerprint,
                }
            )
        }
    )
    settings = Settings(quantity=10, slippage_bps=D(0), capital=p.base.capital or D(10000))
    return Request(
        qualification=q,
        data=data,
        definition=definition,
        settings=settings,
        costs=tariff(),
        authorization=authorization(q, data, p.base),
    )


def control(p: Policy, **updates: Any) -> Control:
    return Control(
        policy_fingerprint_version=VERSION,
        policy_hash=p.fingerprint,
        authorization_evidence="e" * 64,
        enabled=True,
    ).model_copy(update=updates)


def journal(path: Path) -> list[dict[str, Any]]:
    return [json.loads(p.read_bytes()) for p in sorted((path / "journal").glob("*.json"))]


def test_simultaneous_order_cash_positions_and_reports(tmp_path: Path) -> None:
    p = policy()
    a = request(p)
    b = request(p, "TCS")
    x = run((a, b), p, control(p), tmp_path / "a")
    y = run((b, a), p, control(p), tmp_path / "b")
    assert x == y and x["simulation_complete"] and not x["account"]["positions"]
    assert len(x["reports"]) == 12 and x["qualified_genuine"] == 0
    events = journal(tmp_path / "a")
    fills = [e for e in events if e["state"] == "FILLED"]
    assert len(fills) == 2 and fills[0]["timestamp"] == fills[1]["timestamp"]
    assert all(e["details"]["price"] == "102" and e["details"]["quantity"] == 10 for e in fills)
    assert all(D(e["account"]["available"]) >= 0 for e in events)
    assert max(len(e["account"]["positions"]) for e in events) == 2
    assert x["account"]["reserved"] == "0" and D(x["account"]["fees"]) > 0
    assert p.base.capital is not None
    assert D(x["account"]["cash"]) == p.base.capital + D(x["account"]["realized"])
    assert all(e["details"]["reason"] == "EOD" for e in events if e["state"] == "CLOSED")


def test_duplicate_signals_do_not_double_spend(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    run((r, r), p, control(p), tmp_path)
    events = journal(tmp_path)
    assert len([e for e in events if e["state"] == "FILLED"]) == 1
    assert any(e["details"].get("reason") == "DUPLICATE_SIGNAL" for e in events)


@pytest.mark.parametrize(
    "field", ["strategy_exposure", "sector_exposure", "sector_evidence", "correlation_positions"]
)
def test_missing_policy_fails_closed(tmp_path: Path, field: str) -> None:
    p = policy(**{field: None})
    r = request(p)
    result = run((r,), p, control(p), tmp_path)
    assert result["halted"] and result["event_count"] == 1 and result["account"]["cash"] == "10000"
    assert field in journal(tmp_path)[0]["details"]["reason"]


@pytest.mark.parametrize(
    "missing", ["sector", "correlation", "capital", "authorization", "hash", "halt"]
)
def test_admission_unknowns(tmp_path: Path, missing: str) -> None:
    p = policy()
    r = request(p)
    ctl = control(p)
    if missing == "sector":
        p = p.model_copy(update={"sectors": {}})
    elif missing == "correlation":
        p = p.model_copy(update={"base": p.base.model_copy(update={"correlation_reviewed": False})})
    elif missing == "capital":
        p = p.model_copy(update={"base": Risk()})
    elif missing == "authorization":
        ctl = ctl.model_copy(update={"enabled": False})
    elif missing == "hash":
        ctl = ctl.model_copy(update={"policy_hash": "f" * 64})
    else:
        ctl = ctl.model_copy(update={"paper_halt": True})
    result = run((r,), p, ctl, tmp_path)
    assert result["halted"] and not result["account"]["positions"]


@pytest.mark.parametrize("field", ["strategy_exposure", "sector_exposure"])
def test_exposure_size_limits(tmp_path: Path, field: str) -> None:
    p = policy(**{field: D(1200)})
    requests = (request(p, "TCS"), request(p, "INFY"))
    run(requests, p, control(p), tmp_path)
    fills = [e for e in journal(tmp_path) if e["state"] == "FILLED"]
    assert sum(e["details"]["quantity"] for e in fills) <= 11


def test_portfolio_and_cash_limits(tmp_path: Path) -> None:
    p = policy()
    p = p.model_copy(
        update={
            "base": p.base.model_copy(update={"capital": D(1500), "portfolio_exposure": D(1400)})
        }
    )
    result = run((request(p), request(p, "TCS")), p, control(p), tmp_path)
    assert D(result["account"]["cash"]) >= 0
    assert all(D(e["account"]["marked_value"]) <= 1400 for e in journal(tmp_path))
    assert sum(e["details"]["quantity"] for e in journal(tmp_path) if e["state"] == "FILLED") <= 13


def test_concentration_positions_limit(tmp_path: Path) -> None:
    p = policy(correlation_positions=1)
    run((request(p), request(p, "TCS")), p, control(p), tmp_path)
    assert len([e for e in journal(tmp_path) if e["state"] == "FILLED"]) == 1
    assert any(
        e["details"].get("reason") == "POSITION_OR_CORRELATION_LIMIT" for e in journal(tmp_path)
    )


def test_cancel_releases_exact_reservation(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    signal = event(
        "SBIN", r.definition, r.data.series.bars[3], r.data.series.bars[3].end
    ).idempotency_key
    result = run((r,), p, control(p, cancel_signals=(signal,)), tmp_path)
    assert result["account"]["cash"] == "10000" and result["account"]["reserved"] == "0"
    states = [e["state"] for e in journal(tmp_path)]
    assert "RESERVED" in states and "CANCELLED" in states and "FILLED" not in states


def test_missing_eod_retains_position_and_halts(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    cutoff = r.data.series.bars[4].start
    result = run((r,), p, control(p, delivery_end=cutoff), tmp_path)
    assert result["halted"] and not result["simulation_complete"]
    assert "SBIN" in result["account"]["positions"] and result["account"]["reserved"] == "0"
    assert not any(e["state"] == "CLOSED" for e in journal(tmp_path))
    assert journal(tmp_path)[-1]["details"]["complete"] is False


@pytest.mark.parametrize(
    "prices,reason,price",
    [
        ((100, 100, 100, 102, 102, 105), "TARGET", "104.04"),
        ((100, 100, 100, 102, 102, 95), "STOP", "95"),
    ],
)
def test_frozen_target_and_gap(
    tmp_path: Path, prices: tuple[int, ...], reason: str, price: str
) -> None:
    p = policy()
    run((request(p, prices=prices),), p, control(p), tmp_path)
    close = next(e for e in journal(tmp_path) if e["state"] == "CLOSED")
    assert close["details"]["reason"] == reason and D(close["details"]["price"]) == D(price)


def test_daily_loss_halt_and_known_exit(tmp_path: Path) -> None:
    p = policy()
    p = p.model_copy(update={"base": p.base.model_copy(update={"daily_loss": D(1)})})
    result = run((request(p, prices=(100, 100, 100, 102, 102, 95)),), p, control(p), tmp_path)
    assert result["halted"] and not result["account"]["positions"]
    assert any(e["details"].get("reason") == "DAILY_LOSS_BREACH" for e in journal(tmp_path))


def test_decimal_context_invariance(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    with localcontext() as ctx:
        ctx.prec = 6
        a = run((r,), p, control(p), tmp_path / "a")
        ctx.prec = 50
        b = run((r,), p, control(p), tmp_path / "b")
    assert a == b and journal(tmp_path / "a") == journal(tmp_path / "b")


@pytest.mark.parametrize("checkpoint", ["PROPOSED", "RESERVED", "FILLED", "EXIT_PENDING"])
def test_crash_resume_atomic_prefix(tmp_path: Path, checkpoint: str) -> None:
    p = policy()
    r = request(p)
    complete = run((r,), p, control(p), tmp_path / "expected")
    n = next(e["sequence"] for e in journal(tmp_path / "expected") if e["state"] == checkpoint) + 1
    with pytest.raises(InterruptedError):
        run((r,), p, control(p), tmp_path / "crashed", interrupt_after=n)
    assert not (tmp_path / "crashed/run-summary.json").exists()
    assert len(journal(tmp_path / "crashed")) == n
    resumed = run((r,), p, control(p), tmp_path / "crashed")
    assert resumed == complete and journal(tmp_path / "crashed") == journal(tmp_path / "expected")
    assert run((r,), p, control(p), tmp_path / "crashed") == complete


@pytest.mark.parametrize("tamper", ["alter", "missing", "order", "extra", "pending"])
def test_corrupt_journal_rejects(tmp_path: Path, tamper: str) -> None:
    p = policy()
    r = request(p)
    run((r,), p, control(p), tmp_path)
    files = sorted((tmp_path / "journal").glob("*.json"))
    if tamper == "alter":
        files[1].write_bytes(b"{}")
    elif tamper == "missing":
        files[1].unlink()
    elif tamper == "order":
        files[1].write_bytes(files[2].read_bytes())
    elif tamper == "extra":
        (tmp_path / "journal/999999.json").write_bytes(b"{}")
    else:
        (tmp_path / "journal/000000.json.pending").write_bytes(b"{}")
    with pytest.raises(ValueError, match="PAPER_HALTED"):
        run((r,), p, control(p), tmp_path)


@pytest.mark.parametrize("change", ["risk", "dataset", "strategy", "software"])
def test_replay_bindings(tmp_path: Path, change: str, monkeypatch: pytest.MonkeyPatch) -> None:
    p = policy()
    r = request(p)
    ctl = control(p)
    run((r,), p, ctl, tmp_path)
    if change == "risk":
        p = p.model_copy(update={"strategy_exposure": D(9000)})
    elif change == "dataset":
        r = r.model_copy(update={"data": r.data.model_copy(update={"calendar_source": "OTHER"})})
    elif change == "strategy":
        r = r.model_copy(update={"definition": r.definition.model_copy(update={"version": "2"})})
    else:
        monkeypatch.setattr(
            "strategy_engine.phase1418.portfolio.inventory", lambda: {"synthetic-change": "a" * 64}
        )
    with pytest.raises(ValueError, match="REPLAY_BINDING"):
        run((r,), p, ctl, tmp_path)


def test_partial_fill_denied_and_no_broker(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    r = r.model_copy(
        update={"authorization": r.authorization.model_copy(update={"partial_fills": True})}
    )
    run((r,), p, control(p), tmp_path)
    assert not any(e["state"] == "FILLED" for e in journal(tmp_path))
    assert any(
        "PARTIAL_FILLS_UNSUPPORTED" in e["details"].get("reason", "") for e in journal(tmp_path)
    )
    source = (Path(__file__).parents[1] / "src/strategy_engine/phase1418/portfolio.py").read_text()
    assert all(
        token not in source
        for token in ["import requests", "import socket", "kiteconnect", "websocket"]
    )


def test_future_candle_does_not_fund_earlier_entry(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    changed = request(p, prices=(100, 100, 100, 102, 102, 105))
    run((r,), p, control(p), tmp_path / "a")
    run((changed,), p, control(p), tmp_path / "b")
    a = next(e for e in journal(tmp_path / "a") if e["state"] == "FILLED")
    b = next(e for e in journal(tmp_path / "b") if e["state"] == "FILLED")
    assert a["timestamp"] == b["timestamp"] and a["details"] == b["details"]
    assert (
        a["cash_delta"] == b["cash_delta"]
        and a["account"]["available"] == b["account"]["available"]
    )


def test_transition_duplicate_and_out_of_order() -> None:
    p = policy()
    r = request(p)
    c = Coordinator({"scope": "SYNTHETIC_FIXTURE"}, p)
    at = r.data.series.from_inclusive
    c.emit("one", "PROPOSED", at, r, signal="a" * 64)
    with pytest.raises(ValueError, match="DUPLICATE"):
        c.emit("one", "PROPOSED", at, r, signal="a" * 64)
    with pytest.raises(ValueError, match="OUT_OF_ORDER"):
        c.emit("one", "REJECTED", at - FIVE, reason="test")


def test_all_six_strategies_and_conflicting_source(tmp_path: Path) -> None:
    p = policy()
    requests = tuple(request(p, strategy=i) for i in range(6))
    run(requests, p, control(p), tmp_path / "six")
    assert all(len(e["account"]["positions"]) <= 1 for e in journal(tmp_path / "six"))
    with pytest.raises(ValueError, match="CONFLICTING_SYMBOL_DATASETS"):
        run(
            (request(p), request(p, prices=(100, 100, 100, 102, 102, 105))),
            p,
            control(p),
            tmp_path / "conflict",
        )


def test_one_symbol_owner_and_expired_signal() -> None:
    p = policy()
    r = request(p)
    other = request(p, strategy=4)
    at = r.data.series.bars[4].start
    c = Coordinator({"scope": "SYNTHETIC_FIXTURE"}, p)
    c.emit("one", "PROPOSED", at, r, signal="a" * 64)
    c.enter("one", r, {"price": "102", "quantity": 10}, at, control(p))
    c.emit("two", "PROPOSED", at, other, signal="b" * 64)
    c.enter("two", other, {"price": "102", "quantity": 10}, at, control(p))
    assert c.orders["one"]["state"] == "FILLED" and c.orders["two"]["state"] == "REJECTED"
    assert c.events[-1]["details"]["reason"] == "ONE_POSITION_PER_SYMBOL"
    c.emit("three", "PROPOSED", at, r, signal="c" * 64)
    c.enter("three", r, {"price": "102", "quantity": 10}, at + FIVE, control(p))
    assert c.events[-1]["details"]["reason"] == "EXPIRED_SIGNAL"


def test_collision_uses_stop_and_fees(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    bars = list(r.data.series.bars)
    bars[4] = bars[4].model_copy(update={"high": D(105), "low": D(95)})
    series = r.data.series.model_copy(update={"bars": tuple(bars)})
    data = r.data.model_copy(
        update={"series": series, "dataset_sha256": digest(series.model_dump_json())}
    )
    r = r.model_copy(
        update={"data": data, "authorization": authorization(r.qualification, data, p.base)}
    )
    run((r,), p, control(p), tmp_path)
    closed = next(e for e in journal(tmp_path) if e["state"] == "CLOSED")
    assert closed["details"]["reason"] == "STOP" and D(closed["details"]["price"]) == D("99.96")
    assert closed["timestamp"] == bars[4].end.isoformat() and D(closed["fees"]) > 0


def test_short_direction_rejected() -> None:
    from pydantic import ValidationError

    r = request(policy())
    with pytest.raises(ValidationError):
        Request.model_validate(dict(r.model_dump(), direction="SHORT"))


def test_completed_journal_truncation_is_not_repaired(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    run((r,), p, control(p), tmp_path)
    files = sorted((tmp_path / "journal").glob("*.json"))
    files[-1].unlink()
    with pytest.raises(ValueError, match="TRUNCATED_COMPLETED_JOURNAL"):
        run((r,), p, control(p), tmp_path)
    assert not files[-1].exists()


def test_missing_quality_and_stale_qualification_fail_closed(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    bad = r.model_copy(update={"data": r.data.model_copy(update={"mapping_verified": False})})
    result = run((bad,), p, control(p), tmp_path / "quality")
    assert result["halted"] and not result["account"]["positions"]
    stale = r.model_copy(
        update={"qualification": r.qualification.model_copy(update={"software_hash": "f" * 64})}
    )
    result = run((stale,), p, control(p), tmp_path / "stale")
    assert not result["account"]["positions"]
    assert any(e["state"] == "REJECTED" for e in journal(tmp_path / "stale"))


def test_zero_quantity_and_stop_are_denied(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    low = r.model_copy(update={"settings": r.settings.model_copy(update={"allow_long": False})})
    run((low,), p, control(p), tmp_path / "disabled")
    assert not any(e["state"] == "FILLED" for e in journal(tmp_path / "disabled"))
    invalid = r.model_copy(update={"definition": r.definition.model_copy(update={"stop": D(0)})})
    run((invalid,), p, control(p), tmp_path / "stop")
    assert not any(e["state"] == "FILLED" for e in journal(tmp_path / "stop"))


def test_market_delivery_before_fill_does_not_invent_position(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    result = run((r,), p, control(p, delivery_end=r.data.series.bars[2].end), tmp_path)
    assert result["halted"] and result["account"]["cash"] == "10000"
    assert not result["account"]["positions"] and result["account"]["reserved"] == "0"


def test_audit_chain_and_eod_accounting(tmp_path: Path) -> None:
    from strategy_engine.phase1416.imports import canonical, sha

    p = policy()
    r = request(p)
    summary = run((r,), p, control(p), tmp_path)
    previous = "0" * 64
    for i, e in enumerate(journal(tmp_path)):
        assert e["sequence"] == i and e["previous_hash"] == previous
        previous = e["current_hash"]
        assert sha(canonical({k: v for k, v in e.items() if k != "current_hash"})) == previous
    assert summary["final_hash"] == previous
    end = journal(tmp_path)[-1]
    assert end["state"] == "SESSION_END" and end["details"]["complete"]
    assert end["account"]["reserved"] == "0" and end["account"]["positions"] == {}


def test_accounting_error_fails_closed() -> None:
    p = policy()
    c = Coordinator({"scope": "SYNTHETIC_FIXTURE"}, p)
    c.reservations["bad"] = D(10001)
    with pytest.raises(ValueError, match="INVALID_ACCOUNTING"):
        c.account()
    c.reservations.clear()
    c.cash += D(1)
    with pytest.raises(ValueError, match="UNBALANCED_EQUITY"):
        c.account()


def test_missing_completed_report_is_not_regenerated(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    run((r,), p, control(p), tmp_path)
    report = tmp_path / "cash-ledger.json"
    report.unlink()
    with pytest.raises(ValueError, match="COMPLETED_REPORT_MISMATCH"):
        run((r,), p, control(p), tmp_path)
    assert not report.exists()
