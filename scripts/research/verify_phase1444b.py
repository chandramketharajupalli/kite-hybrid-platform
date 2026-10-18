"""Deterministic partial trust-preparation evidence, never owner authentication."""

import json
import sys
from pathlib import Path
from typing import Any

from strategy_engine.phase1444b import trust as t

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.44b/generation-1"
POLICIES = (
    "docs/architecture/phase-14.44b-owner-trust-boundary.md",
    "docs/operations/phase-14.44b-plan.md",
    "docs/operations/phase-14.44b-enrollment-policy.md",
    "docs/operations/phase-14.44b-signature-policy.md",
    "docs/operations/phase-14.44b-time-and-revocation.md",
    "docs/operations/phase-14.44b-key-custody.md",
)


def report() -> dict[str, Any]:
    result = t.assessment()
    result.update(
        classification="PHASE_14_44B_PARTIAL",
        baseline=t.BASELINE,
        amendment_raw=t.AMENDMENT_RAW,
        amendment_canonical=t.AMENDMENT_CANONICAL,
        fresh_certificate=t.CERTIFICATE,
        policy_hashes={n: t.sha((ROOT / n).read_bytes()) for n in POLICIES},
        trust_model_fingerprint=t.sha(t.canonical(t.OwnerTrust().model_dump())),
        envelope_schema_fingerprint=t.sha(t.canonical(t.SignedDecision.model_json_schema())),
        source_sha256=t.sha(Path(t.__file__).read_bytes()),
        unresolved=[
            "ED25519_DEPENDENCY_AND_SIGNATURE_VALIDATION",
            "TRUSTED_TIME",
            "DURABLE_REPLAY_AND_INDEPENDENT_ANCHOR",
            "OS_KEY_PROVIDER",
            "INDEPENDENT_POLICY_REVIEW",
            "REAL_OWNER_ENROLLMENT",
        ],
    )
    return result


def verify() -> dict[str, Any]:
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    manifest = envelope["manifest"]
    t.require(t.sha(t.canonical(manifest)) == envelope["fingerprint"], "MANIFEST_HASH")
    for group in ("baseline_hashes", "artifacts", "external_receipts"):
        for path, expected in manifest[group].items():
            t.require(t.sha((ROOT / path).read_bytes()) == expected, "BYTES_CHANGED:" + path)
    stored = json.loads((DIRECTORY / "trust-assessment.json").read_bytes())
    result = report()
    t.require(
        t.canonical(result) == t.canonical(stored["assessment"])
        and t.sha(t.canonical(result)) == stored["fingerprint"],
        "REPORT_REPLAY",
    )
    t.require(
        t.sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes())
        == "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31",
        "PRESERVATION_EXCEPTION",
    )
    return {
        "manifest": envelope["fingerprint"],
        "report": stored["fingerprint"],
        "classification": "PHASE_14_44B_PARTIAL",
        "trust": "TRUST_NOT_ESTABLISHED",
        "adoption": "NOT_AUTHORIZED",
        "real_kite_requests": 0,
        "operational": "NO_GO",
    }


if __name__ == "__main__":
    print(t.canonical(verify() if "--verify" in sys.argv else report()).decode())
