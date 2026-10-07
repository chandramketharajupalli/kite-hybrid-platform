import ast
import copy
from datetime import UTC, date, datetime, timedelta
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any

import pytest
from pydantic import ValidationError
from test_backtest import Script, buy, calendar_hash, config, raw, rehash, sell, spec

from strategy_engine.backtest.dataset import NSE, Dataset, digest, instant_text
from strategy_engine.backtest.engine import Config, Decision, canonical_json, run
from strategy_engine.research.costs import audit_costs, nse_intraday_snapshot
from strategy_engine.research.experiments import (
    ExperimentSpec,
    FrozenSelection,
    compare_development,
    evaluate_development,
    evaluate_final_test,
    freeze_selection,
)
from strategy_engine.research.features import (
    CompletedSession,
    atr,
    ema,
    opening_range,
    relative_volume,
    rolling_high,
    rolling_low,
    rsi,
    simple_return,
    sma,
    volume_sma,
    vwap,
)
from strategy_engine.research.strategy import BaselineSpec, BaselineStrategy


def sample(closes: list[int], *, days: int = 1, volumes: list[int] | None = None) -> Dataset:
    data = raw()
    data["bars"] = []
    data["calendar"]["days"] = []
    start = datetime(2026, 7, 27, 3, 45, tzinfo=UTC)
    for day in range(days):
        opened = start + timedelta(days=day)
        closed = opened + timedelta(minutes=len(closes))
        data["calendar"]["days"].append(dict(
            date=opened.date().isoformat(), status="EXPECTED_SESSION",
            sessions=[dict(open="09:15", close=closed.astimezone(NSE).strftime("%H:%M"))]))
        for i, price in enumerate(closes):
            data["bars"].append(dict(start=instant_text(opened + timedelta(minutes=i)),
                                     open=str(price), high=str(price + 1), low=str(price - 1),
                                     close=str(price),
                                     volume=100 if volumes is None else volumes[i],
                                     open_interest=None))
    data["from_inclusive"] = instant_text(start)
    data["to_exclusive"] = data["decision_cutoff"] = instant_text(closed)
    calendar_hash(data)
    return Dataset.model_validate(rehash(data))


def settings(dataset: Dataset, **updates: Any) -> Config:
    end = dataset.calendar.days[0].sessions[0].close
    close = datetime.combine(date(2026, 7, 27), end)
    values: dict[str, Any] = dict(
        last_entry_time=(close - timedelta(minutes=2)).strftime("%H:%M"),
        forced_exit_time=(close - timedelta(minutes=1)).strftime("%H:%M"))
    values.update(updates)
    return config(**values)


def contexts(data: Dataset) -> list[Decision]:
    script = Script({})
    run(data, settings(data), spec(), script)
    return script.seen


def view(prices: list[int], *, volumes: list[int] | None = None) -> CompletedSession:
    # Session policy requires at least three bars; retain the requested prefix only.
    data = sample(prices + [prices[-1]] * max(0, 3 - len(prices)), volumes=(
        None if volumes is None else volumes + [volumes[-1]] * max(0, 3 - len(prices))))
    return CompletedSession.from_decision(contexts(data)[len(prices) - 1])


def baseline(kind: str, **parameters: Any) -> BaselineSpec:
    return BaselineSpec.model_validate(dict(version="v1", implementation_fingerprint="c" * 64,
                                           parameters=dict(kind=kind, quantity=1, **parameters)))


@pytest.mark.parametrize("n", [1, 2, 3, 5])
def test_sma_ema_warmup_n_minus_one_n_n_plus_one(n: int) -> None:
    prices = list(range(10, 10 + n + 1))
    if n > 1:
        assert sma(view(prices[:n - 1]), n) is None
        assert ema(view(prices[:n - 1]), n) is None
    expected_seed = Decimal(2 * 10 + n - 1) / 2
    assert sma(view(prices[:n]), n) == expected_seed
    assert ema(view(prices[:n]), n) == expected_seed
    assert sma(view(prices), n) == expected_seed + 1
    assert ema(view(prices), n) == expected_seed + 1


