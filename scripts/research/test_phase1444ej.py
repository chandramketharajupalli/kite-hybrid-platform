"""Pure public-boundary checks, without launching native processes."""

from dataclasses import FrozenInstanceError

import pytest
from strategy_engine.phase1444ej import enforcement as e


def test_no_authority_from_any_fixture_state() -> None:
    states: tuple[e.State, ...] = (
        "PENDING",
        "VERIFIED",
        "RESERVED",
        "CONSUMED",
        "EXPIRED",
        "REVOKED",
        "REJECTED",
        "ABORTED",
    )
    for state in states:
        lease = e.Lease(state)
        assert not lease.execution_authorized
        assert e.authorize_real(lease) is False


def test_fixture_immutable() -> None:
    lease = e.Lease()
    with pytest.raises(FrozenInstanceError):
        setattr(lease, "state", "CONSUMED")


def test_revocation_before_consumption() -> None:
    decision = e.Decision()
    lease = e.verify_fixture(decision, decision, e.Ledger())
    reserved, ledger = e.reserve(lease, e.Ledger(), 0)
    revoked = e.transition(reserved, "REVOKED")
    with pytest.raises(ValueError):
        e.transition(revoked, "CONSUMED", critical_revalidated=True)
    assert decision.identity in ledger.decisions


def test_clock_forward_jump() -> None:
    d = e.Decision()
    assert (
        e.verify_fixture(d, d, e.Ledger(), now=19, previous_time=15).state == "REJECTED"
    )
