"""Pure synthetic lease model. No real lease issuance or execution capability."""

from dataclasses import dataclass, replace
from typing import Literal

State = Literal[
    "PENDING", "VERIFIED", "RESERVED", "CONSUMED", "EXPIRED", "REVOKED", "REJECTED", "ABORTED"
]
BASELINE = "54b15bece6fee2b3be8a039011010cccfc93a4ee"


@dataclass(frozen=True)
class Decision:
    owner: str = "TEST_ONLY"
    role: str = "TEST_ONLY_QUALIFICATION"
    root: str = "TEST_ONLY"
    signature_valid: bool = True
    payload: str = "TEST_ONLY_PAYLOAD"
    baseline: str = BASELINE
    integration: str = "TEST_ONLY_INTEGRATION"
    policy: str = "TEST_ONLY_POLICY"
    supervisor: str = "TEST_ONLY_SUPERVISOR"
    helper: str = "TEST_ONLY_HELPER"
    command: str = "TEST_ONLY_COMMAND"
    tests: tuple[str, ...] = ("TEST_ONLY_A", "TEST_ONLY_B")
    limits: tuple[int, ...] = (4, 10, 256, 8)
    cleanup: str = "TEST_ONLY"
    trusted_time: bool = True
    not_before: int = 10
    expiry: int = 20
    revoked: bool = False
    identity: str = "TEST_ONLY_DECISION"
    nonce: str = "TEST_ONLY_NONCE"
    checkpoint: bool = True
    fixture: bool = True
    operation: str = "TEST_ONLY_QUALIFICATION"


@dataclass(frozen=True)
class Ledger:
    """Immutable CAS specification only; not a database or concurrency guarantee."""

    version: int = 0
    decisions: frozenset[str] = frozenset()
    nonces: frozenset[str] = frozenset()
    checkpoint_version: int = 0


@dataclass(frozen=True)
class Lease:
    state: State = "PENDING"
    identity: str = ""
    nonce: str = ""
    fixture: bool = True

    @property
    def execution_authorized(self) -> Literal[False]:
        return False


def authorize_real(decision: object) -> Literal[False]:
    """Owner trust, time and replay authority are unavailable, regardless of input."""
    return False


def verify_fixture(
    decision: Decision,
    expected: Decision,
    ledger: Ledger,
    *,
    now: int = 15,
    previous_time: int = 15,
    usage: tuple[int, ...] = (1, 1, 1, 1),
) -> Lease:
    if not decision.fixture or not expected.fixture:
        return Lease("REJECTED")
    scope = (
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
        "tests",
        "limits",
        "cleanup",
        "operation",
    )
    if any(getattr(decision, name) != getattr(expected, name) for name in scope):
        return Lease("REJECTED")
    if decision.owner != "TEST_ONLY" or decision.root != "TEST_ONLY":
        return Lease("REJECTED")
    if not decision.signature_valid or not decision.trusted_time or not decision.checkpoint:
        return Lease("REJECTED")
    if decision.revoked:
        return Lease("REVOKED")
    if now >= decision.expiry:
        return Lease("EXPIRED")
    if now < decision.not_before or now < previous_time or now - previous_time > 2:
        return Lease("REJECTED")
    if decision.expiry <= decision.not_before:
        return Lease("REJECTED")
    if not decision.identity or not decision.nonce or not decision.cleanup:
        return Lease("REJECTED")
    if not decision.tests or len(set(decision.tests)) != len(decision.tests):
        return Lease("REJECTED")
    if len(decision.limits) != 4 or len(usage) != 4:
        return Lease("REJECTED")
    if any(type(limit) is not int or limit <= 0 for limit in decision.limits):
        return Lease("REJECTED")
    if any(
        type(actual) is not int or not 0 <= actual <= limit
        for actual, limit in zip(usage, decision.limits, strict=True)
    ):
        return Lease("REJECTED")
    if ledger.version < ledger.checkpoint_version:
        return Lease("REJECTED")
    if decision.identity in ledger.decisions or decision.nonce in ledger.nonces:
        return Lease("REJECTED")
    return Lease("VERIFIED", decision.identity, decision.nonce)


def reserve(lease: Lease, ledger: Ledger, expected_version: int) -> tuple[Lease, Ledger]:
    if (
        not lease.fixture
        or lease.state != "VERIFIED"
        or not lease.identity
        or not lease.nonce
        or ledger.version != expected_version
        or ledger.version < ledger.checkpoint_version
        or lease.identity in ledger.decisions
        or lease.nonce in ledger.nonces
    ):
        return replace(lease, state="REJECTED"), ledger
    updated = Ledger(
        ledger.version + 1,
        ledger.decisions | {lease.identity},
        ledger.nonces | {lease.nonce},
        ledger.checkpoint_version,
    )
    return replace(lease, state="RESERVED"), updated


def transition(lease: Lease, target: State, *, critical_revalidated: bool = False) -> Lease:
    allowed: dict[State, frozenset[State]] = {
        "PENDING": frozenset({"REJECTED", "EXPIRED", "REVOKED", "ABORTED"}),
        "VERIFIED": frozenset({"REJECTED", "EXPIRED", "REVOKED", "ABORTED"}),
        "RESERVED": frozenset({"CONSUMED", "EXPIRED", "REVOKED", "ABORTED"}),
        "CONSUMED": frozenset({"ABORTED", "REVOKED"}),
        "EXPIRED": frozenset(),
        "REVOKED": frozenset(),
        "REJECTED": frozenset(),
        "ABORTED": frozenset(),
    }
    if not lease.fixture or target not in allowed[lease.state]:
        raise ValueError("INVALID_TRANSITION")
    if target == "CONSUMED" and not critical_revalidated:
        raise ValueError("CRITICAL_REVALIDATION_REQUIRED")
    return replace(lease, state=target)


def crash(lease: Lease) -> Lease:
    """No ledger deletion or identity restoration. Consumed is permanently spent."""
    if lease.state in ("EXPIRED", "REVOKED", "REJECTED", "ABORTED"):
        return lease
    return replace(lease, state="ABORTED")
