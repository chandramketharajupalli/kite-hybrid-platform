"""Versioned public-metadata certification. No data acquisition or strategy execution."""
import json
from datetime import date
from typing import Any, Literal

from pydantic import Field, field_validator, model_validator

from strategy_engine.backtest.dataset import Frozen, Hash, digest
from strategy_engine.research.universe import InstrumentIdentity, ResearchUniverseSpec

POLICY_PIN = "a350a24be35b6a844a0a769e8f14bd0821be3ca8bed44d4f8df5d32fc124f5f4"
ActionType = Literal[
    "CASH_DIVIDEND", "STOCK_SPLIT", "BONUS_ISSUE", "RIGHTS_ISSUE", "MERGER", "DEMERGER",
    "SPINOFF", "SECURITY_RESTRUCTURING", "SYMBOL_CHANGE", "FACE_VALUE_CHANGE", "BUYBACK", "OTHER",
]
Classification = Literal[
    "CERTIFIED_CONTINUOUS", "CERTIFIED_WITH_DIVIDEND_NOTE", "REQUIRES_SEGMENTATION",
    "IDENTITY_UNRESOLVED", "CORPORATE_ACTION_UNRESOLVED",
]
Component = Literal["CERTIFIED", "CERTIFIED_WITH_NOTE", "REQUIRES_SEGMENTATION", "UNRESOLVED"]
ALLOWED = {"CERTIFIED_CONTINUOUS", "CERTIFIED_WITH_DIVIDEND_NOTE"}


def fingerprint(value: Any) -> str:
    return digest(json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True,
                             allow_nan=False))


def verify_policy(envelope: dict[str, Any]) -> None:
    if set(envelope) != {"policy", "fingerprint"} or (
        envelope["fingerprint"] != POLICY_PIN or fingerprint(envelope["policy"]) != POLICY_PIN
    ):
        raise ValueError("POLICY_FINGERPRINT_MISMATCH")


class Evidence(Frozen):
    evidence_id: str = Field(min_length=1)
    organization: str = Field(min_length=1)
    title: str = Field(min_length=1)
    url: str = Field(pattern=r"^https://")
    quality: Literal["EXCHANGE_PRIMARY", "ISSUER_PRIMARY", "REGULATOR_PRIMARY",
                     "BROKER_REFERENCE_ONLY"]
    publication_date: date | None
    effective_date: date | None
    symbols: tuple[str, ...]
    finding: str = Field(min_length=1)
    limitation: str
    document_sha256: Hash | None = None


class Coverage(Frozen):
    """Reviewed coverage assertion, not a classification or an inferred empty search."""
    complete: bool = Field(strict=True)
    evidence_ids: tuple[str, ...]
    reason: str = Field(min_length=1)

    @model_validator(mode="after")
    def supported(self) -> "Coverage":
        if self.complete and not self.evidence_ids:
            raise ValueError("UNSUPPORTED_COVERAGE")
        if len(set(self.evidence_ids)) != len(self.evidence_ids):
            raise ValueError("DUPLICATE_EVIDENCE_REFERENCE")
        return self


class Action(Frozen):
    action_id: str
    kind: ActionType
    announcement_date: date | None
    ex_date: date | None
    record_date: date | None
    effective_date: date | None
    amount_or_ratio: str | None
    impact: Literal["DIVIDEND", "SEGMENT", "NONE", "UNRESOLVED"]
    volume_reset: bool = Field(strict=True)
    evidence_ids: tuple[str, ...]
    note: str

    @model_validator(mode="after")
    def valid(self) -> "Action":
        if not self.evidence_ids or len(set(self.evidence_ids)) != len(self.evidence_ids):
            raise ValueError("ACTION_EVIDENCE_REQUIRED")
        if self.kind == "CASH_DIVIDEND" and self.impact not in {"DIVIDEND", "UNRESOLVED"}:
            raise ValueError("DIVIDEND_IMPACT_REQUIRED")
        if self.kind in {"STOCK_SPLIT", "BONUS_ISSUE", "FACE_VALUE_CHANGE"} and (
            self.impact not in {"SEGMENT", "UNRESOLVED"}
        ):
            raise ValueError("SEGMENTATION_REQUIRED")
        if self.impact == "DIVIDEND" and (self.ex_date is None or self.volume_reset):
            raise ValueError("DIVIDEND_BOUNDARY_INVALID")
        if self.impact == "SEGMENT" and self.ex_date is None and self.effective_date is None:
            raise ValueError("SEGMENT_BOUNDARY_REQUIRED")
        return self


