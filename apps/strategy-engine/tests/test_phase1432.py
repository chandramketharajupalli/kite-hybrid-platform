"""Synthetic index records test matching only; they are never genuine attestations."""

import ast
import json
from copy import deepcopy
from dataclasses import replace
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import pytest

from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1431.artifacts import seal
from strategy_engine.phase1432.attestation import (
    ACQUISITION,
    LIMIT,
    MATCH,
    Evidence,
    authorize,
    decision,
    endpoint,
    evaluate,
    manifest,
    retrieval_request,
)

ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT / "research/phase-14.31/generation-1"
NOW = datetime(2026, 10, 17, 12, tzinfo=UTC)


def acquisition() -> dict[str, Any]:
    result: dict[str, Any] = json.loads((BASE / "artifact-acquisition-manifest.json").read_bytes())
    return result


def fixture() -> tuple[dict[str, Any], list[Evidence], dict[str, tuple[str, str]]]:
    envelope = acquisition()
    records = []
    for row in envelope["manifest"]["artifacts"]:
        raw = canonical(dict(
            info=dict(name=row["name"], version=row["version"]),
            urls=[dict(filename=row["filename"], url=row["source_url"],
                       digests=dict(sha256=row["expected_sha256"]), size=row["expected_bytes"],
                       packagetype="bdist_wheel", yanked=False,
                       upload_time_iso_8601="2026-10-16T12:00:00Z")],
        ))
        records.append(Evidence(row["artifact_id"], endpoint(row), NOW.isoformat(), raw, sha(raw)))
    custody = {r.raw_sha256: (r.source_url, r.observed_at) for r in records}
    return envelope, records, custody


def mutated(
    scope: str, key: str, value: Any,
) -> tuple[dict[str, Any], list[Evidence], dict[str, tuple[str, str]]]:
    envelope, evidence, custody = fixture()
    body = json.loads(evidence[0].raw)
    target = body["info"] if scope == "info" else body["urls"][0]
    target[key] = value
    raw = canonical(body)
    evidence[0] = replace(evidence[0], raw=raw, raw_sha256=sha(raw))
    custody[sha(raw)] = (evidence[0].source_url, evidence[0].observed_at)
    return envelope, evidence, custody


def packet_fixture() -> tuple[
    dict[str, Any], list[Evidence], dict[str, tuple[str, str]], dict[str, Any], dict[str, Any]
]:
    env, evidence, custody = fixture()
    ledger = evaluate(env, evidence, custody)
    destination = json.loads((BASE / "acquisition-authorization.json").read_bytes())["manifest"][
        "destination"
    ]
    request = decision(env, ledger, destination)
    approval = deepcopy(request["manifest"])
    approval.update(
        version="Phase1432OwnerApproval.v1", decision="APPROVED",
        request_fingerprint=request["fingerprint"], owner_decision_identity="SYNTHETIC_OWNER",
        issued_at="2026-10-17T11:00:00Z", expires_at="2026-10-17T13:00:00Z",
    )
    return env, evidence, custody, request, approval


def test_exact_frozen_eight() -> None:
    env = acquisition()
    assert env["fingerprint"] == ACQUISITION
    body = manifest(env)
    assert [(r["name"], r["version"]) for r in body["artifacts"]] == [
        ("annotated-types", "0.8.0"), ("pydantic", "2.13.5"), ("pydantic-core", "2.46.5"),
        ("pydantic-settings", "2.15.0"), ("python-dotenv", "1.2.3"),
        ("typing-extensions", "4.16.0"), ("typing-inspection", "0.4.4"), ("tzdata", "2026.4"),
    ]
    assert sum(r["expected_bytes"] for r in body["artifacts"]) == 3029164


def test_synthetic_matches_are_index_agreement_only() -> None:
    result = evaluate(*fixture())["manifest"]
    assert result["independent_digest_matches"] == 8
    assert all(r["classification"] == MATCH for r in result["artifacts"])
    assert not result["publisher_signature_verified"] and not result["archive_bytes_verified"]


