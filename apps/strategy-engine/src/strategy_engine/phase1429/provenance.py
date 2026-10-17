"""Separate exact original archive correspondence from installed-file integrity."""

import email.parser
import importlib.metadata as md
import os
import sys
import tarfile
import tomllib
import zipfile
from pathlib import Path
from typing import Any

from packaging.requirements import Requirement
from packaging.tags import sys_tags
from packaging.utils import canonicalize_name, parse_sdist_filename, parse_wheel_filename

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1427.assessment import distribution_record

VERSION = "Phase1429Provenance.v1"
LOCK_TRUST = "FROZEN_REPOSITORY_LOCK_NO_INDEPENDENT_REGISTRY_ATTESTATION"


def lock_entries(raw: bytes) -> dict[str, Any]:
    packages = tomllib.loads(raw.decode())["package"]
    result: dict[str, Any] = {}
    for package in packages:
        name = canonicalize_name(package["name"])
        if name in result:
            raise ValueError("AMBIGUOUS_LOCK_IDENTITY")
        result[name] = package
    return result


def archive(path: Path, location: str) -> dict[str, Any]:
    if path.is_symlink() or path.stat().st_size > 128 * 1024 * 1024:
        raise ValueError("ARCHIVE_BOUNDARY_OR_SIZE")
    row: dict[str, Any] = dict(
        location=location,
        filename=path.name,
        sha256=sha(path.read_bytes()),
        identity="UNVERIFIED",
        applicable=False,
    )
    try:
        if path.suffix == ".whl":
            name, version, _, tags = parse_wheel_filename(path.name)
            with zipfile.ZipFile(path) as z:
                candidates = [i for i in z.infolist() if i.filename.endswith(".dist-info/METADATA")]
                if len(candidates) != 1 or candidates[0].file_size > 1024 * 1024:
                    raise ValueError("AMBIGUOUS_ARCHIVE_METADATA")
                raw = z.read(candidates[0])
            row.update(type="wheel", tags=sorted(str(t) for t in tags))
            applicable = bool(tags & set(sys_tags()))
        else:
            name, version = parse_sdist_filename(path.name)
            if path.name.endswith(".zip"):
                with zipfile.ZipFile(path) as z:
                    candidates = [
                        i
                        for i in z.infolist()
                        if i.filename.endswith("/PKG-INFO") and i.filename.count("/") == 1
                    ]
                    if len(candidates) != 1 or candidates[0].file_size > 1024 * 1024:
                        raise ValueError("AMBIGUOUS_ARCHIVE_METADATA")
                    raw = z.read(candidates[0])
            else:
                with tarfile.open(path, "r:gz") as t:
                    members = [
                        i
                        for i in t.getmembers()
                        if i.name.endswith("/PKG-INFO") and i.name.count("/") == 1
                    ]
                    if (
                        len(members) != 1
                        or not members[0].isfile()
                        or members[0].size > 1024 * 1024
                    ):
                        raise ValueError("AMBIGUOUS_ARCHIVE_METADATA")
                    stream = t.extractfile(members[0])
                    if stream is None:
                        raise ValueError("MISSING_ARCHIVE_METADATA")
                    raw = stream.read(1024 * 1024 + 1)
            row.update(type="sdist", tags=[])
            applicable = True
        meta = email.parser.BytesParser().parsebytes(raw)
        if (
            len(meta.get_all("Name", [])) != 1
            or len(meta.get_all("Version", [])) != 1
            or canonicalize_name(meta["Name"]) != name
            or meta["Version"] != str(version)
        ):
            raise ValueError("ARCHIVE_IDENTITY_MISMATCH")
        row.update(
            name=str(name),
            version=str(version),
            identity="VERIFIED_METADATA",
            applicable=applicable,
            disposition="CANDIDATE" if applicable else "NOT_APPLICABLE",
        )
    except (ValueError, KeyError, zipfile.BadZipFile, tarfile.TarError, EOFError) as exc:
        row.update(disposition="NOT_APPLICABLE", reason=type(exc).__name__ + ":" + str(exc))
    return row


