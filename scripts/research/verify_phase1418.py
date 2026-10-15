"""Read-only pre-staging Phase14.18 evidence certification; invoke with Python -B."""

import hashlib
import importlib.util
import json
import subprocess
from decimal import Decimal, localcontext
from pathlib import Path

from strategy_engine.phase14.engine import PRECISION
from strategy_engine.phase1416.imports import canonical
from strategy_engine.phase1418.portfolio import Policy, inventory

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.18/generation-1"
BASELINE = "777de800b6ad20acebaa22925f8b34d29020868b"


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def git(*args):
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


def verify():
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    m = envelope["manifest"]
    if sha(canonical(m)) != envelope["fingerprint"]:
        raise ValueError("MANIFEST_FINGERPRINT")
    assert all(git("rev-parse", r).decode().strip() == BASELINE for r in ("HEAD", "origin/develop"))
    assert git("branch", "--show-current").decode().strip() == "develop"
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    base = m["baseline_raw_sha256"]
    assert len(base) == 1233 and set(git("ls-files", "-z").decode().split("\0")[:-1]) == set(base)
    for name, h in base.items():
        assert sha((ROOT / name).read_bytes()) == h, name
    actual = set(git("ls-files", "--others", "--exclude-standard", "-z").decode().split("\0")[:-1])
    assert actual == set(m["artifacts_raw_sha256"]) | {
        "research/phase-14.18/generation-1/evidence-manifest.json"
    }
    for name in actual:
        raw = (ROOT / name).read_bytes()
        if name in m["artifacts_raw_sha256"]:
            assert sha(raw) == m["artifacts_raw_sha256"][name]
        assert b"\r" not in raw and all(line.rstrip(b" \t") == line for line in raw.splitlines())
        assert git("hash-object", name) == git("hash-object", "--no-filters", name)
    assert m["software_inventory"] == inventory() and m["software_hash"] == sha(
        canonical(inventory())
    )
    b = module("receipts1418", "scripts/research/verify_phase1415b.py")
    tests = b.verify_receipts(m["junit_receipts"], ["focused-final", "full-final"], scope="FRESH")
    assert [x["tests"] for x in tests] == [49, 547]
    ids = [r["id"] for r in m["command_receipts"]]
    assert len(ids) == len(set(ids)) == 6
    assert set(ids) == {
        "focused-final",
        "full-final",
        "ruff-final",
        "mypy-final",
        "generate",
        "ruff-verifier-final",
    }
    for r in m["command_receipts"]:
        path = Path(r["path"])
        assert sha(path.read_bytes()) == r["sha256"]
        receipt = json.loads(path.read_bytes())
        assert receipt["exit_code"] == 0 and receipt["command"] == r["command"]
        for ext in ("stdout", "stderr"):
            assert sha(path.with_suffix("." + ext).read_bytes()) == receipt[ext + "_sha256"]
    for path, h in m["external_evidence"].items():
        assert Path(path).is_file() and sha(Path(path).read_bytes()) == h, path
    parent = json.loads(
        (ROOT / "research/phase-14.17/generation-1/evidence-manifest.json").read_bytes()
    )
    assert sha(canonical(parent["manifest"])) == parent["fingerprint"]
    for path, h in parent["manifest"]["external_sources"].items():
        assert sha(Path(path).read_bytes()) == h
    b.verify_receipts(
        parent["manifest"]["junit_receipts"], ["focused-certified", "full-certified"], scope="FRESH"
    )
    for phase in ("14.15d", "14.16"):
        old = json.loads(
            (ROOT / f"research/phase-{phase}/generation-1/evidence-manifest.json").read_bytes()
        )
        assert sha(canonical(old["manifest"])) == old["fingerprint"]
    rows = json.loads(
        (ROOT / "research/phase-14.17/generation-1/qualification-ledger.json").read_bytes()
    )
    assert len(rows) == 13380 and all(
        r["status"] == "DATA_UNAVAILABLE" and r["net"] is None for r in rows
    )
    package = json.loads((DIRECTORY / "synthetic-portfolio-examples.json").read_bytes())
    assert package["scope"] == "SYNTHETIC_FIXTURE" and package["not_real_trading_profits"]
    assert set(package["examples"]) == {"simultaneous", "missing-eod", "cancelled"}
    scenario_counts = {}
    with localcontext(PRECISION):
        for name, example in package["examples"].items():
            summary = example["summary"]
            assert summary["scope"] == "SYNTHETIC_FIXTURE" and summary["qualified_genuine"] == 0
            assert sha(canonical(example["binding"])) == summary["run_id"]
            previous = "0" * 64
            previous_cash = Decimal(example["binding"]["policy"]["base"]["capital"])
            previous_reserved = Decimal(0)
            for i, event in enumerate(example["journal"]):
                assert event["sequence"] == i and event["previous_hash"] == previous
                assert (
                    sha(canonical({k: v for k, v in event.items() if k != "current_hash"}))
                    == event["current_hash"]
                )
                account = event["account"]
                assert previous_cash + Decimal(event["cash_delta"]) == Decimal(account["cash"])
                assert previous_reserved + Decimal(event["reservation_delta"]) == Decimal(
                    account["reserved"]
                )
                assert (
                    Decimal(account["available"])
                    == Decimal(account["cash"]) - Decimal(account["reserved"])
                    >= 0
                )
                previous_cash = Decimal(account["cash"])
                previous_reserved = Decimal(account["reserved"])
                previous = event["current_hash"]
            assert (
                previous == summary["final_hash"]
                and len(example["journal"]) == summary["event_count"]
            )
            for report, value in example["reports"].items():
                assert sha(canonical(value)) == summary["reports"][report]
            assert summary["account"] == example["journal"][-1]["account"]
            scenario_counts[name] = summary["event_count"]
    examples = package["examples"]
    assert examples["simultaneous"]["summary"]["simulation_complete"]
    assert (
        examples["missing-eod"]["summary"]["halted"]
        and examples["missing-eod"]["summary"]["account"]["positions"]
    )
    assert examples["cancelled"]["summary"]["account"]["cash"] == "10000"
    readiness = json.loads((DIRECTORY / "paper-readiness.json").read_bytes())
    assert readiness["status"] == "BLOCKED" and readiness["genuine_qualified"] == 0
    assert readiness["risk_policy_gaps"] == list(Policy().gaps({"SBIN"}))
    d = module("protected1418", "scripts/research/verify_phase1415d.py")
    preservation = d.protected(BASELINE)
    scans = d.scan(sorted(set(base) | actual))
    return dict(
        result="PASS",
        baseline=BASELINE,
        fingerprint=envelope["fingerprint"],
        software_hash=m["software_hash"],
        protected_files=1233,
        new_files=len(actual),
        tests=tests,
        synthetic_events=scenario_counts,
        qualification_rows=13380,
        genuine_qualified=0,
        preservation=preservation,
        scans=scans,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
        real_kite_requests=0,
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
