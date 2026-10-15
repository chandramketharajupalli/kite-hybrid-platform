"""Read-only Phase 14.15B preservation and deterministic manifest replay.

Run with PYTHONDONTWRITEBYTECODE=1 and python -B. No strategy imports or network.
"""

import ast
import hashlib
import importlib.util
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.15b/generation-1"


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def canonical(value):
    return json.dumps(
        value, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
    ).encode()


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def envelope(path):
    value = json.loads(path.read_bytes())
    assert sha(canonical(value["manifest"])) == value["fingerprint"], str(path)
    for group, entries in value["manifest"].items():
        if group.endswith("raw_sha256") and isinstance(entries, dict):
            for name, expected in entries.items():
                assert sha((ROOT / name).read_bytes()) == expected, name
    return value


def verify_receipts(records, required_ids, *, scope, require_command=False):
    """Verify accepted receipts; retained historical failures are never accepted passes."""
    identifiers = [r.get("id") for r in records]
    if (
        not required_ids
        or len(set(required_ids)) != len(required_ids)
        or len(set(identifiers)) != len(identifiers)
        or set(identifiers) != set(required_ids)
    ):
        raise ValueError("RECEIPT_INVENTORY_CONFLICT")
    checked = []
    for record in records:
        if record.get("scope") != scope:
            raise ValueError("RECEIPT_SCOPE_CONFLICT")
        path = Path(record["path"])
        if not path.is_file():
            raise ValueError("RECEIPT_UNAVAILABLE:" + record["id"])
        raw = path.read_bytes()
        if sha(raw) != record["sha256"]:
            raise ValueError("RECEIPT_HASH_MISMATCH")
        try:
            root = ET.fromstring(raw)
            if root.tag not in {"testsuites", "testsuite"}:
                raise ValueError("RECEIPT_XML_ROOT")
            suites = list(root.iter("testsuite"))
            if not suites or any(list(s.iter("testsuite"))[1:] for s in suites):
                raise ValueError("RECEIPT_XML_STRUCTURE")
            totals = {
                k: sum(int(s.attrib[k]) for s in suites)
                for k in ("tests", "failures", "errors", "skipped")
            }
            cases = list(root.iter("testcase"))
            names = [(c.get("classname"), c.get("name")) for c in cases]
            if (
                totals["tests"] <= 0
                or len(cases) != totals["tests"]
                or len(set(names)) != len(names)
                or any(not n[1] for n in names)
            ):
                raise ValueError("RECEIPT_ZERO_OR_INCOMPLETE")
            for suite in suites:
                children = list(suite.findall("testcase"))
                if len(children) != int(suite.attrib["tests"]):
                    raise ValueError("RECEIPT_SUITE_COUNT_MISMATCH")
                for key, tag in (
                    ("failures", "failure"),
                    ("errors", "error"),
                    ("skipped", "skipped"),
                ):
                    if sum(c.find(tag) is not None for c in children) != int(suite.attrib[key]):
                        raise ValueError("RECEIPT_CASE_STATUS_MISMATCH")
            if any(totals[k] != record[k] for k in totals):
                raise ValueError("RECEIPT_TOTAL_MISMATCH")
        except (ET.ParseError, KeyError, TypeError, OverflowError) as exc:
            raise ValueError("RECEIPT_MALFORMED_XML_OR_COUNTS") from exc
        accepted = record.get("accepted", True)
        if not accepted and scope != "HISTORICAL":
            raise ValueError("FRESH_FAILURE_CANNOT_BE_ACCEPTED")
        if accepted and any(totals[k] for k in ("failures", "errors", "skipped")):
            raise ValueError("RECEIPT_NOT_PASS")
        if require_command:
            evidence = record.get("command_receipt")
            if not evidence or not Path(evidence).is_file():
                raise ValueError("COMMAND_EVIDENCE_UNAVAILABLE")
            command_raw = Path(evidence).read_bytes()
            if sha(command_raw) != record.get("command_sha256"):
                raise ValueError("COMMAND_EVIDENCE_HASH_MISMATCH")
            command = json.loads(command_raw)
            if (
                not isinstance(command.get("command"), list)
                or not command["command"]
                or command.get("exit_code") != 0
                or command.get("name") != record["id"]
                or command.get("xml_sha256") != record["sha256"]
            ):
                raise ValueError("COMMAND_OR_EXIT_EVIDENCE_INVALID")
        checked.append(
            {
                "id": record["id"],
                "tests": totals["tests"],
                "status": "PASS" if accepted else "HISTORICAL_FAILURE_RETAINED",
            }
        )
    return checked


