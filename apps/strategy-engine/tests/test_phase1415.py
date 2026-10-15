from collections.abc import Generator, Iterator
from datetime import UTC, datetime, timedelta
from decimal import Decimal, localcontext
from pathlib import Path

import pytest
from pydantic import ValidationError
from test_phase14 import START, sample, tariff

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import NSE, digest
from strategy_engine.phase14.engine import (
    FIVE,
    Candle,
    Policy,
    Series,
    Session,
    Settings,
    Strategy,
    Trade,
    qualify,
    signal,
    simulate,
)
from strategy_engine.phase14.universe import audit, records
from strategy_engine.phase1415.execution import execute
from strategy_engine.phase1415.hardening import bind_result, family_binding, partition_keys
from strategy_engine.phase1415.jobs import Loader, export
from strategy_engine.phase1415.jobs import run as original_run
from strategy_engine.phase1415.paper import event
from strategy_engine.phase1415.research import (
    Dataset,
    ResearchWindow,
    Result,
    evaluate,
    standard_window,
    summarize,
    wilson,
)
from strategy_engine.phase1415.strategies import (
    Definition,
    bands,
    crosses,
    entry_signal,
    opening_range,
    registry,
    sma,
    validate_registry,
    vwap,
)

D = Decimal
ROOT = Path(__file__).resolve().parents[3]
END = datetime(2026, 10, 1, tzinfo=NSE).astimezone(UTC)


def run(
    universe: list[dict[str, str]],
    definitions: tuple[Definition, ...],
    windows: tuple[ResearchWindow, ...],
    loader: Loader,
    settings: Settings,
    costs: IntradayCostSchedule,
    directory: Path,
    policy: Policy | None = None,
) -> Generator[Result, None, None]:
    return original_run(
        universe,
        definitions,
        windows,
        loader,
        settings,
        costs,
        directory,
        policy,
        family=family_binding(END, settings, costs, policy),
    )


def prices(values: tuple[int, ...]) -> Series:
    return sample(values)


def window_data() -> Dataset:
    w = standard_window(END, 6)
    series = prices((100, 100, 100, 102, 102, 102))
    # Sparse explicitly synthetic calendar; never represented as genuine NSE history.
    at = w.holdout_start + timedelta(hours=9, minutes=15)
    bars = tuple(b.model_copy(update={"start": at + i * FIVE}) for i, b in enumerate(series.bars))
    all_days = tuple(
        (w.start + timedelta(days=i)).astimezone(NSE).date()
        for i in range((END - w.start).days)
        if (w.start + timedelta(days=i)).astimezone(NSE).date() != at.date()
    )
    series = Series(
        **(
            series.model_dump()
            | dict(
                from_inclusive=w.start,
                to_exclusive=END,
                as_of=END,
                bars=bars,
                sessions=(Session(start=at, end=bars[-1].end),),
                non_trading_days=all_days,
            )
        )
    )
    return Dataset(
        series=series,
        dataset_sha256=digest(series.model_dump_json()),
        acquired_at=END,
        calendar_version="SYNTHETIC-v1",
        calendar_source="SYNTHETIC_FIXTURE",
        calendar_verified=True,
        corporate_action_evidence="SYNTHETIC_NO_ACTIONS",
        missing_sessions=0,
        duplicate_candles=0,
        out_of_order=0,
        quarantined=False,
        mapping_verified=True,
    )


def test_registry_frozen_unique_and_provenance() -> None:
    defs = registry()
    validate_registry(defs)
    assert len({x.fingerprint for x in defs}) == 6
    assert defs[0].provenance == "USER_SUPPLIED_DEFINITION"
    assert all(x.provenance == "RESEARCH_EQUIVALENT" for x in defs[1:])
    with pytest.raises(ValidationError):
        defs[0].version = "2"  # type: ignore[assignment]
    with pytest.raises(ValueError):
        validate_registry((defs[0], defs[0]))
    with pytest.raises(ValueError):
        validate_registry((defs[1].model_copy(update={"entry": "different"}),))


@pytest.mark.parametrize(
    "a,b,c,d,above,below",
    [
        (1, 1, 2, 1, True, False),
        (1, 1, 0, 1, False, True),
        (1, 1, 1, 1, False, False),
        (2, 1, 3, 1, False, False),
    ],
)
def test_cross_edges(a: int, b: int, c: int, d: int, above: bool, below: bool) -> None:
    assert crosses(D(a), D(b), D(c), D(d)) == above
    assert crosses(D(a), D(b), D(c), D(d), below=True) == below
    assert not crosses(None, D(b), D(c), D(d))


