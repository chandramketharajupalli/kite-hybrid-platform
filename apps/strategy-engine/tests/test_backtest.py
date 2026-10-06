import ast
import copy
import json
from datetime import UTC, datetime, timedelta
from decimal import Decimal, getcontext, localcontext
from pathlib import Path
from typing import Any

import pytest
from jsonschema import Draft202012Validator, FormatChecker
from pydantic import ValidationError

from strategy_engine.backtest.dataset import Bar, Dataset, bar_fingerprint, digest
from strategy_engine.backtest.engine import (
    Config,
    ConfiguredCosts,
    Decision,
    Intent,
    StrategySpec,
    canonical_json,
    run,
)

ROOT = Path(__file__).resolve().parents[3]
FIXTURE = ROOT / "contracts/fixtures/v1/historical-research.synthetic.json"


def raw() -> dict[str, Any]:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))  # type: ignore[no-any-return]


def dataset(data: dict[str, Any] | None = None) -> Dataset:
    value = raw() if data is None else data
    return Dataset.model_validate(value)


def rehash(data: dict[str, Any]) -> dict[str, Any]:
    data["content_fingerprint"] = bar_fingerprint(
        data["instrument_id"], tuple(Bar.model_validate(b) for b in data["bars"])
    )
    return data


def config(**changes: Any) -> Config:
    values = dict(initial_cash="1000", entry_start="09:15", last_entry_time="09:18",
                  forced_exit_time="09:19", slippage={"bps": "0"},
                  costs={"fixed_per_fill": "0", "notional_bps": "0"})
    values.update(changes)
    return Config.model_validate(values)


def spec(**changes: Any) -> StrategySpec:
    values = dict(name="fixture", version="v1", implementation_fingerprint="b" * 64,
                  parameters=[])
    values.update(changes)
    return StrategySpec.model_validate(values)


class Script:
    def __init__(self, requests: dict[int, Intent]) -> None:
        self.requests = requests
        self.seen: list[Decision] = []

    def on_decision(self, context: Decision) -> Intent | None:
        self.seen.append(context)
        return self.requests.get(len(context.completed_bars))


def buy(quantity: int = 2) -> Intent:
    return Intent(side="BUY", quantity=quantity, reason="FIXTURE")


def sell(quantity: int = 2) -> Intent:
    return Intent(side="SELL", quantity=quantity, reason="FIXTURE")


