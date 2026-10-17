"""Pure offline readiness checks. No acquisition, retention or deletion side effects."""

import hashlib
import json
from dataclasses import dataclass
from datetime import datetime, timedelta
from decimal import Decimal
from typing import Any


def digest(value: Any) -> str:
    return hashlib.sha256(json.dumps(
        value, sort_keys=True, separators=(",", ":"), allow_nan=False,
    ).encode()).hexdigest()


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def aware(value: datetime) -> None:
    require(value.tzinfo is not None and value.utcoffset() is not None, "TIMEZONE_REQUIRED")


def compliance(record: dict[str, Any], trusted_sources: set[str]) -> None:
    """Trust is supplied by an independent custody review, never inferred here."""
    require(record.get("version") == "Phase1435Compliance.v1", "COMPLIANCE_VERSION")
    require(bool(record.get("source_sha256"))
            and record["source_sha256"] in trusted_sources, "SOURCE_UNVERIFIED")
    require(all(record.get(k) for k in ("exact_text", "sender", "message_time",
                                      "custody_reference", "authenticity_review")),
            "SOURCE_METADATA_INCOMPLETE")
    require(record.get("authenticity_review") == "VERIFIED", "SENDER_UNVERIFIED")
    require(record.get("conflicts") == [], "COMPLIANCE_CONFLICT")
    require(record.get("permission") == "EXPLICIT", "PERMISSION_UNRESOLVED")


def retention(policy: dict[str, Any], now: datetime, acquired: datetime) -> None:
    aware(now)
    aware(acquired)
    require(acquired <= now, "ACQUISITION_IN_FUTURE")
    require(policy.get("version") == "Phase1435Retention.v1", "RETENTION_VERSION")
    require(policy.get("authorized") is True and bool(policy.get("source_sha256")),
            "RETENTION_NOT_AUTHORIZED")
    duration = policy.get("maximum_seconds")
    require(type(duration) is int and duration > 0, "RETENTION_UNRESOLVED")
    assert isinstance(duration, int)
    require(all(policy.get(k) for k in ("category", "location", "deletion_trigger",
                                      "deletion_verification", "backup_policy",
                                      "derived_output_policy")), "RETENTION_INCOMPLETE")
    require(now < acquired + timedelta(seconds=duration), "DATASET_EXPIRED")


@dataclass(frozen=True)
class Mapping:
    exchange: str
    symbol: str
    token: int
    observed: datetime
    source_sha256: str


def mappings(
    rows: list[Mapping], symbols: tuple[str, ...], previous: dict[int, tuple[str, str]],
    now: datetime, maximum_age: timedelta, expected_source: str,
) -> None:
    aware(now)
    require(len(symbols) == len(set(symbols)) and bool(symbols), "UNIVERSE_INVALID")
    require(bool(expected_source) and maximum_age > timedelta(0), "MAPPING_POLICY")
    require(tuple(row.symbol for row in rows) == symbols, "MISSING_DUPLICATE_OR_REORDERED_SYMBOL")
    tokens: set[int] = set()
    for row in rows:
        aware(row.observed)
        require(row.exchange == "NSE", "EXCHANGE_MISMATCH")
        require(type(row.token) is int and row.token > 0, "TOKEN_INVALID")
        require(row.token not in tokens, "DUPLICATE_TOKEN")
        tokens.add(row.token)
        require(row.source_sha256 == expected_source, "MAPPING_SOURCE_CHANGED")
        require(timedelta(0) <= now - row.observed < maximum_age, "MAPPING_STALE")
        require(row.token not in previous
                or previous[row.token] == (row.exchange, row.symbol), "TOKEN_REUSED")


@dataclass(frozen=True)
class Candle:
    timestamp: datetime
    open: Decimal
    high: Decimal
    low: Decimal
    close: Decimal
    volume: Decimal


def completeness(
    candles: list[Candle], expected: tuple[datetime, ...], calendar_verified: bool,
    corporate_actions_verified: bool,
) -> dict[str, int]:
    """Expected slots must already account for listings, suspensions and special sessions."""
    require(calendar_verified and bool(expected), "CALENDAR_INCOMPLETE")
    require(corporate_actions_verified, "CORPORATE_ACTION_GAP")
    require(tuple(sorted(set(expected))) == expected, "CALENDAR_SLOTS_INVALID")
    for stamp in expected:
        aware(stamp)
    stamps = [row.timestamp for row in candles]
    for row in candles:
        aware(row.timestamp)
        require(all(v.is_finite() for v in (row.open, row.high, row.low, row.close, row.volume)),
                "NONFINITE_OHLCV")
        require(Decimal(0) < row.low <= min(row.open, row.close)
                <= max(row.open, row.close) <= row.high and row.volume >= 0, "INVALID_OHLCV")
    require(len(stamps) == len(set(stamps)), "DUPLICATE_CANDLE")
    require(stamps == sorted(stamps), "TIMESTAMP_ORDER")
    require(not set(stamps) - set(expected), "OUT_OF_SESSION_CANDLE")
    require(not set(expected) - set(stamps), "MISSING_CANDLE")
    return {"expected": len(expected), "observed": len(candles), "missing": 0, "duplicates": 0}


def acquisition(
    packet: dict[str, Any], frozen_fingerprint: str, decision: dict[str, Any] | None,
    trusted_decisions: set[str], now: datetime,
) -> None:
    """A matching approval is necessary but cannot replace compliance/governance review."""
    aware(now)
    require(digest(packet) == frozen_fingerprint, "PACKET_CHANGED")
    require(packet.get("version") == "Phase1435GenuineDataAcquisitionApproval.v1",
            "APPROVAL_VERSION")
    require(decision is not None, "ACQUISITION_NOT_AUTHORIZED")
    assert decision is not None
    require(digest(decision) in trusted_decisions, "OWNER_EVIDENCE_UNVERIFIED")
    require(decision.get("status") == "APPROVED"
            and decision.get("packet_fingerprint") == frozen_fingerprint, "APPROVAL_MISMATCH")
    require(packet.get("Gate_A") == "AUTHORIZED" and packet.get("Gate_B") == "AUTHORIZED",
            "GOVERNANCE_NOT_AUTHORIZED")
    require(all(packet.get(k) for k in ("compliance_sha256", "endpoints", "categories",
                                      "universe_sha256", "date_windows", "rate_limits",
                                      "credential_custody", "retention_sha256", "deletion_sha256",
                                      "calendar_sha256", "corporate_actions_sha256", "audit_policy")),
            "ACQUISITION_SCOPE_UNRESOLVED")
    issued = datetime.fromisoformat(decision["issued_at"])
    expiry = datetime.fromisoformat(decision["expires_at"])
    aware(issued)
    aware(expiry)
    require(issued <= now < expiry, "APPROVAL_EXPIRED_OR_FUTURE")


def readiness() -> dict[str, Any]:
    return {
        "version": "Phase1435Readiness.v1", "provenance": "OFFLINE_PREPARATION",
        "offline_rc": "OFFLINE_RC_CONDITIONAL", "compliance": "UNVERIFIED",
        "instrument_mapping": "BLOCKED", "historical_data": "BLOCKED",
        "corporate_actions": "BLOCKED", "retention": "UNRESOLVED",
        "acquisition": "NOT_AUTHORIZED", "qualification": "DATA_UNAVAILABLE",
        "qualified_pairs": 0, "genuine_performance": None, "operational": "NO_GO",
        "Gate_A": "NOT_AUTHORIZED", "Gate_B": "NOT_AUTHORIZED", "real_kite_requests": 0,
    }
