"""Preparation evidence and synthetic policy only; no process launch or authority."""

import datetime as dt
import hashlib
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-G/generation-1")
IMAGE = "C:/Windows/System32/conhost.exe"
IMAGE_SHA = "c86146188b78e484f32cc29e2331689659943b47689a85570342044f499938ac"
BASELINE = "0888e5265b300585ac866df4a84fafa57649278c"


def fingerprint(value: Any) -> str:
    return hashlib.sha256(
        json.dumps(
            value, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
        ).encode()
    ).hexdigest()


def classify_image(exited: bool, error: int | None, known: bool) -> str:
    if exited:
        return "PROCESS_EXITED"
    if error == 5:
        return "PROCESS_ACCESS_DENIED"
    if error is not None:
        return "PROCESS_IMAGE_QUERY_FAILED"
    return "PROCESS_IDENTITY_VERIFIED" if known else "PROCESS_IDENTITY_UNVERIFIED"


def synthetic_policy(e: dict[str, Any], now: dt.datetime) -> str:
    """No real authority verifier: acceptance exists only in disposable fixtures."""
    if e.get("context") != "TEST_ONLY" or not e.get("authorized"):
        return "REJECT_AUTHORIZATION"
    if e.get("approval_fingerprint") != e.get("expected_approval_fingerprint"):
        return "REJECT_APPROVAL_BINDING"
    if not e.get("approval_fingerprint"):
        return "REJECT_APPROVAL_BINDING"
    if e.get("path") != IMAGE or e.get("sha256") != IMAGE_SHA:
        return "REJECT_IMAGE"
    if e.get("signature") != "TEST_ONLY_VERIFIED" or not e.get("signature_evidence"):
        return "REJECT_SIGNATURE"
    try:
        start = dt.datetime.fromisoformat(e["not_before"])
        expiry = dt.datetime.fromisoformat(e["expiry"])
        if start.tzinfo is None or expiry.tzinfo is None or now.tzinfo is None:
            return "REJECT_TIME"
        if not start <= now < expiry:
            return "REJECT_TIME"
    except (KeyError, TypeError, ValueError):
        return "REJECT_TIME"
    if e.get("revoked"):
        return "REJECT_REVOKED"
    if not e.get("creation") or e.get("creation") != e.get("observed_creation"):
        return "REJECT_CREATION"
    if not e.get("pid") or e.get("pid") != e.get("handle_pid"):
        return "REJECT_PID"
    if e.get("parent_pid") != e.get("approved_root_pid"):
        return "REJECT_PARENT"
    if not e.get("parent_creation") or e.get("parent_creation") != e.get("root_creation"):
        return "REJECT_PARENT"
    if e["parent_creation"] > e["creation"]:
        return "REJECT_PARENT"
    if e.get("parent_exit") and e["creation"] > e["parent_exit"]:
        return "REJECT_PARENT"
    if not e.get("owned_job") or not e.get("job_member"):
        return "REJECT_OWNERSHIP"
    if e.get("job_handle") != e.get("expected_job_handle") or not e.get("job_handle"):
        return "REJECT_OWNERSHIP"
    if (
        classify_image(e.get("exited", False), e.get("image_error"), True)
        != "PROCESS_IDENTITY_VERIFIED"
    ):
        return "REJECT_IMAGE_STATE"
    return "TEST_ONLY_POLICY_ACCEPTED"


def git_ref(name: str) -> str:
    path = ROOT / ".git" / name
    if path.is_file():
        return path.read_text().strip()
    for line in (ROOT / ".git/packed-refs").read_text().splitlines():
        if line and not line.startswith(("#", "^")):
            value, ref = line.split(" ", 1)
            if ref == name:
                return value
    raise ValueError("MISSING_REF")


def verify() -> dict[str, Any]:
    if git_ref("HEAD") != "ref: refs/heads/develop":
        raise ValueError("BRANCH")
    if (
        git_ref("refs/heads/develop") != BASELINE
        or git_ref("refs/remotes/origin/develop") != BASELINE
    ):
        raise ValueError("BASELINE")
    binding = json.loads((CUSTODY / "replay-inputs.json").read_bytes())
    for name, expected in binding["hashes"].items():
        p = Path(name)
        if not p.is_absolute():
            p = ROOT / p
        if hashlib.sha256(p.read_bytes()).hexdigest() != expected:
            raise ValueError("EVIDENCE_CHANGED: " + name)
    baseline = json.loads((CUSTODY / "baseline-hashes.json").read_bytes())
    if len(baseline) != 1734:
        raise ValueError("BASELINE_COUNT")
    for name, expected in baseline.items():
        if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != expected:
            raise ValueError("PROTECTED_BYTES_CHANGED: " + name)
    approval = json.loads(
        (ROOT / "research/phase-14.44eg/generation-1/allowlist-approval.json").read_bytes()
    )
    if (
        approval["status"] != "NOT_AUTHORIZED"
        or approval["owner_authentication_evidence"] is not None
    ):
        raise ValueError("UNEXPECTED_AUTHORIZATION")
    return {
        "contract": "Phase1444EGPreparationReplay.v1",
        "baseline": BASELINE,
        "baseline_fingerprint": fingerprint(baseline),
        "binding_fingerprint": fingerprint(binding),
        "patch_fingerprint": approval["patch_fingerprint"],
        "classification": "PHASE_14_44EG_PARTIAL",
        "live_qualification": "NOT_AUTHORIZED",
        "remaining18": "NOT_EXECUTED",
        "original31": "NOT_EXECUTED",
        "historical_missing": 73,
        "historical_winerror5_31_causes": "UNVERIFIED",
        "real_kite_requests": 0,
        "supervised_replays": 0,
        "gate_a": "NOT_AUTHORIZED",
        "gate_b": "NOT_AUTHORIZED",
        "operational": "NO_GO",
    }


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True, separators=(",", ":"), allow_nan=False))
