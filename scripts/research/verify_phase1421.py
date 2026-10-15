"""Read-only certification replay; invoke with Python -B and external receipts."""

import importlib.util
import json
import subprocess
from pathlib import Path

from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1421.analytics import analyze, inventory

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.21/generation-1"


def module(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def git(*args):
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def verify():
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    m = envelope["manifest"]
    assert sha(canonical(m)) == envelope["fingerprint"]
    assert git("branch", "--show-current").strip() == b"develop"
    for ref in ("HEAD", "origin/develop"):
        assert git("rev-parse", ref).decode().strip() == m["baseline"]
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    new = set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
    assert new == set(m["new_files"])
    assert set(m["artifacts"]) == new - {
        "research/phase-14.21/generation-1/evidence-manifest.json"
    }
    assert len(m["baseline_hashes"]) == 1291
    for n, h in (m["baseline_hashes"] | m["artifacts"]).items():
        assert sha((ROOT / n).read_bytes()) == h, n
    for n in new:
        raw = (ROOT / n).read_bytes()
        assert b"\r" not in raw and all(
            line.rstrip(b" \t") == line for line in raw.splitlines()
        ), n
        assert git("hash-object", n) == git("hash-object", "--no-filters", n)
    assert m["software_inventory"] == inventory()
    assert m["software_hash"] == sha(canonical(inventory()))
    assert (
        sha((ROOT / "docs/operations/phase-14.21-plan.md").read_bytes())
        == m["plan_hash"]
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
        records = [r for r in m["commands"] if r["id"] == name]
        assert len(records) == 1 and records[0]["exit_code"] == 0
    receipts = module("receipts1421", "scripts/research/verify_phase1415b.py")
    tests = receipts.verify_receipts(
        m["junit_receipts"], m["required_tests"], scope="FRESH"
    )
    assert [r["tests"] for r in tests] == m["test_totals"]
    package = json.loads((ROOT / m["input_path"]).read_bytes())
    assert sha((ROOT / m["input_path"]).read_bytes()) == m["input_hash"]
    report = json.loads((DIRECTORY / "synthetic-analytics.json").read_bytes())
    assert (
        set(report)
        == set(package["scenarios"])
        == {"consecutive", "recovered-weekend", "unresolved-eod"}
    )
    results = {}
    for name, source in package["scenarios"].items():
        assert analyze(source, sha(canonical(source))) == report[name]
        r = report[name]
        assert (
            len(r["symbol_attribution"]) == 1115 and len(r["strategy_attribution"]) == 6
        )
        assert r["genuine_qualified"] == 0 and r["operational"] == "NO_GO"
        assert r["monitoring"]["effectiveness"] == "NOT_EVALUATED"
        assert r["risk_adjusted"]["sharpe"]["value"] is None
        results[name] = dict(
            fingerprint=sha(canonical(r)),
            portfolio=r["portfolio"],
            replay="IDENTICAL",
            readiness={k: v["status"] for k, v in r["readiness"].items()},
        )
    protected = module("protected1421", "scripts/research/verify_phase1415d.py")
    return dict(
        result="PASS",
        baseline=m["baseline"],
        fingerprint=envelope["fingerprint"],
        software_hash=m["software_hash"],
        tests=tests,
        protected_unchanged=1291,
        protected=protected.protected(m["baseline"]),
        family=protected.reports(),
        scans=protected.scan(sorted(set(m["baseline_hashes"]) | new)),
        scenarios=results,
        new=len(new),
        modified=0,
        real_kite_requests=0,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
