"""Frozen candidate mechanics on synthetic February data; no broker or holdout reads."""

import ast
from datetime import timedelta
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Literal

import pytest
from test_backtest import Script, spec
from test_diagnostics import completed, development_day, short_corpus
from test_research import baseline, settings

from strategy_engine.backtest.dataset import NSE
from strategy_engine.backtest.engine import Config, Decision, Position, canonical_json, run
from strategy_engine.research.candidate_evaluation import run_reuse, validate_reuse
from strategy_engine.research.candidate_features import signed_efficiency_30
from strategy_engine.research.candidates import (
    JULY_END,
    JULY_START,
    CandidateSpec,
    CandidateStrategy,
    reject_sealed_window,
)
from strategy_engine.research.features import CompletedSession
from strategy_engine.research.strategy import BaselineStrategy


def definition(h: Literal["H1", "H2"]) -> CandidateSpec:
    return CandidateSpec(
        hypothesis=h,
        implementation_fingerprint="a" * 64,
        comparator=(
            baseline("EMA_CROSS", fast=9, slow=21) if h == "H1" else baseline("VWAP_CROSS")
        ),
    )


def contexts(prices: list[int], h: Literal["H1", "H2"] = "H2") -> tuple[Config, list[Decision]]:
    data = development_day(prices)
    config = settings(data)
    collector = Script({})
    run(data, config, spec(), collector)
    return config, [
        d.model_copy(update={"parameters": definition(h).engine_spec().parameters})
        for d in collector.seen
    ]


def test_h1_exact_window_boundary_threshold_zero_sign_and_precision() -> None:
    data = development_day([100, 103, 101] + [101] * 28)
    assert signed_efficiency_30(completed(data, 30)) is None
    assert signed_efficiency_30(completed(data)) == Decimal("0.20")
    view = completed(data)
    last = view.bars[-1].model_copy(update={"close": Decimal("100.99999999")})
    below = view.model_copy(update={"bars": view.bars[:-1] + (last,)})
    value = signed_efficiency_30(below)
    assert value is not None and value < Decimal("0.20")
    assert signed_efficiency_30(completed(development_day([100] * 31))) is None
    assert signed_efficiency_30(completed(development_day(list(range(100, 131))))) == 1
    assert signed_efficiency_30(completed(development_day(list(range(131, 100, -1))))) == -1
    with localcontext() as context:
        context.prec = 4
        assert signed_efficiency_30(below) == value
    # Exactly the trailing 30 changes, not a longer/session statistic.
    extended = completed(development_day([50] * 20 + [100, 103, 101] + [101] * 28))
    assert signed_efficiency_30(extended) == Decimal("0.20")
    with pytest.raises(ValueError):
        signed_efficiency_30(
            view.model_copy(update={"opened_at": view.opened_at + timedelta(days=1)})
        )


def test_h1_veto_is_skipped_then_fresh_cross_can_enter() -> None:
    prices = [100] * 21 + [90] * 5 + [110] * 10 + [80] * 15 + [140] * 12
    config, decisions = contexts(prices, "H1")
    candidate = CandidateStrategy(definition("H1"), config)
    comparator = BaselineStrategy(specification=definition("H1").comparator, config=config)
    baseline_buys, candidate_buys = [], []
    for i, decision in enumerate(decisions):
        original = comparator.on_decision(
            decision.model_copy(
                update={"parameters": definition("H1").comparator.engine_spec().parameters}
            )
        )
        proposed = candidate.on_decision(decision)
        if original is not None:
            baseline_buys.append(i)
        if proposed is not None:
            candidate_buys.append(i)
    assert len(baseline_buys) == 2
    assert baseline_buys[0] < 30
    assert candidate_buys == [baseline_buys[1]]