class Review(Frozen):
    identity: InstrumentIdentity
    observed_isins: tuple[str, ...]
    observed_series: tuple[str, ...]
    security_identity: Coverage
    symbol_continuity: Coverage
    series_continuity: Coverage
    corporate_action_completeness: Coverage
    conflicts: tuple[str, ...]
    actions: tuple[Action, ...]
    notes: tuple[str, ...]

    @field_validator("actions")
    @classmethod
    def order_actions(cls, values: tuple[Action, ...]) -> tuple[Action, ...]:
        if len({v.action_id for v in values}) != len(values):
            raise ValueError("DUPLICATE_ACTION")
        return tuple(sorted(values, key=lambda v: v.action_id))


class EvidenceManifest(Frozen):
    version: Literal["HistoricalContinuityEvidence.v1"] = "HistoricalContinuityEvidence.v1"
    generation: Literal[
        "phase-12.1-g1", "phase-12.1-g2", "phase-12.1a-g1", "phase-12.1b-g1"
    ] = "phase-12.1-g2"
    policy_fingerprint: Hash
    universe_fingerprint: Hash
    first: date
    end_exclusive: date
    evidence: tuple[Evidence, ...]
    reviews: tuple[Review, ...]

    @field_validator("evidence")
    @classmethod
    def order_evidence(cls, values: tuple[Evidence, ...]) -> tuple[Evidence, ...]:
        if len({v.evidence_id for v in values}) != len(values):
            raise ValueError("DUPLICATE_EVIDENCE")
        return tuple(sorted(values, key=lambda v: v.evidence_id))

    @field_validator("reviews")
    @classmethod
    def order_reviews(cls, values: tuple[Review, ...]) -> tuple[Review, ...]:
        if len({v.identity.instrument_id for v in values}) != len(values):
            raise ValueError("DUPLICATE_INSTRUMENT")
        return tuple(sorted(values, key=lambda v: (v.identity.exchange, v.identity.symbol,
                                                   v.identity.instrument_id)))

    @model_validator(mode="after")
    def valid(self) -> "EvidenceManifest":
        if self.policy_fingerprint != POLICY_PIN or self.first >= self.end_exclusive:
            raise ValueError("POLICY_OR_WINDOW_INVALID")
        refs = {v.evidence_id: v for v in self.evidence}
        for review in self.reviews:
            coverages = (review.security_identity, review.symbol_continuity,
                         review.series_continuity, review.corporate_action_completeness)
            items: tuple[Coverage | Action, ...] = (*coverages, *review.actions)
            for item in items:
                if any(i not in refs or review.identity.symbol not in refs[i].symbols
                       for i in item.evidence_ids):
                    raise ValueError("UNKNOWN_OR_WRONG_INSTRUMENT_EVIDENCE")
            for coverage in coverages:
                if coverage.complete and not any(
                    refs[i].quality != "BROKER_REFERENCE_ONLY" for i in coverage.evidence_ids
                ):
                    raise ValueError("BROKER_REFERENCE_CANNOT_CERTIFY_HISTORY")
            for coverage in (review.security_identity, review.symbol_continuity,
                             review.series_continuity):
                if coverage.complete and not any(
                    refs[i].quality == "EXCHANGE_PRIMARY" for i in coverage.evidence_ids
                ):
                    raise ValueError("NSE_IDENTITY_EVIDENCE_REQUIRED")
        return self

    @property
    def fingerprint(self) -> str:
        return fingerprint(self.model_dump(mode="json"))


class Boundary(Frozen):
    instrument_id: str
    date: date
    action_id: str
    action: ActionType
    price_continuity: Literal[False] = False
    volume_continuity: bool = Field(strict=True)
    intraday_session_valid: Literal[True] = True
    segment_reset: bool = Field(strict=True)


class InstrumentCertification(Frozen):
    identity: InstrumentIdentity
    security_identity: Component
    symbol_continuity: Component
    series_continuity: Component
    intraday_price_continuity: Component
    cross_session_continuity: Component
    corporate_action_completeness: Component
    evidence_quality: Component
    classification: Classification
    boundaries: tuple[Boundary, ...]


class Certification(Frozen):
    version: Literal["HistoricalIdentityCertification.v1"] = "HistoricalIdentityCertification.v1"
    policy_fingerprint: Hash
    evidence_fingerprint: Hash
    universe_fingerprint: Hash
    first: date
    end_exclusive: date
    members: tuple[InstrumentCertification, ...]
    aggregate_status: str

    @property
    def fingerprint(self) -> str:
        return fingerprint(self.model_dump(mode="json"))