def test_shared_contract_and_hashes() -> None:
    schema = json.loads((ROOT / "contracts/schemas/v1/HistoricalResearchDataset.v1.schema.json")
                        .read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    Draft202012Validator(schema, format_checker=FormatChecker()).validate(raw())
    loaded = Dataset.from_json(FIXTURE.read_text(encoding="utf-8"))
    assert loaded == dataset()
    assert loaded.content_fingerprint == bar_fingerprint(loaded.instrument_id, loaded.bars)


def test_no_lookahead_and_next_open_fill_not_signal_close() -> None:
    strategy = Script({1: buy(), 3: sell()})
    result = run(dataset(), config(), spec(), strategy)
    for index, context in enumerate(strategy.seen):
        assert len(context.completed_bars) == index + 1
        assert all(b.end <= context.decision_time for b in context.completed_bars)
        assert context.completed_bars[-1].end == context.decision_time
    at_0917 = strategy.seen[1]
    assert [b.start.minute for b in at_0917.completed_bars] == [45, 46]
    assert result.fills[0].execution_time == strategy.seen[0].decision_time
    assert result.fills[0].fill_price == Decimal(102)
    assert result.fills[0].fill_price != strategy.seen[0].completed_bars[-1].close
    assert result.fills[1].fill_price == Decimal(106)
    assert result.final_cash == Decimal(1008)
    assert result.metrics.net_pnl == Decimal(8)
    assert result.final_position == 0
    assert result.equity_curve[-1].equity == result.final_cash
    with pytest.raises(ValidationError):
        strategy.seen[0].cash = Decimal(1)
    with pytest.raises(ValidationError):
        strategy.seen[0].completed_bars[0].close = Decimal(1)


def test_future_prices_cannot_change_earlier_decision_context() -> None:
    original = Script({1: buy(), 3: sell()})
    changed = Script({1: buy(), 3: sell()})
    data = raw()
    data["bars"][-1].update(open="900", high="901", low="899", close="900")
    run(dataset(), config(), spec(), original)
    run(dataset(rehash(data)), config(), spec(), changed)
    assert original.seen[:4] == changed.seen[:4]


def test_adverse_slippage_costs_and_exact_ledger_reconciliation() -> None:
    result = run(dataset(), config(slippage={"bps": "10"},
                                  costs={"fixed_per_fill": "1", "notional_bps": "2"}),
                 spec(), Script({1: buy(), 3: sell()}))
    assert result.fills[0].fill_price == Decimal("102.102")
    assert result.fills[1].fill_price == Decimal("105.894")
    assert result.metrics.gross_pnl == Decimal("7.584")
    assert result.metrics.costs == Decimal("2.0831984")
    assert result.metrics.net_pnl == Decimal("5.5008016")
    assert result.final_cash == Decimal("1005.5008016")
    assert sum(t.net_pnl for t in result.trades) == result.final_cash - Decimal(1000)
    assert result.metrics.return_percent == Decimal("0.55008016")
    assert result.metrics.win_rate_percent == Decimal("100.00000000")


def test_losing_trade_costs_increase_loss_and_drawdown_includes_initial_cash() -> None:
    data = raw()
    data["bars"][3].update(open="90", high="91", low="89", close="90")
    result = run(dataset(rehash(data)), config(costs={"fixed_per_fill": "1",
                                                   "notional_bps": "0"}),
                 spec(), Script({1: buy(), 3: sell()}))
    assert result.metrics.gross_pnl == -24
    assert result.metrics.net_pnl == -26
    assert result.metrics.losses == 1
    assert result.metrics.max_drawdown == 31  # peak 1005 to final 974
    assert result.metrics.max_drawdown_percent == Decimal("3.08457711")


def test_never_trade_and_no_trades_ratios_are_zero() -> None:
    result = run(dataset(), config(), spec(), Script({}))
    assert not result.fills and not result.trades and not result.outcomes
    assert result.metrics.net_pnl == result.metrics.win_rate_percent == 0
    assert all(p.equity == 1000 for p in result.equity_curve)


def test_forced_exit_has_priority_and_uses_configured_open() -> None:
    result = run(dataset(), config(), spec(), Script({1: buy(), 4: buy()}))
    assert result.fills[-1].forced
    assert result.fills[-1].fill_price == 108
    assert result.fills[-1].execution_time == datetime(2026, 7, 30, 3, 49, tzinfo=UTC)
    assert [o.outcome for o in result.outcomes] == [
        "FILLED", "SUPERSEDED_BY_FORCED_EXIT", "FILLED"
    ]


def test_final_signal_never_invents_future_fill() -> None:
    result = run(dataset(), config(), spec(), Script({5: buy()}))
    assert result.fills == ()
    assert result.outcomes[0].outcome == "UNFILLED_END_OF_DATA"


@pytest.mark.parametrize(("requests", "expected"), [
    ({1: sell()}, "OVERSELL"),
    ({1: buy(), 2: sell(3)}, "OVERSELL"),
    ({1: buy(), 2: sell(1)}, "PARTIAL_EXIT_UNSUPPORTED"),
    ({1: buy(), 2: buy()}, "ALREADY_LONG"),
    ({1: buy(100)}, "INSUFFICIENT_CASH"),
    ({4: buy()}, "OUTSIDE_ENTRY_WINDOW"),
])
def test_long_only_cash_and_entry_rejections(requests: dict[int, Intent], expected: str) -> None:
    result = run(dataset(), config(), spec(), Script(requests))
    assert expected in [o.outcome for o in result.outcomes]
    assert all(p.cash >= 0 and p.position_quantity >= 0 for p in result.equity_curve)
    assert result.final_position == 0


def test_exact_cash_boundary_includes_entry_costs() -> None:
    for cash, expected in [("205", "FILLED"), ("204.9999999999", "INSUFFICIENT_CASH")]:
        result = run(dataset(), config(initial_cash=cash,
                                      costs={"fixed_per_fill": "1", "notional_bps": "0"}),
                     spec(), Script({1: buy()}))
        assert result.outcomes[0].outcome == expected


def test_lot_size_and_zero_volume_denied() -> None:
    data = raw()
    data["lot_size"] = 3
    result = run(dataset(data), config(), spec(), Script({1: buy()}))
    assert result.outcomes[0].outcome == "LOT_SIZE_VIOLATION"
    data = raw()
    data["bars"][1]["volume"] = 0
    result = run(dataset(rehash(data)), config(), spec(), Script({1: buy()}))
    assert result.outcomes[0].outcome == "NO_EXECUTABLE_VOLUME"


def test_no_executable_forced_exit_invalidates_run() -> None:
    data = raw()
    data["bars"][-1]["volume"] = 0
    with pytest.raises(ValueError, match="FORCED_EXIT_UNAVAILABLE"):
        run(dataset(rehash(data)), config(), spec(), Script({1: buy()}))


@pytest.mark.parametrize("change", ["gap", "duplicate", "unsorted", "future", "outside"])
def test_dataset_quality_fails_even_when_hash_recomputed(change: str) -> None:
    data = raw()
    if change == "gap":
        del data["bars"][2]
    elif change == "duplicate":
        data["bars"].insert(1, data["bars"][0])
    elif change == "unsorted":
        data["bars"].reverse()
    elif change == "future":
        data["decision_cutoff"] = "2026-07-30T03:49:00Z"
    else:
        data["bars"][-1]["start"] = "2026-07-30T03:50:00Z"
    with pytest.raises(ValueError):
        dataset(rehash(data))


@pytest.mark.parametrize(("field", "value"), [
    ("open", "0"), ("high", "50"), ("low", "999"), ("close", "-1"),
    ("volume", -1), ("volume", True), ("volume", 1.0), ("open_interest", "-1"),
    ("close", 100.1), ("close", "NaN"), ("close", "1e2"),
    ("close", "1000000000000000000"), ("close", "100.00000000001"),
    ("start", "2026-02-30T03:45:00Z"), ("start", "2026-07-30T09:15:00+05:30"),
    ("start", "2026-07-30T03:45:01Z"),
])
def test_invalid_bar_inputs(field: str, value: Any) -> None:
    data = raw()
    data["bars"][0][field] = value
    with pytest.raises(ValueError):
        dataset(data)


def test_unknown_calendar_and_unpinned_or_tampered_inputs_deny() -> None:
    for field, value in [("content_fingerprint", "0" * 64),
                         ("interval", "FIVE_MINUTE"), ("exchange", "BSE"),
                         ("timestamp_semantics", "INTERVAL_END"), ("provenance", [])]:
        data = raw()
        data[field] = value
        with pytest.raises(ValueError):
            dataset(data)
    data = raw()
    data["calendar"]["days"] = []
    data["calendar"]["fingerprint"] = digest(
        data["calendar"]["version"] + "\n" + data["calendar"]["source"] + "\n"
    )
    data["provenance"][0]["calendar_fingerprint"] = data["calendar"]["fingerprint"]
    with pytest.raises(ValueError, match="UNKNOWN_SESSION"):
        dataset(data)


def test_json_rejects_trailing_document_duplicate_keys_and_unknown_fields() -> None:
    text = FIXTURE.read_text(encoding="utf-8")
    for invalid in [text + "{}", text.replace('"lot_size": 1', '"lot_size": 1, "lot_size": 2'),
                    text.replace('"lot_size": 1', '"lot_size": 1, "token": "forbidden"')]:
        with pytest.raises(ValueError):
            Dataset.from_json(invalid)


def test_determinism_isolated_decimal_context_scale_and_result_identity() -> None:
    result = run(dataset(), config(), spec(), Script({1: buy(), 3: sell()}))
    assert result.fingerprint == "523723b226fb27f04c460f3b4ecfbf5c087be05d37c95ce6dd997637bedba7e5"
    with localcontext() as ambient:
        ambient.prec = 3
        repeat = run(dataset(), config(), spec(), Script({1: buy(), 3: sell()}))
    assert canonical_json(result) == canonical_json(repeat)
    assert result.fingerprint == repeat.fingerprint
    scaled = config(initial_cash="1000.00")
    assert run(dataset(), scaled, spec(), Script({1: buy(), 3: sell()})).fingerprint == (
        result.fingerprint
    )
    changed = run(dataset(), config(), spec(version="v2"), Script({1: buy(), 3: sell()}))
    assert changed.fingerprint != result.fingerprint
    data = raw()
    data["bars"][0]["close"] = "101.0000"
    assert dataset(data).content_fingerprint == dataset().content_fingerprint


def test_callback_cannot_change_engine_decimal_context() -> None:
    class MutatingPrecision:
        def on_decision(self, context: Decision) -> Intent | None:
            getcontext().prec = 2
            return buy() if len(context.completed_bars) == 1 else None

    assert run(dataset(), config(), spec(), MutatingPrecision()) == run(
        dataset(), config(), spec(), Script({1: buy()})
    )


def test_strategy_failure_is_bounded() -> None:
    class Broken:
        def on_decision(self, context: Decision) -> Intent | None:
            raise RuntimeError("sensitive provider detail must not escape")

    with pytest.raises(ValueError, match="^STRATEGY_FAILURE$"):
        run(dataset(), config(), spec(), Broken())


@pytest.mark.parametrize("changes", [
    {"initial_cash": "0"}, {"initial_cash": "-1"}, {"slippage": {"bps": "10000"}},
    {"slippage": {"bps": "-1"}}, {"last_entry_time": "09:19"},
    {"entry_start": "09:15:01"}, {"costs": {"fixed_per_fill": "-1", "notional_bps": "0"}},
])
def test_invalid_config(changes: dict[str, Any]) -> None:
    with pytest.raises(ValueError):
        config(**changes)


def test_session_policy_outside_confirmed_session_denied() -> None:
    with pytest.raises(ValueError, match="SESSION_POLICY_OUTSIDE_SESSION"):
        run(dataset(), config(entry_start="09:14"), spec(), Script({}))


def test_research_dependency_and_clock_boundaries() -> None:
    allowed = {"__future__", "datetime", "decimal", "typing", "uuid", "zoneinfo", "hashlib",
               "json", "re", "pydantic", "strategy_engine.backtest.dataset",
               "strategy_engine.backtest.costs", "strategy_engine.backtest.corpus"}
    for source in (ROOT / "apps/strategy-engine/src/strategy_engine/backtest").glob("*.py"):
        tree = ast.parse(source.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                assert all(alias.name in allowed for alias in node.names), source
            if isinstance(node, ast.ImportFrom):
                assert node.module in allowed, source
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute):
                assert node.func.attr not in {"now", "today", "utcnow", "uuid4"}, source
                if node.func.attr == "astimezone":
                    assert len(node.args) == 1, source


def calendar_hash(data: dict[str, Any]) -> None:
    calendar = data["calendar"]
    text = calendar["version"] + "\n" + calendar["source"] + "\n"
    for day in calendar["days"]:
        sessions = ", ".join(f"Session[open={s['open']}, close={s['close']}]"
                             for s in day["sessions"])
        text += f"{day['date']}=Day[status={day['status']}, sessions=[{sessions}]]\n"
    calendar["fingerprint"] = digest(text)
    for source in data["provenance"]:
        source["calendar_fingerprint"] = calendar["fingerprint"]


def test_multi_session_no_overnight_and_no_pending_intent_crosses_nontrading_date() -> None:
    data = raw()
    second_bars = copy.deepcopy(data["bars"])
    for bar in second_bars:
        bar["start"] = bar["start"].replace("07-30", "08-01")
    data["bars"] += second_bars
    day = copy.deepcopy(data["calendar"]["days"][0])
    day["date"] = "2026-08-01"
    data["calendar"]["days"] += [
        dict(date="2026-07-31", status="NON_TRADING_DAY", sessions=[]), day
    ]
    data["to_exclusive"] = data["decision_cutoff"] = "2026-08-01T03:50:00Z"
    data["dataset_cutoff"] = "2026-08-02T00:00:00Z"
    calendar_hash(data)
    result = run(dataset(rehash(data)), config(), spec(), Script({1: buy(), 5: buy(), 6: buy()}))
    assert [o.outcome for o in result.outcomes] == [
        "FILLED", "FILLED", "UNFILLED_SESSION_END", "FILLED", "FILLED"
    ]
    assert len(result.trades) == 2
    assert result.trades[0].exit.forced and result.trades[1].exit.forced
    assert result.equity_curve[4].position_quantity == 0
    assert result.equity_curve[-1].position_quantity == 0


@pytest.mark.parametrize("kind", ["partial", "split", "unknown", "holiday_bars"])
def test_confirmed_calendar_semantics_fail_closed(kind: str) -> None:
    data = raw()
    day = data["calendar"]["days"][0]
    if kind == "partial":
        data["from_inclusive"] = "2026-07-30T03:46:00Z"
        del data["bars"][0]
    elif kind == "split":
        day["sessions"] = [dict(open="09:15", close="09:17"),
                           dict(open="09:18", close="09:20")]
    else:
        day["status"] = "UNKNOWN_SESSION" if kind == "unknown" else "NON_TRADING_DAY"
        day["sessions"] = []
    calendar_hash(data)
    with pytest.raises(ValueError):
        dataset(rehash(data))


def test_missing_forced_exit_bar_is_invalid_before_any_strategy_evaluation() -> None:
    data = raw()
    data["bars"].pop()
    strategy = Script({1: buy()})
    with pytest.raises(ValueError):
        run(dataset(rehash(data)), config(), spec(), strategy)
    assert not strategy.seen


def test_large_decimal_and_quantity_inputs_do_not_overflow_or_round_cash_up() -> None:
    data = raw()
    for bar in data["bars"]:
        for field in ["open", "high", "low", "close"]:
            bar[field] = "999999999999999999.1234567890"
        bar["volume"] = 9_223_372_036_854_775_807
    result = run(dataset(rehash(data)), config(initial_cash="999999999999999999.1234567890"),
                 spec(), Script({1: buy(1)}))
    assert result.final_cash == Decimal("999999999999999999.1234567890")
    assert result.metrics.net_pnl == 0
    result = run(dataset(), config(), spec(), Script({1: buy(2_147_483_647)}))
    assert result.outcomes[0].outcome == "INSUFFICIENT_CASH"
    with pytest.raises(ValueError):
        buy(2_147_483_648)


def test_dataset_cutoff_and_parameters_are_part_of_result_identity() -> None:
    baseline = run(dataset(), config(), spec(), Script({}))
    data = raw()
    data["dataset_cutoff"] = "2026-08-02T00:00:00Z"
    revised = run(dataset(data), config(), spec(), Script({}))
    assert revised.dataset_fingerprint == baseline.dataset_fingerprint
    assert revised.fingerprint != baseline.fingerprint
    revised = run(dataset(), config(), spec(parameters=[dict(name="n", value="2")]), Script({}))
    assert revised.fingerprint != baseline.fingerprint
    with pytest.raises(ValueError):
        spec(parameters=[dict(name="b", value="2"), dict(name="a", value="1")])


def test_provenance_order_and_calendar_date_tokens_are_strict() -> None:
    data = raw()
    data["provenance"] *= 2
    with pytest.raises(ValueError, match="PROVENANCE_MUST_BE_UNIQUE_AND_SORTED"):
        dataset(data)
    data = raw()
    data["calendar"]["days"][0]["date"] = 1_785_369_600
    with pytest.raises(ValueError, match="ISO_DATE_REQUIRED"):
        dataset(data)


def test_scaled_prices_and_input_precision_do_not_change_results() -> None:
    data = raw()
    for bar in data["bars"]:
        for field in ["open", "high", "low", "close"]:
            bar[field] += ".0000000000"
    original = run(dataset(), config(), spec(), Script({1: buy(), 3: sell()}))
    assert run(dataset(data), config(), spec(), Script({1: buy(), 3: sell()})) == original
    assert run(dataset(data), config(), spec(), Script({1: buy(), 3: sell()})).fingerprint == (
        original.fingerprint
    )


def test_pure_cost_and_slippage_models_ignore_ambient_precision_and_deny_invalid_inputs() -> None:
    models = config(slippage={"bps": "10"},
                    costs={"fixed_per_fill": "1", "notional_bps": "2"})
    assert isinstance(models.costs, ConfiguredCosts)
    with localcontext() as context:
        context.prec = 2
        assert models.slippage.price("BUY", Decimal("102")) == Decimal("102.102")
        assert models.costs.costs(Decimal("204.204")) == Decimal("1.0408408")
    for invalid in [Decimal(-1), Decimal("NaN"), Decimal("Infinity")]:
        with pytest.raises(ValueError):
            models.slippage.price("BUY", invalid)
        with pytest.raises(ValueError):
            models.costs.costs(invalid)
    for invalid in [Decimal("1e100000"), Decimal("1e-100000"), Decimal("NaN")]:
        with pytest.raises(ValueError):
            config(initial_cash=invalid)


def test_no_implicit_calendar_timezone_and_completed_bar_boundary() -> None:
    first = dataset().bars[0]
    assert first.end == first.start + timedelta(minutes=1)
    assert first.start.tzinfo == UTC
    from strategy_engine.backtest.dataset import NSE

    assert first.start.astimezone(NSE).strftime("%H:%M") == "09:15"
