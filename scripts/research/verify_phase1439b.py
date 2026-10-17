"""Read-only verification of Phase 14.39B receipts and historical evidence."""

import ast
import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any
from unittest.mock import patch

from verify_phase1439a import replay as historical_replay


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def canonical(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def read(path: Path) -> Any:
    return json.loads(path.read_bytes())


def require(ok: bool, reason: str) -> None:
    if not ok:
        raise ValueError(reason)


def semantic(raw: bytes) -> tuple[list[str], str]:
    tree = ast.parse(raw)
    imports = sorted(ast.dump(n) for n in tree.body if isinstance(n, (ast.Import, ast.ImportFrom)))
    tree.body = [n for n in tree.body if not isinstance(n, (ast.Import, ast.ImportFrom))]
    return imports, ast.dump(tree)


def replay(root: Path, custody: Path) -> dict[str, Any]:
    envelope = read(root / "research/phase-14.39b/generation-1/evidence-manifest.json")
    manifest = envelope["manifest"]
    require(sha(canonical(manifest)) == envelope["fingerprint"], "MANIFEST_HASH")
    for group in ("artifacts", "external_receipts"):
        for name, digest in manifest[group].items():
            require(sha((root / name).read_bytes()) == digest, "ARTIFACT_HASH:" + name)
    changed = "apps/strategy-engine/tests/test_phase1439.py"
    for name, digest in manifest["baseline_hashes"].items():
        expected = manifest["corrected_test_sha256"] if name == changed else digest
        require(sha((root / name).read_bytes()) == expected, "BASELINE_HASH:" + name)
    original = subprocess.check_output(["git", "show", manifest["baseline"] + ":" + changed],
                                       cwd=root)
    require(sha(original) == manifest["baseline_hashes"][changed], "ORIGINAL_TEST")
    require(semantic(original) == semantic((root / changed).read_bytes()), "TEST_SEMANTICS")
    # Historical verifier sees its own sealed baseline for the one authorized correction.
    # No historical file is rewritten; current pipeline code executes normally.
    raw_reader = Path.read_bytes
    target = (root / changed).resolve()
    with patch.object(Path, "read_bytes", lambda p: original if p.resolve() == target
                      else raw_reader(p)):
        prior = historical_replay(root, Path(manifest["prior_custody"]))
    for label in ("collection", "focused"):
        before = read(custody / (label + "-before.inventory.json"))
        after = read(custody / (label + "-after.inventory.json"))
        require(before["nodes"] == after["nodes"], "COLLECTION_CHANGED:" + label)
    inventory = read(custody / "full.inventory.json")
    require(inventory["nodes"] == read(custody / "collection-after.inventory.json")["nodes"],
            "FULL_SCOPE")
    cases = ET.parse(custody / "full.xml").findall(".//testcase")
    require(len(cases) == inventory["count"] == 1231, "FULL_COUNT")
    require(all(all(c.find(k) is None for k in ("failure", "error", "skipped"))
                for c in cases), "FULL_FAILURE")
    events = [json.loads(line) for line in (custody / "full.events.jsonl").read_text().splitlines()]
    calls = [e["nodeid"] for e in events if e["kind"] == "report" and e["when"] == "call"]
    require(len(calls) == len(set(calls)) == 1231
            and set(calls) == {n["nodeid"] for n in inventory["nodes"]}, "EVENT_SCOPE")
    require(all(e["outcome"] == "passed" for e in events if e["kind"] == "report")
            and not any(e["kind"] == "deselected" for e in events), "EVENT_FAILURE")
    for label in ("ruff-after", "mypy-after", "focused-before", "focused-after", "full"):
        receipt = read(custody / (label + ".command.json"))
        require(receipt["exit_code"] == 0 and not receipt["timeout"], "COMMAND:" + label)
    require(read(custody / "ruff-before.command.json")["exit_code"] == 1, "RUFF_HISTORY")
    receipt = read(custody / "full.command.json")
    require(receipt["seconds"] <= 1800, "FULL_BUDGET")
    identity = read(custody / "full.worker.json")
    workers = [p for p in receipt["processes"] if p["pid"] == identity["pid"]]
    require(len(workers) == 1, "WORKER_IDENTITY")
    worker = workers[0]
    require(worker["parent_pid"] == identity["parent_pid"]
            and worker["pid"] != receipt["launcher_pid"]
            and worker["creation_filetime"] > 0 and worker["exit_filetime"] > 0
            and worker["cpu_seconds"] > 0 and worker["peak_working_set_bytes"] > 0
            and worker["exit_code"] == 0, "WORKER_METRICS")
    created = worker["creation_filetime"] / 10_000_000 - 11_644_473_600
    exited = worker["exit_filetime"] / 10_000_000 - 11_644_473_600
    require(created <= identity["at"] <= exited, "WORKER_OBSERVATION_LIFETIME")
    samples = [json.loads(line) for line in
               (custody / "full.samples.jsonl").read_text().splitlines()]
    observed = [s for s in samples if s["pid"] == worker["pid"]]
    require(bool(observed) and all(s["creation_filetime"] == worker["creation_filetime"]
                                  for s in observed), "PID_REUSE")
    require(max(s["cpu_seconds"] for s in observed) == worker["cpu_seconds"], "CPU_RECEIPT")
    require(max(s["os_peak_working_set_bytes"] or 0 for s in observed)
            == worker["peak_working_set_bytes"], "MEMORY_RECEIPT")
    require(sha((root / "docs/operations/phase-14.22-plan.md").read_bytes())
            == "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31", "EXCEPTION")
    return {"version": "Phase1439BReplay.v1", "manifest": envelope["fingerprint"],
            "classification": manifest["classification"], "full_passed": len(cases),
            "corrected_test_sha256": manifest["corrected_test_sha256"],
            "historical_replay": prior, "worker_cpu_seconds": worker["cpu_seconds"],
            "observed_os_peak_working_set_bytes": worker["peak_working_set_bytes"],
            "resource_receipt_sha256": sha((custody / "full.command.json").read_bytes()),
            "protected_baseline_files": len(manifest["baseline_hashes"]) - 1,
            "genuine_qualified_pairs": 0, "real_kite_requests": 0,
            "Gate_A": "NOT_AUTHORIZED", "Gate_B": "NOT_AUTHORIZED", "operational": "NO_GO"}


if __name__ == "__main__":
    print(canonical(replay(Path(sys.argv[1]), Path(sys.argv[2]))).decode())
