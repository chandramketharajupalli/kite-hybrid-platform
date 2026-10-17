"""Exact offline archive, scope and installed-integrity counterexamples."""

import base64
import hashlib
import importlib.metadata as md
import subprocess
import zipfile
from pathlib import Path
from typing import Any

import pytest

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import sha
from strategy_engine.phase1429.provenance import archive, decision, lock_entries, record, scopes


def fixture(tmp_path: Path, tag: str = "py3-none-any", metadata_name: str = "synthetic") -> Path:
    path = tmp_path / f"synthetic-1.0-{tag}.whl"
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("synthetic-1.0.dist-info/METADATA", f"Name: {metadata_name}\nVersion: 1.0\n")
    return path


def entry(path: Path) -> dict[str, Any]:
    return dict(
        name="synthetic",
        version="1.0",
        wheels=[
            dict(
                url="https://invalid.example/" + path.name, hash="sha256:" + sha(path.read_bytes())
            ),
        ],
    )


def test_committed_resource_identity() -> None:
    name = "apps/strategy-engine/src/strategy_engine/phase1428/data/universe.csv"
    raw = (ROOT / name).read_bytes()
    assert subprocess.check_output(["git", "show", "HEAD:" + name], cwd=ROOT) == raw
    assert raw == (ROOT / "config/universes/phase14_unique_universe.csv").read_bytes()
    assert raw.count(b"\r\n") == raw.count(b"\n") == 1116


@pytest.mark.parametrize(
    "fault",
    [
        "valid",
        "missing",
        "hash",
        "duplicate",
        "wrong_platform",
        "wrong_name",
        "wrong_version",
        "wrong_artifact",
        "wrong_metadata",
        "corrupt",
        "no_lock",
        "lock_version",
    ],
)
def test_exact_artifact_decision(tmp_path: Path, fault: str) -> None:
    p = fixture(
        tmp_path,
        tag="cp311-cp311-manylinux_2_17_x86_64" if fault == "wrong_platform" else "py3-none-any",
        metadata_name="other" if fault == "wrong_metadata" else "synthetic",
    )
    locked = entry(p)
    if fault == "hash":
        locked["wheels"][0]["hash"] = "sha256:" + "0" * 64
    if fault == "lock_version":
        locked["version"] = "2.0"
    if fault == "wrong_artifact":
        locked["wheels"][0]["url"] = "https://invalid.example/synthetic-1.0-cp312-win32.whl"
    if fault == "corrupt":
        p.write_bytes(b"invalid zip")
    candidate = archive(p, "synthetic-cache/" + p.name)
    candidates = [] if fault == "missing" else [candidate]
    if fault == "duplicate":
        candidates.append(candidate.copy())
    result = decision(
        "other" if fault == "wrong_name" else "synthetic",
        "2.0" if fault == "wrong_version" else "1.0",
        None if fault == "no_lock" else locked,
        {"status": "VERIFIED_INSTALLED_RECORD"},
        candidates,
    )
    expected = (
        "ARCHIVE_HASH_VERIFIED"
        if fault == "valid"
        else "ARCHIVE_HASH_MISMATCH"
        if fault == "hash"
        else "UNVERIFIED"
        if fault in {"duplicate", "wrong_artifact", "no_lock", "lock_version"}
        else "ARCHIVE_MISSING"
    )
    assert result["archive_provenance"] == expected
    assert result["installed_integrity"] == "INSTALLED_RECORD_VERIFIED"
    assert result["independent_registry_attestation"] is False
    if fault == "valid":
        assert result["evidence_level"] == "B"


@pytest.mark.parametrize("fault", ["valid", "missing_file", "modified", "no_record", "bad_hash"])
def test_record_verification(tmp_path: Path, fault: str) -> None:
    info = tmp_path / "synthetic-1.0.dist-info"
    info.mkdir()
    (info / "METADATA").write_text("Name: synthetic\nVersion: 1.0\n")
    p = tmp_path / "synthetic.py"
    p.write_bytes(b"value=1\n")
    digest = base64.urlsafe_b64encode(hashlib.sha256(p.read_bytes()).digest()).rstrip(b"=").decode()
    (info / "RECORD").write_text("synthetic.py,sha256=" + digest + ",8\n")
    if fault == "missing_file":
        p.unlink()
    elif fault == "modified":
        p.write_bytes(b"value=2\n")
    elif fault == "no_record":
        (info / "RECORD").unlink()
    elif fault == "bad_hash":
        (info / "RECORD").write_text("synthetic.py,unknownhash=abc,8\n")
    result = record(md.PathDistribution(info), tmp_path)
    assert result["status"] == ("VERIFIED_INSTALLED_RECORD" if fault == "valid" else "UNVERIFIED")


def test_lock_duplicate_rejected() -> None:
    raw = b'[[package]]\nname="x"\nversion="1"\n'
    assert lock_entries(raw)["x"]["version"] == "1"
    with pytest.raises(ValueError, match="AMBIGUOUS_LOCK_IDENTITY"):
        lock_entries(raw + raw)


@pytest.mark.parametrize("mode", ["valid", "missing", "wrong_version"])
def test_source_archive_identity(tmp_path: Path, mode: str) -> None:
    p = tmp_path / "synthetic-1.0.zip"
    with zipfile.ZipFile(p, "w") as z:
        if mode != "missing":
            z.writestr(
                "synthetic-1.0/PKG-INFO",
                "Name: synthetic\nVersion: " + ("2.0" if mode == "wrong_version" else "1.0") + "\n",
            )
    result = archive(p, "synthetic-cache/" + p.name)
    assert result["identity"] == ("VERIFIED_METADATA" if mode == "valid" else "UNVERIFIED")


def test_runtime_build_test_separation(tmp_path: Path) -> None:
    distributions: dict[str, md.Distribution] = {}
    for name, requires in (
        ("runtime", ["shared"]),
        ("backend", ["shared"]),
        ("testtool", []),
        ("shared", []),
    ):
        p = tmp_path / (name + "-1.0.dist-info")
        p.mkdir()
        (p / "METADATA").write_text(
            "Name: "
            + name
            + "\nVersion: 1.0\n"
            + "".join("Requires-Dist: " + dep + "\n" for dep in requires)
        )
        distributions[name] = md.PathDistribution(p)
    result = scopes(
        distributions, {"runtime": ["runtime"], "build": ["backend"], "test": ["testtool"]}
    )
    assert result["shared"]["groups"] == ["build", "runtime"]
    assert result["testtool"]["groups"] == ["test"]
    assert result["backend"]["direct_groups"] == ["build"]
    assert not result["shared"]["direct_groups"]


@pytest.mark.parametrize(
    "status,locked,expected",
    [
        ("UNVERIFIED", True, "LOCK_VERSION_ONLY"),
        ("UNVERIFIED", False, "UNVERIFIED"),
        ("VERIFIED_INSTALLED_RECORD", False, "INSTALLED_RECORD_VERIFIED"),
    ],
)
def test_backend_missing_lock_is_not_archive_proof(
    status: str, locked: bool, expected: str
) -> None:
    result = decision("backend", "1", {"version": "1"} if locked else None, {"status": status}, [])
    assert result["installed_integrity"] == expected
    assert result["archive_provenance"] == "ARCHIVE_MISSING"
