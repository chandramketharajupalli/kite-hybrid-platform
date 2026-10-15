"""Session executor extension; reuses Phase14 contracts, costs and accounting."""

from datetime import datetime
from decimal import Decimal, localcontext
from typing import Literal

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import NSE, Frozen, Hash, digest
from strategy_engine.phase14.engine import (
    FIVE,
    PRECISION,
    Candle,
    Metrics,
    Series,
    Settings,
    Trade,
    metrics,
)
from strategy_engine.phase1415.strategies import Definition, entry_signal, validate_registry

D = Decimal


class Execution(Frozen):
    engine: Literal["phase1415-session-executor-v1"] = "phase1415-session-executor-v1"
    mode: Literal["BACKTEST_ONLY"] = "BACKTEST_ONLY"
    synthetic: bool
    symbol: str
    strategy_hash: Hash
    input_hash: Hash
    config_hash: Hash
    trades: tuple[Trade, ...]
    metrics: Metrics
    no_fills: tuple[str, ...]
    exposure_seconds: int
    marked_drawdown: Decimal


def execute(
    series: Series, definition: Definition, settings: Settings, costs: IntradayCostSchedule
) -> Execution:
    validate_registry((definition,))
    missing, unexpected = series.quality()
    if missing or unexpected or not series.sessions:
        raise ValueError("INCOMPLETE_OR_OUT_OF_SESSION_DATA")
    if len(series.bars) > 30000:
        raise ValueError("SERIES_RESOURCE_LIMIT")
    if not series.corporate_actions_reviewed:
        raise ValueError("CORPORATE_ACTION_REVIEW_REQUIRED")
    with localcontext(PRECISION):
        cash = peak = settings.capital
        drawdown = D(0)
        trades: list[Trade] = []
        no_fills: list[str] = []
        exposure = 0
        by_start = {b.start: b for b in series.bars}
        slip = settings.slippage_bps / 10000
        for session in series.sessions:
            if (session.end - session.start) / FIVE > 288:
                raise ValueError("SESSION_RESOURCE_LIMIT")
            if definition.identifier == "S06" and (
                session.start.astimezone(NSE).strftime("%H:%M") != "09:15"
                or session.end - session.start < 4 * FIVE
            ):
                raise ValueError("OPENING_RANGE_REQUIRES_REGULAR_SESSION")
            history: tuple[Candle, ...] = ()
            pending: datetime | None = None
            position: tuple[datetime, datetime, Decimal, Decimal] | None = None
            at = session.start
            while at < session.end:
                bar = by_start[at]
                last = bar.end == session.end
                liquid = D(bar.volume) * settings.participation >= settings.quantity
                if pending is not None:
                    entry = bar.open * (1 + slip)
                    value = entry * settings.quantity
                    fee = costs.quote("BUY", value, at, None).total
                    if (
                        not settings.allow_long
                        or last
                        or not liquid
                        or cash < value + fee
                        or value * settings.price_buffer > settings.order_ceiling
                    ):
                        no_fills.append(f"{at.isoformat()}:ENTRY_LIMIT_OR_LIQUIDITY")
                    else:
                        position = pending, at, entry, fee
                        cash -= value + fee
                    pending = None
                if position is not None:
                    decided, entered, entry, entry_fee = position
                    mark = bar.low * (1 - slip) * settings.quantity
                    mark_fee = costs.quote("SELL", mark, at, entry * settings.quantity).total
                    drawdown = max(drawdown, (peak - cash - mark + mark_fee) / peak)
                    stop, target = entry * (1 - definition.stop), entry * (1 + definition.target)
                    reason: Literal["STOP", "TARGET", "EOD"] | None = None
                    reference = bar.close
                    if bar.low <= stop:
                        reference, reason = min(stop, bar.open), "STOP"
                    elif bar.high >= target:
                        reference, reason = target, "TARGET"
                    elif last:
                        reason = "EOD"
                    if reason is not None:
                        if not liquid:
                            raise ValueError("EXIT_NO_FILL_UNVALUED_POSITION")
                        exit_price = reference * (1 - slip)
                        buy, sell = entry * settings.quantity, exit_price * settings.quantity
                        fee = entry_fee + costs.quote("SELL", sell, at, buy).total
                        trades.append(
                            Trade(
                                signal_at=decided,
                                entry_at=entered,
                                exit_at=bar.end,
                                entry=entry,
                                exit=exit_price,
                                quantity=settings.quantity,
                                reason=reason,
                                gross=sell - buy,
                                fees=fee,
                                net=sell - buy - fee,
                            )
                        )
                        cash += sell - (fee - entry_fee)
                        exposure += int((bar.end - entered).total_seconds())
                        position = None
                peak = max(peak, cash if position is None else cash + bar.close * settings.quantity)
                history += (bar,)
                if position is None and not last and entry_signal(history, definition):
                    pending = bar.end
                at += FIVE
        result = tuple(trades)
        return Execution(
            synthetic=series.synthetic,
            symbol=series.symbol,
            strategy_hash=definition.fingerprint,
            input_hash=digest(series.model_dump_json()),
            config_hash=digest(settings.model_dump_json() + costs.model_dump_json()),
            trades=result,
            metrics=metrics(result, settings.capital),
            no_fills=tuple(no_fills),
            exposure_seconds=exposure,
            marked_drawdown=drawdown,
        )
