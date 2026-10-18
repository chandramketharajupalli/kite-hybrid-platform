"""Read-only design verification. Never applies a patch or runs ledger tests."""

import ast
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.44ea/generation-1"
CUSTODY = Path(r"D:\Yogendra\offline-validation-evidence")
BASELINE = "137b948141a475f53b9cd4737c33dcf67c8fc422"
DOCS = (
    "docs/operations/phase-14.44ea-plan.md",
    "docs/architecture/phase-14.44ea-disposable-harness-boundary.md",
    "docs/operations/phase-14.44ea-patch-proposal.md",
    "docs/operations/phase-14.44ea-test-scope.md",
    "docs/operations/phase-14.44ea-execution-authorization.md",
)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def read(path: Path) -> Any:
    return json.loads(path.read_bytes())


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", *args], cwd=ROOT)


def require(value: bool, reason: str) -> None:
    if not value:
        raise ValueError(reason)


def report() -> dict[str, Any]:
    require(git("rev-parse", "HEAD").decode().strip() == BASELINE, "HEAD_CHANGED")
    require(git("rev-parse", "origin/develop").decode().strip() == BASELINE, "ORIGIN_CHANGED")
    require(git("branch", "--show-current").strip() == b"develop", "BRANCH_CHANGED")
    require(not git("diff", "--name-only"), "TRACKED_EDIT")
    require(not git("diff", "--cached", "--name-only"), "STAGED_EDIT")
    names = git("ls-files", "-z").decode().strip("\0").split("\0")
    baseline = {n: sha((ROOT / n).read_bytes()) for n in sorted(names)}
    require(len(baseline) == 1664, "BASELINE_COUNT")
    baseline_fp = sha(canonical(baseline))
    require(
        baseline_fp == "b7a98bc5f65c4c794065562e681e88efc9d7889f2943e352bc5fec295a6a1e3a",
        "BASELINE_BYTES",
    )
    envelope = read(DIRECTORY / "correction-approval.json")
    require(set(envelope) == {"packet", "packet_fingerprint", "json_schema"}, "ENVELOPE")
    packet = envelope["packet"]
    Draft202012Validator.check_schema(envelope["json_schema"])
    Draft202012Validator(envelope["json_schema"]).validate(packet)
    require(sha(canonical(packet)) == envelope["packet_fingerprint"], "PACKET_HASH")
    require(packet["baseline"] == BASELINE, "PACKET_BASELINE")
    require(packet["status"] == "NOT_AUTHORIZED", "UNAUTHORIZED_PROMOTION")
    require(packet["currently_allowed_execution_operations"] == [], "EXECUTION_PERMISSION")
    for field in (
        "owner_identity", "authentication_evidence", "decision_timestamp", "expiry",
        "decision_id", "nonce", "revocation_evidence", "replay_consumption_receipt",
        "verification_receipt", "decision_fingerprint", "new_runtime_interface",
    ):
        require(packet[field] is None, "INVENTED_AUTHORITY:" + field)
    require(sha((ROOT / DOCS[0]).read_bytes()) == packet["plan_sha256"], "PLAN_CHANGED")
    for name, expected in packet["source_hashes"].items():
        require(sha((ROOT / name).read_bytes()) == expected, "SOURCE_CHANGED:" + name)
        require(git("cat-file", "blob", "HEAD:" + name) == (ROOT / name).read_bytes(), "BLOB")
    require(packet["proposed_original_source_diff"] == [], "ORIGINAL_SOURCE_DIFF")
    require(packet["imports_added"] == packet["imports_removed"] == [], "IMPORT_CHANGE")
    text = (ROOT / DOCS[2]).read_text(encoding="utf-8")
    fence = chr(96) * 3
    require(text.count(fence + "diff\n") == 1, "PATCH_FENCE")
    patch = text.split(fence + "diff\n", 1)[1].split(fence, 1)[0]
    require(sha(patch.encode()) == packet["patch_sha256"], "PATCH_HASH")
    entries = packet["proposed_file_inventory"]
    require(len(entries) == 1, "PATCH_SCOPE")
    entry = entries[0]
    require(entry["path"] == "scripts/research/phase1444ea_guest_execution.json", "PATCH_PATH")
    require(not (ROOT / entry["path"]).exists(), "PATCH_ALREADY_APPLIED")
    lines = patch.splitlines(keepends=True)
    require(lines[0] == "--- /dev/null\n", "PATCH_ORIGIN")
    require(lines[1] == "+++ b/" + entry["path"] + "\n", "PATCH_DESTINATION")
    require(re.fullmatch(r"@@ -0,0 \+1,\d+ @@\n", lines[2]) is not None, "PATCH_HUNK")
    require(all(line.startswith("+") for line in lines[3:]), "PATCH_NONADDITIVE")
    new = "".join(line[1:] for line in lines[3:]).encode()
    require(sha(new) == entry["proposed_sha256"], "PROPOSED_CONTENT_HASH")
    require(int(lines[2].split(",")[-1].split()[0]) == len(lines) - 3, "HUNK_COUNT")
    contract = json.loads(new)
    require(contract["executable"] is False and contract["status"] == "NOT_AUTHORIZED", "ACTIVE")
    require(contract["test_ids"] == packet["test_ids"], "PATCH_TEST_SCOPE")
    require(contract["source_hashes"] == packet["source_hashes"], "PATCH_SOURCE_BINDING")
    require(contract["host_backing_root"] == packet["disposable_custody"], "HOST_BINDING")
    require(contract["guest_fixture_root"] == packet["guest_custody"], "GUEST_BINDING")
    require(contract["invariants"] == packet["security_invariants"], "INVARIANT_BINDING")
    require(not Path(packet["disposable_custody"]).exists(), "DISPOSABLE_CREATED")
    events = [
        json.loads(line)
        for line in (CUSTODY / "phase-14.44D/generation-1/c-safe.events.jsonl")
        .read_text().splitlines()
    ]
    excluded = [n for event in events if event["kind"] == "deselected" for n in event["nodes"]]
    original = read(CUSTODY / "phase-14.44C/generation-1/security.inventory.json")["nodes"]
    selected = read(CUSTODY / "phase-14.44D/generation-1/c-safe.inventory.json")["nodes"]
    require(len(excluded) == len(set(excluded)) == 31, "EXCLUDED_INVENTORY")
    require(packet["test_ids"] == excluded, "EXACT_TEST_IDS")
    require(
        set(excluded).isdisjoint({n["nodeid"] for n in selected})
        and set(excluded) | {n["nodeid"] for n in selected} == {n["nodeid"] for n in original}
        and len(original) == 48 and len(selected) == 17,
        "INVENTORY_PARTITION",
    )
    tree = ast.parse((ROOT / packet["original_test_path"]).read_bytes())
    functions = {node.name for node in tree.body if isinstance(node, ast.FunctionDef)}
    scope = (ROOT / DOCS[3]).read_text(encoding="utf-8")
    for node in excluded:
        require(node.split("::")[1].split("[")[0] in functions, "ORIGINAL_TEST_MISSING")
        require(chr(96) + node + chr(96) in scope, "SCOPE_DOCUMENT")
    require(
        sha((ROOT / "docs/operations/phase-14.22-plan.md").read_bytes())
        == "e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31",
        "PHASE1422_CHANGED",
    )
    return {
        "version": "Phase1444EAPreparationReplay.v1", "classification": "PHASE_14_44EA_PARTIAL",
        "baseline": BASELINE, "baseline_hash_count": 1664, "baseline_fingerprint": baseline_fp,
        "packet_fingerprint": envelope["packet_fingerprint"], "patch_fingerprint": packet["patch_sha256"],
        "proposed_content_sha256": entry["proposed_sha256"], "original31_reconciled": True,
        "schema_validation": "PASS", "static_diff_reconstruction": "PASS",
        "policy_hashes": {n: sha((ROOT / n).read_bytes()) for n in DOCS},
        "verifier_sha256": sha(Path(__file__).read_bytes()),
        "tests_executed": 0, "patch_applied": False, "custody_created": False,
        "isolation": "UNVERIFIED", "owner_authorization": "NOT_AUTHORIZED",
        "historical_receipts_missing": 73, "genuine_qualified_pairs": 0, "real_kite_requests": 0,
        "Gate_A": "NOT_AUTHORIZED", "Gate_B": "NOT_AUTHORIZED", "Stage_B": "ZERO_CALL_ABORT",
        "Phase12": "BLOCK", "H1_H2": "FROZEN", "July": "SEALED", "operational": "NO_GO",
    }


def verify_manifest() -> None:
    value = read(DIRECTORY / "evidence-manifest.json")
    manifest = value["manifest"]
    require(sha(canonical(manifest)) == value["fingerprint"], "MANIFEST_HASH")
    for group in ("baseline_hashes", "artifacts", "predecessor_receipts"):
        for name, expected in manifest[group].items():
            require(sha((ROOT / name).read_bytes()) == expected, "PRESERVATION:" + name)
    for name in manifest["predecessor_manifests"]:
        path = ROOT / name
        prior = read(path)
        require(sha(canonical(prior["manifest"])) == prior["fingerprint"], "PRIOR_ENVELOPE")
        for group in ("baseline_hashes", "artifacts", "external_receipts", "receipts"):
            for item, expected in prior["manifest"].get(group, {}).items():
                base = path.parent if group == "receipts" else ROOT
                require(sha((base / item).read_bytes()) == expected, "PRIOR_BYTES:" + item)
    require(sha(canonical(report())) == manifest["replay_fingerprint"], "REPLAY_CHANGED")


if __name__ == "__main__":
    if "--verify" in sys.argv:
        verify_manifest()
    print(canonical(report()).decode())
