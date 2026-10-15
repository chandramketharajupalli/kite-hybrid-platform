"""Per-pair data gates, chronological splits and uncertainty-aware offline results."""

from datetime import UTC, datetime, timedelta
from decimal import Decimal, localcontext
from statistics import NormalDist
from typing import Literal, TypedDict

from pydantic import Field, model_validator

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import NSE, Frozen, Hash, Instant, digest
from strategy_engine.phase14.engine import (
    PRECISION,
    Metrics,
    Policy,
    Qualification,
    Series,
    Settings,
    Trade,
    metrics,
    qualify,
)
from strategy_engine.phase14.reporting import months_before
from strategy_engine.phase1415.execution import execute
from strategy_engine.phase1415.strategies import Definition

D = Decimal


class ResearchWindow(Frozen):
    months: Literal[6, 12]
    end: Instant
    validation_start: Instant
    holdout_start: Instant
    folds: tuple[Instant, ...]
    parameters_frozen_at: Instant

    @property
    def start(self) -> datetime:
        return months_before(self.end.astimezone(NSE), self.months).astimezone(UTC)

    @model_validator(mode="after")
    def valid(self) -> "ResearchWindow":
        for boundary in (self.end, self.validation_start, self.holdout_start, *self.folds):
            local = boundary.astimezone(NSE)
            if local.hour or local.minute or local.second or local.microsecond:
                raise ValueError("IST_MIDNIGHT_BOUNDARY_REQUIRED")
        if not self.start < self.validation_start < self.holdout_start < self.end:
            raise ValueError("OUT_OF_SAMPLE_NOT_AVAILABLE")
        if self.parameters_frozen_at > self.validation_start:
            raise ValueError("PARAMETERS_NOT_FROZEN_BEFORE_VALIDATION")
        if (
            len(self.folds) != 4
            or self.folds[0] != self.holdout_start
            or self.folds[-1] != self.end
            or tuple(sorted(set(self.folds))) != self.folds
        ):
            raise ValueError("THREE_PREDECLARED_HOLDOUT_FOLDS_REQUIRED")
        return self


