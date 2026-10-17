"""Pure artifact-policy checks and read-only dependency closure verification."""

import re
from datetime import datetime, timedelta
from pathlib import Path, PureWindowsPath
from typing import Any

from packaging.markers import Marker
from packaging.requirements import Requirement
from packaging.specifiers import SpecifierSet
from packaging.tags import Tag
from packaging.utils import canonicalize_name, parse_wheel_filename

from strategy_engine.phase1416.imports import canonical, sha

VERSION = "Phase1431ArtifactAcquisition.v1"
HOST = "files.pythonhosted.org"
LOCK_ONLY = "FROZEN_LOCK_CORRESPONDENCE_ONLY"


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def seal(body: dict[str, Any]) -> dict[str, Any]:
    return {"manifest": body, "fingerprint": sha(canonical(body))}


def source_host(url: str) -> str:
    prefix = "https://" + HOST + "/"
    require(url.startswith(prefix), "SOURCE_HOST")
    suffix = url[len(prefix) :]
    require(
        suffix.isascii()
        and not any(c in suffix for c in "\\%?#@:\r\n\t ")
        and all(p not in {"", ".", ".."} for p in suffix.split("/")),
        "SOURCE_PATH",
    )
    return HOST


def locked_closure(
    spec: dict[str, Any], lock: dict[str, Any], environment: dict[str, str]
) -> list[dict[str, Any]]:
    packages = {str(canonicalize_name(p["name"])): p for p in lock["package"]}
    require(len(packages) == len(lock["package"]), "AMBIGUOUS_LOCK_PACKAGE")
    require(
        environment["python_full_version"] in SpecifierSet(lock["requires-python"]),
        "LOCK_PYTHON_VERSION",
    )
    roots = []
    for raw in spec["project"]["dependencies"]:
        req = Requirement(raw)
        require(not req.extras and req.url is None, "UNSUPPORTED_RUNTIME_REQUIREMENT")
        if req.marker is None or req.marker.evaluate(environment):
            name = str(canonicalize_name(req.name))
            require(name in packages, "MISSING_LOCK_PACKAGE")
            require(packages[name]["version"] in req.specifier, "LOCK_VERSION_CONSTRAINT")
            roots.append(name)
    project = packages["strategy-engine"]
    project_roots = [
        p["name"]
        for p in project["dependencies"]
        if "marker" not in p or Marker(p["marker"]).evaluate(environment)
    ]
    require(set(roots) == set(project_roots), "PROJECT_LOCK_ROOTS")
    pending = list(roots)
    result = {}
    while pending:
        name = pending.pop()
        if name in result:
            continue
        require(name in packages, "MISSING_LOCK_PACKAGE")
        p = packages[name]
        children = []
        for edge in p.get("dependencies", []):
            require(not edge.get("extra"), "UNSUPPORTED_LOCK_EXTRA")
            if "marker" not in edge or Marker(edge["marker"]).evaluate(environment):
                child = str(canonicalize_name(edge["name"]))
                require(child in packages, "MISSING_LOCK_PACKAGE")
                require(
                    "version" not in edge or edge["version"] == packages[child]["version"],
                    "AMBIGUOUS_LOCK_EDGE",
                )
                children.append(child)
                pending.append(child)
        result[name] = dict(
            name=name, version=p["version"], direct=name in roots, children=sorted(children)
        )
    return [result[n] for n in sorted(result)]


def select_wheel(package: dict[str, Any], tags: set[Tag]) -> dict[str, Any]:
    candidates = []
    for entry in package.get("wheels", []):
        source_host(entry["url"])
        filename = entry["url"].rsplit("/", 1)[-1]
        name, version, _, wheel_tags = parse_wheel_filename(filename)
        require(
            name == canonicalize_name(package["name"]) and str(version) == package["version"],
            "WHEEL_IDENTITY",
        )
        if wheel_tags & tags:
            require(
                re.fullmatch(r"sha256:[0-9a-f]{64}", entry.get("hash", "")) is not None,
                "MISSING_DIGEST",
            )
            require(
                type(entry.get("size")) is int and 0 < entry["size"] <= 16 * 1024 * 1024,
                "ARTIFACT_SIZE",
            )
            candidates.append(
                dict(
                    filename=filename,
                    expected_sha256=entry["hash"][7:],
                    expected_bytes=entry["size"],
                    source_url=entry["url"],
                    tags=sorted(str(t) for t in wheel_tags),
                )
            )
    require(len(candidates) == 1, "AMBIGUOUS_OR_MISSING_COMPATIBLE_WHEEL")
    return candidates[0]


def validate_manifest(envelope: dict[str, Any], tags: set[Tag]) -> dict[str, Any]:
    body: dict[str, Any] = envelope["manifest"]
    require(envelope["fingerprint"] == sha(canonical(body)), "MANIFEST_FINGERPRINT")
    require(body["version"] == VERSION, "MANIFEST_VERSION")
    rows = body["artifacts"]
    require(0 < len(rows) <= 64, "ARTIFACT_COUNT")
    for key in ("name", "filename", "artifact_id"):
        require(len({r[key] for r in rows}) == len(rows), "DUPLICATE_ARTIFACT")
    total = 0
    for row in rows:
        require(re.fullmatch(r"[0-9a-f]{64}", row["expected_sha256"]) is not None, "MISSING_DIGEST")
        name, version, _, wheel_tags = parse_wheel_filename(row["filename"])
        require(name == row["name"] and str(version) == row["version"], "WHEEL_IDENTITY")
        require(bool(wheel_tags & tags), "WRONG_PLATFORM")
        require(source_host(row["source_url"]) == row["source_host"], "SOURCE_HOST")
        require(row["source_url"].rsplit("/", 1)[-1] == row["filename"], "SOURCE_FILENAME")
        require(type(row["expected_bytes"]) is int and row["expected_bytes"] > 0, "ARTIFACT_SIZE")
        total += row["expected_bytes"]
    require(
        body["max_artifacts"] == len(rows) and body["max_total_bytes"] == total, "DOWNLOAD_BUDGET"
    )
    require(total <= 64 * 1024 * 1024, "DOWNLOAD_BUDGET")
    require(body["source_hosts"] == [HOST], "SOURCE_ALLOWLIST")
    return body


