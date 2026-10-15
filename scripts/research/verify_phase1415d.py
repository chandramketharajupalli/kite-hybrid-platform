"""Read-only Phase14.15D certification replay. Python -B; no broker imports."""

import ast
import csv
import hashlib
import importlib.util
import io
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.15d/generation-1"


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def canonical(value):
    return json.dumps(
        value, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
    ).encode()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def reports():
    universe_path = ROOT / "config/universes/phase14_unique_universe.csv"
    symbols = [
        r["symbol"]
        for r in csv.DictReader(io.StringIO(universe_path.read_text(encoding="utf-8-sig")))
    ]
    expected = [(s, m, f"S{i:02d}") for s in symbols for m in (6, 12) for i in range(1, 7)]
    require(len(symbols) == len(set(symbols)) == 1115, "UNIVERSE_MISMATCH")
    hashes = {}
    for sub in ("", "ist-corrected/"):
        base = ROOT / "research/phase-14.15/generation-1" / sub
        report = json.loads((base / "pair-results.json").read_bytes())
        rows = report["rows"]
        require([(r[1], r[4], r[2]) for r in rows] == expected, "MATRIX_INCOMPLETE")
        require(
            all(
                r[5] == "UNAVAILABLE" and r[6:10] == [None] * 4 and r[10] is False and r[13] is None
                for r in rows
            ),
            "UNSUPPORTED_PERFORMANCE",
        )
        require(
            all(
                r[12] == report["strategy_hashes"][r[2]] and r[14] == report["policy_hash"]
                for r in rows
            ),
            "ROW_FINGERPRINT_MISMATCH",
        )
        require(report["universe_sha256"] == sha(universe_path.read_bytes()), "UNIVERSE_HASH")
        require(
            report["policy"]["family_size"] == 13380 and report["policy"]["min_win_rate"] == "0.70",
            "POLICY_CHANGED",
        )
        csv_rows = list(csv.reader(io.StringIO((base / "pair-results.csv").read_text())))
        require(
            csv_rows[0] == report["columns"]
            and csv_rows[1:] == [["" if v is None else str(v) for v in row] for row in rows],
            "CSV_JSON_MISMATCH",
        )
        for name in ("pair-results.csv", "pair-results.json"):
            hashes[sub + name] = sha((base / name).read_bytes())
    return dict(
        symbols=1115,
        strategies=6,
        windows=[6, 12],
        rows=13380,
        per_window=6690,
        qualified=0,
        performance=None,
        raw_sha256=hashes,
    )


def scan(paths):
    scanner = ast.parse((ROOT / "scripts/check-secrets.py").read_text(encoding="utf-8"))
    patterns = None
    for node in scanner.body:
        if isinstance(node, ast.Assign) and any(
            isinstance(t, ast.Name) and t.id == "PATTERNS" for t in node.targets
        ):
            patterns = eval(
                compile(ast.Expression(node.value), "scanner_patterns", "eval"), {"re": re}
            )
    require(patterns is not None, "SCANNER_UNAVAILABLE")
    canary = re.compile(r"(?:Bearer +[A-Za-z0-9._-]{20,}|CANARY_SECRET_[A-Za-z0-9]{8,})")
    require(canary.search("CANARY_" + "SECRET_" + "X" * 12), "CANARY_BROKEN")
    additions = set(
        git("ls-files", "--others", "--exclude-standard", "-z").decode().split("\0")[:-1]
    )
    count = 0
    for name in paths:
        path = ROOT / name
        if path.name.startswith(".env") and path.name != ".env.example":
            continue
        if any(part in {"__pycache__", ".gradle", "target", "build"} for part in path.parts):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        require(
            not any(p.search(line) for p in patterns for line in text.splitlines()),
            "SECRET_PATTERN:" + name,
        )
        if name in additions:
            require(not canary.search(text), "CANARY_PATTERN:" + name)
        count += 1
    return dict(text_files=count, secret_findings=0, canary_findings=0)


def protected(baseline):
    parent = module("preserved145d", "scripts/research/verify_phase145.py")
    parent.BASELINE = baseline
    result = parent.verify()
    b = module("receipts1415b", "scripts/research/verify_phase1415b.py")
    for phase in ("14.6", "14.7", "14.8", "14.9", "14.10", "14.11", "14.12", "14.13", "14.14"):
        b.envelope(ROOT / f"research/phase-{phase}/generation-1/evidence-manifest.json")
    return result


