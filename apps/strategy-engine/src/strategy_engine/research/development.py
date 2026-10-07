"""Development-only walk-forward: no TEST type, dates, callback or result branch."""
from collections.abc import Callable
from datetime import date
from decimal import ROUND_HALF_EVEN, Context, Decimal, localcontext
from typing import Literal

from pydantic import field_validator, model_validator

from strategy_engine.backtest.corpus import Corpus, slice_corpus
from strategy_engine.backtest.dataset import NSE, Frozen, Hash, Label, digest
from strategy_engine.backtest.engine import Config, Metrics, Result, canonical_json, run
from strategy_engine.research.experiments import Window
from strategy_engine.research.strategy import BaselineSpec, BaselineStrategy
from strategy_engine.research.walkforward import Fold, WalkForwardSpec

QUARANTINED = frozenset({
    "H1", "H2", "EMA_DIRECTIONAL_PERSISTENCE_G1", "VWAP_ONE_BAR_CONFIRMATION_G1",
    "17d249536d96a469442011454c42a99787a50d08966831b51938ac59964b7f04",
    "ccb5ebe8ad5fce034a30ddb6f8d4272d18b8c1136c7caac7698fd75b64bd6b67",
})


def quarantine(value: object) -> None:
    """Reject candidate envelopes/names/hashes before baseline schema parsing."""
    if isinstance(value, str) and value in QUARANTINED:
        raise ValueError("H1_H2_QUARANTINED_PENDING_SBIN_PROSPECTIVE_CONFIRMATION")
    if isinstance(value, Frozen):
        quarantine(value.model_dump())
    elif isinstance(value, dict):
        for item in value.values():
            quarantine(item)
    elif isinstance(value, (tuple, list)):
        for item in value:
            quarantine(item)


class DevelopmentWalkForwardSpec(Frozen):
    schema_version: Literal["DevelopmentWalkForward.v1"] = "DevelopmentWalkForward.v1"
    research_generation: Label
    corpus_fingerprint: Hash
    implementation_fingerprint: Hash
    common_window: Window
    folds: tuple[Fold, ...]
    strategies: tuple[BaselineSpec, ...]
    config: Config
    warmup: Literal["COLD_PER_PARTITION_AND_SESSION"] = "COLD_PER_PARTITION_AND_SESSION"
    engine_version: Literal["intraday-next-open-v3-session-corpus"] = (
        "intraday-next-open-v3-session-corpus"
    )

    @field_validator("config", mode="before")
    @classmethod
    def times(cls, value: object) -> object:
        return WalkForwardSpec.serialized_minute_times(value)

    @field_validator("strategies", mode="before")
    @classmethod
    def guard(cls, value: object) -> object:
        quarantine(value)
        return value

    @field_validator("strategies")
    @classmethod
    def ordered(cls, value: tuple[BaselineSpec, ...]) -> tuple[BaselineSpec, ...]:
        return tuple(sorted(value, key=lambda s: s.parameters.kind))

    @model_validator(mode="after")
    def valid(self) -> "DevelopmentWalkForwardSpec":
        if not 1 <= len(self.folds) <= 12 or not 1 <= len(self.strategies) <= 4:
            raise ValueError("DEVELOPMENT_BUDGET_INVALID")
        if len({s.parameters.kind for s in self.strategies}) != len(self.strategies):
            raise ValueError("DUPLICATE_STRATEGY_TRIAL")
        previous = None
        for fold in self.folds:
            if fold.train.start != self.common_window.start:
                raise ValueError("EXPANDING_START_CHANGED")
            if previous is not None and fold.train.end != previous.validation.end:
                raise ValueError("FOLDS_NOT_EXPANDING")
            previous = fold
        if self.folds[-1].validation.end != self.common_window.end:
            raise ValueError("DEVELOPMENT_RANGE_MISMATCH")
        if self.common_window.end.astimezone(NSE).date() > date(2026, 7, 1):
            raise ValueError("PHASE120_JULY_OR_LATER_DENIED")
        return self

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


class DevelopmentEvaluation(Frozen):
    instrument_id: str
    corpus_fingerprint: Hash
    strategy_identity: Hash
    strategy_kind: str
    fold_identity: Hash
    fold_number: int
    partition: Literal["TRAIN", "VALIDATION"]
    window: Window
    result_fingerprint: Hash
    metrics: Metrics
    raw_gross: Decimal
    slippage: Decimal
    traded_sessions: int
    turnover: Decimal
    repeated_entries: int


