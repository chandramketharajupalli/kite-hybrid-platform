"""Exact artifact matching with explicit, independently supplied custody inputs.

Custody and owner evidence authentication are external responsibilities. These
pure policy checks do not authenticate a publisher or perform any authorized action.
"""

import json
import re
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Any

from packaging.tags import Tag
from packaging.utils import canonicalize_name, parse_wheel_filename

from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1431.artifacts import require, seal, validate_manifest

ACQUISITION = "74b40b6723fd6bfc5401a48e76fd8e94f2cd68919b5c532fa4b271c7f59db65c"
TAGS = {Tag("py3", "none", "any"), Tag("cp312", "cp312", "win_amd64")}
MATCH = "INDEPENDENT_METADATA_HASH_MATCH"
LIMIT = 2 * 1024 * 1024


@dataclass(frozen=True)
class Evidence:
    artifact_id: str
    source_url: str
    observed_at: str
    raw: bytes
    raw_sha256: str
    version: str = "Phase1432MetadataEvidence.v1"


def manifest(envelope: dict[str, Any]) -> dict[str, Any]:
    body = validate_manifest(envelope, TAGS)
    require(envelope["fingerprint"] == ACQUISITION, "SEALED_ACQUISITION_CHANGED")
    require(body["max_artifacts"] == 8 and body["max_total_bytes"] == 3029164, "BUDGET")
    return body


def endpoint(row: dict[str, Any]) -> str:
    return f"https://pypi.org/pypi/{row['name']}/{row['version']}/json"


def timestamp(value: str) -> datetime:
    result = datetime.fromisoformat(value)
    require(result.tzinfo is not None, "NAIVE_TIMESTAMP")
    return result


def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    obj: dict[str, Any] = {}
    for key, value in pairs:
        require(key not in obj, "DUPLICATE_JSON_KEY")
        obj[key] = value
    return obj


def evaluate(
    envelope: dict[str, Any],
    evidence: list[Evidence],
    custody: dict[str, tuple[str, str]],
) -> dict[str, Any]:
    """Custody maps reviewed raw hashes to exact endpoint and observation time.

Never populate custody from the evidence's own source labels. Empty custody
cannot produce a successful independent match. Synthetic custody is test-only.
"""
    body = manifest(envelope)
    require(len(evidence) <= 8, "EVIDENCE_COUNT")
    allowed = {r["artifact_id"] for r in body["artifacts"]}
    require(all(e.artifact_id in allowed for e in evidence), "EVIDENCE_ALLOWLIST")
    require(len({e.artifact_id for e in evidence}) == len(evidence), "DUPLICATE_EVIDENCE")
    supplied = {e.artifact_id: e for e in evidence}
    rows = []
    for artifact in body["artifacts"]:
        row = dict(
            artifact_id=artifact["artifact_id"],
            filename=artifact["filename"],
            expected_sha256=artifact["expected_sha256"],
            expected_bytes=artifact["expected_bytes"],
            lockfile_correspondence=True,
            independently_observed_sha256=None,
            metadata_source=None,
            source_url=None,
            retrieval_method=None,
            observation_timestamp=None,
            raw_metadata_sha256=None,
            identity_match=None,
            digest_match=None,
            size_match=None,
            yanked=None,
            upload_timestamp=None,
            classification="METADATA_UNAVAILABLE",
            reason="NO_REVIEWED_LOCAL_METADATA",
        )
        item = supplied.get(artifact["artifact_id"])
        if item is not None:
            row.update(compare(artifact, item, custody))
        rows.append(row)
    matches = sum(r["classification"] == MATCH for r in rows)
    return seal(dict(
        version="Phase1432AttestationLedger.v1",
        acquisition_fingerprint=ACQUISITION,
        artifacts=rows,
        independent_digest_matches=matches,
        missing_metadata=sum(r["classification"] == "METADATA_UNAVAILABLE" for r in rows),
        acquisition_eligible=matches == 8,
        publisher_signature_verified=False,
        archive_bytes_verified=False,
        network_requests=0,
    ))


