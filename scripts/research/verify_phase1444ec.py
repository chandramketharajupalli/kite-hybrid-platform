"""Read-only diagnostic evidence verification; no guest or ledger execution."""

import hashlib
import json
import subprocess
import sys
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "research/phase-14.44ec/generation-1"
CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-C/generation-1")
BASELINE = "85e05c9a9d3f69a64256c5b8cc70c8f582be6e1d"
BASELINE_FP = "1d9f286b8deb599a448cb622d4ead61621f0d22d756a2f364bbee070340e6d0c"


def canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def read(path: Path) -> Any:
    return json.loads(path.read_bytes())


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def classify_current(traceback: str, fresh_exit: int, control_traceback: str) -> str:
    signature = "FileNotFoundError: [WinError 3]"
    if signature in traceback and signature in control_traceback and fresh_exit == 0:
        if "NUL\\\\3.12" in traceback or "NUL\\3.12" in traceback:
            return "MYPY_TOOL_INTERNAL_DEFECT"
    return "MYPY_ROOT_CAUSE_UNVERIFIED"


def validate_packet(packet: dict[str, Any], schema: dict[str, Any]) -> None:
    Draft202012Validator.check_schema(schema)
    Draft202012Validator(schema).validate(packet)
    require(packet["status"] == "NOT_AUTHORIZED", "AUTHORITY_PROMOTION")
    require(packet["permitted_execution_operations"] == [], "EXECUTION_PERMISSION")
    for field in (
        "owner_identity",
        "owner_authentication_evidence",
        "decision_timestamp",
        "expiry",
        "approval_fingerprint",
        "provider_identity",
        "python_runtime_closure",
    ):
        require(packet[field] is None, "INVENTED_AUTHORITY")
    require(len(packet["test_ids"]) == len(set(packet["test_ids"])) == 31, "TEST_IDS")
    require(packet["baseline"] == BASELINE, "BASELINE")


def report() -> dict[str, Any]:
    def git(*args: str) -> bytes:
        return subprocess.check_output(["git", *args], cwd=ROOT)

    require(git("rev-parse", "HEAD").decode().strip() == BASELINE, "HEAD")
    require(git("rev-parse", "origin/develop").decode().strip() == BASELINE, "ORIGIN")
    require(git("branch", "--show-current").strip() == b"develop", "BRANCH")
    require(not git("diff", "--name-only") and not git("diff", "--cached", "--name-only"), "EDIT")
    baseline = {
        n: sha((ROOT / n).read_bytes())
        for n in git("ls-files", "-z").decode().strip("\0").split("\0")
    }
    require(len(baseline) == 1686 and sha(canonical(baseline)) == BASELINE_FP, "BASELINE_HASHES")
    predecessor = read(ROOT / "research/phase-14.44eb/generation-1/evidence-manifest.json")
    require(
        sha(canonical(predecessor["manifest"]))
        == "64f670d3730f62384d55902601dd67dc746baeffc6c1bace4ce7c9704a90a448",
        "PREDECESSOR",
    )
    prior = predecessor["manifest"]
    for group in ("artifacts", "predecessor_receipts"):
        for name, expected in prior[group].items():
            require(sha((ROOT / name).read_bytes()) == expected, "PREDECESSOR_BYTES:" + name)
    diagnosis = read(DATA / "mypy-assessment.json")
    require(
        sha((ROOT / "docs/operations/phase-14.44ec-plan.md").read_bytes())
        == diagnosis["plan_sha256"],
        "PLAN",
    )
    fresh = read(CUSTODY / "fresh.command.json")
    trace = (CUSTODY / "traceback.stdout").read_text()
    control = (CUSTODY / "nul-config.stdout").read_text()
    require(
        classify_current(trace, fresh["exit_code"], control) == diagnosis["current_root_cause"],
        "CAUSE",
    )
    require("2 source files" in (CUSTODY / "fresh.stdout").read_text(), "FRESH_RESULT")
    require(diagnosis["historical_receipt"] == prior["validation_receipts"]["mypy"], "HISTORY")
    require(diagnosis["historical_traceback"] is None, "INVENTED_HISTORY")
    require(
        prior["replay_fingerprint"] is None and prior["replays"]["fingerprints"] == [],
        "OLD_REPLAYS",
    )
    envelope = read(DATA / "environment-approval.json")
    packet = envelope["packet"]
    validate_packet(packet, envelope["schema"])
    require(sha(canonical(packet)) == envelope["packet_fingerprint"], "PACKET")
    old_packet = read(ROOT / "research/phase-14.44eb/generation-1/execution-approval.json")[
        "packet"
    ]
    require(packet["test_ids"] == old_packet["test_ids"], "TEST_SCOPE")
    require(
        packet["source_test_hashes"] == old_packet["source_test_hash_inventory"], "SOURCE_SCOPE"
    )
    for name, expected in packet["source_test_hashes"].items():
        require(sha((ROOT / name).read_bytes()) == expected, "SOURCE_BYTES")
    historical = read(ROOT / "research/phase-14.43/generation-1/evidence-manifest.json")[
        "manifest"
    ]["external_receipts"]
    require(
        len(historical) == 73 and all(not Path(n).exists() for n in historical),
        "HISTORICAL_RECEIPTS",
    )
    require(
        sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes())
        == "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31",
        "PHASE1422",
    )
    assessment = read(DATA / "isolation-assessment.json")
    require(
        not assessment["guest_provisioned"] and assessment["original31_executed"] == 0, "EXECUTION"
    )
    return {
        "version": "Phase1444ECDiagnosticReplay.v1",
        "classification": "PHASE_14_44EC_PARTIAL",
        "baseline": BASELINE,
        "baseline_fingerprint": BASELINE_FP,
        "diagnosis_fingerprint": sha(canonical(diagnosis)),
        "isolation_fingerprint": sha(canonical(assessment)),
        "packet_fingerprint": envelope["packet_fingerprint"],
        "current_root_cause": diagnosis["current_root_cause"],
        "historical_root_cause": "MYPY_ROOT_CAUSE_UNVERIFIED",
        "fresh_mypy": "CURRENT_MYPY_PASS",
        "recommendation": assessment["recommendation"],
        "authorization": "NOT_AUTHORIZED",
        "original31_executed": 0,
        "historical_missing": 73,
        "guest_provisioned": False,
        "real_kite_requests": 0,
        "genuine_qualified_pairs": 0,
        "Gate_A": "NOT_AUTHORIZED",
        "Gate_B": "NOT_AUTHORIZED",
        "operational": "NO_GO",
    }


def verify_manifest() -> None:
    envelope = read(DATA / "evidence-manifest.json")
    body = envelope["manifest"]
    require(sha(canonical(body)) == envelope["fingerprint"], "MANIFEST")
    for group in ("artifacts", "baseline_hashes", "external_receipts"):
        for name, expected in body[group].items():
            require(sha((ROOT / name).read_bytes()) == expected, "HASH:" + name)
    require(sha(canonical(report())) == body["replay_fingerprint"], "REPLAY")


if __name__ == "__main__":
    if "--verify" in sys.argv:
        verify_manifest()
    print(canonical(report()).decode())
