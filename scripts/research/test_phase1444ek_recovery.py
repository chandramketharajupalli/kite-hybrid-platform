"""Unique-name pure recovery fixture, retained unexecuted after collection stop."""

from strategy_engine.phase1444ej import enforcement


def test_real_authority_remains_unavailable() -> None:
    assert enforcement.authorize_real(enforcement.Lease()) is False
