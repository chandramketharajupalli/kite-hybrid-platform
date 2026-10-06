"""Phase 11.5 ex-post research only. Never imported by a strategy or decision feature."""

from datetime import UTC, date, datetime, time, timedelta
from decimal import ROUND_HALF_EVEN, Context, Decimal, localcontext
from typing import Any, Literal

from strategy_engine.backtest.corpus import Corpus, slice_corpus
from strategy_engine.backtest.dataset import NSE, Bar, Frozen, Hash, digest
from strategy_engine.backtest.engine import AdverseBps, Result, Trade, canonical_json
from strategy_engine.research.features import CompletedSession, vwap
from strategy_engine.research.opening_features import early_volume, opening_observation
from strategy_engine.research.walkforward import FoldEvaluation, WalkForwardReport

START = datetime(2026, 2, 1, 18, 30, tzinfo=UTC)
END = datetime(2026, 6, 30, 18, 30, tzinfo=UTC)
TEST_END = datetime(2026, 7, 31, 18, 30, tzinfo=UTC)
VERSION = "phase115-diagnostics-v1"
PRECISION = Context(prec=40, rounding=ROUND_HALF_EVEN)
ZERO = Decimal(0)
DIMENSIONS = (
    "volatility",
    "trend_efficiency",
    "gap_magnitude",
    "gap_direction",
    "opening_range_5",
    "opening_range_15",
    "opening_range_30",
    "relative_volume",
    "entry_time",
    "exit_time",
    "holding_duration",
)
QUANTILES = DIMENSIONS[:3] + DIMENSIONS[4:8]


class DiagnosticReport(Frozen):
    report_version: Literal["phase115-diagnostics-v1"] = "phase115-diagnostics-v1"
    usage: Literal["EX_POST_DIAGNOSTIC_ONLY"] = "EX_POST_DIAGNOSTIC_ONLY"
    parent_corpus_fingerprint: Hash
    phase114_report_fingerprint: Hash
    plan_fingerprint: Hash
    implementation_fingerprint: Hash
    development_window: tuple[datetime, datetime] = (START, END)
    excluded_test_window: tuple[datetime, datetime] = (END, TEST_END)
    feature_version: str = "session-decimal40-v1+phase115-diagnostics-v1"
    cost_version: str
    slippage_version: str
    test_state: Literal["SEALED_NOT_EVALUATED"] = "SEALED_NOT_EVALUATED"
    selection: Literal["NONE"] = "NONE"
    planned_dimensions: tuple[str, ...] = DIMENSIONS + (
        "MAE_MFE",
        "cost_burden",
        "churn",
        "session_concentration",
    )
    thresholds: dict[str, tuple[Decimal, Decimal] | None]
    sessions: tuple[dict[str, Any], ...]
    evaluations: tuple[dict[str, Any], ...]
    findings: tuple[dict[str, Any], ...] = ()

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


def guard_bars(bars: tuple[Bar, ...]) -> None:
    if not bars or any(not START <= b.start < b.end <= END for b in bars):
        raise ValueError("SEALED_TEST_OR_OUTSIDE_DEVELOPMENT")


def ratio(numerator: Decimal, denominator: Decimal | int) -> Decimal | None:
    with localcontext(PRECISION):
        return numerator / denominator if denominator else None


