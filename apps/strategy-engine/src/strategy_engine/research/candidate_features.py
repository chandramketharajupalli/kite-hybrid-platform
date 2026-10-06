"""Frozen H1 G1 feature: TRADABLE_AT_TIME_T, no full-session diagnostic inputs."""

from decimal import Decimal, localcontext

from strategy_engine.research.features import PRECISION, CompletedSession


def signed_efficiency_30(view: CompletedSession) -> Decimal | None:
    view = CompletedSession.model_validate(view.model_dump())
    if len(view.bars) < 31:
        return None
    closes = tuple(b.close for b in view.bars[-31:])
    with localcontext(PRECISION):
        path = sum((abs(b - a) for a, b in zip(closes, closes[1:], strict=False)), Decimal(0))
        return (closes[-1] - closes[0]) / path if path else None
