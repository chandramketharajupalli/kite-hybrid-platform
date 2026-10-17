"""Offline retry approval validation only; no directory creation or acquisition."""

import hashlib
import json
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

RETRY = "cee292c92d84a9868e55dd70a1d2e678021e0cf43d42e38e5ada3eeb110c57ee"


def digest(value: Any) -> str:
    return hashlib.sha256(json.dumps(
        value, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode()).hexdigest()


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def validate(
    packet: dict[str, Any], decision: dict[str, Any] | None,
    trusted_decision_hashes: set[str], now: datetime,
) -> None:
    """Trust hashes must come from separately authenticated owner evidence.

    Success validates a decision, never performs or authorizes installation.
    This does not replace destination, ACL, transport or receipt validation.
    """
    body = packet["manifest"]
    require(digest(body) == packet["fingerprint"] == RETRY, "PACKET_CHANGED")
    require(body["decision"] == "PENDING", "PACKET_STATE")
    require(decision is not None, "OWNER_APPROVAL_ABSENT")
    assert decision is not None
    require(digest(decision) in trusted_decision_hashes, "OWNER_EVIDENCE_UNTRUSTED")
    require(decision.get("version") == "Phase1433BRetryOwnerDecision.v1", "VERSION")
    require(decision.get("decision") == "APPROVED", "NOT_APPROVED")
    require(decision.get("retry_packet_fingerprint") == RETRY, "WRONG_PACKET")
    require(decision.get("scope") == body, "SCOPE_MISMATCH")
    require(decision.get("installation_authorization") == "NOT_AUTHORIZED", "INSTALLATION")
    require(isinstance(decision.get("owner_identity"), str)
            and bool(decision["owner_identity"]), "OWNER_IDENTITY")
    require(isinstance(decision.get("owner_evidence_sha256"), str)
            and len(decision["owner_evidence_sha256"]) == 64
            and all(x in "0123456789abcdef" for x in decision["owner_evidence_sha256"]),
            "OWNER_EVIDENCE")
    issued = datetime.fromisoformat(decision["issued_at"])
    expiry = datetime.fromisoformat(decision["expires_at"])
    require(issued.tzinfo is not None and expiry.tzinfo is not None
            and now.tzinfo is not None, "TIMEZONE")
    require(issued <= now < expiry and timedelta(0) < expiry - issued <= timedelta(days=1),
            "EXPIRED_OR_INVALID_TIME")


def certify(
    packet: dict[str, Any], decision: dict[str, Any], trusted_decisions: set[str],
    now: datetime, receipts: list[bytes], trusted_receipts: set[str],
    acl_evidence: dict[str, Any], trusted_acl_hashes: set[str],
) -> dict[str, Any]:
    """Verify explicit local custody evidence without opening any wheel members."""
    validate(packet, decision, trusted_decisions, now)
    body = packet["manifest"]
    require(digest(acl_evidence) in trusted_acl_hashes, "ACL_EVIDENCE_UNTRUSTED")
    require(acl_evidence.get("retry_packet_fingerprint") == RETRY
            and acl_evidence.get("decision_fingerprint") == digest(decision)
            and acl_evidence.get("procedure_sha256") == body["acl_procedure_sha256"],
            "ACL_BINDING")
    for key in ("quarantine_destination", "receipt_custody_destination"):
        require(acl_evidence.get(key) == body[key], "ACL_DESTINATION")
        path = Path(body[key])
        require(path.is_absolute() and path.is_dir(), "CUSTODY_MISSING")
        for ancestor in (path, *path.parents):
            require(not ancestor.is_symlink() and not ancestor.is_junction(), "CUSTODY_LINK")
    require(all(acl_evidence.get(k) is True for k in (
        "owner_group_unchanged", "protected", "exact_approved_rules", "no_sacl_write",
    )), "ACL_INCOMPLETE")
    directory = Path(body["quarantine_destination"])
    artifacts = body["artifacts"]
    names = {a["filename"] for a in artifacts}
    require(len(names) == len(artifacts) == body["max_artifacts"] == len(receipts), "COUNT")
    require({p.name for p in directory.iterdir()} == names, "INVENTORY")
    rows = {}
    for raw in receipts:
        require(hashlib.sha256(raw).hexdigest() in trusted_receipts, "RECEIPT_UNTRUSTED")
        row = json.loads(raw)
        require(isinstance(row, dict) and row.get("version") == "Phase1433BArtifactReceipt.v1",
                "RECEIPT_VERSION")
        name = row["filename"]
        require(name in names and name not in rows, "DUPLICATE_OR_UNKNOWN")
        rows[name] = row
    total = 0
    results = []
    for index, artifact in enumerate(artifacts, 1):
        row = rows[artifact["filename"]]
        require(row.get("retry_packet_fingerprint") == RETRY
                and row.get("decision_fingerprint") == digest(decision), "RECEIPT_AUTHORITY")
        require(row.get("source_url") == artifact["source_url"]
                and urlsplit(row["source_url"]).scheme == "https"
                and urlsplit(row["source_url"]).hostname in body["source_hosts"], "SOURCE")
        require(row.get("ordinal") == index and index <= body["max_requests"], "REQUEST_LIMIT")
        require(row.get("redirects") == 0 and row.get("retries") == 0
                and row.get("status") == 200 and row.get("complete") is True
                and row.get("content_encoding") == "identity", "TRANSPORT")
        start = datetime.fromisoformat(row["started_at"])
        end = datetime.fromisoformat(row["finished_at"])
        require(start.tzinfo is not None and end.tzinfo is not None, "RECEIPT_TIMEZONE")
        require(datetime.fromisoformat(decision["issued_at"]) <= start <= end <= now
                and end < datetime.fromisoformat(decision["expires_at"]), "RECEIPT_TIME")
        require(0 <= row["elapsed_seconds"] <= body["request_timeout_seconds"]
                and (end - start).total_seconds() <= body["request_timeout_seconds"], "TIMEOUT")
        path = directory / artifact["filename"]
        require(path.is_file() and not path.is_symlink() and not path.is_junction(), "FILE_KIND")
        before = path.stat()
        require(before.st_nlink == 1 and before.st_size == artifact["expected_bytes"], "SIZE")
        raw = path.read_bytes()
        after = path.stat()
        require((before.st_ino, before.st_size, before.st_mtime_ns)
                == (after.st_ino, after.st_size, after.st_mtime_ns), "FILE_CHANGED")
        sha = hashlib.sha256(raw).hexdigest()
        require(sha == artifact["expected_sha256"] == row.get("raw_sha256")
                and len(raw) == row.get("bytes") == artifact["expected_bytes"], "RAW_HASH_SIZE")
        total += len(raw)
        require(total <= body["max_total_bytes"], "BUDGET")
        results.append({"filename": artifact["filename"], "sha256": sha, "bytes": len(raw)})
    return {
        "version": "Phase1433BQuarantineCertification.v1", "artifacts": results,
        "aggregate_bytes": total, "retry_packet_fingerprint": RETRY,
        "decision_fingerprint": digest(decision), "acl_evidence_fingerprint": digest(acl_evidence),
        "receipt_hashes": sorted(hashlib.sha256(raw).hexdigest() for raw in receipts),
        "installation_authorization": "NOT_AUTHORIZED",
        "publisher_authentication": "NOT_ESTABLISHED", "package_safety": "NOT_ESTABLISHED",
    }
