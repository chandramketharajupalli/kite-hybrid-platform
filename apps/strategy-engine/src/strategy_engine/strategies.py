"""Strategy and output ports; no broker or authoritative ledger dependency."""
from typing import Protocol

from strategy_engine.domain import SignalEvent


class SignalSink(Protocol):
    def publish(self, event: SignalEvent) -> None:
        """Deliver to the Java control boundary using a future transport."""
        ...


class Strategy(Protocol):
    def evaluate(self) -> tuple[SignalEvent, ...]:
        """Produce proposals only; no execution side effects."""
        ...