def test_returns_extrema_and_volume_definitions() -> None:
    assert simple_return(view([10])) is None
    assert simple_return(view([10, 11])) == Decimal("0.1")
    assert simple_return(view([10, 10])) == 0
    assert simple_return(view([10, 9])) == Decimal("-0.1")
    bars = view([10, 20, 30], volumes=[10, 20, 60])
    assert rolling_high(bars, 2) == 31
    assert rolling_high(bars, 2, exclude_current=True) == 21
    assert rolling_low(bars, 2) == 19
    assert rolling_low(bars, 2, exclude_current=True) == 9
    assert rolling_high(bars, 3, exclude_current=True) is None
    assert volume_sma(bars, 2) == 40
    assert relative_volume(bars, 2) == 4
    assert relative_volume(view([10, 11, 12], volumes=[0, 0, 5]), 2) is None
    assert vwap(view([10, 20], volumes=[1, 3])) == Decimal("17.5")
    assert vwap(view([10, 20], volumes=[0, 0])) is None


def test_wilder_rsi_atr_known_sequences_and_edge_cases() -> None:
    assert rsi(view([10, 12]), 2) is None
    result = rsi(view([10, 12, 11]), 2)
    assert result is not None and result.quantize(Decimal("0.000001")) == Decimal("66.666667")
    result = rsi(view([10, 12, 11, 13]), 2)
    assert result is not None and result.quantize(Decimal("0.000001")) == Decimal("85.714286")
    assert rsi(view([10, 11, 12]), 2) == 100
    assert rsi(view([12, 11, 10]), 2) == 0
    assert rsi(view([10, 10, 10]), 2) == 50
    assert atr(view([10]), 2) is None
    assert atr(view([10, 13]), 2) == 3
    assert atr(view([10, 13, 12]), 2) == Decimal("2.5")


def test_feature_view_rejects_future_gap_and_absent_session_evidence() -> None:
    ctx = contexts(sample([10, 11, 12]))
    with pytest.raises(ValueError, match="FUTURE_BAR"):
        CompletedSession.from_decision(ctx[0].model_copy(update={"completed_bars":
                                                                ctx[1].completed_bars}))
    with pytest.raises(ValueError, match="SESSION_EVIDENCE_REQUIRED"):
        CompletedSession.from_decision(ctx[0].model_copy(update={"session_open": None}))
    with pytest.raises(ValueError, match="FUTURE_OR_MISSING_BAR"):
        CompletedSession.from_decision(ctx[2].model_copy(update={"completed_bars":
                                                               ctx[2].completed_bars[1:]}))


def test_session_reset_uses_confirmed_open_and_excludes_previous_session() -> None:
    seen = contexts(sample([10, 20, 30], days=2))
    second = CompletedSession.from_decision(seen[3])
    assert len(second.bars) == 1
    assert vwap(second) == 10
    assert sma(second, 2) is None
    assert rsi(second, 2) is None
    assert atr(second, 1) == 2  # Not yesterday's close 30.
    assert second.opened_at == seen[3].session_open


def test_features_ignore_ambient_precision_and_round_recurring_divisions() -> None:
    bars = view([10, 12, 11, 13])
    expected = (ema(bars, 2), rsi(bars, 2), atr(bars, 3), vwap(bars))
    with localcontext() as ambient:
        ambient.prec = 2
        assert (ema(bars, 2), rsi(bars, 2), atr(bars, 3), vwap(bars)) == expected


@pytest.mark.parametrize("n", [0, -1, 1441, True])
def test_invalid_lookbacks_rejected(n: int) -> None:
    with pytest.raises(ValueError):
        ema(view([10, 11, 12]), n)


@pytest.mark.parametrize(("definition", "prices", "entry_minute"), [
    (baseline("EMA_CROSS", fast=2, slow=3), [10, 9, 8, 10, 12, 8, 7, 7], 50),
    (baseline("VWAP_CROSS"), [10, 9, 11, 12, 8, 8, 8, 8], 48),
    (baseline("OPENING_RANGE", opening_bars=2), [10, 9, 12, 13, 7, 7, 7, 7], 48),
    (baseline("RSI_RECOVERY", lookback=2, oversold="30", exit_threshold="70"),
     [12, 10, 8, 10, 12, 12, 12, 12], 49),
])
def test_baseline_crossover_entries_next_open_exits_and_repeatability(
    definition: BaselineSpec, prices: list[int], entry_minute: int
) -> None:
    data = sample(prices)
    cfg = settings(data)
    strategy = BaselineStrategy(specification=definition, config=cfg)
    result = run(data, cfg, definition.engine_spec(), strategy)
    assert result.fills[0].execution_time.minute == entry_minute
    assert len(result.fills) == 2
    assert not result.fills[-1].forced
    assert result.final_position == 0
    assert result == run(data, cfg, definition.engine_spec(), strategy)  # no hidden run state
    assert result.fingerprint == run(data, cfg, definition.engine_spec(), strategy).fingerprint


