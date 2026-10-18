"""Read-only preparation evidence verifier; never imports native supervisors."""

import hashlib
import json
from pathlib import Path
from typing import Any

from strategy_engine.phase1444ej import enforcement

ROOT = Path(__file__).resolve().parents[2]
CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-J/generation-1")


def fingerprint(value: Any) -> str:
    return hashlib.sha256(
        json.dumps(
            value,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=True,
            allow_nan=False,
        ).encode()
    ).hexdigest()


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
    if any(
        git_ref(name) != enforcement.BASELINE
        for name in ("refs/heads/develop", "refs/remotes/origin/develop")
    ):
        raise ValueError("BASELINE")
    binding = json.loads((CUSTODY / "replay-inputs.json").read_bytes())
    for name, expected in binding["hashes"].items():
        path = Path(name)
        if not path.is_absolute():
            path = ROOT / path
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError("CHANGED: " + name)
    baseline = json.loads((CUSTODY / "baseline-hashes.json").read_bytes())
    if len(baseline) != 1769:
        raise ValueError("BASELINE_COUNT")
    for name, expected in baseline.items():
        if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != expected:
            raise ValueError("BASELINE_BYTES")
    assessment = json.loads(
        (
            ROOT / "research/phase-14.44ej/generation-1/authorization-assessment.json"
        ).read_bytes()
    )
    inventory = json.loads((CUSTODY / "test-inventory.json").read_bytes())
    if assessment["decision"]["test_ids"] != inventory["remaining18"]:
        raise ValueError("TEST_INVENTORY")
    if len(set(inventory["remaining18"])) != 18:
        raise ValueError("TEST_COUNT")
    if assessment["decision"]["status"] != "NOT_AUTHORIZED":
        raise ValueError("AUTHORIZATION")
    if enforcement.authorize_real(assessment):
        raise ValueError("REAL_AUTHORITY")
    for label in ("policy", "ruff", "mypy"):
        receipt = json.loads((CUSTODY / (label + ".command.json")).read_bytes())
        if receipt["exit_code"] != 0:
            raise ValueError("FAILED_GATE")
    return {
        "contract": "Phase1444EJPreparationReplay.v1",
        "baseline": enforcement.BASELINE,
        "baseline_fingerprint": fingerprint(baseline),
        "binding_fingerprint": fingerprint(binding),
        "assessment_fingerprint": fingerprint(assessment),
        "owner": assessment["owner"]["status"],
        "trusted_time": assessment["trusted_time"]["status"],
        "replay_authority": assessment["replay"]["status"],
        "lease": assessment["lease"]["status"],
        "remaining18": "NOT_EXECUTED",
        "original31": "NOT_EXECUTED",
        "historical_missing": 73,
        "native_activation": "NOT_AUTHORIZED",
        "real_kite_requests": 0,
        "gate_a": "NOT_AUTHORIZED",
        "gate_b": "NOT_AUTHORIZED",
        "operational": "NO_GO",
    }


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True, separators=(",", ":"), allow_nan=False))
