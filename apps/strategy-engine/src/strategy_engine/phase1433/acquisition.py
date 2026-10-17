"""Fail-closed offline acquisition policy. Never fetch, extract, import or install wheels."""

import json
import stat
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1431.artifacts import require, seal, validate_manifest
from strategy_engine.phase1432.attestation import MATCH, TAGS, timestamp, unique_object

ACQUISITION = "74b40b6723fd6bfc5401a48e76fd8e94f2cd68919b5c532fa4b271c7f59db65c"
ATTESTATION = "4cd34fe05156683bde92483d246819ac63cccda5d2a8267bb422042d709f4a7f"
MAX_BYTES = 3029164


def bindings(acquisition: dict[str, Any], attestation: dict[str, Any]) -> dict[str, Any]:
    body = validate_manifest(acquisition, TAGS)
    require(acquisition["fingerprint"] == ACQUISITION, "ACQUISITION_BINDING")
    require(body["max_artifacts"] == 8 and body["max_total_bytes"] == MAX_BYTES, "BYTE_BUDGET")
    ledger = attestation["manifest"]
    require(sha(canonical(ledger)) == attestation["fingerprint"] == ATTESTATION,
            "ATTESTATION_BINDING")
    require(ledger["version"] == "Phase1432AttestationLedger.v1", "ATTESTATION_VERSION")
    require(ledger["acquisition_fingerprint"] == ACQUISITION, "ATTESTATION_ACQUISITION")
    require(ledger["independent_digest_matches"] == 8 and ledger["acquisition_eligible"],
            "ATTESTATION_INCOMPLETE")
    rows = ledger["artifacts"]
    require(len(rows) == 8 and len({r["artifact_id"] for r in rows}) == 8, "DUPLICATE_ATTESTATION")
    indexed = {r["artifact_id"]: r for r in rows}
    require(set(indexed) == {r["artifact_id"] for r in body["artifacts"]}, "ATTESTATION_ALLOWLIST")
    for artifact in body["artifacts"]:
        row = indexed[artifact["artifact_id"]]
        for key in ("filename", "expected_sha256", "expected_bytes"):
            require(row[key] == artifact[key], "ATTESTATION_IDENTITY:" + key)
        require(row["classification"] == MATCH and row["identity_match"] is True
                and row["digest_match"] is True and row["size_match"] is True
                and row["yanked"] is False, "ATTESTATION_NOT_ACCEPTABLE")
    return body


def quarantine_path(path: Path, checkout: Path, *, fresh: bool) -> str:
    require(path.is_absolute() and not str(path).startswith("\\\\") and ".." not in path.parts,
            "QUARANTINE_PATH")
    for parent in (path, *path.parents):
        require(not parent.is_symlink() and not parent.is_junction(), "QUARANTINE_LINK")
    resolved, root = path.resolve(), checkout.resolve()
    require(resolved != root and root not in resolved.parents, "QUARANTINE_CHECKOUT")
    if fresh:
        require(not path.exists(), "QUARANTINE_NOT_FRESH")
    else:
        require(path.is_dir(), "QUARANTINE_MISSING")
    return resolved.as_posix()


def packet(
    acquisition: dict[str, Any], attestation: dict[str, Any], destination: str
) -> dict[str, Any]:
    body = bindings(acquisition, attestation)
    return seal(dict(
        version="Phase1433ArtifactAcquisitionApproval.v1", decision="PENDING",
        acquisition_fingerprint=ACQUISITION, attestation_fingerprint=ATTESTATION,
        artifacts=[{k: r[k] for k in ("artifact_id", "name", "version", "filename", "tags",
                                     "expected_sha256", "expected_bytes", "source_url")}
                   for r in body["artifacts"]],
        source_hosts=["files.pythonhosted.org"], max_artifacts=8, max_requests=8,
        max_total_bytes=MAX_BYTES, quarantine_destination=destination,
        request_timeout_seconds=20, redirects=False, retries=False,
        content_encoding="identity", action="ACQUIRE_RAW_WHEELS_ONLY",
        no_overwrite=True, no_extraction=True, no_execution=True,
        no_installation=True, no_upgrades=True, no_credentials=True,
        maximum_validity_seconds=86400, issued_at=None, expires_at=None,
        owner_decision_evidence=None, owner_identity=None,
        acquisition_authorized=False, installation_authorization="NOT_AUTHORIZED",
        clean_installation="NOT_ATTEMPTED", mode="PREPARATION_ONLY",
    ))


