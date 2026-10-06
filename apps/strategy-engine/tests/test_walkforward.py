from datetime import UTC, datetime, time, timedelta
from decimal import Decimal

import pytest
from test_backtest import Script, spec
from test_research import baseline, sample, settings

from strategy_engine.backtest.corpus import Corpus, calendar_subset, slice_corpus
from strategy_engine.backtest.dataset import NSE, Dataset, bar_fingerprint
from strategy_engine.backtest.engine import canonical_json, run
from strategy_engine.research.experiments import Window, partition_dataset
from strategy_engine.research.walkforward import (
    Fold,
    WalkForwardReport,
    WalkForwardSpec,
    evaluate_walk_forward,
)


def test_pinned_spec_and_report_round_trip_without_time_rounding() -> None:
    specification = definition(fixture())
    restored = WalkForwardSpec.model_validate_json(canonical_json(specification))
    assert restored == specification
    assert restored.fingerprint == specification.fingerprint
    report = WalkForwardReport(specification=specification, evaluations=())
    assert WalkForwardReport.model_validate_json(canonical_json(report)) == report
    values = specification.model_dump(mode="json")
    values["config"]["entry_start"] = "09:15:01"
    with pytest.raises(ValueError, match="MINUTE_TIME_REQUIRED"):
        WalkForwardSpec.model_validate(values)


def test_multi_month_composition_keeps_v1_bounds_and_matches_single_dataset_ledger() -> None:
    from test_backtest import rehash

    from strategy_engine.research.strategy import BaselineStrategy

    original = sample([10, 9, 11, 12, 8, 8, 8, 8])
    shards = []
    for offset in range(45):
        values = original.model_dump()
        for field in ("from_inclusive", "to_exclusive", "decision_cutoff", "dataset_cutoff"):
            values[field] += timedelta(days=offset)
        for bar in values["bars"]:
            bar["start"] += timedelta(days=offset)
        day = original.calendar.days[0].model_copy(update={
            "date": original.calendar.days[0].date + timedelta(days=offset)})
        calendar = calendar_subset(original.calendar, (day,))
        values["calendar"] = calendar.model_dump()
        for provenance in values["provenance"]:
            provenance["calendar_fingerprint"] = calendar.fingerprint
        shards.append(Dataset.model_validate(rehash(values)))
    days = tuple(s.calendar.days[0] for s in shards)
    calendars = (calendar_subset(original.calendar, days[:30]),
                 calendar_subset(original.calendar, days[30:]))
    corpus = Corpus(from_inclusive=shards[0].from_inclusive, to_exclusive=shards[-1].to_exclusive,
                    shards=tuple(shards), calendars=calendars,
                    content_fingerprint=bar_fingerprint(original.instrument_id,
                        tuple(b for s in shards for b in s.bars)))
    definition = baseline("VWAP_CROSS")
    cfg = settings(original)
    result = run(corpus, cfg, definition.engine_spec(), BaselineStrategy(
        specification=definition, config=cfg))
    assert result.final_cash == cfg.initial_cash + result.metrics.net_pnl
    assert result.metrics.trade_count == 45
    assert result.final_position == 0
    # Small identical data retains identical fills/accounting under both context contracts.
    short = fixture()
    legacy = sample([10, 9, 11, 12, 8, 8, 8, 8], days=4)
    left = run(short, cfg, definition.engine_spec(), BaselineStrategy(
        specification=definition, config=cfg))
    right = run(legacy, cfg, definition.engine_spec(), BaselineStrategy(
        specification=definition, config=cfg))
    assert left.fills == right.fills and left.metrics == right.metrics
    assert left.equity_curve == right.equity_curve


def fixture() -> Corpus:
    data = sample([10, 9, 11, 12, 8, 8, 8, 8], days=4)
    shards = tuple(partition_dataset(data, Window(
        start=data.from_inclusive + timedelta(days=n),
        end=data.from_inclusive + timedelta(days=n, minutes=8))) for n in range(4))
    return Corpus(from_inclusive=datetime.combine(
                      data.calendar.days[0].date, time(), NSE).astimezone(UTC),
                  to_exclusive=datetime.combine(data.calendar.days[-1].date + timedelta(days=1),
                                                time(), NSE).astimezone(UTC),
                  calendars=(data.calendar,), shards=shards,
                  content_fingerprint=data.content_fingerprint)


def definition(corpus: Corpus) -> WalkForwardSpec:
    start = corpus.from_inclusive
    def boundary(n: int) -> datetime:
        return start + timedelta(days=n)
    return WalkForwardSpec(
        research_generation="synthetic-v1", corpus_fingerprint=corpus.fingerprint,
        folds=(Fold(train=Window(start=start, end=boundary(1)),
                    validation=Window(start=boundary(1), end=boundary(2))),
               Fold(train=Window(start=start, end=boundary(2)),
                    validation=Window(start=boundary(2), end=boundary(3)))),
        final_test=Window(start=boundary(3), end=corpus.to_exclusive),
        strategies=(baseline("EMA_CROSS", fast=2, slow=3), baseline("VWAP_CROSS"),
                    baseline("OPENING_RANGE", opening_bars=2),
                    baseline("RSI_RECOVERY", lookback=2, oversold="30", exit_threshold="70")),
        config=settings(corpus.shards[0]))


