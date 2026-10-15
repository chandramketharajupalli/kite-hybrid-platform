"""Offline analytics regressions using sealed synthetic session evidence."""

import copy
import json
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any

import pytest

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1421.analytics import (
    acknowledgment_view,
    analyze,
    number,
    readiness,
    risk_metrics,
    run,
)

SOURCE = ROOT / "research/phase-14.20/generation-1/synthetic-multi-sessions.json"


def source(name: str = "consecutive") -> dict[str, Any]:
    value: dict[str, Any] = json.loads(SOURCE.read_bytes())["scenarios"][name]
    return value


def report(value: dict[str, Any]) -> dict[str, Any]:
    return analyze(value, sha(canonical(value)))


@pytest.fixture(scope="module")
def result() -> dict[str, Any]:
    return report(source())


def test_equity_and_daily_returns(result: dict[str, Any]) -> None:
    assert result["portfolio"]["closing_equity"] == "9997.83041920"
    assert Decimal(result["portfolio"]["cumulative_net"]) == Decimal("-2.16958080")
    assert [d["net"] for d in result["daily"]] == ["-1.08479040"] * 2
    assert result["portfolio"]["losing_sessions"] == 2


def test_drawdown_and_peak(result: dict[str, Any]) -> None:
    assert result["portfolio"]["peak_equity"] == "10000"
    assert Decimal(result["portfolio"]["maximum_drawdown"]) == Decimal("2.16958080")
    assert Decimal(result["portfolio"]["drawdown_fraction"]) == Decimal("0.00021695808")


def test_attribution(result: dict[str, Any]) -> None:
    assert len(result["symbol_attribution"]) == 1115
    assert len(result["strategy_attribution"]) == 6
    for key in ("symbol_attribution", "strategy_attribution"):
        assert sum(Decimal(r["net"]) for r in result[key]) == Decimal("-2.16958080")
        assert sum(Decimal(r["fees"]) for r in result[key]) == Decimal("2.16958080")
        assert sum(r["trades"] for r in result[key]) == 2
    s06 = result["strategy_attribution"][5]
    assert s06["losses"] == 2 and s06["peak_cost_basis"] == "1020"


def test_cost_turnover_and_slippage(result: dict[str, Any]) -> None:
    assert result["cost"]["turnover"] == "4080"
    assert result["cost"]["average_per_completed_trade"]["value"] == "1.08479040"
    assert result["cost"]["slippage"]["value"] is None
    assert sum(Decimal(v) for v in result["cost"]["components"].values()) == Decimal("2.16958080")


def test_no_trade_symbols(result: dict[str, Any]) -> None:
    row = next(r for r in result["symbol_attribution"] if r["symbol"] != "SBIN")
    assert row["trades"] == 0 and row["net"] == "0"
    assert row["data_status"] == "NO_SIMULATED_TRADES"


def test_small_sample(result: dict[str, Any]) -> None:
    assert all(r["status"] == "NOT_COMPUTABLE" for r in result["risk_adjusted"].values())


def test_zero_volatility() -> None:
    values = risk_metrics([Decimal(0)] * 30, True, Decimal(0))
    assert Decimal(values["volatility"]["value"]) == 0
    assert values["sharpe"]["reason"] == "ZERO_VOLATILITY"
    assert values["sortino"]["reason"] == "ZERO_DOWNSIDE"
    assert values["calmar"]["value"] is None


def test_sufficient_samples_and_precision() -> None:
    returns = [Decimal("0.01"), Decimal("-0.01")] * 15
    a = risk_metrics(returns, True, Decimal("0.1"))
    with localcontext() as c:
        c.prec = 6
        b = risk_metrics(returns, True, Decimal("0.1"))
    assert a == b
    assert Decimal(a["sharpe"]["value"]) == 0
    assert Decimal(a["downside_deviation"]["value"]) > 0


@pytest.mark.parametrize("value", ["NaN", "Infinity", "-Infinity", 1.2, True])
def test_bad_numeric(value: Any) -> None:
    with pytest.raises(ValueError):
        number(value)


def test_incomplete_halt() -> None:
    r = report(source("unresolved-eod"))
    assert r["monitoring"]["halted"] is True
    assert r["monitoring"]["unprocessed_sessions"] == 1
    assert r["monitoring"]["incomplete_sessions"] == 1
    assert r["monitoring"]["detected_incidents"] == 1
    assert r["monitoring"]["resolved_incidents"] == 0
    assert r["trades"]["count"] == 0 and r["trades"]["win_rate"]["value"] is None
    assert r["readiness"]["engineering"]["status"] == "PARTIAL"


def test_monitoring_no_ground_truth(result: dict[str, Any]) -> None:
    assert result["monitoring"]["effectiveness"] == "NOT_EVALUATED"
    assert result["monitoring"]["false_negatives"] is None
    assert result["monitoring"]["resolution_delay"] is None


def test_readiness_independent(result: dict[str, Any]) -> None:
    assert result["readiness"]["engineering"]["status"] == "VALIDATED_OFFLINE"
    assert result["readiness"]["genuine_strategy"]["status"] == "BLOCKED"
    assert result["readiness"]["operational"]["status"] == "BLOCKED"
    assert result["genuine_qualified"] == 0 and result["family_size"] == 13380
    assert result["operational"] == "NO_GO"
    assert readiness("t", "hash", False)["operational"]["owner_approval"] is None


