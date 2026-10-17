"""Deterministic source, installed dependency and release-evidence assessment."""

import ast
import base64
import hashlib
import importlib.metadata as metadata
import platform
import sys
import tomllib
import zipfile
from pathlib import Path
from typing import Any

from packaging.requirements import Requirement
from packaging.utils import canonicalize_name

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, external, sha
from strategy_engine.phase1425.reliability import require

ENTRY_MODULES = (
    "phase1415.strategies",
    "phase1418.portfolio",
    "phase1419.scheduler",
    "phase1420.multi",
    "phase1421.analytics",
    "phase1423.quality",
    "phase1424.analytics",
    "phase1425.reliability",
    "phase1426.alerts",
    "phase1426.authority",
)
FORBIDDEN_IMPORTS = frozenset(
    {
        "kiteconnect",
        "requests",
        "httpx",
        "aiohttp",
        "websocket",
        "websockets",
        "sqlalchemy",
        "psycopg",
        "redis",
        "smtplib",
        "dotenv",
        "strategy_engine.config",
    }
)


def source_inventory() -> dict[str, Any]:
    base = ROOT / "apps/strategy-engine/src"
    pending = ["strategy_engine." + m for m in ENTRY_MODULES]
    inventory: dict[str, str] = {}
    graph: dict[str, list[str]] = {}
    external_imports: set[str] = set()
    findings: list[dict[str, Any]] = []
    while pending:
        module = pending.pop()
        if module in graph:
            continue
        path = base / (module.replace(".", "/") + ".py")
        if not path.is_file():
            path = base / module.replace(".", "/") / "__init__.py"
        require(path.is_file(), "LOCAL_MODULE_MISSING:" + module)
        raw = path.read_bytes()
        inventory[path.relative_to(ROOT).as_posix()] = sha(raw)
        imports: set[str] = set()
        tree = ast.parse(raw, filename=module)
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imports.update(a.name for a in node.names)
            elif isinstance(node, ast.ImportFrom):
                require(node.level == 0, "RELATIVE_IMPORT_REQUIRES_REVIEW")
                if node.module:
                    imports.add(node.module)
                    for alias in node.names:
                        child = node.module + "." + alias.name
                        if (base / (child.replace(".", "/") + ".py")).is_file():
                            imports.add(child)
            elif isinstance(node, ast.Call):
                call = ast.unparse(node.func)
                if call in {"eval", "exec", "pickle.load", "pickle.loads", "yaml.load"}:
                    findings.append({"module": module, "line": node.lineno, "call": call})
        graph[module] = sorted(imports)
        for imported in imports:
            if imported.startswith("strategy_engine"):
                pending.append(imported)
            else:
                external_imports.add(imported.split(".")[0])
            if any(imported == bad or imported.startswith(bad + ".") for bad in FORBIDDEN_IMPORTS):
                findings.append({"module": module, "prohibited_import": imported})
        parts = module.split(".")
        pending.extend(".".join(parts[:i]) for i in range(1, len(parts)))
    endpoints = []
    credential_references = []
    for name in inventory:
        for number, line in enumerate((ROOT / name).read_text().splitlines(), 1):
            lower = line.lower()
            if any(token in lower for token in ("api.kite.trade", "wss://", "api.zerodha")):
                endpoints.append({"path": name, "line": number})
            if any(
                token in lower
                for token in ("load_dotenv", "access_token", "api_secret", "read_env")
            ):
                credential_references.append({"path": name, "line": number})
    return dict(
        version="Phase1427Source.v1",
        inventory=dict(sorted(inventory.items())),
        fingerprint=sha(canonical(dict(sorted(inventory.items())))),
        graph=dict(sorted(graph.items())),
        external_imports=sorted(external_imports),
        findings=findings,
        broker_endpoints=endpoints,
        credential_access_references=credential_references,
        production_egress="PRODUCTION_EGRESS_NOT_VERIFIED",
    )


def distribution_record(distribution: metadata.Distribution, boundary: Path) -> dict[str, Any]:
    files: dict[str, str] = {}
    failures = []
    unhashed = []
    for entry in distribution.files or ():
        name = str(entry).replace("\\", "/")
        if entry.hash is None:
            unhashed.append(name)
            continue
        path = Path(str(distribution.locate_file(entry))).resolve()
        if boundary.resolve() not in path.parents or not path.is_file():
            failures.append({"file": name, "reason": "MISSING_OR_OUTSIDE_BOUNDARY"})
            continue
        raw = path.read_bytes()
        actual = base64.urlsafe_b64encode(hashlib.new(entry.hash.mode, raw).digest()).rstrip(b"=")
        if actual.decode() != entry.hash.value:
            failures.append({"file": name, "reason": "RECORD_HASH_MISMATCH"})
        files[name] = sha(raw)
    return dict(
        name=canonicalize_name(distribution.metadata["Name"]),
        version=distribution.version,
        requirements=sorted(distribution.requires or []),
        verified_files=files,
        files_fingerprint=sha(canonical(files)),
        failures=failures,
        unhashed=sorted(unhashed),
        status="VERIFIED_INSTALLED_RECORD" if files and not failures else "UNVERIFIED",
    )


