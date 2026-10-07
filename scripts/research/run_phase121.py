"""Offline Phase 12.1 replay. Reads only bounded metadata; never imports a study runner."""
import argparse
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


def replay(root: Path = ROOT) -> dict[str, Any]:
    folder = root / "research/phase-12.1"
    policy = json.loads((folder / "certification-policy-v1.json").read_text(encoding="utf-8"))
    evidence = json.loads((folder / "evidence-manifest.json").read_text(encoding="utf-8"))
    manifest = EvidenceManifest.model_validate(evidence["manifest"])
    if manifest.fingerprint != evidence["fingerprint"]:
        raise ValueError("EVIDENCE_FINGERPRINT_MISMATCH")
    universe = ResearchUniverseSpec.model_validate_json(
        (root / "research/phase-12.0/universe.json").read_text(encoding="utf-8"))
    certificate = certify(policy, manifest, universe)
    envelope = dict(certification=certificate.model_dump(mode="json"),
                    fingerprint=certificate.fingerprint)
    boundaries = dict(certification_fingerprint=certificate.fingerprint,
                      boundaries=[b.model_dump(mode="json") for m in certificate.members
                                  for b in m.boundaries])
    return {
        "instrument-certifications.json": envelope,
        "continuity-boundaries.json": dict(content=boundaries, fingerprint=fingerprint(boundaries)),
        "acquisition-gate.json": acquisition_gate(policy, manifest, universe, envelope),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true",
                        help="Write new/identical derived artifacts")
    args = parser.parse_args()
    first, second = replay(), replay()
    if first != second:
        raise ValueError("OFFLINE_REPLAY_MISMATCH")
    for name, value in first.items():
        destination = ROOT / "research/phase-12.1" / name
        if destination.exists():
            if json.loads(destination.read_text(encoding="utf-8")) != value:
                raise ValueError("RETAINED_CERTIFICATION_CHANGED_NEW_GENERATION_REQUIRED")
        elif args.write:
            destination.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n",
                                   encoding="utf-8")
        else:
            raise ValueError("MISSING_DERIVED_ARTIFACT")
    print(json.dumps(dict(offline_replays=2, identical=True,
                          certification=first["instrument-certifications.json"]["fingerprint"],
                          gate=first["acquisition-gate.json"]), sort_keys=True))


if __name__ == "__main__":
    main()