def test_opening_range_unknown_until_complete_and_entry_requires_later_bar() -> None:
    views = [CompletedSession.from_decision(c) for c in contexts(sample([10, 12, 14, 16, 18]))]
    assert opening_range(views[0], 2) is None
    assert opening_range(views[1], 2) == (Decimal(9), Decimal(13))
    definition = baseline("OPENING_RANGE", opening_bars=2)
    data = sample([10, 12, 14, 16, 18])
    strategy = BaselineStrategy(specification=definition, config=settings(data))
    result = run(data, settings(data), definition.engine_spec(), strategy)
    assert result.fills[0].execution_time.minute == 48


def test_strategy_parameters_immutable_invalid_and_entry_window_respected() -> None:
    with pytest.raises(ValueError):
        baseline("EMA_CROSS", fast=5, slow=5)
    with pytest.raises(ValueError):
        baseline("RSI_RECOVERY", lookback=2, oversold="70", exit_threshold="30")
    definition = baseline("OPENING_RANGE", opening_bars=2)
    with pytest.raises(ValidationError):
        definition.parameters.quantity = 5
    data = sample([10, 12, 14, 16, 18])
    cfg = settings(data, last_entry_time="09:16")
    strategy = BaselineStrategy(specification=definition, config=cfg)
    assert not run(data, cfg, definition.engine_spec(), strategy).fills


def test_calibrated_cost_components_sides_cap_stt_basis_and_no_double_count() -> None:
    model = nse_intraday_snapshot()
    when = datetime(2026, 10, 6, 4, tzinfo=UTC)
    buying = model.quote("BUY", Decimal(10000), when, None)
    selling = model.quote("SELL", Decimal(11000), when, Decimal(10000))
    assert buying.brokerage == 3
    assert buying.exchange == Decimal("0.30699")
    assert buying.ipft == Decimal("0.00001")
    assert buying.exchange + buying.ipft == Decimal("0.307")
    assert buying.sebi == Decimal("0.01")
    assert buying.gst == Decimal("0.59706")
    assert buying.stamp == Decimal("0.3") and buying.stt == 0
    assert buying.total == Decimal("4.21406")
    assert selling.stamp == 0 and selling.stt == Decimal("2.625")
    assert selling.total == Decimal("6.930466")
    assert model.quote("BUY", Decimal(100000), when, None).brokerage == 20
    assert model.quote("BUY", Decimal(1), when, None).brokerage == Decimal("0.0003")
    with localcontext() as context:
        context.prec = 2
        assert model.quote("BUY", Decimal(10000), when, None) == buying


def test_cost_dates_fail_closed_and_fixed_asof_is_explicit_in_fingerprint() -> None:
    data = sample([100, 101, 102, 103, 104])
    with pytest.raises(ValueError, match="COST_SCHEDULE_NOT_EFFECTIVE"):
        run(data, settings(data, costs=nse_intraday_snapshot()), spec(), Script({}))
    costs = nse_intraday_snapshot(fixed_as_of=date(2026, 10, 6))
    cfg = settings(data, costs=costs)
    result = run(data, cfg, spec(), Script({1: buy(), 3: sell()}))
    assert result.engine_version == "intraday-next-open-v2-costs"
    assert result.metrics.costs == sum(c.total for c in audit_costs(result))
    assert result.final_cash == cfg.initial_cash + result.metrics.net_pnl
    assert result.metrics.net_pnl < result.metrics.gross_pnl
    assert '"basis":"FIXED_AS_OF"' in canonical_json(result)
    with pytest.raises(ValueError):
        nse_intraday_snapshot(fixed_as_of=date(2026, 10, 7))