class DevelopmentReport(Frozen):
    specification: DevelopmentWalkForwardSpec
    evaluations: tuple[DevelopmentEvaluation, ...]
    selection: Literal["NONE"] = "NONE"

    @model_validator(mode="after")
    def valid(self) -> "DevelopmentReport":
        spec = self.specification
        expected = [(number, partition, window, strategy)
                    for number, fold in enumerate(spec.folds, 1)
                    for partition, window in (
                        ("TRAIN", fold.train), ("VALIDATION", fold.validation))
                    for strategy in spec.strategies]
        if len(expected) != len(self.evaluations):
            raise ValueError("INCOMPLETE_DEVELOPMENT_REPORT")
        instruments = {r.instrument_id for r in self.evaluations}
        if len(instruments) != 1:
            raise ValueError("REPORT_INSTRUMENT_MISMATCH")
        for row, (number, partition, window, strategy) in zip(
            self.evaluations, expected, strict=True
        ):
            identity = digest(canonical_json(strategy))
            fold = spec.folds[number - 1]
            expected_fold = digest(spec.fingerprint + "\n" + row.instrument_id + "\n"
                                   + canonical_json(fold) + "\n" + identity)
            if (row.fold_number != number or row.partition != partition or row.window != window
                    or row.corpus_fingerprint != spec.corpus_fingerprint
                    or row.strategy_identity != identity or row.fold_identity != expected_fold
                    or row.strategy_kind != strategy.parameters.kind):
                raise ValueError("DEVELOPMENT_RESULT_IDENTITY_MISMATCH")
        return self

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


def evaluate_development(
    corpus: Corpus, spec: DevelopmentWalkForwardSpec,
    retain: Callable[[Result], None] | None = None,
) -> DevelopmentReport:
    corpus = Corpus.model_validate(corpus.model_dump())
    spec = DevelopmentWalkForwardSpec.model_validate(spec.model_dump())
    if corpus.fingerprint != spec.corpus_fingerprint:
        raise ValueError("DEVELOPMENT_CORPUS_MISMATCH")
    if (corpus.from_inclusive, corpus.to_exclusive) != (
        spec.common_window.start, spec.common_window.end
    ):
        raise ValueError("DEVELOPMENT_RANGE_MISMATCH")
    # Verify every partition before the first simulated decision.
    for fold in spec.folds:
        for window in (fold.train, fold.validation):
            slice_corpus(corpus, window.start, window.end)
    evaluations = []
    for number, fold in enumerate(spec.folds, 1):
        partitions: tuple[tuple[Literal["TRAIN", "VALIDATION"], Window], ...] = (
            ("TRAIN", fold.train), ("VALIDATION", fold.validation))
        for partition, window in partitions:
            subset = slice_corpus(corpus, window.start, window.end)
            for strategy in spec.strategies:
                result = run(subset, spec.config, strategy.engine_spec(),
                             BaselineStrategy(specification=strategy, config=spec.config))
                if retain is not None:
                    retain(result)
                identity = digest(canonical_json(strategy))
                with localcontext(Context(prec=80, rounding=ROUND_HALF_EVEN)):
                    raw = sum(((t.exit.reference_price - t.entry.reference_price)
                               * t.entry.quantity for t in result.trades), Decimal(0))
                    slippage = sum((f.adverse_slippage * f.quantity for f in result.fills),
                                   Decimal(0))
                    turnover = sum((f.gross_notional for f in result.fills), Decimal(0))
                    if (raw - slippage != result.metrics.gross_pnl
                            or raw - slippage - result.metrics.costs != result.metrics.net_pnl):
                        raise ValueError("RESEARCH_ACCOUNTING_MISMATCH")
                sessions = len({t.entry.execution_time.astimezone(NSE).date()
                                for t in result.trades})
                evaluations.append(DevelopmentEvaluation(
                    instrument_id=corpus.instrument_id, corpus_fingerprint=corpus.fingerprint,
                    strategy_identity=identity, strategy_kind=strategy.parameters.kind,
                    fold_identity=digest(spec.fingerprint + "\n" + corpus.instrument_id + "\n"
                                         + canonical_json(fold) + "\n" + identity),
                    fold_number=number, partition=partition, window=window,
                    result_fingerprint=result.fingerprint, metrics=result.metrics,
                    raw_gross=raw, slippage=slippage, traded_sessions=sessions,
                    turnover=turnover, repeated_entries=len(result.trades) - sessions))
    return DevelopmentReport(specification=spec, evaluations=tuple(evaluations))