def test_missing_metadata() -> None:
    result = evaluate(acquisition(), [], {})["manifest"]
    assert result["missing_metadata"] == 8 and not result["acquisition_eligible"]
    assert all(r["observation_timestamp"] is None for r in result["artifacts"])


@pytest.mark.parametrize(("scope", "key", "value", "status"), [
    ("file", "digests", {"sha256": "0" * 64}, "INDEPENDENT_METADATA_HASH_MISMATCH"),
    ("file", "size", 1, "INDEPENDENT_METADATA_HASH_MISMATCH"),
    ("file", "filename", "other-1.0-py3-none-any.whl", "ARTIFACT_NOT_FOUND"),
    ("info", "version", "0.0.1", "ARTIFACT_IDENTITY_AMBIGUOUS"),
    ("info", "name", "other", "ARTIFACT_IDENTITY_AMBIGUOUS"),
    ("file", "yanked", True, "ARTIFACT_YANKED"),
    ("file", "url", "https://example.invalid/wheel.whl", "ARTIFACT_IDENTITY_AMBIGUOUS"),
    ("file", "packagetype", "sdist", "ARTIFACT_IDENTITY_AMBIGUOUS"),
    ("file", "filename", "annotated_types-0.8.0-cp312-cp312-linux_x86_64.whl",
     "ARTIFACT_NOT_FOUND"),
])
def test_record_failures(scope: str, key: str, value: Any, status: str) -> None:
    result = evaluate(*mutated(scope, key, value))["manifest"]
    assert result["artifacts"][0]["classification"] == status
    assert not result["acquisition_eligible"]


@pytest.mark.parametrize(("key", "value"), [
    ("digests", {}), ("size", True), ("yanked", None),
    ("upload_time_iso_8601", "2027-01-01T00:00:00Z"),
])
def test_malformed_record_rejected(key: str, value: Any) -> None:
    with pytest.raises(ValueError):
        evaluate(*mutated("file", key, value))


def test_duplicate_file_is_ambiguous() -> None:
    env, records, custody = fixture()
    body = json.loads(records[0].raw)
    body["urls"] *= 2
    raw = canonical(body)
    records[0] = replace(records[0], raw=raw, raw_sha256=sha(raw))
    custody[sha(raw)] = (records[0].source_url, records[0].observed_at)
    assert evaluate(env, records, custody)["manifest"]["artifacts"][0]["classification"] == (
        "ARTIFACT_IDENTITY_AMBIGUOUS"
    )


@pytest.mark.parametrize("change", ["duplicate", "unknown", "altered", "schema", "oversize",
                                   "json_keys", "corrupt", "naive"])
def test_evidence_boundary(change: str) -> None:
    env, records, custody = fixture()
    if change == "duplicate":
        records[1] = records[0]
    elif change == "unknown":
        records[0] = replace(records[0], artifact_id="unknown")
    elif change == "altered":
        records[0] = replace(records[0], raw=records[0].raw + b" ")
    elif change == "schema":
        records[0] = replace(records[0], version="v999")
    elif change == "naive":
        records[0] = replace(records[0], observed_at="2026-10-17T00:00:00")
    else:
        raw = {"oversize": b"x" * (LIMIT + 1), "json_keys": b'{"info":{},"info":{}}',
               "corrupt": b"{"}[change]
        records[0] = replace(records[0], raw=raw, raw_sha256=sha(raw))
        custody[sha(raw)] = (records[0].source_url, records[0].observed_at)
    with pytest.raises(ValueError):
        evaluate(env, records, custody)


@pytest.mark.parametrize("change", ["no_custody", "wrong_host", "wrong_time", "wrong_endpoint"])
def test_untrusted_custody(change: str) -> None:
    env, records, custody = fixture()
    if change == "no_custody":
        custody = {}
    elif change == "wrong_time":
        custody[records[0].raw_sha256] = (records[0].source_url, "2020-01-01T00:00:00Z")
    else:
        records[0] = replace(records[0], source_url=(
            "https://pypi.org.evil.invalid/pypi/x/json" if change == "wrong_host"
            else "https://pypi.org/pypi/other/1/json"
        ))
        custody[records[0].raw_sha256] = (records[0].source_url, records[0].observed_at)
    result = evaluate(env, records, custody)["manifest"]
    assert result["artifacts"][0]["classification"] == "SOURCE_UNTRUSTED"


