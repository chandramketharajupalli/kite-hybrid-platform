"""Synthetic metadata and authority fixtures; no network or installer execution."""

import copy
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import pytest
from verify_phase1434a import ENDPOINT, FILENAME, SHA256, SIZE, approval, attest, digest


def metadata() -> dict[str, Any]:
    return {"info": {"name": "pip", "version": "25.0.1"}, "urls": [{
        "filename": FILENAME, "packagetype": "bdist_wheel", "python_version": "py3",
        "url": "https://files.pythonhosted.org/packages/SYNTHETIC/" + FILENAME,
        "yanked": False, "digests": {"sha256": SHA256}, "size": SIZE,
    }]}


def evaluate(v: dict[str, Any], tmp_path: Path) -> dict[str, Any]:
    path = tmp_path / "synthetic.json"
    path.write_bytes(json.dumps(v).encode())
    raw = path.read_bytes()
    return attest(raw, ENDPOINT, {hashlib.sha256(raw).hexdigest()})


def test_match_replay(tmp_path: Path) -> None:
    a = evaluate(metadata(), tmp_path)
    assert a == evaluate(metadata(), tmp_path)
    assert a["publisher_authentication"] == "NOT_ESTABLISHED"


def test_unavailable() -> None:
    assert attest(None, ENDPOINT, set())["classification"] == "METADATA_UNAVAILABLE"


@pytest.mark.parametrize("field,value", [
    ("filename", "pip-25.0.2-py3-none-any.whl"), ("size", SIZE + 1),
    ("digests", {"sha256": "0" * 64}), ("yanked", True),
    ("url", "https://untrusted.invalid/" + FILENAME), ("python_version", "cp311"),
    ("packagetype", "sdist"),
])
def test_metadata_reject(tmp_path: Path, field: str, value: Any) -> None:
    v = metadata()
    v["urls"][0][field] = value
    with pytest.raises(ValueError):
        evaluate(v, tmp_path)


@pytest.mark.parametrize("change", ["duplicate", "missing", "version", "name"])
def test_identity(tmp_path: Path, change: str) -> None:
    v = metadata()
    if change == "duplicate":
        v["urls"].append(copy.deepcopy(v["urls"][0]))
    elif change == "missing":
        v["urls"] = []
    else:
        v["info"][change] = "wrong"
    with pytest.raises(ValueError):
        evaluate(v, tmp_path)


def test_untrusted_raw() -> None:
    with pytest.raises(ValueError, match="RAW_EVIDENCE_UNTRUSTED"):
        attest(json.dumps(metadata()).encode(), ENDPOINT, set())


def test_untrusted_source() -> None:
    with pytest.raises(ValueError, match="SOURCE_UNTRUSTED"):
        attest(b"{}", "https://untrusted.invalid", set())


def test_duplicate_json() -> None:
    raw = b'{"info":{},"info":{}}'
    with pytest.raises(ValueError, match="DUPLICATE_JSON_KEY"):
        attest(raw, ENDPOINT, {hashlib.sha256(raw).hexdigest()})


@pytest.mark.parametrize("change", [
    "valid", "absent", "expired", "wrong_packet", "destination", "installer",
    "metadata_only", "untrusted_owner", "untrusted_attestation", "missing_provenance",
])
def test_approval(tmp_path: Path, change: str) -> None:
    attestation = evaluate(metadata(), tmp_path)
    body: dict[str, Any] = {
            "version": "Phase1434OfflineInstallationApproval.v1", "status": "NOT_AUTHORIZED",
            "installer": {"sha256": SHA256}, "destination": str(tmp_path / "never-created")}
    packet: dict[str, Any] = {"manifest": body, "fingerprint": digest(body)}
    decision: dict[str, Any] = {
                "version": "Phase1434AOwnerInstallationDecision.v1", "status": "APPROVED",
                "packet_fingerprint": packet["fingerprint"], "scope": copy.deepcopy(body),
                "attestation_fingerprint": digest(attestation),
                "issued_at": "2026-10-17T10:00:00+00:00",
                "expires_at": "2026-10-17T12:00:00+00:00"}
    if change == "expired":
        decision["expires_at"] = "2026-10-17T10:30:00+00:00"
    elif change == "wrong_packet":
        decision["packet_fingerprint"] = "0" * 64
    elif change in ("destination", "installer"):
        decision["scope"][change] = "wrong"
    elif change == "metadata_only":
        decision["version"] = "MetadataApproval.v1"
    elif change == "missing_provenance":
        attestation = attest(None, ENDPOINT, set())
        decision["attestation_fingerprint"] = digest(attestation)
    owners = set() if change == "untrusted_owner" else {digest(decision)}
    attestations = set() if change == "untrusted_attestation" else {digest(attestation)}
    args = (packet, packet["fingerprint"], None if change == "absent" else decision,
            owners, attestation, attestations, datetime(2026, 10, 17, 11, tzinfo=timezone.utc))
    if change == "valid":
        approval(*args)
    else:
        with pytest.raises(ValueError):
            approval(*args)
    assert not Path(body["destination"]).exists()
