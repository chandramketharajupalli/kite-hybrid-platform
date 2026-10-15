"""Frozen research definitions and session-local indicators; no Streak catalog claim."""

from decimal import Decimal, localcontext
from typing import Literal

from pydantic import Field, model_validator

from strategy_engine.backtest.dataset import Frozen, digest
from strategy_engine.phase14.engine import (
    PRECISION,
    Candle,
    Operand,
    Strategy,
    indicator,
    signal,
)

D = Decimal


class Definition(Frozen):
    identifier: Literal["S01", "S02", "S03", "S04", "S05", "S06"]
    name: str
    version: Literal["1"] = "1"
    provenance: Literal[
        "EXACT_STREAK_DEFINITION_VERIFIED",
        "USER_SUPPLIED_DEFINITION",
        "RESEARCH_EQUIVALENT",
        "UNVERIFIED_STRATEGY",
    ]
    source: str = "Phase14.15 user specification; explicitly defined research variant"
    entry: str
    indicators: tuple[str, ...]
    interval: Literal["5minute"] = "5minute"
    direction: Literal["LONG"] = "LONG"
    session: Literal["DATED_NSE_SESSION_RESET"] = "DATED_NSE_SESSION_RESET"
    exit: Literal["STOP_FIRST_TARGET_EOD"] = "STOP_FIRST_TARGET_EOD"
    stop: Decimal = Field(default=D("0.02"), gt=0, lt=1)
    target: Decimal = Field(default=D("0.02"), gt=0, lt=1)
    minimum_volume: int = Field(default=1000, ge=1)

    @model_validator(mode="after")
    def valid(self) -> "Definition":
        if not self.name.strip() or not self.entry.strip() or not self.indicators:
            raise ValueError("INCOMPLETE_DEFINITION")
        if (self.identifier == "S01") != (self.provenance == "USER_SUPPLIED_DEFINITION"):
            raise ValueError("DEFINITION_PROVENANCE")
        return self

    @property
    def fingerprint(self) -> str:
        return digest(self.model_dump_json())


def registry() -> tuple[Definition, ...]:
    rows = (
        ("S01", "RSI/EMA Reversal", "RSI14 crosses30 AND close crossesEMA21", ("RSI14", "EMA21")),
        ("S02", "EMA Trend Crossover", "EMA9 crosses above EMA21", ("EMA9", "EMA21")),
        ("S03", "RSI Momentum", "RSI14 crosses50 AND close>EMA21", ("RSI14", "EMA21")),
        ("S04", "Bollinger Reversion", "close crosses above lower BB20 population2SD", ("BB20_2",)),
        (
            "S05",
            "VWAP Reclaim",
            "close crossesHLC3VWAP,current volume>0,cumulative>=1000",
            ("VWAP",),
        ),
        ("S06", "Opening Range Breakout", "close crosses first15minute range high", ("OR15",)),
    )
    return tuple(
        Definition.model_validate(
            dict(
                identifier=i,
                name=n,
                entry=e,
                indicators=inds,
                provenance="USER_SUPPLIED_DEFINITION" if i == "S01" else "RESEARCH_EQUIVALENT",
            )
        )
        for i, n, e, inds in rows
    )


def validate_registry(definitions: tuple[Definition, ...]) -> None:
    if not definitions or len({d.identifier for d in definitions}) != len(definitions):
        raise ValueError("DUPLICATE_OR_EMPTY_REGISTRY")
    for d in definitions:
        reference = next(x for x in registry() if x.identifier == d.identifier)
        # Only risk/volume parameters may vary; semantic changes need a new code version.
        if (
            d.model_copy(
                update={
                    "stop": reference.stop,
                    "target": reference.target,
                    "minimum_volume": reference.minimum_volume,
                }
            )
            != reference
        ):
            raise ValueError("UNRECOGNIZED_DEFINITION_VERSION")


def crosses(
    a: Decimal | None,
    b: Decimal | None,
    c: Decimal | None,
    d: Decimal | None,
    *,
    below: bool = False,
) -> bool:
    if a is None or b is None or c is None or d is None:
        return False
    return a >= b and c < d if below else a <= b and c > d


def sma(values: tuple[Decimal, ...], period: int) -> Decimal | None:
    if period < 1:
        raise ValueError("INVALID_PERIOD")
    if len(values) < period:
        return None
    with localcontext(PRECISION):
        return sum(values[-period:], D(0)) / period


def bands(values: tuple[Decimal, ...], period: int = 20) -> tuple[Decimal, Decimal] | None:
    mean = sma(values, period)
    if mean is None:
        return None
    with localcontext(PRECISION):
        sd = (sum(((v - mean) ** 2 for v in values[-period:]), D(0)) / period).sqrt()
        return mean - 2 * sd, mean + 2 * sd


def vwap(bars: tuple[Candle, ...]) -> Decimal | None:
    volume = sum(b.volume for b in bars)
    if volume == 0:
        return None
    with localcontext(PRECISION):
        return sum(((b.high + b.low + b.close) / 3 * b.volume for b in bars), D(0)) / volume


def opening_range(bars: tuple[Candle, ...]) -> tuple[Decimal, Decimal] | None:
    if len(bars) < 3:
        return None
    return max(b.high for b in bars[:3]), min(b.low for b in bars[:3])


def entry_signal(bars: tuple[Candle, ...], definition: Definition) -> bool:
    """Caller supplies only contiguous completed bars of ONE session."""
    if len(bars) < 2:
        return False
    closes = tuple(b.close for b in bars)
    previous = closes[:-1]
    kind = definition.identifier
    if kind == "S01":
        return signal(closes, Strategy())
    if kind == "S02":
        fast, slow = Operand(indicator="EMA", period=9), Operand(indicator="EMA", period=21)
        return crosses(
            indicator(previous, fast),
            indicator(previous, slow),
            indicator(closes, fast),
            indicator(closes, slow),
        )
    if kind == "S03":
        rsi, ema = Operand(indicator="RSI", period=14), Operand(indicator="EMA", period=21)
        average = indicator(closes, ema)
        return (
            average is not None
            and closes[-1] > average
            and crosses(indicator(previous, rsi), D(50), indicator(closes, rsi), D(50))
        )
    if kind == "S04":
        before, now = bands(previous), bands(closes)
        return (
            before is not None
            and now is not None
            and crosses(closes[-2], before[0], closes[-1], now[0])
        )
    if kind == "S05":
        return (
            bars[-1].volume > 0
            and sum(b.volume for b in bars) >= definition.minimum_volume
            and crosses(closes[-2], vwap(bars[:-1]), closes[-1], vwap(bars))
        )
    interval = opening_range(bars)
    # First15minutes must finish before the breakout signal bar.
    return (
        len(bars) >= 4
        and interval is not None
        and crosses(closes[-2], interval[0], closes[-1], interval[0])
    )
