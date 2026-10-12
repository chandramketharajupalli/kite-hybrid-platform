from datetime import UTC, date, datetime, timedelta
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any, Literal

import pytest
from pydantic import ValidationError

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.phase14.engine import (
    FIVE,
    Candle,
    Cross,
    Operand,
    Policy,
    Series,
    Session,
    Settings,
    Strategy,
    Trade,
    indicator,
    qualify,
    signal,
    simulate,
)
from strategy_engine.phase14.reporting import Window, evaluate_pairs, months_before
from strategy_engine.phase14.universe import audit, normalize, records

D = Decimal
ROOT = Path(__file__).resolve().parents[3]
START = datetime(2026, 10, 5, 3, 45, tzinfo=UTC)


def tariff() -> IntradayCostSchedule:
    # Explicit hypothetical tariff: fixture arithmetic, not a current broker fee assertion.
    return IntradayCostSchedule(
        version="synthetic-v1",
        source_version="SYNTHETIC_NOT_BROKER_TARIFF",
        effective_from=date(2025, 1, 1),
        effective_to=date(2027, 1, 1),
        basis="HISTORICAL",
        as_of=None,
        scope="NSE_CASH_SHARES_RESIDENT_RETAIL_FULL_CASH",
        rounding="EXACT_ACCRUAL_NOT_CONTRACT_NOTE",
        stt_basis="FLAT_ROUND_TRIP_AVERAGE",
        brokerage_rate=D("0.0003"),
        brokerage_cap=D(20),
        stt_rate=D("0.00025"),
        exchange_rate=D("0.00003"),
        ipft_rate=D("0.000001"),
        sebi_rate=D("0.000001"),
        gst_rate=D("0.18"),
        stamp_rate=D("0.00003"),
    )


def sample(prices: tuple[int, ...] = (99, 101, 101, 101, 101)) -> Series:
    bars = tuple(
        Candle(
            start=START + i * FIVE,
            open=D(p),
            high=D(p) + D("0.1"),
            low=D(p) - D("0.1"),
            close=D(p),
            volume=10000,
            open_interest=None,
        )
        for i, p in enumerate(prices)
    )
    return Series(
        symbol="SYNTHETIC&ONLY",
        instrument_token="123",
        source="SYNTHETIC_FIXTURE",
        synthetic=True,
        source_hash="a" * 64,
        master_hash="b" * 64,
        calendar_hash="c" * 64,
        from_inclusive=START,
        to_exclusive=bars[-1].end,
        as_of=bars[-1].end,
        corporate_actions_reviewed=True,
        sessions=(Session(start=START, end=bars[-1].end),),
        bars=bars,
    )


def cross100() -> Strategy:
    return Strategy(
        name="SYNTHETIC_CROSS100",
        confirmed=True,
        conditions=(
            Cross(
                left=Operand(indicator="CLOSE"), right=Operand(indicator="CONSTANT", value=D(100))
            ),
        ),
    )


def test_authoritative_universe_hashes_duplicates_order_and_idempotence() -> None:
    result = audit(ROOT / "input/phase14")
    assert (result["source_rows"], result["unique_rows"], result["removed_rows"]) == (
        1140,
        1115,
        25,
    )
    unique_hash = "4345207d985672c34e79347eafb13475ed8c7fe5f440914b25ac0999465c3729"
    duplicates_hash = "ae6d0c8a363959b6e6b9a5f62e1487b1caf77321dd2be981ba08136c40488300"
    assert result["hashes"] == {
        "universe(1).csv": "bec552ae3c23c0f22b8980cb0c933b300de53d475efab07c4ddd22de3c9a02ca",
        "phase14_unique_universe.csv": unique_hash,
        "phase14_removed_duplicates.csv": duplicates_hash,
    }
    rows = records(ROOT / "input/phase14/phase14_unique_universe.csv")
    assert normalize(rows) == (rows, [])
    assert any("&" in row["symbol"] for row in rows)
    a = {"symbol": "M&M", "exchange": "NSE", "enabled": "TRUE"}
    b = {"symbol": " m&m ", "exchange": " nse ", "enabled": "true"}
    assert normalize([a, b])[0] == [a]


@pytest.mark.parametrize(
    "prices,expected",
    [((100, 100), False), ((100, 101), True), ((101, 102), False), ((101, 100), False)],
)
def test_strict_cross_equality_edges(prices: tuple[int, ...], expected: bool) -> None:
    assert signal(tuple(map(D, prices)), cross100()) is expected