def test_corpus_reuses_v1_validation_and_rejects_missing_duplicate_changed_identity() -> None:
    corpus = fixture()
    assert Corpus.model_validate(corpus.model_dump()) == corpus
    for shards in (corpus.shards[1:], corpus.shards + corpus.shards[-1:],
                   tuple(reversed(corpus.shards))):
        with pytest.raises(ValueError):
            Corpus.model_validate(corpus.model_copy(update={"shards": shards}).model_dump())
    values = corpus.model_dump()
    values["shards"][1]["symbol"] = "OTHER"
    with pytest.raises(ValueError, match="IDENTITY_MISMATCH"):
        Corpus.model_validate(values)
    values = corpus.model_dump()
    values["shards"][0]["bars"][0]["close"] = Decimal(11)
    with pytest.raises(ValueError, match="FINGERPRINT_MISMATCH"):
        Corpus.model_validate(values)


def test_unknown_calendar_partial_cutoff_and_content_pin_fail_closed() -> None:
    corpus = fixture()
    with pytest.raises(ValueError):
        slice_corpus(corpus, corpus.shards[0].from_inclusive + timedelta(minutes=1),
                     corpus.to_exclusive)
    with pytest.raises(ValueError):
        Corpus.model_validate(corpus.model_copy(update={"content_fingerprint": "0" * 64})
                              .model_dump())
    cal = corpus.calendars[0]
    bad = cal.days[0].model_copy(update={"status": "UNKNOWN_SESSION", "sessions": ()})
    values = corpus.model_dump()
    values["calendars"] = (calendar_subset(cal, (bad,) + cal.days[1:]),)
    with pytest.raises(ValueError, match="UNKNOWN_SESSION"):
        Corpus.model_validate(values)


def test_content_identity_ignores_acquisition_cutoff_but_not_calendar_or_data() -> None:
    corpus = fixture()
    values = corpus.model_dump()
    for shard in values["shards"]:
        shard["dataset_cutoff"] += timedelta(days=1)
    changed = Corpus.model_validate(values)
    assert changed.fingerprint == corpus.fingerprint
    assert canonical_json(changed) != canonical_json(corpus)


def test_corpus_context_is_session_bounded_closed_and_preserves_cash_ledger() -> None:
    corpus = fixture()
    strategy = Script({})
    result = run(corpus, settings(corpus.shards[0]), spec(), strategy)
    assert max(len(d.completed_bars) for d in strategy.seen) == 8
    assert all(b.end <= d.decision_time for d in strategy.seen for b in d.completed_bars)
    assert result.final_position == 0
    assert result.final_cash == result.config.initial_cash + result.metrics.net_pnl
    assert result.engine_version == "intraday-next-open-v3-session-corpus"


def test_walkforward_four_baselines_repeatable_cold_and_test_sealed() -> None:
    corpus = fixture()
    specification = definition(corpus)
    report = evaluate_walk_forward(corpus, specification)
    assert len(report.evaluations) == 16
    assert {e.partition for e in report.evaluations} == {"TRAIN", "VALIDATION"}
    assert all(e.window.end <= specification.final_test.start for e in report.evaluations)
    assert report.test_state == "SEALED_NOT_EVALUATED"
    assert report.selection == "NONE"
    assert report.fingerprint == evaluate_walk_forward(corpus, specification).fingerprint
    # Mutating only sealed TEST changes the corpus/spec pin, never development outputs.
    values = corpus.model_dump()
    for bar in values["shards"][-1]["bars"]:
        for field in ("open", "high", "low", "close"):
            bar[field] += 100
    from strategy_engine.backtest.dataset import Bar
    shard = values["shards"][-1]
    shard["content_fingerprint"] = bar_fingerprint(corpus.instrument_id,
        tuple(Bar.model_validate(b) for b in shard["bars"]))
    shards = tuple(Dataset.model_validate(s) for s in values["shards"])
    values["content_fingerprint"] = bar_fingerprint(corpus.instrument_id,
        tuple(b for s in shards for b in s.bars))
    changed = Corpus.model_validate(values)
    updated = specification.model_copy(update={"corpus_fingerprint": changed.fingerprint})
    other = evaluate_walk_forward(changed, updated)
    assert [(e.metrics, e.result_fingerprint) for e in report.evaluations] == [
        (e.metrics, e.result_fingerprint) for e in other.evaluations]


def test_fold_overlap_changed_training_start_trial_budget_and_wrong_pin_denied() -> None:
    corpus = fixture()
    specification = definition(corpus)
    values = specification.model_dump()
    values["folds"][1]["train"]["start"] += timedelta(minutes=1)
    with pytest.raises(ValueError, match="EXPANDING_START_CHANGED"):
        WalkForwardSpec.model_validate(values)
    values = specification.model_dump()
    values["strategies"] *= 5
    with pytest.raises(ValueError, match="BUDGET_INVALID"):
        WalkForwardSpec.model_validate(values)
    with pytest.raises(ValueError, match="CORPUS_MISMATCH"):
        evaluate_walk_forward(corpus, specification.model_copy(
            update={"corpus_fingerprint": "0" * 64}))