def compare(
    artifact: dict[str, Any], item: Evidence, custody: dict[str, tuple[str, str]]
) -> dict[str, Any]:
    require(item.version == "Phase1432MetadataEvidence.v1", "EVIDENCE_VERSION")
    require(0 < len(item.raw) <= LIMIT, "METADATA_SIZE_BUDGET")
    require(sha(item.raw) == item.raw_sha256, "ALTERED_RAW_METADATA")
    timestamp(item.observed_at)
    result: dict[str, Any] = dict(
        source_url=item.source_url,
        observation_timestamp=item.observed_at,
        raw_metadata_sha256=item.raw_sha256,
        retrieval_method="EXPLICIT_LOCAL_EVIDENCE",
        classification="SOURCE_UNTRUSTED",
        reason="NO_INDEPENDENT_CUSTODY_OR_WRONG_ENDPOINT",
    )
    if (
        item.source_url != endpoint(artifact)
        or custody.get(item.raw_sha256) != (item.source_url, item.observed_at)
    ):
        return result
    data = json.loads(item.raw, object_pairs_hook=unique_object)
    require(isinstance(data, dict), "METADATA_OBJECT")
    info = data.get("info")
    urls = data.get("urls")
    require(isinstance(info, dict) and isinstance(urls, list), "PYPI_VERSION_JSON_SCHEMA")
    require(len(urls) <= 4096, "METADATA_RECORD_BUDGET")
    require(all(isinstance(r, dict) for r in urls), "ARTIFACT_RECORD_SCHEMA")
    result["metadata_source"] = "OFFICIAL_PYPI_INDEX_CUSTODY_REVIEWED"
    if info.get("name") is None or (
        canonicalize_name(str(info["name"])) != artifact["name"]
        or info.get("version") != artifact["version"]
    ):
        return result | dict(classification="ARTIFACT_IDENTITY_AMBIGUOUS", reason="NAME_VERSION")
    records = [r for r in urls if r.get("filename") == artifact["filename"]]
    if not records:
        return result | dict(classification="ARTIFACT_NOT_FOUND", reason="EXACT_FILENAME_ABSENT")
    if len(records) != 1:
        return result | dict(classification="ARTIFACT_IDENTITY_AMBIGUOUS", reason="DUPLICATE_FILE")
    record = records[0]
    name, version, _, tags = parse_wheel_filename(record["filename"])
    identity = (
        name == artifact["name"] and str(version) == artifact["version"]
        and sorted(str(t) for t in tags) == artifact["tags"] and bool(tags & TAGS)
        and record.get("packagetype") == "bdist_wheel"
        and record.get("url") == artifact["source_url"]
    )
    digests = record.get("digests")
    require(isinstance(digests, dict), "DIGEST_SCHEMA")
    observed = digests.get("sha256")
    require(isinstance(observed, str) and re.fullmatch(r"[0-9a-f]{64}", observed) is not None,
            "DIGEST_SCHEMA")
    require(type(record.get("size")) is int and record["size"] > 0, "SIZE_SCHEMA")
    require(type(record.get("yanked")) is bool, "YANKED_STATUS_REQUIRED")
    upload = record.get("upload_time_iso_8601")
    if upload is not None:
        require(isinstance(upload, str), "UPLOAD_TIMESTAMP")
        require(timestamp(upload) <= timestamp(item.observed_at), "OBSERVATION_PRECEDES_UPLOAD")
    result.update(
        identity_match=identity,
        independently_observed_sha256=observed,
        digest_match=observed == artifact["expected_sha256"],
        size_match=record["size"] == artifact["expected_bytes"],
        yanked=record["yanked"], upload_timestamp=upload,
    )
    if not identity:
        status, reason = "ARTIFACT_IDENTITY_AMBIGUOUS", "EXACT_URL_TAG_TYPE_IDENTITY"
    elif record["yanked"]:
        status, reason = "ARTIFACT_YANKED", "INDEX_YANKED"
    elif not result["digest_match"] or not result["size_match"]:
        status, reason = "INDEPENDENT_METADATA_HASH_MISMATCH", "DIGEST_OR_SIZE_MISMATCH"
    else:
        status, reason = MATCH, "EXACT_INDEX_RECORD_MATCH_ONLY"
    return result | dict(classification=status, reason=reason)