def test_sma_bands_population_warmup_precision() -> None:
    assert sma((D(1),), 2) is None
    assert sma((D(1), D(3)), 2) == 2
    assert bands((D(1), D(3)), 2) == (D(0), D(4))
    assert bands((D(1),) * 19) is None
    with localcontext() as c:
        c.prec = 6
        assert bands((D(1), D(3)), 2) == (D(0), D(4))


def test_vwap_volume_and_range() -> None:
    bars = prices((100, 100, 100, 102, 102, 102)).bars
    assert opening_range(bars[:2]) is None
    assert opening_range(bars[:3]) == (D("100.1"), D("99.9"))
    assert vwap(tuple(b.model_copy(update={"volume": 0}) for b in bars)) is None
    assert not entry_signal(bars[:3], registry()[5])
    assert entry_signal(bars[:4], registry()[5])
    assert entry_signal(bars[:4], registry()[4])
    assert not entry_signal(tuple(b.model_copy(update={"volume": 0}) for b in bars), registry()[4])


@pytest.mark.parametrize("index", range(6))
def test_no_lookahead_session_reset_and_constant_no_signals(index: int) -> None:
    definition = registry()[index]
    bars = prices((100,) * 25 + (90, 110, 100, 100)).bars
    prefix = bars[:27]
    expected = entry_signal(prefix, definition)
    changed = prefix + tuple(b.model_copy(update={"close": D(200)}) for b in bars[27:])
    assert entry_signal(changed[:27], definition) == expected
    assert not entry_signal(prices((100,) * 30).bars, definition)
    assert not entry_signal((bars[0],), definition)


def test_s01_phase14_parity_and_inverse_cross() -> None:
    values = tuple(range(120, 95, -1)) + (110, 110, 110, 110)
    data = prices(values)
    for i in range(1, len(data.bars) + 1):
        closes = tuple(b.close for b in data.bars[:i])
        assert entry_signal(data.bars[:i], registry()[0]) == signal(closes, Strategy())
    old = simulate(data, Strategy(), Settings(), tariff())
    new = execute(data, registry()[0], Settings(), tariff())
    assert old.trades == new.trades and old.metrics == new.metrics
    assert old.marked_drawdown == new.marked_drawdown
    assert crosses(D(1), D(2), D(3), D(2)) == crosses(D(2), D(1), D(2), D(3), below=True)


def test_next_open_eod_costs_and_resource_limits() -> None:
    data = prices((100, 100, 100, 102, 102, 102))
    result = execute(data, registry()[5], Settings(), tariff())
    assert len(result.trades) == 1
    t = result.trades[0]
    assert t.signal_at == t.entry_at == data.bars[4].start
    assert t.exit_at == data.bars[-1].end and t.reason == "EOD"
    assert t.entry == D(102) * D("1.0005") and t.fees > 0 and t.net < t.gross
    with localcontext() as c:
        c.prec = 6
        assert execute(data, registry()[5], Settings(), tariff()) == result
    assert not execute(data, registry()[5], Settings(capital=D(1)), tariff()).trades
    assert not execute(
        prices((100, 100, 100, 102, 102)), registry()[5], Settings(), tariff()
    ).trades


@pytest.mark.parametrize("gap", [False, True])
def test_collision_and_adverse_gap(gap: bool) -> None:
    data = prices((100, 100, 100, 102, 102, 102, 102))
    bars = list(data.bars)
    i = 5 if gap else 4
    bars[i] = Candle(
        start=bars[i].start,
        open=D(90) if gap else D(102),
        high=D(120),
        low=D(89),
        close=D(102),
        volume=10000,
        open_interest=None,
    )
    data = data.model_copy(update={"bars": tuple(bars)})
    result = execute(data, registry()[5], Settings(slippage_bps=D(0)), tariff())
    assert result.trades[0].reason == "STOP"
    assert result.trades[0].exit == (D(90) if gap else D("99.96"))


@pytest.mark.parametrize("fault", ["gap", "corporate", "illiquid"])
def test_quality_rejects(fault: str) -> None:
    data = prices((100, 100, 100, 102, 102, 102))
    if fault == "gap":
        data = data.model_copy(update={"bars": data.bars[:-1]})
    elif fault == "corporate":
        data = data.model_copy(update={"corporate_actions_reviewed": False})
    else:
        data = data.model_copy(
            update={"bars": data.bars[:-1] + (data.bars[-1].model_copy(update={"volume": 0}),)}
        )
    if fault == "illiquid":
        result = execute(data, registry()[5], Settings(), tariff())
        assert len(result.unresolved) == 1 and not result.trades
        assert result.unresolved[0].entry == D("102.0510")
    else:
        with pytest.raises(ValueError):
            execute(data, registry()[5], Settings(), tariff())


