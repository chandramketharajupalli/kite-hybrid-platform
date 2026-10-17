"""Deterministic candidate compatibility evidence; no adoption or acquisition."""

import json
import sys
from pathlib import Path
from typing import Any

from test_phase1441 import candidate, transition
from verify_phase1440 import report as legacy_report

from strategy_engine.phase1415.execution import execution_fingerprint
from strategy_engine.phase1439 import pipeline as p
from strategy_engine.phase1440.datasets import CAUSAL
from strategy_engine.phase1441 import compatibility as c

ROOT = Path(__file__).resolve().parents[2]


def report() -> dict[str, Any]:
    value, batch = candidate()
    checked = c.legacy_check(value, batch.series, batch.series.as_of)
    legacy = legacy_report()
    sealed_path = ROOT / "research/phase-14.40/generation-1/dataset-report.json"
    sealed = json.loads(sealed_path.read_bytes())
    p.require(legacy == sealed["report"], "LEGACY_BEHAVIOR_CHANGED")
    p.require(execution_fingerprint() == CAUSAL, "CAUSAL_CHANGED")
    return {"version": "Phase1441CompatibilityReport.v1", "matrix": c.matrix(),
            "candidate": c.assess(value, batch.series.as_of), "legacy_series": checked,
            "token_transition": c.timeline(transition(), batch.series.as_of),
            "symbol_transition": c.timeline(transition(True), batch.series.as_of),
            "comparability_family": c.family(value), "legacy_report": sealed["fingerprint"],
            "legacy_signal_and_accounting_report_unchanged": True,
            "causal": CAUSAL, "scope": "REPRESENTATIVE_ONE_SYMBOL_SIX_STRATEGIES",
            "full_universe_end_to_end": False, "canonical_symbols": 1115,
            "research_combinations": 13380, "owner_methodology_decision": "NOT_AUTHORIZED",
            "genuine_performance": None, "genuine_qualified_pairs": 0,
            "real_kite_requests": 0, "Gate_A": "NOT_AUTHORIZED", "Gate_B": "NOT_AUTHORIZED",
            "operational": "NO_GO"}


def verify() -> dict[str, Any]:
    directory = ROOT / "research/phase-14.41/generation-1"
    envelope = json.loads((directory / "evidence-manifest.json").read_bytes())
    manifest = envelope["manifest"]
    p.require(p.sha(p.canonical(manifest)) == envelope["fingerprint"], "MANIFEST_HASH")
    for group in ("baseline_hashes", "artifacts", "external_receipts"):
        for name, expected in manifest[group].items():
            p.require(p.sha((ROOT / name).read_bytes()) == expected, "BYTES_CHANGED:" + name)
    stored = json.loads((directory / "compatibility-report.json").read_bytes())
    result = report()
    p.require(result == stored["report"] and p.sha(p.canonical(result)) == stored["fingerprint"],
              "REPLAY_CHANGED")
    p.require(p.sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes())
              == "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31",
              "EXCEPTION_CHANGED")
    return {"manifest": envelope["fingerprint"], "report": stored["fingerprint"],
            "classification": manifest["classification"], "baseline_files": 1575,
            "full_tests": manifest["full_tests"], "candidate": "PROPOSED_NOT_ADOPTED",
            "migration": "NOT_AUTHORIZED", "genuine_qualified_pairs": 0,
            "real_kite_requests": 0, "Gate_A": "NOT_AUTHORIZED", "Gate_B": "NOT_AUTHORIZED",
            "operational": "NO_GO"}


if __name__ == "__main__":
    print(p.canonical(verify() if "--verify" in sys.argv else report()).decode())
