"""Read-only verification of additive causal execution evidence."""

import hashlib
import importlib.util
import json
import subprocess
from pathlib import Path

from strategy_engine.phase1415.execution import (
    EXECUTION_POLICY,
    EXECUTION_VERSION,
    execution_fingerprint,
)
from strategy_engine.phase1416.imports import canonical
from strategy_engine.phase1418.portfolio import inventory

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.18b/generation-1"


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


def git(*args):
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def verify():
    e = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    m = e["manifest"]
    assert sha(canonical(m)) == e["fingerprint"]
    assert all(
        git("rev-parse", x).decode().strip() == m["baseline"]
        for x in ("HEAD", "origin/develop")
    )
    assert git("branch", "--show-current").strip() == b"develop"
    assert not git("diff", "--cached", "--name-only")
    modified = set(m["modified"])
    assert set(git("diff", "--name-only").decode().splitlines()) == modified
    new = set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
    assert new == set(m["new_files"])
    baseline = m["baseline_hashes"]
    assert len(baseline) == 1255
    for name, h in baseline.items():
        if name in modified:
            assert (
                sha(git("show", "HEAD:" + name)) == h == m["modified"][name]["before"]
            )
            assert sha((ROOT / name).read_bytes()) == m["modified"][name]["after"]
        else:
            assert sha((ROOT / name).read_bytes()) == h, name
    assert set(m["artifacts"]) == (new | modified) - {
        "research/phase-14.18b/generation-1/evidence-manifest.json"
    }
    for name, h in m["artifacts"].items():
        raw = (ROOT / name).read_bytes()
        assert sha(raw) == h, name
        assert b"\r" not in raw and all(
            line.rstrip(b" \t") == line for line in raw.splitlines()
        )
        assert git("hash-object", name) == git("hash-object", "--no-filters", name)
    git("diff", "--check")
    assert m["execution_policy_version"] == EXECUTION_VERSION
    assert m["execution_policy_hash"] == execution_fingerprint()
    assert (DIRECTORY / "execution-policy.json").read_bytes() == canonical(
        EXECUTION_POLICY
    )
    assert sha(canonical(EXECUTION_POLICY)) == execution_fingerprint()
    assert m["software_inventory"] == inventory()
    assert sha(canonical(inventory())) == m["software_hash"]
    receipts = module("receipt1418b", "scripts/research/verify_phase1415b.py")
    tests = receipts.verify_receipts(
        m["junit_receipts"], ["focused-certified", "full-certified"], scope="FRESH"
    )
    assert [t["tests"] for t in tests] == m["test_totals"]
    for path, h in m["external_evidence"].items():
        assert Path(path).is_file() and sha(Path(path).read_bytes()) == h, path
    assert len({r["id"] for r in m["commands"]}) == len(m["commands"])
    for r in m["commands"]:
        p = Path(r["path"])
        value = json.loads(p.read_bytes())
        assert sha(p.read_bytes()) == r["sha256"]
        assert value["exit_code"] == r["exit_code"]
        assert not value["protected_changed"]
        for ext in ("stdout", "stderr"):
            assert sha(p.with_suffix("." + ext).read_bytes()) == value[ext + "_sha256"]
    for required in ("focused-certified", "full-certified", "ruff-certified", "mypy-certified"):
        selected = [r for r in m["commands"] if r["id"] == required]
        assert len(selected) == 1 and selected[0]["exit_code"] == 0
    examples = json.loads((DIRECTORY / "synthetic-causal-examples.json").read_bytes())
    assert (
        examples["scope"] == "SYNTHETIC_FIXTURE" and examples["genuine_qualified"] == 0
    )
    before = examples["original_diagnostic"]
    assert [x["fills"][0]["quantity"] for x in before["cases"]] == [10, 1]
    scenarios = examples["scenarios"]
    for x in scenarios.values():
        assert x["summary"]["execution_policy_hash"] == execution_fingerprint()
        previous = "0" * 64
        for i, event in enumerate(x["events"]):
            assert event["sequence"] == i and event["previous_hash"] == previous
            assert event["execution_policy_fingerprint"] == execution_fingerprint()
            assert (
                sha(canonical({k: v for k, v in event.items() if k != "current_hash"}))
                == event["current_hash"]
            )
            previous = event["current_hash"]
            if event["state"] == "FILLED":
                t = event["details"]["timing"]
                assert (
                    t["liquidity_observed_at"]
                    == t["quantity_decided_at"]
                    == event["timestamp"]
                )
                assert t["fill_confirmed_at"] == event["timestamp"]
        assert previous == x["summary"]["final_hash"]
        assert x["summary"]["account"] == x["events"][-1]["account"]
        assert x["replay_identical"]
    a, b = [
        next(e for e in scenarios[n]["events"] if e["state"] == "FILLED")
        for n in ("volume-10000", "volume-100")
    ]
    assert a["timestamp"] == b["timestamp"] and a["details"] == b["details"]
    assert a["details"]["quantity"] == 10 and a["details"]["price"] == "102"
    assert scenarios["exit-unfilled"]["summary"]["halted"]
    assert scenarios["exit-unfilled"]["summary"]["account"]["positions"]
    d = module("protected1418b", "scripts/research/verify_phase1415d.py")
    protected = d.protected(m["baseline"])
    scans = d.scan(sorted(set(baseline) | new))
    return dict(
        result="PASS",
        fingerprint=e["fingerprint"],
        execution_policy_hash=execution_fingerprint(),
        software_hash=m["software_hash"],
        tests=tests,
        protected_unchanged=1255 - len(modified),
        modified=len(modified),
        new=len(new),
        protected=protected,
        scans=scans,
        scenarios=sorted(scenarios),
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        real_kite_requests=0,
        operational="NO_GO",
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
