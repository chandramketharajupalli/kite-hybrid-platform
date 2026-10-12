"""Read-only preservation audit. Universal newline normalization is in memory only."""

import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLAN = "d6b8a56494830d75f7060f03a1d7095bdb5bad01c5dc7c5ee3dd792e8c6480ca"
BASELINE = "d7d6c84226d6f12d6330e1618c184c3a9c9c4837"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify() -> dict[str, object]:
    for ref in ("HEAD", "origin/develop"):
        actual = subprocess.check_output(["git", "rev-parse", ref], cwd=ROOT, text=True).strip()
        if actual != BASELINE:
            raise ValueError("GIT_BASELINE_CHANGED")
    if (
        subprocess.check_output(["git", "branch", "--show-current"], cwd=ROOT, text=True).strip()
        != "develop"
    ):
        raise ValueError("BRANCH_CHANGED")
    if sha((ROOT / "docs/operations/phase-14.0-plan.md").read_bytes()) != PLAN:
        raise ValueError("FROZEN_PHASE14_PLAN_CHANGED")
    envelope = json.loads(
        (ROOT / "research/phase-13.20/generation-1/evidence-manifest.json").read_text()
    )
    manifest = envelope["manifest"]
    canonical = json.dumps(
        manifest, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
    ).encode()
    if sha(canonical) != envelope["fingerprint"]:
        raise ValueError("PARENT_ENVELOPE_CHANGED")
    checked = {}
    for key in (
        "protected_research_raw_sha256",
        "prior_kl_raw_sha256",
        "registered_parent_plans",
        "new_documents_raw_sha256",
        "frozen_source_registered_text_sha256",
    ):
        for name, expected in manifest[key].items():
            path = ROOT / name
            content = (
                path.read_text(encoding="utf-8").encode("utf-8")
                if key == "frozen_source_registered_text_sha256"
                else path.read_bytes()
            )
            if sha(content) != expected:
                raise ValueError(f"PRESERVATION_MISMATCH:{name}")
        checked[key] = len(manifest[key])
    # All existing research and governance/docs/config/universe bytes, not just the subset above.
    for name, expected in manifest["initial_tracked_raw_sha256"].items():
        if name.startswith(("research/", "docs/", "config/")) or name == "universe.csv":
            if sha((ROOT / name).read_bytes()) != expected:
                raise ValueError(f"PRIOR_EVIDENCE_CHANGED:{name}")
    for parent in manifest["parents"]:
        path = ROOT / parent["path"]
        if sha(path.read_bytes()) != parent["raw_sha256"]:
            raise ValueError(f"PARENT_RAW_CHANGED:{path}")
        value = json.loads(path.read_text())["manifest"]
        if (
            sha(
                json.dumps(
                    value, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
                ).encode()
            )
            != parent["canonical"]
        ):
            raise ValueError(f"PARENT_CANONICAL_CHANGED:{path}")
    checked["parent_envelopes"] = len(manifest["parents"])
    checked["governance_envelopes_raw_covered_by_KL"] = len(manifest["k_canonical_envelope_paths"])
    return {
        "result": "PASS",
        "checked": checked,
        "plan_raw_sha256": PLAN,
        "parent_fingerprint": envelope["fingerprint"],
        "real_kite_requests_by_this_run": 0,
    }


if __name__ == "__main__":
    result = verify()
    current_path = ROOT / "research/phase-14.0/generation-1/evidence-manifest.json"
    if current_path.exists():
        current = json.loads(current_path.read_text())
        payload = current["manifest"]
        encoded = json.dumps(
            payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
        ).encode()
        if sha(encoded) != current["fingerprint"]:
            raise ValueError("PHASE14_ENVELOPE_CHANGED")
        for name, expected in payload["after_sha256"].items():
            if sha((ROOT / name).read_bytes()) != expected:
                raise ValueError(f"PHASE14_ARTIFACT_CHANGED:{name}")
        result["phase14_artifacts_verified"] = len(payload["after_sha256"])
        result["phase14_fingerprint"] = current["fingerprint"]
    print(json.dumps(result, indent=2, sort_keys=True))
