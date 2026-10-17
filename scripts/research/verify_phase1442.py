"""Read-only governance assessment and deterministic custody verification."""

import json
import sys
from pathlib import Path
from typing import Any

from verify_phase1441 import report as compatibility_report

from strategy_engine.phase1439 import pipeline as p
from strategy_engine.phase1441 import compatibility as c
from strategy_engine.phase1442 import governance as g

ROOT = Path(__file__).resolve().parents[2]
BASELINE = "151bcebd24f8da39609fa41f30a422f1d0bd0cc8"
DIRECTORY = ROOT / "research/phase-14.42/generation-1"


def proposal() -> g.Proposal:
    def document(name: str) -> str:
        return p.sha((ROOT / f"docs/operations/phase-14.42-{name}.md").read_bytes())

    candidate = p.sha(
        p.canonical(
            {
                "schema": c.Candidate.model_json_schema(),
                "source": p.sha(Path(c.__file__).read_bytes()),
            }
        )
    )
    return g.Proposal(
        baseline=BASELINE,
        candidate=candidate,
        adjustment=document("adjustment-decision"),
        identity=document("identity-decision"),
        comparability=document("comparability-decision"),
        rollback=document("rollback-policy"),
        permitted_files=(),
    )


def packet() -> dict[str, Any]:
    proposed = proposal()
    return {
        "proposal": proposed.model_dump(mode="json"),
        "approval": g.pending(proposed).model_dump(mode="json"),
        "execution": "NOT_AUTHORIZED",
    }


def report() -> dict[str, Any]:
    legacy = compatibility_report()
    path = ROOT / "research/phase-14.41/generation-1/compatibility-report.json"
    sealed = json.loads(path.read_bytes())
    p.require(legacy == sealed["report"], "LEGACY_REPORT_CHANGED")
    result = g.assessment()
    result.update(
        {
            "baseline": BASELINE,
            "proposal_fingerprint": p.fingerprint(proposal()),
            "packet_fingerprint": p.sha(p.canonical(packet())),
            "legacy_report": sealed["fingerprint"],
            "legacy_invariance": True,
            "canonical_symbols": 1115,
            "research_combinations": 13380,
            "causal": legacy["causal"],
            "full_universe_end_to_end": False,
            "implementation_inventory": "UNRESOLVED_REQUIRES_SEPARATE_SCOPE",
            "owner_authentication": "NOT_PERFORMED",
        }
    )
    return result


def verify() -> dict[str, Any]:
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    manifest = envelope["manifest"]
    p.require(p.sha(p.canonical(manifest)) == envelope["fingerprint"], "MANIFEST_HASH")
    for group in ("baseline_hashes", "artifacts", "external_receipts"):
        for name, expected in manifest[group].items():
            p.require(p.sha((ROOT / name).read_bytes()) == expected, "BYTES_CHANGED:" + name)
    stored = json.loads((DIRECTORY / "governance-assessment.json").read_bytes())
    p.require(report() == stored["assessment"], "REPORT_REPLAY")
    p.require(p.sha(p.canonical(stored["assessment"])) == stored["fingerprint"], "REPORT_HASH")
    approved = json.loads((DIRECTORY / "migration-approval.json").read_bytes())
    p.require(
        approved["packet"] == packet() and approved["fingerprint"] == p.sha(p.canonical(packet())),
        "PACKET_REPLAY",
    )
    p.require(
        p.sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes())
        == "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31",
        "PRESERVATION_EXCEPTION",
    )
    return {
        "manifest": envelope["fingerprint"],
        "assessment": stored["fingerprint"],
        "packet": approved["fingerprint"],
        "classification": manifest["classification"],
        "baseline_files": len(manifest["baseline_hashes"]),
        "full_tests": manifest["full_tests"],
        "migration": "NOT_AUTHORIZED",
        "genuine_qualified_pairs": 0,
        "real_kite_requests": 0,
        "Gate_A": "NOT_AUTHORIZED",
        "Gate_B": "NOT_AUTHORIZED",
        "operational": "NO_GO",
    }


if __name__ == "__main__":
    print(p.canonical(verify() if "--verify" in sys.argv else report()).decode())
