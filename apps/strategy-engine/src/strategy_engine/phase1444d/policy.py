"""Strict synthetic policy models; no cryptographic verifier or action executor."""

from datetime import timedelta
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field

from strategy_engine.phase1444b.trust import instant, require

BASELINE = "bf48f9622609474e9530063af580bf70c7ace39a"
Hash = Annotated[str, Field(pattern=r"^[0-9a-f]{64}$")]
Nonnegative = Annotated[int, Field(ge=0)]


class Fixture(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)
    source: Literal["TEST_ONLY"] = "TEST_ONLY"


class ArtifactFixture(Fixture):
    """Caller supplies fictional expected identity; this cannot approve acquisition."""

    provider_present: bool
    dependency_approved: bool
    filename: str
    expected_filename: str
    tags: tuple[str, str, str]
    expected_tags: tuple[str, str, str]
    archive_hash: Hash | None
    independent_hash: Hash | None


def inspect_artifact_fixture(item: ArtifactFixture) -> Literal["TEST_ONLY_POLICY_MATCH"]:
    ArtifactFixture.model_validate(item.model_dump())
    require(item.provider_present, "CRYPTO_PROVIDER_NOT_AUTHORIZED")
    require(item.dependency_approved, "DEPENDENCY_NOT_AUTHORIZED")
    require(item.archive_hash is not None, "ARCHIVE_HASH_MISSING")
    require(item.filename == item.expected_filename, "WHEEL_IDENTITY")
    require(item.tags == item.expected_tags, "WHEEL_TAGS")
    require(item.independent_hash is not None, "INDEPENDENT_DIGEST_MISSING")
    require(item.archive_hash == item.independent_hash, "DIGEST_MISMATCH")
    return "TEST_ONLY_POLICY_MATCH"


class TimeFixture(Fixture):
    """Signature validity is a TEST_ONLY assumption, never real verification evidence."""

    authority: str | None
    enrolled_anchor: Hash | None
    signed_evidence: Hash | None
    signature_valid_fixture: bool
    revoked: bool
    issued_utc: str
    valid_until_utc: str
    now_utc: str
    approval_utc: str
    expiry_utc: str
    uncertainty_ms: Nonnegative
    monotonic_origin_ms: Nonnegative
    monotonic_now_ms: Nonnegative
    process_identity: str
    expected_process_identity: str
    boot_identity: str
    expected_boot_identity: str
    snapshot_identity: str
    expected_snapshot_identity: str


def inspect_time_fixture(item: TimeFixture) -> Literal["TEST_ONLY_TIME_INTERVAL"]:
    TimeFixture.model_validate(item.model_dump())
    require(bool(item.authority) and item.enrolled_anchor is not None, "UNKNOWN_TRUST_ROOT")
    require(item.signed_evidence is not None, "MISSING_SIGNED_TIME")
    require(item.signature_valid_fixture, "INVALID_SIGNED_TIME")
    require(not item.revoked, "REVOKED_AUTHORITY")
    require(item.process_identity == item.expected_process_identity, "PROCESS_RESTART")
    require(item.boot_identity == item.expected_boot_identity, "SYSTEM_REBOOT")
    require(item.snapshot_identity == item.expected_snapshot_identity, "SNAPSHOT_RESTORE")
    issued, until, now, approval, expiry = (
        instant(s)
        for s in (
            item.issued_utc,
            item.valid_until_utc,
            item.now_utc,
            item.approval_utc,
            item.expiry_utc,
        )
    )
    require(expiry - approval == timedelta(hours=168), "WRONG_APPROVAL_VALIDITY")
    require(issued <= now, "FUTURE_TIME_EVIDENCE")
    require(issued < until, "INVALID_TIME_WINDOW")
    require(item.uncertainty_ms <= 1000, "TIME_UNCERTAINTY")
    elapsed = item.monotonic_now_ms - item.monotonic_origin_ms
    require(elapsed >= 0, "CLOCK_ROLLBACK")
    require(elapsed <= 300_000 and now - issued <= timedelta(seconds=300), "STALE_TIME")
    estimated = issued + timedelta(milliseconds=elapsed)
    error = timedelta(milliseconds=item.uncertainty_ms)
    require(now >= estimated - error, "CLOCK_ROLLBACK")
    require(now <= estimated + error, "CLOCK_FORWARD_JUMP")
    # Entire bounded interval must remain inside both evidence and approval lifetimes.
    lower, upper = estimated - error, estimated + error
    require(issued <= lower and upper < until, "TIME_EVIDENCE_EXPIRED")
    require(approval <= lower and upper < expiry, "APPROVAL_EXPIRED_OR_FUTURE")
    return "TEST_ONLY_TIME_INTERVAL"


class CheckpointFixture(Fixture):
    ledger_identity: str
    expected_identity: str
    sequence: Nonnegative
    checkpoint_sequence: Nonnegative
    head: Hash
    checkpoint_head: Hash
    independent_checkpoint: Hash | None
    checkpoint_authenticated_fixture: bool
    checkpoint_fresh_fixture: bool
    ledger_integrity: bool
    expected_reservation_generation: Nonnegative
    current_reservation_generation: Nonnegative
    revocation_generation: Nonnegative
    checkpoint_revocation_generation: Nonnegative


def inspect_checkpoint_fixture(
    item: CheckpointFixture,
) -> Literal["TEST_ONLY_CHECKPOINT_MATCH"]:
    CheckpointFixture.model_validate(item.model_dump())
    require(item.independent_checkpoint is not None, "EXTERNAL_CHECKPOINT_MISSING")
    require(item.checkpoint_authenticated_fixture, "CHECKPOINT_NOT_AUTHENTICATED")
    require(item.checkpoint_fresh_fixture, "CHECKPOINT_STALE")
    require(item.ledger_integrity, "LEDGER_CORRUPTED")
    require(item.ledger_identity == item.expected_identity, "LEDGER_REPLACED")
    require(item.sequence == item.checkpoint_sequence, "LEDGER_ROLLBACK_OR_UNANCHORED")
    require(item.head == item.checkpoint_head, "AUDIT_CHAIN_REPLACED")
    require(
        item.revocation_generation == item.checkpoint_revocation_generation,
        "REVOCATION_ROLLBACK",
    )
    require(
        item.expected_reservation_generation == item.current_reservation_generation,
        "CONCURRENT_OR_REPLAYED_RESERVATION",
    )
    return "TEST_ONLY_CHECKPOINT_MATCH"


def deny_real_operation(operation: str) -> None:
    """No input packet, fixture flags or successful inspection grants actual authority."""
    raise ValueError("NOT_AUTHORIZED:" + operation)
