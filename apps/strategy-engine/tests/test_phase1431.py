"""Synthetic Gate 31-A checks never acquire or install dependencies."""

import ast
import copy
import tomllib
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

import pytest
from packaging.markers import default_environment
from packaging.tags import Tag, sys_tags

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1431.artifacts import (
    HOST,
    LOCK_ONLY,
    VERSION,
    authorize,
    locked_closure,
    packet,
    quarantine,
    seal,
    select_wheel,
    source_host,
    validate_manifest,
    verify_raw,
)

RAW = b"synthetic-wheel"
TAGS = {Tag("py3", "none", "any"), Tag("cp312", "cp312", "win_amd64")}
NOW = datetime(2026, 10, 17, 12, tzinfo=UTC)


def fixture() -> dict[str, Any]:
    return seal(
        dict(
            version=VERSION,
            baseline="a" * 40,
            max_artifacts=1,
            max_total_bytes=len(RAW),
            source_hosts=[HOST],
            artifacts=[
                dict(
                    name="synthetic",
                    version="1.0",
                    filename="synthetic-1.0-py3-none-any.whl",
                    artifact_id="synthetic==1.0/synthetic-1.0-py3-none-any.whl",
                    expected_sha256=sha(RAW),
                    expected_bytes=len(RAW),
                    source_host=HOST,
                    source_url="https://" + HOST + "/synthetic-1.0-py3-none-any.whl",
                    hash_provenance=LOCK_ONLY,
                )
            ],
        )
    )


@pytest.mark.parametrize(
    "fault",
    [
        "valid",
        "fingerprint",
        "duplicate",
        "digest",
        "platform",
        "count",
        "bytes",
        "source",
        "version",
        "identity",
    ],
)
def test_manifest_faults(fault: str) -> None:
    e = fixture()
    b = e["manifest"]
    row = b["artifacts"][0]
    if fault == "duplicate":
        b["artifacts"].append(copy.deepcopy(row))
    elif fault == "digest":
        row["expected_sha256"] = ""
    elif fault == "platform":
        row["filename"] = "synthetic-1.0-cp311-cp311-manylinux_2_17_x86_64.whl"
    elif fault == "count":
        b["max_artifacts"] = 2
    elif fault == "bytes":
        b["max_total_bytes"] += 1
    elif fault == "source":
        b["source_hosts"] = ["mirror.invalid"]
    elif fault == "version":
        b["version"] = "unknown"
    elif fault == "identity":
        row["version"] = "2.0"
    e = seal(b)
    if fault == "fingerprint":
        e["fingerprint"] = "0" * 64
    if fault == "valid":
        assert validate_manifest(e, TAGS) == b
    else:
        with pytest.raises(ValueError):
            validate_manifest(e, TAGS)


@pytest.mark.parametrize(
    "suffix",
    [
        "@evil.invalid/a.whl",
        ":443/a.whl",
        "/../a.whl",
        "/a.whl?token=x",
        "/%2e%2e/a.whl",
        "/a.whl#fragment",
    ],
)
def test_source_allowlist(suffix: str) -> None:
    with pytest.raises(ValueError):
        source_host("https://" + HOST + suffix)


@pytest.mark.parametrize(
    "fault",
    [
        "valid",
        "absent",
        "untrusted_owner",
        "expired",
        "future",
        "wrong_manifest",
        "source",
        "budget",
        "install",
        "untrusted_digest",
        "wrong_packet",
        "too_long",
        "wrong_destination",
        "rejected",
    ],
)
def test_owner_authorization(fault: str) -> None:
    e = fixture()
    request = packet(e, "D:/synthetic-quarantine/manifest")
    approval = {
        k: request[k]
        for k in (
            "baseline",
            "artifact_allowlist",
            "source_hosts",
            "destination",
            "max_artifacts",
            "max_total_bytes",
            "action",
            "no_upgrades",
            "no_installation",
            "manifest_fingerprint",
        )
    }
    approval.update(
        version="Phase1431OwnerDecision.v1",
        decision="APPROVED",
        issued_at=(NOW - timedelta(minutes=1)).isoformat(),
        expires_at=(NOW + timedelta(minutes=10)).isoformat(),
    )
    if fault == "expired":
        approval["expires_at"] = NOW.isoformat()
    elif fault == "future":
        approval["issued_at"] = (NOW + timedelta(minutes=1)).isoformat()
    elif fault == "wrong_manifest":
        approval["manifest_fingerprint"] = "0" * 64
    elif fault == "source":
        approval["source_hosts"] = ["mirror.invalid"]
    elif fault == "budget":
        approval["max_total_bytes"] += 1
    elif fault == "install":
        approval["action"] = "INSTALL"
    elif fault == "too_long":
        approval["expires_at"] = (NOW + timedelta(days=2)).isoformat()
    elif fault == "wrong_destination":
        approval["destination"] = "D:/other"
    elif fault == "rejected":
        approval["decision"] = "REJECTED"
    elif fault == "wrong_packet":
        request["no_installation"] = False
    trusted = set() if fault == "untrusted_owner" else {sha(canonical(approval))}
    digests = set() if fault == "untrusted_digest" else {sha(RAW)}
    if fault == "valid":
        authorize(e, request, approval, NOW, trusted, digests, TAGS)
        assert request["decision"] == "PENDING" and request["download_authorized"] is False
        assert request["installation_status"] == "NOT_ATTEMPTED"
    else:
        with pytest.raises(ValueError):
            authorize(
                e, request, None if fault == "absent" else approval, NOW, trusted, digests, TAGS
            )