def test_ema_rsi_warmup_wilder_and_same_bar_conjunction() -> None:
    ema = Operand(indicator="EMA", period=21)
    rsi = Operand(indicator="RSI", period=14)
    assert indicator((D(100),) * 20, ema) is None
    assert indicator((D(100),) * 21, ema) == 100
    assert indicator((D(100),) * 14, rsi) is None
    assert indicator((D(100),) * 15, rsi) == 50
    assert indicator(tuple(map(D, range(15))), rsi) == 100
    assert indicator(tuple(map(D, range(15, 0, -1))), rsi) == 0
    both = Strategy(
        confirmed=True,
        conditions=(
            cross100().conditions[0],
            Cross(
                left=Operand(indicator="CLOSE"), right=Operand(indicator="CONSTANT", value=D(102))
            ),
        ),
    )
    assert not signal((D(99), D(101), D(103)), both)
    assert signal((D(99), D(103)), both)
    assert not signal(tuple(D(100) for _ in range(75)), Strategy())


def test_next_open_eod_costs_slippage_and_decimal_context_replay() -> None:
    data = sample()
    result = simulate(data, cross100(), Settings(), tariff())
    assert len(result.trades) == 1
    trade = result.trades[0]
    assert trade.signal_at == trade.entry_at == data.bars[2].start
    assert trade.entry == D(101) * D("1.0005")
    assert trade.reason == "EOD" and trade.exit_at == data.bars[-1].end
    assert trade.fees > 0 and trade.net < trade.gross < 0
    with localcontext() as context:
        context.prec = 6
        assert (
            simulate(data, cross100(), Settings(), tariff()).model_dump_json()
            == result.model_dump_json()
        )


def test_no_lookahead_and_previous_bar_lag() -> None:
    prefix = (D(99), D(101))
    assert signal(prefix, cross100())
    assert indicator(prefix, Operand(indicator="CLOSE", lag=1)) == 99
    a = simulate(sample((99, 101, 101, 101, 101)), cross100(), Settings(), tariff())
    b = simulate(sample((99, 101, 101, 110, 110)), cross100(), Settings(), tariff())
    assert a.trades[0].entry_at == b.trades[0].entry_at
    assert a.trades[0].entry == b.trades[0].entry


@pytest.mark.parametrize("gap,expected", [(False, D("98.98")), (True, D(90))])
def test_stop_target_collision_and_gap_take_conservative_stop(gap: bool, expected: Decimal) -> None:
    data = sample()
    bars = list(data.bars)
    index = 3 if gap else 2
    bars[index] = Candle(
        start=bars[index].start,
        open=D(90) if gap else D(101),
        high=D(110),
        low=D(89),
        close=D(101),
        volume=10000,
        open_interest=None,
    )
    data = Series.model_validate({**data.model_dump(), "bars": tuple(bars)})
    result = simulate(data, cross100(), Settings(slippage_bps=D(0)), tariff())
    assert result.trades[0].reason == "STOP"
    assert result.trades[0].exit == expected


def test_no_fills_for_capital_ceiling_volume_and_disabled_long() -> None:
    for config in (Settings(capital=D(1)), Settings(quantity=1000), Settings(allow_long=False)):
        result = simulate(sample(), cross100(), config, tariff())
        assert not result.trades and result.no_fills
    bars = list(sample().bars)
    bars[2] = bars[2].model_copy(update={"volume": 0})
    data = Series.model_validate({**sample().model_dump(), "bars": bars})
    assert not simulate(data, cross100(), Settings(), tariff()).trades
    bars = list(sample().bars)
    bars[-1] = bars[-1].model_copy(update={"volume": 0})
    with pytest.raises(ValueError, match="EXIT_NO_FILL"):
        simulate(
            Series.model_validate({**sample().model_dump(), "bars": bars}),
            cross100(),
            Settings(),
            tariff(),
        )


@pytest.mark.parametrize(
    "anomaly", ["duplicate", "out_of_order", "negative_volume", "ohlc", "timezone"]
)
def test_candle_anomalies_rejected(anomaly: str) -> None:
    raw = sample().model_dump()
    bars = list(sample().bars)
    if anomaly == "duplicate":
        raw["bars"] = (bars[0], *bars)
    elif anomaly == "out_of_order":
        raw["bars"] = tuple(reversed(bars))
    else:
        changed = bars[0].model_dump()
        updates: dict[str, dict[str, Any]] = {
            "negative_volume": {"volume": -1},
            "ohlc": {"low": D(200)},
            "timezone": {"start": START.replace(tzinfo=None)},
        }
        changed.update(updates[anomaly])
        raw["bars"] = (changed, *bars[1:])
    with pytest.raises(ValidationError):
        Series.model_validate(raw)