@pytest.mark.parametrize("bad", ["negative", "nan", "duplicate", "future"])
def test_invalid_candles_and_series(bad: str) -> None:
    data = sample()
    with pytest.raises(ValueError):
        if bad == "negative":
            Candle(**(data.bars[0].model_dump() | {"volume": -1}))
        elif bad == "nan":
            Candle(**(data.bars[0].model_dump() | {"close": "NaN"}))
        elif bad == "duplicate":
            Series(**(data.model_dump() | {"bars": (data.bars[0],) * 2}))
        else:
            Series(**(data.model_dump() | {"as_of": START}))


def test_windows_calendar_months_splits_and_hash_gate() -> None:
    w = standard_window(datetime(2024, 8, 31, tzinfo=NSE).astimezone(UTC), 6)
    assert w.start == datetime(2024, 2, 29, tzinfo=NSE).astimezone(UTC)
    assert standard_window(END, 12).start == datetime(2025, 10, 1, tzinfo=NSE).astimezone(UTC)
    with pytest.raises(ValueError):
        type(w)(**(w.model_dump() | {"parameters_frozen_at": w.end}))
    data = window_data()
    with pytest.raises(ValueError):
        Dataset(**(data.model_dump() | {"dataset_sha256": "0" * 64}))


@pytest.mark.parametrize(
    "field,value,reason",
    [
        ("calendar_verified", False, "CALENDAR_NOT_VERIFIED"),
        ("mapping_verified", False, "MASTER_UNAVAILABLE"),
        ("corporate_action_evidence", "UNRESOLVED", "CORPORATE_ACTION_REVIEW_REQUIRED"),
        ("quarantined", True, "DATA_QUARANTINED"),
        ("missing_sessions", 1, "DATA_QUARANTINED"),
    ],
)
def test_provenance_qualification_gates(field: str, value: object, reason: str) -> None:
    data = window_data().model_copy(update={field: value})
    r = evaluate(
        data.series.symbol,
        registry()[5],
        standard_window(END, 6),
        data,
        Settings(),
        tariff(),
        Policy(family_size=13380),
    )
    assert not r.qualified and r.total is None and reason in r.reasons


def test_synthetic_result_never_qualifies_and_split_no_leakage() -> None:
    data = window_data()
    w = standard_window(END, 6)
    r = evaluate(data.series.symbol, registry()[5], w, data, Settings(), tariff(), Policy())
    assert not r.qualified and r.total and r.total.basic.trades == 1
    assert r.training and r.training.basic.trades == 0
    assert r.validation and r.validation.basic.trades == 0
    assert r.holdout and r.holdout.basic.trades == 1
    assert "SYNTHETIC_NOT_HISTORICAL_PERFORMANCE" in r.reasons
    cut = data.series.sessions[0].start + FIVE
    bad = w.model_copy(update={"holdout_start": cut, "folds": (cut, *w.folds[1:])})
    with pytest.raises(ValueError, match="IST_MIDNIGHT_BOUNDARY_REQUIRED"):
        evaluate(data.series.symbol, registry()[5], bad, data, Settings(), tariff(), Policy())


def test_confidence_minimum_sample_breakeven_and_cost_accounting() -> None:
    assert wilson(0, 0) == (0, 1)
    lo, hi = wilson(70, 100)
    assert 0.60 < lo < 0.62 and 0.78 < hi < 0.79
    assert wilson(1, 1)[0] < 0.3
    t = Trade(
        signal_at=START,
        entry_at=START,
        exit_at=START + FIVE,
        entry=D(100),
        exit=D(101),
        quantity=1,
        reason="EOD",
        gross=D(1),
        fees=D(1),
        net=D(0),
    )
    summary = summarize((t,), D(10000), 600)
    assert summary.basic.breakeven == 1 and summary.basic.win_rate == 0
    assert summary.exposure == D(".5") and summary.profit_factor is None
    q = qualify(
        (t,),
        D(10000),
        START,
        (START, START + timedelta(days=1), START + timedelta(days=2), START + timedelta(days=3)),
        Policy(family_size=13380),
    )
    assert not q.qualified and "INSUFFICIENT_OOS_TRADES" in q.reasons


