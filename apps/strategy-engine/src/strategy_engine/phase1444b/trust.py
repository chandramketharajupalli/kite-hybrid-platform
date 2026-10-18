"""Strict governance contracts and explicitly volatile synthetic policy simulation."""

import hashlib
import hmac
import json
from datetime import UTC, datetime, timedelta
from threading import Lock
from typing import Annotated, Any, Final, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

Hash = Annotated[str, Field(pattern=r"^[0-9a-f]{64}$")]
Identifier = Annotated[str, Field(min_length=1, max_length=200, pattern=r"^[A-Za-z0-9_.:-]+$")]
BASELINE = "3e679fcc4c8bb28dd35f0181f64dddf4b87f61c8"
AMENDMENT_RAW = "78f508939a1a050b7b2e90005e11f81e8262147a207144b88514fc467ab9f6fd"
AMENDMENT_CANONICAL = "36a79d7a4e43f0bd9ac4f93a9d0258918eb7a22692efce41f4d465fd59ce47c4"
CERTIFICATE = "ec3a30573abe7068d540523de1bc570fc0a6b8553119f60755bb934b3a9a9db1"
DECISION = "CONDITIONAL_ADOPTION_FOR_CURRENT_VALIDATION_ONLY"
SCOPE = "CURRENT_OFFLINE_VALIDATION_ONLY"
REAL_DOMAIN: Final = "KITE_GOVERNANCE_DECISION:Phase1444B:v1"
TEST_DOMAIN: Final = "KITE_SYNTHETIC_ONLY:Phase1444B:v1"


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def canonical(value: object) -> bytes:
    return json.dumps(
        value, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
    ).encode("utf-8")


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def instant(value: str) -> datetime:
    parsed = datetime.strptime(value, "%Y-%m-%dT%H:%M:%S.%fZ").replace(tzinfo=UTC)
    require(parsed.strftime("%Y-%m-%dT%H:%M:%S.%fZ") == value, "NONCANONICAL_TIME")
    return parsed


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)


class OwnerTrust(Strict):
    version: Literal["Phase1444BOwnerTrustModel.v1"] = "Phase1444BOwnerTrustModel.v1"
    owner_identity_reference: Identifier | None = None
    authority_role: Identifier | None = None
    allowed_decision_types: tuple[str, ...] = (DECISION,)
    enrollment_evidence_reference: Hash | None = None
    trusted_public_key_fingerprint: Hash | None = None
    trust_root_identity: Identifier | None = None
    trust_status: Literal["TRUST_NOT_ESTABLISHED"] = "TRUST_NOT_ESTABLISHED"
    effective_from: str | None = None
    expires_at: str | None = None
    revocation_status: Literal["UNVERIFIED"] = "UNVERIFIED"
    enrollment_approval_identity: Identifier | None = None
    verification_policy_version: str = "Phase1444BVerificationPolicy.v1.PROPOSED"


class SignedDecision(Strict):
    version: Literal["Phase1444BSignedGovernanceDecision.v1"] = (
        "Phase1444BSignedGovernanceDecision.v1"
    )
    domain: Literal["KITE_GOVERNANCE_DECISION:Phase1444B:v1", "KITE_SYNTHETIC_ONLY:Phase1444B:v1"]
    source: Literal["REAL_PENDING", "SYNTHETIC_TEST"]
    decision_type: Identifier
    owner_identity_reference: Identifier
    authority_role: Identifier
    trust_root_fingerprint: Hash
    signing_key_fingerprint: Hash
    source_git_baseline: Annotated[str, Field(pattern=r"^[0-9a-f]{40}$")]
    amendment_raw_sha256: Hash
    amendment_canonical_fingerprint: Hash
    fresh_certificate_sha256: Hash
    decision_scope: Identifier
    approval_timestamp: str
    expiry_timestamp: str
    decision_id: Identifier
    replay_nonce: Annotated[str, Field(pattern=r"^[0-9a-f]{64}$")]
    revocation_reference: Hash
    trusted_time_reference: Hash
    governance_fingerprint: Hash
    rollback_separation: Literal["SEPARATE_AUTHORIZATION_REQUIRED"]
    signature_algorithm: Literal["Ed25519", "HMAC_SHA256_SYNTHETIC_ONLY"]
    canonical_payload_fingerprint: Hash
    signature_bytes: Annotated[str, Field(pattern=r"^(?:[0-9a-f]{64}|[0-9a-f]{128})$")]

    @field_validator("approval_timestamp", "expiry_timestamp")
    @classmethod
    def validate_time(cls, value: str) -> str:
        instant(value)
        return value


