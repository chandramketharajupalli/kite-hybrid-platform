"""Independent fixed baselines, complete member results, descriptive breadth only."""
from collections.abc import Callable
from decimal import ROUND_HALF_EVEN, Context, Decimal, localcontext
from statistics import median
from typing import Annotated, Literal

from pydantic import Field, field_validator, model_validator

from strategy_engine.backtest.dataset import Frozen, Hash, Label, digest
from strategy_engine.backtest.engine import Result, canonical_json
from strategy_engine.research.development import (
    DevelopmentEvaluation,
    DevelopmentReport,
    DevelopmentWalkForwardSpec,
    evaluate_development,
)
from strategy_engine.research.universe import (
    MultiInstrumentCorpusManifest,
    MultiInstrumentResearchCorpus,
)

Category = Literal[
    "INSUFFICIENT_SAMPLE", "POSITIVE_NET", "POSITIVE_GROSS_COST_ERODED",
    "NEGATIVE_GROSS", "ZERO_GROSS",
]
PARAMETERS = (
    '{"fast":9,"kind":"EMA_CROSS","quantity":1,"slow":21}',
    '{"kind":"OPENING_RANGE","opening_bars":15,"quantity":1}',
    '{"exit_threshold":"50","kind":"RSI_RECOVERY","lookback":14,"oversold":"30",'
    '"quantity":1}',
    '{"kind":"VWAP_CROSS","quantity":1}',
)
LIMITATIONS = (
    "DEVELOPMENT_ONLY_NO_OUT_OF_TIME_CONFIRMATION",
    "COMMON_TIME_MARKET_EXPOSURE_NOT_INDEPENDENT_TEMPORAL_EXPERIMENTS",
    "PURPOSIVE_FIXED_UNIVERSE_NOT_UNBIASED_NSE_POPULATION",
    "UNSPECIFIED_NO_LOCAL_ADJUSTMENTS_CORPORATE_ACTION_NOTES_APPLY",
    "FIXED_AS_OF_COST_SCENARIO_NOT_HISTORICAL_CONTRACT_NOTES",
    "ONE_SHARE_INDEPENDENT_FULL_CASH_NO_PORTFOLIO_CAPITAL_MODEL",
    "NEXT_OPEN_WITH_FIXED_SLIPPAGE_NOT_VERIFIED_LIQUIDITY_OR_LATENCY",
    "EXPANDING_TRAIN_OVERLAPS_DO_NOT_POOL",
    "H1_H2_QUARANTINED_SBIN_JULY_SEALED_NO_LIVE_SELECTION",
)


class MinimumSamples(Frozen):
    trades: Annotated[int, Field(strict=True, ge=20)] = 20
    traded_sessions: Annotated[int, Field(strict=True, ge=5)] = 5


class MultiInstrumentBaselineStudySpec(Frozen):
    schema_version: Literal["MultiInstrumentBaselineStudy.v1"] = "MultiInstrumentBaselineStudy.v1"
    study_version: Label
    universe_fingerprint: Hash
    aggregate_corpus_fingerprint: Hash
    specifications: tuple[DevelopmentWalkForwardSpec, ...]
    minimum_samples: MinimumSamples = MinimumSamples()
    reporting_rules: Literal["phase120-descriptive-v1"] = "phase120-descriptive-v1"

    @field_validator("specifications")
    @classmethod
    def ordered(cls, value: tuple[DevelopmentWalkForwardSpec, ...]
                ) -> tuple[DevelopmentWalkForwardSpec, ...]:
        return tuple(sorted(value, key=lambda s: s.corpus_fingerprint))

    @model_validator(mode="after")
    def valid(self) -> "MultiInstrumentBaselineStudySpec":
        if not 2 <= len(self.specifications) <= 10:
            raise ValueError("STUDY_SIZE_INVALID")
        if len({s.corpus_fingerprint for s in self.specifications}) != len(self.specifications):
            raise ValueError("DUPLICATE_STUDY_MEMBER")
        first = self.specifications[0]
        if tuple(canonical_json(s.parameters) for s in first.strategies) != PARAMETERS:
            raise ValueError("PHASE114_FIXED_BASELINES_REQUIRED")
        for spec in self.specifications:
            if any(getattr(spec, name) != getattr(first, name) for name in (
                "strategies", "config", "folds", "common_window", "implementation_fingerprint",
                "engine_version", "warmup", "research_generation",
            )):
                raise ValueError("PER_INSTRUMENT_PARAMETERS_OR_POLICY_DIFFER")
        return self

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


def category(row: DevelopmentEvaluation, minimum: MinimumSamples) -> Category:
    if (row.metrics.trade_count < minimum.trades
            or row.traded_sessions < minimum.traded_sessions):
        return "INSUFFICIENT_SAMPLE"
    if row.metrics.net_pnl > 0:
        return "POSITIVE_NET"
    if row.raw_gross > 0:
        return "POSITIVE_GROSS_COST_ERODED"
    return "NEGATIVE_GROSS" if row.raw_gross < 0 else "ZERO_GROSS"


class RobustnessSummary(Frozen):
    strategy_identity: Hash
    strategy_kind: str
    fold_number: int
    instruments: int
    positive_net: int
    negative_net: int
    zero_net: int
    sufficient_sample: int
    sufficient_positive_net: int
    raw_gross_positive: int
    cost_eroded: int
    median_net: Decimal
    median_net_per_trade: Decimal | None
    median_trade_count: Decimal
    # Magnitudes describe concentration, never stock selection or portfolio returns.
    largest_absolute_net_share: Decimal | None
    categories: tuple[tuple[str, Category], ...]


