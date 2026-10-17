"""Offline custody/lifecycle checks: no file deletion, recovery or API execution."""

import hashlib
from datetime import datetime, timedelta
from typing import Any

from verify_phase1435 import aware, digest, require


def custody(raw: bytes | None, record: dict[str, Any], trusted_reviews: set[str]) -> dict[str, Any]:
    require(record.get("version") == "Phase1437Custody.v1", "CUSTODY_VERSION")
    classification = "USER_SUPPLIED_RESPONSE_TEXT" if record.get("user_supplied") else "UNAVAILABLE"
    if raw is None:
        return {"classification": classification, "original": "ORIGINAL_CUSTODY_UNVERIFIED",
                "missing": ["original_raw_bytes"]}
    review = record.get("review")
    require(isinstance(review, dict) and digest(review) in trusted_reviews, "REVIEW_UNTRUSTED")
    assert isinstance(review, dict)
    require(review.get("raw_sha256") == hashlib.sha256(raw).hexdigest(), "RAW_CHANGED")
    require(review.get("body_sha256") == digest(record.get("exact_body"))
            and bool(record.get("exact_body")), "BODY_CHANGED")
    require(review.get("authenticity") == "VERIFIED", "AUTHENTICITY_UNVERIFIED")
    require(review.get("metadata_sha256") == digest(record.get("metadata")), "METADATA_CHANGED")
    metadata = record["metadata"]
    require(all(metadata.get(k) for k in ("sender", "recipient", "sent_at", "observation_receipt")),
            "METADATA_INCOMPLETE")
    aware(datetime.fromisoformat(metadata["sent_at"]))
    require(record.get("read_only_original") is True and bool(record.get("access_policy")),
            "CUSTODY_CONTROLS_MISSING")
    missing = [k for k in ("message_id", "ticket_reference") if not metadata.get(k)]
    return {"classification": "ORIGINAL_CORRESPONDENCE_VERIFIED",
            "original": "VERIFIED_WITH_TRUSTED_REVIEW", "missing": missing}


def lifecycle(policy: dict[str, Any], expected: str, acquired: datetime, now: datetime,
              operation: str) -> None:
    aware(acquired)
    aware(now)
    require(digest(policy) == expected, "POLICY_CHANGED")
    require(policy.get("version") == "Phase1437DataRetentionPolicy.v1", "POLICY_VERSION")
    require(operation in ("process", "persist", "backup", "recover"), "OPERATION")
    require(policy.get("status") == "AUTHORIZED" and bool(policy.get("authority_fingerprint")),
            "RETENTION_AUTHORITY_MISSING")
    lifetime = policy.get("maximum_lifetime_seconds")
    require(type(lifetime) is int and lifetime > 0, "RETENTION_DURATION_UNRESOLVED")
    assert isinstance(lifetime, int)
    require(all(policy.get(k) for k in ("source", "purpose", "location", "deletion_trigger",
                                      "deletion_authority", "backup_handling", "recovery_handling",
                                      "audit_policy", "policy_evidence")), "POLICY_INCOMPLETE")
    require(acquired <= now < acquired + timedelta(seconds=lifetime), "EXPIRED_OR_FUTURE_DATA")
    require(operation in policy.get("allowed_operations", []), "OPERATION_NOT_AUTHORIZED")


def deletion_receipts(
    policy: dict[str, Any], policy_hash: str, object_hash: str, copies: set[str],
    receipts: list[dict[str, Any]], trusted_receipts: set[str],
) -> None:
    """Attested completion only: this function cannot prove physical secure erasure."""
    require(digest(policy) == policy_hash, "POLICY_CHANGED")
    require(policy.get("status") == "AUTHORIZED" and bool(policy.get("deletion_authority")),
            "DELETION_AUTHORITY_MISSING")
    require(bool(copies), "COPY_INVENTORY_MISSING")
    seen: set[str] = set()
    ids: set[str] = set()
    for receipt in receipts:
        require(digest(receipt) in trusted_receipts, "RECEIPT_TAMPERED_OR_UNTRUSTED")
        require(receipt.get("version") == "Phase1437DeletionReceipt.v1", "RECEIPT_VERSION")
        require(receipt.get("policy") == policy_hash and receipt.get("object") == object_hash,
                "RECEIPT_BINDING")
        identity = receipt.get("id")
        copy = receipt.get("copy")
        require(isinstance(identity, str) and bool(identity) and identity not in ids, "DUPLICATE_RECEIPT")
        require(isinstance(copy, str) and copy in copies and copy not in seen, "COPY_MISMATCH")
        assert isinstance(identity, str) and isinstance(copy, str)
        require(receipt.get("status") == "COMPLETED" and bool(receipt.get("verification_evidence")),
                "DELETION_UNRESOLVED")
        aware(datetime.fromisoformat(receipt["completed_at"]))
        ids.add(identity)
        seen.add(copy)
    require(seen == copies, "BACKUP_OR_COPY_RECEIPT_MISSING")


def approval(packet: dict[str, Any], expected: str, operation: str,
             decision: dict[str, Any] | None, trusted_decisions: set[str], now: datetime) -> None:
    aware(now)
    versions = {"master": "Phase1437InstrumentMasterApproval.v1",
                "candles": "Phase1437HistoricalCandleApproval.v1"}
    require(operation in versions and packet.get("version") == versions[operation], "SCOPE_MISMATCH")
    require(digest(packet) == expected, "PACKET_CHANGED")
    require(decision is not None, "OWNER_APPROVAL_MISSING")
    assert decision is not None
    require(digest(decision) in trusted_decisions, "OWNER_EVIDENCE_UNTRUSTED")
    require(decision.get("status") == "APPROVED" and decision.get("packet") == expected
            and decision.get("operation") == operation, "OWNER_SCOPE_MISMATCH")
    issued = datetime.fromisoformat(decision["issued_at"])
    expiry = datetime.fromisoformat(decision["expires_at"])
    aware(issued)
    aware(expiry)
    require(issued <= now < expiry, "APPROVAL_EXPIRED")
    require(packet.get("Gate_A") == "AUTHORIZED", "GATE_A_CLOSED")
    for key in ("verified_compliance", "endpoint", "universe", "authentication_custody",
                "request_budget", "storage_scope", "retention_policy", "deletion_policy", "audit"):
        require(bool(packet.get(key)), "AUTHORIZATION_FIELD_MISSING")
    if operation == "candles":
        require(all(packet.get(k) for k in ("date_windows", "instrument_resolution",
                                           "calendar", "corporate_actions", "rate_budget")),
                "CANDLE_SCOPE_INCOMPLETE")
