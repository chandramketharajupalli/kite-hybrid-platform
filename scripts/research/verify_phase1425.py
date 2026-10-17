"""Read-only Phase 14.25 seal verification plus external synthetic replay; Python -B."""

import importlib.util
import json
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1424.analytics import inventory
from strategy_engine.phase1425.reliability import POLICY

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.25/generation-1"


def git(*args):
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def verify():
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    m = envelope["manifest"]
    assert sha(canonical(m)) == envelope["fingerprint"], "MANIFEST_HASH"
    for ref in ("HEAD", "origin/develop"):
        assert git("rev-parse", ref).decode().strip() == m["baseline"]
    assert git("branch", "--show-current").strip() == b"develop"
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    tracked = set(git("ls-files").decode().splitlines())
    assert tracked == set(m["baseline_hashes"]) and len(tracked) == 1326
    new = set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
    assert new == set(m["new_files"]) | set(m["preservation_exceptions"])
    assert set(m["artifacts"]) == set(m["new_files"]) - {
        "research/phase-14.25/generation-1/evidence-manifest.json"}
    for n, h in (m["baseline_hashes"] | m["preservation_exceptions"] | m["artifacts"]).items():
        assert sha((ROOT / n).read_bytes()) == h, n
    for n in m["new_files"]:
        raw = (ROOT / n).read_bytes()
        assert b"\r" not in raw and all(x.rstrip(b" \t") == x for x in raw.splitlines()), n
        assert git("hash-object", n) == git("hash-object", "--no-filters", n), n
    assert sha(canonical(POLICY)) == m["fault_policy_fingerprint"]
    software = inventory()
    for path in (ROOT / "apps/strategy-engine/src/strategy_engine/phase1425").glob("*.py"):
        software[path.relative_to(ROOT).as_posix()] = sha(path.read_bytes())
    assert software == m["software_inventory"]
    assert sha(canonical(software)) == m["software_fingerprint"]
    assert sha((ROOT / "docs/operations/phase-14.25-plan.md").read_bytes()) == m["plan_hash"]
    for n, h in m["external_evidence"].items():
        assert sha(Path(n).read_bytes()) == h, n
    tests = {}
    for receipt in m["junit"]:
        root = ET.fromstring(Path(receipt["path"]).read_bytes())
        cases = list(root.iter("testcase"))
        assert len(cases) == receipt["tests"] > 0
        assert all(int(s.attrib[k]) == 0 for s in root.iter("testsuite")
                   for k in ("failures", "errors", "skipped"))
        assert not any(list(root.iter(k)) for k in ("failure", "error", "skipped"))
        tests[receipt["id"]] = len(cases)
    for name in m["required_commands"]:
        selected = [c for c in m["commands"] if c["id"] == name]
        assert len(selected) == 1 and selected[0]["exit_code"] == 0
    for command in m["commands"]:
        path = Path(command["path"])
        receipt = json.loads(path.read_bytes())
        assert receipt["exit_code"] == command["exit_code"]
        assert receipt["protected_changed"] == []
        for ext in ("stdout", "stderr"):
            assert sha(path.with_suffix("." + ext).read_bytes()) == receipt[ext + "_sha256"]
    # Reuse fixture constructors, never copy strategy/risk/accounting algorithms.
    sys.path.insert(0, str(ROOT / "apps/strategy-engine/tests"))
    from test_phase1425 import build_reports

    recorded = json.loads((DIRECTORY / "synthetic-reliability.json").read_bytes())
    assert {r["scenario_id"]: r["fingerprint"] for r in recorded["scenario_ledger"]}
    for row in recorded["scenario_ledger"]:
        assert sha(canonical({k: v for k, v in row.items() if k != "fingerprint"})) \
            == m["scenario_fingerprints"][row["scenario_id"]] == row["fingerprint"]
    assert recorded["engineering_reliability"]["metrics"] == m["metrics"]
    with tempfile.TemporaryDirectory(prefix="phase1425-replay-") as temp:
        replay = build_reports(Path(temp))
        assert replay == recorded, "REPLAY_DIFFERENCE"
    assert sha(canonical(recorded)) == m["report_fingerprint"]
    path = ROOT / "scripts/research/verify_phase1415d.py"
    spec = importlib.util.spec_from_file_location("preserved1425", path)
    old = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(old)
    scans = old.scan(sorted(tracked | new))
    return {"verification": "PASS", "classification": recorded["final_certification"],
            "baseline": m["baseline"], "manifest": envelope["fingerprint"],
            "report": m["report_fingerprint"], "protected": len(tracked), "tests": tests,
            "scans": scans, "new_files": len(new) - 1, "real_kite_requests": 0,
            "Gate_A": "NOT_AUTHORIZED", "Gate_B": "NOT_AUTHORIZED", "operational": "NO_GO"}


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