def decision(
    name: str,
    version: str,
    locked: dict[str, Any] | None,
    installed: dict[str, Any],
    candidates: list[dict[str, Any]],
) -> dict[str, Any]:
    applicable = [
        a
        for a in candidates
        if a.get("name") == name
        and a.get("version") == version
        and a["identity"] == "VERIFIED_METADATA"
        and a["applicable"]
    ]
    artifacts = (
        []
        if locked is None or locked["version"] != version
        else (locked.get("wheels", []) + ([locked["sdist"]] if "sdist" in locked else []))
    )
    results = []
    for candidate in applicable:
        exact = [
            a
            for a in artifacts
            if a["url"].rsplit("/", 1)[-1] == candidate["filename"]
        ]
        status = "UNVERIFIED"
        if len(exact) == 1:
            status = (
                "ARCHIVE_HASH_VERIFIED"
                if exact[0]["hash"] == "sha256:" + candidate["sha256"]
                else "ARCHIVE_HASH_MISMATCH"
            )
        results.append(candidate | {"status": status, "exact_lock_artifacts": exact})
    duplicates = len({(a["filename"], a["sha256"]) for a in applicable}) != len(applicable)
    if duplicates:
        status = "UNVERIFIED"
    elif any(a["status"] == "ARCHIVE_HASH_MISMATCH" for a in results):
        status = "ARCHIVE_HASH_MISMATCH"
    elif any(a["status"] == "ARCHIVE_HASH_VERIFIED" for a in results):
        status = "ARCHIVE_HASH_VERIFIED"
    elif applicable:
        status = "UNVERIFIED"
    else:
        status = "ARCHIVE_MISSING"
    version_match = locked is not None and locked["version"] == version
    integrity = (
        "INSTALLED_RECORD_VERIFIED"
        if installed["status"] == "VERIFIED_INSTALLED_RECORD"
        else "LOCK_VERSION_ONLY"
        if version_match
        else "UNVERIFIED"
    )
    return dict(
        name=name,
        version=version,
        installed_integrity=integrity,
        archive_provenance=status,
        duplicate_candidates=duplicates,
        candidates=results,
        locked_version_match=version_match,
        lockfile_entry=locked,
        expected_artifacts=artifacts,
        installed_record=installed,
        evidence_level="B"
        if status == "ARCHIVE_HASH_VERIFIED"
        else "C"
        if integrity == "INSTALLED_RECORD_VERIFIED"
        else "D"
        if version_match
        else "E",
        lockfile_trust=LOCK_TRUST,
        independent_registry_attestation=False,
    )


def record(dist: md.Distribution, boundary: Path) -> dict[str, Any]:
    try:
        return distribution_record(dist, boundary)
    except (OSError, ValueError, TypeError) as exc:
        return dict(
            status="UNVERIFIED", failures=[type(exc).__name__], verified_files={}, unhashed=[]
        )


def scopes(
    installed: dict[str, md.Distribution],
    direct: dict[str, list[str]],
) -> dict[str, Any]:
    result: dict[str, Any] = {
        n: {"groups": [], "direct_groups": [], "optional_extras": []} for n in installed
    }
    for group, roots in direct.items():
        pending = [(Requirement(raw), True) for raw in roots]
        seen = set()
        while pending:
            req, is_direct = pending.pop()
            name = canonicalize_name(req.name)
            key = name, tuple(sorted(req.extras))
            if name not in installed:
                raise ValueError("MISSING_SCOPED_DEPENDENCY:" + name)
            row = result[name]
            if group not in row["groups"]:
                row["groups"].append(group)
            if is_direct and group not in row["direct_groups"]:
                row["direct_groups"].append(group)
            row["optional_extras"] = sorted(set(row["optional_extras"]) | req.extras)
            if key in seen:
                continue
            seen.add(key)
            if installed[name].version not in req.specifier:
                raise ValueError("DEPENDENCY_VERSION_CONSTRAINT:" + name)
            for raw in installed[name].requires or ():
                child = Requirement(raw)
                if child.marker is None or any(
                    child.marker.evaluate({"extra": extra}) for extra in {"", *req.extras}
                ):
                    pending.append((child, False))
    for row in result.values():
        row["groups"].sort()
        row["direct_groups"].sort()
        row["relationship"] = "DIRECT" if row["direct_groups"] else "TRANSITIVE_OR_UNSELECTED"
    return result


