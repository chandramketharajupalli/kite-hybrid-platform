"""Synthetic boundaries/arithmetic only; no real corpus or broker connection."""

import ast
from datetime import timedelta
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any

import pytest
from test_backtest import Script, buy, rehash, sell, spec
from test_research import sample, settings

from strategy_engine.backtest.corpus import Corpus, calendar_subset
from strategy_engine.backtest.dataset import Dataset, bar_fingerprint
from strategy_engine.backtest.engine import Decision, Result, canonical_json, run
from strategy_engine.research.costs import nse_intraday_snapshot
from strategy_engine.research.diagnostics import (
    END,
    START,
    DiagnosticReport,
    analyze,
    excursions,
    guard_bars,
    holding_bucket,
    label,
    session_records,
    summary,
    tertiles,
    time_bucket,
    trade_records,
)
from strategy_engine.research.features import CompletedSession
from strategy_engine.research.opening_features import early_volume, opening_observation
from strategy_engine.research.walkforward import WalkForwardReport


def development_day(prices: list[int], offset: int = 0) -> Dataset:
    original = sample(prices)
    values = original.model_dump()
    delta = START + timedelta(hours=9, minutes=15, days=offset) - original.from_inclusive
    for key in ("from_inclusive", "to_exclusive", "decision_cutoff", "dataset_cutoff"):
        values[key] += delta
    for bar in values["bars"]:
        bar["start"] += delta
    day = original.calendar.days[0].model_copy(update={"date": values["from_inclusive"].date()})
    calendar = calendar_subset(original.calendar, (day,))
    values["calendar"] = calendar.model_dump()
    for provenance in values["provenance"]:
        provenance["calendar_fingerprint"] = calendar.fingerprint
    return Dataset.model_validate(rehash(values))


def short_corpus(prices: list[int], days: int = 1) -> Corpus:
    shards = tuple(development_day(prices, i) for i in range(days))
    calendar = calendar_subset(shards[0].calendar, tuple(s.calendar.days[0] for s in shards))
    return Corpus(
        from_inclusive=START,
        to_exclusive=START + timedelta(days=days),
        calendars=(calendar,),
        shards=shards,
        content_fingerprint=bar_fingerprint(
            shards[0].instrument_id, tuple(b for s in shards for b in s.bars)
        ),
    )


def completed(data: Dataset, count: int | None = None) -> CompletedSession:
    bars = data.bars if count is None else data.bars[:count]
    return CompletedSession(
        opened_at=data.bars[0].start,
        closes_at=data.bars[-1].end,
        decision_at=bars[-1].end,
        bars=bars,
    )


def simulated() -> tuple[Dataset, Result]:
    from datetime import date

    data = development_day([10, 10, 14, 8, 11, 90, 90, 90])
    cfg = settings(
        data, slippage={"bps": "5"}, costs=nse_intraday_snapshot(fixed_as_of=date(2026, 10, 6))
    )
    return data, run(data, cfg, spec(), Script({1: buy(), 4: sell()}))


def test_excursion_includes_entry_excludes_exit_high_low_and_later_bars() -> None:
    data, result = simulated()
    trade = result.trades[0]
    observed = excursions(trade, data.bars)
    assert observed["mfe"] == Decimal(15) - trade.entry.fill_price
    assert observed["mae"] == trade.entry.fill_price - Decimal(7)
    # Exit at index 4 open=11; changing its high and every later bar cannot leak.
    bars = list(data.bars)
    for i in range(4, len(bars)):
        bars[i] = bars[i].model_copy(update={"high": Decimal(10000), "low": Decimal("0.01")})
    assert observed == excursions(trade, tuple(bars))
    first = excursions(trade, data.bars, 1)
    assert first["observed_minutes"] == 1
    assert first["mfe"] == Decimal(11) - trade.entry.fill_price
    with pytest.raises(ValueError, match="COVERAGE_MISSING"):
        excursions(trade, data.bars[:2] + data.bars[3:])


