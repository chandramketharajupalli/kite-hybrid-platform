"""Read-only Gate 31-A evidence replay. No acquisition, install or cache mutation."""

import importlib.util
import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, sha


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def verify() -> dict[str, object]:
    base = ROOT / "research/phase-14.31/generation-1"
    envelope = json.loads((base / "evidence-manifest.json").read_bytes())
    m = envelope["manifest"]
    assert sha(canonical(m)) == envelope["fingerprint"]
    assert git("branch", "--show-current").strip() == b"develop"
    assert git("rev-parse", "HEAD", "origin/develop").decode().splitlines() == [m["baseline"]] * 2
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    tracked = set(git("ls-files").decode().splitlines())
    assert tracked == set(m["baseline_hashes"]) and len(tracked) == 1412
    assert set(git("ls-files", "--others", "--exclude-standard").decode().splitlines()) == (
        set(m["new_files"]) | set(m["preservation_exceptions"])
    )
    for group in (
        m["baseline_hashes"],
        m["artifacts"],
        m["preservation_exceptions"],
        m["external_evidence"],
    ):
        for name, expected in group.items():
            assert sha((ROOT / name).read_bytes()) == expected, name
    for name in m["new_files"]:
        raw = (ROOT / name).read_bytes()
        assert b"\r" not in raw and raw.endswith(b"\n"), name
        assert all(line.rstrip(b" \t") == line for line in raw.splitlines()), name
        assert git("hash-object", name) == git("hash-object", "--no-filters", name), name
    assert sha((ROOT / "docs/operations/phase-14.31-plan.md").read_bytes()) == m["plan_hash"]
    spec = importlib.util.spec_from_file_location(
        "phase1431_preparation", ROOT / "scripts/research/prepare_phase1431.py"
    )
    assert spec and spec.loader
    generator = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(generator)
    fresh = generator.prepare()
    for name, key in (
        ("artifact-acquisition-manifest.json", "acquisition"),
        ("acquisition-authorization.json", "authorization"),
    ):
        stored = json.loads((base / name).read_bytes())
        assert stored == fresh[key]
        assert sha(canonical(stored["manifest"])) == stored["fingerprint"]
        assert stored["fingerprint"] == m[key + "_fingerprint"]
    acquisition = fresh["acquisition"]["manifest"]
    request = fresh["authorization"]["manifest"]
    assert acquisition["expected_lock_digest_coverage"] == 8
    assert acquisition["independent_trusted_digest_coverage"] == 0
    assert acquisition["missing_artifact_count"] == 8
    assert request["decision"] == "PENDING" and request["owner_decision_evidence"] is None
    assert not request["download_authorized"] and request["installation_status"] == "NOT_ATTEMPTED"
    assert acquisition["downloads"] == acquisition["installations"] == 0
    assert acquisition["recommendation"] == "OFFLINE_RC_CONDITIONAL"
    totals = {}
    for row in m["junit"]:
        p = Path(row["path"])
        assert sha(p.read_bytes()) == row["sha256"]
        xml = ET.fromstring(p.read_bytes())
        assert not any(list(xml.iter(k)) for k in ("failure", "error", "skipped"))
        assert len(list(xml.iter("testcase"))) == row["tests"]
        totals[row["id"]] = row["tests"]
    for row in m["commands"]:
        p = Path(row["path"])
        assert sha(p.read_bytes()) == row["sha256"]
        receipt = json.loads(p.read_bytes())
        assert receipt["exit_code"] == row["exit_code"] and not receipt["protected_changed"]
        for ext in ("stdout", "stderr"):
            assert sha(p.with_suffix("." + ext).read_bytes()) == receipt[ext + "_sha256"]
    for name in m["required_commands"]:
        assert [r["exit_code"] for r in m["commands"] if r["id"] == name] == [0]
    spec = importlib.util.spec_from_file_location(
        "phase1431_scan", ROOT / "scripts/research/verify_phase1415d.py"
    )
    assert spec and spec.loader
    scanner = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(scanner)
    family = scanner.reports()
    scans = scanner.scan(sorted(tracked | set(m["new_files"])))
    git("diff", "--check")
    return dict(
        verification="PASS",
        baseline=m["baseline"],
        manifest=envelope["fingerprint"],
        acquisition=m["acquisition_fingerprint"],
        authorization=m["authorization_fingerprint"],
        classification="PHASE_14_31_PARTIAL",
        acquisition_authorization="PENDING",
        installation_authorization="NOT_AUTHORIZED",
        clean_installation="NOT_ATTEMPTED",
        recommendation="OFFLINE_RC_CONDITIONAL",
        missing_artifacts=8,
        lock_digest_coverage=8,
        independent_trusted_digest_coverage=0,
        tests=totals,
        protected_unchanged=1412,
        new_files=len(m["new_files"]),
        family=family,
        scans=scans,
        downloads=0,
        installations=0,
        real_kite_requests=0,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
