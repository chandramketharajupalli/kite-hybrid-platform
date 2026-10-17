"""Allowlisted offline evidence inspection; no acquisition, extraction or publication."""

import hashlib
import stat
from email import policy
from email.parser import BytesParser
from pathlib import Path
from typing import Any

from verify_phase1435 import digest, require


def inspect_original(path: Path, approved: set[Path], expected_sha256: str,
                     seen: set[str], expected_sender: str | None = None) -> dict[str, Any]:
    require(path.is_absolute() and path in approved, "UNAPPROVED_CUSTODY_PATH")
    for ancestor in (path, *path.parents):
        require(not ancestor.is_symlink(), "SYMLINK_CUSTODY_PATH")
        require(not getattr(ancestor.stat(), "st_file_attributes", 0)
                & stat.FILE_ATTRIBUTE_REPARSE_POINT, "REPARSE_CUSTODY_PATH")
    require(path.suffix.lower() == ".eml" and path.is_file(), "UNSUPPORTED_SOURCE")
    require(0 < path.stat().st_size <= 2097152, "SOURCE_SIZE")
    with path.open("rb") as stream:
        raw = stream.read(2097153)
    require(len(raw) <= 2097152, "SOURCE_SIZE")
    fingerprint = hashlib.sha256(raw).hexdigest()
    require(fingerprint == expected_sha256, "ORIGINAL_BYTES_CHANGED")
    require(fingerprint not in seen, "DUPLICATE_CUSTODY_RECORD")
    message = BytesParser(policy=policy.default).parsebytes(raw)
    fields = {key: str(message[key]) if message[key] is not None else None
              for key in ("From", "To", "Date", "Message-ID", "X-Ticket-ID")}
    require(all(len(message.get_all(key, [])) <= 1 for key in fields), "AMBIGUOUS_HEADERS")
    if expected_sender is not None:
        require(fields["From"] == expected_sender, "SENDER_CLAIM_MISMATCH")
    attachments = []
    for part in message.walk():
        if part.get_content_disposition() == "attachment":
            payload = part.get_payload(decode=True)
            require(isinstance(payload, bytes), "ATTACHMENT_INVALID")
            assert isinstance(payload, bytes)
            attachments.append({"filename": part.get_filename(), "bytes": len(payload),
                                "sha256": hashlib.sha256(payload).hexdigest()})
    require(hashlib.sha256(path.read_bytes()).hexdigest() == fingerprint, "SOURCE_CHANGED_DURING_READ")
    return {"version": "Phase1438CustodyInspection.v1", "source_type": "EML",
            "raw_sha256": fingerprint, "bytes": len(raw), "headers": fields,
            "missing_headers": [k for k, value in fields.items() if value is None],
            "attachments": attachments, "transport_header_count": len(message.get_all("Received", [])),
            "parser_defects": [type(defect).__name__ for defect in message.defects],
            "sender_authenticity": "UNVERIFIED", "contractual_authority": "UNVERIFIED",
            "source_path": str(path), "acquisition_timestamp": None,
            "observation_receipt": None, "custody_restrictions": "PRIVATE_DERIVATIVE_ONLY"}


def compare_answers(original: dict[int, str] | None, supplied: dict[int, str] | None) -> list[dict[str, Any]]:
    for source in (original, supplied):
        if source is not None:
            require(set(source) == set(range(1, 15)), "INCOMPLETE_FOURTEEN_ANSWERS")
            require(all(type(k) is int and isinstance(v, str) and bool(v) for k, v in source.items()),
                    "ANSWER_IDENTITY")
    return [{"answer": n, "comparison": "UNVERIFIED" if original is None or supplied is None
             else "EXACT_MATCH" if original[n] == supplied[n] else "DIFFERENT",
             "original_text_fingerprint": None if original is None else digest(original[n]),
             "supplied_text_fingerprint": None if supplied is None else digest(supplied[n])}
            for n in range(1, 15)]


def retention_ready(record: dict[str, Any], expected: str) -> None:
    require(digest(record) == expected, "RETENTION_CHANGED")
    require(record.get("version") == "Phase1438RetentionDecision.v1", "RETENTION_VERSION")
    require(record.get("source_authenticity") in (
        "ORIGINAL_CORRESPONDENCE_VERIFIED", "AUTHENTICATED_SUPPORT_RECORD"), "SOURCE_UNVERIFIED")
    require(record.get("permission") == "EXPLICITLY_PERMITTED"
            and record.get("conflicts") == [], "RETENTION_PERMISSION_UNRESOLVED")
    require(type(record.get("duration_seconds")) is int and record["duration_seconds"] > 0,
            "DURATION_UNRESOLVED")
    require(all(record.get(k) for k in ("exact_source", "purpose", "location", "deletion_trigger",
                                      "backup_treatment", "derived_output_handling")),
            "RETENTION_SCOPE_INCOMPLETE")


def approval(packet: dict[str, Any], expected: str, kind: str,
             decision: dict[str, Any] | None, trusted: set[str]) -> None:
    require(digest(packet) == expected, "APPROVAL_FINGERPRINT")
    versions = {"master": "Phase1438InstrumentMasterApproval.v1",
                "candles": "Phase1438HistoricalCandleApproval.v1"}
    require(kind in versions and packet.get("version") == versions[kind], "APPROVAL_SCOPE")
    require(decision is not None, "OWNER_APPROVAL_ABSENT")
    assert decision is not None
    require(digest(decision) in trusted and decision.get("packet") == expected
            and decision.get("kind") == kind and decision.get("status") == "APPROVED",
            "OWNER_APPROVAL_INVALID")
    # This preparatory validator cannot activate execution even with a matching owner record.
    require(packet.get("execution_enabled") is False, "EXECUTION_OUT_OF_SCOPE")
    require(packet.get("status") == "NOT_AUTHORIZED", "PREPARATION_STATE_CHANGED")