@pytest.mark.parametrize("fault", ["valid", "hash", "size", "allowlist", "untrusted"])
def test_raw_verification(fault: str) -> None:
    row = fixture()["manifest"]["artifacts"][0]
    data = RAW if fault not in {"hash", "size"} else b"x" * (len(RAW) + (fault == "size"))
    allowed = set() if fault == "allowlist" else {row["artifact_id"]}
    trusted = set() if fault == "untrusted" else {sha(RAW)}
    if fault == "valid":
        assert (
            verify_raw(row, data, allowed, trusted)
            == "RAW_HASH_MATCH_ONLY_NOT_INSTALLATION_AUTHORITY"
        )
    else:
        with pytest.raises(ValueError):
            verify_raw(row, data, allowed, trusted)


@pytest.mark.parametrize("fault", ["valid", "duplicate", "missing_digest", "wrong_platform"])
def test_wheel_selection(fault: str) -> None:
    filename = (
        "synthetic-1.0-"
        + ("cp311-cp311-manylinux_2_17_x86_64" if fault == "wrong_platform" else "py3-none-any")
        + ".whl"
    )
    wheel = dict(url="https://" + HOST + "/" + filename, hash="sha256:" + sha(RAW), size=len(RAW))
    if fault == "missing_digest":
        wheel["hash"] = ""
    p = dict(
        name="synthetic", version="1.0", wheels=[wheel, wheel] if fault == "duplicate" else [wheel]
    )
    if fault == "valid":
        assert select_wheel(p, TAGS)["filename"] == filename
    else:
        with pytest.raises(ValueError):
            select_wheel(p, TAGS)


@pytest.mark.parametrize("platform,count", [("win32", 8), ("linux", 7)])
def test_real_lock_closure(platform: str, count: int) -> None:
    project = ROOT / "apps/strategy-engine"
    spec = tomllib.loads((project / "pyproject.toml").read_text())
    lock = tomllib.loads((project / "uv.lock").read_text())
    env = {k: str(v) for k, v in default_environment().items()} | {
        "extra": "",
        "sys_platform": platform,
    }
    result = locked_closure(spec, lock, env)
    assert len(result) == count
    assert ("tzdata" in {r["name"] for r in result}) == (platform == "win32")
    for row in result:
        p = next(p for p in lock["package"] if p["name"] == row["name"])
        assert select_wheel(p, set(sys_tags()))["expected_sha256"]


@pytest.mark.parametrize("fault", ["duplicate", "missing", "wrong_version"])
def test_lock_rejection(fault: str) -> None:
    project = ROOT / "apps/strategy-engine"
    spec = tomllib.loads((project / "pyproject.toml").read_text())
    lock = tomllib.loads((project / "uv.lock").read_text())
    if fault == "duplicate":
        lock["package"].append(copy.deepcopy(lock["package"][0]))
    elif fault == "missing":
        lock["package"] = [p for p in lock["package"] if p["name"] != "tzdata"]
    else:
        next(p for p in lock["package"] if p["name"] == "tzdata")["version"] = "2025.1"
    with pytest.raises(ValueError):
        locked_closure(
            spec, lock, {k: str(v) for k, v in default_environment().items()} | {"extra": ""}
        )


@pytest.mark.parametrize("fault", ["valid", "relative", "traversal", "checkout", "existing"])
def test_quarantine_paths(tmp_path: Path, fault: str) -> None:
    p = tmp_path / "new"
    if fault == "relative":
        p = Path("relative/new")
    elif fault == "traversal":
        p = tmp_path / ".." / "new"
    elif fault == "checkout":
        p = ROOT / "uncreated-quarantine"
    elif fault == "existing":
        p = tmp_path
    if fault == "valid":
        assert quarantine(p, ROOT) == p.as_posix()
        assert not p.exists()
    else:
        with pytest.raises(ValueError):
            quarantine(p, ROOT)


def test_deterministic_pending_packet_and_preservation() -> None:
    assert canonical(fixture()) == canonical(fixture())
    request = packet(fixture(), "D:/synthetic-quarantine/manifest")
    assert request["owner_decision_evidence"] is None
    assert request["no_installation"] and request["no_upgrades"]
    assert sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes()) == (
        "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31"
    )


def test_no_network_or_installation_implementation() -> None:
    path = ROOT / "apps/strategy-engine/src/strategy_engine/phase1431/artifacts.py"
    imports: set[str] = set()
    for node in ast.walk(ast.parse(path.read_bytes())):
        if isinstance(node, ast.Import):
            imports.update(n.name.split(".")[0] for n in node.names)
        elif isinstance(node, ast.ImportFrom):
            imports.add((node.module or "").split(".")[0])
    assert not imports & {"socket", "urllib", "requests", "httpx", "pip", "venv", "subprocess"}
