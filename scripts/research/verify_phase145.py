"""Read-only Phase14.5 evidence replay. No broker/session/application imports."""

import hashlib
import importlib.util
import json
import subprocess
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
BASELINE = "2c3632ae0ccadbf70da26acbaf95986e9bb79c43"
PLAN = "18f6e41619bcb158f4ac72afb19b359cf46085b8ae6a408c13d30019dc26cbea"
DIRECTORY = ROOT / "research/phase-14.5/generation-1"


def sha(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def canonical(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"),
                      ensure_ascii=True, allow_nan=False).encode()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def envelope(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_bytes())
    require(sha(canonical(value["manifest"])) == value["fingerprint"], "ENVELOPE_MISMATCH")
    for group in ("baseline_raw_sha256", "artifacts_raw_sha256"):
        for name, expected in value["manifest"][group].items():
            require(sha((ROOT / name).read_bytes()) == expected, f"HASH_MISMATCH:{name}")
    return value


def verify() -> dict[str, Any]:
    require(sha((ROOT / "docs/operations/phase-14.5-plan.md").read_bytes()) == PLAN,
            "FROZEN_PLAN_CHANGED")
    spec = importlib.util.spec_from_file_location("phase142_preserved", ROOT / "scripts/research/verify_phase142.py")
    if spec is None or spec.loader is None:
        raise ValueError("PARENT_VERIFIER_MISSING")
    parent = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(parent)
    parent.__dict__["BASELINE"] = BASELINE
    protected = parent.verify()
    for phase in ("14.3", "14.4"):
        envelope(ROOT / f"research/phase-{phase}/generation-1/evidence-manifest.json")
    require(sha((ROOT / "research/phase-14.3/generation-1/validation-amendment-1.json").read_bytes())
            == "1c64f94fa69549c5af568526e989ffad1f3461f17de4da5642555c3e39657b58", "AMENDMENT_CHANGED")
    previous = json.loads((ROOT / "research/phase-14.4/generation-1/git-inventory.json").read_bytes())
    for name in previous["new_files"]:
        blob = subprocess.check_output(["git", "cat-file", "blob", f"{BASELINE}:{name}"], cwd=ROOT)
        require(blob == (ROOT / name).read_bytes(), "COMMITTED_PARENT_CHANGED")
    value = envelope(DIRECTORY / "evidence-manifest.json")
    inventory = json.loads((DIRECTORY / "git-inventory.json").read_bytes())
    for name in inventory["new_files"]:
        raw = (ROOT / name).read_bytes()
        require(b"\r" not in raw, "NEW_TEXT_NOT_LF")
        clean = subprocess.check_output(["git", "hash-object", "--path=" + name, name], cwd=ROOT).decode().strip()
        expected = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
        require(clean == expected, "GIT_FILTER_BYTE_DIFFERENCE")
    return {"result": "PASS", "baseline": BASELINE, "protected": protected,
            "new_files": len(inventory["new_files"]), "fingerprint": value["fingerprint"],
            "real_broker_requests_by_verifier": 0}


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True, indent=2))