def test_missing_calendar_blocks_corporate_actions_and_short_fail_closed() -> None:
    data = sample()
    missing = Series.model_validate({**data.model_dump(), "bars": data.bars[:-1]})
    assert len(missing.quality()[0]) == 1
    with pytest.raises(ValueError, match="INCOMPLETE"):
        simulate(missing, cross100(), Settings(), tariff())
    with pytest.raises(ValueError, match="CORPORATE_ACTION"):
        simulate(
            data.model_copy(update={"corporate_actions_reviewed": False}),
            cross100(),
            Settings(),
            tariff(),
        )
    with pytest.raises(ValueError, match="UNSUPPORTED"):
        simulate(data, Strategy(direction="SHORT"), Settings(), tariff())
    with pytest.raises(ValidationError, match="CALENDAR_UNKNOWN"):
        Series.model_validate({**data.model_dump(), "sessions": ()})


def trade_at(at: datetime, win: bool) -> Trade:
    net = D(2) if win else D(-1)
    return Trade(
        signal_at=at,
        entry_at=at,
        exit_at=at + FIVE,
        entry=D(100),
        exit=D(100) + net,
        quantity=1,
        reason="EOD",
        gross=net + D("0.1"),
        fees=D("0.1"),
        net=net,
    )


@pytest.mark.parametrize("wins,expected", [(7, True), (6, False), (10, True)])
def test_pair_specific_oos_threshold_and_stability(wins: int, expected: bool) -> None:
    bounds = tuple(START + timedelta(days=i * 10) for i in range(4))
    trades = tuple(
        trade_at(bounds[fold] + timedelta(minutes=10 * i), i % 10 < wins)
        for fold in range(3)
        for i in range(100)
    )
    result = qualify(trades, D(10000), bounds[0], bounds, Policy(family_size=2230))
    assert result.qualified is expected
    assert result.out_of_sample.trades == 300
    assert result.out_of_sample.win_rate == D(wins) / 10


def test_small_sample_100_percent_and_negative_expectancy_rejected() -> None:
    bounds = tuple(START + timedelta(days=i * 10) for i in range(4))
    result = qualify((trade_at(START, True),), D(10000), START, bounds, Policy())
    assert not result.qualified and "INSUFFICIENT_OOS_TRADES" in result.reasons
    bad = tuple(
        trade_at(START + timedelta(minutes=i * 10), i % 10 < 8).model_copy(
            update={"net": D(1) if i % 10 < 8 else D(-100)}
        )
        for i in range(100)
    )
    result = qualify(bad, D(10000), START, bounds, Policy())
    assert "NON_POSITIVE_NET_EXPECTANCY" in result.reasons


def windows() -> tuple[Window, ...]:
    end = datetime(2026, 10, 1, tzinfo=UTC)
    bounds = tuple(datetime(2026, month, 1, tzinfo=UTC) for month in (7, 8, 9, 10))
    periods: tuple[Literal[6, 12], ...] = (6, 12)
    return tuple(
        Window(months=m, end=end, oos_start=bounds[0], fold_boundaries=bounds) for m in periods
    )


def test_complete_universe_separate_windows_no_fabricated_results() -> None:
    rows = records(ROOT / "input/phase14/phase14_unique_universe.csv")
    result = evaluate_pairs(rows + rows, (Strategy(),), windows(), {}, Settings(), tariff())
    assert len(result) == 2230
    assert len({(r.symbol, r.window.months) for r in result}) == 2230
    assert all(
        r.simulation is None and r.qualification is None and r.status == "REJECTED" for r in result
    )
    assert months_before(datetime(2026, 8, 31, tzinfo=UTC), 6).day == 28


@pytest.mark.parametrize("month", [1, 3, 7, 11])
def test_ist_has_no_dst_shift(month: int) -> None:
    from strategy_engine.backtest.dataset import NSE

    local = datetime(2026, month, 5, 9, 15, tzinfo=NSE)
    assert local.astimezone(UTC).hour == 3 and local.utcoffset() == timedelta(hours=5, minutes=30)


