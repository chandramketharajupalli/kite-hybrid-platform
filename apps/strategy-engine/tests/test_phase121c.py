"""Targeted original-review integrity; synthetic metadata only, no provider calls."""
import copy
import importlib.util
import json
import shutil
import socket
from pathlib import Path
from typing import Any

import pytest

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "phase121c", ROOT / "scripts/research/run_phase121c.py")
assert SPEC is not None and SPEC.loader is not None
RUNNER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUNNER)


def fixture() -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    plan = {"pending_items": [{"symbol": "SBIN", "document_id": "synthetic",
                              "url": "https://example.test/original", "isin": "INE062A01020"}]}
    review = {"records": [{"symbol": "SBIN", "document_id": "synthetic",
                          "original_url": "https://example.test/original",
                          "study_isin": "INE062A01020", "resolution": "RESOLVED",
                          "original_verified": True, "evidence_ids": ["original"],
                          "conflicts": [], "eq_boundary": "NO", "security_scope": "DEBT",
                          "appointed_date": "2026-04-01", "effective_date": None}]}
    inventory = {"sources": [{"source_id": "original", "author_kind": "ISSUER",
                              "quality": "ISSUER_PRIMARY", "host": "nsearchives.nseindia.com",
                              "symbols": ["SBIN"], "study_isin": "INE062A01020"}]}
    return plan, review, inventory


def test_issuer_hosted_by_exchange_is_still_issuer_evidence() -> None:
    RUNNER.validate_pending(*fixture())


@pytest.mark.parametrize("fault", [
    "missing_original", "unverified_original", "missing_record", "duplicate_record",
    "legacy_isin", "wrong_original", "wrong_issuer", "wrong_source_isin", "host_as_author",
    "exchange_mislabeled", "debt_as_eq", "subsidiary_as_eq", "appointed_only",
    "conflicting_correction", "duplicate_source",
])
def test_original_review_cannot_silently_close_invalid_evidence(fault: str) -> None:
    plan, review, inventory = fixture()
    row, source = review["records"][0], inventory["sources"][0]
    if fault == "missing_original":
        row["evidence_ids"] = []
    elif fault == "unverified_original":
        row["original_verified"] = False
    elif fault == "missing_record":
        review["records"] = []
    elif fault == "duplicate_record":
        review["records"].append(copy.deepcopy(row))
    elif fault == "legacy_isin":
        row["study_isin"] = "INE062A01012"
    elif fault == "wrong_original":
        row["original_url"] = "https://example.test/different"
    elif fault == "wrong_issuer":
        source["symbols"] = ["LT"]
    elif fault == "wrong_source_isin":
        source["study_isin"] = "INE062A01012"
    elif fault == "host_as_author":
        source["quality"] = "EXCHANGE_PRIMARY"
    elif fault == "exchange_mislabeled":
        source["author_kind"] = "EXCHANGE"
    elif fault in {"debt_as_eq", "subsidiary_as_eq", "appointed_only"}:
        row["eq_boundary"] = "YES"
        row["security_scope"] = {"debt_as_eq": "DEBT", "subsidiary_as_eq": "SUBSIDIARY",
                                 "appointed_only": "EQ"}[fault]
    elif fault == "conflicting_correction":
        row["conflicts"] = ["SYNTHETIC original/correction disagree"]
    elif fault == "duplicate_source":
        inventory["sources"].append(copy.deepcopy(source))
    with pytest.raises(ValueError):
        RUNNER.validate_pending(plan, review, inventory)


def test_incomplete_original_is_retained_without_inventing_a_finding() -> None:
    plan, review, inventory = fixture()
    review["records"][0].update(resolution="UNRESOLVED", original_verified=False,
                                evidence_ids=[], eq_boundary="UNRESOLVED")
    RUNNER.validate_pending(plan, review, inventory)


def test_real_generation_replays_offline_with_all_cells_and_boundaries(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def no_network(*args: Any, **kwargs: Any) -> Any:
        raise AssertionError("Offline replay attempted network")
    monkeypatch.setattr(socket, "socket", no_network)
    first = RUNNER.replay()
    assert first == RUNNER.replay()
    folder = ROOT / RUNNER.GENERATION
    for name, value in first.items():
        assert json.loads((folder / name).read_text(encoding="utf-8")) == value
    cells = json.loads((folder / "corporate-action-coverage.json").read_text(encoding="utf-8"))
    assert len(cells["rows"]) == 80
    assert all(row["prior_cell"]["symbol"] == row["symbol"] for row in cells["rows"])
    boundaries = first["continuity-boundaries.json"]["content"]["boundaries"]
    assert len(boundaries) == 4
    assert all(b["volume_continuity"] and not b["price_continuity"] for b in boundaries)
    assert not first["acquisition-gate.json"]["gate"]["acquisition_allowed"]
    # The August ICICI dividend is retained as evidence but outside study boundaries.
    members = first["instrument-certifications.json"]["certification"]["members"]
    icici = next(m for m in members if m["identity"]["symbol"] == "ICICIBANK")
    assert icici["boundaries"] == []
    manifest = json.loads((folder / "evidence-manifest.json").read_text(encoding="utf-8"))
    review = next(r for r in manifest["manifest"]["reviews"]
                  if r["identity"]["symbol"] == "ICICIBANK")
    assert any(a["ex_date"] == "2026-08-03" for a in review["actions"])


@pytest.mark.parametrize("name", [
    "pending-event-review.json", "corporate-action-coverage.json", "source-inventory.json",
    "evidence-manifest.json", "corporate-action-events.json", "closure-plan.json",
])
def test_tampered_frozen_input_stops_before_classification(tmp_path: Path, name: str) -> None:
    folder = tmp_path / RUNNER.GENERATION
    shutil.copytree(ROOT / RUNNER.GENERATION, folder)
    with (folder / name).open("a", encoding="utf-8") as target:
        target.write(" ")
    with pytest.raises(ValueError, match="FROZEN_INPUT_CHANGED"):
        RUNNER.replay(tmp_path)


def test_replay_preserves_prior_generations() -> None:
    for phase in ("121", "121a", "121b"):
        spec = importlib.util.spec_from_file_location(
            "prior_" + phase, ROOT / f"scripts/research/run_phase{phase}.py")
        assert spec is not None and spec.loader is not None
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        assert module.replay() == module.replay()
