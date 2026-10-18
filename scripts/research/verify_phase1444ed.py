"""Read-only E-D evidence verification and synthetic denial rules."""

import hashlib
import json
import subprocess
import sys
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "research/phase-14.44ed/generation-1"
CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-D/generation-1")
BASELINE = "2e02459f531e30b8c992cde040e7f7c0ce40186d"
BASELINE_FP = "184a91844bed6a9cc163744189f65f8e68ea0f2211d964503120dcea967de717"
REJECTIONS = (
    "missing_provider",
    "unsupported_guest",
    "unverified_image",
    "incomplete_runtime",
    "host_custody_mount",
    "writable_repository",
    "credential_inheritance",
    "network_egress",
    "dns_egress",
    "nat_fallback",
    "child_escape",
    "pid_reuse",
    "unrelated_termination",
    "missing_test_id",
    "changed_source",
    "expired_approval",
    "revoked_approval",
    "missing_authentication",
    "guest_provisioning",
    "database_test_execution",
    "series_v2",
)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def read(path: Path) -> Any:
    return json.loads(path.read_bytes())


def require(value: bool, reason: str) -> None:
    if not value:
        raise ValueError(reason)


def inspect_synthetic(scenario: dict[str, Any]) -> str:
    require(scenario.get("source") == "TEST_ONLY", "SYNTHETIC_PROMOTION")
    require(set(scenario) == {"source", *REJECTIONS}, "SCHEMA")
    for key in REJECTIONS:
        require(scenario[key] is False, key)
    return "TEST_ONLY_NO_EXECUTION_AUTHORITY"


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
    require(len(baseline) == 1697 and sha(canonical(baseline)) == BASELINE_FP, "BASELINE")
    prior = read(ROOT / "research/phase-14.44ec/generation-1/evidence-manifest.json")
    require(
        sha(canonical(prior["manifest"]))
        == "7518d07c873f52f63bdd4f6c00fd6b0f8842312b900f029251c4cb38f6da7ffa",
        "PREDECESSOR",
    )
    for group in ("artifacts", "external_receipts", "predecessor_receipts"):
        for name, expected in prior["manifest"][group].items():
            require(sha((ROOT / name).read_bytes()) == expected, "PRIOR_BYTES:" + name)
    closure = read(DATA / "mypy-closure.json")
    require(
        sha((ROOT / "docs/operations/phase-14.44ed-plan.md").read_bytes())
        == closure["plan_sha256"],
        "PLAN",
    )
    require(closure["current_failure"] == "MYPY_TOOL_INTERNAL_DEFECT", "CURRENT_FAILURE")
    require(
        closure["historical_attribution"] == "HISTORICAL_MYPY_ATTRIBUTION_UNVERIFIED", "HISTORY"
    )
    for i, run in enumerate(closure["runs"], 1):
        require(run == read(CUSTODY / f"mypy-{i}.command.json"), "RUN_RECEIPT")
        require(run["exit_code"] == 0 and not run["timed_out"], "MYPY_FAILURE")
        require(run["command"][-2:] == ["src", "tests"], "NARROWED_SCOPE")
        require(run["tree_identity_verified"], "MONITOR")
        require("139 source files" in (CUSTODY / f"mypy-{i}.stdout").read_text(), "COUNT")
        children = [
            p
            for p in run["processes"]
            if p["pid"] != run["root_pid"] and p["executable"].lower().endswith("python.exe")
        ]
        require(
            bool(children)
            and all(
                p["job_membership_verified"]
                and p["parent_identity_verified"]
                and p["exit_code"] == 0
                for p in children
            ),
            "CHILD",
        )
    require(closure["timeout_qualification"]["unrelated_owned_sentinel_survived"], "TIMEOUT")
    selection = read(DATA / "environment-selection.json")
    require(
        selection["selected_environment"] == "DEFER_PENDING_APPROVED_ISOLATION_HOST", "SELECTION"
    )
    envelope = read(DATA / "environment-approval.json")
    packet = envelope["packet"]
    Draft202012Validator.check_schema(envelope["schema"])
    Draft202012Validator(envelope["schema"]).validate(packet)
    require(sha(canonical(packet)) == envelope["packet_fingerprint"], "PACKET")
    require(sha(canonical(selection)) == packet["selection_fingerprint"], "SELECTION_HASH")
    require(
        packet["status"] == "NOT_AUTHORIZED" and packet["permitted_operations"] == [], "PROMOTION"
    )
    old = read(ROOT / "research/phase-14.44ec/generation-1/environment-approval.json")["packet"]
    require(
        packet["test_ids"] == old["test_ids"] and len(set(packet["test_ids"])) == 31, "TEST_IDS"
    )
    require(packet["source_test_hashes"] == old["source_test_hashes"], "SOURCE_SCOPE")
    for n, v in packet["source_test_hashes"].items():
        require(sha((ROOT / n).read_bytes()) == v, "SOURCE_HASH")
    require(
        sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes())
        == "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31",
        "PHASE1422",
    )
    return {
        "version": "Phase1444EDReplay.v1",
        "classification": "PHASE_14_44ED_PARTIAL",
        "baseline": BASELINE,
        "baseline_fingerprint": BASELINE_FP,
        "closure_fingerprint": sha(canonical(closure)),
        "selection_fingerprint": sha(canonical(selection)),
        "approval_packet_fingerprint": envelope["packet_fingerprint"],
        "mypy": "TWO_FULL_SCOPE_PASSES_139_FILES",
        "monitoring": closure["monitoring"],
        "monitoring_limitations": closure["monitoring_limitations"],
        "selection": selection["selected_environment"],
        "authorization": "NOT_AUTHORIZED",
        "historical_missing": 73,
        "original31_executed": 0,
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
        for n, expected in body[group].items():
            require(sha((ROOT / n).read_bytes()) == expected, "HASH:" + n)
    require(sha(canonical(report())) == body["replay_fingerprint"], "REPLAY")


if __name__ == "__main__":
    if "--verify" in sys.argv:
        verify_manifest()
    print(canonical(report()).decode())
