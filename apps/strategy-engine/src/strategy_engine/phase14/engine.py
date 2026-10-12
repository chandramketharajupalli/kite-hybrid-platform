"""Deterministic, isolated BACKTEST_ONLY five-minute engine; no live handoff."""

from datetime import UTC, date, datetime, timedelta
from decimal import ROUND_HALF_EVEN, Context, Decimal, localcontext
from statistics import NormalDist
from typing import Literal

from pydantic import Field, model_validator

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import (
    NSE,
    Bar,
    Frozen,
    Hash,
    Instant,
    Money,
    digest,
)

D = Decimal
FIVE = timedelta(minutes=5)
PRECISION = Context(prec=40, rounding=ROUND_HALF_EVEN)
VERSION: Literal["phase14-five-minute-v1"] = "phase14-five-minute-v1"


class Candle(Bar):
    @property
    def end(self) -> datetime:
        return self.start + FIVE

    @model_validator(mode="after")
    def aligned(self) -> "Candle":
        if self.start.minute % 5:
            raise ValueError("FIVE_MINUTE_ALIGNMENT")
        return self


class Session(Frozen):
    start: Instant
    end: Instant

    @model_validator(mode="after")
    def valid(self) -> "Session":
        if (
            self.start >= self.end
            or (self.end - self.start) % FIVE
            or self.start.second
            or self.start.microsecond
            or self.start.minute % 5
            or self.start.astimezone(NSE).date() != self.end.astimezone(NSE).date()
        ):
            raise ValueError("INVALID_DATED_SESSION")
        return self


class Series(Frozen):
    exchange: Literal["NSE"] = "NSE"
    symbol: str = Field(min_length=1)
    instrument_token: str = Field(pattern=r"^[1-9][0-9]{0,9}$")
    interval: Literal["5minute"] = "5minute"
    source: str = Field(min_length=1)
    synthetic: bool
    source_hash: Hash
    master_hash: Hash
    calendar_hash: Hash
    as_of: Instant
    from_inclusive: Instant
    to_exclusive: Instant
    adjustment: Literal["PROVIDER_UNSPECIFIED_NO_LOCAL_ADJUSTMENT"] = (
        "PROVIDER_UNSPECIFIED_NO_LOCAL_ADJUSTMENT"
    )
    corporate_actions_reviewed: bool = False
    sessions: tuple[Session, ...]
    non_trading_days: tuple[date, ...] = ()
    bars: tuple[Candle, ...]

    @model_validator(mode="after")
    def valid(self) -> "Series":
        if not self.from_inclusive < self.to_exclusive <= self.as_of:
            raise ValueError("FUTURE_OR_INVALID_WINDOW")
        if self.to_exclusive - self.from_inclusive > timedelta(days=366):
            raise ValueError("MAXIMUM_TWELVE_MONTHS")
        previous = self.from_inclusive
        for session in self.sessions:
            if session.start < previous or session.end > self.to_exclusive:
                raise ValueError("SESSION_ORDER_OR_RANGE")
            previous = session.end
        first = self.from_inclusive.astimezone(NSE).date()
        last = (self.to_exclusive - timedelta(microseconds=1)).astimezone(NSE).date()
        required = {first + timedelta(days=n) for n in range((last - first).days + 1)}
        active = {s.start.astimezone(NSE).date() for s in self.sessions}
        closed = set(self.non_trading_days)
        if active & closed or active | closed != required:
            raise ValueError("CALENDAR_UNKNOWN_OR_CONFLICT")
        starts = tuple(b.start for b in self.bars)
        if starts != tuple(sorted(set(starts))):
            raise ValueError("DUPLICATE_OR_OUT_OF_ORDER")
        if any(b.start < self.from_inclusive or b.end > self.to_exclusive for b in self.bars):
            raise ValueError("BAR_OUTSIDE_WINDOW")
        return self

    def quality(self) -> tuple[tuple[datetime, ...], tuple[datetime, ...]]:
        expected = set()
        for session in self.sessions:
            at = session.start
            while at < session.end:
                expected.add(at)
                at += FIVE
        actual = {b.start for b in self.bars}
        return tuple(sorted(expected - actual)), tuple(sorted(actual - expected))


