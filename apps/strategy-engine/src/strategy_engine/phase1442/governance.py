"""Review packets and synthetic decision checks, with no migration execution port."""

from datetime import datetime
from typing import Any, Literal

from pydantic import Field

from strategy_engine.backtest.dataset import Frozen, Hash, Instant
from strategy_engine.phase1439 import pipeline as p

CONTRACT = "Phase1441ResearchSeriesV2Candidate.v1"
ADJUSTMENT = "Phase1442CorporateActionMethodologyDecision.v1"
IDENTITY = "Phase1442HistoricalIdentityDecision.v1"
COMPARABILITY = "Phase1442ResearchComparabilityDecision.v1"
ROLLBACK = "Phase1442MigrationRollbackPolicy.v1"
SCENARIOS = (
    "NO_ACTION",
    "RAW_UNADJUSTED",
    "PROVIDER_ADJUSTED",
    "LOCALLY_ADJUSTED",
    "SPLIT",
    "BONUS",
    "DIVIDEND",
    "RIGHTS",
    "MERGER",
    "DEMERGER",
    "SYMBOL_CHANGE",
    "DELISTING",
    "SUSPENSION",
    "COMPLEX_RESTRUCTURING",
    "MIXED_BASES",
)
RISK_NAMES = (
    "SIGNAL_DRIFT",
    "PNL_CHANGE",
    "DOUBLE_ADJUSTMENT",
    "TOKEN_CONTINUITY",
    "SECURITY_CONFLATION",
    "FUTURE_INFORMATION",
    "DATASET_FINGERPRINT",
    "RESULT_POOLING",
    "REQUALIFICATION",
    "ROLLBACK_FAILURE",
    "EVIDENCE_MIGRATION",
    "LEGACY_PRESERVATION",
)


class Proposal(Frozen):
    version: Literal["Phase1442GovernanceProposal.v1"] = "Phase1442GovernanceProposal.v1"
    baseline: str = Field(pattern=r"^[0-9a-f]{40}$")
    candidate: Hash
    adjustment: Hash
    identity: Hash
    comparability: Hash
    rollback: Hash
    permitted_files: tuple[str, ...]
    scope: Literal["SEPARATE_REVIEWED_IMPLEMENTATION_ONLY"] = (
        "SEPARATE_REVIEWED_IMPLEMENTATION_ONLY"
    )
    tests: tuple[str, ...] = (
        "FULL_SUITE",
        "LEGACY_INVARIANCE",
        "CAUSALITY",
        "REQUALIFICATION",
        "ROLLBACK_VALIDATION",
        "TWO_REPLAYS",
        "PRESERVATION",
    )


class Approval(Frozen):
    version: Literal["Phase1442ResearchMigrationApproval.v1"] = (
        "Phase1442ResearchMigrationApproval.v1"
    )
    status: Literal["NOT_AUTHORIZED", "APPROVED", "REJECTED", "REVOKED"] = "NOT_AUTHORIZED"
    source: Literal["PENDING_OWNER_INPUT", "SYNTHETIC_TEST"] = "PENDING_OWNER_INPUT"
    governance_fingerprint: Hash
    baseline: str = Field(pattern=r"^[0-9a-f]{40}$")
    owner: str | None = None
    decision_evidence: Hash | None = None
    decided_at: Instant | None = None
    expires_at: Instant | None = None
    approved_contract: str | None = None
    adjustment: Hash | None = None
    identity: Hash | None = None
    comparability: Hash | None = None
    rollback: Hash | None = None
    rollback_authority: str | None = None
    implementation_scope: str | None = None
    permitted_files: tuple[str, ...] = ()
    tests: tuple[str, ...] = ()
    conflicting: bool = False
    revoked: bool = False


def pending(proposal: Proposal) -> Approval:
    return Approval(governance_fingerprint=p.fingerprint(proposal), baseline=proposal.baseline)


def verify_synthetic_decision(
    decision: Approval, proposal: Proposal, expected: str, now: datetime
) -> str:
    """Structural simulation only; hashes do not authenticate owner identity or consent."""
    decision = Approval.model_validate(decision.model_dump())
    proposal = Proposal.model_validate(proposal.model_dump())
    p.require(p.fingerprint(decision) == expected, "DECISION_FINGERPRINT")
    p.require(decision.source == "SYNTHETIC_TEST", "OWNER_AUTHENTICATION_UNAVAILABLE")
    p.require(decision.status == "APPROVED" and not decision.revoked, "NOT_AUTHORIZED")
    p.require(not decision.conflicting, "CONFLICTING_DECISION")
    p.require(
        bool(decision.owner and decision.owner.strip()) and decision.decision_evidence is not None,
        "MISSING_OWNER_EVIDENCE",
    )
    p.require(decision.decided_at is not None and decision.expires_at is not None, "MISSING_EXPIRY")
    if decision.decided_at is None or decision.expires_at is None:
        raise ValueError("MISSING_EXPIRY")
    p.require(decision.decided_at <= now < decision.expires_at, "EXPIRED_OR_FUTURE_DECISION")
    p.require(decision.governance_fingerprint == p.fingerprint(proposal), "GOVERNANCE_BINDING")
    p.require(decision.baseline == proposal.baseline, "BASELINE_BINDING")
    p.require(decision.approved_contract == CONTRACT, "CONTRACT_BINDING")
    for name in ("adjustment", "identity", "comparability", "rollback"):
        p.require(getattr(decision, name) == getattr(proposal, name), "POLICY_BINDING:" + name)
    p.require(
        bool(decision.rollback_authority and decision.rollback_authority.strip()),
        "ROLLBACK_AUTHORITY",
    )
    p.require(decision.implementation_scope == proposal.scope, "SCOPE_BINDING")
    p.require(
        bool(proposal.permitted_files) and decision.permitted_files == proposal.permitted_files,
        "INVENTORY_BINDING",
    )
    p.require(len(set(proposal.permitted_files)) == len(proposal.permitted_files), "DUPLICATE_PATH")
    for path in proposal.permitted_files:
        p.require(
            path.startswith("apps/strategy-engine/")
            and ".." not in path.split("/")
            and "\\" not in path
            and ":" not in path,
            "UNSAFE_PATH",
        )
    p.require(decision.tests == proposal.tests and bool(proposal.tests), "TEST_SCOPE_BINDING")
    return "SYNTHETIC_DECISION_STRUCTURALLY_VALID_EXECUTION_NOT_AUTHORIZED"


