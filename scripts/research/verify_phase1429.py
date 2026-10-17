"""Read-only replay of sealed offline provenance, resource and build evidence."""

import importlib.util
import json
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1428.resources import load
from strategy_engine.phase1428.runtime import assess
from strategy_engine.phase1429.provenance import audit


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def verify() -> dict[str, object]:
    directory = ROOT / "research/phase-14.29/generation-1"
    e = json.loads((directory / "evidence-manifest.json").read_bytes())
    m = e["manifest"]
    assert sha(canonical(m)) == e["fingerprint"]
    assert git("branch", "--show-current").strip() == b"develop"
    for ref in ("HEAD", "origin/develop"):
        assert git("rev-parse", ref).decode().strip() == m["baseline"]
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    tracked = set(git("ls-files").decode().splitlines())
    new = set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
    assert len(tracked) == 1384 and tracked == set(m["baseline_hashes"])
    assert new == set(m["new_files"]) | set(m["preservation_exceptions"])
    for n, h in (m["baseline_hashes"] | m["artifacts"] | m["preservation_exceptions"]).items():
        assert sha((ROOT / n).read_bytes()) == h, n
    for n, h in m["external_evidence"].items():
        assert sha(Path(n).read_bytes()) == h, n
    for n in m["new_files"]:
        raw = (ROOT / n).read_bytes()
        assert b"\r" not in raw and raw.endswith(b"\n"), n
        assert all(line.rstrip(b" \t") == line for line in raw.splitlines()), n
        assert git("hash-object", n) == git("hash-object", "--no-filters", n), n
    report = json.loads((directory / "provenance-assessment.json").read_bytes())
    assert sha(canonical(report)) == m["report_fingerprint"]
    assert audit() == report["provenance"]
    assert load()["manifest"] == report["build"]["resource_contract"]
    assert assess() == report["build"]["standalone"]["probe"]["assessment"]
    assert report["classification"] == "PHASE_14_29_PARTIAL"
    assert report["recommendation"] == "OFFLINE_RC_CONDITIONAL"
    resource_name = "apps/strategy-engine/src/strategy_engine/phase1428/data/universe.csv"
    raw = (ROOT / resource_name).read_bytes()
    assert raw == git("show", "HEAD:" + resource_name)
    assert raw == (ROOT / "config/universes/phase14_unique_universe.csv").read_bytes()
    assert sha(raw) == report["resource_reconciliation"]["canonical_sha256"]
    assert raw.count(b"\r\n") == raw.count(b"\n") == 1116
    out = Path(m["external_directory"])
    builds = report["build"]["builds"]
    assert builds[0] == builds[1]
    for label in ("a", "b"):
        wheel = next((out / ("build-" + label) / "wheel").glob("*.whl"))
        source = out / ("build-" + label) / "offline-source.zip"
        assert sha(wheel.read_bytes()) == builds[0]["wheel"]
        assert sha(source.read_bytes()) == builds[0]["source"]
        with zipfile.ZipFile(wheel) as z:
            assert z.read("strategy_engine/phase1428/data/universe.csv") == raw
            assert {n for n in z.namelist() if ".dist-info/" not in n} == {
                n[4:] for n in report["build"]["inventory"] if n.startswith("src/")
            }
            for n, h in report["build"]["inventory"].items():
                if n.startswith("src/"):
                    assert sha(z.read(n[4:])) == h
        with zipfile.ZipFile(source) as z:
            assert set(z.namelist()) == set(report["build"]["inventory"])
            for n, h in report["build"]["inventory"].items():
                assert sha(z.read(n)) == h
    tests = {}
    for entry in m["junit"]:
        p = Path(entry["path"])
        assert sha(p.read_bytes()) == entry["sha256"]
        xml = ET.fromstring(p.read_bytes())
        assert not any(list(xml.iter(k)) for k in ("failure", "error", "skipped"))
        assert len(list(xml.iter("testcase"))) == entry["tests"]
        tests[entry["id"]] = entry["tests"]
    for entry in m["commands"]:
        p = Path(entry["path"])
        assert sha(p.read_bytes()) == entry["sha256"]
        receipt = json.loads(p.read_bytes())
        assert receipt["exit_code"] == entry["exit_code"] and not receipt["protected_changed"]
        for ext in ("stdout", "stderr"):
            assert sha(p.with_suffix("." + ext).read_bytes()) == receipt[ext + "_sha256"]
    for name in m["required_commands"]:
        assert [r["exit_code"] for r in m["commands"] if r["id"] == name] == [0]
    spec = importlib.util.spec_from_file_location(
        "preserved1429",
        ROOT / "scripts/research/verify_phase1415d.py",
    )
    assert spec and spec.loader
    parent = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(parent)
    protection = parent.protected(m["baseline"])
    family = parent.reports()
    scans = parent.scan(sorted(tracked | new))
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    return dict(
        verification="PASS",
        baseline=m["baseline"],
        manifest=e["fingerprint"],
        report=m["report_fingerprint"],
        provenance=report["provenance"]["fingerprint"],
        classification=report["classification"],
        recommendation=report["recommendation"],
        tests=tests,
        protected=protection,
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