def test_opening_windows_completion_and_future_prefix_invariance() -> None:
    data = development_day([10] * 35)
    for n in (5, 15, 30):
        assert opening_observation(completed(data, n - 1), n) is None
        value = opening_observation(completed(data, n), n)
        assert value is not None and value["available_at"] == data.bars[n - 1].end
        assert value == opening_observation(completed(data), n)
    with pytest.raises(ValueError, match="UNREGISTERED"):
        opening_observation(completed(data), 7)
    forged = completed(data, 5).model_copy(update={"bars": data.bars})
    with pytest.raises(ValueError):
        opening_observation(forged, 5)


def test_relative_volume_prior_only_minimum_history_and_zero_reference() -> None:
    history = tuple(completed(development_day([10] * 30, i)) for i in range(5))
    current = completed(development_day([10] * 30, 5))
    assert early_volume(current, history[:4]) is None
    assert early_volume(completed(development_day([10] * 30, 5), 14), history) is None
    value = early_volume(current, history)
    assert value is not None and value["value"] == 1 and value["reference_sessions"] == 5
    with pytest.raises(ValueError, match="FUTURE_OR_INCOMPLETE"):
        early_volume(current, history + (current,))
    with pytest.raises(ValueError, match="NOT_ORDERED"):
        early_volume(current, tuple(reversed(history)))
    zero = tuple(
        h.model_copy(update={"bars": tuple(b.model_copy(update={"volume": 0}) for b in h.bars)})
        for h in history
    )
    result = early_volume(current, zero)
    assert result is not None and result["value"] is None


def test_session_gap_uses_previous_confirmed_session_and_flat_efficiency_zero() -> None:
    corpus = short_corpus([10] * 30, 6)
    records, thresholds = session_records(corpus)
    assert records[0]["opening_gap"] is None
    assert records[1]["previous_confirmed_date"] == records[0]["date"]
    assert all(r["values"]["trend_efficiency"] == 0 for r in records)
    assert records[5]["early_relative_volume"]["value"] == 1
    assert thresholds["trend_efficiency"] == (Decimal(0), Decimal(0))
    assert label(Decimal(0), thresholds["trend_efficiency"]) == "LOW"
    with localcontext() as context:
        context.prec = 6
        assert session_records(corpus) == (records, thresholds)


def test_july_rejected_before_any_analysis_and_report_has_no_strategy_capability() -> None:
    july = sample([10] * 30)
    with pytest.raises(ValueError, match="SEALED_TEST"):
        guard_bars(july.bars)
    corpus = short_corpus([10] * 30)
    with pytest.raises(ValueError, match="DEVELOPMENT_ONLY"):
        analyze(corpus, WalkForwardReport.model_construct(), (), "a" * 64, "b" * 64)
    with pytest.raises(ValueError, match="SEALED_TEST"):
        session_records(corpus.model_copy(update={"shards": (july,)}))
    data, result = simulated()
    with pytest.raises(ValueError, match="SEALED_TEST"):
        trade_records(
            result.model_copy(update={"to_exclusive": END + timedelta(days=1)}), (), data.bars
        )
    assert not {"mfe", "mae", "regimes", "diagnostics"} & Decision.model_fields.keys()
    root = Path(__file__).resolve().parents[1] / "src/strategy_engine"
    for name in (
        "research/features.py",
        "research/opening_features.py",
        "research/strategy.py",
        "backtest/engine.py",
        "strategies.py",
    ):
        tree = ast.parse((root / name).read_text())
        for node in ast.walk(tree):
            if isinstance(node, ast.ImportFrom):
                assert "diagnostics" not in (node.module or "")
                assert all(a.name not in {"DiagnosticReport", "excursions"} for a in node.names)
            if isinstance(node, ast.Import):
                assert all("diagnostics" not in a.name for a in node.names)