@pytest.mark.parametrize(
    "mutation",
    [
        "equity",
        "order",
        "policy",
        "missing",
        "duplicate",
        "trust",
        "calendar",
        "source_hash",
        "software",
        "alerts",
    ],
)
def test_tampered_evidence(mutation: str) -> None:
    value = source()
    if mutation == "equity":
        value["outcomes"][0]["value"]["end"]["equity"] = "20000"
    elif mutation == "order":
        value["outcomes"][0]["value"]["reports"]["orders.json"] *= 2
    elif mutation == "policy":
        value["input"]["policy"]["base"]["capital"] = "99999"
    elif mutation == "missing":
        value["records"].pop()
    elif mutation == "duplicate":
        value["records"][1] = copy.deepcopy(value["records"][0])
    elif mutation == "trust":
        value["summary"]["scope"] = "VERIFIED_AUTHORIZED_LOCAL_DATA"
    elif mutation == "calendar":
        value["input"]["calendar"]["version"] = "changed"
    elif mutation == "source_hash":
        with pytest.raises(ValueError, match="SOURCE_HASH"):
            analyze(value, "0" * 64)
        return
    elif mutation == "software":
        value["outcomes"][0]["value"]["reports"]["readiness.json"]["binding"]["software"] = {}
    else:
        value["summary"]["alerts"] *= 2
    with pytest.raises(ValueError):
        report(value)


def test_missing_required_evidence() -> None:
    s = source()
    del s["input"]
    with pytest.raises((KeyError, ValueError)):
        report(s)


def test_determinism(result: dict[str, Any]) -> None:
    assert canonical(report(source())) == canonical(result)


def test_restart_safe_publication(tmp_path: Path) -> None:
    first = run(SOURCE, sha(SOURCE.read_bytes()), tmp_path)
    before = (tmp_path / "analytics.json").read_bytes()
    assert run(SOURCE, sha(SOURCE.read_bytes()), tmp_path) == first
    assert (tmp_path / "analytics.json").read_bytes() == before
    with pytest.raises(ValueError):
        run(SOURCE, "0" * 64, tmp_path)


def test_no_broker_imports() -> None:
    import ast

    p = ROOT / "apps/strategy-engine/src/strategy_engine/phase1421/analytics.py"
    modules = [
        n.module or "" for n in ast.walk(ast.parse(p.read_text())) if isinstance(n, ast.ImportFrom)
    ]
    assert not any("kite" in n or "http" in n or "socket" in n for n in modules)


def test_acknowledgment_is_not_resolution() -> None:
    alerts = source("unresolved-eod")["summary"]["alerts"]
    event = alerts[-1]
    row = dict(
        alert_id=event["alert_id"],
        run_id=event["run_id"],
        timestamp=event["timestamp"],
        evidence="a" * 64,
        status="ACKNOWLEDGED",
        resume_authorized=False,
        previous="0" * 64,
    )
    envelope = dict(value=row, fingerprint=sha(canonical(row)))
    view = acknowledgment_view(alerts, [envelope])
    assert view["acknowledged"] == 1 and view["resolved_incidents"] == 0
    assert view["resume_authorized"] is False and view["delays"][0]["seconds"] == "0"
    with pytest.raises(ValueError):
        acknowledgment_view(alerts, [envelope, envelope])


def test_zero_trade_sessions(tmp_path: Path) -> None:
    from strategy_engine.phase1418.portfolio import Control, Policy
    from strategy_engine.phase1419.scheduler import Calendar
    from strategy_engine.phase1420.multi import Day, Limits, report_views
    from strategy_engine.phase1420.multi import run as multi_run

    s = source()
    inputs = s["input"]
    for d in inputs["days"]:
        d["config"]["cutoff"] = d["config"]["cutoff"][:11] + "03:50:00Z"
    days = tuple(Day.model_validate(d) for d in inputs["days"])
    policy, control = (
        Policy.model_validate(inputs["policy"]),
        Control.model_validate(inputs["control"]),
    )
    calendar, limits = (
        Calendar.model_validate(inputs["calendar"]),
        Limits.model_validate(inputs["limits"]),
    )
    summary = multi_run(days, policy, control, calendar, limits, tmp_path)
    s.update(
        input_hash=sha(canonical(inputs)),
        summary=summary,
        outcomes=[
            json.loads(p.read_bytes()) for p in sorted((tmp_path / "outcomes").glob("*.json"))
        ],
        records=[json.loads(p.read_bytes()) for p in sorted((tmp_path / "records").glob("*.json"))],
        reports=report_views(summary, limits),
    )
    r = report(s)
    assert r["trades"]["count"] == 0
    assert Decimal(r["portfolio"]["cumulative_net"]) == 0
    assert r["cost"]["fraction_of_turnover"]["value"] is None
    assert len(r["daily"]) == 2


def test_rehashed_bad_outcome_rejected() -> None:
    s = source()
    out = s["outcomes"][0]
    out["value"]["daily"]["equity"] = "20000"
    out["fingerprint"] = sha(canonical(out["value"]))
    record = s["records"][0]
    record["value"]["outcome_hash"] = sha(canonical(out))
    record["fingerprint"] = sha(canonical(record["value"]))
    with pytest.raises(ValueError, match="OUTCOME_REPLAY_MISMATCH"):
        report(s)