class Operand(Frozen):
    indicator: Literal["CLOSE", "EMA", "RSI", "CONSTANT"]
    period: int = Field(default=1, ge=1, le=1440)
    value: Money = D(0)
    lag: int = Field(default=0, ge=0, le=1440)


class Cross(Frozen):
    left: Operand
    right: Operand
    direction: Literal["ABOVE", "BELOW"] = "ABOVE"


class Strategy(Frozen):
    name: str = "RSI14_EMA21_RECOVERY"
    version: str = "1"
    confirmed: bool = True
    conditions: tuple[Cross, ...] = (
        Cross(
            left=Operand(indicator="RSI", period=14),
            right=Operand(indicator="CONSTANT", value=D(30)),
        ),
        Cross(left=Operand(indicator="CLOSE"), right=Operand(indicator="EMA", period=21)),
    )
    direction: Literal["LONG", "SHORT"] = "LONG"
    stop: Money = D("0.02")
    target: Money = D("0.02")

    @model_validator(mode="after")
    def valid(self) -> "Strategy":
        if not self.conditions or not 0 < self.stop < 1 or not 0 < self.target < 1:
            raise ValueError("INVALID_STRATEGY")
        if (
            self.conditions != type(self).model_fields["conditions"].default
            and "confirmed" not in self.model_fields_set
        ):
            raise ValueError("ADDITIONAL_RULES_REQUIRE_EXPLICIT_CONFIRMATION")
        return self


def indicator(values: tuple[Decimal, ...], operand: Operand) -> Decimal | None:
    if operand.lag:
        values = values[: -operand.lag]
    if not values:
        return None
    if operand.indicator == "CONSTANT":
        return operand.value
    if operand.indicator == "CLOSE":
        return values[-1]
    n = operand.period
    with localcontext(PRECISION):
        if operand.indicator == "EMA":
            if len(values) < n:
                return None
            result = sum(values[:n], D(0)) / n
            alpha = D(2) / (n + 1)
            for value in values[n:]:
                result += alpha * (value - result)
            return +result
        if len(values) <= n:
            return None
        changes = tuple(b - a for a, b in zip(values, values[1:], strict=False))
        gain = sum((max(v, D(0)) for v in changes[:n]), D(0)) / n
        loss = sum((max(-v, D(0)) for v in changes[:n]), D(0)) / n
        for change in changes[n:]:
            gain = (gain * (n - 1) + max(change, D(0))) / n
            loss = (loss * (n - 1) + max(-change, D(0))) / n
        if loss == 0:
            return D(50) if gain == 0 else D(100)
        return +(100 - 100 / (1 + gain / loss))


def signal(closes: tuple[Decimal, ...], strategy: Strategy) -> bool:
    """Both crosses must occur on the same completed bar; never latch earlier crosses."""
    if not strategy.confirmed or strategy.direction != "LONG":
        raise ValueError("UNCONFIRMED_OR_UNSUPPORTED_STRATEGY")
    for rule in strategy.conditions:
        lp, rp = indicator(closes[:-1], rule.left), indicator(closes[:-1], rule.right)
        lc, rc = indicator(closes, rule.left), indicator(closes, rule.right)
        if lp is None or rp is None or lc is None or rc is None:
            return False
        if not (lp <= rp and lc > rc if rule.direction == "ABOVE" else lp >= rp and lc < rc):
            return False
    return True


class Settings(Frozen):
    mode: Literal["BACKTEST_ONLY"] = "BACKTEST_ONLY"
    capital: Money = D(10000)
    order_ceiling: Money = D(10000)
    price_buffer: Money = D("1.05")
    quantity: int = Field(default=1, ge=1)
    slippage_bps: Money = D(5)
    participation: Money = D("0.01")
    allow_long: bool = True
    allow_short: Literal[False] = False

    @model_validator(mode="after")
    def valid(self) -> "Settings":
        if (
            not 0 < self.order_ceiling <= 10000
            or self.capital <= 0
            or self.price_buffer < 1
            or self.slippage_bps > 1000
            or not 0 < self.participation <= 1
        ):
            raise ValueError("INVALID_SIMULATION_LIMITS")
        return self


