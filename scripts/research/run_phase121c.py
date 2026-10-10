"""Offline targeted-closure replay. Existing classifier/policy/gate remain authoritative."""
import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from strategy_engine.research.continuity import (
    EvidenceManifest,
    acquisition_gate,
    certify,
    fingerprint,
)
from strategy_engine.research.universe import ResearchUniverseSpec

ROOT = Path(__file__).resolve().parents[2]
GENERATION = Path("research/phase-12.1c/generation-1")


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def validate_pending(plan: dict[str, Any], review: dict[str, Any],
                     inventory: dict[str, Any]) -> None:
    """Check review consistency, never infer an action or absence from document counts."""
    expected = {(x["symbol"], x["document_id"]): x for x in plan["pending_items"]}
    actual = [(x["symbol"], x["document_id"]) for x in review["records"]]
    if len(actual) != len(set(actual)) or set(actual) != set(expected):
        raise ValueError("PENDING_RECORD_MISSING_OR_DUPLICATE")
    sources = {s["source_id"]: s for s in inventory["sources"]}
    if len(sources) != len(inventory["sources"]):
        raise ValueError("DUPLICATE_SOURCE")
    for row in review["records"]:
        old = expected[row["symbol"], row["document_id"]]
        if row["original_url"] != old["url"] or row["study_isin"] != old["isin"]:
            raise ValueError("PENDING_IDENTITY_OR_SOURCE_CHANGED")
        if row["resolution"] not in {"RESOLVED", "UNRESOLVED"}:
            raise ValueError("INVALID_RESOLUTION")
        if row["resolution"] == "RESOLVED":
            ids = row["evidence_ids"]
            if not row["original_verified"] or not ids or any(i not in sources for i in ids):
                raise ValueError("MISSING_VERIFIED_ORIGINAL")
            if any(row["symbol"] not in sources[i]["symbols"]
                   or row["study_isin"] != sources[i]["study_isin"] for i in ids):
                raise ValueError("ORIGINAL_SECURITY_MISMATCH")
            if row["conflicts"] or row["eq_boundary"] == "UNRESOLVED":
                raise ValueError("CONFLICT_CANNOT_CLOSE")
            if row["security_scope"] in {"DEBT", "SUBSIDIARY"} and row["eq_boundary"] == "YES":
                raise ValueError("WRONG_SECURITY_BOUNDARY")
            if row["eq_boundary"] == "YES" and not row["effective_date"]:
                raise ValueError("APPOINTED_DATE_NOT_EFFECTIVE")
    for source in sources.values():
        if source["author_kind"] == "ISSUER" and source["quality"] != "ISSUER_PRIMARY":
            raise ValueError("HOST_IS_NOT_AUTHOR")
        if source["author_kind"] == "EXCHANGE" and source["quality"] != "EXCHANGE_PRIMARY":
            raise ValueError("EXCHANGE_AUTHOR_QUALITY_MISMATCH")


