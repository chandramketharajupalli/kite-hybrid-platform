"""In-memory fixtures only; no native imports."""

from dataclasses import replace

import pytest
from strategy_engine.phase1444ej import enforcement as e

CASES = (
    "owner",
    "role",
    "root",
    "signature",
    "payload",
    "baseline",
    "integration",
    "policy",
    "supervisor",
    "helper",
    "command",
    "missing_test",
    "duplicate_test",
    "changed_tests",
    "limits",
    "process_limit",
    "cpu_limit",
    "memory_limit",
    "output_limit",
    "cleanup",
    "trusted_time",
    "expired",
    "future",
    "clock_rollback",
    "revoked",
    "duplicate_decision",
    "duplicate_nonce",
    "concurrent",
    "crash_before_reserve",
    "crash_after_reserve",
    "crash_before_consume",
    "crash_after_consume",
    "ledger_rollback",
    "checkpoint",
    "invalid_transition",
    "promotion",
    "native",
    "live",
    "database",
    "series",
    "kite",
)


@pytest.mark.parametrize("case", CASES)
def test_security(case: str) -> None:
    expected = e.Decision()
    decision = expected
    ledger = e.Ledger()
    now, previous = 15, 15
    usage: tuple[int, ...] = (1, 1, 1, 1)
    if case in (
        "owner",
        "role",
        "root",
        "payload",
        "baseline",
        "integration",
        "policy",
        "supervisor",
        "helper",
        "command",
        "cleanup",
    ):
        decision = replace(decision, **{case: "WRONG"})
    elif case == "signature":
        decision = replace(decision, signature_valid=False)
    elif case in ("missing_test", "duplicate_test", "changed_tests"):
        tests = {
            "missing_test": ("TEST_ONLY_A",),
            "duplicate_test": ("TEST_ONLY_A", "TEST_ONLY_A"),
            "changed_tests": ("TEST_ONLY_B", "TEST_ONLY_A"),
        }[case]
        decision = replace(decision, tests=tests)
    elif case == "limits":
        decision = replace(decision, limits=())
    elif case.endswith("_limit"):
        index = ("process_limit", "cpu_limit", "memory_limit", "output_limit").index(case)
        values = [1, 1, 1, 1]
        values[index] = decision.limits[index] + 1
        usage = tuple(values)
    elif case == "trusted_time":
        decision = replace(decision, trusted_time=False)
    elif case == "expired":
        now, previous = 20, 20
    elif case == "future":
        now, previous = 9, 9
    elif case == "clock_rollback":
        previous = 16
    elif case == "revoked":
        decision = replace(decision, revoked=True)
    elif case == "duplicate_decision":
        ledger = e.Ledger(decisions=frozenset({decision.identity}))
    elif case == "duplicate_nonce":
        ledger = e.Ledger(nonces=frozenset({decision.nonce}))
    elif case == "ledger_rollback":
        ledger = e.Ledger(version=1, checkpoint_version=2)
    elif case == "checkpoint":
        decision = replace(decision, checkpoint=False)
    elif case == "promotion":
        decision = replace(decision, fixture=False)
    elif case in ("native", "live", "database", "series", "kite"):
        decision = replace(decision, operation=case)
    lease = e.verify_fixture(
        decision, expected, ledger, now=now, previous_time=previous, usage=usage
    )
    if case == "concurrent":
        first, updated = e.reserve(lease, ledger, 0)
        second, unchanged = e.reserve(lease, updated, 0)
        assert first.state == "RESERVED" and second.state == "REJECTED"
        assert unchanged == updated
    elif case.startswith("crash_"):
        if case == "crash_before_reserve":
            aborted = e.crash(lease)
            rejected, unchanged = e.reserve(aborted, ledger, 0)
            assert rejected.state == "REJECTED" and unchanged == ledger
        else:
            reserved, updated = e.reserve(lease, ledger, 0)
            if case == "crash_after_consume":
                reserved = e.transition(reserved, "CONSUMED", critical_revalidated=True)
            aborted = e.crash(reserved)
            assert aborted.state == "ABORTED"
            assert e.verify_fixture(decision, expected, updated).state == "REJECTED"
    elif case == "invalid_transition":
        with pytest.raises(ValueError):
            e.transition(lease, "CONSUMED", critical_revalidated=True)
    else:
        assert lease.state in ("REJECTED", "EXPIRED", "REVOKED")
    assert not lease.execution_authorized
    assert e.authorize_real(lease) is False


def test_happy_fixture_and_consumption_barrier() -> None:
    d = e.Decision()
    lease = e.verify_fixture(d, d, e.Ledger())
    assert lease.state == "VERIFIED"
    reserved, ledger = e.reserve(lease, e.Ledger(), 0)
    with pytest.raises(ValueError):
        e.transition(reserved, "CONSUMED")
    consumed = e.transition(reserved, "CONSUMED", critical_revalidated=True)
    assert consumed.state == "CONSUMED" and not consumed.execution_authorized
    assert e.verify_fixture(d, d, ledger).state == "REJECTED"
    with pytest.raises(ValueError):
        e.transition(consumed, "VERIFIED")
