"""Windows marker, missing-input and timezone regression tests; all offline."""

import copy
import importlib.metadata as md
import json
import os
import subprocess
import sys
import tomllib
from pathlib import Path
from typing import Any

import pytest
from packaging.requirements import Requirement

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1430.assessment import (
    REQUIREMENT,
    graph,
    installation_gate,
    installed_set,
    metadata,
)

PROJECT = ROOT / "apps/strategy-engine"


@pytest.mark.parametrize(
    "platform,expected", [("win32", True), ("linux", False), ("darwin", False)]
)
def test_windows_marker(platform: str, expected: bool) -> None:
    marker = Requirement(REQUIREMENT).marker
    assert marker is not None and marker.evaluate({"sys_platform": platform}) is expected


@pytest.mark.parametrize("fault", ["valid", "missing", "marker", "version", "edge", "metadata"])
def test_lock_contract(fault: str) -> None:
    spec = tomllib.loads((PROJECT / "pyproject.toml").read_text())
    lock = tomllib.loads((PROJECT / "uv.lock").read_text())
    package = next(p for p in lock["package"] if p["name"] == "strategy-engine")
    if fault == "missing":
        spec["project"]["dependencies"] = ["pydantic"]
    elif fault == "marker":
        spec["project"]["dependencies"][-1] = "tzdata==2026.4"
    elif fault == "version":
        next(p for p in lock["package"] if p["name"] == "tzdata")["version"] = "2025.1"
    elif fault == "edge":
        package["dependencies"] = []
    elif fault == "metadata":
        package["metadata"]["requires-dist"] = []
    if fault == "valid":
        assert metadata(spec, lock)["lock_consistency"] == "PASS"
    else:
        with pytest.raises(ValueError):
            metadata(spec, lock)


def test_no_unrelated_lock_changes() -> None:
    old = tomllib.loads(
        subprocess.check_output(
            [
                "git",
                "show",
                "91db90a3e8329c2273a3c414d32a1b01dc04e362:apps/strategy-engine/uv.lock",
            ],
            cwd=ROOT,
        ).decode()
    )
    current = tomllib.loads((PROJECT / "uv.lock").read_text())
    expected = copy.deepcopy(old)
    package = next(p for p in expected["package"] if p["name"] == "strategy-engine")
    package["dependencies"].append({"name": "tzdata", "marker": "sys_platform == 'win32'"})
    package["metadata"]["requires-dist"].append(
        {"name": "tzdata", "marker": "sys_platform == 'win32'", "specifier": "==2026.4"}
    )
    assert current == expected


@pytest.mark.parametrize("fault", ["valid", "missing", "record_only", "wrong_version", "duplicate"])
def test_installation_inputs_fail_closed(fault: str) -> None:
    deps = [{"name": "tzdata", "version": "2026.4"}]
    provenance: list[dict[str, Any]] = [
        dict(name="tzdata", version="2026.4", archive_provenance="ARCHIVE_HASH_VERIFIED")
    ]
    if fault == "missing":
        provenance = []
    elif fault == "record_only":
        provenance[0]["archive_provenance"] = "INSTALLED_RECORD_VERIFIED"
    elif fault == "wrong_version":
        provenance[0]["version"] = "2025.1"
    elif fault == "duplicate":
        provenance[0]["duplicate_candidates"] = True
    result = installation_gate(deps, provenance)
    assert result["status"] == ("INPUTS_AVAILABLE" if fault == "valid" else "BLOCKED")
    assert result["installation_attempted"] is False


@pytest.mark.parametrize("observed", [[], ["a", "other"], ["a", "a"]])
def test_missing_undeclared_duplicate_packages(observed: list[str]) -> None:
    with pytest.raises(ValueError):
        installed_set(["a"], observed)


def test_dependency_graph_platform_and_missing(tmp_path: Path) -> None:
    info = tmp_path / "tzdata-2026.4.dist-info"
    info.mkdir()
    (info / "METADATA").write_text("Name: tzdata\nVersion: 2026.4\n")
    distributions: dict[str, md.Distribution] = {"tzdata": md.PathDistribution(info)}
    assert graph([REQUIREMENT], distributions, "linux") == []
    assert graph([REQUIREMENT], distributions) == [
        {"name": "tzdata", "version": "2026.4", "direct": True}
    ]
    with pytest.raises(ValueError, match="MISSING_DEPENDENCY"):
        graph([REQUIREMENT], {})


@pytest.mark.parametrize("host_tz", ["UTC", "America/New_York"])
def test_timezone_explicit_absence_and_session_boundaries(host_tz: str) -> None:
    code = """
import importlib.abc,sys,json,zoneinfo
from datetime import datetime,UTC,timedelta
class Deny(importlib.abc.MetaPathFinder):
 def find_spec(self,fullname,path=None,target=None):
  if fullname=='tzdata' or fullname.startswith('tzdata.'):
   raise ModuleNotFoundError('synthetic absence')
zoneinfo.reset_tzpath([])
sys.meta_path.insert(0,Deny())
try: zoneinfo.ZoneInfo.no_cache('Asia/Kolkata')
except zoneinfo.ZoneInfoNotFoundError: absent=True
else: absent=False
sys.meta_path.pop(0)
zone=zoneinfo.ZoneInfo.no_cache('Asia/Kolkata')
for year in (2020,2025,2026):
 for h,m,expected in ((3,45,'09:15'),(10,0,'15:30')):
  value=datetime(year,1,2,h,m,tzinfo=UTC).astimezone(zone)
  assert value.strftime('%H:%M')==expected
  assert value.utcoffset()==timedelta(hours=5,minutes=30)
  assert value.astimezone(UTC)==datetime(year,1,2,h,m,tzinfo=UTC)
assert absent
print(json.dumps({'missing_tzdata':'REJECTED','conversions':6}))
"""
    child = subprocess.run(
        [sys.executable, "-I", "-B", "-c", code],
        capture_output=True,
        env=os.environ | {"TZ": host_tz, "PYTHONDONTWRITEBYTECODE": "1"},
    )
    assert child.returncode == 0, child.stderr.decode()
    assert json.loads(child.stdout) == {"missing_tzdata": "REJECTED", "conversions": 6}


def test_historical_provenance_not_promoted() -> None:
    report = json.loads(
        (ROOT / "research/phase-14.29/generation-1/provenance-assessment.json").read_bytes()
    )
    assert report["archive_counts"]["required_third_party_missing"] == 38
    assert report["archive_counts"]["required_archive_matches"] == 0
    assert all(
        r["installed_integrity"] == "INSTALLED_RECORD_VERIFIED"
        for r in report["provenance"]["dependencies"]
    )
