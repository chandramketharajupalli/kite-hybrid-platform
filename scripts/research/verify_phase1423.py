"""Read-only preservation and deterministic Phase 14.23 certification replay."""

import importlib.util
import json
import subprocess
from pathlib import Path

from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1423.quality import POLICY, assess, inventory

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.23/generation-1"
PHASE1422_HASH = "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31"


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


def git(*args):
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def verify():
    path = DIRECTORY / "evidence-manifest.json"
    e = json.loads(path.read_bytes())
    m = e["manifest"]
    assert sha(canonical(m)) == e["fingerprint"]
    assert git("branch", "--show-current").strip() == b"develop"
    for ref in ("HEAD", "origin/develop"):
        assert git("rev-parse", ref).decode().strip() == m["baseline"]
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    exception = {
        "docs/operations/phase-14.22-plan.md": PHASE1422_HASH
    }
    assert m["preservation_exceptions"] == exception
    new = set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
    assert new == set(m["new_files"]) | set(exception)
    assert set(m["new_files"]).isdisjoint(exception)
    assert set(m["artifacts"]) == set(m["new_files"]) - {
        path.relative_to(ROOT).as_posix()
    }
    tracked = set(git("ls-files").decode().splitlines())
    assert tracked == set(m["baseline_hashes"]) and len(tracked) == 1304
    for n, h in (m["baseline_hashes"] | exception | m["artifacts"]).items():
        assert sha((ROOT / n).read_bytes()) == h, n
    for n in m["new_files"]:
        raw = (ROOT / n).read_bytes()
        assert b"\r" not in raw and all(
            line.rstrip(b" \t") == line for line in raw.splitlines()
        ), n
        assert git("hash-object", n) == git("hash-object", "--no-filters", n)
    assert m["software_inventory"] == inventory()
    assert m["software_hash"] == sha(canonical(inventory()))
    assert m["policy_hash"] == sha(canonical(POLICY))
    assert m["plan_hash"] == sha(
        (ROOT / "docs/operations/phase-14.23-plan.md").read_bytes()
    )
    for p, h in (m["historical_external_evidence"] | m["external_evidence"]).items():
        assert sha(Path(p).read_bytes()) == h, p
    for command in m["commands"]:
        p = Path(command["path"])
        receipt = json.loads(p.read_bytes())
        assert sha(p.read_bytes()) == command["sha256"]
        assert (
            receipt["exit_code"] == command["exit_code"]
            and not receipt["protected_changed"]
        )
        for ext in ("stdout", "stderr"):
            assert (
                sha(p.with_suffix("." + ext).read_bytes()) == receipt[ext + "_sha256"]
            )
    for name in m["required_commands"]:
        selected = [c for c in m["commands"] if c["id"] == name]
        assert len(selected) == 1 and selected[0]["exit_code"] == 0
    receipts = module("receipts1423", "scripts/research/verify_phase1415b.py")
    tests = receipts.verify_receipts(
        m["junit_receipts"], m["required_tests"], scope="FRESH"
    )
    assert [r["tests"] for r in tests] == m["test_totals"]
    data = json.loads((DIRECTORY / "synthetic-execution-quality.json").read_bytes())
    assert data["scope"] == "SYNTHETIC_FIXTURE"
    source_path = ROOT / data["source_path"]
    assert sha(source_path.read_bytes()) == data["source_raw_hash"]
    source = json.loads(source_path.read_bytes())["scenarios"][data["source_scenario"]]
    assert sha(canonical(source)) == data["source_hash"]
    assert (
        data["software_hash"] == m["software_hash"]
        and data["policy_hash"] == m["policy_hash"]
    )
    cases = {}
    assert set(data["cases"]) == {
        "complete-mixed",
        "partial-coverage",
        "future-rejected",
    }
    for name, case in data["cases"].items():
        assert sha(canonical(case["references"])) == case["reference_hash"]
        result = assess(source, data["source_hash"], case["references"])
        assert (
            result == case["output"] and sha(canonical(result)) == case["output_hash"]
        )
        assert case["publication"]["report_hash"] == case["output_hash"]
        assert case["replay"] == "IDENTICAL"
        assert (
            len(result["symbol_slippage"]) == 1115
            and len(result["strategy_slippage"]) == 6
        )
        assert result["portfolio"]["slippage_added_to_cash"] == "0"
        cases[name] = dict(
            portfolio=result["portfolio"],
            fingerprint=case["output_hash"],
            rejected=len(result["rejections"]),
            replay="IDENTICAL",
        )
    protected = module("protected1423", "scripts/research/verify_phase1415d.py")
    return dict(
        result="PASS",
        baseline=m["baseline"],
        fingerprint=e["fingerprint"],
        software_hash=m["software_hash"],
        policy_hash=m["policy_hash"],
        tests=tests,
        protected_unchanged=1304,
        preservation_exceptions=exception,
        protected=protected.protected(m["baseline"]),
        family=protected.reports(),
        scans=protected.scan(sorted(tracked | new)),
        cases=cases,
        new=len(m["new_files"]),
        modified=0,
        real_kite_requests=0,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