def dependencies() -> dict[str, Any]:
    project = ROOT / "apps/strategy-engine"
    spec = tomllib.loads((project / "pyproject.toml").read_text())
    lock = tomllib.loads((project / "uv.lock").read_text())
    locked = {p["name"]: p for p in lock["package"]}
    installed = {canonicalize_name(d.metadata["Name"]): d for d in metadata.distributions()}
    records = [distribution_record(installed[n], Path(sys.prefix)) for n in sorted(installed)]
    constraints = []
    for record in records:
        for raw in record["requirements"]:
            requirement = Requirement(raw)
            if requirement.marker is not None and not requirement.marker.evaluate({"extra": ""}):
                continue
            name = canonicalize_name(requirement.name)
            valid = name in installed and installed[name].version in requirement.specifier
            constraints.append({"owner": record["name"], "requirement": raw, "satisfied": valid})
    mismatches = [
        r["name"]
        for r in records
        if r["name"] not in locked or locked[r["name"]]["version"] != r["version"]
    ]
    return dict(
        version="Phase1427Dependencies.v1",
        python=platform.python_version(),
        implementation=platform.python_implementation(),
        machine=platform.machine(),
        python_executable_sha256=sha(Path(sys.executable).read_bytes()),
        direct=spec["project"]["dependencies"],
        development=spec["dependency-groups"]["dev"],
        build=spec["build-system"],
        records=records,
        constraints=constraints,
        lock_mismatches=mismatches,
        missing_locked=sorted(set(locked) - set(installed)),
        manifests={n: sha((project / n).read_bytes()) for n in ("pyproject.toml", "uv.lock")},
        registry_sources=sorted(
            {p["source"].get("registry", "LOCAL_EDITABLE") for p in lock["package"]}
        ),
        locked_archive_hashes={
            n: sorted(
                {a["hash"] for a in p.get("wheels", [])}
                | ({p["sdist"]["hash"]} if "sdist" in p else set())
            )
            for n, p in locked.items()
        },
        archive_origin="UNVERIFIED_WITHOUT_ORIGINAL_ARCHIVE_BYTES",
        java="NOT_APPLICABLE_PYTHON_ONLY_RC",
        maven="NOT_APPLICABLE_PYTHON_ONLY_RC",
        network_resolution=False,
        supply_chain_completeness=False,
    )


def source_archive(directory: Path, inventory: dict[str, str]) -> dict[str, Any]:
    directory = external(directory)
    directory.mkdir(parents=True, exist_ok=True)
    require(len(inventory) <= 128, "SOURCE_BUDGET")
    target = directory / "offline-rc-source.zip"
    with zipfile.ZipFile(target, "x", compression=zipfile.ZIP_STORED) as archive:
        for name, expected in sorted(inventory.items()):
            path = (ROOT / name).resolve()
            require(
                ROOT in path.parents
                and not Path(name).is_absolute()
                and ".." not in Path(name).parts,
                "SOURCE_PATH",
            )
            raw = path.read_bytes()
            require(sha(raw) == expected and len(raw) <= 4 * 1024 * 1024, "SOURCE_HASH_OR_BUDGET")
            info = zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    return dict(
        name=target.name,
        sha256=sha(target.read_bytes()),
        files=len(inventory),
        format="DETERMINISTIC_SOURCE_ONLY_ZIP",
        launcher=False,
        genuine_data=False,
    )


def identity(bindings: dict[str, Any]) -> dict[str, Any]:
    required = {
        "git_sha",
        "software",
        "dependencies",
        "strategies",
        "qualification",
        "risk",
        "execution",
        "monitoring",
        "authority",
        "evidence_payload",
        "build_environment",
        "test_receipts",
    }
    require(set(bindings) == required, "INCOMPLETE_RC_BINDING")
    require(
        len(bindings["git_sha"]) == 40
        and all(c in "0123456789abcdef" for c in bindings["git_sha"]),
        "GIT_IDENTITY",
    )
    require(
        all(
            isinstance(v, str) and len(v) == 64 and all(c in "0123456789abcdef" for c in v)
            for k, v in bindings.items()
            if k != "git_sha"
        ),
        "FINGERPRINT_IDENTITY",
    )
    body = dict(version="Phase1427OfflineRC.v1", scope="SYNTHETIC_OFFLINE", bindings=bindings)
    return body | {"rc_id": "offline-rc-" + sha(canonical(body))}