def test_h1_gate_exact_threshold_inclusive_on_real_crossover(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    import strategy_engine.research.candidates as module

    config, decisions = contexts([100] * 31 + [90] * 5 + [140] * 8, "H1")
    baseline_strategy = BaselineStrategy(specification=definition("H1").comparator, config=config)
    crossings = [
        d
        for d in decisions
        if baseline_strategy.on_decision(
            d.model_copy(
                update={"parameters": definition("H1").comparator.engine_spec().parameters}
            )
        )
        is not None
    ]
    assert len(crossings) == 1
    for value, permits in (
        (Decimal("0.20"), True),
        (Decimal("0.19999999"), False),
        (None, False),
        (Decimal("-0.5"), False),
    ):
        monkeypatch.setattr(module, "signed_efficiency_30", lambda view, value=value: value)
        proposal = CandidateStrategy(definition("H1"), config).on_decision(crossings[0])
        assert (proposal is not None) == permits


def test_h2_cross_pending_confirmation_and_earliest_fill() -> None:
    prices = [10, 9, 11, 12, 8, 8, 8, 8]
    config, decisions = contexts(prices)
    strategy = CandidateStrategy(definition("H2"), config)
    assert all(strategy.on_decision(d) is None for d in decisions[:3])
    assert (
        strategy.pending is not None and strategy.pending.crossover_at == decisions[2].decision_time
    )
    proposal = strategy.on_decision(decisions[3])
    assert proposal is not None and proposal.side == "BUY" and strategy.pending is None
    data = development_day(prices)
    result = run(
        data, config, definition("H2").engine_spec(), CandidateStrategy(definition("H2"), config)
    )
    assert result.fills[0].execution_time == data.bars[4].start
    assert result.fills[0].execution_time == decisions[3].decision_time
    assert result.trades[0].exit.execution_time == data.bars[5].start
    assert result.trades[0].exit.forced is False


@pytest.mark.parametrize("confirmation", [9, 10])
def test_h2_rejects_below_or_equal_and_needs_fresh_cross(confirmation: int) -> None:
    # 10 is equal to prefix VWAP after closes 10,9,11,10.
    config, decisions = contexts([10, 9, 11, confirmation, 12, 13, 13, 13, 13])
    strategy = CandidateStrategy(definition("H2"), config)
    for d in decisions[:3]:
        assert strategy.on_decision(d) is None
    assert strategy.pending is not None
    assert strategy.on_decision(decisions[3]) is None and strategy.pending is None
    assert strategy.on_decision(decisions[4]) is None  # Fresh crossover, not late confirmation.
    assert strategy.pending is not None
    assert strategy.on_decision(decisions[5]) is not None


def test_h2_never_waits_third_bar_after_failed_confirmation() -> None:
    config, decisions = contexts([10, 9, 11, 9, 9, 9, 9, 9])
    strategy = CandidateStrategy(definition("H2"), config)
    assert all(strategy.on_decision(d) is None for d in decisions)
    assert strategy.pending is None


def test_h2_missing_reversed_repeated_callbacks_discard_pending() -> None:
    config, decisions = contexts([10, 9, 11, 12, 13, 14, 14, 14])
    for invalid_index in (2, 1, 4):
        strategy = CandidateStrategy(definition("H2"), config)
        for d in decisions[:3]:
            strategy.on_decision(d)
        assert strategy.pending is not None
        assert strategy.on_decision(decisions[invalid_index]) is None
        assert strategy.pending is None
    strategy = CandidateStrategy(definition("H2"), config)
    for d in decisions[:3]:
        strategy.on_decision(d)
    missing = decisions[3].model_copy(update={"completed_bars": decisions[3].completed_bars[1:]})
    with pytest.raises(ValueError, match="FUTURE_OR_MISSING"):
        strategy.on_decision(missing)
    assert strategy.pending is None


def test_h2_entry_expiry_session_end_and_next_session_reset() -> None:
    config, decisions = contexts([10, 9, 11, 12, 12, 12, 12, 12])
    expiry = config.model_copy(
        update={"last_entry_time": decisions[2].decision_time.astimezone(NSE).time()}
    )
    strategy = CandidateStrategy(definition("H2"), expiry)
    for d in decisions[:3]:
        strategy.on_decision(d)
    assert strategy.pending is not None
    assert strategy.on_decision(decisions[3]) is None and strategy.pending is None
    # Direct synthetic end callback: no pending state survives even if entry cutoff allowed.
    strategy = CandidateStrategy(definition("H2"), config)
    for d in decisions[:3]:
        strategy.on_decision(d)
    end = decisions[3].model_copy(update={"session_close": decisions[3].decision_time})
    assert strategy.on_decision(end) is None and strategy.pending is None
    strategy = CandidateStrategy(definition("H2"), config)
    for d in decisions[:3]:
        strategy.on_decision(d)
    next_day = decisions[0].model_copy(
        update={
            "decision_time": decisions[0].decision_time + timedelta(days=1),
            "session_open": decisions[0].session_open + timedelta(days=1),
            "session_close": decisions[0].session_close + timedelta(days=1),
            "completed_bars": tuple(
                b.model_copy(update={"start": b.start + timedelta(days=1)})
                for b in decisions[0].completed_bars
            ),
        }
    )
    assert strategy.on_decision(next_day) is None and strategy.pending is None


@pytest.mark.parametrize("hypothesis", ["H1", "H2"])
def test_future_mutation_exit_and_session_warmup_invariance(
    hypothesis: Literal["H1", "H2"],
) -> None:
    prices = [100] * 31 + [90] * 5 + [140] * 10 + [80] * 8
    config, first = contexts(prices, hypothesis)
    _, changed = contexts(prices[:-5] + [1000] * 5, hypothesis)
    left, right = (CandidateStrategy(definition(hypothesis), config) for _ in range(2))
    for a, b in zip(first[:-5], changed[:-5], strict=True):
        assert left.on_decision(a) == right.on_decision(b)
        assert left.pending == right.pending
    # On identical long contexts every ordinary exit equals the original baseline.
    baseline_strategy = BaselineStrategy(
        specification=definition(hypothesis).comparator, config=config
    )
    strategy = CandidateStrategy(definition(hypothesis), config)
    sells = 0
    for decision in first:
        long = decision.model_copy(
            update={
                "position": Position(
                    quantity=1,
                    entry_price=Decimal(100),
                    entry_costs=Decimal(0),
                    unrealized_gross=Decimal(0),
                )
            }
        )
        original = baseline_strategy.on_decision(
            long.model_copy(
                update={"parameters": definition(hypothesis).comparator.engine_spec().parameters}
            )
        )
        assert strategy.on_decision(long) == original
        sells += original is not None
    assert sells > 0
    # Fresh session's small prefix cannot borrow the previous day's efficiency warmup.
    assert signed_efficiency_30(CompletedSession.from_decision(first[0])) is None


@pytest.mark.parametrize("hypothesis", ["H1", "H2"])
def test_engine_forced_exit_next_open_and_fresh_run_repeatability(
    hypothesis: Literal["H1", "H2"],
) -> None:
    prices = [100] * 31 + [90] * 5 + [140] * 12 if hypothesis == "H1" else [10, 9, 11] + [12] * 10
    data = development_day(prices)
    cfg = settings(data)
    definition_ = definition(hypothesis)
    first = run(data, cfg, definition_.engine_spec(), CandidateStrategy(definition_, cfg))
    second = run(data, cfg, definition_.engine_spec(), CandidateStrategy(definition_, cfg))
    assert first.fingerprint == second.fingerprint
    assert len(first.trades) == 1 and first.trades[0].exit.forced
    buy_outcome = next(o for o in first.outcomes if o.side == "BUY" and o.outcome == "FILLED")
    assert first.trades[0].entry.execution_time == buy_outcome.created_at
    # Intent uses completed bar end; execution is next bar open, never signal bar open.
    assert buy_outcome.created_at > data.bars[0].start


def test_july_overlap_rejected_before_candidate_or_comparator_and_no_diagnostic_imports() -> None:
    for start, end in (
        (JULY_START, JULY_END),
        (JULY_START - timedelta(days=1), JULY_START + timedelta(days=1)),
        (JULY_END - timedelta(days=1), JULY_END + timedelta(days=1)),
    ):
        with pytest.raises(ValueError, match="JULY_TEST_SEALED"):
            reject_sealed_window(start, end)
    reject_sealed_window(JULY_START - timedelta(days=1), JULY_START)
    reject_sealed_window(JULY_END, JULY_END + timedelta(days=1))
    data = short_corpus([10, 9, 11, 12, 8, 8, 8, 8])
    for role in ("CANDIDATE", "COMPARATOR"):
        with pytest.raises(ValueError, match="JULY_TEST_SEALED"):
            run_reuse(
                data.model_copy(update={"to_exclusive": JULY_END}),
                settings(data.shards[0]),
                definition("H2"),
                role,
            )
    with pytest.raises(ValueError, match="DEVELOPMENT_REUSE_ONLY"):
        validate_reuse(
            data.model_copy(
                update={"from_inclusive": JULY_END, "to_exclusive": JULY_END + timedelta(days=1)}
            )
        )
    with pytest.raises(ValueError, match="FROZEN_COMPARATOR"):
        CandidateSpec(
            hypothesis="H1",
            implementation_fingerprint="a" * 64,
            comparator=baseline("EMA_CROSS", fast=8, slow=20),
        )
    assert CandidateSpec.model_validate_json(canonical_json(definition("H1"))) == definition("H1")
    root = Path(__file__).resolve().parents[1] / "src/strategy_engine/research"
    for name in ("candidates.py", "candidate_features.py"):
        for node in ast.walk(ast.parse((root / name).read_text())):
            if isinstance(node, ast.ImportFrom):
                assert "diagnostics" not in (node.module or "")
                assert all(
                    a.name not in {"Dataset", "Corpus", "Result", "DiagnosticReport"}
                    for a in node.names
                )


def test_reuse_reports_reconcile_and_never_score_confirmation() -> None:
    from datetime import date

    from strategy_engine.research.candidate_evaluation import ReuseReport, describe
    from strategy_engine.research.costs import nse_intraday_snapshot

    data = short_corpus([10, 9, 11, 12, 8, 8, 8, 8])
    cfg = settings(
        data.shards[0],
        slippage={"bps": "5"},
        costs=nse_intraday_snapshot(fixed_as_of=date(2026, 10, 6)),
    )
    candidate = definition("H2")
    result = run_reuse(data, cfg, candidate, "CANDIDATE")
    record = describe(data, result, candidate, "CANDIDATE")
    assert record["trade_count"] == 1 and record["short_round_trips_le_5"] == 1
    assert record["raw_gross"] - record["slippage"] - record["fees"] == record["net"]
    assert record["net"] == result.metrics.net_pnl
    assert len(record["daily"]) == 1
    report = ReuseReport(
        freeze_fingerprint="a" * 64,
        registration_fingerprint="b" * 64,
        parent_corpus_fingerprint="c" * 64,
        parent_report_fingerprint="d" * 64,
        diagnostic_fingerprint="e" * 64,
        implementation_fingerprints=("f" * 64,),
        cost_version="test",
        evaluations=(record,),
    )
    assert ReuseReport.model_validate_json(canonical_json(report)).fingerprint == report.fingerprint
    assert report.usage == "DEVELOPMENT_REUSE_ONLY"
    assert report.confirmation_state == "NOT_ASSESSED_ON_REUSED_DEVELOPMENT"
    with localcontext() as ctx:
        ctx.prec = 4
        assert describe(data, result, candidate, "CANDIDATE") == record


def test_source_freeze_rejects_changes_and_conflicting_writes(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    import importlib

    monkeypatch.syspath_prepend(str(Path(__file__).resolve().parents[3] / "scripts/research"))
    runner = importlib.import_module("run_phase116")
    body = runner.freeze_body()
    path = tmp_path / "freeze.json"
    text = runner.canonical(
        {"freeze": body, "freeze_fingerprint": runner.digest(runner.canonical(body))}
    )
    runner.pin(path, text)
    identity, _ = runner.verify_freeze(path)
    assert identity == runner.digest(runner.canonical(body))
    with pytest.raises(ValueError, match="PINNED_RESEARCH_ARTIFACT_CONFLICT"):
        runner.pin(path, text + " ")
    monkeypatch.setattr(runner, "sources", lambda: {"modified": "0" * 64})
    with pytest.raises(ValueError, match="FROZEN_IMPLEMENTATION_CHANGED"):
        runner.verify_freeze(path)


def test_callbacks_reject_july_and_future_prefix_before_any_entry() -> None:
    for hypothesis in ("H1", "H2"):
        config, decisions = contexts([10, 9, 11, 12, 8, 8, 8, 8], hypothesis)
        strategy = CandidateStrategy(definition(hypothesis), config)
        delta = JULY_START - decisions[0].completed_bars[0].start + timedelta(hours=9, minutes=15)
        original = decisions[2]
        assert original.session_open is not None and original.session_close is not None
        july = original.model_copy(
            update={
                "session_open": original.session_open + delta,
                "session_close": original.session_close + delta,
                "decision_time": original.decision_time + delta,
                "completed_bars": tuple(
                    b.model_copy(update={"start": b.start + delta}) for b in original.completed_bars
                ),
            }
        )
        with pytest.raises(ValueError, match="JULY_TEST_SEALED"):
            strategy.on_decision(july)
        with pytest.raises(ValueError, match="FUTURE_BAR"):
            strategy.on_decision(
                original.model_copy(update={"completed_bars": decisions[3].completed_bars})
            )
        assert strategy.pending is None
