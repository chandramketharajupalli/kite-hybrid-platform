"""Session-local indicators. None means insufficient evidence, never a fabricated value."""
from decimal import ROUND_HALF_EVEN, Context, Decimal, localcontext

from pydantic import model_validator

from strategy_engine.backtest.dataset import MINUTE, Bar, Frozen, Instant
from strategy_engine.backtest.engine import Decision

FEATURE_VERSION = "session-decimal40-v1"
PRECISION = Context(prec=40, rounding=ROUND_HALF_EVEN)


class CompletedSession(Frozen):
    opened_at: Instant
    closes_at: Instant
    decision_at: Instant
    bars: tuple[Bar, ...]

    @model_validator(mode="after")
    def valid(self) -> "CompletedSession":
        if not self.opened_at < self.closes_at or self.decision_at > self.closes_at:
            raise ValueError("INVALID_SESSION_VIEW")
        if not self.bars or self.bars[-1].end != self.decision_at:
            raise ValueError("INVALID_COMPLETED_PREFIX")
        for index, bar in enumerate(self.bars):
            if bar.start != self.opened_at + index * MINUTE or bar.end > self.decision_at:
                raise ValueError("FUTURE_OR_MISSING_BAR")
        return self

    @classmethod
    def from_decision(cls, context: Decision) -> "CompletedSession":
        if context.session_open is None or context.session_close is None:
            raise ValueError("SESSION_EVIDENCE_REQUIRED")
        if any(b.end > context.decision_time for b in context.completed_bars):
            raise ValueError("FUTURE_BAR")
        return cls(opened_at=context.session_open, closes_at=context.session_close,
                   decision_at=context.decision_time,
                   bars=tuple(b for b in context.completed_bars if b.start >= context.session_open))

    def previous(self) -> "CompletedSession | None":
        if len(self.bars) < 2:
            return None
        return CompletedSession(opened_at=self.opened_at, closes_at=self.closes_at,
                                decision_at=self.bars[-2].end, bars=self.bars[:-1])


def _window(n: int) -> None:
    if type(n) is not int or not 1 <= n <= 1440:
        raise ValueError("INVALID_LOOKBACK")


def simple_return(view: CompletedSession) -> Decimal | None:
    if len(view.bars) < 2:
        return None
    with localcontext(PRECISION):
        return view.bars[-1].close / view.bars[-2].close - 1


def sma(view: CompletedSession, n: int) -> Decimal | None:
    _window(n)
    if len(view.bars) < n:
        return None
    with localcontext(PRECISION):
        return sum((b.close for b in view.bars[-n:]), Decimal(0)) / n


def ema(view: CompletedSession, n: int) -> Decimal | None:
    _window(n)
    if len(view.bars) < n:
        return None
    with localcontext(PRECISION):
        value = sum((b.close for b in view.bars[:n]), Decimal(0)) / n
        alpha = Decimal(2) / (n + 1)
        for bar in view.bars[n:]:
            value = alpha * bar.close + (1 - alpha) * value
        return value


def vwap(view: CompletedSession) -> Decimal | None:
    volume = sum(b.volume for b in view.bars)
    if volume == 0:
        return None
    with localcontext(PRECISION):
        total = sum(((b.high + b.low + b.close) * b.volume for b in view.bars), Decimal(0))
        return total / (3 * volume)


def rsi(view: CompletedSession, n: int) -> Decimal | None:
    _window(n)
    if len(view.bars) < n + 1:
        return None
    with localcontext(PRECISION):
        changes = [b.close - a.close for a, b in zip(view.bars, view.bars[1:], strict=False)]
        gain = sum((max(d, Decimal(0)) for d in changes[:n]), Decimal(0)) / n
        loss = sum((max(-d, Decimal(0)) for d in changes[:n]), Decimal(0)) / n
        for change in changes[n:]:
            gain = (gain * (n - 1) + max(change, Decimal(0))) / n
            loss = (loss * (n - 1) + max(-change, Decimal(0))) / n
        if not gain and not loss:
            return Decimal(50)
        if not loss:
            return Decimal(100)
        return 100 - 100 / (1 + gain / loss)


def atr(view: CompletedSession, n: int) -> Decimal | None:
    _window(n)
    if len(view.bars) < n:
        return None
    with localcontext(PRECISION):
        ranges = [view.bars[0].high - view.bars[0].low]
        for prior, bar in zip(view.bars, view.bars[1:], strict=False):
            ranges.append(max(bar.high - bar.low, abs(bar.high - prior.close),
                              abs(bar.low - prior.close)))
        value = sum(ranges[:n], Decimal(0)) / n
        for true_range in ranges[n:]:
            value = (value * (n - 1) + true_range) / n
        return value


def rolling_high(view: CompletedSession, n: int, *, exclude_current: bool = False
                 ) -> Decimal | None:
    _window(n)
    bars = view.bars[:-1] if exclude_current else view.bars
    return None if len(bars) < n else max(b.high for b in bars[-n:])


def rolling_low(view: CompletedSession, n: int, *, exclude_current: bool = False) -> Decimal | None:
    _window(n)
    bars = view.bars[:-1] if exclude_current else view.bars
    return None if len(bars) < n else min(b.low for b in bars[-n:])


def volume_sma(view: CompletedSession, n: int) -> Decimal | None:
    _window(n)
    if len(view.bars) < n:
        return None
    with localcontext(PRECISION):
        return Decimal(sum(b.volume for b in view.bars[-n:])) / n


def relative_volume(view: CompletedSession, n: int) -> Decimal | None:
    _window(n)
    previous = view.previous()
    average = None if previous is None else volume_sma(previous, n)
    if average is None or average == 0:
        return None
    with localcontext(PRECISION):
        return Decimal(view.bars[-1].volume) / average


def opening_range(view: CompletedSession, n: int) -> tuple[Decimal, Decimal] | None:
    _window(n)
    if len(view.bars) < n:
        return None
    return min(b.low for b in view.bars[:n]), max(b.high for b in view.bars[:n])
