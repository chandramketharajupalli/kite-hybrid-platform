"""Deterministic trust-infrastructure assessment; no real authority or data acquisition."""

import json
import sys
from pathlib import Path
from typing import Any

from strategy_engine.phase1444b.trust import (
    AMENDMENT_CANONICAL,
    AMENDMENT_RAW,
    CERTIFICATE,
    canonical,
    require,
    sha,
)
from strategy_engine.phase1444c import crypto, replay

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.44c/generation-1"
POLICIES = (
    "docs/architecture/phase-14.44c-trust-infrastructure.md",
    "docs/operations/phase-14.44c-plan.md",
    "docs/operations/phase-14.44c-dependency-assessment.md",
    "docs/operations/phase-14.44c-replay-policy.md",
    "docs/operations/phase-14.44c-trusted-time.md",
    "docs/operations/phase-14.44c-key-custody.md",
    "docs/operations/phase-14.44c-enrollment.md",
)


def report() -> dict[str, Any]:
    return {
        "version": "Phase1444CTrustReadiness.v1",
        "classification": "PHASE_14_44C_PARTIAL",
        "baseline": crypto.BASELINE,
        "crypto_provider": "CRYPTO_PROVIDER_NOT_AUTHORIZED",
        "asymmetric_tests": "NOT_RUN_PROVIDER_UNAVAILABLE",
        "trusted_time": "TRUSTED_TIME_UNVERIFIED",
        "ledger": replay.VERSION,
        "local_persistence": "SYNTHETIC_SQLITE_ONLY",
        "independent_rollback_anchor": "UNVERIFIED",
        "physical_power_loss": "NOT_TESTED",
        "OS_nonexportability": "UNVERIFIED",
        "actual_action_dispatch": "NOT_IMPLEMENTED",
        "enrollment": {
            "version": "Phase1444COwnerEnrollment.v1",
            "status": "NOT_ENROLLED",
            "owner_identity_reference": None,
            "identity_verification_method": None,
            "enrollment_authority": None,
            "trust_root_fingerprint": None,
            "public_key_fingerprint": None,
            "key_provider_identity": None,
            "permitted_roles_proposed_only": ["EVIDENCE_ADOPTION_OWNER"],
            "enrollment_timestamp": None,
            "expiry": None,
            "revocation_ledger": None,
            "rotation_procedure": "SEPARATE_AUTHENTICATED_DECISION_REQUIRED",
            "independent_approval_evidence": None,
        },
        "dependency_packet": {
            "version": "Phase1444CCryptoDependencyAuthorization.v1",
            "status": "PENDING",
            "selected_package": None,
            "version_pin": None,
            "license_evidence": None,
            "wheel_tags": None,
            "artifact_url": None,
            "original_archive_sha256": None,
            "metadata_attestation": None,
            "acquisition": "NOT_AUTHORIZED",
            "installation": "NOT_AUTHORIZED",
        },
        "verification_schema": crypto.VerificationRequest.model_json_schema(),
        "fixture_schema": crypto.SyntheticDecision.model_json_schema(),
        "ledger_schema_fingerprint": sha(canonical(replay.SCHEMA)),
        "policy_hashes": {n: sha((ROOT / n).read_bytes()) for n in POLICIES},
        "source_hashes": {
            "crypto": sha(Path(crypto.__file__).read_bytes()),
            "replay": sha(Path(replay.__file__).read_bytes()),
        },
        "amendment_raw": AMENDMENT_RAW,
        "amendment_canonical": AMENDMENT_CANONICAL,
        "fresh_certificate": CERTIFICATE,
        "historical_receipts": "HISTORICAL_RECEIPTS_UNAVAILABLE",
        "missing_receipts": 73,
        "adoption": "NOT_AUTHORIZED",
        "Series_v2": "PROPOSED_NOT_ADOPTED",
        "real_owner_authentication": "NOT_ESTABLISHED",
        "implementation": "NOT_AUTHORIZED",
        "rollback": "NOT_AUTHORIZED",
        "Gate_A": "NOT_AUTHORIZED",
        "Gate_B": "NOT_AUTHORIZED",
        "Stage_B": "ZERO_CALL_ABORT",
        "Phase12": "BLOCK",
        "H1_H2": "FROZEN",
        "July": "SEALED",
        "operational": "NO_GO",
        "genuine_qualified_pairs": 0,
        "real_kite_requests": 0,
    }


def verify() -> dict[str, Any]:
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    manifest = envelope["manifest"]
    require(sha(canonical(manifest)) == envelope["fingerprint"], "MANIFEST_HASH")
    for group in ("baseline_hashes", "artifacts", "external_receipts"):
        for path, expected in manifest[group].items():
            require(sha((ROOT / path).read_bytes()) == expected, "BYTES_CHANGED:" + path)
    stored = json.loads((DIRECTORY / "trust-readiness.json").read_bytes())
    result = report()
    require(
        canonical(result) == canonical(stored["readiness"])
        and sha(canonical(result)) == stored["fingerprint"],
        "REPORT_REPLAY",
    )
    require(
        sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes())
        == "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31",
        "EXCEPTION",
    )
    return {
        "classification": "PHASE_14_44C_PARTIAL",
        "manifest": envelope["fingerprint"],
        "readiness": stored["fingerprint"],
        "adoption": "NOT_AUTHORIZED",
        "crypto_provider": "CRYPTO_PROVIDER_NOT_AUTHORIZED",
        "real_kite_requests": 0,
        "operational": "NO_GO",
    }


if __name__ == "__main__":
    print(canonical(verify() if "--verify" in sys.argv else report()).decode())