@pytest.mark.parametrize("change", ["fingerprint", "version", "tag", "budget"])
def test_sealed_manifest_change(change: str) -> None:
    env = acquisition()
    if change == "fingerprint":
        env["fingerprint"] = "0" * 64
    else:
        body = env["manifest"]
        if change == "version":
            body["version"] = "unknown"
        elif change == "tag":
            body["artifacts"][0]["tags"] = ["cp311-cp311-win32"]
        else:
            body["max_total_bytes"] += 1
        env = seal(body)
    with pytest.raises(ValueError):
        manifest(env)


def test_metadata_request_grants_no_permission() -> None:
    body = retrieval_request(acquisition())["manifest"]
    assert body["decision"] == "PENDING" and not body["network_authorized"]
    assert body["maximum_requests"] == len(body["endpoints"]) == 8
    assert body["maximum_total_bytes"] == 8 * LIMIT
    for key in ("redirects", "retries", "credentials", "distribution_downloads", "installation",
                "broker_access"):
        assert body[key] is False


def test_synthetic_acquisition_policy_does_not_install() -> None:
    env, records, custody, request, approval = packet_fixture()
    authorize(env, records, custody, request, approval, {sha(canonical(approval))}, NOW)
    assert request["manifest"]["installation_authorization"] == "NOT_AUTHORIZED"
    assert request["manifest"]["clean_installation"] == "NOT_ATTEMPTED"
    assert not request["manifest"]["automatic_download"]


@pytest.mark.parametrize("change", ["absent", "untrusted", "expired", "manifest", "allowlist",
                                   "budget", "metadata_only", "attestation", "host", "request"])
def test_approval_rejection(change: str) -> None:
    env, records, custody, request, approval = packet_fixture()
    if change == "expired":
        approval["expires_at"] = "2026-10-17T11:30:00Z"
    elif change == "manifest":
        approval["manifest_fingerprint"] = "0" * 64
    elif change == "allowlist":
        approval["artifact_allowlist"] = []
    elif change == "budget":
        approval["max_total_bytes"] += 1
    elif change == "metadata_only":
        approval["action"] = "RETRIEVE_METADATA_ONLY"
    elif change == "attestation":
        approval["attestation_fingerprint"] = "0" * 64
    elif change == "host":
        approval["source_hosts"] = ["example.invalid"]
    elif change == "request":
        request["manifest"]["max_total_bytes"] += 1
    trusted = set() if change == "untrusted" else {sha(canonical(approval))}
    with pytest.raises(ValueError):
        authorize(env, records, custody, request, None if change == "absent" else approval,
                  trusted, NOW)


def test_owner_approval_cannot_override_missing_attestation() -> None:
    env, _, _, request, approval = packet_fixture()
    request = decision(env, evaluate(env, [], {}), request["manifest"]["quarantine_destination"])
    approval.update(request_fingerprint=request["fingerprint"],
                    attestation_fingerprint=request["manifest"]["attestation_fingerprint"])
    with pytest.raises(ValueError, match="MANDATORY_ATTESTATION_MISSING"):
        authorize(env, [], {}, request, approval, {sha(canonical(approval))}, NOW)


def test_deterministic_reordering() -> None:
    env, records, custody = fixture()
    assert evaluate(env, records, custody) == evaluate(env, list(reversed(records)), custody)


def test_preservation_exception() -> None:
    assert sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes()) == (
        "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31"
    )


def test_no_network_or_install_client() -> None:
    from strategy_engine.phase1432 import attestation
    source = Path(str(attestation.__file__)).read_text()
    for node in ast.walk(ast.parse(source)):
        if isinstance(node, ast.Import):
            names = [r.name.split('.')[0] for r in node.names]
        elif isinstance(node, ast.ImportFrom):
            names = [(node.module or '').split('.')[0]]
        else:
            continue
        assert not set(names) & {"socket", "urllib", "requests", "httpx", "subprocess", "pip"}