def test_checkpoint_restart_conflict_and_corruption(tmp_path: Path) -> None:
    members = [dict(symbol="M&M", exchange="NSE", enabled="TRUE")]
    defs, windows = registry(), (standard_window(END, 6),)

    def call() -> list[object]:
        return list(run(members, defs, windows, lambda s, m: None, Settings(), tariff(), tmp_path))

    first = call()
    assert "software_hash" in (tmp_path / "configuration.json").read_text()
    assert call() == first
    checkpoint = next(x for x in tmp_path.glob("*.json") if x.name != "configuration.json")
    checkpoint.write_text("{}")
    with pytest.raises((KeyError, ValueError)):
        call()
    assert not (tmp_path / "job.lock").exists()


def test_concurrency_lock_and_configuration_denial(tmp_path: Path) -> None:
    members = [dict(symbol="M&M", exchange="NSE", enabled="TRUE")]
    args = (
        members,
        registry(),
        (standard_window(END, 6),),
        lambda s, m: None,
        Settings(),
        tariff(),
    )
    a = run(*args, tmp_path)
    next(a)
    with pytest.raises(FileExistsError):
        next(run(*args, tmp_path))
    a.close()
    (tmp_path / "configuration.json").write_text("changed")
    with pytest.raises(ValueError):
        next(run(*args, tmp_path))


def test_full_universe_rows_and_deterministic_exports(tmp_path: Path) -> None:
    members = records(ROOT / "config/universes/phase14_unique_universe.csv")
    assert audit(ROOT / "input/phase14")["unique_rows"] == 1115
    windows = (standard_window(END, 6), standard_window(END, 12))

    # Streaming evaluator avoids13k checkpoint files for unavailable report smoke test.
    def rows() -> Iterator[Result]:
        for m in members:
            for w in windows:
                for d in registry():
                    yield evaluate(
                        m["symbol"], d, w, None, Settings(), tariff(), Policy(family_size=13380)
                    )

    family = family_binding(END, Settings(), tariff())
    keys = partition_keys(tuple(m["symbol"] for m in members), registry(), windows, family)
    a = export(
        (bind_result(r, family) for r in rows()), tmp_path / "a", family=family, expected_keys=keys
    )
    b = export(
        (bind_result(r, family) for r in rows()), tmp_path / "b", family=family, expected_keys=keys
    )
    assert a == b and a["rows"] == 13380 and a["windows"] == {6: 6690, 12: 6690}


def test_paper_contract_expiry_idempotency_and_future() -> None:
    b = sample().bars[0]
    a = event("M&M", registry()[0], b, b.end)
    assert a == event("M&M", registry()[0], b, b.end)
    assert a.mode == "BACKTEST_ONLY" and a.rejection == "OFFLINE_ONLY_NO_TRANSPORT"
    assert event("M&M", registry()[0], b, b.end + FIVE).rejection == "EXPIRED"
    with pytest.raises(ValueError):
        event("M&M", registry()[0], b, b.start)


@pytest.mark.parametrize(
    "index,values,bar_number",
    [
        (0, (100,) * 25 + (50, 100, 100, 100), 27),
        (1, tuple(range(130, 99, -1)) + tuple(range(100, 135)), 44),
        (2, tuple(range(130, 99, -1)) + tuple(range(100, 135)), 41),
        (3, (100,) * 25 + (80, 90, 100, 110), 27),
    ],
)
def test_positive_strategy_signals(index: int, values: tuple[int, ...], bar_number: int) -> None:
    data = prices(values)
    definition = registry()[index]
    assert entry_signal(data.bars[:bar_number], definition)
    assert not entry_signal(data.bars[: bar_number - 1], definition)
    result = execute(data, definition, Settings(), tariff())
    assert result.trades and result.trades[0].entry_at == data.bars[bar_number].start
    if index == 0:
        assert result.trades == simulate(data, Strategy(), Settings(), tariff()).trades


def test_per_pair_oos_win_rate_and_multiple_testing() -> None:
    boundaries = tuple(START + timedelta(days=100 * i) for i in range(4))

    def outcomes(wins: int) -> tuple[Trade, ...]:
        result = []
        for i in range(300):
            net = D(1) if i % 100 < wins else D(-1)
            at = START + timedelta(days=i)
            result.append(
                Trade(
                    signal_at=at,
                    entry_at=at,
                    exit_at=at + FIVE,
                    entry=D(100),
                    exit=D(100) + net,
                    quantity=1,
                    reason="EOD",
                    gross=net,
                    fees=D(0),
                    net=net,
                )
            )
        return tuple(result)

    policy = Policy(family_size=13380)
    good = qualify(outcomes(75), D(10000), START, boundaries, policy)
    bad = qualify(outcomes(69), D(10000), START, boundaries, policy)
    assert good.qualified and good.adjusted_wilson_lower > 0.5
    assert not bad.qualified and "OOS_WIN_RATE_BELOW_THRESHOLD" in bad.reasons
    small = qualify(outcomes(100)[:5], D(10000), START, boundaries, policy)
    assert small.out_of_sample.win_rate == 1 and not small.qualified


