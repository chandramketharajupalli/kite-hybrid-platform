"""Synthetic byte fixtures never attest or acquire real distribution archives."""

import ast
import json
from copy import deepcopy
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import pytest

from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1431.artifacts import seal
from strategy_engine.phase1432.attestation import MATCH
from strategy_engine.phase1433 import acquisition as policy

ROOT = Path(__file__).resolve().parents[3]
NOW = datetime(2026, 10, 17, 12, 5, tzinfo=UTC)


def real_manifest() -> dict[str, Any]:
    result: dict[str, Any] = json.loads((ROOT / (
        "research/phase-14.31/generation-1/artifact-acquisition-manifest.json"
    )).read_bytes())
    return result


@pytest.fixture
def fixture(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> dict[str, Any]:
    acquisition = real_manifest()
    rows = acquisition["manifest"]["artifacts"]
    directory = tmp_path / "synthetic-quarantine"
    directory.mkdir()
    ledger_rows = []
    for i, row in enumerate(rows, 1):
        raw = bytes([i]) * row["expected_bytes"]
        row["expected_sha256"] = sha(raw)
        (directory / row["filename"]).write_bytes(raw)
        ledger_rows.append(dict(
            artifact_id=row["artifact_id"], filename=row["filename"],
            expected_sha256=sha(raw), expected_bytes=len(raw), classification=MATCH,
            identity_match=True, digest_match=True, size_match=True, yanked=False,
        ))
    acquisition = seal(acquisition["manifest"])
    ledger = seal(dict(version="Phase1432AttestationLedger.v1", artifacts=ledger_rows,
                       acquisition_fingerprint=acquisition["fingerprint"],
                       independent_digest_matches=8, acquisition_eligible=True))
    # Explicit test-only pins. Production defaults always pin the real sealed manifests.
    monkeypatch.setattr(policy, "ACQUISITION", acquisition["fingerprint"])
    monkeypatch.setattr(policy, "ATTESTATION", ledger["fingerprint"])
    request = policy.packet(acquisition, ledger, directory.resolve().as_posix())
    approval = deepcopy(request["manifest"])
    approval.update(version="Phase1433OwnerDecision.v1", decision="APPROVED",
                    request_fingerprint=request["fingerprint"], owner_identity="SYNTHETIC_OWNER",
                    issued_at="2026-10-17T11:00:00Z", expires_at="2026-10-17T13:00:00Z")
    receipts = []
    for i, row in enumerate(rows, 1):
        receipts.append(dict(
            version="Phase1433ArtifactReceipt.v1", artifact_id=row["artifact_id"],
            request_sequence=i, request_fingerprint=request["fingerprint"],
            approval_fingerprint=sha(canonical(approval)), filename=row["filename"],
            source_url=row["source_url"], source_host="files.pythonhosted.org",
            expected_sha256=row["expected_sha256"], expected_bytes=row["expected_bytes"],
            actual_sha256=row["expected_sha256"], response_bytes=row["expected_bytes"],
            redirects=0, retries=0, method="GET", http_status=200, content_encoding="identity",
            complete=True, started_at="2026-10-17T12:00:00Z",
            completed_at="2026-10-17T12:00:01Z", elapsed_seconds=1,
        ))
    return dict(acquisition=acquisition, attestation=ledger, request=request, approval=approval,
                directory=directory, receipts=receipts)


def verify(fixture: dict[str, Any]) -> dict[str, Any]:
    receipts = [canonical(r) for r in fixture["receipts"]]
    return policy.verify_quarantine(
        fixture["acquisition"], fixture["attestation"], fixture["request"], fixture["approval"],
        {sha(canonical(fixture["approval"]))}, NOW, fixture["directory"], ROOT,
        receipts, {sha(r) for r in receipts},
    )


def test_synthetic_complete_and_no_installation(fixture: dict[str, Any]) -> None:
    result = verify(fixture)["manifest"]
    assert result["raw_hashes_verified"] == 8 and result["aggregate_bytes"] == 3029164
    assert result["installation_authorization"] == "NOT_AUTHORIZED"
    assert result["network_requests_by_verifier"] == 0 and not result["package_safety_verified"]


def test_pending_packet_does_not_authorize(fixture: dict[str, Any]) -> None:
    request = fixture["request"]["manifest"]
    assert request["decision"] == "PENDING" and not request["acquisition_authorized"]
    assert request["mode"] == "PREPARATION_ONLY"
    with pytest.raises(ValueError, match="OWNER_APPROVAL_ABSENT"):
        policy.authorize(fixture["acquisition"], fixture["attestation"], fixture["request"],
                         None, set(), NOW)


@pytest.mark.parametrize(("key", "value"), [
    ("acquisition_fingerprint", "0" * 64), ("attestation_fingerprint", "0" * 64),
    ("request_fingerprint", "0" * 64), ("expires_at", "2026-10-17T12:01:00Z"),
    ("source_hosts", ["pypi.org"]), ("artifacts", []), ("max_total_bytes", 3029165),
    ("redirects", True), ("retries", True), ("action", "RETRIEVE_METADATA_ONLY"),
    ("version", "unknown"), ("no_installation", False), ("owner_identity", None),
])
def test_owner_binding(fixture: dict[str, Any], key: str, value: Any) -> None:
    fixture["approval"][key] = value
    with pytest.raises(ValueError):
        verify(fixture)


def test_untrusted_owner(fixture: dict[str, Any]) -> None:
    with pytest.raises(ValueError, match="OWNER_EVIDENCE_UNTRUSTED"):
        policy.authorize(fixture["acquisition"], fixture["attestation"], fixture["request"],
                         fixture["approval"], set(), NOW)


@pytest.mark.parametrize(("key", "value"), [
    ("source_url", "https://files.pythonhosted.org/unapproved.whl"),
    ("source_host", "pypi.org"), ("redirects", 1), ("retries", 1),
    ("response_bytes", 3029165), ("response_bytes", 1), ("filename", "wrong.whl"),
    ("filename", "pydantic_core-2.46.5-cp311-cp311-win32.whl"),
    ("complete", False), ("elapsed_seconds", 21),
    ("completed_at", "2026-10-17T12:01:00Z"), ("actual_sha256", "0" * 64),
    ("approval_fingerprint", "0" * 64), ("request_fingerprint", "0" * 64),
    ("http_status", 302), ("content_encoding", "gzip"), ("version", "unknown"),
])
def test_receipt_failures(fixture: dict[str, Any], key: str, value: Any) -> None:
    fixture["receipts"][0][key] = value
    with pytest.raises(ValueError):
        verify(fixture)


def test_duplicate_artifact(fixture: dict[str, Any]) -> None:
    fixture["receipts"][1] = fixture["receipts"][0]
    with pytest.raises(ValueError, match="DUPLICATE"):
        verify(fixture)


def test_missing_receipt(fixture: dict[str, Any]) -> None:
    fixture["receipts"].pop()
    with pytest.raises(ValueError, match="INCOMPLETE_RECEIPTS"):
        verify(fixture)


@pytest.mark.parametrize("raw", [b"{", b'{"version":"a","version":"b"}', b"[]"])
def test_corrupt_receipt(raw: bytes) -> None:
    with pytest.raises(ValueError):
        policy.receipt(raw, {sha(raw)})


def test_changed_receipt_custody() -> None:
    raw = b'{"version":"Phase1433ArtifactReceipt.v1"}'
    with pytest.raises(ValueError, match="CUSTODY"):
        policy.receipt(raw + b" ", {sha(raw)})


@pytest.mark.parametrize("change", ["hash", "size", "partial", "missing", "unexpected"])
def test_raw_file_failures(fixture: dict[str, Any], change: str) -> None:
    directory = fixture["directory"]
    path = directory / fixture["receipts"][0]["filename"]
    if change == "hash":
        path.write_bytes(b"x" * path.stat().st_size)
    elif change == "size":
        path.write_bytes(b"small")
    elif change == "partial":
        path.rename(path.with_suffix(".part"))
    elif change == "missing":
        path.unlink()
    else:
        (directory / "unexpected.txt").write_bytes(b"unexpected")
    with pytest.raises(ValueError):
        verify(fixture)


@pytest.mark.parametrize("kind", ["relative", "traversal", "checkout", "existing"])
def test_quarantine_path(tmp_path: Path, kind: str) -> None:
    path = {"relative": Path("relative"), "traversal": tmp_path / ".." / "escape",
            "checkout": ROOT / "no-create-here", "existing": tmp_path}[kind]
    with pytest.raises(ValueError):
        policy.quarantine_path(path, ROOT, fresh=True)


def test_quarantine_link_detection(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    candidate = tmp_path / "not-created"
    original = Path.is_symlink
    monkeypatch.setattr(Path, "is_symlink", lambda p: p == candidate or original(p))
    with pytest.raises(ValueError, match="QUARANTINE_LINK"):
        policy.quarantine_path(candidate, ROOT, fresh=True)


def test_real_junction_escape(tmp_path: Path) -> None:
    # Windows NTFS junctions do not require symlink privileges. Test-only external paths.
    import subprocess
    target = tmp_path / "target"
    target.mkdir()
    link = tmp_path / "junction"
    result = subprocess.run(["cmd", "/c", "mklink", "/J", str(link), str(target)],
                            capture_output=True, check=False)
    assert result.returncode == 0, result.stderr.decode(errors="replace")
    try:
        with pytest.raises(ValueError, match="QUARANTINE_LINK"):
            policy.quarantine_path(link / "child", ROOT, fresh=True)
    finally:
        link.rmdir()


def test_wrong_wheel_platform(fixture: dict[str, Any]) -> None:
    body = fixture["acquisition"]["manifest"]
    row = body["artifacts"][2]
    row["filename"] = "pydantic_core-2.46.5-cp312-cp312-manylinux_2_17_x86_64.whl"
    body = deepcopy(body)
    fixture["acquisition"] = seal(body)
    with pytest.raises(ValueError, match="WRONG_PLATFORM"):
        verify(fixture)


def test_manifest_tamper(fixture: dict[str, Any]) -> None:
    fixture["acquisition"]["manifest"]["artifacts"][0]["expected_sha256"] = "0" * 64
    with pytest.raises(ValueError, match="MANIFEST_FINGERPRINT"):
        verify(fixture)


def test_attestation_tamper(fixture: dict[str, Any]) -> None:
    fixture["attestation"]["manifest"]["artifacts"][0]["yanked"] = True
    with pytest.raises(ValueError, match="ATTESTATION_BINDING"):
        verify(fixture)


def test_deterministic_replay(fixture: dict[str, Any]) -> None:
    first = verify(fixture)
    fixture["receipts"].reverse()
    assert verify(fixture) == first


def test_no_network_extraction_or_install_dependency() -> None:
    for node in ast.walk(ast.parse(Path(str(policy.__file__)).read_text())):
        names = []
        if isinstance(node, ast.Import):
            names = [a.name.split('.')[0] for a in node.names]
        elif isinstance(node, ast.ImportFrom):
            names = [(node.module or '').split('.')[0]]
        assert not set(names) & {"subprocess", "socket", "urllib", "http", "requests", "httpx",
                                 "pip", "zipfile", "tarfile", "importlib"}


def test_phase1422_preserved() -> None:
    assert sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes()) == (
        "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31"
    )
