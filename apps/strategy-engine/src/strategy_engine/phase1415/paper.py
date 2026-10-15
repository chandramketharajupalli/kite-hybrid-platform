"""Offline event contract, never an order transport."""

from datetime import datetime
from decimal import Decimal, localcontext
from typing import Literal

from pydantic import model_validator

from strategy_engine.backtest.dataset import Frozen, Hash, Instant, digest
from strategy_engine.phase14.engine import FIVE, PRECISION, Candle
from strategy_engine.phase1415.strategies import Definition


class PaperEvent(Frozen):
    mode: Literal["BACKTEST_ONLY"] = "BACKTEST_ONLY"
    strategy_id: str
    strategy_version: str
    strategy_hash: Hash
    symbol: str
    signal_at: Instant
    candle_hash: Hash
    direction: Literal["LONG"] = "LONG"
    reference: Decimal
    stop: Decimal
    target: Decimal
    expiry: Instant
    idempotency_key: Hash
    rejection: str | None

    @model_validator(mode="after")
    def valid(self) -> "PaperEvent":
        if not 0 < self.stop < self.reference < self.target or self.expiry <= self.signal_at:
            raise ValueError("INVALID_PAPER_EVENT")
        return self


def event(symbol: str, definition: Definition, candle: Candle, now: datetime) -> PaperEvent:
    if candle.end > now:
        raise ValueError("INCOMPLETE_SIGNAL_CANDLE")
    with localcontext(PRECISION):
        return PaperEvent(
            strategy_id=definition.identifier,
            strategy_version=definition.version,
            strategy_hash=definition.fingerprint,
            symbol=symbol,
            signal_at=candle.end,
            candle_hash=digest(candle.model_dump_json()),
            reference=candle.close,
            stop=candle.close * (1 - definition.stop),
            target=candle.close * (1 + definition.target),
            expiry=candle.end + FIVE,
            idempotency_key=digest(symbol + definition.fingerprint + candle.model_dump_json()),
            rejection="EXPIRED" if now >= candle.end + FIVE else "OFFLINE_ONLY_NO_TRANSPORT",
        )
