"""Read-only isolation preparation and synthetic-only denial policy."""

import hashlib
import json
import subprocess
import sys
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.44eb/generation-1"
BASELINE = "9511fc9e7d76433c1afce6a8d9f296867a520723"
BASELINE_FP = "1c025a9426305b04bd1168958c092994f853ecc7e3607004c9d08a8287492a2b"


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def canonical(value: object) -> bytes:
    return json.dumps(
        value, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode()


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def deny_real_execution() -> None:
    raise ValueError("EXECUTION_NOT_AUTHORIZED")


def inspect_fixture(value: dict[str, Any]) -> str:
    """Flags are synthetic scenario assumptions, never OS or approval attestations."""
    require(value["source"] == "TEST_ONLY", "SYNTHETIC_PROMOTION")
    require(value["provider"] == "TEST_ONLY_PROVIDER", "UNKNOWN_PROVIDER")
    require(value["approved_provider"], "UNAPPROVED_PROVIDER")
    require(value["image"] == value["expected_image"], "WRONG_IMAGE")
    require(value["image_provenance"], "MISSING_IMAGE_PROVENANCE")
    require(value["runtime"] == value["expected_runtime"], "WRONG_RUNTIME")
    require(value["closure_complete"], "INCOMPLETE_CLOSURE")
    require(value["source_hash"] == value["expected_source_hash"], "SOURCE_MISMATCH")
    require(
        value["assertions_hash"] == value["expected_assertions_hash"],
        "ASSERTION_CHANGE",
    )
    require(len(value["ids"]) == len(set(value["ids"])), "DUPLICATE_ID")
    require(set(value["ids"]) == set(value["expected_ids"]), "MISSING_ID")
    for key, reason in (
        ("guest_maps_host", "HOST_CUSTODY_MAPPING"),
        ("sealed_access", "HOST_SEALED_ACCESS"),
        ("repo_write", "REPOSITORY_WRITE"),
        ("credential_inheritance", "CREDENTIAL_INHERITANCE"),
        ("production_db", "PRODUCTION_DATABASE"),
        ("network_egress", "NETWORK_EGRESS"),
        ("dns_egress", "DNS_EGRESS"),
        ("nat_fallback", "NAT_FALLBACK"),
        ("child_escape", "CHILD_ESCAPE"),
    ):
        require(not value[key], reason)
    require(value["creation_time"] == value["expected_creation_time"], "PID_REUSE")
    require(
        value["owned_handle"] and value["parent_lifetime_valid"], "UNRELATED_PROCESS"
    )
    require(value["timeout_enforced"], "TIMEOUT_FAILURE")
    require(not value["guest_crashed"], "GUEST_CRASH")
    require(value["generation"] == value["external_generation"], "SNAPSHOT_ROLLBACK")
    require(value["export_approved"], "UNAUTHORIZED_EXPORT")
    require(value["owner_approval_fixture"], "MISSING_OWNER_APPROVAL")
    require(value["not_before"] <= value["now"] < value["expiry"], "EXPIRED_APPROVAL")
    require(not value["revoked"], "REVOKED_APPROVAL")
    require(value["action"] != "RUN_ORIGINAL31", "UNAUTHORIZED_ORIGINAL_TESTS")
    require(value["action"] == "INSPECT_FIXTURE", "UNAUTHORIZED_SERIES_V2")
    return "TEST_ONLY_POLICY_MATCH"


def read(path: Path) -> Any:
    return json.loads(path.read_bytes())


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", *args], cwd=ROOT)


def report() -> dict[str, Any]:
    require(git("rev-parse", "HEAD").decode().strip() == BASELINE, "HEAD")
    require(git("rev-parse", "origin/develop").decode().strip() == BASELINE, "ORIGIN")
    require(git("branch", "--show-current").strip() == b"develop", "BRANCH")
    require(
        not git("diff", "--name-only") and not git("diff", "--cached", "--name-only"),
        "GIT_EDIT",
    )
    baseline = {
        n: sha((ROOT / n).read_bytes())
        for n in git("ls-files", "-z").decode().strip("\0").split("\0")
    }
    require(
        len(baseline) == 1673 and sha(canonical(baseline)) == BASELINE_FP,
        "BASELINE_BYTES",
    )
    assessment = read(DIRECTORY / "isolation-assessment.json")
    envelope = read(DIRECTORY / "execution-approval.json")
    packet = envelope["packet"]
    Draft202012Validator.check_schema(envelope["schema"])
    Draft202012Validator(envelope["schema"]).validate(packet)
    require(sha(canonical(packet)) == envelope["packet_fingerprint"], "PACKET_HASH")
    require(
        sha(canonical(assessment)) == packet["assessment_fingerprint"],
        "ASSESSMENT_HASH",
    )
    require(
        packet["status"] == "NOT_AUTHORIZED" and packet["no_execution"], "PROMOTION"
    )
    require(packet["permitted_commands"] == [], "ACTIVE_COMMAND")
    require(packet["baseline"] == assessment["baseline"] == BASELINE, "PACKET_BASELINE")
    require(
        assessment["provider_status"] == "ISOLATION_PROVIDER_UNAVAILABLE",
        "PROVIDER_CLAIM",
    )
    require(
        not assessment["guest_provisioned"]
        and assessment["original_tests_executed"] == 0,
        "EXECUTION",
    )
    for field in (
        "isolation_provider_identity",
        "guest_image_fingerprint",
        "python_runtime_closure_fingerprint",
        "process_supervisor_identity",
        "network_containment_evidence",
        "credential_isolation_evidence",
        "owner_identity",
        "owner_authentication_evidence",
        "approval_timestamp",
        "expiry",
        "decision_id",
        "nonce",
        "revocation_receipt",
        "replay_receipt",
        "verification_receipt",
    ):
        require(packet[field] is None, "INVENTED_EVIDENCE:" + field)
    for name, expected in packet["source_test_hash_inventory"].items():
        require(sha((ROOT / name).read_bytes()) == expected, "SOURCE:" + name)
    require(
        packet["source_test_hash_inventory"] == assessment["source_hashes"],
        "SOURCE_BINDING",
    )
    previous = read(
        ROOT / "research/phase-14.44ea/generation-1/correction-approval.json"
    )["packet"]
    require(
        packet["test_ids"] == assessment["test_ids"] == previous["test_ids"], "TEST_IDS"
    )
    require(len(packet["test_ids"]) == len(set(packet["test_ids"])) == 31, "TEST_COUNT")
    require(not Path(packet["proposed_host_backing_root"]).exists(), "CUSTODY_CREATED")
    require(
        sha((ROOT / "docs/operations/phase-14.44eb-plan.md").read_bytes())
        == assessment["plan_sha256"],
        "PLAN_CHANGED",
    )
    require(
        sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes())
        == "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31",
        "PHASE1422",
    )
    policies = [ROOT / "docs/architecture/phase-14.44eb-isolation-boundary.md"] + [
        ROOT / ("docs/operations/phase-14.44eb-" + suffix + ".md")
        for suffix in (
            "plan",
            "provider-assessment",
            "guest-image",
            "runtime-closure",
            "containment-policy",
            "execution-authorization",
        )
    ]
    return {
        "version": "Phase1444EBReadinessReplay.v1",
        "classification": "PHASE_14_44EB_PARTIAL",
        "baseline": BASELINE,
        "baseline_hash_count": 1673,
        "baseline_fingerprint": BASELINE_FP,
        "assessment_fingerprint": sha(canonical(assessment)),
        "packet_fingerprint": envelope["packet_fingerprint"],
        "policy_hashes": {
            p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in policies
        },
        "verifier_sha256": sha(Path(__file__).read_bytes()),
        "test_source_sha256": sha(
            (ROOT / "scripts/research/test_phase1444eb.py").read_bytes()
        ),
        "provider": "ISOLATION_PROVIDER_UNAVAILABLE",
        "guest_image": "UNVERIFIED",
        "runtime_closure": "UNVERIFIED",
        "filesystem": "FILESYSTEM_CONTAINMENT_UNVERIFIED",
        "network": "NETWORK_CONTAINMENT_UNVERIFIED",
        "process": "UNVERIFIED",
        "credentials": "UNVERIFIED",
        "authorization": "NOT_AUTHORIZED",
        "guest_provisioned": False,
        "original_tests_executed": 0,
        "original31_reconciled": True,
        "historical_missing": 73,
        "real_kite_requests": 0,
        "genuine_qualified_pairs": 0,
        "Gate_A": "NOT_AUTHORIZED",
        "Gate_B": "NOT_AUTHORIZED",
        "Stage_B": "ZERO_CALL_ABORT",
        "Phase12": "BLOCK",
        "H1_H2": "FROZEN",
        "July": "SEALED",
        "operational": "NO_GO",
    }


def verify_manifest() -> None:
    envelope = read(DIRECTORY / "evidence-manifest.json")
    manifest = envelope["manifest"]
    require(sha(canonical(manifest)) == envelope["fingerprint"], "MANIFEST_HASH")
    for group in ("baseline_hashes", "artifacts", "predecessor_receipts"):
        for name, expected in manifest[group].items():
            require(sha((ROOT / name).read_bytes()) == expected, "HASH:" + name)
    for name in manifest["predecessor_manifests"]:
        path = ROOT / name
        prior = read(path)
        require(
            sha(canonical(prior["manifest"])) == prior["fingerprint"], "PRIOR_MANIFEST"
        )
        for group in ("baseline_hashes", "artifacts", "external_receipts", "receipts"):
            for item, expected in prior["manifest"].get(group, {}).items():
                base = path.parent if group == "receipts" else ROOT
                require(
                    sha((base / item).read_bytes()) == expected, "PRIOR_BYTES:" + item
                )
    require(
        sha(canonical(report())) == manifest["replay_fingerprint"], "REPLAY_CHANGED"
    )


if __name__ == "__main__":
    if "--verify" in sys.argv:
        verify_manifest()
    print(canonical(report()).decode())