def test_attribution_sample_flags_null_ratios_and_determinism() -> None:
    data, result = simulated()
    corpus = short_corpus([10, 10, 14, 8, 11, 90, 90, 90])
    sessions, _ = session_records(corpus)
    rows = trade_records(result, sessions, data.bars)
    assert len(rows) == 1
    stats = summary(rows, 1)
    assert stats["gross_before_slippage"] - stats["slippage"] == stats["gross_pnl"]
    assert stats["gross_pnl"] - stats["costs"] == stats["net_pnl"]
    assert stats["net_pnl"] == result.metrics.net_pnl
    assert stats["sample_status"] == "INSUFFICIENT_SAMPLE"
    assert summary([], 1)["costs_over_abs_aggregate_gross"] is None
    assert summary([], 1)["win_rate"] is None
    values: dict[str, Any] = dict(
        parent_corpus_fingerprint="a" * 64,
        phase114_report_fingerprint="b" * 64,
        plan_fingerprint="c" * 64,
        implementation_fingerprint="d" * 64,
        cost_version="test",
        slippage_version="test",
        thresholds={},
        sessions=sessions,
        evaluations=({"summary": stats},),
    )
    report = DiagnosticReport(**values)
    assert (
        DiagnosticReport.model_validate_json(canonical_json(report)).fingerprint
        == report.fingerprint
    )
    assert report.model_copy(update={"implementation_fingerprint": "e" * 64}).fingerprint != (
        report.fingerprint
    )


def test_registered_bucket_edges_and_ties() -> None:
    opened = START + timedelta(hours=9, minutes=15)
    assert time_bucket(opened) == "OPENING"
    assert time_bucket(opened + timedelta(minutes=30)) == "MORNING"
    assert time_bucket(opened + timedelta(minutes=135)) == "MIDDAY"
    assert time_bucket(opened + timedelta(minutes=255)) == "AFTERNOON"
    assert [holding_bucket(n) for n in (5, 6, 15, 16, 30, 31)] == [
        "LE_5",
        "6_15",
        "6_15",
        "16_30",
        "16_30",
        "GT_30",
    ]
    assert tertiles([Decimal(n) for n in range(1, 7)]) == (Decimal(2), Decimal(4))


