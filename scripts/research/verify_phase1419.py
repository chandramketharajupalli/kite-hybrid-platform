"""Read-only certification replay of the offline synthetic session package."""

import hashlib
import importlib.util
import json
import re
import subprocess
from pathlib import Path

from strategy_engine.phase1415.execution import EXECUTION_VERSION, execution_fingerprint
from strategy_engine.phase1416.imports import canonical
from strategy_engine.phase1418.canonical_policy import VERSION
from strategy_engine.phase1418.portfolio import Control, Policy, Request
from strategy_engine.phase1419.scheduler import (
    EXECUTION_HASH,
    RISK_HASH,
    Calendar,
    Command,
    Configuration,
    Engine,
    software,
)

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.19/generation-1"
MANIFEST = "research/phase-14.19/generation-1/evidence-manifest.json"


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
    data = json.loads((DIRECTORY / "synthetic-sessions.json").read_bytes())
    assert data["scope"] == "SYNTHETIC_FIXTURE" and data["genuine_qualified"] == 0
    assert data["software_hash"] == sha(canonical(software()))
    assert set(data["scenarios"]) == {
        "regular-all-six",
        "missing-eod-liquidity",
        "recovered-reservation",
    }
    results = {}
    for name, value in data["scenarios"].items():
        inputs = value["input"]
        assert sha(canonical(inputs)) == value["input_hash"]
        requests = tuple(Request.model_validate(r) for r in inputs["requests"])
        policy = Policy.model_validate(inputs["policy"])
        control = Control.model_validate(inputs["control"])
        calendar = Calendar.model_validate(inputs["calendar"])
        config = Configuration.model_validate(inputs["config"])
        engine = Engine(requests, policy, control, calendar, config)
        assert not engine.readiness()
        assert policy.fingerprint_version == VERSION and policy.fingerprint == RISK_HASH
        reports = value["reports"]
        journal = reports["session-events.json"]
        assert len(journal) == len(inputs["commands"])
        for command, row in zip(inputs["commands"], journal, strict=True):
            assert (
                engine.apply(Command.model_validate(command), row["recovered"]) == row
            )
        assert engine.output() == reports and len(reports) == 18
        summary = value["summary"]
        assert summary["session_id"] == engine.identifier
        assert summary["reports"] == {n: sha(canonical(r)) for n, r in reports.items()}
        assert summary["account"] == engine.portfolio.account()
        assert summary["final_hash"] == engine.audit[-1]["current_hash"]
        assert summary["state"] == engine.state
        assert summary["risk_hash"] == RISK_HASH and summary["risk_version"] == VERSION
        assert summary["execution_hash"] == EXECUTION_HASH
        assert summary["execution_version"] == EXECUTION_VERSION
        assert summary["genuine_qualified"] == 0 and value["replay_identical"]
        assert summary["calendar_hash"] == calendar.fingerprint
        assert summary["cutoff_hash"] == sha(canonical(config.model_dump(mode="json")))
        assert summary["complete"] == (name != "missing-eod-liquidity")
        if name == "missing-eod-liquidity":
            assert engine.state == "PAPER_HALTED" and engine.portfolio.positions
            assert (
                engine.portfolio.account()["positions"]["SBIN"]["state"]
                == "EXIT_PENDING"
            )
        else:
            assert not engine.portfolio.positions and not engine.portfolio.reservations
        if name == "regular-all-six":
            assert len(inputs["commands"]) == 77 and len(requests) == 18
            assert {r.definition.identifier for r in requests} == {
                f"S{i:02}" for i in range(1, 7)
            }
        if name == "recovered-reservation":
            assert any(row["recovered"] for row in journal)
        results[name] = dict(state=engine.state, reports=18, replay="IDENTICAL")
    return results


