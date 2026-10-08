"""Offline replay of the separately frozen Phase 12.1A evidence generation."""
import argparse
import csv
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
GENERATION = Path("research/phase-12.1a/generation-1")


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def replay(root: Path = ROOT) -> dict[str, Any]:
    folder = root / GENERATION
    freeze = read_json(folder / "evidence-freeze.json")
    for name, expected in freeze["input_sha256"].items():
        if Path(name).name != name:
            raise ValueError("INVALID_INPUT_NAME")
        if hashlib.sha256((folder / name).read_bytes()).hexdigest() != expected:
            raise ValueError("FROZEN_INPUT_CHANGED:" + name)
    parent = read_json(root / "research/phase-12.1/evidence-manifest.json")
    policy = read_json(root / "research/phase-12.1/certification-policy-v1.json")
    envelope = read_json(folder / "evidence-manifest.json")
    manifest = EvidenceManifest.model_validate(envelope["manifest"])
    if manifest.generation != "phase-12.1a-g1":
        raise ValueError("WRONG_GENERATION")
    if manifest.fingerprint != envelope["fingerprint"]:
        raise ValueError("EVIDENCE_FINGERPRINT_MISMATCH")
    if (parent["fingerprint"] != envelope["parent_evidence_fingerprint"]
            or EvidenceManifest.model_validate(parent["manifest"]).fingerprint
            != parent["fingerprint"]):
        raise ValueError("PARENT_EVIDENCE_CHANGED")
    universe = ResearchUniverseSpec.model_validate(
        read_json(root / "research/phase-12.0/universe.json"))
    plan = read_json(root / "research/phase-12.0/acquisition-plan.json")
    dates = [c["date"] for c in plan["members"][0]["chunks"]]
    inventory = read_json(folder / "source-inventory.json")
    daily = inventory["daily_security_masters"]
    if [s["date"] for s in daily] != dates:
        raise ValueError("REFERENCE_DATE_COVERAGE_MISMATCH")
    sources = {s.evidence_id: s for s in manifest.evidence}
    for item in daily:
        source = sources[item["evidence_id"]]
        if (source.document_sha256, source.url) != (item["document_sha256"], item["url"]):
            raise ValueError("REFERENCE_SOURCE_MISMATCH")
    with (folder / "security-reference.csv").open(encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream))
    actual = [(r["date"], r["TckrSymb"], r["SctySrs"], r["FinInstrmId"]) for r in rows]
    if len(set(actual)) != len(actual) or actual != sorted(actual):
        raise ValueError("REFERENCE_ORDER_OR_DUPLICATE")
    for review in manifest.reviews:
        symbol = review.identity.symbol
        observations = [r for r in rows if r["TckrSymb"] == symbol and r["SctySrs"] == "EQ"]
        if [r["date"] for r in observations] != dates:
            raise ValueError("INSTRUMENT_DATE_COVERAGE_MISMATCH:" + symbol)
        keys = ("FinInstrmId", "ISIN", "ParVal", "NewBrdLotQty", "SctyTpFlg")
        if any(len({r[k] for r in observations}) != 1 for k in keys):
            raise ValueError("IDENTITY_TRANSITION_REQUIRES_REVIEW:" + symbol)
        if any(r["DelFlg"] != "N" or r["ElgbltyNrmlMkt"] != "1"
               or r["PrtdToTrad"] != "0" for r in observations):
            raise ValueError("INELIGIBLE_OR_WRONG_VENUE_REFERENCE:" + symbol)
        if observations[0]["ISIN"] not in review.observed_isins:
            raise ValueError("REFERENCE_ISIN_NOT_REVIEWED:" + symbol)
    certificate = certify(policy, manifest, universe)
    proof = {"certification": certificate.model_dump(mode="json"),
             "fingerprint": certificate.fingerprint}
    boundaries = {"certification_fingerprint": certificate.fingerprint,
                  "boundaries": [b.model_dump(mode="json") for m in certificate.members
                                 for b in m.boundaries]}
    return {
        "instrument-certifications.json": proof,
        "continuity-boundaries.json": {"content": boundaries,
                                      "fingerprint": fingerprint(boundaries)},
        "acquisition-gate.json": acquisition_gate(policy, manifest, universe, proof),
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
                raise ValueError("NEW_GENERATION_REQUIRED:" + name)
        elif args.write:
            with destination.open("x", encoding="utf-8", newline="\n") as stream:
                stream.write(json.dumps(value, indent=2, sort_keys=True) + "\n")
        else:
            raise ValueError("MISSING_DERIVED_ARTIFACT:" + name)
    print(json.dumps({"offline_replays": 2, "identical": True,
                      "certification": first["instrument-certifications.json"]["fingerprint"],
                      "gate": first["acquisition-gate.json"]}, sort_keys=True))


if __name__ == "__main__":
    main()