def retrieval_request(envelope: dict[str, Any]) -> dict[str, Any]:
    body = manifest(envelope)
    return seal(dict(
        version="Phase1432MetadataRetrievalRequest.v1",
        decision="PENDING", manifest_fingerprint=ACQUISITION,
        source_hosts=["pypi.org"],
        endpoints=[endpoint(r) for r in body["artifacts"]],
        method="GET", maximum_requests=8, maximum_response_bytes=LIMIT,
        maximum_total_bytes=8 * LIMIT, timeout_seconds=20,
        redirects=False, retries=False, credentials=False,
        distribution_downloads=False, installation=False, broker_access=False,
        owner_decision_evidence=None, network_authorized=False,
        validity_scope="ONE_EXACT_MANIFEST_EIGHT_ENDPOINTS_MAX_24_HOURS_AFTER_APPROVAL",
    ))


def decision(
    envelope: dict[str, Any], ledger: dict[str, Any], destination: str
) -> dict[str, Any]:
    body = manifest(envelope)
    require(sha(canonical(ledger["manifest"])) == ledger["fingerprint"], "LEDGER_FINGERPRINT")
    require(ledger["manifest"]["version"] == "Phase1432AttestationLedger.v1", "LEDGER_VERSION")
    require(ledger["manifest"]["acquisition_fingerprint"] == ACQUISITION, "LEDGER_BINDING")
    expected_destination = "D:/Yogendra/offline-artifact-quarantine/phase-14.31/" + ACQUISITION
    require(destination == expected_destination, "QUARANTINE_DESTINATION_BINDING")
    return seal(dict(
        version="Phase1432AcquisitionDecision.v1", decision="PENDING",
        manifest_fingerprint=ACQUISITION, attestation_fingerprint=ledger["fingerprint"],
        artifact_allowlist=[{k: r[k] for k in (
            "artifact_id", "filename", "source_url", "expected_sha256", "expected_bytes"
        )} for r in body["artifacts"]],
        source_hosts=body["source_hosts"], max_artifacts=8, max_total_bytes=3029164,
        quarantine_destination=destination, action="ACQUIRE_ARTIFACTS_ONLY",
        approval_eligible=ledger["manifest"]["acquisition_eligible"],
        source_allowlist_status="PROPOSED_NOT_OWNER_APPROVED",
        issued_at=None, expires_at=None, owner_decision_identity=None,
        approval_evidence_fingerprint=None, maximum_validity_seconds=86400,
        installation_authorization="NOT_AUTHORIZED", clean_installation="NOT_ATTEMPTED",
        recommendation="OFFLINE_RC_CONDITIONAL", automatic_download=False,
    ))


def authorize(
    envelope: dict[str, Any], evidence: list[Evidence], custody: dict[str, tuple[str, str]],
    request: dict[str, Any], approval: dict[str, Any] | None,
    trusted_owner_decisions: set[str], now: datetime,
) -> None:
    """Offline policy predicate only; never acquires or installs anything."""
    ledger = evaluate(envelope, evidence, custody)
    expected = decision(envelope, ledger, request["manifest"]["quarantine_destination"])
    require(request == expected, "DECISION_PACKET_CHANGED")
    require(approval is not None, "OWNER_APPROVAL_ABSENT")
    assert approval is not None
    require(sha(canonical(approval)) in trusted_owner_decisions, "OWNER_CUSTODY_UNTRUSTED")
    require(approval.get("version") == "Phase1432OwnerApproval.v1", "APPROVAL_VERSION")
    require(approval.get("decision") == "APPROVED", "OWNER_NOT_APPROVED")
    require(approval.get("request_fingerprint") == request["fingerprint"], "WRONG_REQUEST")
    for key in (
        "manifest_fingerprint", "attestation_fingerprint", "artifact_allowlist", "source_hosts",
        "max_artifacts", "max_total_bytes", "quarantine_destination", "action",
    ):
        require(approval.get(key) == request["manifest"][key], "APPROVAL_SCOPE:" + key)
    require(isinstance(approval.get("owner_decision_identity"), str)
            and bool(approval["owner_decision_identity"]), "OWNER_IDENTITY")
    issued, expiry = timestamp(approval["issued_at"]), timestamp(approval["expires_at"])
    require(now.tzinfo is not None and issued <= now < expiry
            and timedelta(0) < expiry - issued <= timedelta(days=1), "APPROVAL_EXPIRED")
    require(ledger["manifest"]["acquisition_eligible"], "MANDATORY_ATTESTATION_MISSING")