class Trade(Frozen):
    signal_at: Instant
    entry_at: Instant
    exit_at: Instant
    entry: Decimal
    exit: Decimal
    quantity: int
    reason: Literal["STOP", "TARGET", "EOD"]
    gross: Decimal
    fees: Decimal
    net: Decimal


class Metrics(Frozen):
    trades: int
    wins: int
    losses: int
    breakeven: int
    win_rate: Decimal
    expectancy: Decimal
    net: Decimal
    net_return: Decimal
    costs: Decimal
    drawdown: Decimal


def metrics(trades: tuple[Trade, ...], capital: Decimal) -> Metrics:
    with localcontext(PRECISION):
        count = len(trades)
        wins = sum(t.net > 0 for t in trades)
        net = sum((t.net for t in trades), D(0))
        equity = peak = capital
        drawdown = D(0)
        for trade in trades:
            equity += trade.net
            peak = max(peak, equity)
            drawdown = max(drawdown, (peak - equity) / peak)
        return Metrics(
            trades=count,
            wins=wins,
            losses=sum(t.net < 0 for t in trades),
            breakeven=sum(t.net == 0 for t in trades),
            win_rate=D(wins) / count if count else D(0),
            expectancy=net / count if count else D(0),
            net=net,
            net_return=net / capital,
            costs=sum((t.fees for t in trades), D(0)),
            drawdown=drawdown,
        )


class Simulation(Frozen):
    engine: Literal["phase14-five-minute-v1"] = VERSION
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


def simulate(
    series: Series, strategy: Strategy, settings: Settings, costs: IntradayCostSchedule
) -> Simulation:
    missing, unexpected = series.quality()
    if missing or unexpected or not series.sessions:
        raise ValueError("INCOMPLETE_OR_OUT_OF_SESSION_DATA")
    if not series.corporate_actions_reviewed:
        raise ValueError("CORPORATE_ACTION_REVIEW_REQUIRED")
    signal((), strategy)  # Validate permissions even for an empty series.
    with localcontext(PRECISION):
        cash = settings.capital
        trades: list[Trade] = []
        no_fills: list[str] = []
        exposure = 0
        peak = cash
        marked_drawdown = D(0)
        by_start = {bar.start: bar for bar in series.bars}
        slip = settings.slippage_bps / 10000
        for session in series.sessions:
            closes: tuple[Decimal, ...] = ()
            position: tuple[datetime, datetime, Decimal, Decimal] | None = None
            pending: datetime | None = None
            at = session.start
            while at < session.end:
                bar = by_start[at]
                last = bar.end == session.end
                liquid = D(bar.volume) * settings.participation >= settings.quantity
                if pending is not None and position is None:
                    entry = bar.open * (1 + slip)
                    notional = entry * settings.quantity
                    entry_fee = costs.quote("BUY", notional, at, None).total
                    if (
                        not settings.allow_long
                        or last
                        or not liquid
                        or cash < notional + entry_fee
                        or notional * settings.price_buffer > settings.order_ceiling
                    ):
                        no_fills.append(f"{at.isoformat()}:ENTRY_LIMIT_OR_LIQUIDITY")
                    else:
                        position = pending, at, entry, entry_fee
                        cash -= notional + entry_fee
                    pending = None
                if position is not None:
                    decided, entered, entry, entry_fee = position
                    mark_value = bar.low * (1 - slip) * settings.quantity
                    mark_fee = costs.quote("SELL", mark_value, at, entry * settings.quantity).total
                    marked_drawdown = max(
                        marked_drawdown, (peak - cash - mark_value + mark_fee) / peak
                    )
                    stop, target = entry * (1 - strategy.stop), entry * (1 + strategy.target)
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
                            # Do not fabricate mandatory square-off in an illiquid bar.
                            raise ValueError("EXIT_NO_FILL_UNVALUED_POSITION")
                        exit_price = reference * (1 - slip)
                        entry_value, exit_value = (
                            entry * settings.quantity,
                            exit_price * settings.quantity,
                        )
                        fee = costs.quote("SELL", exit_value, at, entry_value).total + entry_fee
                        gross = exit_value - entry_value
                        trades.append(
                            Trade(
                                signal_at=decided,
                                entry_at=entered,
                                exit_at=bar.end,
                                entry=entry,
                                exit=exit_price,
                                quantity=settings.quantity,
                                reason=reason,
                                gross=gross,
                                fees=fee,
                                net=gross - fee,
                            )
                        )
                        cash += exit_value - (fee - entry_fee)
                        exposure += int((bar.end - entered).total_seconds())
                        position = None
                if position is None:
                    peak = max(peak, cash)
                else:
                    peak = max(peak, cash + bar.close * settings.quantity)
                closes += (bar.close,)
                if position is None and not last and signal(closes, strategy):
                    pending = bar.end
                at += FIVE
        result = tuple(trades)
        return Simulation(
            synthetic=series.synthetic,
            symbol=series.symbol,
            strategy_hash=digest(strategy.model_dump_json()),
            input_hash=digest(series.model_dump_json()),
            config_hash=digest(settings.model_dump_json() + costs.model_dump_json()),
            trades=result,
            metrics=metrics(result, settings.capital),
            no_fills=tuple(no_fills),
            exposure_seconds=exposure,
            marked_drawdown=marked_drawdown,
        )


