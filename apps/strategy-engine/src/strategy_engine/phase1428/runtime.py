"""Bounded synthetic strategy assessment through unchanged validated algorithms."""

from datetime import UTC, datetime, timedelta
from decimal import Decimal
from typing import Any

from strategy_engine.phase14.engine import Candle
from strategy_engine.phase1415.hardening import frozen_policy
from strategy_engine.phase1415.strategies import entry_signal, registry
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1419.scheduler import EXECUTION_HASH, RISK_HASH
from strategy_engine.phase1428.resources import load, require


def assess() -> dict[str, Any]:
    resources = load()
    cfg = resources["configuration"]
    definitions = registry()
    require(
        cfg["strategies"] == sha(canonical([d.model_dump(mode="json") for d in definitions]))
        and cfg["qualification"] == sha(canonical(frozen_policy().model_dump(mode="json")))
        and cfg["risk"] == RISK_HASH and cfg["execution"] == EXECUTION_HASH,
        "CONFIGURATION_POLICY_BINDING",
    )
    bars = tuple(
        Candle(
            start=datetime(2025, 1, 2, 3, 45, tzinfo=UTC) + timedelta(minutes=5 * i),
            open=Decimal(100 + i % 4), high=Decimal(105 + i % 4),
            low=Decimal(95 + i % 4), close=Decimal(101 + i % 4), volume=1000,
            open_interest=None,
        )
        for i in range(40)
    )
    signals = {d.identifier: entry_signal(bars, d) for d in definitions}
    body = dict(
        version="Phase1428StandaloneAssessment.v1", scope="SYNTHETIC_OFFLINE",
        resource_version=resources["manifest"]["version"],
        symbols=len(resources["symbols"]), strategies=len(definitions),
        family_size=1115 * len(definitions) * 2,
        universe=resources["manifest"]["resources"]["universe"]["sha256"],
        inputs=[b.model_dump(mode="json") for b in bars], signals=signals,
        genuine_qualified=0, operational="NO_GO", real_kite_requests=0,
        Gate_A="NOT_AUTHORIZED", Gate_B="NOT_AUTHORIZED",
    )
    return body | {"fingerprint": sha(canonical(body))}
