"""Synthetic policy assumptions only; no keys, acquisition, or real clock evidence."""

from concurrent.futures import ThreadPoolExecutor
from threading import Lock
from typing import Any

import pytest
from pydantic import ValidationError

from strategy_engine.phase1444d.policy import (
    ArtifactFixture,
    CheckpointFixture,
    TimeFixture,
    deny_real_operation,
    inspect_artifact_fixture,
    inspect_checkpoint_fixture,
    inspect_time_fixture,
)

H = "a" * 64


def artifact(**changes: Any) -> ArtifactFixture:
    values: dict[str, Any] = dict(
        provider_present=True,
        dependency_approved=True,
        filename="TEST_ONLY-0-cp312-cp312-win_amd64.whl",
        expected_filename="TEST_ONLY-0-cp312-cp312-win_amd64.whl",
        tags=("cp312", "cp312", "win_amd64"),
        expected_tags=("cp312", "cp312", "win_amd64"),
        archive_hash=H,
        independent_hash=H,
    )
    return ArtifactFixture.model_validate(values | changes)


def time_fixture(**changes: Any) -> TimeFixture:
    values: dict[str, Any] = dict(
        authority="TEST_ONLY_TIME",
        enrolled_anchor=H,
        signed_evidence=H,
        signature_valid_fixture=True,
        revoked=False,
        issued_utc="2030-01-01T00:00:00.000000Z",
        valid_until_utc="2030-01-01T00:05:00.000000Z",
        now_utc="2030-01-01T00:00:10.000000Z",
        approval_utc="2030-01-01T00:00:00.000000Z",
        expiry_utc="2030-01-08T00:00:00.000000Z",
        uncertainty_ms=100,
        monotonic_origin_ms=1000,
        monotonic_now_ms=11000,
        process_identity="TEST_ONLY_PROCESS",
        expected_process_identity="TEST_ONLY_PROCESS",
        boot_identity="TEST_ONLY_BOOT",
        expected_boot_identity="TEST_ONLY_BOOT",
        snapshot_identity="TEST_ONLY_SNAPSHOT",
        expected_snapshot_identity="TEST_ONLY_SNAPSHOT",
    )
    return TimeFixture.model_validate(values | changes)


def checkpoint(**changes: Any) -> CheckpointFixture:
    values: dict[str, Any] = dict(
        ledger_identity="TEST_ONLY_LEDGER",
        expected_identity="TEST_ONLY_LEDGER",
        sequence=5,
        checkpoint_sequence=5,
        head=H,
        checkpoint_head=H,
        independent_checkpoint=H,
        checkpoint_authenticated_fixture=True,
        checkpoint_fresh_fixture=True,
        ledger_integrity=True,
        expected_reservation_generation=4,
        current_reservation_generation=4,
        revocation_generation=2,
        checkpoint_revocation_generation=2,
    )
    return CheckpointFixture.model_validate(values | changes)


def test_valid_fixture_is_not_authorization() -> None:
    assert inspect_artifact_fixture(artifact()) == "TEST_ONLY_POLICY_MATCH"
    assert inspect_time_fixture(time_fixture()) == "TEST_ONLY_TIME_INTERVAL"
    assert inspect_checkpoint_fixture(checkpoint()) == "TEST_ONLY_CHECKPOINT_MATCH"
    with pytest.raises(ValueError, match="NOT_AUTHORIZED"):
        deny_real_operation("adoption")


@pytest.mark.parametrize(
    ("change", "error"),
    [
        ({"provider_present": False}, "CRYPTO_PROVIDER_NOT_AUTHORIZED"),
        ({"dependency_approved": False}, "DEPENDENCY_NOT_AUTHORIZED"),
        ({"archive_hash": None}, "ARCHIVE_HASH_MISSING"),
        ({"filename": "other.whl"}, "WHEEL_IDENTITY"),
        ({"tags": ("cp311", "cp311", "win_amd64")}, "WHEEL_TAGS"),
        ({"independent_hash": None}, "INDEPENDENT_DIGEST_MISSING"),
        ({"independent_hash": "b" * 64}, "DIGEST_MISMATCH"),
    ],
)
def test_artifact_rejection(change: dict[str, Any], error: str) -> None:
    with pytest.raises(ValueError, match=error):
        inspect_artifact_fixture(artifact(**change))