def test_minimum_samples_edge_cases_and_three_fixed_fill_cost_scenarios() -> None:
    from strategy_engine.research.diagnostics import sensitivity

    data, result = simulated()
    sessions, _ = session_records(short_corpus([10, 10, 14, 8, 11, 90, 90, 90]))
    row = trade_records(result, sessions, data.bars)[0]
    rows = [dict(row, date=row["date"] + timedelta(days=i // 4)) for i in range(20)]
    assert summary(rows, 5)["sample_status"] == "SUFFICIENT_DESCRIPTIVE_SAMPLE"
    assert summary(rows[:-1], 5)["sample_status"] == "INSUFFICIENT_SAMPLE"
    assert summary([row] * 20, 5)["sample_status"] == "INSUFFICIENT_SAMPLE"
    for gross, net, expected in (
        (1, -1, "A_GROSS_POSITIVE_NET_NEGATIVE"),
        (-1, -2, "B_GROSS_NEGATIVE_NET_NEGATIVE"),
        (2, 1, "C_GROSS_POSITIVE_NET_POSITIVE"),
        (0, -1, "BOUNDARY_OR_EMPTY"),
    ):
        stats = summary([dict(row, gross_pnl=Decimal(gross), net_pnl=Decimal(net))], 1)
        assert stats["edge_case"] == expected
    scenarios = sensitivity(result)
    assert [s["bps"] for s in scenarios] == [0, 5, 10]
    assert scenarios[1]["net_pnl"] == result.metrics.net_pnl
    assert scenarios[0]["net_pnl"] > scenarios[1]["net_pnl"] > scenarios[2]["net_pnl"]
    assert all(s["usage"] == "FIXED_TRADES_REPRICING_ONLY" for s in scenarios)


def test_full_analysis_exact_replay_binding_holdout_rejection_and_determinism() -> None:
    from datetime import date

    from test_research import baseline

    from strategy_engine.backtest.corpus import slice_corpus
    from strategy_engine.backtest.dataset import NSE, Day, digest
    from strategy_engine.research.diagnostics import TEST_END
    from strategy_engine.research.experiments import Window
    from strategy_engine.research.strategy import BaselineStrategy
    from strategy_engine.research.walkforward import Fold, FoldEvaluation, WalkForwardSpec

    # A tiny synthetic corpus with explicit nontrading dates, never a real-data filter.
    last_offset = (END - START).days - 1
    shards = tuple(development_day([10, 9, 11, 12, 8, 8, 8, 8], i) for i in (0, last_offset))
    active = {s.calendar.days[0].date: s.calendar.days[0] for s in shards}
    dates = tuple(START.astimezone(NSE).date() + timedelta(days=i) for i in range(last_offset + 1))
    days = tuple(active.get(d, Day(date=d, status="NON_TRADING_DAY", sessions=())) for d in dates)
    calendars = tuple(
        calendar_subset(shards[0].calendar, days[i : i + 30]) for i in range(0, len(days), 30)
    )
    corpus = Corpus(
        from_inclusive=START,
        to_exclusive=END,
        shards=shards,
        calendars=calendars,
        content_fingerprint=bar_fingerprint(
            shards[0].instrument_id, tuple(b for s in shards for b in s.bars)
        ),
    )
    strategy = baseline("VWAP_CROSS")
    cfg = settings(
        shards[0], slippage={"bps": "5"}, costs=nse_intraday_snapshot(fixed_as_of=date(2026, 10, 6))
    )
    split = START + timedelta(days=last_offset)
    fold = Fold(train=Window(start=START, end=split), validation=Window(start=split, end=END))
    definition = WalkForwardSpec(
        research_generation="synthetic",
        corpus_fingerprint="a" * 64,
        folds=(fold,),
        strategies=(strategy,),
        config=cfg,
        final_test=Window(start=END, end=TEST_END),
    )
    results = tuple(
        run(
            slice_corpus(corpus, w.start, w.end),
            cfg,
            strategy.engine_spec(),
            BaselineStrategy(specification=strategy, config=cfg),
        )
        for w in (fold.train, fold.validation)
    )
    parent = WalkForwardReport(
        specification=definition,
        evaluations=tuple(
            FoldEvaluation(
                fold_identity="b" * 64,
                strategy_identity=digest(canonical_json(strategy)),
                fold_number=1,
                partition="TRAIN" if i == 0 else "VALIDATION",
                window=w,
                result_fingerprint=r.fingerprint,
                metrics=r.metrics,
            )
            for i, (w, r) in enumerate(zip((fold.train, fold.validation), results, strict=True))
        ),
    )
    first = analyze(corpus, parent, results, "c" * 64, "d" * 64)
    assert len(first.evaluations) == 2 and len(first.sessions) == 2
    assert first.sessions[1]["previous_confirmed_date"] == first.sessions[0]["date"]
    assert all(e["summary"]["trade_count"] == 1 for e in first.evaluations)
    with localcontext() as context:
        context.prec = 6
        assert first.fingerprint == analyze(corpus, parent, results, "c" * 64, "d" * 64).fingerprint
    with pytest.raises(ValueError, match="REPLAY_DIVERGED"):
        analyze(corpus, parent, tuple(reversed(results)), "c" * 64, "d" * 64)
    with pytest.raises(ValueError, match="MISSING_OR_DUPLICATE"):
        analyze(corpus, parent, results[:1], "c" * 64, "d" * 64)
    with pytest.raises(ValueError, match="SEALED_TEST"):
        analyze(
            corpus,
            parent,
            (results[0], results[1].model_copy(update={"to_exclusive": TEST_END})),
            "c" * 64,
            "d" * 64,
        )
    # No full corpus accepted, regardless of July values or even forged bounds.
    for price in (Decimal(1), Decimal(9999)):
        july = sample([10] * 8)
        july = july.model_copy(
            update={"bars": tuple(b.model_copy(update={"close": price}) for b in july.bars)}
        )
        with pytest.raises(ValueError, match="SEALED_TEST"):
            analyze(
                corpus.model_copy(update={"shards": shards + (july,)}),
                parent,
                results,
                "c" * 64,
                "d" * 64,
            )
