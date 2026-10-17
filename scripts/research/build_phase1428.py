"""Offline selected-library build/probe; explicit external evidence directory only."""

import base64
import csv
import hashlib
import importlib.metadata as md
import io
import json
import os
import shutil
import subprocess
import sys
import venv
import zipfile
from pathlib import Path

from packaging.requirements import Requirement
from packaging.utils import canonicalize_name

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, external, sha
from strategy_engine.phase1427.assessment import dependencies, distribution_record
from strategy_engine.phase1428.provenance import classify
from strategy_engine.phase1428.resources import load
from strategy_engine.phase1428.runtime import assess

OUT = external(Path(sys.argv[1]))
assert OUT.is_dir()
PACKAGE = ROOT / "apps/strategy-engine/src/strategy_engine"
resource = load()["manifest"]
inventory = {"src/strategy_engine/" + n: h for n, h in resource["software_inventory"].items()}
for name in ("contract.py", "data/manifest.json", "data/universe.csv", "data/configuration.json"):
    inventory["src/strategy_engine/phase1428/" + name] = sha(
        (PACKAGE / "phase1428" / name).read_bytes()
    )
inventory["pyproject.toml"] = sha((ROOT / "apps/strategy-engine/pyproject.toml").read_bytes())
assert len(inventory) <= 128
dependency = dependencies()
assert not dependency["lock_mismatches"] and not dependency["missing_locked"]
assert all(r["status"] == "VERIFIED_INSTALLED_RECORD" for r in dependency["records"])
assert all(r["satisfied"] for r in dependency["constraints"])
cache = Path(os.environ["LOCALAPPDATA"]) / "uv/cache"
backend = []
for name, version in (("hatchling", "1.27.0"), ("trove_classifiers", "2026.6.1.19")):
    paths = list((cache / "archive-v0").glob(f"*/{name}-{version}.dist-info"))
    assert len(paths) == 1
    path = paths[0]
    record = distribution_record(md.PathDistribution(path), path.parent)
    assert record["status"] == "VERIFIED_INSTALLED_RECORD"
    backend.append(record)
    sys.path.insert(0, str(path.parent))
archives = []
scan_roots = [cache, Path(os.environ["LOCALAPPDATA"]) / "pip/Cache/wheels"]
for directory in scan_roots:
    if directory.exists():
        for current, dirs, names in os.walk(directory, followlinks=False):
            dirs[:] = [d for d in dirs if not (Path(current) / d).is_symlink()]
            for name in names:
                if name.endswith((".whl", ".tar.gz", ".zip")):
                    path = Path(current) / name
                    if path.is_file() and not path.is_symlink():
                        archives.append(path)
                        assert len(archives) <= 4096
archive_hashes = {p: sha(p.read_bytes()) for p in archives}
provenance = []
for record in dependency["records"]:
    expected = dependency["locked_archive_hashes"][record["name"]]
    candidates = tuple(p for p, h in archive_hashes.items() if "sha256:" + h in expected)
    row = classify(record, record["version"], expected, candidates)
    row["installed_location"] = str(md.distribution(record["name"]).locate_file(""))
    provenance.append(row)
(OUT / "dependencies.json").write_bytes(canonical(dependency) + b"\n")
(OUT / "provenance.json").write_bytes(canonical(dict(
    records=provenance, scan_roots=[str(p) for p in scan_roots],
    archive_inventory={str(p): h for p, h in archive_hashes.items()},
    scan_limit="Known uv cache and pip wheel cache; not an exhaustive disk search",
    backend=backend, backend_archive_provenance="UNVERIFIED",
)) + b"\n")


def no_network(event: str, args: tuple[object, ...]) -> None:
    if event.startswith("socket."):
        raise PermissionError("OFFLINE_BUILD_NETWORK_DENIED")


sys.addaudithook(no_network)
from hatchling.build import build_wheel  # noqa: E402

os.environ["SOURCE_DATE_EPOCH"] = "1577836800"
built = []
for label in ("a", "b"):
    stage = OUT / ("build-" + label)
    project = stage / "project"
    project.mkdir(parents=True)
    for name, expected in sorted(inventory.items()):
        raw = (ROOT / "apps/strategy-engine" / name).read_bytes()
        assert sha(raw) == expected
        destination = project / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(raw)
    target = stage / "wheel"
    target.mkdir()
    os.chdir(project)
    filename = build_wheel(str(target))
    wheel = target / filename
    with zipfile.ZipFile(wheel) as archive:
        names = archive.namelist()
        assert not any(n.endswith((".exe", "entry_points.txt")) for n in names)
        record_name = next(n for n in names if n.endswith(".dist-info/RECORD"))
        for name, digest, size in csv.reader(io.StringIO(archive.read(record_name).decode())):
            if digest:
                algorithm, expected = digest.split("=", 1)
                raw = archive.read(name)
                assert base64.urlsafe_b64encode(hashlib.new(algorithm, raw).digest()).rstrip(
                    b"="
                ).decode() == expected
                assert len(raw) == int(size)
        for name, expected in inventory.items():
            if name.startswith("src/"):
                assert sha(archive.read(name[4:])) == expected
    source = stage / "offline-source.zip"
    with zipfile.ZipFile(source, "x", compression=zipfile.ZIP_STORED) as archive:
        for name in sorted(inventory):
            info = zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, (project / name).read_bytes())
    built.append(dict(wheel=sha(wheel.read_bytes()), source=sha(source.read_bytes())))
