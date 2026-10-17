"""Read-only sealed standalone package evidence verification; no broker or rebuild."""

import importlib.util
import json
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1427.assessment import dependencies
from strategy_engine.phase1428.contract import MANIFEST_HASH
from strategy_engine.phase1428.resources import load
from strategy_engine.phase1428.runtime import assess


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def verify() -> dict[str, object]:
    directory = ROOT / "research/phase-14.28/generation-1"
    envelope = json.loads((directory / "evidence-manifest.json").read_bytes())
    m = envelope["manifest"]
    assert sha(canonical(m)) == envelope["fingerprint"]
    assert git("branch", "--show-current").strip() == b"develop"
    for ref in ("HEAD", "origin/develop"):
        assert git("rev-parse", ref).decode().strip() == m["baseline"]
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    tracked = set(git("ls-files").decode().splitlines())
    new = set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
    assert len(tracked) == 1364 and tracked == set(m["baseline_hashes"])
    assert new == set(m["new_files"]) | set(m["preservation_exceptions"])
    for name, expected in (
        m["baseline_hashes"] | m["artifacts"] | m["preservation_exceptions"]
    ).items():
        assert sha((ROOT / name).read_bytes()) == expected, name
    for name, expected in m["external_evidence"].items():
        assert sha(Path(name).read_bytes()) == expected, name
    for name in m["new_files"]:
        raw = (ROOT / name).read_bytes()
        assert raw.endswith(b"\n")
        if not name.endswith("/data/universe.csv"):
            assert b"\r" not in raw, name
        assert all(line.rstrip(b" \t") == line for line in raw.splitlines()), name
        assert git("hash-object", name) == git("hash-object", "--no-filters", name), name
    report = json.loads((directory / "standalone-assessment.json").read_bytes())
    assert sha(canonical(report)) == m["report_fingerprint"]
    assert load()["manifest"] == report["resource_contract"]
    assert MANIFEST_HASH == m["resource_contract_fingerprint"]
    assert assess() == report["build"]["probe"]["assessment"]
    assert dependencies() == json.loads(Path(m["dependency_evidence"]).read_bytes())
    assert report["classification"] == "PHASE_14_28_PARTIAL"
    assert report["recommendation"] == "OFFLINE_RC_CONDITIONAL"
    assert report["build"]["backend_archive_provenance"] == "UNVERIFIED"
    assert all(
        row["classification"] == "INSTALLED_RECORD_VERIFIED"
        and not row["archive_matches"]
        for row in report["build"]["dependency_provenance"]
    )
    tests = {}
    for entry in m["junit"]:
        raw = Path(entry["path"]).read_bytes()
        assert sha(raw) == entry["sha256"]
        root = ET.fromstring(raw)
        assert not any(list(root.iter(k)) for k in ("failure", "error", "skipped"))
        assert len(list(root.iter("testcase"))) == entry["tests"]
        tests[entry["id"]] = entry["tests"]
    for entry in m["commands"]:
        p = Path(entry["path"])
        assert sha(p.read_bytes()) == entry["sha256"]
        receipt = json.loads(p.read_bytes())
        assert receipt["exit_code"] == entry["exit_code"]
        assert receipt["protected_changed"] == []
        for ext in ("stdout", "stderr"):
            assert sha(p.with_suffix("." + ext).read_bytes()) == receipt[ext + "_sha256"]
    for name in m["required_commands"]:
        assert [c["exit_code"] for c in m["commands"] if c["id"] == name] == [0]
    build = report["build"]
    out = Path(m["build_directory"])
    for label in ("a", "b"):
        wheel = next((out / ("build-" + label) / "wheel").glob("*.whl"))
        source = out / ("build-" + label) / "offline-source.zip"
        assert sha(wheel.read_bytes()) == build["builds"][0]["wheel"]
        assert sha(source.read_bytes()) == build["builds"][0]["source"]
        with zipfile.ZipFile(wheel) as archive:
            expected_names = {n[4:] for n in build["inventory"] if n.startswith("src/")}
            actual_names = {n for n in archive.namelist() if ".dist-info/" not in n}
            assert actual_names == expected_names
            for name, expected in build["inventory"].items():
                if name.startswith("src/"):
                    assert sha(archive.read(name[4:])) == expected
        with zipfile.ZipFile(source) as archive:
            assert set(archive.namelist()) == set(build["inventory"])
            for name, expected in build["inventory"].items():
                assert sha(archive.read(name)) == expected
    spec = importlib.util.spec_from_file_location(
        "preservation1428", ROOT / "scripts/research/verify_phase1415d.py",
    )
    assert spec and spec.loader
    parent = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(parent)
    protection = parent.protected(m["baseline"])
    family = parent.reports()
    scans = parent.scan(sorted(tracked | new))
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    return dict(
        verification="PASS", classification=report["classification"],
        baseline=m["baseline"], manifest=envelope["fingerprint"],
        report=m["report_fingerprint"], resource_contract=MANIFEST_HASH,
        tests=tests, protected=protection, family=family, scans=scans,
        new_files=len(m["new_files"]), real_kite_requests=0,
        Gate_A="NOT_AUTHORIZED", Gate_B="NOT_AUTHORIZED", operational="NO_GO",
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
