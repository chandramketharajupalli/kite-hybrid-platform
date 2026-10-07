"""Synthetic metadata/feature checks only; no study or strategy calls."""
import copy
import json
from datetime import date
from pathlib import Path
from typing import Any

import pytest
from test_diagnostics import completed, development_day

from strategy_engine.research.continuity import (
    Action,
    EvidenceManifest,
    acquisition_gate,
    certify,
    fingerprint,
    verify_policy,
)
from strategy_engine.research.continuity_features import (
    ContinuityContext,
    historical_early_volume,
    opening_gap,
    previous_close,
)
from strategy_engine.research.universe import ResearchUniverseSpec

ROOT = Path(__file__).resolve().parents[3]
FOLDER = ROOT / "research/phase-12.1"


def inputs() -> tuple[dict[str, Any], EvidenceManifest, ResearchUniverseSpec]:
    policy = json.loads((FOLDER / "certification-policy-v1.json").read_text())
    values = json.loads((FOLDER / "evidence-manifest.json").read_text())["manifest"]
    # Explicit synthetic coverage for gate allow cases; never written into real evidence.
    for review in values["reviews"]:
        review["conflicts"], review["actions"] = [], []
        for key in ("security_identity", "symbol_continuity", "series_continuity",
                    "corporate_action_completeness"):
            review[key]["complete"] = True
            review[key]["reason"] = "SYNTHETIC TEST ASSUMPTION ONLY"
    universe = ResearchUniverseSpec.model_validate_json(
        (ROOT / "research/phase-12.0/universe.json").read_text())
    return policy, EvidenceManifest.model_validate(values), universe


def envelope(policy: dict[str, Any], evidence: EvidenceManifest,
             universe: ResearchUniverseSpec) -> dict[str, Any]:
    cert = certify(policy, evidence, universe)
    return dict(certification=cert.model_dump(mode="json"), fingerprint=cert.fingerprint)


def test_policy_evidence_order_and_deterministic_classification() -> None:
    policy, evidence, universe = inputs()
    verify_policy(policy)
    values = evidence.model_dump()
    values["reviews"] = tuple(reversed(values["reviews"]))
    values["evidence"] = tuple(reversed(values["evidence"]))
    other = EvidenceManifest.model_validate(values)
    assert other.fingerprint == evidence.fingerprint
    first = envelope(policy, evidence, universe)
    assert first == envelope(policy, other, universe)
    assert acquisition_gate(policy, evidence, universe, first)["acquisition_allowed"]


@pytest.mark.parametrize("field", ["evidence", "reviews"])
def test_duplicate_evidence_or_instrument_rejected(field: str) -> None:
    _, evidence, _ = inputs()
    values = evidence.model_dump()
    values[field] += (values[field][0],)
    with pytest.raises(ValueError, match="DUPLICATE"):
        EvidenceManifest.model_validate(values)


def action(kind: str = "CASH_DIVIDEND") -> dict[str, Any]:
    return dict(action_id="synthetic-boundary", kind=kind, announcement_date="2026-02-02",
                ex_date="2026-02-07", record_date="2026-02-07", effective_date="2026-02-07",
                amount_or_ratio="1", impact="DIVIDEND" if kind == "CASH_DIVIDEND" else "SEGMENT",
                volume_reset=kind != "CASH_DIVIDEND", evidence_ids=["HDFCBANK-actions"],
                note="Synthetic boundary only")


def test_unknown_action_and_unsafe_split_rejected() -> None:
    with pytest.raises(ValueError):
        Action.model_validate(action("UNKNOWN"))
    with pytest.raises(ValueError, match="SEGMENTATION_REQUIRED"):
        Action.model_validate({**action("STOCK_SPLIT"), "impact": "NONE"})


@pytest.mark.parametrize("change", ["identity", "actions", "split", "bonus", "missing",
                                   "universe", "window", "policy", "tampered", "absent"])
def test_gate_fails_closed(change: str) -> None:
    policy, evidence, universe = inputs()
    values = evidence.model_dump(mode="json")
    proof: dict[str, Any] | None = envelope(policy, evidence, universe)
    if change == "identity":
        values["reviews"][0]["security_identity"]["complete"] = False
    elif change == "actions":
        values["reviews"][0]["corporate_action_completeness"]["complete"] = False
    elif change in {"split", "bonus"}:
        values["reviews"][0]["actions"] = [action(
            "STOCK_SPLIT" if change == "split" else "BONUS_ISSUE")]
    elif change == "missing":
        values["reviews"].pop()
    elif change == "universe":
        values["universe_fingerprint"] = "b" * 64
    elif change == "window":
        values["first"] = "2026-02-03"
    elif change == "policy":
        policy["policy"]["gap_policy"] = "ignore"
    elif change == "tampered":
        assert proof is not None
        proof["fingerprint"] = "b" * 64
    elif change == "absent":
        proof = None
    evidence = EvidenceManifest.model_validate(values)
    if change in {"identity", "actions", "split", "bonus"}:
        proof = envelope(policy, evidence, universe)
    assert not acquisition_gate(policy, evidence, universe, proof)["acquisition_allowed"]