def standard_window(end: datetime, months: Literal[6, 12]) -> ResearchWindow:
    local = end.astimezone(NSE)
    if end.tzinfo != UTC or local.hour or local.minute or local.second or local.microsecond:
        raise ValueError("IST_MIDNIGHT_END_REQUIRED")
    start = months_before(local, months).astimezone(UTC)
    days = (end - start).days
    validation = start + timedelta(days=days * 5 // 10)
    holdout = start + timedelta(days=days * 7 // 10)
    remaining = (end - holdout).days
    return ResearchWindow(
        months=months,
        end=end,
        validation_start=validation,
        holdout_start=holdout,
        parameters_frozen_at=start,
        folds=(
            holdout,
            holdout + timedelta(days=remaining // 3),
            holdout + timedelta(days=2 * remaining // 3),
            end,
        ),
    )


class Dataset(Frozen):
    series: Series
    dataset_sha256: Hash
    acquired_at: Instant
    calendar_version: str = Field(min_length=1)
    calendar_source: str = Field(min_length=1)
    calendar_verified: bool
    corporate_action_evidence: str = Field(min_length=1)
    missing_sessions: int = Field(ge=0)
    duplicate_candles: int = Field(ge=0)
    out_of_order: int = Field(ge=0)
    quarantined: bool
    mapping_verified: bool

    @model_validator(mode="after")
    def valid(self) -> "Dataset":
        if digest(self.series.model_dump_json()) != self.dataset_sha256:
            raise ValueError("DATASET_FINGERPRINT_MISMATCH")
        if self.acquired_at < self.series.to_exclusive:
            raise ValueError("ACQUISITION_BEFORE_COMPLETED_DATA")
        return self


class Summary(Frozen):
    basic: Metrics
    gross: Decimal
    average_return: Decimal | None
    profit_factor: Decimal | None
    average_holding_seconds: Decimal | None
    exposure: Decimal
    max_consecutive_wins: int
    max_consecutive_losses: int
    confidence_95: tuple[float, float]


def wilson(wins: int, count: int, alpha: float = 0.05) -> tuple[float, float]:
    if count < 0 or not 0 <= wins <= count or not 0 < alpha < 1:
        raise ValueError("INVALID_CONFIDENCE_INPUT")
    if not count:
        return 0.0, 1.0
    z = NormalDist().inv_cdf(1 - alpha / 2)
    p = wins / count
    center = p + z * z / (2 * count)
    width = z * (p * (1 - p) / count + z * z / (4 * count * count)) ** 0.5
    scale = 1 + z * z / count
    return max(0.0, (center - width) / scale), min(1.0, (center + width) / scale)


def summarize(trades: tuple[Trade, ...], capital: Decimal, seconds: int) -> Summary:
    with localcontext(PRECISION):
        basic = metrics(trades, capital)
        positives = sum((max(t.net, D(0)) for t in trades), D(0))
        negatives = -sum((min(t.net, D(0)) for t in trades), D(0))
        holding = sum(int((t.exit_at - t.entry_at).total_seconds()) for t in trades)
        win = loss = max_win = max_loss = 0
        for t in trades:
            win = win + 1 if t.net > 0 else 0
            loss = loss + 1 if t.net < 0 else 0
            max_win, max_loss = max(max_win, win), max(max_loss, loss)
        return Summary(
            basic=basic,
            gross=sum((t.gross for t in trades), D(0)),
            average_return=(
                sum((t.net / (t.entry * t.quantity) for t in trades), D(0)) / len(trades)
                if trades
                else None
            ),
            profit_factor=positives / negatives if negatives else None,
            average_holding_seconds=D(holding) / len(trades) if trades else None,
            exposure=D(holding) / seconds if seconds else D(0),
            max_consecutive_wins=max_win,
            max_consecutive_losses=max_loss,
            confidence_95=wilson(basic.wins, basic.trades),
        )


class Result(Frozen):
    schema_version: Literal["Phase1415Pair.v1"] = "Phase1415Pair.v1"
    mode: Literal["BACKTEST_ONLY"] = "BACKTEST_ONLY"
    family_run_id: Hash | None = None
    hypothesis_family_size: int | None = None
    exchange: Literal["NSE"] = "NSE"
    symbol: str
    strategy_id: str
    strategy_version: str
    strategy_hash: Hash
    window: ResearchWindow
    policy_hash: Hash
    dataset_hash: Hash | None
    data_status: str
    synthetic: bool | None
    qualified: bool = False
    reasons: tuple[str, ...]
    coverage: Decimal | None = None
    training: Summary | None = None
    validation: Summary | None = None
    holdout: Summary | None = None
    total: Summary | None = None
    qualification: Qualification | None = None
    trades: tuple[Trade, ...] = ()


class ResultIdentity(TypedDict):
    symbol: str
    strategy_id: str
    strategy_version: str
    strategy_hash: str
    window: ResearchWindow
    policy_hash: str
    dataset_hash: str | None
    synthetic: bool | None


def evaluate(
    symbol: str,
    definition: Definition,
    window: ResearchWindow,
    dataset: Dataset | None,
    settings: Settings,
    costs: IntradayCostSchedule,
    policy: Policy,
) -> Result:
    base: ResultIdentity = dict(
        symbol=symbol,
        strategy_id=definition.identifier,
        strategy_version=definition.version,
        strategy_hash=definition.fingerprint,
        window=window,
        policy_hash=digest(policy.model_dump_json()),
        dataset_hash=dataset.dataset_sha256 if dataset else None,
        synthetic=dataset.series.synthetic if dataset else None,
    )
    reasons: list[str] = []
    if dataset is None:
        return Result(
            **base,
            data_status="UNAVAILABLE",
            reasons=("NO_SOURCED_HISTORY_OR_RESOLVED_MAPPING", "OUT_OF_SAMPLE_NOT_AVAILABLE"),
        )
    series = dataset.series
    if (
        series.symbol != symbol
        or series.from_inclusive != window.start
        or series.to_exclusive != window.end
    ):
        reasons.append("INSUFFICIENT_HISTORY_OR_IDENTITY_MISMATCH")
    if not dataset.calendar_verified:
        reasons.append("CALENDAR_NOT_VERIFIED")
    if not dataset.mapping_verified:
        reasons.append("MASTER_UNAVAILABLE")
    if not series.corporate_actions_reviewed or dataset.corporate_action_evidence == "UNRESOLVED":
        reasons.append("CORPORATE_ACTION_REVIEW_REQUIRED")
    if (
        dataset.quarantined
        or dataset.missing_sessions
        or dataset.duplicate_candles
        or dataset.out_of_order
    ):
        reasons.append("DATA_QUARANTINED")
    missing, extras = series.quality()
    if missing or extras:
        reasons.append("DATA_INCOMPLETE")
    boundaries = (window.validation_start, *window.folds)
    if any(s.start < b < s.end for s in series.sessions for b in boundaries):
        reasons.append("SPLIT_CUTS_SESSION")
    if reasons:
        return Result(**base, data_status="QUARANTINED", reasons=tuple(reasons))
    try:
        simulation = execute(series, definition, settings, costs)
    except ValueError:
        return Result(**base, data_status="QUARANTINED", reasons=("EXECUTION_OR_DATA_REJECTED",))
    trades = simulation.trades
    if any(t.signal_at < b < t.exit_at for t in trades for b in boundaries):
        return Result(**base, data_status="QUARANTINED", reasons=("SPLIT_TRADE_LEAKAGE",))
    q = qualify(trades, settings.capital, window.holdout_start, window.folds, policy)
    reasons.extend(q.reasons)
    if simulation.marked_drawdown > policy.max_drawdown:
        reasons.append("MARKED_DRAWDOWN_LIMIT")
    if series.synthetic:
        reasons.append("SYNTHETIC_NOT_HISTORICAL_PERFORMANCE")

    def part(a: datetime, b: datetime) -> Summary:
        seconds = sum(
            int((s.end - s.start).total_seconds()) for s in series.sessions if a <= s.start < b
        )
        return summarize(tuple(t for t in trades if a <= t.entry_at < b), settings.capital, seconds)

    return Result(
        **base,
        data_status="SYNTHETIC" if series.synthetic else "LOCAL_VERIFIED",
        qualified=not reasons,
        reasons=tuple(reasons),
        coverage=D(1),
        training=part(window.start, window.validation_start),
        validation=part(window.validation_start, window.holdout_start),
        holdout=part(window.holdout_start, window.end),
        total=part(window.start, window.end),
        qualification=q,
        trades=trades,
    )