def test_reference_rsi_and_ema_joint_cross_enters_next_bar() -> None:
    prices = tuple(range(120, 99, -1)) + (115, 115, 115, 115)
    result = simulate(sample(prices), Strategy(), Settings(), tariff())
    assert len(result.trades) == 1
    assert result.trades[0].entry_at == START + 22 * FIVE
    assert result.trades[0].reason == "EOD"


def test_java_export_import_fingerprint_calendar_and_replay() -> None:
    import json

    from strategy_engine.phase14.imports import FiveMinuteExport, compose

    raw = json.loads(
        (ROOT / "contracts/fixtures/v1/historical-five-minute.synthetic.json").read_text()
    )
    chunk = FiveMinuteExport.model_validate(raw)
    series = compose(
        (chunk,),
        chunk.from_inclusive,
        chunk.to_exclusive,
        (chunk.calendar,),
        "a" * 64,
        synthetic=True,
        corporate_actions_reviewed=True,
    )
    assert series.quality() == ((), ()) and len(series.bars) == 1
    assert (
        series.model_dump_json()
        == compose(
            (chunk,),
            chunk.from_inclusive,
            chunk.to_exclusive,
            (chunk.calendar,),
            "a" * 64,
            synthetic=True,
            corporate_actions_reviewed=True,
        ).model_dump_json()
    )
    raw["bars"][0]["close"] = "100"
    with pytest.raises(ValidationError, match="HASH_MISMATCH"):
        FiveMinuteExport.model_validate(raw)


def test_insufficient_history_is_a_pair_rejection() -> None:
    data = sample()
    rows = [{"symbol": data.symbol, "exchange": "NSE", "enabled": "TRUE"}]
    result = evaluate_pairs(
        rows, (Strategy(),), windows(), {("NSE", data.symbol, 6): data}, Settings(), tariff()
    )
    assert "INSUFFICIENT_HISTORY_OR_IDENTITY_MISMATCH" in result[0].reasons
    assert result[1].simulation is None


def test_custom_rules_are_opt_in_and_synthetic_reports_validate_schema() -> None:
    import json

    import jsonschema

    from strategy_engine.phase14.imports import FiveMinuteExport
    from strategy_engine.phase14.reporting import PairReport

    with pytest.raises(ValidationError, match="EXPLICIT_CONFIRMATION"):
        Strategy(conditions=cross100().conditions)
    fixture = json.loads(
        (ROOT / "contracts/fixtures/v1/historical-five-minute.synthetic.json").read_text()
    )
    jsonschema.validate(fixture, FiveMinuteExport.model_json_schema())
    row = {"symbol": "SYNTHETIC&ONLY", "exchange": "NSE", "enabled": "TRUE"}
    result = evaluate_pairs([row], (Strategy(),), windows(), {}, Settings(), tariff())
    for report in result:
        jsonschema.validate(report.model_dump(mode="json"), PairReport.model_json_schema())


@pytest.mark.parametrize("months", [6, 12])
def test_long_window_calendar_coverage_is_explicit_and_simulated(months: int) -> None:
    # Artificial calendar for a bounds test only; not NSE holidays or invented market history.
    from strategy_engine.backtest.dataset import NSE

    window = next(w for w in windows() if w.months == months)
    first = months_before(window.end, months)
    data = sample()
    start_day = first.astimezone(NSE).date()
    last_day = (window.end - timedelta(microseconds=1)).astimezone(NSE).date()
    # Place the short synthetic session inside this particular requested window.
    shift = timedelta(days=10)
    moved_bars = tuple(b.model_copy(update={"start": b.start - shift}) for b in data.bars)
    session = Session(start=moved_bars[0].start, end=moved_bars[-1].end)
    closed = tuple(
        start_day + timedelta(days=i)
        for i in range((last_day - start_day).days + 1)
        if start_day + timedelta(days=i) != session.start.astimezone(NSE).date()
    )
    series = Series.model_validate(
        {
            **data.model_dump(),
            "from_inclusive": first,
            "to_exclusive": window.end,
            "as_of": window.end,
            "bars": moved_bars,
            "sessions": (session,),
            "non_trading_days": closed,
        }
    )
    row = {"symbol": series.symbol, "exchange": "NSE", "enabled": "TRUE"}
    report = evaluate_pairs(
        [row],
        (Strategy(),),
        (window,),
        {("NSE", series.symbol, months): series},
        Settings(),
        tariff(),
    )[0]
    assert report.status == "SIMULATED"
    assert report.qualification is not None and not report.qualification.qualified
