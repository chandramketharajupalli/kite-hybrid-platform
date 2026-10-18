"""Disposable HMAC fixtures only; no real keys, Ed25519 or owner authentication."""

import json
import secrets
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime, timedelta
from typing import Any

import pytest

from strategy_engine.phase1444b import trust as t

H = "a" * 64
J = "b" * 64


def fixture() -> tuple[t.SignedDecision, bytes, t.FixtureAuthority, t.FixtureClock]:
    key = secrets.token_bytes(32)
    decision = t.SignedDecision(
        domain=t.TEST_DOMAIN,
        source="SYNTHETIC_TEST",
        decision_type=t.DECISION,
        owner_identity_reference="SYNTHETIC_OWNER",
        authority_role="EVIDENCE_ADOPTION_OWNER",
        trust_root_fingerprint=H,
        signing_key_fingerprint=J,
        source_git_baseline=t.BASELINE,
        amendment_raw_sha256=t.AMENDMENT_RAW,
        amendment_canonical_fingerprint=t.AMENDMENT_CANONICAL,
        fresh_certificate_sha256=t.CERTIFICATE,
        decision_scope=t.SCOPE,
        approval_timestamp="2026-01-01T00:00:00.000000Z",
        expiry_timestamp="2026-01-08T00:00:00.000000Z",
        decision_id="SYNTHETIC_DECISION_1",
        replay_nonce="c" * 64,
        revocation_reference=H,
        trusted_time_reference=H,
        governance_fingerprint=H,
        rollback_separation="SEPARATE_AUTHORIZATION_REQUIRED",
        signature_algorithm="HMAC_SHA256_SYNTHETIC_ONLY",
        canonical_payload_fingerprint=H,
        signature_bytes=H,
    )
    authority = t.FixtureAuthority(
        owner="SYNTHETIC_OWNER",
        role="EVIDENCE_ADOPTION_OWNER",
        root=H,
        key_fingerprint=J,
        governance=H,
        revocation_reference=H,
        trusted_time_reference=H,
        effective_from="2025-12-01T00:00:00.000000Z",
        expires_at="2026-02-01T00:00:00.000000Z",
    )
    clock = t.FixtureClock(
        now=datetime(2026, 1, 2, tzinfo=UTC),
        prior_checkpoint=datetime(2026, 1, 1, tzinfo=UTC),
        evidence=H,
    )
    return t.sign_fixture(decision, key), key, authority, clock


def verify(
    decision: t.SignedDecision,
    key: bytes,
    authority: t.FixtureAuthority,
    clock: t.FixtureClock,
    ledger: t.VolatileFixtureLedger | None = None,
) -> str:
    return t.verify_fixture(
        t.canonical(decision.model_dump()),
        key,
        authority,
        clock,
        ledger if ledger is not None else t.VolatileFixtureLedger(),
    )


def test_valid_disposable_signature_is_synthetic_only() -> None:
    d, key, a, clock = fixture()
    assert verify(d, key, a, clock) == "SYNTHETIC_POLICY_PASS_REAL_AUTHORITY_NOT_ESTABLISHED"
    assert t.sign_fixture(d, key).signature_bytes == d.signature_bytes
    assert t.assessment()["owner_trust"]["trust_status"] == "TRUST_NOT_ESTABLISHED"


def test_modified_payload_rejects_even_with_recomputed_hash() -> None:
    d, key, a, clock = fixture()
    d = d.model_copy(update={"decision_id": "SYNTHETIC_CHANGED"})
    d = d.model_copy(update={"canonical_payload_fingerprint": t.sha(t.payload(d))})
    with pytest.raises(ValueError, match="INVALID_FIXTURE_SIGNATURE"):
        verify(d, key, a, clock)


def test_wrong_verification_key() -> None:
    d, _, a, clock = fixture()
    with pytest.raises(ValueError, match="INVALID_FIXTURE_SIGNATURE"):
        verify(d, secrets.token_bytes(32), a, clock)


