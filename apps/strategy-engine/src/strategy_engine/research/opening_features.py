"""Raw opening observations from completed prefixes; no ex-post labels or outcomes."""

from decimal import Decimal, localcontext
from typing import Any

from strategy_engine.research.features import PRECISION, CompletedSession, opening_range


def opening_observation(view: CompletedSession, minutes: int) -> dict[str, Any] | None:
    """Raw completed-window observation; no diagnostic classification returned."""
    # Revalidate callers using unsafe model_copy/model_construct.
    view = CompletedSession.model_validate(view.model_dump())
    if minutes not in (5, 15, 30):
        raise ValueError("UNREGISTERED_OPENING_WINDOW")
    bounds = opening_range(view, minutes)
    if bounds is None:
        return None
    low, high = bounds
    with localcontext(PRECISION):
        return {
            "usage": "TRADABLE_AT_TIME_T",
            "available_at": view.bars[minutes - 1].end,
            "low": low,
            "high": high,
            "range": high - low,
            "range_percent": (high - low) / view.bars[0].open * 100,
        }


def early_volume(
    view: CompletedSession, history: tuple[CompletedSession, ...]
) -> dict[str, Any] | None:
    """Raw point-in-time first-15 volume, prior 20 confirmed sessions, minimum five."""
    view = CompletedSession.model_validate(view.model_dump())
    history = tuple(CompletedSession.model_validate(h.model_dump()) for h in history)
    if any(h.decision_at != h.closes_at or h.closes_at >= view.opened_at for h in history):
        raise ValueError("FUTURE_OR_INCOMPLETE_VOLUME_REFERENCE")
    if any(a.opened_at >= b.opened_at for a, b in zip(history, history[1:], strict=False)):
        raise ValueError("VOLUME_HISTORY_NOT_ORDERED")
    if len(view.bars) < 15:
        return None
    prior = history[-20:]
    if len(prior) < 5 or any(len(h.bars) < 15 for h in prior):
        return None
    with localcontext(PRECISION):
        reference = Decimal(sum(sum(b.volume for b in h.bars[:15]) for h in prior)) / len(prior)
        return {
            "usage": "TRADABLE_AT_TIME_T",
            "available_at": view.bars[14].end,
            "reference_sessions": len(prior),
            "reference_mean": reference,
            "value": (
                Decimal(sum(b.volume for b in view.bars[:15])) / reference if reference else None
            ),
        }
