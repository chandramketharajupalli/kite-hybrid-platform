"""Four stateless research baselines; outputs are simulated intents only."""
from typing import Annotated, Literal

from pydantic import Field, model_validator

from strategy_engine.backtest.dataset import NSE, Frozen, Hash, Money, Quantity, decimal_text
from strategy_engine.backtest.engine import Config, Decision, Intent, Parameter, StrategySpec
from strategy_engine.research.features import (
    FEATURE_VERSION,
    CompletedSession,
    ema,
    opening_range,
    rsi,
    vwap,
)

Lookback = Annotated[int, Field(strict=True, ge=1, le=1440)]


class EmaParameters(Frozen):
    kind: Literal["EMA_CROSS"]
    quantity: Quantity
    fast: Lookback
    slow: Lookback

    @model_validator(mode="after")
    def valid(self) -> "EmaParameters":
        if self.fast >= self.slow:
            raise ValueError("FAST_MUST_BE_LESS_THAN_SLOW")
        return self


class VwapParameters(Frozen):
    kind: Literal["VWAP_CROSS"]
    quantity: Quantity


class OpeningRangeParameters(Frozen):
    kind: Literal["OPENING_RANGE"]
    quantity: Quantity
    opening_bars: Lookback


class RsiParameters(Frozen):
    kind: Literal["RSI_RECOVERY"]
    quantity: Quantity
    lookback: Lookback
    oversold: Money
    exit_threshold: Money

    @model_validator(mode="after")
    def valid(self) -> "RsiParameters":
        if not 0 < self.oversold < self.exit_threshold < 100:
            raise ValueError("INVALID_RSI_THRESHOLDS")
        return self


Parameters = Annotated[
    EmaParameters | VwapParameters | OpeningRangeParameters | RsiParameters,
    Field(discriminator="kind")
]


class BaselineSpec(Frozen):
    version: Literal["v1"]
    implementation_fingerprint: Hash
    parameters: Parameters

    def engine_spec(self) -> StrategySpec:
        from decimal import Decimal

        values = self.parameters.model_dump()
        values["feature_version"] = FEATURE_VERSION
        parameters = tuple(Parameter(name=k, value=decimal_text(v) if isinstance(v, Decimal)
                                     else str(v)) for k, v in sorted(values.items()))
        return StrategySpec(name=self.parameters.kind, version=self.version,
                            implementation_fingerprint=self.implementation_fingerprint,
                            parameters=parameters)


class BaselineStrategy(Frozen):
    specification: BaselineSpec
    config: Config

    def on_decision(self, context: Decision) -> Intent | None:
        if context.parameters != self.specification.engine_spec().parameters:
            raise ValueError("STRATEGY_PARAMETER_BINDING_MISMATCH")
        current = CompletedSession.from_decision(context)
        previous = current.previous()
        if previous is None:
            return None
        p = self.specification.parameters
        enter = exit_position = False
        if isinstance(p, EmaParameters):
            pf, ps = ema(previous, p.fast), ema(previous, p.slow)
            cf, cs = ema(current, p.fast), ema(current, p.slow)
            if pf is not None and ps is not None and cf is not None and cs is not None:
                enter = pf <= ps and cf > cs
                exit_position = pf >= ps and cf < cs
        elif isinstance(p, VwapParameters):
            before, now = vwap(previous), vwap(current)
            if before is not None and now is not None:
                enter = previous.bars[-1].close <= before and current.bars[-1].close > now
                exit_position = previous.bars[-1].close >= before and current.bars[-1].close < now
        elif isinstance(p, OpeningRangeParameters):
            bounds = opening_range(current, p.opening_bars)
            if bounds is not None and len(current.bars) > p.opening_bars:
                low, high = bounds
                enter = previous.bars[-1].close <= high < current.bars[-1].close
                exit_position = current.bars[-1].close < low
        else:
            before, now = rsi(previous, p.lookback), rsi(current, p.lookback)
            if before is not None and now is not None:
                enter = before <= p.oversold < now
                exit_position = before < p.exit_threshold <= now
        clock = context.decision_time.astimezone(NSE).time()
        if context.position.quantity:
            return (Intent(side="SELL", quantity=context.position.quantity, reason=p.kind)
                    if exit_position else None)
        if enter and self.config.entry_start <= clock <= self.config.last_entry_time:
            return Intent(side="BUY", quantity=p.quantity, reason=p.kind)
        return None