def verify():
    successor = ROOT / "research/phase-14.15d/generation-1/evidence-manifest.json"
    if successor.exists():
        spec = importlib.util.spec_from_file_location(
            "phase1415d", ROOT / "scripts/research/verify_phase1415d.py"
        )
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module.verify()
    value = envelope(DIRECTORY / "evidence-manifest.json")
    manifest = value["manifest"]
    assert git("rev-parse", "HEAD").decode().strip() == manifest["baseline"]
    assert git("rev-parse", "origin/develop").decode().strip() == manifest["baseline"]
    assert git("branch", "--show-current").decode().strip() == "develop"
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    diagnostic = json.loads((DIRECTORY / "diagnostic-inventory.json").read_bytes())
    tracked = git("ls-files", "-z").decode().split("\0")[:-1]
    assert set(tracked) == set(diagnostic["baseline_raw_sha256"])
    for name, expected in diagnostic["baseline_raw_sha256"].items():
        assert sha((ROOT / name).read_bytes()) == expected, name
    inventory = json.loads((DIRECTORY / "git-inventory.json").read_bytes())
    actual = git("ls-files", "--others", "--exclude-standard", "-z").decode().split("\0")[:-1]
    assert sorted(actual) == inventory["new_files"]
    for name in actual:
        raw = (ROOT / name).read_bytes()
        assert b"\r" not in raw, name
        assert all(line.rstrip(b" \t") == line for line in raw.splitlines()), name
        direct = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
        assert git("hash-object", "--path=" + name, name).decode().strip() == direct, name
    spec = importlib.util.spec_from_file_location(
        "preserved145", ROOT / "scripts/research/verify_phase145.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.BASELINE = manifest["baseline"]
    protected = module.verify()
    for phase in (
        "14.6",
        "14.7",
        "14.8",
        "14.9",
        "14.10",
        "14.11",
        "14.12",
        "14.13",
        "14.14",
    ):
        envelope(ROOT / f"research/phase-{phase}/generation-1/evidence-manifest.json")
    for name, expected in manifest["historical_report_hashes"].items():
        assert sha((ROOT / name).read_bytes()) == expected, name
    report = json.loads(
        (ROOT / "research/phase-14.15/generation-1/ist-corrected/pair-results.json").read_bytes()
    )
    rows = report["rows"]
    assert len(rows) == 13380 and len({(r[1], r[2], r[4]) for r in rows}) == 13380
    assert all(r[6:10] == [None] * 4 and r[10] is False for r in rows)
    assert sum(r[4] == 6 for r in rows) == sum(r[4] == 12 for r in rows) == 6690
    # Use the repository scanner's exact patterns without traversing private environments.
    scanner = ast.parse((ROOT / "scripts/check-secrets.py").read_text(encoding="utf-8"))
    patterns = None
    for node in scanner.body:
        if isinstance(node, ast.Assign) and any(
            isinstance(t, ast.Name) and t.id == "PATTERNS" for t in node.targets
        ):
            patterns = eval(
                compile(ast.Expression(node.value), "scanner_patterns", "eval"),
                {"re": re},
            )
    assert patterns
    canary = re.compile(r"(?:Bearer +[A-Za-z0-9._-]{20,}|CANARY_SECRET_[A-Za-z0-9]{8,})")
    assert canary.search("CANARY_" + "SECRET_" + "X" * 12)
    tests = json.loads((DIRECTORY / "test-results.json").read_bytes())["fresh"]
    accepted = next(t for t in tests if t["attempt"] == "focused-accepted")
    full = next(t for t in tests if t["attempt"] == "full")
    assert accepted["tests"] == 52 and full["tests"] == 334
    assert all(t[k] == 0 for t in (accepted, full) for k in ("failures", "errors", "skipped"))
    verify_receipts(
        [
            dict(
                id=t["attempt"],
                scope="HISTORICAL",
                path=t["external_evidence"],
                sha256=t["junit_sha256"],
                tests=t["tests"],
                failures=t["failures"],
                errors=t["errors"],
                skipped=t["skipped"],
                accepted=t["failures"] == 0,
            )
            for t in tests
        ],
        [t["attempt"] for t in tests],
        scope="HISTORICAL",
    )
    for attempt in tests:
        external = Path(attempt["external_evidence"])
        if not external.is_file():
            raise ValueError("RECEIPT_UNAVAILABLE")
        if external.is_file():
            raw = external.read_bytes()
            assert sha(raw) == attempt["junit_sha256"]
            text = raw.decode("utf-8")
            assert not any(
                pattern.search(line) for pattern in patterns for line in text.splitlines()
            )
            assert not canary.search(text)
    scanned = 0
    for name in sorted(set(tracked + actual)):
        path = ROOT / name
        if path.name.startswith(".env") and path.name != ".env.example":
            continue
        if any(p in {".gradle", "__pycache__", "target", "build"} for p in path.parts):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        assert not any(
            pattern.search(line) for pattern in patterns for line in text.splitlines()
        ), "SECRET:" + name
        if name in actual:
            assert not canary.search(text), "CANARY:" + name
        scanned += 1
    return {
        "result": "PASS",
        "fingerprint": value["fingerprint"],
        "baseline_files": len(tracked),
        "additions": len(actual),
        "report_rows": len(rows),
        "secret_scanned_text_files": scanned,
        "canary_findings": 0,
        "protected": protected,
        "real_Kite_requests": 0,
    }


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True, indent=2))