@pytest.mark.parametrize(
    ("change", "error"),
    [
        ({"enrolled_anchor": None}, "UNKNOWN_TRUST_ROOT"),
        ({"signed_evidence": None}, "MISSING_SIGNED_TIME"),
        ({"signature_valid_fixture": False}, "INVALID_SIGNED_TIME"),
        ({"valid_until_utc": "2030-01-01T00:00:09.000000Z"}, "TIME_EVIDENCE_EXPIRED"),
        ({"issued_utc": "2030-01-01T00:00:11.000000Z"}, "FUTURE_TIME_EVIDENCE"),
        ({"now_utc": "2030-01-01T00:00:09.000000Z"}, "CLOCK_ROLLBACK"),
        ({"now_utc": "2030-01-01T00:00:11.000000Z"}, "CLOCK_FORWARD_JUMP"),
        ({"process_identity": "NEW"}, "PROCESS_RESTART"),
        ({"boot_identity": "NEW"}, "SYSTEM_REBOOT"),
        ({"snapshot_identity": "NEW"}, "SNAPSHOT_RESTORE"),
        ({"now_utc": "2030-01-01T00:06:00.000000Z"}, "STALE_TIME"),
        (
            {
                "approval_utc": "2029-12-25T00:00:10.000000Z",
                "expiry_utc": "2030-01-01T00:00:10.000000Z",
            },
            "APPROVAL_EXPIRED_OR_FUTURE",
        ),
        ({"revoked": True}, "REVOKED_AUTHORITY"),
        ({"uncertainty_ms": 1001}, "TIME_UNCERTAINTY"),
        ({"monotonic_now_ms": 0}, "CLOCK_ROLLBACK"),
        ({"expiry_utc": "2030-01-09T00:00:00.000000Z"}, "WRONG_APPROVAL_VALIDITY"),
    ],
)
def test_time_rejection(change: dict[str, Any], error: str) -> None:
    with pytest.raises(ValueError, match=error):
        inspect_time_fixture(time_fixture(**change))


@pytest.mark.parametrize(
    ("change", "error"),
    [
        ({"sequence": 4}, "LEDGER_ROLLBACK_OR_UNANCHORED"),
        ({"head": "b" * 64}, "AUDIT_CHAIN_REPLACED"),
        ({"independent_checkpoint": None}, "EXTERNAL_CHECKPOINT_MISSING"),
        ({"current_reservation_generation": 5}, "CONCURRENT_OR_REPLAYED_RESERVATION"),
        ({"ledger_integrity": False}, "LEDGER_CORRUPTED"),
        ({"checkpoint_authenticated_fixture": False}, "CHECKPOINT_NOT_AUTHENTICATED"),
        ({"checkpoint_fresh_fixture": False}, "CHECKPOINT_STALE"),
        ({"revocation_generation": 1}, "REVOCATION_ROLLBACK"),
        ({"ledger_identity": "REPLACEMENT"}, "LEDGER_REPLACED"),
    ],
)
def test_checkpoint_rejection(change: dict[str, Any], error: str) -> None:
    with pytest.raises(ValueError, match=error):
        inspect_checkpoint_fixture(checkpoint(**change))


def test_concurrent_fixture_cas_is_not_durable_protection() -> None:
    lock = Lock()
    generation = 4

    def consume() -> str:
        nonlocal generation
        with lock:
            try:
                inspect_checkpoint_fixture(checkpoint(current_reservation_generation=generation))
            except ValueError:
                return "REJECTED"
            generation += 1
            return "TEST_ONLY_RESERVED"

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: consume(), range(2)))
    assert sorted(results) == ["REJECTED", "TEST_ONLY_RESERVED"]


@pytest.mark.parametrize(
    "operation",
    [
        "metadata_request",
        "archive_download",
        "installation",
        "key_rotation",
        "amendment_adoption",
        "Series_v2_implementation",
        "owner_enrollment",
        "Kite_request",
    ],
)
def test_real_operations_denied(operation: str) -> None:
    with pytest.raises(ValueError, match="NOT_AUTHORIZED"):
        deny_real_operation(operation)


def test_no_fixture_promotion_or_unknown_fields() -> None:
    with pytest.raises(ValidationError):
        time_fixture(source="REAL")
    with pytest.raises(ValidationError):
        checkpoint(executable_permission=True)