assert built[0] == built[1]

# Fresh environment, no pip bootstrap/resolver. Install wheel files after RECORD validation.
environment = OUT / "isolated"
venv.EnvBuilder(with_pip=False).create(environment)
site = environment / "Lib/site-packages"
wheel = next((OUT / "build-a/wheel").glob("*.whl"))
with zipfile.ZipFile(wheel) as archive:
    for name in archive.namelist():
        assert ".." not in Path(name).parts and not Path(name).is_absolute()
    archive.extractall(site)
copied = []
for name in (
    "pydantic", "pydantic_core", "annotated_types", "typing_extensions",
    "typing_inspection", "tzdata", "pydantic_settings", "python_dotenv",
):
    dist = md.distribution(name)
    for item in dist.files or ():
        if item.hash is None:
            continue
        if name == "python_dotenv" and str(item).replace("\\", "/") == "../../Scripts/dotenv.exe":
            continue  # Explicitly exclude the unused dotenv console launcher.
        source = Path(str(dist.locate_file(item))).resolve()
        relative = Path(str(item))
        assert ".." not in relative.parts and not relative.is_absolute()
        destination = site / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
    copied.append(name)
installed = {
    canonicalize_name(d.metadata["Name"]): d
    for d in md.distributions(path=[str(site)])
}
for distribution in installed.values():
    for raw_requirement in distribution.requires or ():
        requirement = Requirement(raw_requirement)
        if requirement.marker is None or requirement.marker.evaluate({"extra": ""}):
            resolved = installed[canonicalize_name(requirement.name)]
            assert resolved.version in requirement.specifier
probe = '''import json,sys,os
from pathlib import Path
checkout=Path(sys.argv[1]).resolve()
def guard(event,args):
 if event.startswith('socket.') or event in {'subprocess.Popen','os.system'}:
  raise PermissionError('OFFLINE_RUNTIME_DENIED')
 if event=='open' and args and isinstance(args[0],(str,bytes,os.PathLike)):
  path=Path(os.fsdecode(args[0])).resolve()
  if path==checkout or checkout in path.parents or path.name.casefold()=='.env':
   raise PermissionError('CHECKOUT_OR_CREDENTIAL_READ_DENIED')
sys.addaudithook(guard)
assert not any(str(checkout).casefold() in p.casefold() for p in sys.path)
from strategy_engine.phase1428.runtime import assess
from strategy_engine.phase1428.resources import load
from strategy_engine.phase1415.hardening import UNIVERSE
try: UNIVERSE.read_bytes()
except (FileNotFoundError,PermissionError): historical='REPOSITORY_TOOL_UNAVAILABLE'
else: raise AssertionError('UNEXPECTED_SOURCE_RESOURCE')
result=assess();assert result==assess()
assert len(load()['symbols'])==1115
blocked=0
for target in (checkout/'config/universes/phase14_unique_universe.csv',Path.cwd()/'.env'):
 try: target.read_bytes()
 except PermissionError: blocked+=1
assert blocked==2
print(json.dumps({'assessment':result,'legacy_research_tool':historical,'checkout_reads':'DENIED','credential_reads':'DENIED','network_guard':'ENABLED','OS_egress':'PRODUCTION_EGRESS_NOT_VERIFIED'},sort_keys=True))
'''
(OUT / "probe.py").write_text(probe, encoding="utf-8", newline="\n")
for path in (site / "strategy_engine/phase1428/data").iterdir():
    path.chmod(0o444)
child = subprocess.run(
    [str(environment / "Scripts/python.exe"), "-I", "-B", str(OUT / "probe.py"), str(ROOT)],
    cwd=OUT, env=os.environ | {"PYTHONDONTWRITEBYTECODE": "1", "PYTHONPATH": ""},
    capture_output=True,
)
(OUT / "probe.stdout").write_bytes(child.stdout)
(OUT / "probe.stderr").write_bytes(child.stderr)
assert child.returncode == 0, child.stderr.decode()
result = json.loads(child.stdout)
assert result["assessment"] == assess()
report = dict(
    version="Phase1428Build.v1", inventory=inventory, software=resource["software_fingerprint"],
    builds=built, reproducibility="BYTE_IDENTICAL", probe=result,
    runtime_dependency_installation="Verified installed-file copies; no original archive claim",
    excluded_dependency_launcher="python-dotenv ../../Scripts/dotenv.exe",
    copied_runtime_distributions=copied, dependency_provenance=provenance,
    classification="PHASE_14_28_PARTIAL", recommendation="OFFLINE_RC_CONDITIONAL",
    resource_binding="VALIDATED_NEW_STANDALONE_ENTRY_POINT",
    historical_tools="Repository evidence replay and family-binding APIs remain repository tools",
    backend_archive_provenance="UNVERIFIED", OS_egress="PRODUCTION_EGRESS_NOT_VERIFIED",
)
(OUT / "build-report.json").write_bytes(canonical(report) + b"\n")
print(json.dumps(dict(
    status="PASS", builds=built, symbols=1115, classification=report["classification"],
)))
