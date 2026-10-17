"""Rebuild the unchanged offline RC without installing or resolving dependencies."""

import json
import os
import subprocess
import sys
import zipfile
from pathlib import Path

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, external, sha
from strategy_engine.phase1428.resources import load
from strategy_engine.phase1429.provenance import audit

OUT = external(Path(sys.argv[1]))
assert OUT.is_dir()
report = audit()
(OUT / "provenance-report.json").write_bytes(canonical(report) + b"\n")
resources = load()["manifest"]
inventory = {"src/strategy_engine/" + n: h for n, h in resources["software_inventory"].items()}
for name in ("contract.py", "data/manifest.json", "data/universe.csv", "data/configuration.json"):
    path = "src/strategy_engine/phase1428/" + name
    inventory[path] = sha((ROOT / "apps/strategy-engine" / path).read_bytes())
inventory["pyproject.toml"] = sha((ROOT / "apps/strategy-engine/pyproject.toml").read_bytes())
assert len(inventory) <= 128
cache = Path(os.environ["LOCALAPPDATA"]) / "uv/cache/archive-v0"
for name, version in (("hatchling", "1.27.0"), ("trove_classifiers", "2026.6.1.19")):
    paths = list(cache.glob(f"*/{name}-{version}.dist-info"))
    assert len(paths) == 1
    sys.path.insert(0, str(paths[0].parent))
assert all(
    r["installed_integrity"] == "INSTALLED_RECORD_VERIFIED"
    for r in report["dependencies"]
    if "build" in r["scope"]["groups"]
)


def no_network(event: str, args: tuple[object, ...]) -> None:
    if event.startswith("socket."):
        raise PermissionError("OFFLINE_NETWORK_DENIED")


sys.addaudithook(no_network)
from hatchling.build import build_wheel  # noqa: E402

os.environ["SOURCE_DATE_EPOCH"] = "1577836800"
builds = []
for label in ("a", "b"):
    project = OUT / ("build-" + label) / "project"
    project.mkdir(parents=True)
    for name, expected in sorted(inventory.items()):
        raw = (ROOT / "apps/strategy-engine" / name).read_bytes()
        assert sha(raw) == expected
        p = project / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(raw)
    target = project.parent / "wheel"
    target.mkdir()
    os.chdir(project)
    wheel = target / build_wheel(str(target))
    with zipfile.ZipFile(wheel) as z:
        assert {n for n in z.namelist() if ".dist-info/" not in n} == {
            n[4:] for n in inventory if n.startswith("src/")
        }
        for name, expected in inventory.items():
            if name.startswith("src/"):
                assert sha(z.read(name[4:])) == expected
    source = project.parent / "offline-source.zip"
    with zipfile.ZipFile(source, "x", compression=zipfile.ZIP_STORED) as z:
        for name in sorted(inventory):
            info = zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            z.writestr(info, (project / name).read_bytes())
    builds.append(dict(wheel=sha(wheel.read_bytes()), source=sha(source.read_bytes())))
assert builds[0] == builds[1]
previous = json.loads(
    (ROOT / "research/phase-14.28/generation-1/evidence-manifest.json").read_bytes()
)
old = previous["manifest"]
prior_build = Path(old["build_directory"])
prior_report = json.loads((prior_build / "build-report.json").read_bytes())
assert builds == prior_report["builds"]
for name in ("probe.py", "isolated/Scripts/python.exe"):
    p = prior_build / name
    assert sha(p.read_bytes()) == old["external_evidence"][str(p)]
probe = """import contextlib,io,json,runpy,socket,sys
from pathlib import Path
wheel,original,checkout=sys.argv[1:]
sys.path.insert(0,wheel)
sys.argv=[original,checkout]
capture=io.StringIO()
with contextlib.redirect_stdout(capture):runpy.run_path(original,run_name='__main__')
result=json.loads(capture.getvalue())
import strategy_engine.phase1428.runtime as runtime
assert runtime.__file__.startswith(wheel)
try: socket.socket()
except PermissionError: network='DENIED'
else: raise AssertionError('NETWORK_GUARD_FAILED')
for module in ('kiteconnect','requests','httpx','dotenv','strategy_engine.config'):
 assert module not in sys.modules,module
print(json.dumps({'probe':result,'new_wheel_loaded':True,'network_negative_probe':network,
 'dependency_installation':False,'OS_egress':'PRODUCTION_EGRESS_NOT_VERIFIED'},sort_keys=True))
"""
(OUT / "probe.py").write_text(probe, encoding="utf-8", newline="\n")
child = subprocess.run(
    [
        str(prior_build / "isolated/Scripts/python.exe"),
        "-I",
        "-B",
        str(OUT / "probe.py"),
        str(next((OUT / "build-a/wheel").glob("*.whl"))),
        str(prior_build / "probe.py"),
        str(ROOT),
    ],
    cwd=OUT,
    env=os.environ | {"PYTHONDONTWRITEBYTECODE": "1", "PYTHONPATH": ""},
    capture_output=True,
)
(OUT / "probe.stdout").write_bytes(child.stdout)
(OUT / "probe.stderr").write_bytes(child.stderr)
assert child.returncode == 0, child.stderr.decode()
result = json.loads(child.stdout)
assert result["probe"]["assessment"] == prior_report["probe"]["assessment"]
body = dict(
    version="Phase1429Build.v1",
    scope="SYNTHETIC_OFFLINE",
    builds=builds,
    same_as_phase1428=True,
    inventory=inventory,
    standalone=result,
    original_backend_provenance="ARCHIVE_MISSING",
    network_resolution=False,
    dependency_installation=False,
    resource_contract=resources,
)
(OUT / "build-report.json").write_bytes(canonical(body) + b"\n")
print(
    json.dumps(
        dict(status="PASS", builds=builds, new_wheel_loaded=True, dependency_installation=False),
        sort_keys=True,
    )
)