def certify(policy: dict[str, Any], manifest: EvidenceManifest,
            universe: ResearchUniverseSpec) -> Certification:
    verify_policy(policy)
    manifest = EvidenceManifest.model_validate(manifest.model_dump())
    if manifest.universe_fingerprint != universe.fingerprint:
        raise ValueError("UNIVERSE_FINGERPRINT_MISMATCH")
    from strategy_engine.backtest.dataset import NSE
    if (manifest.first, manifest.end_exclusive) != (
        universe.common_window.start.astimezone(NSE).date(),
        universe.common_window.end.astimezone(NSE).date(),
    ):
        raise ValueError("RESEARCH_WINDOW_MISMATCH")
    if tuple(r.identity for r in manifest.reviews) != universe.members:
        raise ValueError("MISSING_OR_MISMATCHED_INSTRUMENT_CERTIFICATION")
    members = []
    for r in manifest.reviews:
        identity_ok = all(c.complete for c in (
            r.security_identity, r.symbol_continuity, r.series_continuity)) and not r.conflicts
        actions_ok = r.corporate_action_completeness.complete and not any(
            a.impact == "UNRESOLVED" for a in r.actions)
        pending: list[Boundary] = []
        for action in r.actions:
            boundary_date = action.ex_date or action.effective_date
            if action.impact in {"DIVIDEND", "SEGMENT"} and boundary_date is not None:
                if manifest.first <= boundary_date < manifest.end_exclusive:
                    pending.append(Boundary(
                        instrument_id=r.identity.instrument_id, date=boundary_date,
                        action_id=action.action_id, action=action.kind,
                        volume_continuity=not action.volume_reset,
                        segment_reset=action.impact == "SEGMENT"))
        boundaries = tuple(sorted(pending, key=lambda b: (b.date, b.action_id)))
        segmented = any(b.segment_reset for b in boundaries)
        classification: Classification = (
            "IDENTITY_UNRESOLVED" if not identity_ok else
            "CORPORATE_ACTION_UNRESOLVED" if not actions_ok else
            "REQUIRES_SEGMENTATION" if segmented else
            "CERTIFIED_WITH_DIVIDEND_NOTE" if boundaries else "CERTIFIED_CONTINUOUS")
        complete = identity_ok and actions_ok
        members.append(InstrumentCertification(
            identity=r.identity,
            security_identity="CERTIFIED" if r.security_identity.complete and not r.conflicts
            else "UNRESOLVED",
            symbol_continuity="CERTIFIED" if r.symbol_continuity.complete else "UNRESOLVED",
            series_continuity="CERTIFIED" if r.series_continuity.complete else "UNRESOLVED",
            intraday_price_continuity="CERTIFIED" if complete else "UNRESOLVED",
            cross_session_continuity=("UNRESOLVED" if not complete else
                                      "REQUIRES_SEGMENTATION" if segmented else
                                      "CERTIFIED_WITH_NOTE" if boundaries else "CERTIFIED"),
            corporate_action_completeness="CERTIFIED" if actions_ok else "UNRESOLVED",
            evidence_quality="CERTIFIED" if complete else "UNRESOLVED",
            classification=classification, boundaries=boundaries))
    unresolved = any(m.classification.endswith("UNRESOLVED") for m in members)
    return Certification(
        policy_fingerprint=POLICY_PIN, evidence_fingerprint=manifest.fingerprint,
        universe_fingerprint=universe.fingerprint, first=manifest.first,
        end_exclusive=manifest.end_exclusive, members=tuple(members),
        aggregate_status=("UNIVERSE_CERTIFICATION_UNRESOLVED" if unresolved else
                          "UNIVERSE_REQUIRES_SEGMENTATION" if any(
                              m.classification == "REQUIRES_SEGMENTATION" for m in members)
                          else "UNIVERSE_CONTINUITY_CERTIFIED"))


def acquisition_gate(policy: dict[str, Any], manifest: EvidenceManifest,
                     universe: ResearchUniverseSpec,
                     envelope: dict[str, Any] | None) -> dict[str, Any]:
    """Re-derive instead of trusting caller-provided classifications or allow booleans."""
    try:
        expected = certify(policy, manifest, universe)
        if envelope is None or envelope != {
            "certification": expected.model_dump(mode="json"),
            "fingerprint": expected.fingerprint,
        }:
            raise ValueError("CERTIFICATION_MISSING_OR_TAMPERED")
        reasons = [f"{m.identity.symbol}:{m.classification}" for m in expected.members
                   if m.classification not in ALLOWED]
        pin: str | None = expected.fingerprint
    except ValueError as error:
        reasons, pin = [str(error)], None
    return dict(version="UniverseAcquisitionCertification.v1",
                universe_fingerprint=universe.fingerprint, certification_fingerprint=pin,
                acquisition_allowed=not reasons, reasons=reasons,
                strategy_evaluation_allowed=False)