def payload(decision: SignedDecision) -> bytes:
    checked = SignedDecision.model_validate(decision.model_dump())
    return canonical(
        checked.model_dump(exclude={"signature_bytes", "canonical_payload_fingerprint"})
    )


def signature_input(decision: SignedDecision) -> bytes:
    return decision.domain.encode("ascii") + b"\x00" + payload(decision)


def parse(raw: bytes) -> SignedDecision:
    def unique(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            require(key not in result, "DUPLICATE_KEY")
            result[key] = value
        return result

    value = json.loads(raw, object_pairs_hook=unique)
    decision = SignedDecision.model_validate(value)
    require(raw == canonical(decision.model_dump()), "NONCANONICAL_SERIALIZATION")
    require(sha(payload(decision)) == decision.canonical_payload_fingerprint, "PAYLOAD_HASH")
    return decision


class FixtureAuthority(Strict):
    """Caller-supplied synthetic assumptions; never an independently verified trust root."""

    source: Literal["SYNTHETIC_TEST"] = "SYNTHETIC_TEST"
    owner: Identifier
    role: Identifier
    root: Hash
    key_fingerprint: Hash
    governance: Hash
    revocation_reference: Hash
    trusted_time_reference: Hash
    effective_from: str
    expires_at: str
    allowed_decisions: tuple[str, ...] = (DECISION,)
    revoked_key: bool = False
    revoked_owner: bool = False
    revoked_decisions: frozenset[str] = frozenset()
    conflicting: bool = False
    key_available: bool = True
    rotation_authorized: bool = False


class FixtureClock(Strict):
    source: Literal["SYNTHETIC_TEST"] = "SYNTHETIC_TEST"
    now: datetime
    prior_checkpoint: datetime
    evidence: Hash | None
    uncertainty_seconds: Annotated[int, Field(ge=0, le=1)] = 1


class VolatileFixtureLedger:
    """Atomic in-process simulation only. No persistence or real consumption guarantee."""

    def __init__(self) -> None:
        self._lock = Lock()
        self._ids: set[str] = set()
        self._nonces: set[str] = set()

    def consume(self, decision_id: str, nonce: str) -> None:
        with self._lock:
            require(decision_id not in self._ids, "REPLAYED_DECISION")
            require(nonce not in self._nonces, "DUPLICATE_NONCE")
            self._ids.add(decision_id)
            self._nonces.add(nonce)

    def require_durable(self) -> None:
        raise ValueError("DURABLE_REPLAY_UNVERIFIED")


def sign_fixture(decision: SignedDecision, key: bytes) -> SignedDecision:
    """HMAC fixture only; not an Ed25519 implementation or real owner signature."""
    require(decision.source == "SYNTHETIC_TEST" and decision.domain == TEST_DOMAIN, "TEST_ONLY")
    require(decision.signature_algorithm == "HMAC_SHA256_SYNTHETIC_ONLY", "ED25519_UNAVAILABLE")
    require(len(key) >= 32, "FIXTURE_KEY_TOO_SHORT")
    checked = SignedDecision.model_validate(decision.model_dump())
    return checked.model_copy(
        update={
            "canonical_payload_fingerprint": sha(payload(checked)),
            "signature_bytes": hmac.new(key, signature_input(checked), hashlib.sha256).hexdigest(),
        }
    )


def verify_fixture(
    raw: bytes,
    key: bytes,
    authority: FixtureAuthority,
    clock: FixtureClock,
    ledger: VolatileFixtureLedger,
) -> str:
    decision = parse(raw)
    authority = FixtureAuthority.model_validate(authority.model_dump())
    clock = FixtureClock.model_validate(clock.model_dump())
    require(decision.source == "SYNTHETIC_TEST" and decision.domain == TEST_DOMAIN, "TEST_ONLY")
    require(decision.signature_algorithm == "HMAC_SHA256_SYNTHETIC_ONLY", "ED25519_UNAVAILABLE")
    require(len(key) >= 32, "FIXTURE_KEY_TOO_SHORT")
    require(authority.key_available, "LOST_KEY")
    require(decision.trust_root_fingerprint == authority.root, "UNKNOWN_TRUST_ROOT")
    require(decision.owner_identity_reference == authority.owner, "OWNER_MISMATCH")
    require(decision.authority_role == authority.role == "EVIDENCE_ADOPTION_OWNER", "WRONG_ROLE")
    require(decision.signing_key_fingerprint == authority.key_fingerprint, "UNKNOWN_KEY")
    require(decision.governance_fingerprint == authority.governance, "GOVERNANCE_BINDING")
    require(
        decision.decision_type in authority.allowed_decisions
        and decision.decision_type == DECISION,
        "UNAUTHORIZED_DECISION_TYPE",
    )
    require(decision.decision_scope == SCOPE, "UNAUTHORIZED_SCOPE")
    require(decision.source_git_baseline == BASELINE, "BASELINE_BINDING")
    require(decision.amendment_raw_sha256 == AMENDMENT_RAW, "AMENDMENT_RAW_BINDING")
    require(
        decision.amendment_canonical_fingerprint == AMENDMENT_CANONICAL,
        "AMENDMENT_CANONICAL_BINDING",
    )
    require(decision.fresh_certificate_sha256 == CERTIFICATE, "CERTIFICATE_BINDING")
    require(
        hmac.compare_digest(
            decision.signature_bytes,
            hmac.new(key, signature_input(decision), hashlib.sha256).hexdigest(),
        ),
        "INVALID_FIXTURE_SIGNATURE",
    )
    require(not authority.revoked_key, "KEY_REVOKED")
    require(not authority.revoked_owner, "OWNER_REVOKED")
    require(decision.decision_id not in authority.revoked_decisions, "DECISION_REVOKED")
    require(not authority.conflicting, "CONFLICTING_DECISION")
    require(decision.revocation_reference == authority.revocation_reference, "REVOCATION_BINDING")
    require(
        clock.evidence is not None
        and clock.evidence == authority.trusted_time_reference == decision.trusted_time_reference,
        "TRUSTED_TIME_UNVERIFIED",
    )
    require(
        clock.now.utcoffset() == timedelta(0)
        and clock.prior_checkpoint.utcoffset() == timedelta(0),
        "UTC_REQUIRED",
    )
    require(clock.now >= clock.prior_checkpoint, "CLOCK_ROLLBACK")
    start, end = instant(decision.approval_timestamp), instant(decision.expiry_timestamp)
    require(end - start == timedelta(hours=168), "INVALID_VALIDITY")
    low = clock.now - timedelta(seconds=clock.uncertainty_seconds)
    high = clock.now + timedelta(seconds=clock.uncertainty_seconds)
    require(start <= low and high < end, "EXPIRED_OR_FUTURE")
    require(
        instant(authority.effective_from) <= start and end <= instant(authority.expires_at),
        "AUTHORITY_VALIDITY",
    )
    ledger.consume(decision.decision_id, decision.replay_nonce)
    return "SYNTHETIC_POLICY_PASS_REAL_AUTHORITY_NOT_ESTABLISHED"


def rotate_fixture(authority: FixtureAuthority) -> None:
    require(authority.rotation_authorized, "ROTATION_NOT_AUTHORIZED")
    raise ValueError("REAL_KEY_ROTATION_NOT_IMPLEMENTED")


def require_real_authority(operation: str) -> None:
    raise ValueError("TRUST_NOT_ESTABLISHED:" + operation)


def assessment() -> dict[str, Any]:
    return {
        "version": "Phase1444BTrustAssessment.v1",
        "owner_trust": OwnerTrust().model_dump(),
        "decision_schema": SignedDecision.model_json_schema(),
        "signature_policy": "Phase1444BSignaturePolicy.v1.PROPOSED_ED25519_UNAVAILABLE",
        "trusted_time": "TRUSTED_TIME_UNVERIFIED",
        "durable_replay": "UNVERIFIED",
        "os_nonexportable_key_provider": "UNVERIFIED",
        "enrollment": "NOT_PERFORMED",
        "synthetic_algorithm": "HMAC_SHA256_SYNTHETIC_ONLY_NOT_ED25519",
        "historical_receipts": "HISTORICAL_RECEIPTS_UNAVAILABLE",
        "missing_receipts": 73,
        "adoption": "NOT_AUTHORIZED",
        "Series_v2": "PROPOSED_NOT_ADOPTED",
        "implementation": "NOT_AUTHORIZED",
        "rollback": "NOT_AUTHORIZED",
        "Gate_A": "NOT_AUTHORIZED",
        "Gate_B": "NOT_AUTHORIZED",
        "Stage_B": "ZERO_CALL_ABORT",
        "Phase12": "BLOCK",
        "H1_H2": "FROZEN",
        "July": "SEALED",
        "operational": "NO_GO",
        "genuine_qualified_pairs": 0,
        "real_kite_requests": 0,
    }