def authorize(
    acquisition: dict[str, Any], attestation: dict[str, Any], request: dict[str, Any],
    approval: dict[str, Any] | None, trusted_owner_evidence: set[str], now: datetime,
) -> dict[str, Any]:
    """Evaluate an independently authenticated owner decision; perform no action."""
    expected = packet(acquisition, attestation, request["manifest"]["quarantine_destination"])
    require(request == expected, "REQUEST_CHANGED")
    require(approval is not None, "OWNER_APPROVAL_ABSENT")
    assert approval is not None
    require(sha(canonical(approval)) in trusted_owner_evidence, "OWNER_EVIDENCE_UNTRUSTED")
    require(approval.get("version") == "Phase1433OwnerDecision.v1", "OWNER_DECISION_VERSION")
    require(approval.get("decision") == "APPROVED", "OWNER_NOT_APPROVED")
    require(approval.get("request_fingerprint") == request["fingerprint"], "WRONG_REQUEST")
    for key in (
        "acquisition_fingerprint", "attestation_fingerprint", "artifacts", "source_hosts",
        "max_artifacts", "max_requests", "max_total_bytes", "quarantine_destination",
        "request_timeout_seconds", "redirects", "retries", "content_encoding", "action",
        "no_overwrite", "no_extraction", "no_execution", "no_installation", "no_upgrades",
        "no_credentials", "maximum_validity_seconds",
    ):
        require(approval.get(key) == request["manifest"][key], "APPROVAL_SCOPE:" + key)
    require(isinstance(approval.get("owner_identity"), str) and bool(approval["owner_identity"]),
            "OWNER_IDENTITY")
    issued, expiry = timestamp(approval["issued_at"]), timestamp(approval["expires_at"])
    require(now.tzinfo is not None and issued <= now < expiry
            and timedelta(0) < expiry - issued <= timedelta(days=1), "APPROVAL_EXPIRED")
    return approval


def receipt(raw: bytes, trusted_receipts: set[str]) -> dict[str, Any]:
    require(0 < len(raw) <= 65536, "RECEIPT_SIZE")
    require(sha(raw) in trusted_receipts, "RECEIPT_CUSTODY_UNTRUSTED")
    body = json.loads(raw, object_pairs_hook=unique_object)
    require(isinstance(body, dict), "RECEIPT_OBJECT")
    require(body.get("version") == "Phase1433ArtifactReceipt.v1", "RECEIPT_VERSION")
    result: dict[str, Any] = body
    return result


def verify_bytes(artifact: dict[str, Any], raw: bytes) -> str:
    require(len(raw) == artifact["expected_bytes"], "ARTIFACT_SIZE")
    require(sha(raw) == artifact["expected_sha256"], "ARCHIVE_HASH_MISMATCH")
    return "RAW_BYTES_MATCH_ATTESTED_RECORD"