def replay(root: Path = ROOT) -> dict[str, Any]:
    # Load the retained coverage validator without altering its original replay contract.
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        "retained_phase121b", ROOT / "scripts/research/run_phase121b.py")
    assert spec is not None and spec.loader is not None
    retained = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(retained)
    folder = root / GENERATION
    freeze = read_json(folder / "evidence-freeze.json")
    if set(freeze["input_sha256"]) != {
        "closure-plan.json", "source-inventory.json", "pending-event-review.json",
        "corporate-action-coverage.json", "corporate-action-events.json", "evidence-manifest.json",
    }:
        raise ValueError("MISSING_FROZEN_INPUT")
    for name, expected in freeze["input_sha256"].items():
        if Path(name).name != name:
            raise ValueError("INVALID_INPUT_NAME")
        if hashlib.sha256((folder / name).read_bytes()).hexdigest() != expected:
            raise ValueError("FROZEN_INPUT_CHANGED:" + name)
    plan_envelope = read_json(folder / "closure-plan.json")
    plan = plan_envelope["plan"]
    if (fingerprint(plan) != plan_envelope["fingerprint"]
            or plan_envelope["fingerprint"] != freeze["closure_plan_fingerprint"]):
        raise ValueError("CLOSURE_PLAN_CHANGED")
    parent_folder = root / "research/phase-12.1b/generation-1"
    parent = read_json(parent_folder / "evidence-manifest.json")
    policy = read_json(root / "research/phase-12.1/certification-policy-v1.json")
    universe = ResearchUniverseSpec.model_validate(
        read_json(root / "research/phase-12.0/universe.json"))
    previous = EvidenceManifest.model_validate(parent["manifest"])
    parent_cert = certify(policy, previous, universe)
    parent_gate = acquisition_gate(policy, previous, universe, {
        "certification": parent_cert.model_dump(mode="json"),
        "fingerprint": parent_cert.fingerprint,
    })
    if (previous.fingerprint != plan["parent_evidence_fingerprint"]
            or parent_cert.fingerprint != plan["parent_certification_fingerprint"]
            or fingerprint(parent_gate) != plan["parent_gate_fingerprint"]):
        raise ValueError("PARENT_CHANGED")
    envelope = read_json(folder / "evidence-manifest.json")
    manifest = EvidenceManifest.model_validate(envelope["manifest"])
    if (manifest.generation != "phase-12.1c-g1"
            or manifest.fingerprint != envelope["fingerprint"]
            or envelope["parent_evidence_fingerprint"] != previous.fingerprint):
        raise ValueError("EVIDENCE_GENERATION_MISMATCH")
    old_cells = read_json(parent_folder / "corporate-action-coverage.json")["rows"]
    if old_cells != plan["original_coverage_cells"]:
        raise ValueError("ORIGINAL_COVERAGE_CHANGED")
    coverage = read_json(folder / "corporate-action-coverage.json")
    old_map = {(r["symbol"], r["category"]): r for r in old_cells}
    for row in coverage["rows"]:
        if row["prior_cell"] != old_map.get((row["symbol"], row["category"])):
            raise ValueError("COVERAGE_LINEAGE_CHANGED")
    retained.validate_coverage(plan, coverage, manifest)
    sources = {e.evidence_id: e for e in manifest.evidence}
    inventory = read_json(folder / "source-inventory.json")
    # Quality is canonical in Evidence; the inventory records author and host separately.
    inventory = {**inventory, "sources": [
        {**s, "quality": sources[s["source_id"]].quality} for s in inventory["sources"]
    ]}
    validate_pending(plan, read_json(folder / "pending-event-review.json"), inventory)
    for source in inventory["sources"]:
        e = sources[source["source_id"]]
        if (e.url, e.document_sha256, e.quality, e.organization) != (
            source["url"], source["document_sha256"], source["quality"], source["author"]
        ):
            raise ValueError("SOURCE_INVENTORY_MISMATCH")
    for old, new in zip(previous.reviews, manifest.reviews, strict=True):
        for field in ("identity", "observed_isins", "observed_series", "security_identity",
                      "symbol_continuity", "series_continuity"):
            if getattr(old, field) != getattr(new, field):
                raise ValueError("FROZEN_IDENTITY_CHANGED")
    certificate = certify(policy, manifest, universe)
    proof = {"certification": certificate.model_dump(mode="json"),
             "fingerprint": certificate.fingerprint}
    boundaries = {"certification_fingerprint": certificate.fingerprint,
                  "boundaries": [b.model_dump(mode="json") for m in certificate.members
                                 for b in m.boundaries]}
    gate = acquisition_gate(policy, manifest, universe, proof)
    return {"instrument-certifications.json": proof,
            "continuity-boundaries.json": {"content": boundaries,
                                          "fingerprint": fingerprint(boundaries)},
            "acquisition-gate.json": {"gate": gate, "fingerprint": fingerprint(gate)}}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    first, second = replay(), replay()
    if first != second:
        raise ValueError("OFFLINE_REPLAY_MISMATCH")
    for name, value in first.items():
        path = ROOT / GENERATION / name
        if path.exists():
            if read_json(path) != value:
                raise ValueError("IMMUTABLE_GENERATION_CHANGED")
        elif args.write:
            path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        else:
            raise ValueError("MISSING_DERIVED_ARTIFACT")
    print(json.dumps({"offline_replays": 2, "identical": True,
                      "certification": first["instrument-certifications.json"]["fingerprint"],
                      "gate": first["acquisition-gate.json"]}, sort_keys=True))


if __name__ == "__main__":
    main()
