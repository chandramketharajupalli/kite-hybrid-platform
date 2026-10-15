"""Read-only correction evidence verification, including serialized policy round-trips."""

import hashlib
import importlib.util
import json
import subprocess
from decimal import Decimal, localcontext
from pathlib import Path

from strategy_engine.phase14.engine import PRECISION
from strategy_engine.phase1416.imports import canonical
from strategy_engine.phase1417.paper import Risk
from strategy_engine.phase1418.canonical_policy import VERSION, payload
from strategy_engine.phase1418.portfolio import Control, Policy, inventory

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.18a/generation-1"
BASELINE = "0a608de56d969ecd6b6d10a7eda799c70e036ae1"
ALLOWED = {
    "apps/strategy-engine/src/strategy_engine/phase1418/portfolio.py",
    "apps/strategy-engine/tests/test_phase1418.py",
}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def git(*args):
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


def legacy_hash(raw, order):
    value = dict(
        base=Risk.model_validate(raw["base"]).model_dump(mode="json"),
        strategy_exposure=raw["strategy_exposure"],
        sector_exposure=raw["sector_exposure"],
        sectors={key: raw["sectors"][key] for key in order},
        sector_evidence=raw["sector_evidence"],
        correlation_positions=raw["correlation_positions"],
    )
    return sha(json.dumps(value, separators=(",", ":"), ensure_ascii=False).encode("utf-8"))