def experiment(data: Dataset, **updates: Any) -> ExperimentSpec:
    windows = []
    for day in data.calendar.days:
        opened = datetime.combine(day.date, day.sessions[0].open, NSE).astimezone(UTC)
        closed = datetime.combine(day.date, day.sessions[0].close, NSE).astimezone(UTC)
        windows.append(dict(start=opened, end=closed))
    values = dict(experiment_id="fixture-a",
                  dataset_artifact_fingerprint=digest(canonical_json(data)),
                  dataset_content_fingerprint=data.content_fingerprint,
                  instrument_id=data.instrument_id,
                  interval="MINUTE", strategy=baseline("VWAP_CROSS"),
                  config=settings(data, costs=nse_intraday_snapshot(fixed_as_of=date(2026, 10, 6))),
                  partitions=dict(train=windows[0], validation=windows[1], test=windows[2],
                                  warmup="COLD_PER_PARTITION_AND_SESSION"),
                  slippage_model_version="adverse-bps-v1", feature_version="session-decimal40-v1",
                  engine_version="intraday-next-open-v2-costs",
                  cost_model_version="nse-retail-intraday-20261006-v1")
    values.update(updates)
    return ExperimentSpec.model_validate(values)


def test_experiments_cold_partitions_default_test_exclusion_and_explicit_selection() -> None:
    data = sample([10, 9, 11, 12, 8, 8, 8, 8], days=3)
    definition = experiment(data)
    report = evaluate_development(data, definition)
    assert [e.partition for e in report.evaluations] == ["TRAIN", "VALIDATION"]
    for evaluation in report.evaluations:
        assert evaluation.result.equity_curve[0].position_quantity == 0
        assert evaluation.result.final_position == 0
        assert evaluation.result.metrics.costs == sum(c.total for c in evaluation.cost_breakdown)
    selection = FrozenSelection(selected_experiment_fingerprint=definition.fingerprint,
                                development_report_fingerprints=(report.fingerprint,),
                                trial_count=1, selection_criterion="PREDECLARED_FIXTURE")
    final = evaluate_final_test(data, definition, selection)
    assert [e.partition for e in final.evaluations] == ["TEST"]
    assert final.selection_fingerprint == selection.fingerprint
    assert report.fingerprint == evaluate_development(data, definition).fingerprint
    altered = definition.model_copy(update={"experiment_id": "changed"})
    with pytest.raises(ValueError, match="FROZEN_SELECTION_MISMATCH"):
        evaluate_final_test(data, altered, selection)
    assert freeze_selection((report,), definition.experiment_id,
                            "PREDECLARED_FIXTURE") == selection
    with pytest.raises(ValueError, match="TEST_MUST_NOT_SELECT_PARAMETERS"):
        freeze_selection((final,), definition.experiment_id, "FORBIDDEN_TEST_SELECTION")


def test_partition_overlaps_partial_sessions_pin_mismatch_and_comparisons() -> None:
    data = sample([10, 9, 11, 12, 8, 8, 8, 8], days=3)
    definition = experiment(data)
    values = definition.model_dump()
    values["partitions"]["validation"] = values["partitions"]["train"]
    with pytest.raises(ValueError, match="PARTITIONS_OVERLAP_OR_UNORDERED"):
        ExperimentSpec.model_validate(values)
    values = definition.model_dump()
    values["partitions"]["train"]["start"] += timedelta(minutes=1)
    changed = ExperimentSpec.model_validate(values)
    assert changed.fingerprint != definition.fingerprint
    with pytest.raises(ValueError, match="PARTIAL_SESSION"):
        evaluate_development(data, changed)
    changed = definition.model_copy(update={"dataset_content_fingerprint": "0" * 64})
    with pytest.raises(ValueError, match="EXPERIMENT_DATASET_MISMATCH"):
        evaluate_development(data, changed)
    alternative = experiment(data, experiment_id="fixture-b",
                             strategy=baseline("OPENING_RANGE", opening_bars=2))
    reports = compare_development(data, (alternative, definition))
    assert [r.specification.experiment_id for r in reports] == ["fixture-a", "fixture-b"]
    assert all(len(r.evaluations) == 2 for r in reports)
    with pytest.raises(ValueError):
        compare_development(data, (definition,) * 17)


def test_changing_only_test_bars_cannot_change_train_validation_outputs() -> None:
    data = sample([10, 9, 11, 12, 8, 8, 8, 8], days=3)
    original = evaluate_development(data, experiment(data))
    changed = copy.deepcopy(data.model_dump())
    for bar in changed["bars"][16:]:
        for name in ("open", "high", "low", "close"):
            bar[name] += Decimal(100)
    changed_data = Dataset.model_validate(rehash(changed))
    revised = evaluate_development(changed_data, experiment(changed_data))
    for first, second in zip(original.evaluations, revised.evaluations, strict=True):
        assert first.result.fills == second.result.fills
        assert first.result.metrics == second.result.metrics
        assert first.result.fingerprint == second.result.fingerprint
    assert original.fingerprint != revised.fingerprint  # Parent artifact pin changed.


