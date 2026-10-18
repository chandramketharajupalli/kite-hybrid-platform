"""Distinct-name pure boundary fixture; retained unexecuted."""

from strategy_engine.phase1444ej import enforcement


def test_no_real_lease_authority() -> None:
    assert enforcement.authorize_real(enforcement.Lease()) is False