def verify_quarantine(
    acquisition: dict[str, Any], attestation: dict[str, Any], request: dict[str, Any],
    approval: dict[str, Any] | None, trusted_owner_evidence: set[str], now: datetime,
    directory: Path, checkout: Path, receipts: list[bytes], trusted_receipts: set[str],
) -> dict[str, Any]:
    """Read explicitly supplied local evidence only. Receipt authenticity is external.

The directory must remain under exclusive approved custody throughout verification.
Link and stat checks are not a hostile concurrent-filesystem security sandbox.
"""
    owner = authorize(acquisition, attestation, request, approval, trusted_owner_evidence, now)
    require(quarantine_path(directory, checkout, fresh=False)
            == request["manifest"]["quarantine_destination"], "QUARANTINE_DESTINATION")
    body = bindings(acquisition, attestation)
    require(len(receipts) == 8, "INCOMPLETE_RECEIPTS")
    rows = [receipt(raw, trusted_receipts) for raw in receipts]
    require(len({r["artifact_id"] for r in rows}) == 8, "DUPLICATE_ARTIFACT_RECEIPT")
    indexed = {r["artifact_id"]: r for r in rows}
    require(set(indexed) == {r["artifact_id"] for r in body["artifacts"]}, "RECEIPT_ALLOWLIST")
    require(sorted(r["request_sequence"] for r in rows) == list(range(1, 9)), "REQUEST_COUNT")
    require(sum(r["response_bytes"] for r in rows) <= MAX_BYTES, "AGGREGATE_BYTE_BUDGET")
    entries = []
    for entry in directory.iterdir():
        entries.append(entry)
        require(len(entries) <= 8, "QUARANTINE_INVENTORY_BUDGET")
    require({p.name for p in entries} == {r["filename"] for r in body["artifacts"]},
            "QUARANTINE_INVENTORY")
    outcomes = []
    for artifact in body["artifacts"]:
        r = indexed[artifact["artifact_id"]]
        require(r["request_fingerprint"] == request["fingerprint"]
                and r["approval_fingerprint"] == sha(canonical(owner)), "RECEIPT_AUTHORITY")
        for key in ("filename", "source_url", "expected_sha256", "expected_bytes"):
            require(r[key] == artifact[key], "RECEIPT_IDENTITY:" + key)
        require(r["source_host"] == "files.pythonhosted.org", "RECEIPT_HOST")
        require(r["redirects"] == 0 and r["retries"] == 0, "REDIRECT_OR_RETRY")
        require(r["method"] == "GET" and r["http_status"] == 200
                and r["content_encoding"] == "identity", "RESPONSE_POLICY")
        require(r["complete"] is True, "PARTIAL_OR_INTERRUPTED_ACQUISITION")
        start, end = timestamp(r["started_at"]), timestamp(r["completed_at"])
        require(timestamp(owner["issued_at"]) <= start <= end <= now
                and end < timestamp(owner["expires_at"]), "RECEIPT_TIME")
        require(timedelta(0) <= end - start <= timedelta(seconds=20)
                and type(r["elapsed_seconds"]) in (int, float)
                and 0 <= r["elapsed_seconds"] <= 20, "REQUEST_TIMEOUT")
        require(type(r["response_bytes"]) is int
                and r["response_bytes"] == artifact["expected_bytes"], "RESPONSE_SIZE")
        path = directory / artifact["filename"]
        require(not path.is_symlink() and not path.is_junction(), "ARTIFACT_LINK")
        before = path.lstat()
        require(stat.S_ISREG(before.st_mode) and before.st_nlink == 1, "ARTIFACT_NOT_REGULAR")
        require(before.st_size == artifact["expected_bytes"], "ARTIFACT_SIZE")
        with path.open("rb") as stream:
            raw = stream.read(artifact["expected_bytes"] + 1)
        after = path.lstat()
        require(
            (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns)
            == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns), "ARTIFACT_CHANGED",
        )
        verify_bytes(artifact, raw)
        require(r["actual_sha256"] == sha(raw), "RECEIPT_ACTUAL_HASH")
        outcomes.append(dict(artifact_id=artifact["artifact_id"], filename=artifact["filename"],
                             sha256=sha(raw), bytes=len(raw), status="RAW_BYTES_VERIFIED"))
    return seal(dict(
        version="Phase1433AcquisitionVerification.v1", artifacts=outcomes,
        acquisition_fingerprint=ACQUISITION, attestation_fingerprint=ATTESTATION,
        request_fingerprint=request["fingerprint"], raw_hashes_verified=8,
        aggregate_bytes=sum(r["bytes"] for r in outcomes), completeness="COMPLETE",
        installation_authorization="NOT_AUTHORIZED", clean_installation="NOT_ATTEMPTED",
        recommendation="OFFLINE_RC_CONDITIONAL", package_safety_verified=False,
        network_requests_by_verifier=0,
    ))
