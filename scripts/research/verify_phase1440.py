"""Deterministic synthetic dataset report and read-only evidence verification."""

import json
import sys
from pathlib import Path
from typing import Any

from strategy_engine.phase1415.execution import execution_fingerprint
from strategy_engine.phase1439 import pipeline as p
from strategy_engine.phase1440 import datasets as d
from test_phase14 import tariff
from test_phase1440 import inputs

ROOT = Path(__file__).resolve().parents[2]


def report() -> dict[str, Any]:
    raw, scope, batch, mapping = inputs()
    software = p.sha(Path(d.__file__).read_bytes())
    complete = d.assess(raw, p.sha(raw), scope)
    values = json.loads(raw)
    missing = p.canonical(values[1:])
    duplicate = p.canonical(values + [values[0]])
    no_actions = scope.model_copy(update={"actions": scope.actions.model_copy(
        update={"complete": False})})
    master_raw = p.canonical(mapping.model_dump(mode="json"))
    checked = p.master(master_raw, p.sha(master_raw), scope.as_of)
    integration = d.integrate(raw, p.sha(raw), scope, software, batch, mapping, tariff())
    return {"version": "Phase1440SyntheticDatasetReport.v1", "dataset": d.dataset_report([complete]),
            "certification": integration["certification"], "integration": integration,
            "scenarios": {"missing": d.assess(missing, p.sha(missing), scope)["status"],
                          "duplicate": d.assess(duplicate, p.sha(duplicate), scope)["status"],
                          "action_coverage": d.assess(raw, p.sha(raw), no_actions)["status"],
                          "genuine": d.assess(raw, p.sha(raw), scope.model_copy(
                              update={"source_type": "GENUINE"}))["status"]},
            "canonical_mapping_count": len(checked.records), "family_combinations": 13380,
            "causal_policy": execution_fingerprint(), "retention_authority": "UNVERIFIED",
            "full_universe_end_to_end": False, "adjustment": "NO_LOCAL_ADJUSTMENT",
            "genuine_performance": None, "genuine_qualified_pairs": 0, "real_kite_requests": 0,
            "Gate_A": "NOT_AUTHORIZED", "Gate_B": "NOT_AUTHORIZED", "operational": "NO_GO"}


def verify() -> dict[str, Any]:
    path = ROOT / "research/phase-14.40/generation-1/evidence-manifest.json"
    envelope = json.loads(path.read_bytes())
    manifest = envelope["manifest"]
    p.require(p.sha(p.canonical(manifest)) == envelope["fingerprint"], "MANIFEST_HASH")
    for group in ("baseline_hashes", "artifacts", "external_receipts"):
        for name, expected in manifest[group].items():
            p.require(p.sha((ROOT / name).read_bytes()) == expected, "BYTES_CHANGED:" + name)
    result = report()
    stored = json.loads((path.parent / "dataset-report.json").read_bytes())
    p.require(stored["report"] == result and stored["fingerprint"] == p.sha(p.canonical(result)),
              "REPORT_REPLAY")
    p.require(result["causal_policy"] == d.CAUSAL, "CAUSAL_POLICY_CHANGED")
    p.require(p.sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes())
              == "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31",
              "PRESERVATION_EXCEPTION")
    return {"manifest": envelope["fingerprint"], "report": stored["fingerprint"],
            "baseline_files": len(manifest["baseline_hashes"]),
            "certification": result["certification"]["fingerprint"],
            "full_tests": manifest["full_tests"], "classification": manifest["classification"],
            "genuine_qualified_pairs": 0, "real_kite_requests": 0,
            "Gate_A": "NOT_AUTHORIZED", "Gate_B": "NOT_AUTHORIZED", "operational": "NO_GO"}


if __name__ == "__main__":
    print(p.canonical(verify() if "--verify" in sys.argv else report()).decode())
