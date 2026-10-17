"""Read-only Phase 14.33 manifest replay; no network, writes or regeneration."""

import importlib.util
import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

from strategy_engine.phase1416.imports import canonical, sha

ROOT = Path(__file__).resolve().parents[2]


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def module(name: str, relative: str) -> Any:
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    assert spec and spec.loader
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def verify() -> dict[str, Any]:
    base = ROOT / "research/phase-14.33/generation-1"
    envelope = json.loads((base / "evidence-manifest.json").read_bytes())
    m = envelope["manifest"]
    assert envelope["fingerprint"] == sha(canonical(m))
    assert m["version"] == "Phase1433EvidenceManifest.v1"
    assert git("branch", "--show-current").strip() == b"develop"
    assert git("rev-parse", "HEAD", "origin/develop").decode().splitlines() == [m["baseline"]] * 2
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    tracked = set(git("ls-files").decode().splitlines())
    assert tracked == set(m["baseline_hashes"]) and len(tracked) == 1443
    assert set(git("ls-files", "--others", "--exclude-standard").decode().splitlines()) == (
        set(m["new_files"]) | set(m["preservation_exceptions"])
    )
    for group in ("baseline_hashes", "artifacts", "preservation_exceptions", "external_evidence"):
        for name, digest in m[group].items():
            assert sha((ROOT / name).read_bytes()) == digest, name
    assert sha((ROOT / "docs/operations/phase-14.33-plan.md").read_bytes()) == m["plan_hash"]
    for name in m["new_files"]:
        raw = (ROOT / name).read_bytes()
        assert b"\r" not in raw and raw.endswith(b"\n"), name
        assert all(line.rstrip(b" \t") == line for line in raw.splitlines()), name
        assert git("hash-object", name) == git("hash-object", "--no-filters", name), name
    fresh = module("prepare1433", "scripts/research/prepare_phase1433.py").prepare()
    for filename, value in fresh.items():
        assert value == json.loads((base / filename).read_bytes())
        assert value["fingerprint"] == m["report_fingerprints"][filename]
    report = fresh["preparation-report.json"]["manifest"]
    assert report["independent_metadata_matches"] == 8
    assert report["artifacts_downloaded"] == report["real_raw_archive_hashes_verified"] == 0
    assert report["quarantine_created"] is False
    assert fresh["acquisition-approval.json"]["manifest"]["decision"] == "PENDING"
    totals = {}
    for row in m["junit"]:
        raw = Path(row["path"]).read_bytes()
        assert sha(raw) == row["sha256"]
        tree = ET.fromstring(raw)
        assert not any(list(tree.iter(k)) for k in ("failure", "error", "skipped"))
        assert len(list(tree.iter("testcase"))) == row["tests"]
        totals[row["id"]] = row["tests"]
    for row in m["commands"]:
        p = Path(row["path"])
        assert sha(p.read_bytes()) == row["sha256"]
        receipt = json.loads(p.read_bytes())
        assert receipt["exit_code"] == row["exit_code"] and receipt["protected_changed"] == []
        for stream in ("stdout", "stderr"):
            assert sha(p.with_suffix("." + stream).read_bytes()) == receipt[stream + "_sha256"]
    for name in m["required_commands"]:
        assert [r["exit_code"] for r in m["commands"] if r["id"] == name] == [0]
    scanner = module("scan1433", "scripts/research/verify_phase1415d.py")
    scans = scanner.scan(sorted(tracked | set(m["new_files"])))
    family = scanner.reports()
    git("diff", "--check")
    return dict(
        verification="PASS", baseline=m["baseline"], manifest=envelope["fingerprint"],
        reports=m["report_fingerprints"], protected_unchanged=len(tracked),
        new_files=len(m["new_files"]), tests=totals, scans=scans, family=family,
        classification="PHASE_14_33_ACQUISITION_READY_FOR_OWNER_DECISION",
        independent_metadata_matches=8, raw_archive_hashes_verified=0,
        acquisition="PENDING", mode="PREPARATION_ONLY", quarantine_created=False,
        installation="NOT_AUTHORIZED", clean_installation="NOT_ATTEMPTED",
        recommendation="OFFLINE_RC_CONDITIONAL", network_requests=0,
        archive_downloads=0, installations=0, real_kite_requests=0,
        Gate_A="NOT_AUTHORIZED", Gate_B="NOT_AUTHORIZED", operational="NO_GO",
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
