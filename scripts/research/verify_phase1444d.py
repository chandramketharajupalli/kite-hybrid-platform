"""Read-only deterministic offline trust-policy assessment."""

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
from strategy_engine.phase1444d import policy

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.44d/generation-1"
POLICIES = (
    "docs/architecture/phase-14.44d-cryptographic-trust-boundary.md",
    "docs/operations/phase-14.44d-plan.md",
    "docs/operations/phase-14.44d-provider-assessment.md",
    "docs/operations/phase-14.44d-dependency-authorization.md",
    "docs/operations/phase-14.44d-trusted-time.md",
    "docs/operations/phase-14.44d-ledger-rollback.md",
    "docs/operations/phase-14.44d-key-custody.md",
)


def report() -> dict[str, Any]:
    return {
        "version": "Phase1444DTrustReadiness.v1",
        "classification": "PHASE_14_44D_PARTIAL",
        "baseline": policy.BASELINE,
        "provider": "CRYPTO_PROVIDER_NOT_AUTHORIZED",
        "time": "TRUSTED_TIME_UNVERIFIED",
        "checkpoint": "LEDGER_ROLLBACK_RESISTANCE_UNVERIFIED",
        "hardware_key_custody": "UNVERIFIED",
        "owner_enrollment": "NOT_PERFORMED",
        "owner_authentication": "NOT_ESTABLISHED",
        "metadata_retrieval": "NOT_AUTHORIZED",
        "download": "NOT_AUTHORIZED",
        "installation": "NOT_AUTHORIZED",
        "adoption": "NOT_AUTHORIZED",
        "implementation": "NOT_AUTHORIZED",
        "rollback_execution": "NOT_AUTHORIZED",
        "approval_timestamp": None,
        "approval_expiry": None,
        "proposed_validity_hours": 168,
        "fixture_schemas": {
            c.__name__: c.model_json_schema()
            for c in (
                policy.ArtifactFixture,
                policy.TimeFixture,
                policy.CheckpointFixture,
            )
        },
        "policy_hashes": {n: sha((ROOT / n).read_bytes()) for n in POLICIES},
        "policy_source_sha256": sha(Path(policy.__file__).read_bytes()),
        "packets": {
            n: json.loads((DIRECTORY / n).read_bytes())
            for n in ("provider-assessment.json", "dependency-authorization.json")
        },
        "amendment_raw": AMENDMENT_RAW,
        "amendment_canonical": AMENDMENT_CANONICAL,
        "fresh_certificate": CERTIFICATE,
        "historical_receipts": "HISTORICAL_RECEIPTS_UNAVAILABLE",
        "historical_missing": 73,
        "C_database_regressions": "NOT_RUN_SEALED_CUSTODY_CONFLICT",
        "full_suite": "NOT_ADOPTED",
        "Series_v2": "PROPOSED_NOT_ADOPTED",
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
    require(sha(canonical(manifest)) == envelope["fingerprint"], "MANIFEST_CHANGED")
    for group in ("baseline_hashes", "artifacts", "external_receipts"):
        for name, expected in manifest[group].items():
            require(sha((ROOT / name).read_bytes()) == expected, "HASH_CHANGED:" + name)
    stored = json.loads((DIRECTORY / "trust-readiness.json").read_bytes())
    require(canonical(report()) == canonical(stored["readiness"]), "REPLAY_MISMATCH")
    require(sha(canonical(report())) == stored["fingerprint"], "REPORT_HASH")
    require(
        sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes())
        == "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31",
        "UNTRACKED_EXCEPTION_CHANGED",
    )
    return {
        "classification": "PHASE_14_44D_PARTIAL",
        "manifest": envelope["fingerprint"],
        "readiness": stored["fingerprint"],
        "real_owner_authentication": "NOT_ESTABLISHED",
        "adoption": "NOT_AUTHORIZED",
        "operational": "NO_GO",
    }


if __name__ == "__main__":
    print(canonical(verify() if "--verify" in sys.argv else report()).decode())