def summarize(reports: tuple[DevelopmentReport, ...], minimum: MinimumSamples
              ) -> tuple[RobustnessSummary, ...]:
    groups: dict[tuple[str, int], list[DevelopmentEvaluation]] = {}
    for report in reports:
        for row in report.evaluations:
            if row.partition == "VALIDATION":
                groups.setdefault((row.strategy_identity, row.fold_number), []).append(row)
    summaries = []
    with localcontext(Context(prec=40, rounding=ROUND_HALF_EVEN)):
        for (identity, number), rows in sorted(groups.items()):
            categories = tuple(sorted((r.instrument_id, category(r, minimum)) for r in rows))
            ratios = [r.metrics.net_pnl / r.metrics.trade_count for r in rows
                      if r.metrics.trade_count]
            magnitudes = [abs(r.metrics.net_pnl) for r in rows]
            total = sum(magnitudes, Decimal(0))
            summaries.append(RobustnessSummary(
                strategy_identity=identity, strategy_kind=rows[0].strategy_kind,
                fold_number=number, instruments=len(rows),
                positive_net=sum(r.metrics.net_pnl > 0 for r in rows),
                negative_net=sum(r.metrics.net_pnl < 0 for r in rows),
                zero_net=sum(r.metrics.net_pnl == 0 for r in rows),
                sufficient_sample=sum(c != "INSUFFICIENT_SAMPLE" for _, c in categories),
                sufficient_positive_net=sum(c == "POSITIVE_NET" for _, c in categories),
                raw_gross_positive=sum(r.raw_gross > 0 for r in rows),
                cost_eroded=sum(r.raw_gross > 0 and r.metrics.net_pnl <= 0 for r in rows),
                median_net=median([r.metrics.net_pnl for r in rows]),
                median_net_per_trade=median(ratios) if ratios else None,
                median_trade_count=median([Decimal(r.metrics.trade_count) for r in rows]),
                largest_absolute_net_share=max(magnitudes) / total if total else None,
                categories=categories))
    return tuple(summaries)


class MultiInstrumentBaselineReport(Frozen):
    schema_version: Literal["MultiInstrumentBaselineReport.v1"] = "MultiInstrumentBaselineReport.v1"
    specification: MultiInstrumentBaselineStudySpec
    manifest: MultiInstrumentCorpusManifest
    per_instrument_results: tuple[DevelopmentReport, ...]
    robustness_summaries: tuple[RobustnessSummary, ...]
    limitations: tuple[str, ...] = LIMITATIONS
    selection: Literal["NONE"] = "NONE"

    @model_validator(mode="after")
    def valid(self) -> "MultiInstrumentBaselineReport":
        if self.manifest.status != "CERTIFIED":
            raise ValueError("UNIVERSE_NOT_CERTIFIED")
        if (self.specification.universe_fingerprint != self.manifest.universe.fingerprint
                or self.specification.aggregate_corpus_fingerprint != self.manifest.fingerprint):
            raise ValueError("REPORT_MANIFEST_MISMATCH")
        expected = {s.fingerprint for s in self.specification.specifications}
        actual = [r.specification.fingerprint for r in self.per_instrument_results]
        if len(actual) != len(expected) or set(actual) != expected:
            raise ValueError("REPORT_MEMBER_SET_MISMATCH")
        if tuple(r.specification.corpus_fingerprint for r in self.per_instrument_results) != tuple(
            m.corpus_fingerprint for m in self.manifest.members
        ):
            raise ValueError("REPORT_MEMBER_ORDER_MISMATCH")
        for report, member in zip(self.per_instrument_results, self.manifest.members, strict=True):
            if any(r.instrument_id != member.identity.instrument_id for r in report.evaluations):
                raise ValueError("REPORT_INSTRUMENT_MISMATCH")
        if self.robustness_summaries != summarize(
            self.per_instrument_results, self.specification.minimum_samples
        ) or self.limitations != LIMITATIONS:
            raise ValueError("REPORT_SUMMARY_MISMATCH")
        return self

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


def evaluate_study(
    corpus: MultiInstrumentResearchCorpus, spec: MultiInstrumentBaselineStudySpec,
    retain: Callable[[Result], None] | None = None,
) -> MultiInstrumentBaselineReport:
    # Full validation of ALL members and all specifications precedes the first backtest.
    corpus = MultiInstrumentResearchCorpus.model_validate(corpus.model_dump())
    spec = MultiInstrumentBaselineStudySpec.model_validate(spec.model_dump())
    if (spec.universe_fingerprint != corpus.manifest.universe.fingerprint
            or spec.aggregate_corpus_fingerprint != corpus.fingerprint):
        raise ValueError("STUDY_CORPUS_MISMATCH")
    by_corpus = {s.corpus_fingerprint: s for s in spec.specifications}
    if set(by_corpus) != {c.fingerprint for c in corpus.members}:
        raise ValueError("STUDY_MEMBER_SET_MISMATCH")
    if any(s.common_window != corpus.manifest.universe.common_window
           for s in spec.specifications):
        raise ValueError("COMMON_WINDOW_MISMATCH")
    reports = tuple(evaluate_development(c, by_corpus[c.fingerprint], retain)
                    for c in corpus.members)
    return MultiInstrumentBaselineReport(
        specification=spec, manifest=corpus.manifest, per_instrument_results=reports,
        robustness_summaries=summarize(reports, spec.minimum_samples))
