"""Read-only offline Phase14.16 evidence replay; run with Python -B."""

import csv
import hashlib
import importlib.util
import io
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.16/generation-1"
BASELINE = "91760b073ee39ce62eb42ef041e8b75d9650e44f"


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def git(*args):
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def verify():
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    value = envelope["manifest"]
    require(sha(canonical(value)) == envelope["fingerprint"], "MANIFEST_FINGERPRINT")
    require(git("rev-parse", "HEAD").decode().strip() == BASELINE, "HEAD_CHANGED")
    require(git("rev-parse", "origin/develop").decode().strip() == BASELINE, "ORIGIN_CHANGED")
    require(git("branch", "--show-current").decode().strip() == "develop", "BRANCH_CHANGED")
    require(
        not git("diff", "--name-only") and not git("diff", "--cached", "--name-only"),
        "TRACKED_OR_INDEX_CHANGED",
    )
    for path, expected in value["baseline_raw_sha256"].items():
        require(sha((ROOT / path).read_bytes()) == expected, "PROTECTED_CHANGED:" + path)
    tracked = set(git("ls-files", "-z").decode().split("\0")[:-1])
    require(tracked == set(value["baseline_raw_sha256"]), "BASELINE_INVENTORY_CHANGED")
    expected_paths = set(value["artifacts_raw_sha256"]) | {
        "research/phase-14.16/generation-1/evidence-manifest.json"
    }
    actual = set(git("ls-files", "--others", "--exclude-standard", "-z").decode().split("\0")[:-1])
    require(actual == expected_paths, "UNEXPECTED_NEW_FILES")
    for path, expected in value["artifacts_raw_sha256"].items():
        raw = (ROOT / path).read_bytes()
        require(sha(raw) == expected and b"\r" not in raw, "ARTIFACT_BYTES:" + path)
        require(
            git("hash-object", "--no-filters", path) == git("hash-object", path),
            "GIT_BYTES:" + path,
        )
    b = module("receipts1416", "scripts/research/verify_phase1415b.py")
    receipts = b.verify_receipts(
        value["junit_receipts"], ["focused-certified", "full-certified"], scope="FRESH"
    )
    identifiers = [r["id"] for r in value["command_receipts"]]
    require(
        len(identifiers) == len(set(identifiers)) == 7
        and set(identifiers)
        == {
            "focused-certified",
            "full-certified",
            "ruff-certified",
            "mypy-certified",
            "synthetic-integration",
            "generate",
            "ruff-verifier-final",
        },
        "COMMAND_RECEIPT_INVENTORY",
    )
    for record in value["command_receipts"]:
        path = Path(record["path"])
        require(
            path.is_file() and sha(path.read_bytes()) == record["sha256"], "RECEIPT_UNAVAILABLE"
        )
        receipt = json.loads(path.read_bytes())
        require(
            receipt["exit_code"] == 0 and receipt["command"] == record["command"],
            "COMMAND_FAILED_OR_CHANGED",
        )
        for extension in ("stdout", "stderr"):
            raw = path.with_suffix("." + extension).read_bytes()
            require(sha(raw) == receipt[extension + "_sha256"], "DIAGNOSTIC_CHANGED")
    for path, expected in value["external_scripts"].items():
        require(Path(path).is_file() and sha(Path(path).read_bytes()) == expected,
                "EXTERNAL_REPLAY_SOURCE_UNAVAILABLE")
    rows = json.loads((DIRECTORY / "stock-strategy-results.json").read_bytes())
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
        (u["symbol"], s, m)
        for u in universe
        for m in (6, 12)
        for s in ("S01", "S02", "S03", "S04", "S05", "S06")
    ]
    require(
        [(r["symbol"], r["strategy"], r["months"]) for r in rows] == keys and len(keys) == 13380,
        "INCOMPLETE_FAMILY",
    )
    require(
        all(
            r["data_status"] == "UNAVAILABLE"
            and not r["qualified"]
            and r["net"] is None
            and r["trade_count"] is None
            for r in rows
        ),
        "UNSUPPORTED_MARKET_PERFORMANCE",
    )
    csv_rows = list(
        csv.DictReader(io.StringIO((DIRECTORY / "stock-strategy-results.csv").read_text()))
    )
    require(
        len(csv_rows) == len(rows)
        and all(
            c == {k: "" if v is None else str(v) for k, v in r.items()}
            for c, r in zip(csv_rows, rows, strict=True)
        ),
        "CSV_JSON_MISMATCH",
    )
    summary = json.loads((DIRECTORY / "run-summary.json").read_bytes())
    for path, expected in summary["reports"].items():
        require(sha((DIRECTORY / path).read_bytes()) == expected, "REPORT_CHANGED")
    d = module("preservation1416", "scripts/research/verify_phase1415d.py")
    preserved = d.protected(BASELINE)
    scan = d.scan(sorted(tracked | actual))
    return dict(
        result="PASS",
        baseline=BASELINE,
        fingerprint=envelope["fingerprint"],
        protected_files=len(tracked),
        new_files=len(actual),
        receipts=receipts,
        rows=len(rows),
        qualified=0,
        preserved=preserved,
        scans=scan,
        real_kite_requests=0,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