@pytest.mark.parametrize(
    ("changes", "reason"),
    [
        ({"trust_root_fingerprint": J}, "UNKNOWN_TRUST_ROOT"),
        ({"authority_role": "OPERATOR"}, "WRONG_ROLE"),
        ({"decision_type": "IMPLEMENT_STAGE1"}, "UNAUTHORIZED_DECISION_TYPE"),
        ({"source_git_baseline": "0" * 40}, "BASELINE_BINDING"),
        ({"amendment_raw_sha256": H}, "AMENDMENT_RAW_BINDING"),
        ({"amendment_canonical_fingerprint": H}, "AMENDMENT_CANONICAL_BINDING"),
        ({"fresh_certificate_sha256": H}, "CERTIFICATE_BINDING"),
        ({"signing_key_fingerprint": H}, "UNKNOWN_KEY"),
        ({"owner_identity_reference": "OTHER_OWNER"}, "OWNER_MISMATCH"),
        ({"decision_scope": "ADOPT_NOW"}, "UNAUTHORIZED_SCOPE"),
        ({"governance_fingerprint": J}, "GOVERNANCE_BINDING"),
        ({"revocation_reference": J}, "REVOCATION_BINDING"),
        ({"expiry_timestamp": "2026-01-09T00:00:00.000000Z"}, "INVALID_VALIDITY"),
    ],
)
def test_signed_but_unauthorized_binding(changes: dict[str, Any], reason: str) -> None:
    d, key, a, clock = fixture()
    d = t.sign_fixture(d.model_copy(update=changes), key)
    with pytest.raises(ValueError, match=reason):
        verify(d, key, a, clock)


@pytest.mark.parametrize(
    ("changes", "reason"),
    [
        ({"revoked_key": True}, "KEY_REVOKED"),
        ({"revoked_owner": True}, "OWNER_REVOKED"),
        ({"revoked_decisions": frozenset({"SYNTHETIC_DECISION_1"})}, "DECISION_REVOKED"),
        ({"conflicting": True}, "CONFLICTING_DECISION"),
        ({"key_available": False}, "LOST_KEY"),
        ({"expires_at": "2026-01-07T00:00:00.000000Z"}, "AUTHORITY_VALIDITY"),
    ],
)
def test_authority_rejections(changes: dict[str, Any], reason: str) -> None:
    d, key, a, clock = fixture()
    with pytest.raises(ValueError, match=reason):
        verify(d, key, a.model_copy(update=changes), clock)


@pytest.mark.parametrize(
    ("now", "prior", "evidence", "reason"),
    [
        (
            datetime(2026, 1, 8, tzinfo=UTC),
            datetime(2026, 1, 1, tzinfo=UTC),
            H,
            "EXPIRED_OR_FUTURE",
        ),
        (
            datetime(2025, 12, 31, tzinfo=UTC),
            datetime(2025, 12, 30, tzinfo=UTC),
            H,
            "EXPIRED_OR_FUTURE",
        ),
        (datetime(2026, 1, 2, tzinfo=UTC), datetime(2026, 1, 3, tzinfo=UTC), H, "CLOCK_ROLLBACK"),
        (
            datetime(2026, 1, 2, tzinfo=UTC),
            datetime(2026, 1, 1, tzinfo=UTC),
            None,
            "TRUSTED_TIME_UNVERIFIED",
        ),
    ],
)
def test_time_rejections(now: datetime, prior: datetime, evidence: str | None, reason: str) -> None:
    d, key, a, _ = fixture()
    clock = t.FixtureClock(now=now, prior_checkpoint=prior, evidence=evidence)
    with pytest.raises(ValueError, match=reason):
        verify(d, key, a, clock)


def test_uncertainty_window_does_not_straddle_expiry() -> None:
    d, key, a, clock = fixture()
    clock = clock.model_copy(update={"now": t.instant(d.expiry_timestamp) - timedelta(seconds=1)})
    with pytest.raises(ValueError, match="EXPIRED_OR_FUTURE"):
        verify(d, key, a, clock)


