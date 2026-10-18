"""Read-only preparation replay. Does not import or launch a native supervisor."""

import hashlib
import json
from pathlib import Path
from typing import Any

from strategy_engine.phase1444ei import integration as policy

ROOT = Path(__file__).resolve().parents[2]
CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-I/generation-1")


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
    p = ROOT / ".git" / name
    if p.is_file():
        return p.read_text().strip()
    for line in (ROOT / ".git/packed-refs").read_text().splitlines():
        if line and not line.startswith(("#", "^")):
            value, ref = line.split(" ", 1)
            if ref == name:
                return value
    raise ValueError("MISSING_REF")


def verify() -> dict[str, Any]:
    if git_ref("HEAD") != "ref: refs/heads/develop":
        raise ValueError("BRANCH")
    if (
        git_ref("refs/heads/develop") != policy.BASELINE
        or git_ref("refs/remotes/origin/develop") != policy.BASELINE
    ):
        raise ValueError("BASELINE")
    binding = json.loads((CUSTODY / "replay-inputs.json").read_bytes())
    for name, expected in binding["hashes"].items():
        p = Path(name)
        if not p.is_absolute():
            p = ROOT / p
        if hashlib.sha256(p.read_bytes()).hexdigest() != expected:
            raise ValueError("HASH_CHANGED: " + name)
    baseline = json.loads((CUSTODY / "baseline-hashes.json").read_bytes())
    if len(baseline) != 1757:
        raise ValueError("BASELINE_COUNT")
    for name, expected in baseline.items():
        if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != expected:
            raise ValueError("PROTECTED_BYTES_CHANGED: " + name)
    assessment = json.loads(
        (
            ROOT / "research/phase-14.44ei/generation-1/integration-assessment.json"
        ).read_bytes()
    )
    envelope = assessment["integration_contract"]
    if envelope["fingerprint"] != policy.fingerprint(envelope):
        raise ValueError("CONTRACT_FINGERPRINT")
    verdict = policy.real_authorization(envelope)
    if verdict.execution_authorized or verdict.fixture_accepted:
        raise ValueError("REAL_AUTHORITY_PROMOTED")
    approval = json.loads(
        (ROOT / "research/phase-14.44ei/generation-1/live-approval.json").read_bytes()
    )
    if approval["status"] != "NOT_AUTHORIZED" or approval["authentication"] is not None:
        raise ValueError("UNEXPECTED_LIVE_AUTHORIZATION")
    if approval["test_ids"] != list(policy.TEST_IDS):
        raise ValueError("TEST_INVENTORY")
    return {
        "contract": "Phase1444EIPreparationReplay.v1",
        "baseline": policy.BASELINE,
        "baseline_fingerprint": fingerprint(baseline),
        "binding_fingerprint": fingerprint(binding),
        "authorization_envelope_fingerprint": envelope["fingerprint"],
        "real_authorization": verdict.reason,
        "live_authorization": "NOT_AUTHORIZED",
        "historical_unknowns": "PRESERVED",
        "remaining18": "NOT_EXECUTED",
        "original31": "NOT_EXECUTED",
        "historical_missing": 73,
        "supervised_live_replays": 0,
        "real_kite_requests": 0,
        "gate_a": "NOT_AUTHORIZED",
        "gate_b": "NOT_AUTHORIZED",
        "operational": "NO_GO",
    }


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True, separators=(",", ":"), allow_nan=False))
