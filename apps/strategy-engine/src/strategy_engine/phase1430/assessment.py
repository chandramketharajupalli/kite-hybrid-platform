"""Fail-closed metadata, installed graph and offline installation input checks."""

import base64
import csv
import email.parser
import hashlib
import importlib.metadata as md
import io
import zipfile
from pathlib import Path
from typing import Any

from packaging.requirements import Requirement
from packaging.utils import canonicalize_name

REQUIREMENT = "tzdata==2026.4; sys_platform == 'win32'"
VERSION = "Phase1430WindowsDependencies.v1"


def require(value: bool, reason: str) -> None:
    if not value:
        raise ValueError(reason)


def metadata(spec: dict[str, Any], lock: dict[str, Any]) -> dict[str, Any]:
    declarations = [Requirement(r) for r in spec["project"]["dependencies"]]
    matches = [r for r in declarations if canonicalize_name(r.name) == "tzdata"]
    require(len(matches) == 1, "TZDATA_DECLARATION")
    req = matches[0]
    require(str(req.specifier) == "==2026.4" and not req.extras, "TZDATA_CONSTRAINT")
    require(str(req.marker) == 'sys_platform == "win32"', "WINDOWS_MARKER")
    packages = {p["name"]: p for p in lock["package"]}
    require(len(packages) == len(lock["package"]), "DUPLICATE_LOCK_PACKAGE")
    require(packages["tzdata"]["version"] in req.specifier, "TZDATA_LOCK_VERSION")
    project = packages["strategy-engine"]
    require(
        [p for p in project["dependencies"] if p["name"] == "tzdata"]
        == [{"name": "tzdata", "marker": "sys_platform == 'win32'"}],
        "LOCK_RUNTIME_EDGE",
    )
    require(
        [p for p in project["metadata"]["requires-dist"] if p["name"] == "tzdata"]
        == [{"name": "tzdata", "marker": "sys_platform == 'win32'", "specifier": "==2026.4"}],
        "LOCK_DECLARATION",
    )
    return dict(version=VERSION, requirement=REQUIREMENT, lock_consistency="PASS")


def graph(
    roots: list[str], installed: dict[str, md.Distribution], platform: str = "win32"
) -> list[dict[str, Any]]:
    pending = [(Requirement(r), True) for r in roots]
    result: dict[str, dict[str, Any]] = {}
    seen: set[tuple[str, tuple[str, ...]]] = set()
    while pending:
        req, direct = pending.pop()
        if req.marker and not req.marker.evaluate({"sys_platform": platform, "extra": ""}):
            continue
        name = str(canonicalize_name(req.name))
        require(name in installed, "MISSING_DEPENDENCY:" + name)
        dist = installed[name]
        require(dist.version in req.specifier, "DEPENDENCY_VERSION:" + name)
        row = result.setdefault(name, dict(name=name, version=dist.version, direct=False))
        row["direct"] = row["direct"] or direct
        key = name, tuple(sorted(req.extras))
        if key in seen:
            continue
        seen.add(key)
        for raw in dist.requires or ():
            child = Requirement(raw)
            if child.marker is None or any(
                child.marker.evaluate({"sys_platform": platform, "extra": extra})
                for extra in {"", *req.extras}
            ):
                # The edge's extra has already been evaluated in its parent's context.
                child.marker = None
                pending.append((child, False))
    return [result[n] for n in sorted(result)]


def installation_gate(
    runtime: list[dict[str, Any]], provenance: list[dict[str, Any]]
) -> dict[str, Any]:
    require(len({r["name"] for r in provenance}) == len(provenance), "DUPLICATE_PROVENANCE")
    rows = {r["name"]: r for r in provenance}
    missing = []
    for dep in runtime:
        row = rows.get(dep["name"])
        if (
            row is None
            or row["version"] != dep["version"]
            or row["archive_provenance"] != "ARCHIVE_HASH_VERIFIED"
            or row.get("duplicate_candidates", False)
        ):
            missing.append(dep["name"])
    return dict(
        status="BLOCKED" if missing else "INPUTS_AVAILABLE",
        missing_approved_archives=sorted(missing),
        installation_attempted=False,
        installed_RECORD_is_archive_proof=False,
    )


def installed_set(expected: list[str], observed: list[str]) -> None:
    require(len(observed) == len(set(observed)), "DUPLICATE_INSTALLED")
    require(set(expected) == set(observed), "MISSING_OR_UNDECLARED_INSTALLED")


def wheel_metadata(path: Path) -> dict[str, Any]:
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        require(len(names) == len(set(names)), "DUPLICATE_WHEEL_MEMBER")
        metadata_names = [n for n in names if n.endswith(".dist-info/METADATA")]
        records = [n for n in names if n.endswith(".dist-info/RECORD")]
        require(len(metadata_names) == len(records) == 1, "WHEEL_METADATA_INVENTORY")
        meta = email.parser.BytesParser().parsebytes(z.read(metadata_names[0]))
        declarations = [Requirement(r) for r in meta.get_all("Requires-Dist", [])]
        tz = [str(r) for r in declarations if canonicalize_name(r.name) == "tzdata"]
        require(tz == [str(Requirement(REQUIREMENT))], "WHEEL_WINDOWS_DEPENDENCY")
        listed = []
        for name, digest, size in csv.reader(io.StringIO(z.read(records[0]).decode())):
            listed.append(name)
            if name == records[0]:
                require(not digest and not size, "RECORD_SELF_HASH")
                continue
            raw = z.read(name)
            expected = base64.urlsafe_b64encode(hashlib.sha256(raw).digest()).rstrip(b"=")
            require(digest == "sha256=" + expected.decode(), "WHEEL_RECORD_HASH")
            require(size == str(len(raw)), "WHEEL_RECORD_SIZE")
        require(len(listed) == len(set(listed)) and set(listed) == set(names), "WHEEL_RECORD_SET")
        return dict(requires_dist=sorted(str(r) for r in declarations), RECORD="PASS")
