"""Read-only Phase14.17 certification; Python -B, no broker requests."""

import csv
import hashlib
import importlib.util
import io
import json
import subprocess
from pathlib import Path

from strategy_engine.phase1417.paper import Audit, Risk
from strategy_engine.phase1417.qualification import inventory, software_hash

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.17/generation-1"
BASELINE = "9b3c7f13d4e5c7cbcd29f74b54d1b390d6f7dd64"


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def canonical(value):
    return json.dumps(
        value, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode()


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def git(*args):
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def verify():
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    value = envelope["manifest"]
    require(sha(canonical(value)) == envelope["fingerprint"], "MANIFEST_FINGERPRINT")
    require(
        all(
            git("rev-parse", ref).decode().strip() == BASELINE
            for ref in ("HEAD", "origin/develop")
        ),
        "BASELINE_CHANGED",
    )
    require(
        git("branch", "--show-current").decode().strip() == "develop", "BRANCH_CHANGED"
    )
    require(
        not git("diff", "--name-only") and not git("diff", "--cached", "--name-only"),
        "TRACKED_OR_INDEX_CHANGED",
    )
    base = value["baseline_raw_sha256"]
    require(
        len(base) == 1211
        and set(git("ls-files", "-z").decode().split("\0")[:-1]) == set(base),
        "BASELINE_INVENTORY",
    )
    for path, expected in base.items():
        require(
            sha((ROOT / path).read_bytes()) == expected, "PROTECTED_CHANGED:" + path
        )
    actual = set(
        git("ls-files", "--others", "--exclude-standard", "-z")
        .decode()
        .split("\0")[:-1]
    )
    expected = set(value["artifacts_raw_sha256"]) | {
        "research/phase-14.17/generation-1/evidence-manifest.json"
    }
    require(actual == expected, "UNEXPECTED_NEW_FILES")
    for path, expected_hash in value["artifacts_raw_sha256"].items():
        raw = (ROOT / path).read_bytes()
        require(
            sha(raw) == expected_hash and b"\r" not in raw, "ARTIFACT_BYTES:" + path
        )
        require(
            git("hash-object", path) == git("hash-object", "--no-filters", path),
            "GIT_BYTES:" + path,
        )
    require(
        value["software_inventory"] == inventory()
        and value["software_hash"] == software_hash(),
        "SOFTWARE_FINGERPRINT",
    )
    receipts = module("receipt1417", "scripts/research/verify_phase1415b.py")
    tests = receipts.verify_receipts(
        value["junit_receipts"], ["focused-certified", "full-certified"], scope="FRESH"
    )
    require([r["tests"] for r in tests] == [37, 498], "TEST_COUNTS")
    identifiers = [r["id"] for r in value["command_receipts"]]
    require(
        len(identifiers) == len(set(identifiers)) == 6
        and set(identifiers)
        == {
            "focused-certified",
            "full-certified",
            "ruff-certified",
            "mypy-certified-full",
            "generate-corrected",
            "ruff-verifier-final",
        },
        "COMMAND_INVENTORY",
    )
    for record in value["command_receipts"]:
        path = Path(record["path"])
        require(
            path.is_file() and sha(path.read_bytes()) == record["sha256"],
            "RECEIPT_UNAVAILABLE",
        )
        receipt = json.loads(path.read_bytes())
        require(
            receipt["exit_code"] == 0 and receipt["command"] == record["command"],
            "COMMAND_FAILED",
        )
        for ext in ("stdout", "stderr"):
            require(
                sha(path.with_suffix("." + ext).read_bytes())
                == receipt[ext + "_sha256"],
                "DIAGNOSTIC_CHANGED",
            )
    for path, expected_hash in value["external_sources"].items():
        require(
            Path(path).is_file() and sha(Path(path).read_bytes()) == expected_hash,
            "EXTERNAL_SOURCE_UNAVAILABLE",
        )
    rows = json.loads((DIRECTORY / "qualification-ledger.json").read_bytes())
    universe = list(
        csv.DictReader(
            io.StringIO(
                (ROOT / "config/universes/phase14_unique_universe.csv").read_text(
                    encoding="utf-8-sig"
                )
            )
        )
    )
    keys = [
        (u["symbol"], f"S{i:02d}", months)
        for u in universe
        for months in (6, 12)
        for i in range(1, 7)
    ]
    require(
        len(keys) == 13380
        and [(r["symbol"], r["strategy_id"], r["months"]) for r in rows] == keys,
        "INCOMPLETE_QUALIFICATION_FAMILY",
    )
    require(
        all(
            r["status"] == "DATA_UNAVAILABLE"
            and all(
                r[k] is None
                for k in (
                    "trade_count",
                    "win_rate",
                    "confidence_interval",
                    "net",
                    "profit_factor",
                    "drawdown",
                    "training",
                    "validation",
                    "holdout",
                )
            )
            for r in rows
        ),
        "FABRICATED_PERFORMANCE",
    )
    csv_rows = list(
        csv.DictReader(
            io.StringIO((DIRECTORY / "qualification-ledger.csv").read_text())
        )
    )
    require(
        len(csv_rows) == len(rows)
        and all(
            c
            == {
                k: ""
                if v is None
                else canonical(v).decode()
                if isinstance(v, (dict, list))
                else str(v)
                for k, v in r.items()
            }
            for c, r in zip(csv_rows, rows, strict=True)
        ),
        "CSV_JSON_MISMATCH",
    )
    paper = json.loads((DIRECTORY / "synthetic-paper-example.json").read_bytes())
    require(
        paper["binding"]["scope"] == "SYNTHETIC_FIXTURE"
        and not paper["genuine_qualified"],
        "SYNTHETIC_SCOPE",
    )
    audit = Audit.replay(paper["binding"], paper["events"])
    require(
        audit.events[-1]["current_hash"] == paper["final_hash"], "PAPER_CHAIN_ANCHOR"
    )
    readiness = json.loads((DIRECTORY / "paper-readiness.json").read_bytes())
    require(
        readiness["status"] == "BLOCKED"
        and readiness["risk_policy_hash"] == Risk().fingerprint
        and readiness["risk_gaps"] == list(Risk().gaps()),
        "RISK_DEFAULTS_CHANGED",
    )
    for phase in ("14.15d", "14.16"):
        historical = json.loads(
            (
                ROOT / f"research/phase-{phase}/generation-1/evidence-manifest.json"
            ).read_bytes()
        )
        require(
            sha(canonical(historical["manifest"])) == historical["fingerprint"],
            "PARENT_ENVELOPE",
        )
    d = module("protected1417", "scripts/research/verify_phase1415d.py")
    preserved = d.protected(BASELINE)
    scans = d.scan(sorted(set(base) | actual))
    return dict(
        result="PASS",
        baseline=BASELINE,
        fingerprint=envelope["fingerprint"],
        protected_files=1211,
        new_files=len(actual),
        tests=tests,
        rows=len(rows),
        qualified=0,
        paper_events=len(audit.events),
        paper_chain=paper["final_hash"],
        readiness="BLOCKED_MISSING_DATA_AND_RISK",
        preserved=preserved,
        scans=scans,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
        real_kite_requests=0,
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