def verify():
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    m = envelope["manifest"]
    if sha(canonical(m)) != envelope["fingerprint"]:
        raise ValueError("MANIFEST_FINGERPRINT")
    assert all(git("rev-parse", r).decode().strip() == BASELINE for r in ("HEAD", "origin/develop"))
    assert git("branch", "--show-current").decode().strip() == "develop"
    assert not git("diff", "--cached", "--name-only")
    assert set(git("diff", "--name-only").decode().splitlines()) == ALLOWED
    base = m["baseline_raw_sha256"]
    assert len(base) == 1245 and set(git("ls-files", "-z").decode().split("\0")[:-1]) == set(base)
    assert set(m["modified_files_before_after"]) == ALLOWED
    for name, h in base.items():
        assert (
            sha(git("show", "HEAD:" + name)) == h
            if name in ALLOWED
            else sha((ROOT / name).read_bytes()) == h
        ), name
    for name, hashes in m["modified_files_before_after"].items():
        assert hashes["before"] == base[name]
        assert sha((ROOT / name).read_bytes()) == hashes["after"] == m["artifacts_raw_sha256"][name]
    new = set(git("ls-files", "--others", "--exclude-standard", "-z").decode().split("\0")[:-1])
    assert new == set(m["new_files"])
    assert set(m["artifacts_raw_sha256"]) == (new | ALLOWED) - {
        "research/phase-14.18a/generation-1/evidence-manifest.json"
    }
    for name in new | ALLOWED:
        raw = (ROOT / name).read_bytes()
        if name in m["artifacts_raw_sha256"]:
            assert sha(raw) == m["artifacts_raw_sha256"][name]
        assert b"\r" not in raw and all(line.rstrip(b" \t") == line for line in raw.splitlines())
        assert git("hash-object", name) == git("hash-object", "--no-filters", name)
    git("diff", "--check")
    assert m["software_inventory"] == inventory() and m["software_hash"] == sha(
        canonical(inventory())
    )
    assert m["canonicalization_version"] == VERSION
    b = module("receipt1418a", "scripts/research/verify_phase1415b.py")
    tests = b.verify_receipts(m["junit_receipts"], ["focused-1", "full-1"], scope="FRESH")
    assert [x["tests"] for x in tests] == [74, 572]
    ids = [r["id"] for r in m["command_receipts"]]
    assert len(ids) == len(set(ids)) == 6
    assert set(ids) == {"focused-1", "full-1", "ruff-1", "mypy-1", "generate", "ruff-verifier"}
    for r in m["command_receipts"]:
        path = Path(r["path"])
        assert sha(path.read_bytes()) == r["sha256"]
        receipt = json.loads(path.read_bytes())
        assert receipt["exit_code"] == 0 and receipt["command"] == r["command"]
        for ext in ("stdout", "stderr"):
            assert sha(path.with_suffix("." + ext).read_bytes()) == receipt[ext + "_sha256"]
    for path, h in m["external_evidence"].items():
        assert Path(path).is_file() and sha(Path(path).read_bytes()) == h, path
    parent_path = ROOT / "research/phase-14.18/generation-1/evidence-manifest.json"
    parent = json.loads(parent_path.read_bytes())
    assert sha(canonical(parent["manifest"])) == parent["fingerprint"]
    for name, h in parent["manifest"]["baseline_raw_sha256"].items():
        assert sha((ROOT / name).read_bytes()) == h
    for path, h in parent["manifest"]["external_evidence"].items():
        assert sha(Path(path).read_bytes()) == h
    b.verify_receipts(
        parent["manifest"]["junit_receipts"], ["focused-final", "full-final"], scope="FRESH"
    )
    old = json.loads((parent_path.parent / "synthetic-portfolio-examples.json").read_bytes())[
        "examples"
    ]
    package = json.loads((DIRECTORY / "synthetic-portfolio-examples.json").read_bytes())
    assert package["scope"] == "SYNTHETIC_FIXTURE" and package["not_real_trading_profits"]
    examples = package["examples"]
    assert set(examples) == set(old) == {"simultaneous", "missing-eod", "cancelled"}
    payload_raw = (DIRECTORY / "canonical-policy-payload.json").read_bytes()
    corrected = sha(payload_raw)
    assert corrected == m["corrected_policy_fingerprint"]
    results = {}
    with localcontext(PRECISION):
        for name, example in examples.items():
            binding = example["binding"]
            p = Policy.model_validate(binding["policy"])
            ctl = Control.model_validate(binding["control"])
            assert p.base.fixture_only and p.fingerprint == corrected == ctl.policy_hash
            assert payload(p.model_dump(mode="python"), VERSION) == payload_raw
            assert (
                Policy.model_validate_json(canonical(p.model_dump(mode="json"))).fingerprint
                == corrected
            )
            assert (
                p.model_copy(
                    update={"sectors": dict(reversed(list(p.sectors.items())))}
                ).fingerprint
                == corrected
            )
            assert (
                binding["schema"] == "Phase1418Portfolio.v2"
                and binding["policy_fingerprint_version"] == VERSION
            )
            summary = example["summary"]
            assert summary["schema"] == "Phase1418PortfolioSummary.v2"
            assert (
                summary["policy_fingerprint_version"] == VERSION
                and summary["risk_policy_fingerprint"] == corrected
            )
            assert sha(canonical(binding)) == summary["run_id"]
            assert summary["run_id"] != old[name]["summary"]["run_id"]
            assert summary["scope"] == "SYNTHETIC_FIXTURE" and summary["qualified_genuine"] == 0
            previous = "0" * 64
            cash = p.base.capital
            reserved = Decimal(0)
            for i, event in enumerate(example["journal"]):
                assert event["sequence"] == i and event["previous_hash"] == previous
                assert (
                    event["risk_policy_fingerprint"] == corrected
                    and event["risk_policy_fingerprint_version"] == VERSION
                )
                assert (
                    sha(canonical({k: v for k, v in event.items() if k != "current_hash"}))
                    == event["current_hash"]
                )
                account = event["account"]
                assert cash + Decimal(event["cash_delta"]) == Decimal(account["cash"])
                assert reserved + Decimal(event["reservation_delta"]) == Decimal(
                    account["reserved"]
                )
                cash = Decimal(account["cash"])
                reserved = Decimal(account["reserved"])
                assert Decimal(account["available"]) == cash - reserved >= 0
                previous = event["current_hash"]
            assert (
                previous == summary["final_hash"]
                and len(example["journal"]) == summary["event_count"]
            )
            assert summary["event_count"] == old[name]["summary"]["event_count"]
            for field in (
                "cash",
                "available",
                "reserved",
                "cost_basis",
                "equity",
                "realized",
                "unrealized",
                "fees",
                "halted",
            ):
                assert summary["account"][field] == old[name]["summary"]["account"][field]
            for symbol, position in summary["account"]["positions"].items():
                prior_position = old[name]["summary"]["account"]["positions"][symbol]
                assert {k: v for k, v in position.items() if k != "order_id"} == {
                    k: v for k, v in prior_position.items() if k != "order_id"
                }
            for report, value in example["reports"].items():
                assert sha(canonical(value)) == summary["reports"][report]
            assert summary["account"] == example["journal"][-1]["account"]
            raw_old = old[name]["binding"]["policy"]
            assert legacy_hash(raw_old, ["SBIN", "TCS", "INFY"]) == m["original_policy_fingerprint"]
            assert (
                legacy_hash(raw_old, sorted(raw_old["sectors"]))
                == m["recomputed_original_policy_fingerprint"]
            )
            results[name] = dict(
                result="PASS",
                events=summary["event_count"],
                fingerprint=corrected,
                economics="UNCHANGED",
            )
    assert examples["simultaneous"]["summary"]["simulation_complete"]
    assert (
        examples["missing-eod"]["summary"]["halted"]
        and examples["missing-eod"]["summary"]["account"]["positions"]
    )
    assert examples["cancelled"]["summary"]["account"]["cash"] == "10000"
    d = module("protected1418a", "scripts/research/verify_phase1415d.py")
    preserved = d.protected(BASELINE)
    scans = d.scan(sorted(set(base) | new))
    return dict(
        result="PASS",
        baseline=BASELINE,
        fingerprint=envelope["fingerprint"],
        software_hash=m["software_hash"],
        canonicalization_version=VERSION,
        corrected_policy_fingerprint=corrected,
        tests=tests,
        examples=results,
        protected_unchanged=1243,
        authorized_modified=2,
        new_files=len(new),
        preserved=preserved,
        scans=scans,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
        real_kite_requests=0,
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
