"""Offline replay of frozen public metadata; uses the existing policy, classifier and gate."""
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
GENERATION = Path("research/phase-12.1b/generation-1")


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def validate_coverage(plan: dict[str, Any], coverage: dict[str, Any],
                      manifest: EvidenceManifest) -> None:
    """Check report assertions before passing existing Coverage booleans to the classifier.

    Descriptors are evidence-report fields, not additions to the frozen policy taxonomy.
    This checks consistency, not the truth of a human-reviewed authoritative finding.
    """
    categories = plan["categories"] + plan["additional_report_dimensions"]
    expected = {(symbol, category) for symbol in plan["members"] for category in categories}
    rows = coverage["rows"]
    actual = [(row["symbol"], row["category"]) for row in rows]
    if len(set(actual)) != len(actual) or set(actual) != expected:
        raise ValueError("COVERAGE_MATRIX_MISSING_OR_DUPLICATE")
    refs = {e.evidence_id: e for e in manifest.evidence}
    complete: dict[str, list[bool]] = {s: [] for s in plan["members"]}
    for row in rows:
        symbol = row["symbol"]
        if row["isin"] != plan["members"][symbol] or row["series"] != plan["series"]:
            raise ValueError("COVERAGE_SECURITY_MISMATCH")
        status = row["status"]
        if status not in {"COVERED_WITH_EVENTS", "COVERED_NO_EVENTS", "INCOMPLETE", "CONFLICTING"}:
            raise ValueError("INVALID_REPORT_DESCRIPTOR")
        ids = row["evidence_ids"]
        if not ids or len(set(ids)) != len(ids) or any(
            i not in refs or symbol not in refs[i].symbols for i in ids
        ):
            raise ValueError("COVERAGE_SOURCE_MISMATCH")
        covered = status in {"COVERED_WITH_EVENTS", "COVERED_NO_EVENTS"}
        if covered:
            if (row["coverage_basis"] != "AUTHORITATIVE_FULL_PERIOD"
                    or row["covered_window"] != plan["window"] or row["gaps"]
                    or not row["supported_finding"] or not any(
                        refs[i].quality != "BROKER_REFERENCE_ONLY" for i in ids)):
                raise ValueError("FULL_PERIOD_COVERAGE_NOT_ESTABLISHED")
            if status == "COVERED_NO_EVENTS" and (
                not row["negative_evidence"] or row["event_ids"]
            ):
                raise ValueError("UNSUPPORTED_NEGATIVE_EVIDENCE")
            if status == "COVERED_WITH_EVENTS" and not row["event_ids"]:
                raise ValueError("MISSING_COVERED_EVENT")
        complete[symbol].append(covered)
    for review in manifest.reviews:
        if review.corporate_action_completeness.complete != all(complete[review.identity.symbol]):
            raise ValueError("COVERAGE_MANIFEST_DISAGREEMENT")


def replay(root: Path = ROOT) -> dict[str, Any]:
    folder = root / GENERATION
    freeze = read_json(folder / "evidence-freeze.json")
    for name, expected in freeze["input_sha256"].items():
        if Path(name).name != name:
            raise ValueError("INVALID_INPUT_NAME")
        if hashlib.sha256((folder / name).read_bytes()).hexdigest() != expected:
            raise ValueError("FROZEN_INPUT_CHANGED:" + name)
    plan_envelope = read_json(folder / "research-plan.json")
    plan = plan_envelope["plan"]
    if (fingerprint(plan) != plan_envelope["fingerprint"]
            or plan_envelope["fingerprint"] != freeze["research_plan_fingerprint"]):
        raise ValueError("RESEARCH_PLAN_CHANGED")
    parent_folder = root / "research/phase-12.1a/generation-1"
    parent = read_json(parent_folder / "evidence-manifest.json")
    policy = read_json(root / "research/phase-12.1/certification-policy-v1.json")
    universe = ResearchUniverseSpec.model_validate(
        read_json(root / "research/phase-12.0/universe.json"))
    parent_manifest = EvidenceManifest.model_validate(parent["manifest"])
    parent_cert = certify(policy, parent_manifest, universe)
    if (parent_manifest.fingerprint != parent["fingerprint"]
            or parent_cert.fingerprint != plan["parent_certification_fingerprint"]):
        raise ValueError("PARENT_CERTIFICATION_CHANGED")
    envelope = read_json(folder / "evidence-manifest.json")
    manifest = EvidenceManifest.model_validate(envelope["manifest"])
    if (manifest.generation != "phase-12.1b-g1"
            or manifest.fingerprint != envelope["fingerprint"]
            or envelope["parent_evidence_fingerprint"] != parent["fingerprint"]
            or manifest.policy_fingerprint != plan["policy_fingerprint"]
            or manifest.universe_fingerprint != plan["universe_fingerprint"]):
        raise ValueError("EVIDENCE_GENERATION_MISMATCH")
    for old, new in zip(parent_manifest.reviews, manifest.reviews, strict=True):
        for field in ("identity", "observed_isins", "observed_series", "security_identity",
                      "symbol_continuity", "series_continuity"):
            if getattr(old, field) != getattr(new, field):
                raise ValueError("PARENT_IDENTITY_CHANGED")
    validate_coverage(plan, read_json(folder / "corporate-action-coverage.json"), manifest)
    sources = {e.evidence_id: e for e in manifest.evidence}
    for source in read_json(folder / "source-inventory.json")["sources"]:
        evidence = sources[source["source_id"]]
        if (evidence.url, evidence.document_sha256) != (source["url"], source["document_sha256"]):
            raise ValueError("SOURCE_INVENTORY_MISMATCH")
    certificate = certify(policy, manifest, universe)
    proof = {"certification": certificate.model_dump(mode="json"),
             "fingerprint": certificate.fingerprint}
    boundaries = {"certification_fingerprint": certificate.fingerprint,
                  "boundaries": [b.model_dump(mode="json") for m in certificate.members
                                 for b in m.boundaries]}
    gate = acquisition_gate(policy, manifest, universe, proof)
    return {
        "instrument-certifications.json": proof,
        "continuity-boundaries.json": {"content": boundaries,
                                      "fingerprint": fingerprint(boundaries)},
        "acquisition-gate.json": {"gate": gate, "fingerprint": fingerprint(gate)},
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="Create missing derived files only")
    args = parser.parse_args()
    first, second = replay(), replay()
    if first != second:
        raise ValueError("OFFLINE_REPLAY_MISMATCH")
    for name, value in first.items():
        destination = ROOT / GENERATION / name
        if destination.exists():
            if read_json(destination) != value:
                raise ValueError("IMMUTABLE_GENERATION_CHANGED")
        elif args.write:
            destination.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n",
                                   encoding="utf-8")
        else:
            raise ValueError("MISSING_DERIVED_ARTIFACT")
    print(json.dumps({"offline_replays": 2, "identical": True,
                      "certification": first["instrument-certifications.json"]["fingerprint"],
                      "gate": first["acquisition-gate.json"]}, sort_keys=True))


if __name__ == "__main__":
    main()