def verify():
    envelope = json.loads((ROOT / MANIFEST).read_bytes())
    m = envelope["manifest"]
    assert sha(canonical(m)) == envelope["fingerprint"]
    assert all(
        git("rev-parse", x).decode().strip() == m["baseline"]
        for x in ("HEAD", "origin/develop")
    )
    assert git("branch", "--show-current").strip() == b"develop"
    assert not git("diff", "--cached", "--name-only")
    modified = set(m["modified"])
    new = set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
    assert set(git("diff", "--name-only").decode().splitlines()) == modified
    assert new == set(m["new_files"])
    baseline = m["baseline_hashes"]
    assert len(baseline) == 1265 and len(modified) == 2
    for name, h in baseline.items():
        if name in modified:
            assert (
                sha(git("show", "HEAD:" + name)) == h == m["modified"][name]["before"]
            )
            assert sha((ROOT / name).read_bytes()) == m["modified"][name]["after"]
            assert sha(Path(m["modified"][name]["archive"]).read_bytes()) == h
        else:
            assert sha((ROOT / name).read_bytes()) == h, name
    assert set(m["artifacts"]) == (new | modified) - {MANIFEST}
    for name in sorted(new | modified):
        raw = (ROOT / name).read_bytes()
        if name != MANIFEST:
            assert sha(raw) == m["artifacts"][name], name
        assert b"\r" not in raw and all(
            line.rstrip(b" \t") == line for line in raw.splitlines()
        ), name
        assert git("hash-object", name) == git("hash-object", "--no-filters", name), (
            name
        )
        assert not re.search(
            rb"(?:Bearer +[A-Za-z0-9._-]{20,}|CANARY_SECRET_[A-Za-z0-9]{8,})", raw
        )
    git("diff", "--check")
    assert (
        sha((ROOT / "docs/operations/phase-14.19-plan.md").read_bytes())
        == m["plan_hash"]
    )
    assert m["software_inventory"] == software()
    assert m["software_hash"] == sha(canonical(software()))
    assert m["risk_version"] == VERSION and m["risk_hash"] == RISK_HASH
    assert m["execution_version"] == EXECUTION_VERSION
    assert m["execution_hash"] == EXECUTION_HASH == execution_fingerprint()
    receipts = module("receipts1419", "scripts/research/verify_phase1415b.py")
    tests = receipts.verify_receipts(
        m["junit_receipts"], m["required_tests"], scope="FRESH"
    )
    assert [x["tests"] for x in tests] == m["test_totals"]
    for path, h in m["external_evidence"].items():
        assert Path(path).is_file() and sha(Path(path).read_bytes()) == h, path
    assert len({r["id"] for r in m["commands"]}) == len(m["commands"])
    for r in m["commands"]:
        p = Path(r["path"])
        v = json.loads(p.read_bytes())
        assert sha(p.read_bytes()) == r["sha256"] and v["exit_code"] == r["exit_code"]
        assert not v["protected_changed"]
        for ext in ("stdout", "stderr"):
            assert sha(p.with_suffix("." + ext).read_bytes()) == v[ext + "_sha256"]
    for name in m["required_commands"]:
        selected = [r for r in m["commands"] if r["id"] == name]
        assert len(selected) == 1 and selected[0]["exit_code"] == 0
    historical = json.loads(
        (
            ROOT / "research/phase-14.18b/generation-1/evidence-manifest.json"
        ).read_bytes()
    )
    assert sha(canonical(historical["manifest"])) == historical["fingerprint"]
    hm = historical["manifest"]
    for path, h in hm["external_evidence"].items():
        assert Path(path).is_file() and sha(Path(path).read_bytes()) == h, path
    historical_tests = receipts.verify_receipts(
        hm["junit_receipts"], ["focused-certified", "full-certified"], scope="FRESH"
    )
    assert [r["tests"] for r in historical_tests] == [99, 597]
    d = module("protected1419", "scripts/research/verify_phase1415d.py")
    preservation = d.protected(m["baseline"])
    scans = d.scan(sorted(set(baseline) | new))
    return dict(
        result="PASS",
        baseline=m["baseline"],
        fingerprint=envelope["fingerprint"],
        software_hash=m["software_hash"],
        risk_hash=RISK_HASH,
        execution_hash=EXECUTION_HASH,
        tests=tests,
        historical_receipts="HASH_VERIFIED_NOT_RERUN",
        protected_unchanged=1263,
        modified=len(modified),
        new=len(new),
        protected=preservation,
        scans=scans,
        scenarios=examples(),
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        real_kite_requests=0,
        operational="NO_GO",
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
