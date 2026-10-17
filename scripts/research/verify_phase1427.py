"""Verify the sealed RC assessment; replay only new synthetic external fixtures."""

import importlib.util
import json
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

from strategy_engine.phase1415.execution import execution_fingerprint
from strategy_engine.phase1415.hardening import frozen_policy
from strategy_engine.phase1415.strategies import registry
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1419.scheduler import EXECUTION_HASH, RISK_HASH
from strategy_engine.phase1424.analytics import MONITORING_POLICY
from strategy_engine.phase1426.alerts import ALERT_POLICY
from strategy_engine.phase1426.authority import AUTHORITY_POLICY
from strategy_engine.phase1427.assessment import (
    dependencies,
    identity,
    source_archive,
    source_inventory,
)

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.27/generation-1"


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def policies() -> dict[str, str]:
    assert execution_fingerprint() == EXECUTION_HASH
    return dict(
        strategies=sha(canonical([s.model_dump(mode="json") for s in registry()])),
        qualification=sha(canonical(frozen_policy().model_dump(mode="json"))),
        risk=RISK_HASH,
        execution=EXECUTION_HASH,
        monitoring=sha(
            canonical({"matching": MONITORING_POLICY, "assurance": ALERT_POLICY})
        ),
        authority=sha(canonical(AUTHORITY_POLICY)),
    )


def verify() -> dict[str, Any]:
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    manifest = envelope["manifest"]
    assert sha(canonical(manifest)) == envelope["fingerprint"]
    m = manifest["payload"]
    assert manifest["evidence_payload_fingerprint"] == sha(canonical(m))
    assert git("branch", "--show-current").strip() == b"develop"
    for ref in ("HEAD", "origin/develop"):
        assert git("rev-parse", ref).decode().strip() == m["baseline"]
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    tracked = set(git("ls-files").decode().splitlines())
    new = set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
    assert tracked == set(m["baseline_hashes"]) and len(tracked) == 1351
    assert new == set(m["new_files"]) | set(m["preservation_exceptions"])
    assert set(m["artifacts"]) == set(m["new_files"]) - {
        "research/phase-14.27/generation-1/evidence-manifest.json"
    }
    for n, h in (
        m["baseline_hashes"] | m["artifacts"] | m["preservation_exceptions"]
    ).items():
        assert sha((ROOT / n).read_bytes()) == h, n
    for n, h in m["external_evidence"].items():
        assert sha(Path(n).read_bytes()) == h, n
    for n in m["new_files"]:
        raw = (ROOT / n).read_bytes()
        assert b"\r" not in raw and raw.endswith(b"\n"), n
        assert all(x.rstrip(b" \t") == x for x in raw.splitlines()), n
        assert git("hash-object", n) == git("hash-object", "--no-filters", n), n
    report = json.loads((DIRECTORY / "offline-rc-assessment.json").read_bytes())
    assert sha(canonical(report)) == m["report_fingerprint"]
    assert source_inventory() == report["source"]
    assert dependencies() == report["dependencies"]
    assert policies() == m["policy_fingerprints"]
    tests = {}
    for entry in m["junit"]:
        root = ET.fromstring(Path(entry["path"]).read_bytes())
        cases = list(root.iter("testcase"))
        assert len(cases) == entry["tests"] > 0
        assert not any(list(root.iter(k)) for k in ("failure", "error", "skipped"))
        assert all(
            int(s.attrib[k]) == 0
            for s in root.iter("testsuite")
            for k in ("failures", "errors", "skipped")
        )
        assert sha(Path(entry["path"]).read_bytes()) == entry["sha256"]
        tests[entry["id"]] = len(cases)
    for c in m["commands"]:
        p = Path(c["path"])
        assert sha(p.read_bytes()) == c["sha256"]
        receipt = json.loads(p.read_bytes())
        assert (
            receipt["exit_code"] == c["exit_code"]
            and receipt["protected_changed"] == []
        )
        for ext in ("stdout", "stderr"):
            assert (
                sha(p.with_suffix("." + ext).read_bytes()) == receipt[ext + "_sha256"]
            )
    for name in m["required_commands"]:
        assert [c["exit_code"] for c in m["commands"] if c["id"] == name] == [0]
    receipt_fingerprint = sha(
        canonical({"commands": m["commands"], "junit": m["junit"]})
    )
    bindings = dict(
        git_sha=m["baseline"],
        software=report["source"]["fingerprint"],
        dependencies=sha(canonical(report["dependencies"])),
        **policies(),
        evidence_payload=sha(canonical(m)),
        build_environment=sha(canonical(report["build"]["environment"])),
        test_receipts=receipt_fingerprint,
    )
    assert identity(bindings) == manifest["rc_identity"]
    assert report["classification"] == "PHASE_14_27_PARTIAL"
    assert report["recommendation"] == "OFFLINE_RC_CONDITIONAL"
    assert report["engineering"] == "PARTIAL" and report["owner_approval"] is None
    assert (
        report["package_runtime"]["resource_binding"] == "UNRESOLVED_STANDALONE_WHEEL"
    )
    assert (
        report["dependencies"]["archive_origin"]
        == "UNVERIFIED_WITHOUT_ORIGINAL_ARCHIVE_BYTES"
    )
    assert report["security"]["guard"] == "PASS"
    assert report["security"]["blocked_negative_probes"] == 3
    assert not report["security"]["unexpected_runtime_modules"]
    assert report["security"]["production_egress"] == "PRODUCTION_EGRESS_NOT_VERIFIED"
    sys.path.insert(0, str(ROOT / "apps/strategy-engine/tests"))
    from test_phase1427 import build_reports

    with tempfile.TemporaryDirectory(prefix="phase1427-replay-") as temp:
        directory = Path(temp)
        replay = build_reports(directory / "scenarios")
        assert replay == report["scenarios"], "SCENARIO_REPLAY"
        assert (
            source_archive(directory / "archive", report["source"]["inventory"])
            == report["build"]["source_archive"]
        )
    for row in report["scenarios"]["scenarios"]:
        assert sha(canonical(row["inputs"])) == row["input_fingerprint"]
        assert (
            sha(
                canonical(
                    {
                        k: v
                        for k, v in row.items()
                        if k not in {"input_fingerprint", "fingerprint"}
                    }
                )
            )
            == row["fingerprint"]
        )
    spec = importlib.util.spec_from_file_location(
        "protected1427", ROOT / "scripts/research/verify_phase1415d.py"
    )
    assert spec and spec.loader
    preserved = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(preserved)
    protection = preserved.protected(m["baseline"])
    family = preserved.reports()
    scans = preserved.scan(sorted(tracked | new))
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    return dict(
        verification="PASS",
        classification=report["classification"],
        recommendation=report["recommendation"],
        baseline=m["baseline"],
        rc_id=manifest["rc_identity"]["rc_id"],
        manifest=envelope["fingerprint"],
        evidence_payload=manifest["evidence_payload_fingerprint"],
        report=m["report_fingerprint"],
        tests=tests,
        protected=len(tracked),
        protected_details=protection,
        family=family,
        scans=scans,
        new_files=len(m["new_files"]),
        real_kite_requests=0,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