def verify():
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    manifest = envelope["manifest"]
    require(sha(canonical(manifest)) == envelope["fingerprint"], "MANIFEST_FINGERPRINT")
    baseline = manifest["baseline"]
    for ref in ("HEAD", "origin/develop"):
        require(git("rev-parse", ref).decode().strip() == baseline, "BASELINE_CHANGED")
    require(git("branch", "--show-current").decode().strip() == "develop", "BRANCH_CHANGED")
    require(
        not git("diff", "--name-only") and not git("diff", "--cached", "--name-only"),
        "TRACKED_OR_INDEX_CHANGED",
    )
    original = json.loads((DIRECTORY / "original-inventory.json").read_bytes())
    for name, expected in original["baseline_raw_sha256"].items():
        require(sha((ROOT / name).read_bytes()) == expected, "PROTECTED_CHANGED:" + name)
    require(
        set(git("ls-files", "-z").decode().split("\0")[:-1])
        == set(original["baseline_raw_sha256"]),
        "TRACKED_INVENTORY",
    )
    changes = {r["path"]: r for r in manifest["source_changes"]}
    for name, expected in original["artifacts_raw_sha256"].items():
        require(
            sha((Path(manifest["original_archive"]) / name).read_bytes()) == expected,
            "ORIGINAL_ARCHIVE_CHANGED:" + name,
        )
        if name not in changes:
            require(
                sha((ROOT / name).read_bytes()) == expected, "HISTORICAL_ARTIFACT_CHANGED:" + name
            )
        else:
            require(changes[name]["before_sha256"] == expected, "SOURCE_CHANGE_PROVENANCE")
    for name, expected in manifest["artifacts_raw_sha256"].items():
        require(sha((ROOT / name).read_bytes()) == expected, "ARTIFACT_CHANGED:" + name)
    actual = git("ls-files", "--others", "--exclude-standard", "-z").decode().split("\0")[:-1]
    inventory = json.loads((DIRECTORY / "git-inventory.json").read_bytes())
    require(sorted(actual) == inventory["new_files"], "UNACCOUNTED_ARTIFACT")
    for name in actual:
        raw = (ROOT / name).read_bytes()
        require(
            b"\r" not in raw and all(x.rstrip(b" \t") == x for x in raw.splitlines()),
            "LF_OR_WHITESPACE:" + name,
        )
        direct = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
        require(
            git("hash-object", "--path=" + name, name).decode().strip() == direct,
            "GIT_BYTES:" + name,
        )
    dep = json.loads((DIRECTORY / "dependency-inventory.json").read_bytes())
    require(
        [d["path"] for d in dep["inventory"]] == sorted({d["path"] for d in dep["inventory"]}),
        "DEPENDENCY_ORDER",
    )
    for item in dep["inventory"]:
        require(
            sha((ROOT / "apps/strategy-engine/src" / item["path"]).read_bytes()) == item["sha256"],
            "DEPENDENCY_CHANGED",
        )
    require(sha(canonical(dep["inventory"])) == dep["software_hash"], "SOFTWARE_HASH")
    policy_record = json.loads((DIRECTORY / "policy-binding.json").read_bytes())
    binding = json.loads(policy_record["serialized_binding"])
    require(
        sha(policy_record["serialized_binding"].encode()) == policy_record["run_id"],
        "RUN_ID_MISMATCH",
    )
    require(
        binding["hypotheses"] == 1115 * 6 * 2 and len(set(binding["symbols"])) == 1115,
        "FULL_FAMILY_SIZE",
    )
    require(
        binding["software_hash"] == dep["software_hash"]
        and binding["dependency_inventory"] == dep["inventory"],
        "FAMILY_SOFTWARE_BINDING",
    )
    require(
        sha(policy_record["serialized_policy"].encode()) == binding["policy_hash"],
        "POLICY_FINGERPRINT",
    )
    policy = json.loads(policy_record["serialized_policy"])
    require(
        policy
        == dict(
            min_oos_trades=100,
            min_fold_trades=30,
            min_win_rate="0.70",
            max_drawdown="0.10",
            min_fold_win_rate="0.60",
            family_size=13380,
            alpha="0.05",
        ),
        "FROZEN_POLICY_CHANGED",
    )
    b = module("receipts1415d", "scripts/research/verify_phase1415b.py")
    receipts = json.loads((DIRECTORY / "test-results.json").read_bytes())
    current = b.verify_receipts(
        receipts["current"], receipts["required_current"], scope="FRESH", require_command=True
    )
    historical = b.verify_receipts(
        receipts["historical_b"], receipts["required_historical_b"], scope="HISTORICAL"
    )
    previous = b.verify_receipts(
        receipts["previous_c"], ["focused", "full"], scope="PREVIOUS_C", require_command=True
    )
    for tool in receipts["tool_receipts"]:
        path = Path(tool["path"])
        require(
            path.is_file() and sha(path.read_bytes()) == tool["sha256"], "TOOL_RECEIPT_UNAVAILABLE"
        )
        value = json.loads(path.read_bytes())
        require(value["exit_code"] == 0 and value["command"], "TOOL_FAILED")
        for stream in ("stdout", "stderr"):
            raw = Path(tool[stream]).read_bytes()
            require(sha(raw) == value[stream + "_sha256"], "TOOL_OUTPUT_CHANGED")
    preserved = protected(baseline)
    report = reports()
    secrets = scan(sorted(set(actual) | set(original["baseline_raw_sha256"])))
    return dict(
        result="PASS",
        fingerprint=envelope["fingerprint"],
        software_hash=dep["software_hash"],
        baseline=baseline,
        baseline_files=1150,
        original_artifacts=27,
        current_artifacts=len(actual),
        protected=preserved,
        reports=report,
        receipts=current,
        historical_receipts=historical,
        previous_c=previous,
        scans=secrets,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        readiness="NO_GO",
        real_Kite_requests=0,
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True, indent=2))