def quarantine(destination: Path, checkout: Path) -> str:
    raw = PureWindowsPath(str(destination))
    require(
        raw.is_absolute() and not raw.drive.startswith("\\") and ".." not in raw.parts,
        "QUARANTINE_PATH",
    )
    for parent in (destination, *destination.parents):
        require(not parent.is_symlink() and not parent.is_junction(), "QUARANTINE_LINK")
    resolved = destination.resolve()
    root = checkout.resolve()
    require(resolved != root and root not in resolved.parents, "QUARANTINE_CHECKOUT")
    require(not destination.exists(), "QUARANTINE_NOT_FRESH")
    return resolved.as_posix()


def packet(envelope: dict[str, Any], destination: str) -> dict[str, Any]:
    body: dict[str, Any] = envelope["manifest"]
    return dict(
        version="Phase1431AcquisitionAuthorization.v1",
        decision="PENDING",
        manifest_fingerprint=envelope["fingerprint"],
        baseline=body["baseline"],
        artifact_allowlist=[r["artifact_id"] for r in body["artifacts"]],
        source_hosts=body["source_hosts"],
        destination=destination,
        max_artifacts=body["max_artifacts"],
        max_total_bytes=body["max_total_bytes"],
        action="ACQUIRE_ARTIFACTS_ONLY",
        no_upgrades=True,
        no_installation=True,
        redirects_allowed=False,
        alternate_sources_allowed=False,
        validity_scope="SINGLE_MANIFEST_SINGLE_BASELINE_SINGLE_ACQUISITION",
        maximum_approval_seconds=86400,
        issued_at=None,
        expires_at=None,
        owner_decision_evidence=None,
        download_authorized=False,
        installation_authorization="NOT_AUTHORIZED",
        installation_status="NOT_ATTEMPTED",
        digest_review="INDEPENDENT_EXPECTED_DIGEST_ATTESTATION_REQUIRED",
    )


def authorize(
    envelope: dict[str, Any],
    request: dict[str, Any],
    approval: dict[str, Any] | None,
    now: datetime,
    trusted_decisions: set[str],
    trusted_digests: set[str],
    tags: set[Tag],
) -> None:
    """Synthetic policy check only. Trusted evidence must come from separate owner review."""
    body = validate_manifest(envelope, tags)
    require(approval is not None, "OWNER_APPROVAL_ABSENT")
    assert approval is not None
    require(sha(canonical(approval)) in trusted_decisions, "OWNER_EVIDENCE_UNTRUSTED")
    require(approval.get("decision") == "APPROVED", "OWNER_NOT_APPROVED")
    require(approval.get("version") == "Phase1431OwnerDecision.v1", "APPROVAL_VERSION")
    require(
        request["manifest_fingerprint"] == envelope["fingerprint"]
        and approval.get("manifest_fingerprint") == envelope["fingerprint"],
        "WRONG_MANIFEST_APPROVAL",
    )
    require(request == packet(envelope, request["destination"]), "PACKET_BINDING")
    for key in (
        "baseline",
        "artifact_allowlist",
        "source_hosts",
        "destination",
        "max_artifacts",
        "max_total_bytes",
        "action",
        "no_upgrades",
        "no_installation",
    ):
        require(approval.get(key) == request[key], "APPROVAL_SCOPE:" + key)
    require(now.tzinfo is not None, "APPROVAL_CLOCK")
    issued = datetime.fromisoformat(approval["issued_at"])
    expiry = datetime.fromisoformat(approval["expires_at"])
    require(issued.tzinfo is not None and expiry.tzinfo is not None, "APPROVAL_CLOCK")
    require(
        issued <= now < expiry and timedelta(0) < expiry - issued <= timedelta(days=1),
        "APPROVAL_EXPIRED_OR_INVALID",
    )
    require(
        {r["expected_sha256"] for r in body["artifacts"]} <= trusted_digests,
        "UNTRUSTED_EXPECTED_DIGEST",
    )


def verify_raw(
    row: dict[str, Any], raw: bytes, artifact_allowlist: set[str], trusted_digests: set[str]
) -> str:
    require(row["artifact_id"] in artifact_allowlist, "ARTIFACT_NOT_ALLOWED")
    require(row["expected_sha256"] in trusted_digests, "UNTRUSTED_EXPECTED_DIGEST")
    require(len(raw) == row["expected_bytes"], "ARTIFACT_SIZE")
    require(sha(raw) == row["expected_sha256"], "ARCHIVE_HASH_MISMATCH")
    return "RAW_HASH_MATCH_ONLY_NOT_INSTALLATION_AUTHORITY"
