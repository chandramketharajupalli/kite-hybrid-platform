"""Deterministic missing-metadata assessment; never retrieves evidence."""

import json
from pathlib import Path
from typing import Any

from strategy_engine.phase1432.attestation import decision, evaluate, retrieval_request

ROOT = Path(__file__).resolve().parents[2]


def prepare() -> dict[str, Any]:
    base = ROOT / "research/phase-14.31/generation-1"
    acquisition = json.loads((base / "artifact-acquisition-manifest.json").read_bytes())
    authorization = json.loads((base / "acquisition-authorization.json").read_bytes())
    ledger = evaluate(acquisition, [], {})
    return {
        "attestation-ledger.json": ledger,
        "acquisition-decision.json": decision(
            acquisition, ledger, authorization["manifest"]["destination"]
        ),
        "metadata-retrieval-request.json": retrieval_request(acquisition),
    }


if __name__ == "__main__":
    print(json.dumps(prepare(), sort_keys=True))