def execute_migration() -> None:
    raise ValueError("MIGRATION_NOT_AUTHORIZED")


class ResultFamily(Frozen):
    contract: Literal["LEGACY_SERIES_V1", "CANDIDATE_SERIES_V2"]
    basis: Literal[
        "RAW_UNADJUSTED", "PROVIDER_ADJUSTED", "LOCALLY_ADJUSTED", "ADJUSTMENT_UNVERIFIED"
    ]
    methodology: Hash
    identity: Hash
    availability: Hash
    actions: Hash
    costs: Hash
    execution: Hash
    qualification: Hash
    source_type: Literal["SYNTHETIC", "GENUINE"]


def compare(left: ResultFamily, right: ResultFamily, genuine_claim: bool = False) -> str:
    left = ResultFamily.model_validate(left.model_dump())
    right = ResultFamily.model_validate(right.model_dump())
    p.require(left == right, "INCOMPATIBLE_RESULT_FAMILIES")
    p.require(left.basis != "ADJUSTMENT_UNVERIFIED", "UNVERIFIED_BASIS")
    p.require(not genuine_claim, "GENUINE_QUALIFICATION_NOT_AUTHORIZED")
    return "SAME_METADATA_FAMILY_NOT_QUALIFICATION_APPROVAL"


def scenario_gate(action: str, identity_unambiguous: bool, basis: str) -> str:
    p.require(action in SCENARIOS, "UNSUPPORTED_CORPORATE_ACTION")
    p.require(identity_unambiguous, "AMBIGUOUS_IDENTITY")
    p.require(
        basis in {"RAW_UNADJUSTED", "PROVIDER_ADJUSTED", "LOCALLY_ADJUSTED"},
        "MIXED_OR_UNVERIFIED_BASIS",
    )
    return "PENDING_OWNER_DECISION"


def assessment() -> dict[str, Any]:
    rows = []
    for action in SCENARIOS:
        rows.append(
            {
                "scenario": action,
                "status": "PENDING_OWNER_DECISION",
                "source": "authoritative action/identity/basis and completeness evidence required",
                "price": "UNCHANGED_LEGACY" if action == "NO_ACTION" else "UNSELECTED",
                "volume": "UNCHANGED_LEGACY" if action == "NO_ACTION" else "UNSELECTED",
                "effective_date": "explicit ex/effective boundaries; no inferred dates",
                "rounding": "NO_NEW_CONVENTION_ADOPTED",
                "availability": "PIT_OR_LABELED_RETROSPECTIVE",
                "comparability": "SEPARATE_CHANGED_FAMILIES",
                "transformation": "NOT_IMPLEMENTED",
                "approval": "SEPARATE_AUTHENTICATED_OWNER_AND_GOVERNANCE_DECISION",
            }
        )
    return {
        "version": "Phase1442GovernanceAssessment.v1",
        "adjustment_version": ADJUSTMENT,
        "identity_version": IDENTITY,
        "comparability_version": COMPARABILITY,
        "rollback_version": ROLLBACK,
        "candidate": "PROPOSED_NOT_ADOPTED",
        "rows": rows,
        "risks": [
            {
                "risk": name,
                "severity": "HIGH",
                "detection": "paired evidence and regression",
                "mitigation_status": "PROPOSED_NOT_IMPLEMENTED",
                "owner_acceptance": "REQUIRED",
            }
            for name in RISK_NAMES
        ],
        "methodology": "PENDING_OWNER_DECISION",
        "identity": "PENDING_OWNER_DECISION",
        "migration": "NOT_AUTHORIZED",
        "rollback": "POLICY_PROPOSED_NOT_EXECUTED",
        "genuine_performance": None,
        "genuine_qualified_pairs": 0,
        "real_kite_requests": 0,
        "Gate_A": "NOT_AUTHORIZED",
        "Gate_B": "NOT_AUTHORIZED",
        "operational": "NO_GO",
    }
