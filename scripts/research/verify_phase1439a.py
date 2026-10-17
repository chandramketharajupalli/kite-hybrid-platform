"""Read-only replay of sealed validation receipts and existing synthetic reports."""

import hashlib
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

from strategy_engine.phase1415.execution import execution_fingerprint
from strategy_engine.phase1415.strategies import registry
from strategy_engine.phase1428.resources import load
from verify_phase1439 import report


def canonical(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def replay(root: Path, custody: Path) -> dict[str, Any]:
    path = root / "research/phase-14.39a/generation-1/evidence-manifest.json"
    envelope = json.loads(path.read_bytes())
    manifest = envelope["manifest"]
    require(sha(canonical(manifest)) == envelope["fingerprint"], "MANIFEST_FINGERPRINT")
    for name, expected in manifest["artifacts"].items():
        require(sha((root / name).read_bytes()) == expected, "ARTIFACT_BYTES:" + name)
    for name, expected in manifest["tracked_hashes"].items():
        require(sha((root / name).read_bytes()) == expected, "BASELINE_BYTES:" + name)
    for name, expected in manifest["external_receipts"].items():
        require(sha(Path(name).read_bytes()) == expected, "EXTERNAL_RECEIPT:" + name)
    exception = manifest["preservation_exception"]
    require(sha((root / exception["path"]).read_bytes()) == exception["sha256"], "EXCEPTION")
    collection = json.loads((custody / "collection.inventory.json").read_bytes())
    executed = json.loads((custody / "full.inventory.json").read_bytes())
    require(collection["nodes"] == executed["nodes"], "COLLECTION_EXECUTION_SCOPE")
    command = json.loads((custody / "full.command.json").read_bytes())
    require(command["exit_code"] == 0 and not command["timeout"]
            and command["seconds"] <= 1800, "FULL_SUITE_NOT_PASSED")
    cases = ET.parse(custody / "full.xml").findall(".//testcase")
    require(len(cases) == collection["count"] and all(
        c.find("failure") is None and c.find("error") is None and c.find("skipped") is None
        for c in cases), "JUNIT_INCOMPLETE")
    events = [json.loads(line) for line in (custody / "full.events.jsonl").read_text().splitlines()]
    phases = [e for e in events if e["kind"] == "report"]
    calls = [e["nodeid"] for e in phases if e["when"] == "call"]
    require(len(calls) == len(set(calls)) == collection["count"]
            and set(calls) == {n["nodeid"] for n in collection["nodes"]}
            and all(e["outcome"] == "passed" for e in phases)
            and not any(e["kind"] == "deselected" for e in events), "EXECUTION_INCOMPLETE")
    require(manifest["classification"] == "PHASE_14_39A_PARTIAL"
            and json.loads((custody / "ruff.command.json").read_bytes())["exit_code"] == 1
            and json.loads((custody / "mypy.command.json").read_bytes())["exit_code"] == 0,
            "CERTIFICATION_STATUS_CHANGED")
    prior_path = root / "research/phase-14.39/generation-1/pipeline-report.json"
    prior = json.loads(prior_path.read_bytes())
    result = report()
    require(canonical(result) == canonical(prior["report"]), "SYNTHETIC_REPORT_CHANGED")
    require(sha(canonical(result)) == prior["fingerprint"], "SYNTHETIC_FINGERPRINT")
    causal = execution_fingerprint()
    require(causal == "2078a10ecad7df0faef638e3e6e37eb4808c4a8c33af5fb81883b427d0ed5925",
            "CAUSAL_POLICY_CHANGED")
    resources = load()
    return dict(version="Phase1439AReplay.v1", manifest_fingerprint=envelope["fingerprint"],
                classification=manifest["classification"],
                tracked_preserved=len(manifest["tracked_hashes"]),
                collected=collection["count"], executed_inventory=executed["count"],
                pipeline_fingerprint=prior["fingerprint"],
                research_fingerprint=result["research_fingerprint"],
                universe=resources["manifest"]["resources"]["universe"]["sha256"],
                registry=sha(canonical([d.model_dump(mode="json") for d in registry()])),
                calendar=sha(canonical(result["calendar"])), causal=causal,
                historical_timeout="FULL_SUITE_TIMEOUT", genuine_qualified=0,
                real_kite_requests=0, Gate_A="NOT_AUTHORIZED", Gate_B="NOT_AUTHORIZED",
                operational="NO_GO")


if __name__ == "__main__":
    print(canonical(replay(Path(sys.argv[1]), Path(sys.argv[2]))).decode())
