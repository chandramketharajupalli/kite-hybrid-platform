"""Offline compliance evidence classification; never retrieves or authorizes API access."""

from datetime import datetime
from typing import Any

from verify_phase1435 import aware, digest, require


def classify(source: dict[str, Any], trusted_reviews: set[str]) -> str:
    require(source.get("version") == "Phase1436Source.v1", "SOURCE_VERSION")
    exact = source.get("exact_text")
    if not exact:
        return "SECONDARY_SUMMARY" if source.get("summary") else "UNAVAILABLE"
    review = source.get("review")
    if not isinstance(review, dict) or digest(review) not in trusted_reviews:
        return "USER_SUPPLIED_RESPONSE_TEXT"
    require(review.get("raw_sha256") == source.get("raw_sha256")
            and bool(source.get("raw_sha256")), "CUSTODY_HASH_MISMATCH")
    require(review.get("exact_text_sha256") == digest(exact), "BODY_BINDING")
    require(review.get("authenticity") == "VERIFIED", "AUTHENTICITY_UNVERIFIED")
    require(all(source.get(k) for k in ("sender", "recipient", "sent_at", "custody_reference")),
            "CUSTODY_METADATA_MISSING")
    aware(datetime.fromisoformat(source["sent_at"]))
    kind = review.get("kind")
    require(kind in ("ORIGINAL_CORRESPONDENCE_VERIFIED", "AUTHENTICATED_SUPPORT_RECORD"),
            "REVIEW_KIND")
    return str(kind)


def permission(classification: str, statements: list[str], retention_known: bool) -> str:
    require(all(s in ("PERMIT", "RESTRICT", "AMBIGUOUS") for s in statements), "CLAUSE_TYPE")
    if classification not in ("ORIGINAL_CORRESPONDENCE_VERIFIED", "AUTHENTICATED_SUPPORT_RECORD"):
        return "UNVERIFIED"
    if "RESTRICT" in statements:
        return "RESTRICTED"
    if not statements or "AMBIGUOUS" in statements:
        return "UNVERIFIED"
    return "EXPLICITLY_PERMITTED" if retention_known else "PERMITTED_WITH_CONDITIONS"


def validate_scope(
    packet: dict[str, Any], expected: str, operation: str,
    decision: dict[str, Any] | None, trusted_decisions: set[str], now: datetime,
) -> None:
    """Necessary offline checks only. Verified compliance must be independently supplied."""
    aware(now)
    require(digest(packet) == expected, "PACKET_CHANGED")
    require(packet.get("version") == "Phase1436GenuineDataAcquisitionApproval.v1", "VERSION")
    require(operation in ("instrument_master", "historical_candles"), "OPERATION")
    require(decision is not None, "APPROVAL_ABSENT")
    assert decision is not None
    require(digest(decision) in trusted_decisions, "OWNER_EVIDENCE_UNVERIFIED")
    require(decision.get("status") == "APPROVED" and decision.get("packet") == expected
            and decision.get("operation") == operation, "APPROVAL_SCOPE")
    issued = datetime.fromisoformat(decision["issued_at"])
    expiry = datetime.fromisoformat(decision["expires_at"])
    aware(issued)
    aware(expiry)
    require(issued <= now < expiry, "EXPIRED_OR_FUTURE_APPROVAL")
    scope = packet["scopes"][operation]
    require(scope.get("compliance_classification") in (
        "ORIGINAL_CORRESPONDENCE_VERIFIED", "AUTHENTICATED_SUPPORT_RECORD"), "COMPLIANCE_UNVERIFIED")
    require(scope.get("permission") == "EXPLICITLY_PERMITTED", "PERMISSION_UNRESOLVED")
    for key in ("compliance_fingerprint", "endpoints", "categories", "universe_sha256",
                "credential_custody", "storage_location", "retention_policy", "deletion_policy",
                "calendar_controls", "corporate_action_controls", "audit_policy"):
        require(bool(scope.get(key)), "MISSING_" + key.upper())
    for key in ("maximum_requests", "maximum_response_bytes", "maximum_total_bytes",
                "requests_per_second"):
        require(type(scope.get(key)) is int and scope[key] > 0, "BUDGET_UNRESOLVED")
    require(scope["maximum_total_bytes"] >= scope["maximum_response_bytes"], "BUDGET_INCONSISTENT")
    if operation == "historical_candles":
        require(bool(scope.get("date_windows")) and bool(scope.get("instrument_resolution")),
                "HISTORICAL_SCOPE_UNRESOLVED")
    require(packet.get("Gate_A") == "AUTHORIZED", "GATE_A_NOT_AUTHORIZED")
    require(packet.get("Gate_B") == "NOT_AUTHORIZED", "UNEXPECTED_GATE_B_SCOPE")
