"""Deterministic owner-decision preparation evidence; no stage activation."""

import json
import sys
from pathlib import Path
from typing import Any

from verify_phase1442 import report as governance_report

from strategy_engine.phase1439 import pipeline as p
from strategy_engine.phase1441 import compatibility as c
from strategy_engine.phase1443 import decisions as d

ROOT = Path(__file__).resolve().parents[2]
BASELINE = "05ec95261c226662da36a47f445933bbb163baaf"
DIRECTORY = ROOT / "research/phase-14.43/generation-1"


def document(name: str) -> str:
    return p.sha((ROOT / f"docs/operations/phase-14.43-{name}.md").read_bytes())


def make_scope(prohibited: dict[str, str]) -> d.Scope:
    ns = "apps/strategy-engine/src/strategy_engine/research_v2_candidate/"
    tests = "apps/strategy-engine/tests/"
    inventory = (
        (ns + "__init__.py", ns + "series.py", tests + "test_research_v2_contract.py"),
        (ns + "identity.py", tests + "test_research_v2_identity.py"),
        (ns + "adjustment.py", tests + "test_research_v2_adjustment.py"),
        (ns + "adapter.py", tests + "test_research_v2_adapter.py"),
        (tests + "test_research_v2_comparability.py", "scripts/research/verify_research_v2.py"),
        ("research/methodology-v2/activation-decision.json",),
    )
    names = (
        "CONTRACT_ONLY",
        "IDENTITY_TIMELINE",
        "ADJUSTMENT_METHOD",
        "VERSIONED_ADAPTER",
        "COMPARABILITY_AND_REQUALIFICATION",
        "SEPARATE_ACTIVATION_REQUEST",
    )
    protected = p.sha(p.canonical(prohibited))
    stages = tuple(
        d.Stage(
            number=i,
            name=name,
            permitted=tuple(d.FileCommitment(path=x) for x in paths),
            prohibited_fingerprint=protected,
            interfaces=(name, "NO_LEGACY_MUTATION", "NO_ACTIVE_ENTRYPOINT"),
            tests=("FULL_SUITE", "LEGACY_INVARIANCE", "CAUSALITY", name + "_REGRESSIONS"),
            evidence=(
                "EXACT_CONTENT_HASHES",
                "OWNER_AUTH",
                "JUNIT_LOGS",
                "TWO_REPLAYS",
                "WORKER_RESOURCES",
                "PRESERVATION",
                name + "_RECEIPT",
            ),
            budget_seconds=1800,
            rollback=document("rollback"),
            requires_stage=i - 1,
        )
        for i, (name, paths) in enumerate(zip(names, inventory, strict=True), 1)
    )
    scope = d.Scope(baseline=BASELINE, prohibited_files=prohibited, stages=stages)
    d.scope_check(scope)
    return scope


def report() -> dict[str, Any]:
    envelope = json.loads((DIRECTORY / "migration-scope.json").read_bytes())
    scope = d.Scope.model_validate(envelope["scope"])
    p.require(d.scope_check(scope) == envelope["fingerprint"], "SCOPE_HASH")
    p.require(make_scope(scope.prohibited_files) == scope, "SCOPE_CONTRACT_CHANGED")
    candidate = p.sha(
        p.canonical(
            {
                "schema": c.Candidate.model_json_schema(),
                "source": p.sha(Path(c.__file__).read_bytes()),
            }
        )
    )
    bindings = d.Bindings(
        baseline=BASELINE,
        candidate=candidate,
        corporate_action=document("adjustment-options"),
        identity=document("identity-options"),
        comparability=document("comparability"),
        scope=p.fingerprint(scope),
        rollback=document("rollback"),
    )
    legacy = governance_report()
    old = json.loads(
        (ROOT / "research/phase-14.42/generation-1/governance-assessment.json").read_bytes()
    )
    p.require(old["assessment"] == legacy, "LEGACY_REPORT_CHANGED")
    return {
        "version": "Phase1443OwnerDecisionPreparation.v1",
        "proposal": bindings.model_dump(mode="json"),
        "decision": d.pending(bindings).model_dump(mode="json"),
        "scope_status": "UNAPPROVED",
        "scope_content_hashes": "UNRESOLVED",
        "corporate_action_selection": None,
        "identity_selection": None,
        "identity_recommendation": "B_PLUS_C_WITH_D_FAIL_CLOSED_LEGACY_A_PRESERVED",
        "candidate": "PROPOSED_NOT_ADOPTED",
        "rollback_authority": None,
        "comparability": "SEPARATE_FAMILIES_INDEPENDENT_REQUALIFICATION",
        "legacy_report": old["fingerprint"],
        "legacy_invariance": True,
        "canonical_symbols": 1115,
        "research_combinations": 13380,
        "causal": legacy["causal"],
        "full_universe_end_to_end": False,
        "owner_authentication": "NOT_SUPPLIED_SYNTHETIC_TESTS_ONLY",
        "migration": "NOT_AUTHORIZED",
        "genuine_performance": None,
        "genuine_qualified_pairs": 0,
        "real_kite_requests": 0,
        "Gate_A": "NOT_AUTHORIZED",
        "Gate_B": "NOT_AUTHORIZED",
        "operational": "NO_GO",
    }


def verify() -> dict[str, Any]:
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    manifest = envelope["manifest"]
    p.require(p.sha(p.canonical(manifest)) == envelope["fingerprint"], "MANIFEST_HASH")
    for group in ("baseline_hashes", "artifacts", "external_receipts"):
        for name, expected in manifest[group].items():
            p.require(p.sha((ROOT / name).read_bytes()) == expected, "BYTES_CHANGED:" + name)
    stored = json.loads((DIRECTORY / "owner-decision.json").read_bytes())
    p.require(
        report() == stored["packet"]
        and p.sha(p.canonical(stored["packet"])) == stored["fingerprint"],
        "DECISION_REPLAY",
    )
    scope = json.loads((DIRECTORY / "migration-scope.json").read_bytes())
    p.require(scope["scope"]["prohibited_files"] == manifest["baseline_hashes"], "PROTECTED_SCOPE")
    p.require(
        p.sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes())
        == "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31",
        "PRESERVATION_EXCEPTION",
    )
    return {
        "manifest": envelope["fingerprint"],
        "packet": stored["fingerprint"],
        "scope": scope["fingerprint"],
        "classification": manifest["classification"],
        "baseline_files": len(manifest["baseline_hashes"]),
        "full_tests": manifest["full_tests"],
        "owner": "PENDING_OWNER_DECISION",
        "migration": "NOT_AUTHORIZED",
        "genuine_qualified_pairs": 0,
        "real_kite_requests": 0,
        "Gate_A": "NOT_AUTHORIZED",
        "Gate_B": "NOT_AUTHORIZED",
        "operational": "NO_GO",
    }


if __name__ == "__main__":
    print(p.canonical(verify() if "--verify" in sys.argv else report()).decode())
