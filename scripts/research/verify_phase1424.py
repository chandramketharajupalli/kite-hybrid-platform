"""Read-only Phase 14.24 evidence replay. Invoke with Python -B."""

import importlib.util
import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1424.analytics import (
    EXPOSURE_POLICY,
    MONITORING_POLICY,
    exposure,
    inventory,
    monitoring,
)

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.24/generation-1"


def git(*args):
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


def verify():
    e = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    m = e["manifest"]
    assert sha(canonical(m)) == e["fingerprint"]
    assert git("branch", "--show-current").strip() == b"develop"
    for ref in ("HEAD", "origin/develop"):
        assert git("rev-parse", ref).decode().strip() == m["baseline"]
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    tracked = set(git("ls-files").decode().splitlines())
    assert tracked == set(m["baseline_hashes"]) and len(tracked) == 1314
    new = set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
    assert new == set(m["new_files"]) | set(m["preservation_exceptions"])
    assert set(m["artifacts"]) == set(m["new_files"]) - {
        "research/phase-14.24/generation-1/evidence-manifest.json"
    }
    for name, expected in (
        m["baseline_hashes"] | m["artifacts"] | m["preservation_exceptions"]
    ).items():
        assert sha((ROOT / name).read_bytes()) == expected, name
    for name in m["new_files"]:
        raw = (ROOT / name).read_bytes()
        assert b"\r" not in raw
        assert all(line.rstrip(b" \t") == line for line in raw.splitlines()), name
        assert git("hash-object", name) == git("hash-object", "--no-filters", name)
    assert sha((ROOT / "docs/operations/phase-14.24-plan.md").read_bytes()) == m["plan_hash"]
    assert inventory() == m["software_inventory"]
    assert sha(canonical(inventory())) == m["software_hash"]
    assert sha(canonical(EXPOSURE_POLICY)) == m["exposure_policy_hash"]
    assert sha(canonical(MONITORING_POLICY)) == m["monitoring_policy_hash"]
    for name, expected in (m["external_evidence"] | m["historical_external_evidence"]).items():
        assert sha(Path(name).read_bytes()) == expected, name
    for command in m["commands"]:
        p = Path(command["path"])
        receipt = json.loads(p.read_bytes())
        assert receipt["exit_code"] == command["exit_code"]
        assert not receipt["protected_changed"]
        for ext in ("stdout", "stderr"):
            assert sha(p.with_suffix("." + ext).read_bytes()) == receipt[ext + "_sha256"]
    for name in m["required_commands"]:
        selected = [c for c in m["commands"] if c["id"] == name]
        assert len(selected) == 1 and selected[0]["exit_code"] == 0
    totals = {}
    for receipt in m["junit"]:
        root = ET.fromstring(Path(receipt["path"]).read_bytes())
        suites = list(root.iter("testsuite"))
        cases = list(root.iter("testcase"))
        assert suites and len(cases) == receipt["tests"] > 0
        assert sum(int(s.attrib["tests"]) for s in suites) == len(cases)
        assert all(int(s.attrib[k]) == 0 for s in suites for k in ("failures", "errors", "skipped"))
        assert not any(list(root.iter(k)) for k in ("failure", "error", "skipped"))
        assert receipt["id"] not in totals
        totals[receipt["id"]] = len(cases)
    data = json.loads((DIRECTORY / "synthetic-exposure-monitoring.json").read_bytes())
    sources = json.loads((ROOT / data["source_path"]).read_bytes())["scenarios"]
    source = sources[data["source_scenario"]]
    assert data["software_hash"] == m["software_hash"]
    assert data["exposure_policy"] == EXPOSURE_POLICY
    assert data["monitoring_policy"] == MONITORING_POLICY
    cases = {}
    for name, case in data["cases"].items():
        result = exposure(source, data["source_hash"], case["observations"])
        assert result == case["output"] and sha(canonical(result)) == case["fingerprint"]
        assert len(result["symbols"]) == 1115 and len(result["strategies"]) == 6
        assert case["replay"] == "IDENTICAL"
        cases[name] = case["fingerprint"]
    monitor = monitoring(data["ground_truth"], data["alerts"], sha(canonical(data["alerts"])))
    assert monitor == data["monitoring"] and sha(canonical(monitor)) == data["monitoring_hash"]
    assert monitoring(None, data["alerts"], sha(canonical(data["alerts"]))) == data["missing_truth"]
    assert data["genuine_qualified"] == 0 and data["operational"] == "NO_GO"
    d = module("historical1424", "scripts/research/verify_phase1415d.py")
    return dict(
        result="PASS", baseline=m["baseline"], manifest=e["fingerprint"],
        protected_unchanged=len(tracked), protected=d.protected(m["baseline"]),
        family=d.reports(), scans=d.scan(sorted(tracked | new)), tests=totals,
        software=m["software_hash"], exposure_policy=m["exposure_policy_hash"],
        monitoring_policy=m["monitoring_policy_hash"], exposure_cases=cases,
        monitoring=data["monitoring_hash"], new=len(m["new_files"]), modified=0,
        real_kite_requests=0, Gate_A="NOT_AUTHORIZED", Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