def test_dividend_gap_invalid_volume_retained_and_session_indicators_unchanged() -> None:
    from strategy_engine.research.features import opening_range, vwap
    policy, evidence, universe = inputs()
    values = evidence.model_dump(mode="json")
    values["reviews"][0]["actions"] = [action()]
    evidence = EvidenceManifest.model_validate(values)
    cert = certify(policy, evidence, universe)
    member = cert.members[0]
    assert member.classification == "CERTIFIED_WITH_DIVIDEND_NOTE"
    assert acquisition_gate(policy, evidence, universe,
                            envelope(policy, evidence, universe))["acquisition_allowed"]
    context = ContinuityContext(instrument_id=member.identity.instrument_id,
                                first=cert.first, end_exclusive=cert.end_exclusive,
                                boundaries=member.boundaries)
    history = tuple(completed(development_day([100] * 30, i)) for i in range(5))
    current = completed(development_day([50] * 30, 5))
    instrument = member.identity.instrument_id
    assert opening_gap(instrument, current, history[-1], context) == {
        "status": "INVALID_BOUNDARY", "value": None}
    assert previous_close(instrument, current, history[-1], context) is None
    volume = historical_early_volume(instrument, current, history, context)
    assert volume is not None and volume["value"] == 1
    assert vwap(current) == 50 and opening_range(current, 15) is not None
    later = completed(development_day([50] * 30, 6))
    assert opening_gap(instrument, later, current, context)["value"] == 0
    with pytest.raises(ValueError, match="FUTURE_OR_INCOMPLETE"):
        historical_early_volume(instrument, current, history + (current,), context)
    with pytest.raises(ValueError, match="SCOPE"):
        opening_gap("other", current, history[-1], context)


def test_segment_reset_deterministic_and_future_boundary_cannot_change_past() -> None:
    policy, evidence, universe = inputs()
    values = evidence.model_dump(mode="json")
    values["reviews"][0]["actions"] = [action("STOCK_SPLIT")]
    cert = certify(policy, EvidenceManifest.model_validate(values), universe)
    member = cert.members[0]
    context = ContinuityContext(instrument_id=member.identity.instrument_id,
                                first=cert.first, end_exclusive=cert.end_exclusive,
                                boundaries=member.boundaries)
    assert context.segments() == ((date(2026, 2, 2), date(2026, 2, 7)),
                                  (date(2026, 2, 7), date(2026, 7, 1)))
    history = tuple(completed(development_day([100] * 30, i)) for i in range(5))
    current = completed(development_day([50] * 30, 5))
    assert historical_early_volume(member.identity.instrument_id, current, history, context) is None
    assert previous_close(member.identity.instrument_id, current, history[-1], context) is None
    before = opening_gap(member.identity.instrument_id, history[-1], history[-2], context)
    empty = context.model_copy(update={"boundaries": ()})
    assert before == opening_gap(member.identity.instrument_id, history[-1], history[-2], empty)


def test_retained_offline_artifacts_match_without_network_or_corpus_access() -> None:
    policy, _, universe = inputs()
    raw = json.loads((FOLDER / "evidence-manifest.json").read_text())
    evidence = EvidenceManifest.model_validate(raw["manifest"])
    assert evidence.fingerprint == raw["fingerprint"]
    first = envelope(policy, evidence, universe)
    assert first == envelope(policy, evidence, universe)
    assert first == json.loads((FOLDER / "instrument-certifications.json").read_text())
    assert not acquisition_gate(policy, evidence, universe, first)["acquisition_allowed"]
    changed = copy.deepcopy(first)
    changed["certification"]["members"].pop()
    changed["fingerprint"] = fingerprint(changed["certification"])
    assert not acquisition_gate(policy, evidence, universe, changed)["acquisition_allowed"]


def test_broker_reference_alone_never_certifies_identity() -> None:
    _, evidence, _ = inputs()
    values = evidence.model_dump()
    for source in values["evidence"]:
        source["quality"] = "BROKER_REFERENCE_ONLY"
    with pytest.raises(ValueError, match="BROKER_REFERENCE"):
        EvidenceManifest.model_validate(values)
