"""One reproducible result per complete universe/strategy/window key; no promotion."""

import calendar
import csv
from datetime import datetime
from pathlib import Path
from typing import Literal

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import Frozen, Hash, Instant, digest
from strategy_engine.phase14.engine import (
    Metrics,
    Policy,
    Qualification,
    Series,
    Settings,
    Simulation,
    Strategy,
    metrics,
    qualify,
    simulate,
)
from strategy_engine.phase14.universe import normalize


def months_before(end: datetime, months: int) -> datetime:
    if months not in (6, 12):
        raise ValueError("SIX_OR_TWELVE_MONTH_WINDOW_REQUIRED")
    number = end.year * 12 + end.month - 1 - months
    year, month0 = divmod(number, 12)
    day = min(end.day, calendar.monthrange(year, month0 + 1)[1])
    return end.replace(year=year, month=month0 + 1, day=day)


class Window(Frozen):
    months: Literal[6, 12]
    end: Instant
    oos_start: Instant
    fold_boundaries: tuple[Instant, ...]


class PairReport(Frozen):
    schema_version: Literal["Phase14PairReport.v1"] = "Phase14PairReport.v1"
    exchange: str
    symbol: str
    strategy: str
    strategy_hash: Hash
    window: Window
    policy_hash: Hash
    state: Literal["BACKTEST_ONLY"] = "BACKTEST_ONLY"
    status: Literal["REJECTED", "RESEARCH_CANDIDATE", "SIMULATED"]
    reasons: tuple[str, ...]
    missing_bars: int | None = None
    unexpected_bars: int | None = None
    in_sample: Metrics | None = None
    qualification: Qualification | None = None
    simulation: Simulation | None = None


def evaluate_pairs(
    universe: list[dict[str, str]],
    strategies: tuple[Strategy, ...],
    windows: tuple[Window, ...],
    data: dict[tuple[str, str, int], Series],
    settings: Settings,
    costs: IntradayCostSchedule,
    policy: Policy | None = None,
) -> tuple[PairReport, ...]:
    policy = policy or Policy()
    members, _ = normalize(universe)
    if len({s.model_dump_json() for s in strategies}) != len(strategies):
        raise ValueError("DUPLICATE_STRATEGY")
    if len({w.months for w in windows}) != len(windows):
        raise ValueError("DUPLICATE_WINDOW")
    if policy.family_size < len(members) * len(strategies) * len(windows):
        raise ValueError("MULTIPLE_TESTING_FAMILY_UNDERSTATED")
    reports = []
    for row in members:
        for strategy in strategies:
            for window in windows:
                start = months_before(window.end, window.months)
                if not start < window.oos_start < window.end:
                    raise ValueError("INVALID_PREDECLARED_SPLIT")
                # Validate folds even when data is absent; no post-hoc segmentation.
                qualify((), settings.capital, window.oos_start, window.fold_boundaries, policy)
                if window.fold_boundaries[-1] != window.end:
                    raise ValueError("OOS_END_MISMATCH")
                series = data.get((row["exchange"], row["symbol"], window.months))
                reasons: list[str] = []
                result = None
                qualification = None
                ins = None
                missing = unexpected = None
                if series is None:
                    reasons.append("NO_SOURCED_HISTORY_OR_RESOLVED_MAPPING")
                elif (
                    series.symbol != row["symbol"]
                    or series.exchange != row["exchange"]
                    or series.from_inclusive != start
                    or series.to_exclusive != window.end
                ):
                    reasons.append("INSUFFICIENT_HISTORY_OR_IDENTITY_MISMATCH")
                else:
                    if any(
                        s.start < bound < s.end
                        for s in series.sessions
                        for bound in window.fold_boundaries
                    ):
                        raise ValueError("SPLIT_MUST_NOT_CUT_SESSION")
                    gaps, extras = series.quality()
                    missing, unexpected = len(gaps), len(extras)
                    if gaps or extras:
                        reasons.append("DATA_INCOMPLETE")
                    else:
                        try:
                            result = simulate(series, strategy, settings, costs)
                            qualification = qualify(
                                result.trades,
                                settings.capital,
                                window.oos_start,
                                window.fold_boundaries,
                                policy,
                            )
                            ins = metrics(
                                tuple(t for t in result.trades if t.entry_at < window.oos_start),
                                settings.capital,
                            )
                            reasons.extend(qualification.reasons)
                            if result.marked_drawdown > policy.max_drawdown:
                                reasons.append("MARKED_DRAWDOWN_LIMIT")
                                qualification = qualification.model_copy(
                                    update={"qualified": False, "reasons": tuple(reasons)}
                                )
                        except ValueError as failure:
                            reasons.append(str(failure))
                status: Literal["REJECTED", "RESEARCH_CANDIDATE", "SIMULATED"] = "REJECTED"
                if result is not None and result.synthetic:
                    status = "SIMULATED"
                    reasons.append("SYNTHETIC_NOT_HISTORICAL_PERFORMANCE")
                elif qualification is not None and qualification.qualified:
                    status = "RESEARCH_CANDIDATE"
                reports.append(
                    PairReport(
                        exchange=row["exchange"],
                        symbol=row["symbol"],
                        strategy=strategy.name,
                        strategy_hash=digest(strategy.model_dump_json()),
                        window=window,
                        policy_hash=digest(policy.model_dump_json()),
                        status=status,
                        reasons=tuple(reasons),
                        missing_bars=missing,
                        unexpected_bars=unexpected,
                        in_sample=ins,
                        qualification=qualification,
                        simulation=result,
                    )
                )
    return tuple(reports)


def export_csv(reports: tuple[PairReport, ...], path: Path) -> None:
    """Detailed trades/folds/configs live in JSON; absent performance stays empty in CSV."""
    columns = (
        "exchange",
        "symbol",
        "strategy",
        "strategy_hash",
        "months",
        "status",
        "reasons",
        "oos_trades",
        "oos_win_rate",
        "oos_expectancy",
        "oos_net",
        "oos_drawdown",
    )
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        for report in reports:
            q = report.qualification
            writer.writerow(
                dict(
                    exchange=report.exchange,
                    symbol=report.symbol,
                    strategy=report.strategy,
                    strategy_hash=report.strategy_hash,
                    months=report.window.months,
                    status=report.status,
                    reasons=";".join(report.reasons),
                    oos_trades=q.out_of_sample.trades if q else "",
                    oos_win_rate=q.out_of_sample.win_rate if q else "",
                    oos_expectancy=q.out_of_sample.expectancy if q else "",
                    oos_net=q.out_of_sample.net if q else "",
                    oos_drawdown=q.out_of_sample.drawdown if q else "",
                )
            )
