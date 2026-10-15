"""Synthetic qualification and offline paper risk/state regressions."""

import json
from datetime import timedelta
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any

import pytest
from test_phase14 import sample, tariff
from test_phase1415 import END, window_data

from strategy_engine.backtest.dataset import digest
from strategy_engine.phase14.engine import FIVE, Settings, Trade, qualify
from strategy_engine.phase1415.hardening import bind_result, family_binding, frozen_policy
from strategy_engine.phase1415.research import (
    Dataset,
    Result,
    evaluate,
    standard_window,
    summarize,
    wilson,
)
from strategy_engine.phase1415.strategies import registry
from strategy_engine.phase1416.jobs import plan
from strategy_engine.phase1417.paper import Audit, Authorization, Risk, simulate, size
from strategy_engine.phase1417.qualification import Row, primary, run, software_hash

D = Decimal


def outcomes(wins: int = 70) -> tuple[Trade, ...]:
    w = standard_window(END, 6)
    trades = []
    for start in w.folds[:-1]:
        for i in range(100):
            at = start + timedelta(days=i // 30, hours=9, minutes=15 + 10 * (i % 30))
            profit = D(1) if i % 100 < wins else D(-1)
            trades.append(
                Trade(
                    signal_at=at,
                    entry_at=at,
                    exit_at=at + FIVE,
                    entry=D(100),
                    exit=D(100) + profit,
                    quantity=1,
                    reason="EOD",
                    gross=profit,
                    fees=D(0),
                    net=profit,
                )
            )
    return tuple(trades)


def risk_fixture() -> Risk:
    return Risk(
        fixture_only=True,
        reviewed_evidence="a" * 64,
        capital=D(10000),
        risk_per_trade=D(100),
        daily_loss=D(100),
        simultaneous_positions=1,
        symbol_exposure=D(10000),
        portfolio_exposure=D(10000),
        max_quantity=10,
        correlation_reviewed=True,
    )


def proof(risk: Risk, wins: int = 70) -> Row:
    w = standard_window(END, 6)
    t = outcomes(wins)
    q = qualify(t, D(10000), w.holdout_start, w.folds, frozen_policy())
    h = summarize(t, D(10000), 86400)
    r = Result(
        symbol="SBIN",
        strategy_id="S06",
        strategy_version=registry()[5].version,
        strategy_hash=registry()[5].fingerprint,
        window=w,
        policy_hash=digest(frozen_policy().model_dump_json()),
        dataset_hash="a" * 64,
        data_status="SYNTHETIC",
        synthetic=True,
        qualified=False,
        reasons=(*q.reasons, "SYNTHETIC_NOT_HISTORICAL_PERFORMANCE"),
        coverage=D(1),
        holdout=h,
        total=h,
        qualification=q,
        trades=t,
    )
    family = family_binding(END, Settings(), tariff())
    return Row(
        run_id="a" * 64,
        software_hash=software_hash(),
        calendar_hash="b" * 64,
        corporate_action_policy="PROVIDER_UNSPECIFIED_NO_LOCAL_ADJUSTMENT",
        risk_policy_hash=risk.fingerprint,
        capital=D(10000),
        result=bind_result(r, family),
    )


def paper_data(prices: tuple[int, ...] = (100, 100, 100, 102, 102, 102)) -> Dataset:
    series = sample(prices).model_copy(update={"symbol": "SBIN"})
    return Dataset(
        series=series,
        dataset_sha256=digest(series.model_dump_json()),
        acquired_at=series.as_of,
        calendar_version="SYNTHETIC",
        calendar_source="TEST",
        calendar_verified=True,
        corporate_action_evidence="SYNTHETIC",
        missing_sessions=0,
        duplicate_candles=0,
        out_of_order=0,
        quarantined=False,
        mapping_verified=True,
    )


def authorization(p: Row, data: Dataset, risk: Risk) -> Authorization:
    return Authorization(
        mode="SYNTHETIC_FIXTURE",
        enabled=True,
        evidence_hash="c" * 64,
        qualification_hash=p.fingerprint,
        risk_hash=risk.fingerprint,
        dataset_hash=data.dataset_sha256,
        valid_from=data.series.from_inclusive,
        valid_to=data.series.to_exclusive,
    )


@pytest.mark.parametrize("wins,accepted", [(69, False), (70, True), (75, True)])
def test_frozen_threshold_independent_pair(wins: int, accepted: bool) -> None:
    p = proof(risk_fixture(), wins)
    assert p.numerical_gate() is accepted
    assert p.status == "INSUFFICIENT_EVIDENCE"  # synthetic never historical qualification
    assert p.result.holdout and p.result.holdout.basic.win_rate == D(wins) / 100
    if wins == 69:
        assert "OOS_WIN_RATE_BELOW_THRESHOLD" in p.result.reasons
    assert proof(risk_fixture(), 70).numerical_gate()  # another pair cannot change this evidence


def test_samples_confidence_and_full_family() -> None:
    p = proof(risk_fixture())
    w = p.result.window
    q = qualify(outcomes(100)[:5], D(10000), w.holdout_start, w.folds, frozen_policy())
    assert not q.qualified and "INSUFFICIENT_OOS_TRADES" in q.reasons
    assert "MULTIPLE_TESTING_UNCERTAINTY" in q.reasons
    low, high = wilson(70, 100)
    assert 0.60 < low < 0.62 and 0.78 < high < 0.79
    assert frozen_policy().family_size == 13380
    with pytest.raises(ValueError, match="FROZEN"):
        frozen_policy(frozen_policy().model_copy(update={"family_size": 6}))


@pytest.mark.parametrize(
    "reason,status",
    [
        ("INSUFFICIENT_OOS_TRADES", "REJECTED_TRADE_COUNT"),
        ("OOS_WIN_RATE_BELOW_THRESHOLD", "REJECTED_WIN_RATE"),
        ("NON_POSITIVE_NET_EXPECTANCY", "REJECTED_NET_PROFIT"),
        ("DRAWDOWN_LIMIT", "REJECTED_DRAWDOWN"),
        ("MULTIPLE_TESTING_UNCERTAINTY", "REJECTED_CONFIDENCE"),
        ("OUT_OF_SAMPLE_NOT_AVAILABLE", "REJECTED_OUT_OF_SAMPLE"),
        ("UNKNOWN_CRITICAL_QUALITY", "REJECTED_DATA_QUALITY"),
    ],
)
def test_primary_states_keep_all_reasons(reason: str, status: str) -> None:
    r = proof(risk_fixture()).result.model_copy(
        update=dict(
            synthetic=False, data_status="LOCAL_VERIFIED", reasons=(reason, "SECOND_REASON")
        )
    )
    assert primary(r) == status and r.reasons == (reason, "SECOND_REASON")


def test_net_and_drawdown_actual_trade_rejections() -> None:
    w = standard_window(END, 6)
    t = tuple(t.model_copy(update={"net": D(-100), "fees": D(101)}) for t in outcomes())
    q = qualify(t, D(10000), w.holdout_start, w.folds, frozen_policy())
    assert "NON_POSITIVE_NET_EXPECTANCY" in q.reasons and "DRAWDOWN_LIMIT" in q.reasons


def test_walk_forward_no_leakage_and_parameter_freeze() -> None:
    data = window_data()
    w = standard_window(END, 6)
    r = evaluate(data.series.symbol, registry()[5], w, data, Settings(), tariff(), frozen_policy())
    assert r.training and r.validation and r.holdout
    assert r.training.basic.trades == r.validation.basic.trades == 0
    assert r.holdout.basic.trades == 1 and len(r.trades) == 1
    assert all(w.holdout_start <= t.entry_at < t.exit_at <= w.end for t in r.trades)
    with pytest.raises(ValueError, match="PARAMETERS_NOT_FROZEN"):
        type(w)(**(w.model_dump() | {"parameters_frozen_at": w.holdout_start}))
    missing = evaluate("SBIN", registry()[0], w, None, Settings(), tariff(), frozen_policy())
    assert primary(missing) == "DATA_UNAVAILABLE" and missing.holdout is None
    quarantined = evaluate(
        data.series.symbol,
        registry()[5],
        w,
        data.model_copy(update={"quarantined": True}),
        Settings(),
        tariff(),
        frozen_policy(),
    )
    assert primary(quarantined) == "DATA_QUARANTINED" and quarantined.holdout is None


def test_paper_fill_sizing_eod_replay_and_tamper(tmp_path: Path) -> None:
    risk = risk_fixture()
    p = proof(risk)
    data = paper_data()
    auth = authorization(p, data, risk)
    settings = Settings(quantity=10, slippage_bps=D(0))
    result = simulate(p, data, registry()[5], settings, tariff(), risk, auth, tmp_path)
    events = result["events"]
    fill = next(e for e in events if e["state"] == "PAPER_FILLED")
    close = events[-1]
    assert fill["price"] == "102" and fill["quantity"] == 10 and fill["position"] == 10
    assert fill["timestamp"] == (data.series.from_inclusive + 4 * FIVE).isoformat()
    assert close["state"] == "PAPER_POSITION_CLOSED" and close["position"] == 0
    assert (
        close["rejection"] == "EOD" and close["timestamp"] == data.series.to_exclusive.isoformat()
    )
    assert D(result["cash"]) < settings.capital and not result["genuine_qualified"]
    assert simulate(p, data, registry()[5], settings, tariff(), risk, auth, tmp_path) == result
    Audit.replay(result["binding"], events)
    altered = json.loads(json.dumps(events))
    altered[3]["quantity"] = 999
    with pytest.raises(ValueError, match="TAMPERED"):
        Audit.replay(result["binding"], altered)
    audit = Audit.replay(result["binding"], events)
    with pytest.raises(ValueError, match="INVALID_OR_REPEATED"):
        audit.emit(fill["signal_fingerprint"], "PAPER_POSITION_CLOSED", data.series.to_exclusive)


@pytest.mark.parametrize(
    "field,value,reason",
    [
        ("enabled", False, "AUTHORIZATION_REQUIRED"),
        ("partial_fills", True, "PARTIAL_FILLS_UNSUPPORTED"),
        ("paper_halt", True, "PAPER_HALT"),
        ("dataset_hash", "0" * 64, "BINDING_CONFLICT"),
        ("mode", "AUTHORIZED_LOCAL_PAPER", "QUALIFIED_PAIR_REQUIRED"),
    ],
)
def test_paper_authorization_and_halt(tmp_path: Path, field: str, value: Any, reason: str) -> None:
    risk = risk_fixture()
    p = proof(risk)
    data = paper_data()
    auth = authorization(p, data, risk).model_copy(update={field: value})
    output = simulate(p, data, registry()[5], Settings(), tariff(), risk, auth, tmp_path)
    assert reason in output["events"][-1]["rejection"]
    assert not any(e["state"] == "PAPER_FILLED" for e in output["events"])


@pytest.mark.parametrize(
    "field",
    [
        "risk_per_trade",
        "daily_loss",
        "symbol_exposure",
        "portfolio_exposure",
        "simultaneous_positions",
        "max_quantity",
        "reviewed_evidence",
        "correlation_reviewed",
    ],
)
def test_unknown_risk_blocks(tmp_path: Path, field: str) -> None:
    risk = risk_fixture().model_copy(
        update={field: False if field == "correlation_reviewed" else None}
    )
    p = proof(risk)
    data = paper_data()
    output = simulate(
        p, data, registry()[5], Settings(), tariff(), risk, authorization(p, data, risk), tmp_path
    )
    assert "RISK_POLICY_GAP:" + field in output["events"][-1]["rejection"]


def test_position_sizing_and_exposure() -> None:
    risk = risk_fixture().model_copy(update={"symbol_exposure": D(250)})
    settings = Settings(quantity=10, slippage_bps=D(0))
    at = paper_data().series.from_inclusive
    assert size(risk, settings, tariff(), D(100), at, D(10000), 10000, D(".02")) == 2
    assert size(risk, settings, tariff(), D(100), at, D(1), 10000, D(".02")) == 0
    assert size(risk, settings, tariff(), D(100), at, D(10000), 0, D(".02")) == 0


def test_collision_daily_loss_and_missing_liquidity(tmp_path: Path) -> None:
    risk = risk_fixture().model_copy(update={"daily_loss": D(1)})
    p = proof(risk)
    data = paper_data((100, 100, 100, 102, 102, 100, 102, 102, 100))
    bars = tuple(
        b.model_copy(update={"low": D(90), "high": D(110)}) if i in (4, 7) else b
        for i, b in enumerate(data.series.bars)
    )
    series = data.series.model_copy(update={"bars": bars})
    data = data.model_copy(
        update={"series": series, "dataset_sha256": digest(series.model_dump_json())}
    )
    output = simulate(
        p,
        data,
        registry()[5],
        Settings(slippage_bps=D(0)),
        tariff(),
        risk,
        authorization(p, data, risk),
        tmp_path,
    )
    closes = [e for e in output["events"] if e["state"] == "PAPER_POSITION_CLOSED"]
    assert (
        len(closes) == 1
        and closes[0]["rejection"] == "STOP"
        and D(closes[0]["price"]) == D("99.96")
    )
    assert any(e["rejection"] == "DAILY_LOSS_GUARD" for e in output["events"])


def test_qualification_full_matrix_replay(tmp_path: Path) -> None:
    job = plan(END, Settings(), tariff(), (), as_of=END)
    result = run(job, tmp_path, Risk().fingerprint, now=END)
    assert result["rows"] == 13380 and result["statuses"] == {"DATA_UNAVAILABLE": 13380}
    assert result["windows"] == {"6": 6690, "12": 6690} and result["qualified"] == 0
    rows = json.loads((tmp_path / "qualification-ledger.json").read_bytes())
    assert len({(r["symbol"], r["strategy_id"], r["months"]) for r in rows}) == 13380
    assert all(r["win_rate"] is None and r["confidence_interval"] is None for r in rows)
    assert run(job, tmp_path, Risk().fingerprint, now=END) == result
    (tmp_path / "qualification-ledger.json").write_bytes(b"[]")
    with pytest.raises(ValueError, match="TAMPERED"):
        run(job, tmp_path, Risk().fingerprint, now=END)


def test_no_broker_imports_and_paper_software_rejection(tmp_path: Path) -> None:
    import ast

    from strategy_engine.phase1415.hardening import ROOT

    for path in (ROOT / "apps/strategy-engine/src/strategy_engine/phase1417").glob("*.py"):
        tree = ast.parse(path.read_bytes())
        imports = [n.module or "" for n in ast.walk(tree) if isinstance(n, ast.ImportFrom)]
        assert not any(
            "kite" in name.lower() or name.startswith(("requests", "http", "socket"))
            for name in imports
        )
    risk = risk_fixture()
    p = proof(risk).model_copy(update={"software_hash": "0" * 64})
    data = paper_data()
    output = simulate(
        p, data, registry()[5], Settings(), tariff(), risk, authorization(p, data, risk), tmp_path
    )
    assert "QUALIFIED_PAIR_REQUIRED" in output["events"][-1]["rejection"]


@pytest.mark.parametrize("kind", ["target", "liquidity", "exit_liquidity"])
def test_paper_target_and_liquidity_prices_and_times(tmp_path: Path, kind: str) -> None:
    risk = risk_fixture()
    p = proof(risk)
    data = paper_data()
    bars = list(data.series.bars)
    if kind == "target":
        bars[4] = bars[4].model_copy(update={"high": D(110)})
    elif kind == "liquidity":
        bars[4] = bars[4].model_copy(update={"volume": 0})
    else:
        bars[5] = bars[5].model_copy(update={"volume": 100})
    series = data.series.model_copy(update={"bars": tuple(bars)})
    data = data.model_copy(
        update={"series": series, "dataset_sha256": digest(series.model_dump_json())}
    )
    output = simulate(
        p,
        data,
        registry()[5],
        Settings(quantity=10, slippage_bps=D(0)),
        tariff(),
        risk,
        authorization(p, data, risk),
        tmp_path,
    )
    last = output["events"][-1]
    if kind == "target":
        assert last["state"] == "PAPER_POSITION_CLOSED" and last["rejection"] == "TARGET"
        assert D(last["price"]) == D("104.04")
        assert last["timestamp"] == (series.from_inclusive + 5 * FIVE).isoformat()
    elif kind == "liquidity":
        assert last["state"] == "PAPER_ORDER_REJECTED" and "LIQUIDITY" in last["rejection"]
        assert last["timestamp"] == (series.from_inclusive + 4 * FIVE).isoformat()
        assert not any(e["state"] == "PAPER_FILLED" for e in output["events"])
    else:
        assert last["state"] == "PAPER_HALTED" and last["position"] == 10
        assert last["rejection"] == "EXIT_LIQUIDITY_UNVALUED"


def test_audit_cancel_and_duplicate_signal(tmp_path: Path) -> None:
    risk = risk_fixture()
    p = proof(risk)
    data = paper_data()
    output = simulate(
        p, data, registry()[5], Settings(), tariff(), risk, authorization(p, data, risk), tmp_path
    )
    audit = Audit(output["binding"])
    for state in ("SIGNAL_RECEIVED", "ELIGIBILITY_CHECKED", "RISK_APPROVED", "PAPER_ORDER_CREATED"):
        audit.emit("a" * 64, state, data.series.from_inclusive)
    audit.emit(
        "a" * 64, "PAPER_CANCELLED", data.series.from_inclusive, rejection="EXPLICIT_TEST_CANCEL"
    )
    assert audit.events[-1]["position"] == 0
    with pytest.raises(ValueError, match="INVALID_OR_REPEATED"):
        audit.emit("a" * 64, "SIGNAL_RECEIVED", data.series.from_inclusive)


def test_paper_forward_boundary_and_future_candle_invariance(tmp_path: Path) -> None:
    risk = risk_fixture()
    p = proof(risk)
    data = paper_data()
    first = simulate(
        p,
        data,
        registry()[5],
        Settings(),
        tariff(),
        risk,
        authorization(p, data, risk),
        tmp_path / "first",
    )
    bars = (
        *data.series.bars[:-1],
        data.series.bars[-1].model_copy(update={"close": D(103), "high": D("103.1")}),
    )
    series = data.series.model_copy(update={"bars": bars})
    changed = data.model_copy(
        update={"series": series, "dataset_sha256": digest(series.model_dump_json())}
    )
    later = simulate(
        p,
        changed,
        registry()[5],
        Settings(),
        tariff(),
        risk,
        authorization(p, changed, risk),
        tmp_path / "later",
    )
    a = next(e for e in first["events"] if e["state"] == "PAPER_FILLED")
    b = next(e for e in later["events"] if e["state"] == "PAPER_FILLED")
    assert (a["price"], a["quantity"], a["timestamp"]) == (
        b["price"],
        b["quantity"],
        b["timestamp"],
    )
    invalid = p.model_copy(
        update={
            "result": p.result.model_copy(
                update={
                    "window": p.result.window.model_copy(update={"end": data.series.to_exclusive})
                }
            )
        }
    )
    denied = simulate(
        invalid,
        data,
        registry()[5],
        Settings(),
        tariff(),
        risk,
        authorization(invalid, data, risk),
        tmp_path / "denied",
    )
    assert "HISTORY_BOUNDARY" in denied["events"][-1]["rejection"]


def test_paper_decimal_context_invariance(tmp_path: Path) -> None:
    risk = risk_fixture()
    p = proof(risk)
    data = paper_data()
    auth = authorization(p, data, risk)
    outputs = []
    for precision in (6, 50):
        with localcontext() as context:
            context.prec = precision
            outputs.append(
                simulate(
                    p,
                    data,
                    registry()[5],
                    Settings(quantity=10),
                    tariff(),
                    risk,
                    auth,
                    tmp_path / str(precision),
                )
            )
    assert outputs[0] == outputs[1]