class Policy(Frozen):
    min_oos_trades: int = Field(default=100, ge=30)
    min_fold_trades: int = Field(default=30, ge=10)
    min_win_rate: Money = D("0.70")
    max_drawdown: Money = D("0.10")
    min_fold_win_rate: Money = D("0.60")
    family_size: int = Field(default=2230, ge=1)
    alpha: Money = D("0.05")

    @model_validator(mode="after")
    def valid(self) -> "Policy":
        if (
            not D("0.70") <= self.min_win_rate <= 1
            or not 0 < self.max_drawdown < 1
            or not 0 < self.alpha < 1
            or not 0 <= self.min_fold_win_rate <= 1
        ):
            raise ValueError("INVALID_QUALIFICATION_POLICY")
        return self


class Qualification(Frozen):
    qualified: bool
    reasons: tuple[str, ...]
    out_of_sample: Metrics
    folds: tuple[Metrics, ...]
    adjusted_wilson_lower: float


def qualify(
    trades: tuple[Trade, ...],
    capital: Decimal,
    oos_start: datetime,
    boundaries: tuple[datetime, ...],
    policy: Policy,
) -> Qualification:
    if (
        len(boundaries) < 4
        or boundaries[0] != oos_start
        or boundaries != tuple(sorted(set(boundaries)))
        or any(t.tzinfo != UTC for t in boundaries)
    ):
        raise ValueError("PREDECLARED_THREE_OOS_FOLDS_REQUIRED")
    oos = tuple(t for t in trades if oos_start <= t.entry_at < boundaries[-1])
    result = metrics(oos, capital)
    folds = tuple(
        metrics(tuple(t for t in oos if a <= t.entry_at < b), capital)
        for a, b in zip(boundaries, boundaries[1:], strict=False)
    )
    reasons = []
    if result.trades < policy.min_oos_trades:
        reasons.append("INSUFFICIENT_OOS_TRADES")
    if result.win_rate < policy.min_win_rate:
        reasons.append("OOS_WIN_RATE_BELOW_THRESHOLD")
    if result.expectancy <= 0:
        reasons.append("NON_POSITIVE_NET_EXPECTANCY")
    if result.drawdown > policy.max_drawdown:
        reasons.append("DRAWDOWN_LIMIT")
    if any(
        f.trades < policy.min_fold_trades
        or f.expectancy <= 0
        or f.win_rate < policy.min_fold_win_rate
        or f.drawdown > policy.max_drawdown
        for f in folds
    ):
        reasons.append("UNSTABLE_OR_INSUFFICIENT_WALK_FORWARD")
    lower = 0.0
    if result.trades:
        z = NormalDist().inv_cdf(1 - float(policy.alpha) / (2 * policy.family_size))
        n, p = result.trades, float(result.win_rate)
        lower = (p + z * z / (2 * n) - z * ((p * (1 - p) / n + z * z / (4 * n * n)) ** 0.5)) / (
            1 + z * z / n
        )
    if lower <= 0.5:
        reasons.append("MULTIPLE_TESTING_UNCERTAINTY")
    return Qualification(
        qualified=not reasons,
        reasons=tuple(reasons),
        out_of_sample=result,
        folds=folds,
        adjusted_wilson_lower=lower,
    )