def test_research_import_boundaries_and_no_wallclock_random_or_io() -> None:
    root = Path(__file__).resolve().parents[1] / "src/strategy_engine/research"
    allowed = {"datetime", "decimal", "typing", "pydantic"}
    # Phase 12 pure deterministic identity/statistics helpers only. No I/O capabilities.
    phase120_imports = {
        "universe.py": {"hashlib", "struct", "uuid"},
        "development.py": {"collections.abc"},
        "multi_instrument.py": {"collections.abc", "statistics"},
    }
    for source in root.glob("*.py"):
        for node in ast.walk(ast.parse(source.read_text(encoding="utf-8"))):
            names = ([a.name for a in node.names] if isinstance(node, ast.Import) else
                     [node.module or ""] if isinstance(node, ast.ImportFrom) else [])
            assert all(name in allowed | phase120_imports.get(source.name, set())
                       or name.startswith("strategy_engine.research.")
                       or name.startswith("strategy_engine.backtest.") for name in names), source
            if source.name in {"features.py", "strategy.py"} and isinstance(node, ast.ImportFrom):
                assert all(alias.name not in {"Dataset", "ExperimentSpec"} for alias in node.names)
            if isinstance(node, ast.Call):
                if isinstance(node.func, ast.Attribute):
                    assert node.func.attr not in {"now", "today", "utcnow", "shuffle", "sample"}
                if isinstance(node.func, ast.Name):
                    assert node.func.id not in {"open", "eval", "exec", "__import__"}


def test_all_features_and_baselines_are_prefix_invariant_under_future_price_change() -> None:
    original = sample([10, 9, 11, 12, 8, 8, 8, 8])
    mutated = sample([10, 9, 11, 12, 80, 80, 80, 80])
    before, after = contexts(original), contexts(mutated)
    definitions = (baseline("EMA_CROSS", fast=2, slow=3), baseline("VWAP_CROSS"),
                   baseline("OPENING_RANGE", opening_bars=2),
                   baseline("RSI_RECOVERY", lookback=2, oversold="30", exit_threshold="70"))
    for index in range(4):
        first, second = (CompletedSession.from_decision(values[index])
                         for values in (before, after))
        assert first == second
        assert (sma(first, 2), ema(first, 2), vwap(first), rsi(first, 2), atr(first, 2),
                relative_volume(first, 2), opening_range(first, 2)) == (
                    sma(second, 2), ema(second, 2), vwap(second), rsi(second, 2), atr(second, 2),
                    relative_volume(second, 2), opening_range(second, 2))
        for definition in definitions:
            strategy = BaselineStrategy(specification=definition, config=settings(original))
            values = {"parameters": definition.engine_spec().parameters}
            left = before[index].model_copy(update=values)
            right = after[index].model_copy(update=values)
            assert strategy.on_decision(left) == strategy.on_decision(right)


@pytest.mark.parametrize("value", ["0.0000000001", "0.1", "20", "999999999999999999"])
def test_cost_components_nonnegative_and_reconcile_at_decimal_boundaries(value: str) -> None:
    costs = nse_intraday_snapshot()
    notional = Decimal(value)
    when = datetime(2026, 10, 6, 4, tzinfo=UTC)
    for side in ("BUY", "SELL"):
        charges = costs.quote(side, notional, when, notional)
        assert all(v >= 0 for v in charges.model_dump().values())
        assert charges.total >= 0


def test_calibrated_cash_gate_and_forced_exit_costs_are_not_broker_squareoff_fees() -> None:
    data = sample([100, 100, 100, 100, 100])
    costs = nse_intraday_snapshot(fixed_as_of=date(2026, 10, 6))
    cfg = settings(data, initial_cash="200", costs=costs)
    rejected = run(data, cfg, spec(), Script({1: buy()}))
    assert rejected.outcomes[0].outcome == "INSUFFICIENT_CASH"
    cfg = settings(data, costs=costs)
    result = run(data, cfg, spec(), Script({1: buy()}))
    assert result.fills[-1].forced
    assert result.metrics.net_pnl == -result.metrics.costs
    assert result.metrics.costs == sum(c.total for c in audit_costs(result))
    assert result.fills[-1].costs < 1  # planned exit, no invented dealer/RMS 50-rupee fee


