"""Record the fresh offline installation boundary without substituting packages."""

import ast
import importlib.metadata as md
import importlib.util
import json
import os
import subprocess
import sys
import tomllib
import venv
import zipfile
from pathlib import Path

from packaging.utils import canonicalize_name

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, external, sha
from strategy_engine.phase1428.resources import load
from strategy_engine.phase1430.assessment import graph, installation_gate, metadata, wheel_metadata

OUT = external(Path(sys.argv[1]))
project = ROOT / "apps/strategy-engine"
spec = tomllib.loads((project / "pyproject.toml").read_text())
lock = tomllib.loads((project / "uv.lock").read_text())
contract = metadata(spec, lock)
provenance = json.loads((OUT / "provenance-report.json").read_bytes())
installed = {str(canonicalize_name(d.metadata["Name"])): d for d in md.distributions()}
runtime = graph(spec["project"]["dependencies"], installed)
locked = {p["name"]: p for p in lock["package"]}
assert all(locked[r["name"]]["version"] == r["version"] for r in runtime)
gate = installation_gate(runtime, provenance["dependencies"])
# This phase has no authority to reconstruct missing archives from installed files.
assert gate["status"] == "BLOCKED", "NEW_ARTIFACTS_REQUIRE_REVIEWED_INSTALLATION_PATH"


def guard(event: str, args: tuple[object, ...]) -> None:
    if event.startswith("socket."):
        raise PermissionError("OFFLINE_NETWORK_DENIED")


sys.addaudithook(guard)
fresh = OUT / "fresh-windows"
assert not fresh.exists()
venv.EnvBuilder(with_pip=False, system_site_packages=False).create(fresh)
probe = """import importlib.metadata as md,json,sys,zoneinfo
from pathlib import Path
assert sys.prefix != sys.base_prefix
assert not list(md.distributions())
assert 'include-system-site-packages = false' in (Path(sys.prefix)/'pyvenv.cfg').read_text()
zoneinfo.reset_tzpath([])
try: zoneinfo.ZoneInfo.no_cache('Asia/Kolkata')
except zoneinfo.ZoneInfoNotFoundError: missing='REJECTED'
else: raise AssertionError('UNDECLARED_TIMEZONE_PROVIDER')
print(json.dumps({'installed_distributions':[],'system_site_packages':False,
 'missing_tzdata':missing,'platform':sys.platform,'network_resolution':False},sort_keys=True))
"""
(OUT / "fresh-probe.py").write_text(probe, encoding="utf-8", newline="\n")
child = subprocess.run(
    [str(fresh / "Scripts/python.exe"), "-I", "-B", str(OUT / "fresh-probe.py")],
    cwd=OUT,
    env=os.environ | {"PYTHONDONTWRITEBYTECODE": "1", "PYTHONPATH": ""},
    capture_output=True,
    check=False,
)
(OUT / "fresh-probe.stdout").write_bytes(child.stdout)
(OUT / "fresh-probe.stderr").write_bytes(child.stderr)
assert child.returncode == 0, child.stderr.decode()
fresh_result = json.loads(child.stdout)
assert fresh_result["platform"] == "win32"
build = json.loads((OUT / "build-report.json").read_bytes())
resources = load()["manifest"]
findings = []
module_count = 0
for label in ("a", "b"):
    wheel = next((OUT / ("build-" + label) / "wheel").glob("*.whl"))
    assert wheel_metadata(wheel) == build["wheel_metadata"]
    with zipfile.ZipFile(wheel) as z:
        for name in z.namelist():
            assert not name.startswith("/") and ".." not in Path(name).parts
            assert ".env" not in name and "entry_points.txt" not in name
            if not name.endswith(".py"):
                continue
            module_count += 1
            for node in ast.walk(ast.parse(z.read(name))):
                imports = (
                    [a.name for a in node.names]
                    if isinstance(node, ast.Import)
                    else [node.module or ""]
                    if isinstance(node, ast.ImportFrom)
                    else []
                )
                if any(
                    n.split(".")[0]
                    in {
                        "kiteconnect",
                        "requests",
                        "httpx",
                        "urllib",
                        "socket",
                        "redis",
                        "psycopg",
                        "sqlalchemy",
                        "subprocess",
                        "ctypes",
                        "pickle",
                        "dotenv",
                    }
                    for n in imports
                ):
                    findings.append(name)
