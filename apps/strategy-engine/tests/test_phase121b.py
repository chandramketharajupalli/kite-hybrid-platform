"""Synthetic coverage assertions and offline evidence replay; no strategy evaluation."""
import copy
import importlib.util
import json
import shutil
import socket
from pathlib import Path
from typing import Any

import pytest
from test_continuity import action, envelope, inputs

from strategy_engine.research.continuity import EvidenceManifest, acquisition_gate, certify

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "phase121b", ROOT / "scripts/research/run_phase121b.py")
assert SPEC is not None and SPEC.loader is not None
RUNNER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUNNER)


def synthetic_coverage() -> tuple[dict[str, Any], dict[str, Any], EvidenceManifest]:
    _, manifest, _ = inputs()
    plan = json.loads((ROOT / RUNNER.GENERATION / "research-plan.json").read_text())["plan"]
    plan["categories"], plan["additional_report_dimensions"] = ["CASH_DIVIDEND"], []
    rows = [{
        "symbol": s, "isin": isin, "series": "EQ", "category": "CASH_DIVIDEND",
        "status": "COVERED_NO_EVENTS", "coverage_basis": "AUTHORITATIVE_FULL_PERIOD",
        "covered_window": copy.deepcopy(plan["window"]), "gaps": [],
        "supported_finding": "SYNTHETIC explicit authoritative absence, not real evidence",
        "negative_evidence": "SYNTHETIC exhaustive category and effective-date coverage",
        "evidence_ids": [s + "-actions"], "event_ids": [],
    } for s, isin in plan["members"].items()]
    return plan, {"rows": rows}, manifest


def test_explicit_complete_negative_coverage_is_representable() -> None:
    plan, coverage, manifest = synthetic_coverage()
    RUNNER.validate_coverage(plan, coverage, manifest)


@pytest.mark.parametrize("fault", [
    "empty_query", "date_gap", "wrong_isin", "wrong_series", "wrong_source", "duplicate",
    "missing_category", "missing_negative", "conflict", "incomplete", "event_without_event",
])
def test_coverage_cannot_be_promoted_by_empty_partial_or_conflicting_evidence(fault: str) -> None:
    plan, coverage, manifest = synthetic_coverage()
    row = coverage["rows"][0]
    if fault == "empty_query":
        row["coverage_basis"] = "EMPTY_QUERY"
    elif fault == "date_gap":
        row["covered_window"]["first_date"] = "2026-02-03"
    elif fault == "wrong_isin":
        row["isin"] = "INE040A01018"  # Legacy, not the active study identity.
    elif fault == "wrong_series":
        row["series"] = "BE"
    elif fault == "wrong_source":
        row["evidence_ids"] = ["SBIN-actions"]
    elif fault == "duplicate":
        coverage["rows"].append(copy.deepcopy(row))
    elif fault == "missing_category":
        coverage["rows"].pop()
    elif fault == "missing_negative":
        row["negative_evidence"] = None
    elif fault in {"conflict", "incomplete"}:
        row["status"] = "CONFLICTING" if fault == "conflict" else "INCOMPLETE"
    elif fault == "event_without_event":
        row["status"] = "COVERED_WITH_EVENTS"
    with pytest.raises(ValueError):
        RUNNER.validate_coverage(plan, coverage, manifest)


@pytest.mark.parametrize("kind", ["CASH_DIVIDEND", "STOCK_SPLIT", "BONUS_ISSUE"])
@pytest.mark.parametrize("inside", [True, False])
def test_effective_boundary_not_announcement_controls_target_window(
    kind: str, inside: bool,
) -> None:
    policy, manifest, universe = inputs()
    values = manifest.model_dump(mode="json")
    event = action(kind)
    event["announcement_date"] = "2025-12-01" if inside else "2026-06-29"
    effective = "2026-02-03" if inside else "2026-08-03"
    for key in ("ex_date", "record_date", "effective_date"):
        event[key] = effective
    values["reviews"][0]["actions"] = [event]
    evidence = EvidenceManifest.model_validate(values)
    member = certify(policy, evidence, universe).members[0]
    assert len(member.boundaries) == int(inside)
    expected = ("CERTIFIED_WITH_DIVIDEND_NOTE" if kind == "CASH_DIVIDEND"
                else "REQUIRES_SEGMENTATION") if inside else "CERTIFIED_CONTINUOUS"
    assert member.classification == expected
    if inside:
        assert member.boundaries[0].volume_continuity == (kind == "CASH_DIVIDEND")
        assert member.boundaries[0].segment_reset == (kind != "CASH_DIVIDEND")


def test_unrepresented_confirmed_boundary_denies_even_with_rehashed_proof() -> None:
    policy, manifest, universe = inputs()
    values = manifest.model_dump(mode="json")
    values["reviews"][0]["actions"] = [action()]
    evidence = EvidenceManifest.model_validate(values)
    proof = envelope(policy, evidence, universe)
    proof["certification"]["members"][0]["boundaries"] = []
    from strategy_engine.research.continuity import fingerprint
    proof["fingerprint"] = fingerprint(proof["certification"])
    assert not acquisition_gate(policy, evidence, universe, proof)["acquisition_allowed"]


def test_conflicting_and_duplicate_evidence_fail_closed() -> None:
    policy, manifest, universe = inputs()
    values = manifest.model_dump(mode="json")
    values["reviews"][0]["conflicts"] = ["SYNTHETIC contradictory effective records"]
    evidence = EvidenceManifest.model_validate(values)
    assert not acquisition_gate(policy, evidence, universe,
                                envelope(policy, evidence, universe))["acquisition_allowed"]
    values["evidence"].append(values["evidence"][0])
    with pytest.raises(ValueError, match="DUPLICATE_EVIDENCE"):
        EvidenceManifest.model_validate(values)


def test_new_generation_replays_twice_without_network_or_writes(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def deny(*args: object, **kwargs: object) -> None:
        raise AssertionError("OFFLINE_REPLAY_NETWORK_OR_WRITE")

    monkeypatch.setattr(socket, "socket", deny)
    monkeypatch.setattr(Path, "write_text", deny)
    monkeypatch.setattr(Path, "write_bytes", deny)
    first = RUNNER.replay(ROOT)
    assert first == RUNNER.replay(ROOT)
    for name, value in first.items():
        assert value == json.loads((ROOT / RUNNER.GENERATION / name).read_text())
    cert = first["instrument-certifications.json"]["certification"]
    assert all(m["security_identity"] == "CERTIFIED" for m in cert["members"])
    assert all(m["classification"] == "CORPORATE_ACTION_UNRESOLVED" for m in cert["members"])
    assert sum(len(m["boundaries"]) for m in cert["members"]) == 4
    assert not first["acquisition-gate.json"]["gate"]["acquisition_allowed"]


@pytest.mark.parametrize("name", [
    "research-plan.json", "source-inventory.json", "corporate-action-coverage.json",
    "corporate-action-events.json", "evidence-manifest.json", "announcement-review.json",
])
def test_frozen_generation_rejects_tampered_inputs(tmp_path: Path, name: str) -> None:
    destination = tmp_path / RUNNER.GENERATION
    shutil.copytree(ROOT / RUNNER.GENERATION, destination)
    with (destination / name).open("a", encoding="utf-8") as stream:
        stream.write(" ")
    with pytest.raises(ValueError, match="FROZEN_INPUT_CHANGED"):
        RUNNER.replay(tmp_path)
