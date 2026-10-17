"""Explicit synthetic decision authentication; never real owner authority."""

import hashlib
import hmac
from datetime import datetime
from typing import Literal

from pydantic import Field

from strategy_engine.backtest.dataset import Frozen, Hash, Instant
from strategy_engine.phase1439 import pipeline as p


class FileCommitment(Frozen):
    path: str
    sha256: Hash | None = None


class Stage(Frozen):
    number: int = Field(ge=1, le=6)
    name: str
    permitted: tuple[FileCommitment, ...]
    prohibited_fingerprint: Hash
    interfaces: tuple[str, ...]
    tests: tuple[str, ...]
    evidence: tuple[str, ...]
    budget_seconds: int = Field(gt=0, le=1800)
    rollback: Hash
    requires_stage: int = Field(ge=0, le=5)
    status: Literal["UNAPPROVED"] = "UNAPPROVED"


class Scope(Frozen):
    version: Literal["Phase1443MigrationScope.v1"] = "Phase1443MigrationScope.v1"
    status: Literal["UNAPPROVED"] = "UNAPPROVED"
    baseline: str = Field(pattern=r"^[0-9a-f]{40}$")
    prohibited_files: dict[str, Hash]
    stages: tuple[Stage, ...]


def scope_check(scope: Scope, require_hashes: bool = False) -> str:
    scope = Scope.model_validate(scope.model_dump())
    p.require(tuple(s.number for s in scope.stages) == (1, 2, 3, 4, 5, 6), "STAGE_INVENTORY")
    p.require(bool(scope.prohibited_files), "MISSING_LEGACY_PROTECTION")
    protected = p.sha(p.canonical(scope.prohibited_files))
    seen: set[str] = set()
    for stage in scope.stages:
        p.require(stage.requires_stage == stage.number - 1, "STAGE_DEPENDENCY")
        p.require(stage.prohibited_fingerprint == protected, "PROTECTION_BINDING")
        p.require(
            bool(stage.permitted and stage.tests and stage.evidence and stage.interfaces),
            "MISSING_STAGE_SCOPE",
        )
        for item in stage.permitted:
            path = item.path
            p.require(
                not path.startswith(("/", "\\"))
                and ":" not in path
                and "\\" not in path
                and all(x not in {"", ".", ".."} for x in path.split("/")),
                "UNSAFE_PATH",
            )
            p.require(
                path not in scope.prohibited_files and path not in seen, "LEGACY_OR_DUPLICATE_PATH"
            )
            seen.add(path)
            if require_hashes:
                p.require(item.sha256 is not None, "MISSING_CONTENT_COMMITMENT")
    return p.fingerprint(scope)


class Bindings(Frozen):
    baseline: str = Field(pattern=r"^[0-9a-f]{40}$")
    candidate: Hash
    corporate_action: Hash
    identity: Hash
    comparability: Hash
    scope: Hash
    rollback: Hash


class Decision(Frozen):
    version: Literal["Phase1443OwnerMethodologyDecision.v1"] = (
        "Phase1443OwnerMethodologyDecision.v1"
    )
    state: Literal[
        "PENDING_OWNER_DECISION", "APPROVED", "REJECTED", "DEFERRED", "EXPIRED", "REVOKED"
    ] = "PENDING_OWNER_DECISION"
    source: Literal["PENDING_OWNER_INPUT", "SYNTHETIC_TEST"] = "PENDING_OWNER_INPUT"
    proposal_fingerprint: Hash
    owner: str | None = None
    decided_at: Instant | None = None
    evidence: Hash | None = None
    approved: Bindings | None = None
    corporate_choice: Literal["A", "B", "C", "D"] | None = None
    identity_choice: Literal["A", "B", "C", "D"] | None = None
    rollback_authority: str | None = None
    expires_at: Instant | None = None
    stage: int | None = Field(default=None, ge=1, le=6)
    completed_stage: int = Field(default=0, ge=0, le=5)
    prior_receipt: Hash | None = None
    conflicting: bool = False
    signature: Hash | None = None


def pending(bindings: Bindings) -> Decision:
    return Decision(proposal_fingerprint=p.fingerprint(bindings))


def synthetic_signature(decision: Decision, key: bytes) -> str:
    p.require(decision.source == "SYNTHETIC_TEST", "SYNTHETIC_AUTH_ONLY")
    p.require(len(key) >= 32, "TEST_KEY_TOO_SHORT")
    body = decision.model_dump(mode="json", exclude={"signature"})
    return hmac.new(
        key, b"PHASE1443_SYNTHETIC_ONLY:" + p.canonical(body), hashlib.sha256
    ).hexdigest()


def verify_synthetic(
    decision: Decision, bindings: Bindings, scope: Scope, evidence: bytes, key: bytes, now: datetime
) -> str:
    """Externally supplied synthetic evidence/key only; no credential or identity discovery."""
    decision = Decision.model_validate(decision.model_dump())
    bindings = Bindings.model_validate(bindings.model_dump())
    p.require(decision.source == "SYNTHETIC_TEST", "OWNER_AUTHENTICATION_NOT_SUPPLIED")
    p.require(decision.state == "APPROVED", "OWNER_APPROVAL_MISSING_OR_INACTIVE")
    p.require(not decision.conflicting, "CONFLICTING_OWNER_DECISIONS")
    p.require(bool(decision.owner and decision.owner.strip()), "MISSING_OWNER")
    p.require(
        decision.signature is not None
        and hmac.compare_digest(decision.signature, synthetic_signature(decision, key)),
        "INVALID_AUTHENTICATION",
    )
    p.require(decision.evidence == p.sha(evidence) and bool(evidence), "INVALID_DECISION_EVIDENCE")
    p.require(decision.decided_at is not None and decision.expires_at is not None, "MISSING_TIME")
    if decision.decided_at is None or decision.expires_at is None:
        raise ValueError("MISSING_TIME")
    p.require(decision.decided_at <= now < decision.expires_at, "EXPIRED_OR_FUTURE_APPROVAL")
    p.require(
        decision.approved == bindings and decision.proposal_fingerprint == p.fingerprint(bindings),
        "POLICY_BINDING",
    )
    p.require(
        scope.baseline == bindings.baseline and scope_check(scope, True) == bindings.scope,
        "SCOPE_BINDING",
    )
    p.require(
        bool(decision.rollback_authority and decision.rollback_authority.strip()),
        "MISSING_ROLLBACK_AUTHORITY",
    )
    p.require(
        decision.corporate_choice is not None and decision.identity_choice is not None,
        "METHODOLOGY_UNSELECTED",
    )
    p.require(decision.stage is not None, "STAGE_UNAPPROVED")
    if decision.stage is None:
        raise ValueError("STAGE_UNAPPROVED")
    stage = scope.stages[decision.stage - 1]
    p.require(stage.rollback == bindings.rollback, "ROLLBACK_BINDING")
    p.require(decision.completed_stage == stage.requires_stage, "UNAUTHORIZED_STAGE_TRANSITION")
    p.require(stage.number == 1 or decision.prior_receipt is not None, "MISSING_PRIOR_RECEIPT")
    return "SYNTHETIC_AUTH_VERIFIED_REAL_IMPLEMENTATION_NOT_AUTHORIZED"


def activate_stage() -> None:
    raise ValueError("NO_STAGE_EXECUTION_AUTHORIZED")
