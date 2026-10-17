"""Read-only deterministic replay of partial Windows runtime evidence."""

import importlib.util
import json
import subprocess
import tomllib
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1428.resources import load
from strategy_engine.phase1428.runtime import assess
from strategy_engine.phase1429.provenance import audit
from strategy_engine.phase1430.assessment import installation_gate, metadata, wheel_metadata


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def verify() -> dict[str, object]:
    directory = ROOT / "research/phase-14.30/generation-1"
    envelope = json.loads((directory / "evidence-manifest.json").read_bytes())
    m = envelope["manifest"]
    assert sha(canonical(m)) == envelope["fingerprint"]
    assert git("branch", "--show-current").strip() == b"develop"
    for ref in ("HEAD", "origin/develop"):
        assert git("rev-parse", ref).decode().strip() == m["baseline"]
    assert not git("diff", "--cached", "--name-only")
    assert set(git("ls-files").decode().splitlines()) == set(m["baseline_hashes"])
    assert len(m["baseline_hashes"]) == 1397
    assert set(git("diff", "--name-only").decode().splitlines()) == set(m["changed_files"])
    assert set(git("ls-files", "--others", "--exclude-standard").decode().splitlines()) == (
        set(m["new_files"]) | set(m["preservation_exceptions"])
    )
    for name, expected in m["baseline_hashes"].items():
        if name not in m["changed_files"]:
            assert sha((ROOT / name).read_bytes()) == expected, name
    for group in (m["artifacts"], m["preservation_exceptions"], m["external_evidence"]):
        for name, expected in group.items():
            assert sha((ROOT / name).read_bytes()) == expected, name
    for name in m["new_files"] + m["changed_files"]:
        raw = (ROOT / name).read_bytes()
        assert b"\r" not in raw and raw.endswith(b"\n"), name
        assert all(line.rstrip(b" \t") == line for line in raw.splitlines()), name
        assert git("hash-object", name) == git("hash-object", "--no-filters", name), name
    assert sha((ROOT / "docs/operations/phase-14.30-plan.md").read_bytes()) == m["plan_hash"]
    report = json.loads((directory / "runtime-assessment.json").read_bytes())
    assert sha(canonical(report)) == m["report_fingerprint"]
    assert report["classification"] == "PHASE_14_30_PARTIAL"
    assert report["recommendation"] == "OFFLINE_RC_CONDITIONAL"
    assert report["fresh_installation"]["status"] == "BLOCKED"
    assert not report["fresh_installation"]["installation_attempted"]
    assert report["fresh_installation"]["environment_probe"]["installed_distributions"] == []
    project = ROOT / "apps/strategy-engine"
    assert (
        metadata(
            tomllib.loads((project / "pyproject.toml").read_text()),
            tomllib.loads((project / "uv.lock").read_text()),
        )
        == report["dependency_declaration"]
    )
    provenance = audit()
    assert provenance["fingerprint"] == report["provenance_inventory_fingerprint"]
    gate = installation_gate(report["runtime_graph"], provenance["dependencies"])
    assert all(report["fresh_installation"][k] == v for k, v in gate.items())
    assert load()["manifest"] == report["resources"]
    assert assess() == report["build"]["standalone"]["probe"]["assessment"]
    universe = "apps/strategy-engine/src/strategy_engine/phase1428/data/universe.csv"
    raw = (ROOT / universe).read_bytes()
    assert raw == git("show", "HEAD:" + universe)
    assert raw == (ROOT / "config/universes/phase14_unique_universe.csv").read_bytes()
    assert sha(raw) == "4345207d985672c34e79347eafb13475ed8c7fe5f440914b25ac0999465c3729"
    assert raw.count(b"\r\n") == raw.count(b"\n") == 1116
    out = Path(m["external_directory"])
    build = report["build"]
    assert build["builds"][0] == build["builds"][1]
    for label in ("a", "b"):
        wheel = next((out / ("build-" + label) / "wheel").glob("*.whl"))
        source = out / ("build-" + label) / "offline-source.zip"
        assert sha(wheel.read_bytes()) == build["builds"][0]["wheel"]
        assert sha(source.read_bytes()) == build["builds"][0]["source"]
        assert wheel_metadata(wheel) == build["wheel_metadata"]
        with zipfile.ZipFile(wheel) as z:
            assert {n for n in z.namelist() if ".dist-info/" not in n} == {
                n[4:] for n in build["inventory"] if n.startswith("src/")
            }
            for n, digest in build["inventory"].items():
                if n.startswith("src/"):
                    assert sha(z.read(n[4:])) == digest
        with zipfile.ZipFile(source) as z:
            assert set(z.namelist()) == set(build["inventory"])
            for n, digest in build["inventory"].items():
                assert sha(z.read(n)) == digest
    totals = {}
    for entry in m["junit"]:
        path = Path(entry["path"])
        assert sha(path.read_bytes()) == entry["sha256"]
        xml = ET.fromstring(path.read_bytes())
        assert not any(list(xml.iter(k)) for k in ("failure", "error", "skipped"))
        assert len(list(xml.iter("testcase"))) == entry["tests"]
        totals[entry["id"]] = entry["tests"]
    for entry in m["commands"]:
        path = Path(entry["path"])
        assert sha(path.read_bytes()) == entry["sha256"]
        receipt = json.loads(path.read_bytes())
        assert receipt["exit_code"] == entry["exit_code"] and not receipt["protected_changed"]
        for stream in ("stdout", "stderr"):
            assert sha(path.with_suffix("." + stream).read_bytes()) == receipt[stream + "_sha256"]
    for name in m["required_commands"]:
        assert [r["exit_code"] for r in m["commands"] if r["id"] == name] == [0]
    scanner_spec = importlib.util.spec_from_file_location(
        "phase1430_scan", ROOT / "scripts/research/verify_phase1415d.py"
    )
    assert scanner_spec and scanner_spec.loader
    scanner = importlib.util.module_from_spec(scanner_spec)
    scanner_spec.loader.exec_module(scanner)
    family = scanner.reports()
    scans = scanner.scan(sorted(set(m["baseline_hashes"]) | set(m["new_files"])))
    git("diff", "--check")
    return dict(
        verification="PASS",
        baseline=m["baseline"],
        manifest=envelope["fingerprint"],
        report=m["report_fingerprint"],
        classification=report["classification"],
        recommendation=report["recommendation"],
        fresh_installation="BLOCKED",
        tests=totals,
        protected_unchanged=1397 - len(m["changed_files"]),
        authorized_changes=m["changed_files"],
        family=family,
        scans=scans,
        real_kite_requests=0,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
    )


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
