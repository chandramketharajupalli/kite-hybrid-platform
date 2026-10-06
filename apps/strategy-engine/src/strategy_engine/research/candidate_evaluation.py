"""Development reuse only; sealed July and prospective windows have no evaluation branch."""

from datetime import UTC, datetime
from decimal import Decimal, localcontext
from typing import Any, Literal

from strategy_engine.backtest.corpus import Corpus
from strategy_engine.backtest.dataset import NSE, Frozen, Hash, digest
from strategy_engine.backtest.engine import ARITHMETIC, Config, Result, canonical_json, run
from strategy_engine.research.candidates import (
    CandidateSpec,
    CandidateStrategy,
    reject_sealed_window,
)
from strategy_engine.research.diagnostics import ratio, sensitivity
from strategy_engine.research.strategy import BaselineStrategy

DEVELOPMENT_START = datetime(2026, 2, 1, 18, 30, tzinfo=UTC)
DEVELOPMENT_END = datetime(2026, 6, 30, 18, 30, tzinfo=UTC)


class ReuseReport(Frozen):
    schema_version: Literal["FrozenHypothesisDevelopmentReuse.v1"] = (
        "FrozenHypothesisDevelopmentReuse.v1"
    )
    usage: Literal["DEVELOPMENT_REUSE_ONLY"] = "DEVELOPMENT_REUSE_ONLY"
    freeze_fingerprint: Hash
    registration_fingerprint: Hash
    parent_corpus_fingerprint: Hash
    parent_report_fingerprint: Hash
    diagnostic_fingerprint: Hash
    implementation_fingerprints: tuple[Hash, ...]
    development_window: tuple[datetime, datetime] = (DEVELOPMENT_START, DEVELOPMENT_END)
    excluded_test_window: tuple[datetime, datetime] = (
        DEVELOPMENT_END,
        datetime(2026, 7, 31, 18, 30, tzinfo=UTC),
    )
    cost_version: str
    slippage_version: Literal["adverse-bps-v1"] = "adverse-bps-v1"
    test_state: Literal["SEALED_NOT_EVALUATED"] = "SEALED_NOT_EVALUATED"
    confirmation_state: Literal["NOT_ASSESSED_ON_REUSED_DEVELOPMENT"] = (
        "NOT_ASSESSED_ON_REUSED_DEVELOPMENT"
    )
    selection: Literal["NONE"] = "NONE"
    evaluations: tuple[dict[str, Any], ...]

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


def validate_reuse(data: Corpus) -> None:
    reject_sealed_window(data.from_inclusive, data.to_exclusive)
    if not DEVELOPMENT_START <= data.from_inclusive < data.to_exclusive <= DEVELOPMENT_END:
        raise ValueError("DEVELOPMENT_REUSE_ONLY_WINDOW_REQUIRED")
    if any(not data.from_inclusive <= b.start < b.end <= data.to_exclusive for b in data.bars):
        raise ValueError("OUTSIDE_REUSE_WINDOW")


def run_reuse(
    data: Corpus, config: Config, candidate: CandidateSpec, role: Literal["CANDIDATE", "COMPARATOR"]
) -> Result:
    validate_reuse(data)  # Before creating either candidate or comparator callbacks.
    candidate = CandidateSpec.model_validate(candidate.model_dump())
    if role == "CANDIDATE":
        return run(data, config, candidate.engine_spec(), CandidateStrategy(candidate, config))
    if role == "COMPARATOR":
        return run(
            data,
            config,
            candidate.comparator.engine_spec(),
            BaselineStrategy(specification=candidate.comparator, config=config),
        )
    raise ValueError("UNKNOWN_REUSE_ROLE")


def describe(
    data: Corpus, result: Result, candidate: CandidateSpec, role: Literal["CANDIDATE", "COMPARATOR"]
) -> dict[str, Any]:
    validate_reuse(data)
    if (
        result.from_inclusive != data.from_inclusive
        or result.to_exclusive != data.to_exclusive
        or result.dataset_artifact_fingerprint != digest(canonical_json(data))
    ):
        raise ValueError("REUSE_RESULT_DATA_MISMATCH")
    days = tuple(d.date for d in data.calendar.days if d.status == "EXPECTED_SESSION")
    rows: list[dict[str, Any]] = []
    with localcontext(ARITHMETIC):
        for trade in result.trades:
            if (
                not data.from_inclusive
                <= trade.entry.execution_time
                < (trade.exit.execution_time)
                < data.to_exclusive
            ):
                raise ValueError("TRADE_OUTSIDE_REUSE_WINDOW")
            raw = (trade.exit.reference_price - trade.entry.reference_price) * trade.entry.quantity
            slippage = (
                trade.entry.adverse_slippage + trade.exit.adverse_slippage
            ) * trade.entry.quantity
            if raw - slippage - trade.costs != trade.net_pnl:
                raise ValueError("REUSE_ATTRIBUTION_MISMATCH")
            rows.append(
                {
                    "date": trade.entry.execution_time.astimezone(NSE).date(),
                    "entry": trade.entry.execution_time,
                    "exit": trade.exit.execution_time,
                    "raw_gross": raw,
                    "slippage": slippage,
                    "fees": trade.costs,
                    "net": trade.net_pnl,
                    "holding_minutes": (
                        trade.exit.execution_time - trade.entry.execution_time
                    ).seconds
                    // 60,
                }
            )
        daily = [
            {
                "date": day,
                "trade_count": sum(r["date"] == day for r in rows),
                **{
                    key: sum((r[key] for r in rows if r["date"] == day), Decimal(0))
                    for key in ("raw_gross", "slippage", "fees", "net")
                },
            }
            for day in days
        ]
        totals = {
            key: sum((r[key] for r in rows), Decimal(0))
            for key in ("raw_gross", "slippage", "fees", "net")
        }
        if totals["net"] != result.metrics.net_pnl or totals["fees"] != result.metrics.costs:
            raise ValueError("REUSE_METRICS_MISMATCH")
        ordered = sorted(daily, key=lambda d: (d["net"], d["date"]))
        absolute = sum((abs(d["net"]) for d in daily), Decimal(0))
        return {
            "usage": "DEVELOPMENT_REUSE_ONLY",
            "hypothesis": candidate.hypothesis,
            "role": role,
            "window": (data.from_inclusive, data.to_exclusive),
            "result_fingerprint": result.fingerprint,
            "strategy": result.strategy.model_dump(),
            "eligible_sessions": len(days),
            "traded_sessions": len({r["date"] for r in rows}),
            "trade_count": len(rows),
            "short_round_trips_le_5": sum(r["holding_minutes"] <= 5 for r in rows),
            **totals,
            "engine_gross": result.metrics.gross_pnl,
            "raw_gross_per_trade": ratio(totals["raw_gross"], len(rows)),
            "friction_per_session": ratio(totals["slippage"] + totals["fees"], len(days)),
            "marked_max_drawdown": result.metrics.max_drawdown,
            "daily": daily,
            "trade_audit": rows,
            "concentration": {
                "best": ordered[-1],
                "worst": ordered[0],
                "top_three_net": sum((d["net"] for d in ordered[-3:]), Decimal(0)),
                "bottom_three_net": sum((d["net"] for d in ordered[:3]), Decimal(0)),
                "sum_abs_daily_net": absolute,
                "worst_share_abs_daily": ratio(ordered[0]["net"], absolute),
            },
            "slippage_sensitivity": sensitivity(result),
        }
