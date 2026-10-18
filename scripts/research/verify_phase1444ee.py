"""Static failure-evidence verifier; does not launch qualification or replays."""

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "research/phase-14.44ee/generation-1"


def canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def main() -> None:
    envelope = json.loads((DATA / "evidence-manifest.json").read_bytes())
    body = envelope["manifest"]
    if hashlib.sha256(canonical(body)).hexdigest() != envelope["fingerprint"]:
        raise ValueError("MANIFEST_CHANGED")
    for group in ("baseline_hashes", "artifacts", "external_receipts"):
        for name, expected in body[group].items():
            if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != expected:
                raise ValueError("EVIDENCE_CHANGED:" + name)
    readiness = json.loads((DATA / "readiness-report.json").read_bytes())
    if hashlib.sha256(canonical(readiness["report"])).hexdigest() != readiness["fingerprint"]:
        raise ValueError("READINESS_CHANGED")
    if readiness["report"]["fully_supervised_replays"] != 0:
        raise ValueError("UNSUPPORTED_REPLAY_CLAIM")
    print(canonical({"classification": "PHASE_14_44EE_PARTIAL",
                     "manifest": envelope["fingerprint"], "supervised_replays": 0,
                     "authorization": "NOT_AUTHORIZED_FOR_GUEST_EXECUTION"}).decode())


if __name__ == "__main__":
    main()