def test_interrupted_checkpoint_fail_closed(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:

    members = [dict(symbol="M&M", exchange="NSE", enabled="TRUE")]
    args = (
        members,
        registry(),
        (standard_window(END, 6),),
        lambda s, m: None,
        Settings(),
        tariff(),
    )

    def fail(a: object, b: object) -> None:
        raise OSError("SYNTHETIC_STORAGE_FAILURE")

    with monkeypatch.context() as patch:
        patch.setattr("strategy_engine.phase1415.jobs.os.replace", fail)
        with pytest.raises(OSError):
            next(run(*args, tmp_path))
    assert list(tmp_path.glob("*.pending"))
    with pytest.raises(FileExistsError):
        next(run(*args, tmp_path))
    assert not (tmp_path / "job.lock").exists()


def test_changed_dataset_checkpoint_denied(tmp_path: Path) -> None:
    data = window_data()
    series = data.series.model_copy(update={"symbol": "M&M"})
    data = data.model_copy(
        update={"series": series, "dataset_sha256": digest(series.model_dump_json())}
    )
    members = [dict(symbol=data.series.symbol, exchange="NSE", enabled="TRUE")]
    args = (members, registry(), (standard_window(END, 6),))
    assert len(list(run(*args, lambda s, m: data, Settings(), tariff(), tmp_path))) == 6
    changed = data.model_copy(update={"calendar_version": "CHANGED"})
    with pytest.raises(ValueError, match="CHECKPOINT_DATA"):
        list(run(*args, lambda s, m: changed, Settings(), tariff(), tmp_path))


def test_no_broker_or_network_dependencies() -> None:
    import ast

    package = ROOT / "apps/strategy-engine/src/strategy_engine/phase1415"
    forbidden = ("requests", "httpx", "socket", "urllib", "kiteconnect", "subprocess")
    for path in package.glob("*.py"):
        tree = ast.parse(path.read_text())
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                assert all(not x.name.startswith(forbidden) for x in node.names)
            if isinstance(node, ast.ImportFrom):
                assert not (node.module or "").startswith(forbidden)


def test_compact_reports_all_pairs_replay(tmp_path: Path) -> None:
    import csv
    import json

    from strategy_engine.phase1415.reports import generate

    universe = ROOT / "config/universes/phase14_unique_universe.csv"
    a = generate(universe, END, tmp_path / "a")
    b = generate(universe, END, tmp_path / "b")
    assert a == b and a["rows"] == 13380
    report = json.loads((tmp_path / "a/pair-results.json").read_bytes())
    assert len(report["rows"]) == 13380
    assert len({(r[1], r[2], r[4]) for r in report["rows"]}) == 13380
    assert all(r[6:10] == [None] * 4 and not r[10] for r in report["rows"])
    with (tmp_path / "a/pair-results.csv").open(newline="") as f:
        assert len(list(csv.DictReader(f))) == 13380


def test_regular_opening_range_and_true_session_reset() -> None:
    data = prices((100, 100, 100, 102, 102, 102))
    offset = timedelta(days=1)
    bars = data.bars + tuple(b.model_copy(update={"start": b.start + offset}) for b in data.bars)
    sessions = data.sessions + (Session(start=START + offset, end=bars[-1].end),)
    combined = Series(
        **(
            data.model_dump()
            | dict(bars=bars, sessions=sessions, to_exclusive=bars[-1].end, as_of=bars[-1].end)
        )
    )
    result = execute(combined, registry()[5], Settings(), tariff())
    assert len(result.trades) == 2
    assert result.trades[1].entry_at - result.trades[0].entry_at == offset
    shifted = data.model_copy(
        update={
            "sessions": (Session(start=START + FIVE, end=data.to_exclusive),),
            "bars": data.bars[1:],
        }
    )
    with pytest.raises(ValueError, match="OPENING_RANGE"):
        execute(shifted, registry()[5], Settings(), tariff())


@pytest.mark.parametrize(
    "classification", ["EXACT_STREAK_DEFINITION_VERIFIED", "UNVERIFIED_STRATEGY"]
)
def test_unestablished_catalog_claim_cannot_execute(classification: str) -> None:
    from strategy_engine.phase1415.strategies import Definition

    claimed = Definition.model_validate(registry()[1].model_dump() | {"provenance": classification})
    with pytest.raises(ValueError, match="UNRECOGNIZED_DEFINITION_VERSION"):
        execute(sample(), claimed, Settings(), tariff())
