"""Read-only verification of additive multi-session evidence."""

import hashlib
import importlib.util
import json
import subprocess
from decimal import localcontext
from pathlib import Path

from strategy_engine.phase14.engine import PRECISION
from strategy_engine.phase1416.imports import canonical
from strategy_engine.phase1418.portfolio import Control, Policy
from strategy_engine.phase1419.scheduler import Calendar
from strategy_engine.phase1420.multi import Day, Limits, execute, report_views, software

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.20/generation-1"


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


def git(*args):
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def examples():
    data = json.loads((DIRECTORY / "synthetic-multi-sessions.json").read_bytes())
    assert data["scope"] == "SYNTHETIC_FIXTURE" and data["genuine_qualified"] == 0
    assert data["software_hash"] == sha(canonical(software()))
    results = {}
    assert set(data["scenarios"]) == {
        "consecutive",
        "recovered-weekend",
        "unresolved-eod",
    }
    for name, value in data["scenarios"].items():
        inputs = value["input"]
        assert sha(canonical(inputs)) == value["input_hash"]
        days = tuple(Day.model_validate(d) for d in inputs["days"])
        policy = Policy.model_validate(inputs["policy"])
        control = Control.model_validate(inputs["control"])
        calendar = Calendar.model_validate(inputs["calendar"])
        limits = Limits.model_validate(inputs["limits"])
        previous = "0" * 64
        last = None
        for i, (outcome, record) in enumerate(
            zip(value["outcomes"], value["records"], strict=True)
        ):
            assert sha(canonical(outcome["value"])) == outcome["fingerprint"]
            assert sha(canonical(record["value"])) == record["fingerprint"]
            o, r = outcome["value"], record["value"]
            assert r["sequence"] == i and r["previous"] == previous
            assert r["outcome_hash"] == sha(canonical(outcome))
            assert o["binding"] == r["binding"]
            assert o["start"] == (
                last
                if last
                else dict(
                    cash="10000",
                    equity="10000",
                    cumulative_net="0",
                    cumulative_fees="0",
                    high_water="10000",
                    maximum_drawdown="0",
                    positions={},
                    reservations={},
                    halted=False,
                )
            )
            with localcontext(PRECISION):
                assert (
                    execute(
                        days[i],
                        policy,
                        control,
                        calendar,
                        limits,
                        o["start"],
                        o["binding"],
                    )
                    == o
                )
            previous = sha(canonical(record))
            last = o["end"]
        summary = value["summary"]
        assert summary["state"] == last and summary["head"] == previous
        assert summary["complete"] == (name != "unresolved-eod")
        assert summary["alerts"] == [
            a for r in value["records"] for a in r["value"]["alerts"]
        ]
        assert report_views(summary, limits) == value["reports"]
        assert summary["reports"] == {
            n: sha(canonical(v)) for n, v in value["reports"].items()
        }
        assert summary["genuine_qualified"] == 0 and value["replay_identical"]
        assert len(summary["sessions"]) == (1 if name == "unresolved-eod" else 2)
        results[name] = dict(
            complete=summary["complete"], head=previous, replay="IDENTICAL", state=last
        )
    return results


def verify():
    path = DIRECTORY / "evidence-manifest.json"
    envelope = json.loads(path.read_bytes())
    m = envelope["manifest"]
    assert sha(canonical(m)) == envelope["fingerprint"]
    assert git("branch", "--show-current").strip() == b"develop"
    assert all(
        git("rev-parse", r).decode().strip() == m["baseline"]
        for r in ("HEAD", "origin/develop")
    )
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    new = set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
    assert new == set(m["new_files"])
    assert set(m["artifacts"]) == new - {path.relative_to(ROOT).as_posix()}
    assert len(m["baseline_hashes"]) == 1277
    for n, h in m["baseline_hashes"].items():
        assert sha((ROOT / n).read_bytes()) == h, n
    for n, h in m["artifacts"].items():
        assert sha((ROOT / n).read_bytes()) == h, n
    for n in new:
        raw = (ROOT / n).read_bytes()
        assert b"\r" not in raw and all(
            line.rstrip(b" \t") == line for line in raw.splitlines()
        ), n
        assert git("hash-object", n) == git("hash-object", "--no-filters", n)
    assert (
        m["software_inventory"] == software()
        and sha(canonical(software())) == m["software_hash"]
    )
    assert (
        sha((ROOT / "docs/operations/phase-14.20-plan.md").read_bytes())
        == m["plan_hash"]
    )
    receipts = module("receipts1420", "scripts/research/verify_phase1415b.py")
    tests = receipts.verify_receipts(
        m["junit_receipts"], m["required_tests"], scope="FRESH"
    )
    assert [t["tests"] for t in tests] == m["test_totals"]
    for p, h in m["external_evidence"].items():
        assert Path(p).is_file() and sha(Path(p).read_bytes()) == h, p
    for r in m["commands"]:
        p = Path(r["path"])
        v = json.loads(p.read_bytes())
        assert (
            sha(p.read_bytes()) == r["sha256"]
            and v["exit_code"] == r["exit_code"]
            and not v["protected_changed"]
        )
        for ext in ("stdout", "stderr"):
            assert sha(p.with_suffix("." + ext).read_bytes()) == v[ext + "_sha256"]
    for n in m["required_commands"]:
        selected = [r for r in m["commands"] if r["id"] == n]
        assert len(selected) == 1 and selected[0]["exit_code"] == 0
    historical = module("history1420", "scripts/research/verify_phase1419.py")
    historical.examples()
    d = module("protected1420", "scripts/research/verify_phase1415d.py")
    return dict(
        result="PASS",
        baseline=m["baseline"],
        fingerprint=envelope["fingerprint"],
        software_hash=m["software_hash"],
        new=len(new),
        modified=0,
        tests=tests,
        protected_unchanged=1277,
        protected=d.protected(m["baseline"]),
        research_family=d.reports(),
        scans=d.scan(sorted(set(m["baseline_hashes"]) | new)),
        scenarios=examples(),
        real_kite_requests=0,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
