"""Additive Phase 12 features; retained Phase 11 implementations remain frozen."""
from datetime import date
from decimal import Decimal, localcontext
from typing import Any

from pydantic import model_validator

from strategy_engine.backtest.dataset import NSE, Frozen
from strategy_engine.research.continuity import Boundary
from strategy_engine.research.features import PRECISION, CompletedSession
from strategy_engine.research.opening_features import early_volume


class ContinuityContext(Frozen):
    instrument_id: str
    first: date
    end_exclusive: date
    boundaries: tuple[Boundary, ...]

    @model_validator(mode="after")
    def valid(self) -> "ContinuityContext":
        if self.first >= self.end_exclusive or any(
            b.instrument_id != self.instrument_id or not self.first <= b.date < self.end_exclusive
            for b in self.boundaries
        ):
            raise ValueError("CONTINUITY_SCOPE_MISMATCH")
        if self.boundaries != tuple(sorted(self.boundaries, key=lambda b: (b.date, b.action_id))):
            raise ValueError("BOUNDARIES_NOT_CANONICAL")
        if len({b.action_id for b in self.boundaries}) != len(self.boundaries):
            raise ValueError("DUPLICATE_BOUNDARY")
        return self

    def validate_session(self, instrument_id: str, view: CompletedSession) -> date:
        ContinuityContext.model_validate(self.model_dump())
        CompletedSession.model_validate(view.model_dump())
        day = view.opened_at.astimezone(NSE).date()
        if instrument_id != self.instrument_id or not self.first <= day < self.end_exclusive:
            raise ValueError("CONTINUITY_SCOPE_MISMATCH")
        return day

    def crosses(self, previous: date, current: date, *, volume: bool = False) -> bool:
        if not self.first <= previous < current < self.end_exclusive:
            raise ValueError("CONTINUITY_SCOPE_MISMATCH")
        return any(previous < b.date <= current and (
            not b.volume_continuity if volume else not b.price_continuity)
                   for b in self.boundaries)

    def segments(self) -> tuple[tuple[date, date], ...]:
        points = sorted({self.first, self.end_exclusive} | {
            b.date for b in self.boundaries if b.segment_reset})
        return tuple(zip(points, points[1:], strict=False))


def opening_gap(instrument_id: str, current: CompletedSession,
                previous: CompletedSession | None, continuity: ContinuityContext) -> dict[str, Any]:
    day = continuity.validate_session(instrument_id, current)
    if previous is None:
        return {"status": "NO_PREVIOUS_SESSION", "value": None}
    prior = continuity.validate_session(instrument_id, previous)
    if previous.decision_at != previous.closes_at or previous.closes_at >= current.opened_at:
        raise ValueError("FUTURE_OR_INCOMPLETE_PREVIOUS_SESSION")
    if continuity.crosses(prior, day):
        return {"status": "INVALID_BOUNDARY", "value": None}
    with localcontext(PRECISION):
        return {"status": "AVAILABLE", "value": current.bars[0].open - previous.bars[-1].close}


def historical_early_volume(instrument_id: str, current: CompletedSession,
                            history: tuple[CompletedSession, ...],
                            continuity: ContinuityContext) -> dict[str, Any] | None:
    day = continuity.validate_session(instrument_id, current)
    # Validate the entire input before filtering; future evidence must never disappear silently.
    for previous in history:
        continuity.validate_session(instrument_id, previous)
    early_volume(current, history)
    eligible = tuple(h for h in history if not continuity.crosses(
        h.opened_at.astimezone(NSE).date(), day, volume=True))
    return early_volume(current, eligible)


def previous_close(instrument_id: str, current: CompletedSession,
                   previous: CompletedSession | None,
                   continuity: ContinuityContext) -> Decimal | None:
    observation = opening_gap(instrument_id, current, previous, continuity)
    return previous.bars[-1].close if previous is not None and (
        observation["status"] == "AVAILABLE") else None
