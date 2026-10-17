"""Offline installer metadata and approval checks; no installer execution or network."""

import hashlib
import json
from datetime import datetime, timedelta
from typing import Any
from urllib.parse import urlsplit

ENDPOINT = "https://pypi.org/pypi/pip/25.0.1/json"
FILENAME = "pip-25.0.1-py3-none-any.whl"
SHA256 = "c46efd13b6aa8279f33f2864459c8ce587ea6a1a59ee20de055868d8f7688f7f"
SIZE = 1841526


def digest(value: Any) -> str:
    return hashlib.sha256(json.dumps(
        value, sort_keys=True, separators=(",", ":"), allow_nan=False,
    ).encode()).hexdigest()


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def unique(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        require(key not in result, "DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def attest(raw: bytes | None, source: str, trusted_raw_hashes: set[str]) -> dict[str, Any]:
    """Trusted hashes must originate in authenticated, approved retrieval custody."""
    if raw is None:
        return {"version": "Phase1434AInstallerAttestation.v1",
                "classification": "METADATA_UNAVAILABLE", "installer_sha256": SHA256}
    require(source == ENDPOINT, "SOURCE_UNTRUSTED")
    require(0 < len(raw) <= 2097152, "METADATA_SIZE")
    raw_hash = hashlib.sha256(raw).hexdigest()
    require(raw_hash in trusted_raw_hashes, "RAW_EVIDENCE_UNTRUSTED")
    body = json.loads(raw, object_pairs_hook=unique)
    require(body["info"]["name"] == "pip" and body["info"]["version"] == "25.0.1",
            "DISTRIBUTION_IDENTITY")
    rows = [row for row in body["urls"] if row.get("filename") == FILENAME]
    require(len(rows) == 1, "MISSING_OR_AMBIGUOUS_ARTIFACT")
    row = rows[0]
    require(row["packagetype"] == "bdist_wheel" and row["python_version"] == "py3",
            "ARTIFACT_TYPE")
    url = urlsplit(row["url"])
    require(url.scheme == "https" and url.hostname == "files.pythonhosted.org"
            and url.port in (None, 443) and not url.username and not url.password
            and not url.query and not url.fragment and url.path.endswith("/" + FILENAME),
            "ARTIFACT_URL")
    require(row["yanked"] is False, "ARTIFACT_YANKED")
    require(row["digests"]["sha256"] == SHA256, "HASH_MISMATCH")
    require(row["size"] == SIZE, "SIZE_MISMATCH")
    return {
        "version": "Phase1434AInstallerAttestation.v1",
        "classification": "INDEPENDENT_METADATA_HASH_MATCH", "installer_sha256": SHA256,
        "bytes": SIZE, "filename": FILENAME, "metadata_sha256": raw_hash,
        "source": source, "artifact_url": row["url"],
        "upload_time": row.get("upload_time_iso_8601"),
        "publisher_authentication": "NOT_ESTABLISHED", "package_safety": "NOT_ESTABLISHED",
    }


def approval(
    packet: dict[str, Any], expected_packet: str, decision: dict[str, Any] | None,
    trusted_decisions: set[str], attestation: dict[str, Any],
    trusted_attestations: set[str], now: datetime,
) -> None:
    """Validate only; never create an environment, install or execute code."""
    require(packet.get("fingerprint") == digest(packet["manifest"]) == expected_packet,
            "PACKET_CHANGED")
    body = packet["manifest"]
    require(body.get("version") == "Phase1434OfflineInstallationApproval.v1"
            and body.get("status") == "NOT_AUTHORIZED", "PACKET_SCHEMA")
    require(decision is not None, "INSTALLATION_APPROVAL_ABSENT")
    assert decision is not None
    require(digest(decision) in trusted_decisions, "OWNER_EVIDENCE_UNTRUSTED")
    require(decision.get("version") == "Phase1434AOwnerInstallationDecision.v1"
            and decision.get("status") == "APPROVED", "WRONG_APPROVAL_TYPE")
    require(decision.get("packet_fingerprint") == expected_packet
            and decision.get("scope") == body, "APPROVAL_SCOPE")
    require(digest(attestation) in trusted_attestations
            and decision.get("attestation_fingerprint") == digest(attestation),
            "ATTESTATION_UNTRUSTED")
    require(attestation.get("version") == "Phase1434AInstallerAttestation.v1"
            and attestation.get("classification") == "INDEPENDENT_METADATA_HASH_MATCH"
            and attestation.get("installer_sha256") == body["installer"]["sha256"] == SHA256
            and attestation.get("source") == ENDPOINT, "PROVENANCE_INCOMPLETE")
    issued, expiry = (datetime.fromisoformat(decision[k]) for k in ("issued_at", "expires_at"))
    require(issued.tzinfo is not None and expiry.tzinfo is not None and now.tzinfo is not None,
            "TIMEZONE")
    require(issued <= now < expiry and timedelta(0) < expiry - issued <= timedelta(days=1),
            "EXPIRED_OR_INVALID_TIME")