def audit() -> dict[str, Any]:
    project = ROOT / "apps/strategy-engine"
    lock = lock_entries((project / "uv.lock").read_bytes())
    spec = tomllib.loads((project / "pyproject.toml").read_text())
    installed: dict[str, md.Distribution] = {
        str(canonicalize_name(d.metadata["Name"])): d for d in md.distributions()
    }
    boundaries = {name: Path(sys.prefix) for name in installed}
    locations = {name: "python-env/Lib/site-packages" for name in installed}
    uv = Path(os.environ["LOCALAPPDATA"]) / "uv/cache"
    for name, version in (("hatchling", "1.27.0"), ("trove-classifiers", "2026.6.1.19")):
        paths = list((uv / "archive-v0").glob(f"*/{name.replace('-', '_')}-{version}.dist-info"))
        if len(paths) != 1:
            raise ValueError("AMBIGUOUS_OR_MISSING_BACKEND:" + name)
        installed[name] = md.PathDistribution(paths[0])
        boundaries[name] = paths[0].parent
        locations[name] = "uv-cache/" + paths[0].parent.relative_to(uv).as_posix()
    candidates = []
    roots = {
        "uv-cache": uv,
        "pip-wheel-cache": Path(os.environ["LOCALAPPDATA"]) / "pip/Cache/wheels",
    }
    for alias, root in roots.items():
        if not root.exists():
            continue
        for current, dirs, names in os.walk(root, followlinks=False):
            dirs[:] = sorted(d for d in dirs if not (Path(current) / d).is_symlink())
            for name in sorted(names):
                p = Path(current) / name
                if name.endswith((".whl", ".tar.gz", ".zip")) and p.is_file():
                    candidates.append(archive(p, alias + "/" + p.relative_to(root).as_posix()))
                    if len(candidates) > 4096:
                        raise ValueError("CACHE_ARCHIVE_BUDGET")
    direct = dict(
        runtime=spec["project"]["dependencies"] + ["tzdata"],
        build=spec["build-system"]["requires"],
        test=["pytest", "jsonschema[format]"],
        development=["mypy", "ruff", "types-jsonschema"],
    )
    groups = scopes(installed, direct)
    optional_edges = []
    for name, dist in sorted(installed.items()):
        for raw in dist.requires or ():
            requirement = Requirement(raw)
            if requirement.marker is not None and not requirement.marker.evaluate({"extra": ""}):
                for extra in groups[name]["optional_extras"]:
                    if requirement.marker.evaluate({"extra": extra}):
                        optional_edges.append(dict(owner=name, extra=extra, requirement=raw))
    rows = []
    for name, dist in sorted(installed.items()):
        row = decision(
            name, dist.version, lock.get(name), record(dist, boundaries[name]), candidates
        )
        metadata = dist.read_text("METADATA")
        row.update(
            scope=groups[name],
            installed_location=locations[name],
            metadata_fingerprint=sha(metadata.encode()) if metadata else None,
            origin="LOCAL_PROJECT" if name == "strategy-engine" else "THIRD_PARTY",
        )
        rows.append(row)
    body = dict(
        version=VERSION,
        scope="OFFLINE_ASSESSMENT",
        lockfile_trust=LOCK_TRUST,
        lock_sha256=sha((project / "uv.lock").read_bytes()),
        direct=direct,
        optional_dependency_edges=optional_edges,
        runtime_supplement=dict(
            requirement="tzdata",
            declared_in_project_runtime=False,
            evidence="Phase14.28 isolated runtime explicitly copies tzdata for Windows ZoneInfo",
            status="EXPLICIT_PROBE_DEPENDENCY_NOT_DECLARED_IN_PROJECT_METADATA",
        ),
        cache_roots=list(roots),
        candidates=candidates,
        dependencies=rows,
        classification="PHASE_14_29_PARTIAL",
        recommendation="OFFLINE_RC_CONDITIONAL",
        original_archive_provenance="UNRESOLVED",
        owner_approval=None,
        real_kite_requests=0,
        operational="NO_GO",
    )
    return body | {"fingerprint": sha(canonical(body))}
