"""Provider-unavailable boundary. No custom cryptography or HMAC fallback."""

from datetime import datetime, timedelta
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

from strategy_engine.phase1444b.trust import (
    AMENDMENT_CANONICAL,
    AMENDMENT_RAW,
    CERTIFICATE,
    instant,
    require,
)

BASELINE = "c46db6308dd8466be604473133ab4ead14290d75"
Hash = Annotated[str, Field(pattern=r"^[0-9a-f]{64}$")]
Identifier = Annotated[str, Field(min_length=1, max_length=128, pattern=r"^[A-Za-z0-9_.:-]+$")]


class VerificationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)
    version: Literal["Phase1444CCryptoVerificationAdapter.v1"] = (
        "Phase1444CCryptoVerificationAdapter.v1"
    )
    algorithm: Literal["Ed25519"]
    domain: Literal["KITE_GOVERNANCE_DECISION:Phase1444B:v1"]
    canonical_payload: bytes
    signature: Annotated[bytes, Field(min_length=64, max_length=64)]
    public_key: Annotated[bytes, Field(min_length=32, max_length=32)]
    public_key_fingerprint: Hash
    enrollment_reference: Hash
    provider_identity: Identifier


def verify_signature(request: VerificationRequest) -> None:
    VerificationRequest.model_validate(request.model_dump())
    raise ValueError("CRYPTO_PROVIDER_NOT_AUTHORIZED")


class SyntheticDecision(BaseModel):
    """Prevalidated fixture assumption, explicitly not a signed or enrolled decision."""

    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)
    version: Literal["Phase1444CSyntheticDecision.v1"] = "Phase1444CSyntheticDecision.v1"
    source: Literal["TEST_ONLY"] = "TEST_ONLY"
    action: Literal["TEST_LEDGER_CONSUMPTION"] = "TEST_LEDGER_CONSUMPTION"
    decision_id: Identifier
    nonce: Hash
    signed_payload_fingerprint: Hash
    owner_authority_reference: Identifier
    trust_root_fingerprint: Hash
    key_reference: Identifier
    enrollment_reference: Hash | None
    enrolled_fixture: bool
    source_git_baseline: str = BASELINE
    amendment_raw: Hash = AMENDMENT_RAW
    amendment_canonical: Hash = AMENDMENT_CANONICAL
    certificate: Hash = CERTIFICATE
    approval_timestamp: str
    expiry: str
    trusted_time_reference: Hash | None

    @field_validator("approval_timestamp", "expiry")
    @classmethod
    def valid_time(cls, value: str) -> str:
        instant(value)
        return value


def check_fixture(decision: SyntheticDecision, now: datetime) -> None:
    SyntheticDecision.model_validate(decision.model_dump())
    require(decision.source_git_baseline == BASELINE, "WRONG_BASELINE")
    require(decision.amendment_raw == AMENDMENT_RAW, "WRONG_AMENDMENT_RAW")
    require(decision.amendment_canonical == AMENDMENT_CANONICAL, "WRONG_AMENDMENT_CANONICAL")
    require(decision.certificate == CERTIFICATE, "WRONG_CERTIFICATE")
    require(
        decision.enrolled_fixture and decision.enrollment_reference is not None,
        "OWNER_NOT_ENROLLED",
    )
    require(decision.trusted_time_reference is not None, "TRUSTED_TIME_UNVERIFIED")
    require(now.utcoffset() == timedelta(0), "UTC_REQUIRED")
    start, end = instant(decision.approval_timestamp), instant(decision.expiry)
    require(end - start == timedelta(hours=168), "WRONG_VALIDITY")
    require(start <= now < end, "EXPIRED_OR_FUTURE")


def real_authority(operation: str) -> None:
    raise ValueError("REAL_AUTHORITY_NOT_ESTABLISHED:" + operation)