def test_wrong_model_versions_dates_and_strategy_binding_fail_closed() -> None:
    data = sample([10, 9, 11, 12, 8], days=3)
    with pytest.raises(ValueError, match="EXPERIMENT_MODEL_VERSION_MISMATCH"):
        experiment(data, cost_model_version="invented-version")
    model = nse_intraday_snapshot()
    for when in (datetime(2026, 10, 5, 4, tzinfo=UTC),
                 datetime(2026, 10, 7, 4, tzinfo=UTC)):
        with pytest.raises(ValueError, match="COST_SCHEDULE_NOT_EFFECTIVE"):
            model.quote("BUY", Decimal(100), when, None)
    with pytest.raises(ValueError, match="ENTRY_BASIS_REQUIRED"):
        model.quote("SELL", Decimal(100), datetime(2026, 10, 6, 4, tzinfo=UTC), None)
    definition = baseline("VWAP_CROSS")
    with pytest.raises(ValueError, match="STRATEGY_FAILURE"):
        run(data, settings(data), spec(),
            BaselineStrategy(specification=definition, config=settings(data)))


def test_experiments_stable_across_ambient_precision_and_distinguish_parameters() -> None:
    data = sample([10, 9, 11, 12, 8, 8], days=3)
    first = experiment(data)
    expected = evaluate_development(data, first)
    with localcontext() as context:
        context.prec = 2
        assert evaluate_development(data, first).fingerprint == expected.fingerprint
    alternate = experiment(data, strategy=baseline("EMA_CROSS", fast=2, slow=3))
    assert alternate.fingerprint != first.fingerprint


def test_calibrated_fees_use_adverse_fill_notional_and_official_stt_example() -> None:
    model = nse_intraday_snapshot(fixed_as_of=date(2026, 10, 6))
    data = sample([100, 100, 100, 100, 100])
    cfg = settings(data, costs=model, slippage={"bps": "10"})
    result = run(data, cfg, spec(), Script({1: buy(), 3: sell()}))
    assert result.fills[0].gross_notional == Decimal("200.2")
    assert audit_costs(result)[0].brokerage == Decimal("0.06006")
    assert result.fills[1].gross_notional == Decimal("199.8")
    assert result.metrics.net_pnl < result.metrics.gross_pnl < 0
    quote = model.quote("SELL", Decimal(500 * 105),
                        datetime(2026, 10, 6, 4, tzinfo=UTC), Decimal(500 * 100))
    assert quote.stt == Decimal("12.8125")
    from decimal import ROUND_HALF_UP

    # Official example settles at 13; the engine explicitly retains unrounded accrual.
    assert quote.stt.quantize(Decimal(1), rounding=ROUND_HALF_UP) == 13


def test_warmup_extrema_volume_and_atr_n_minus_one_n_n_plus_one() -> None:
    before, ready, after = view([10, 11]), view([10, 11, 12]), view([10, 11, 12, 13])
    for feature in (rolling_high, rolling_low, volume_sma, atr):
        assert feature(before, 3) is None
        assert feature(ready, 3) is not None
        assert feature(after, 3) is not None


def test_changing_current_bar_range_cannot_rewrite_opening_range() -> None:
    first = view([10, 11, 100])
    second = view([10, 11, 1000])
    assert opening_range(first, 2) == opening_range(second, 2)
    assert rolling_high(first, 2, exclude_current=True) == (
        rolling_high(second, 2, exclude_current=True)
    )


def test_repeated_above_vwap_is_not_repeated_crossover_entry() -> None:
    data = sample([10, 9, 11, 12, 13, 14])
    definition = baseline("VWAP_CROSS")
    strategy = BaselineStrategy(specification=definition, config=settings(data))
    proposals = []
    for decision in contexts(data):
        bound = decision.model_copy(update={"parameters": definition.engine_spec().parameters})
        proposal = strategy.on_decision(bound)
        if proposal is not None:
            proposals.append(proposal)
    assert len(proposals) == 1  # All supplied contexts are flat; crossing prevents repeats.
    assert proposals[0].side == "BUY"