assert not findings
scanner_spec = importlib.util.spec_from_file_location(
    "phase1430_scanner", ROOT / "scripts/research/verify_phase1415d.py"
)
assert scanner_spec and scanner_spec.loader
scanner = importlib.util.module_from_spec(scanner_spec)
scanner_spec.loader.exec_module(scanner)
names = subprocess.check_output(["git", "ls-files"], cwd=ROOT).decode().splitlines()
names += (
    subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], cwd=ROOT)
    .decode()
    .splitlines()
)
scans = scanner.scan(sorted(names))
body = dict(
    version="Phase1430Assessment.v1",
    baseline=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip(),
    classification="PHASE_14_30_PARTIAL",
    windows_runtime_dependency_closure="PARTIAL",
    dependency_declaration=contract,
    lock_consistency="STRUCTURAL_EXACT_EDGE_AND_VERSION_PASS_NO_RESOLVER_REFRESH",
    runtime_graph=runtime,
    dependency_groups=[
        {
            "name": r["name"],
            "version": r["version"],
            "scope": r["scope"],
            "windows_only_runtime": r["name"] == "tzdata",
            "installed_integrity": r["installed_integrity"],
            "archive_provenance": r["archive_provenance"],
        }
        for r in provenance["dependencies"]
    ],
    provenance_inventory_fingerprint=provenance["fingerprint"],
    archive_provenance="UNVERIFIED",
    historical_provenance=dict(
        RECORD_verified=39, missing_originals=38, applicable_matches=0, registry_attestation=False
    ),
    fresh_installation=gate | {"environment_probe": fresh_result},
    standalone_readiness="PARTIAL",
    build=build,
    resources=resources,
    security=dict(
        scans=scans,
        modules_scanned=module_count,
        prohibited_imports=findings,
        process_guard="EXISTING_ISOLATED_PROBE_PASS",
        credential_access=False,
        OS_egress="PRODUCTION_EGRESS_NOT_VERIFIED",
    ),
    limitations=[
        "Fresh environment is empty; eight required runtime archives are unavailable.",
        "No fresh wheel installation or fresh six-strategy success is claimed.",
        "Six-strategy success uses the existing Phase14.28 isolated dependencies.",
        "Installed RECORD integrity does not establish original archive provenance.",
        "Source ZIP is the selected offline source closure, not a full-project sdist.",
        "Lock verification is offline structural verification, not a resolver refresh.",
        "Non-Windows host timezone availability is outside this Windows assessment.",
        "OS egress, independent custody, power-loss and multi-host guarantees unestablished.",
    ],
    recommendation="OFFLINE_RC_CONDITIONAL",
    owner_approval=None,
    genuine_qualification="BLOCKED",
    genuine_qualified=0,
    real_kite_requests=0,
    Gate_A="NOT_AUTHORIZED",
    Gate_B="NOT_AUTHORIZED",
    Stage_B="ZERO_CALL_ABORT",
    Phase_12="BLOCK",
    H1_H2="FROZEN",
    July="SEALED",
    operational="NO_GO",
)
assert len(gate["missing_approved_archives"]) == 8
(OUT / "assessment.json").write_bytes(canonical(body) + b"\n")
print(
    json.dumps(
        dict(
            classification=body["classification"],
            fresh_installation=gate,
            runtime_dependencies=len(runtime),
            fingerprint=sha(canonical(body)),
        ),
        sort_keys=True,
    )
)