def median(values: list[Decimal]) -> Decimal | None:
    if not values:
        return None
    values = sorted(values)
    n = len(values)
    with localcontext(PRECISION):
        return (values[(n - 1) // 2] + values[n // 2]) / 2


def tertiles(values: list[Decimal]) -> tuple[Decimal, Decimal] | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[(len(values) + 2) // 3 - 1], ordered[(2 * len(values) + 2) // 3 - 1]


def label(value: Decimal | None, bounds: tuple[Decimal, Decimal] | None) -> str:
    if value is None or bounds is None:
        return "UNKNOWN"
    return "LOW" if value <= bounds[0] else "MEDIUM" if value <= bounds[1] else "HIGH"


def time_bucket(at: datetime) -> str:
    clock = at.astimezone(NSE).time()
    for start, end, name in (
        (time(9, 15), time(9, 45), "OPENING"),
        (time(9, 45), time(11, 30), "MORNING"),
        (time(11, 30), time(13, 30), "MIDDAY"),
        (time(13, 30), time(15, 30), "AFTERNOON"),
    ):
        if start <= clock < end:
            return name
    raise ValueError("OUTSIDE_REGISTERED_SESSION")


def holding_bucket(minutes: int) -> str:
    return (
        "LE_5"
        if minutes <= 5
        else "6_15"
        if minutes <= 15
        else "16_30"
        if minutes <= 30
        else "GT_30"
    )


def session_records(
    development: Corpus,
) -> tuple[tuple[dict[str, Any], ...], dict[str, tuple[Decimal, Decimal] | None]]:
    guard_bars(development.bars)
    records: list[dict[str, Any]] = []
    history: list[CompletedSession] = []
    with localcontext(PRECISION):
        for shard in development.shards:
            bars = shard.bars
            view = CompletedSession(
                opened_at=bars[0].start, closes_at=bars[-1].end, decision_at=bars[-1].end, bars=bars
            )
            previous = history[-1].bars[-1].close if history else None
            gap = None if previous is None else bars[0].open - previous
            gap_pct = None if gap is None or previous is None else gap / previous * 100
            closes = (bars[0].open,) + tuple(b.close for b in bars)
            pairs = tuple(zip(closes, closes[1:], strict=False))
            path = sum((abs(b - a) for a, b in pairs), ZERO)
            efficiency = abs(closes[-1] - closes[0]) / path if path else ZERO
            volatility = sum(((b / a - 1) ** 2 for a, b in pairs), ZERO).sqrt()
            ranges = {str(n): opening_observation(view, n) for n in (5, 15, 30)}
            relative = early_volume(view, tuple(history))
            session_vwap = vwap(view)
            values = {
                "volatility": volatility,
                "trend_efficiency": efficiency,
                "gap_magnitude": None if gap_pct is None else abs(gap_pct),
                "relative_volume": None if relative is None else relative["value"],
            }
            for n in (5, 15, 30):
                item = ranges[str(n)]
                values[f"opening_range_{n}"] = None if item is None else item["range_percent"]
            records.append(
                {
                    "date": bars[0].start.astimezone(NSE).date(),
                    "usage": "EX_POST_DIAGNOSTIC_ONLY",
                    "values": values,
                    "opening_gap": gap,
                    "opening_gap_percent": gap_pct,
                    "opening_gap_absolute": None if gap is None else abs(gap),
                    "previous_confirmed_date": (
                        None if not history else history[-1].opened_at.astimezone(NSE).date()
                    ),
                    "gap_direction": (
                        "UNKNOWN"
                        if gap is None
                        else "UP"
                        if gap > 0
                        else "DOWN"
                        if gap < 0
                        else "FLAT"
                    ),
                    "opening_ranges": ranges,
                    "early_relative_volume": relative,
                    "session_range": max(b.high for b in bars) - min(b.low for b in bars),
                    "session_range_percent": (max(b.high for b in bars) - min(b.low for b in bars))
                    / bars[0].open
                    * 100,
                    "session_volume": sum(b.volume for b in bars),
                    "close_vs_vwap": (
                        None if session_vwap is None else bars[-1].close - session_vwap
                    ),
                }
            )
            history.append(view)
        thresholds = {
            key: tertiles([r["values"][key] for r in records if r["values"][key] is not None])
            for key in QUANTILES
        }
        for record in records:
            record["regime_labels"] = {
                key: label(record["values"][key], thresholds[key]) for key in QUANTILES
            }
            record["regime_labels"]["gap_direction"] = record["gap_direction"]
    return tuple(records), thresholds


def excursions(trade: Trade, bars: tuple[Bar, ...], minutes: int | None = None) -> dict[str, Any]:
    guard_bars(bars)
    entry, exit_at = trade.entry.execution_time, trade.exit.execution_time
    if not START <= entry < exit_at <= END:
        raise ValueError("SEALED_TEST_OR_INVALID_TRADE")
    cutoff = exit_at if minutes is None else min(exit_at, entry + timedelta(minutes=minutes))
    held = tuple(b for b in bars if entry <= b.start and b.end <= cutoff)
    if not held or held[0].start != entry or held[-1].end != cutoff:
        raise ValueError("EXCURSION_COVERAGE_MISSING")
    if any(a.end != b.start for a, b in zip(held, held[1:], strict=False)):
        raise ValueError("EXCURSION_COVERAGE_MISSING")
    with localcontext(PRECISION):
        terminal = [trade.exit.reference_price] if cutoff == exit_at else []
        high = max([b.high for b in held] + terminal)
        low = min([b.low for b in held] + terminal)
        return {
            "usage": "EX_POST_DIAGNOSTIC_ONLY",
            "mfe": max(ZERO, high - trade.entry.fill_price),
            "mae": max(ZERO, trade.entry.fill_price - low),
            "observed_minutes": int((cutoff - entry).total_seconds()) // 60,
            "last_close_change": held[-1].close - trade.entry.fill_price,
        }


def trade_records(
    result: Result, sessions: tuple[dict[str, Any], ...], bars: tuple[Bar, ...]
) -> list[dict[str, Any]]:
    guard_bars(bars)
    if not START <= result.from_inclusive < result.to_exclusive <= END:
        raise ValueError("SEALED_TEST_OR_OUTSIDE_DEVELOPMENT")
    by_date = {s["date"]: s for s in sessions}
    session_bars: dict[date, tuple[Bar, ...]] = {}
    for day in by_date:
        session_bars[day] = tuple(b for b in bars if b.start.astimezone(NSE).date() == day)
    reasons = {o.intent_id: o.reason for o in result.outcomes}
    rows: list[dict[str, Any]] = []
    with localcontext(PRECISION):
        for trade in result.trades:
            day = trade.entry.execution_time.astimezone(NSE).date()
            if trade.exit.execution_time.astimezone(NSE).date() != day:
                raise ValueError("OVERNIGHT_DIAGNOSTIC_FORBIDDEN")
            data = session_bars[day]
            whole = excursions(trade, data)
            early = excursions(trade, data, 5)
            first = excursions(trade, data, 1)
            quantity = trade.entry.quantity
            slip = (trade.entry.adverse_slippage + trade.exit.adverse_slippage) * quantity
            raw = (trade.exit.reference_price - trade.entry.reference_price) * quantity
            if raw - slip != trade.gross_pnl or trade.gross_pnl - trade.costs != trade.net_pnl:
                raise ValueError("TRADE_ATTRIBUTION_MISMATCH")
            held = (
                int((trade.exit.execution_time - trade.entry.execution_time).total_seconds()) // 60
            )
            prefix = tuple(b for b in data if b.end <= trade.entry.execution_time)
            entry_vwap = (
                vwap(
                    CompletedSession(
                        opened_at=data[0].start,
                        closes_at=data[-1].end,
                        decision_at=prefix[-1].end,
                        bars=prefix,
                    )
                )
                if prefix
                else None
            )
            regimes = dict(by_date[day]["regime_labels"])
            regimes.update(
                entry_time=time_bucket(trade.entry.execution_time),
                exit_time=time_bucket(trade.exit.execution_time),
                holding_duration=holding_bucket(held),
            )
            rows.append(
                {
                    "date": day,
                    "entry_time": trade.entry.execution_time,
                    "exit_time": trade.exit.execution_time,
                    "quantity": quantity,
                    "entry_price": trade.entry.fill_price,
                    "exit_price": trade.exit.fill_price,
                    "reference_entry": trade.entry.reference_price,
                    "reference_exit": trade.exit.reference_price,
                    "holding_minutes": held,
                    "gross_before_slippage": raw,
                    "slippage": slip,
                    "gross_pnl": trade.gross_pnl,
                    "costs": trade.costs,
                    "net_pnl": trade.net_pnl,
                    "turnover": trade.entry.gross_notional + trade.exit.gross_notional,
                    "exit_reason": reasons[trade.exit.intent_id],
                    "entry_vwap_distance": (
                        None if entry_vwap is None else trade.entry.reference_price - entry_vwap
                    ),
                    "entry_vwap_usage": "TRADABLE_AT_TIME_T",
                    "regime_usage": "EX_POST_DIAGNOSTIC_ONLY",
                    "regimes": regimes,
                    "excursions": whole,
                    "first_five": early,
                    "first_bar": first,
                    "full_five_observed": held >= 5,
                    "giveback": whole["mfe"] * quantity - trade.gross_pnl,
                    "losing_with_favorable_excursion": trade.net_pnl < 0 and whole["mfe"] > 0,
                    "losing_with_cost_covering_excursion": trade.net_pnl < 0
                    and whole["mfe"] * quantity
                    > trade.costs + trade.exit.adverse_slippage * quantity,
                }
            )
    return rows


def summary(rows: list[dict[str, Any]], eligible_sessions: int) -> dict[str, Any]:
    with localcontext(PRECISION):
        count = len(rows)
        traded = len({r["date"] for r in rows})
        totals = {
            key: sum((r[key] for r in rows), ZERO)
            for key in (
                "gross_before_slippage",
                "slippage",
                "gross_pnl",
                "costs",
                "net_pnl",
                "turnover",
            )
        }
        outcomes = [r["net_pnl"] for r in rows]
        peak = equity = drawdown = ZERO
        for row in sorted(rows, key=lambda r: r["exit_time"]):
            equity += row["net_pnl"]
            peak = max(peak, equity)
            drawdown = max(drawdown, peak - equity)
        gross, net = totals["gross_pnl"], totals["net_pnl"]
        case = (
            "A_GROSS_POSITIVE_NET_NEGATIVE"
            if gross > 0 and net < 0
            else "B_GROSS_NEGATIVE_NET_NEGATIVE"
            if gross < 0 and net < 0
            else "C_GROSS_POSITIVE_NET_POSITIVE"
            if gross > 0 and net > 0
            else "BOUNDARY_OR_EMPTY"
        )
        return {
            **totals,
            "trade_count": count,
            "traded_sessions": traded,
            "eligible_sessions": eligible_sessions,
            "sample_status": (
                "SUFFICIENT_DESCRIPTIVE_SAMPLE"
                if traded >= 5 and count >= 20
                else "INSUFFICIENT_SAMPLE"
            ),
            "edge_case": case,
            "wins": sum(p > 0 for p in outcomes),
            "losses": sum(p < 0 for p in outcomes),
            "breakeven": sum(p == 0 for p in outcomes),
            "win_rate": ratio(Decimal(sum(p > 0 for p in outcomes)), count),
            "mean_net": ratio(net, count),
            "median_net": median(outcomes),
            "mean_gross": ratio(gross, count),
            "closed_trade_max_drawdown": drawdown,
            "costs_over_abs_aggregate_gross": ratio(totals["costs"], abs(gross)),
            "costs_over_sum_abs_trade_gross": ratio(
                totals["costs"], sum((abs(r["gross_pnl"]) for r in rows), ZERO)
            ),
            "average_round_trip_cost": ratio(totals["costs"], count),
            "cost_share_of_turnover": ratio(totals["costs"], totals["turnover"]),
            "trades_per_session": ratio(Decimal(count), eligible_sessions),
            "turnover_per_session": ratio(totals["turnover"], eligible_sessions),
            "mean_holding_minutes": ratio(
                sum((Decimal(r["holding_minutes"]) for r in rows), ZERO), count
            ),
            "mean_mfe": ratio(sum((r["excursions"]["mfe"] for r in rows), ZERO), count),
            "mean_mae": ratio(sum((r["excursions"]["mae"] for r in rows), ZERO), count),
            "mean_first_five_mfe": ratio(sum((r["first_five"]["mfe"] for r in rows), ZERO), count),
            "mean_first_five_mae": ratio(sum((r["first_five"]["mae"] for r in rows), ZERO), count),
            "full_five_observed": sum(r["full_five_observed"] for r in rows),
            "first_bar_close_adverse": sum(r["first_bar"]["last_close_change"] < 0 for r in rows),
            "first_five_close_adverse": sum(r["first_five"]["last_close_change"] < 0 for r in rows),
            "losing_with_favorable_excursion": sum(
                r["losing_with_favorable_excursion"] for r in rows
            ),
            "losing_with_cost_covering_excursion": sum(
                r["losing_with_cost_covering_excursion"] for r in rows
            ),
            "mean_giveback": ratio(sum((r["giveback"] for r in rows), ZERO), count),
        }


def sensitivity(result: Result) -> tuple[dict[str, Any], ...]:
    from strategy_engine.backtest.costs import IntradayCostSchedule

    if not START <= result.from_inclusive < result.to_exclusive <= END:
        raise ValueError("SEALED_TEST_OR_OUTSIDE_DEVELOPMENT")
    if not isinstance(result.config.costs, IntradayCostSchedule):
        raise ValueError("DATED_COST_SCENARIO_REQUIRED")
    rows = []
    with localcontext(PRECISION):
        for bps in (0, 5, 10):
            model = AdverseBps(bps=Decimal(bps))
            raw = gross = fees = ZERO
            for trade in result.trades:
                buy = model.price("BUY", trade.entry.reference_price) * trade.entry.quantity
                sell = model.price("SELL", trade.exit.reference_price) * trade.exit.quantity
                raw += (
                    trade.exit.reference_price - trade.entry.reference_price
                ) * trade.entry.quantity
                gross += sell - buy
                fees += result.config.costs.quote(
                    "BUY", buy, trade.entry.execution_time, None
                ).total
                fees += result.config.costs.quote(
                    "SELL", sell, trade.exit.execution_time, buy
                ).total
            rows.append(
                {
                    "bps": bps,
                    "usage": "FIXED_TRADES_REPRICING_ONLY",
                    "primary": bps == 5,
                    "gross_before_slippage": raw,
                    "slippage": raw - gross,
                    "gross_pnl": gross,
                    "costs": fees,
                    "net_pnl": gross - fees,
                }
            )
    if result.config.slippage.bps == 5 and (
        rows[1]["net_pnl"] != result.metrics.net_pnl or rows[1]["costs"] != result.metrics.costs
    ):
        raise ValueError("PRIMARY_SENSITIVITY_MISMATCH")
    return tuple(rows)


def evaluation_record(
    evaluation: FoldEvaluation,
    result: Result,
    sessions: tuple[dict[str, Any], ...],
    bars: tuple[Bar, ...],
) -> dict[str, Any]:
    if result.fingerprint != evaluation.result_fingerprint or result.metrics != evaluation.metrics:
        raise ValueError("PHASE114_REPLAY_DIVERGED")
    if (
        result.from_inclusive != evaluation.window.start
        or result.to_exclusive != evaluation.window.end
    ):
        raise ValueError("RESULT_WINDOW_MISMATCH")
    selected = tuple(
        s
        for s in sessions
        if evaluation.window.start.astimezone(NSE).date()
        <= s["date"]
        < evaluation.window.end.astimezone(NSE).date()
    )
    rows = trade_records(result, selected, bars)
    total = summary(rows, len(selected))
    if any(total[k] != getattr(result.metrics, k) for k in ("gross_pnl", "costs", "net_pnl")):
        raise ValueError("AGGREGATE_ATTRIBUTION_MISMATCH")
    groups: list[dict[str, Any]] = []
    for dimension in DIMENSIONS:
        labels = (
            sorted({s["regime_labels"][dimension] for s in selected})
            if dimension in QUANTILES or dimension == "gap_direction"
            else list(
                ("LE_5", "6_15", "16_30", "GT_30")
                if dimension == "holding_duration"
                else ("OPENING", "MORNING", "MIDDAY", "AFTERNOON")
            )
        )
        for value in labels:
            eligible = (
                sum(s["regime_labels"][dimension] == value for s in selected)
                if dimension in QUANTILES or dimension == "gap_direction"
                else len(selected)
            )
            groups.append(
                {
                    "dimension": dimension,
                    "label": value,
                    **summary([r for r in rows if r["regimes"][dimension] == value], eligible),
                }
            )
    with localcontext(PRECISION):
        daily: list[dict[str, Any]] = []
        gaps: list[Decimal] = []
        repeated = 0
        for session in selected:
            trades = [r for r in rows if r["date"] == session["date"]]
            repeated += max(0, len(trades) - 1)
            gaps.extend(
                Decimal(int((b["entry_time"] - a["exit_time"]).total_seconds())) / 60
                for a, b in zip(trades, trades[1:], strict=False)
            )
            daily.append({"date": session["date"], **summary(trades, 1)})
        ordered = sorted(daily, key=lambda d: (d["net_pnl"], d["date"]))
        absolute = sum((abs(d["net_pnl"]) for d in daily), ZERO)
        concentration = {
            "best_session": ordered[-1] if ordered else None,
            "worst_session": ordered[0] if ordered else None,
            "top_3_net": sum((d["net_pnl"] for d in ordered[-3:]), ZERO),
            "bottom_3_net": sum((d["net_pnl"] for d in ordered[:3]), ZERO),
            "sum_abs_daily_net": absolute,
            "best_share_abs_daily": ratio(ordered[-1]["net_pnl"], absolute) if ordered else None,
            "worst_share_abs_daily": ratio(ordered[0]["net_pnl"], absolute) if ordered else None,
            "top_3_share_abs_daily": ratio(
                sum((d["net_pnl"] for d in ordered[-3:]), ZERO), absolute
            ),
            "bottom_3_share_abs_daily": ratio(
                sum((d["net_pnl"] for d in ordered[:3]), ZERO), absolute
            ),
            "median_daily_net": median([d["net_pnl"] for d in daily]),
        }
        return {
            "fold": evaluation.fold_number,
            "partition": evaluation.partition,
            "strategy": result.strategy.name,
            "window": evaluation.window.model_dump(),
            "parent_result_fingerprint": result.fingerprint,
            "summary": total,
            "engine_max_drawdown": result.metrics.max_drawdown,
            "regimes": groups,
            "trades": rows,
            "daily": daily,
            "concentration": concentration,
            "churn": {
                "repeated_entries": repeated,
                "mean_flat_minutes": ratio(sum(gaps, ZERO), len(gaps)),
                "median_flat_minutes": median(gaps),
            },
            "slippage_sensitivity": sensitivity(result),
        }


def analyze(
    development: Corpus,
    parent: WalkForwardReport,
    results: tuple[Result, ...],
    plan_fingerprint: str,
    implementation_fingerprint: str,
) -> DiagnosticReport:
    # No full corpus accepted, even when a caller intends to filter it later.
    if development.from_inclusive != START or development.to_exclusive != END:
        raise ValueError("DEVELOPMENT_ONLY_CORPUS_REQUIRED")
    guard_bars(development.bars)
    development = Corpus.model_validate(development.model_dump())
    spec = parent.specification
    if (
        spec.folds[0].train.start != START
        or spec.final_test.start != END
        or spec.final_test.end != TEST_END
        or parent.test_state != "SEALED_NOT_EVALUATED"
    ):
        raise ValueError("PHASE115_WINDOW_MISMATCH")
    expected = {
        (n, name, digest(canonical_json(s)))
        for n in range(1, len(spec.folds) + 1)
        for name in ("TRAIN", "VALIDATION")
        for s in spec.strategies
    }
    actual = {(e.fold_number, e.partition, e.strategy_identity) for e in parent.evaluations}
    if (
        expected != actual
        or len(parent.evaluations) != len(expected)
        or len(results) != len(expected)
    ):
        raise ValueError("MISSING_OR_DUPLICATE_DEVELOPMENT_EVALUATION")
    if any(e.window.end > END for e in parent.evaluations):
        raise ValueError("SEALED_TEST_BOUNDARY")
    # Fail closed on every result before computing any diagnostic.
    for evaluation, result in zip(parent.evaluations, results, strict=True):
        if not START <= result.from_inclusive < result.to_exclusive <= END:
            raise ValueError("SEALED_TEST_BOUNDARY")
        if result.fingerprint != evaluation.result_fingerprint:
            raise ValueError("PHASE114_REPLAY_DIVERGED")
        subset = slice_corpus(development, evaluation.window.start, evaluation.window.end)
        if result.dataset_artifact_fingerprint != digest(canonical_json(subset)):
            raise ValueError("RESULT_DEVELOPMENT_DATA_MISMATCH")
        if any(not START <= f.execution_time < END for f in result.fills):
            raise ValueError("SEALED_TEST_BOUNDARY")
    sessions, thresholds = session_records(development)
    evaluations = tuple(
        evaluation_record(e, r, sessions, development.bars)
        for e, r in zip(parent.evaluations, results, strict=True)
    )
    return DiagnosticReport(
        parent_corpus_fingerprint=spec.corpus_fingerprint,
        phase114_report_fingerprint=parent.fingerprint,
        plan_fingerprint=plan_fingerprint,
        implementation_fingerprint=implementation_fingerprint,
        cost_version=getattr(spec.config.costs, "version", "configured-fixed-bps-v1"),
        slippage_version=spec.slippage_version,
        thresholds=thresholds,
        sessions=sessions,
        evaluations=evaluations,
        findings=tuple(
            {
                "strategy": strategy,
                "scope": "DESCRIPTIVE_NOT_CAUSAL_OR_SELECTED",
                "fold_evidence": tuple(
                    {
                        "fold": e["fold"],
                        "partition": e["partition"],
                        "gross_before_slippage": e["summary"]["gross_before_slippage"],
                        "slippage": e["summary"]["slippage"],
                        "gross_pnl": e["summary"]["gross_pnl"],
                        "costs": e["summary"]["costs"],
                        "net_pnl": e["summary"]["net_pnl"],
                        "edge_case": e["summary"]["edge_case"],
                        "sample_status": e["summary"]["sample_status"],
                    }
                    for e in evaluations
                    if e["strategy"] == strategy
                ),
            }
            for strategy in sorted({e["strategy"] for e in evaluations})
        ),
    )