def test_duplicate_nonce_and_replay() -> None:
    d, key, a, clock = fixture()
    ledger = t.VolatileFixtureLedger()
    verify(d, key, a, clock, ledger)
    with pytest.raises(ValueError, match="REPLAYED_DECISION"):
        verify(d, key, a, clock, ledger)
    changed = t.sign_fixture(d.model_copy(update={"decision_id": "SYNTHETIC_DECISION_2"}), key)
    with pytest.raises(ValueError, match="DUPLICATE_NONCE"):
        verify(changed, key, a, clock, ledger)


def test_concurrent_fixture_consumption() -> None:
    d, key, a, clock = fixture()
    ledger = t.VolatileFixtureLedger()

    def consume(_: int) -> str:
        try:
            return verify(d, key, a, clock, ledger)
        except ValueError as error:
            return str(error)

    with ThreadPoolExecutor(max_workers=2) as pool:
        outcomes = list(pool.map(consume, range(2)))
    assert outcomes.count("REPLAYED_DECISION") == 1
    assert outcomes.count("SYNTHETIC_POLICY_PASS_REAL_AUTHORITY_NOT_ESTABLISHED") == 1


def test_restart_safety_is_explicitly_unverified() -> None:
    d, key, a, clock = fixture()
    verify(d, key, a, clock, t.VolatileFixtureLedger())
    restarted = t.VolatileFixtureLedger()
    with pytest.raises(ValueError, match="DURABLE_REPLAY_UNVERIFIED"):
        restarted.require_durable()
    with pytest.raises(ValueError, match="TRUST_NOT_ESTABLISHED"):
        t.require_real_authority("RESTART_CONSUMPTION")


@pytest.mark.parametrize(
    "change",
    [
        {"version": "Unknown.v9"},
        {"unexpected": "field"},
        {"owner_identity_reference": ""},
        {"rollback_separation": "SELF_AUTHORIZED"},
        {"approval_timestamp": "2026-01-01T00:00:00Z"},
    ],
)
def test_unknown_or_ambiguous_schema(change: dict[str, Any]) -> None:
    d, _, _, _ = fixture()
    with pytest.raises(ValueError):
        t.parse(t.canonical(d.model_dump() | change))


def test_noncanonical_and_duplicate_json_rejected() -> None:
    d, _, _, _ = fixture()
    with pytest.raises(ValueError, match="NONCANONICAL_SERIALIZATION"):
        t.parse(json.dumps(d.model_dump(), indent=2).encode())
    raw = t.canonical(d.model_dump())
    with pytest.raises(ValueError, match="DUPLICATE_KEY"):
        t.parse(b'{"source":"SYNTHETIC_TEST",' + raw[1:])


def test_rotation_requires_separate_authority() -> None:
    _, _, a, _ = fixture()
    with pytest.raises(ValueError, match="ROTATION_NOT_AUTHORIZED"):
        t.rotate_fixture(a)


def test_synthetic_to_real_promotion_rejected() -> None:
    d, key, a, clock = fixture()
    d = d.model_copy(update={"source": "REAL_PENDING", "domain": t.REAL_DOMAIN})
    d = d.model_copy(update={"canonical_payload_fingerprint": t.sha(t.payload(d))})
    with pytest.raises(ValueError, match="TEST_ONLY"):
        verify(d, key, a, clock)


@pytest.mark.parametrize(
    "operation", ["SERIES_V2_IMPLEMENTATION", "AMENDMENT_ADOPTION", "ROLLBACK", "KITE_REQUEST"]
)
def test_no_real_operation(operation: str) -> None:
    with pytest.raises(ValueError, match="TRUST_NOT_ESTABLISHED"):
        t.require_real_authority(operation)


def test_ed25519_is_unavailable_not_silently_substituted() -> None:
    d, key, _, _ = fixture()
    with pytest.raises(ValueError, match="ED25519_UNAVAILABLE"):
        t.sign_fixture(d.model_copy(update={"signature_algorithm": "Ed25519"}), key)
