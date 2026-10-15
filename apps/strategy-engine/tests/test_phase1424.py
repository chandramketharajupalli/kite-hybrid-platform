"""Independent synthetic interval and incident schedules, never genuine performance."""

import copy
import json
from datetime import UTC, datetime, timedelta
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any

import pytest

from strategy_engine.phase14.engine import PRECISION
from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1419.scheduler import EXECUTION_HASH, RISK_HASH, Calendar
from strategy_engine.phase1420.multi import alert
from strategy_engine.phase1423.quality import accepted_fills
from strategy_engine.phase1424.analytics import (
    CATEGORIES,
    EXPOSURE_POLICY,
    MONITORING_POLICY,
    exposure,
    monitoring,
    publication,
    seal,
)

D = Decimal


def source_fixture() -> dict[str, Any]:
    path = ROOT / "research/phase-14.20/generation-1/synthetic-multi-sessions.json"
    value: dict[str, Any] = json.loads(path.read_bytes())["scenarios"]["consecutive"]
    return value


def iso(at: datetime) -> str:
    return at.isoformat().replace("+00:00", "Z")


def observations_fixture(source: dict[str, Any]) -> list[dict[str, Any]]:
    """Declared flat synthetic quote 102, independent of any future candle values."""
    fills = accepted_fills(source, sha(canonical(source)))
    calendar = Calendar.model_validate(source["input"]["calendar"])
    rows = []
    for index, envelope in enumerate(source["outcomes"]):
        o = envelope["value"]
        session = calendar.sessions[index]
        orders = o["reports"]["orders.json"]
        local = [f for f in fills if f.session_date.isoformat() == o["binding"]["date"]]
        cuts = sorted({session.start, session.end} | {f.at for f in local})
        for n, at in enumerate(cuts):
            until = cuts[min(n + 1, len(cuts) - 1)]
            positions = []
            for order in orders:
                if (
                    order.get("entry_at")
                    and datetime.fromisoformat(order["entry_at"]) <= at
                    and (not order.get("exit_at") or at < datetime.fromisoformat(order["exit_at"]))
                ):
                    positions.append(
                        dict(
                            position_id=order["order_id"],
                            symbol=order["symbol"],
                            strategy_id=order["strategy_id"],
                            quantity=order["quantity"],
                            side="LONG",
                            price="102",
                            market_value=str(D(102) * order["quantity"]),
                            source="SYNTHETIC_CONSTANT_QUOTE.v1",
                            source_id="1" * 64,
                            observed_at=iso(at),
                            available_at=iso(at),
                            valid_until=iso(until),
                        )
                    )
            cash = D(o["start"]["cash"]) + sum((f.cash_delta for f in local if f.at <= at), D(0))
            rows.append(
                seal(
                    dict(
                        schema_version="Phase1424Observation.v1",
                        source="SYNTHETIC_FIXTURE",
                        run_id=o["binding"]["run_id"],
                        session_id=sha(canonical(o["binding"])),
                        trading_date=o["binding"]["date"],
                        observation_id=sha(canonical([index, n])),
                        at=iso(at),
                        available_at=iso(at),
                        until=iso(until),
                        positions=positions,
                        equity=str(cash + sum((D(p["market_value"]) for p in positions), D(0))),
                        calendar_hash=calendar.fingerprint,
                        dataset_hash=sha(canonical(source["input"]["days"][index])),
                        risk_hash=RISK_HASH,
                        execution_hash=EXECUTION_HASH,
                        policy_hash=sha(canonical(EXPOSURE_POLICY)),
                    )
                )
            )
    return rows


def truth_fixture() -> dict[str, Any]:
    # Independently specified incident schedule. No alert-output argument or inspection.
    start = datetime.fromisoformat("2026-10-05T04:00:00+00:00")
    incidents = [
        dict(
            incident_id=sha(canonical(["independent-schedule", code])),
            run_id="a" * 64,
            session_date="2026-10-05",
            category=code,
            severity="CRITICAL",
            start=iso(start),
            end=None,
            expected_alert=code,
            deadline=iso(start + timedelta(seconds=60)),
            window_end=iso(start + timedelta(seconds=120)),
            source_id="b" * 64,
        )
        for code in CATEGORIES
    ]
    return seal(
        dict(
            schema_version="Phase1424IndependentTruth.v1",
            source="INDEPENDENT_SYNTHETIC_SCENARIO",
            policy_hash=sha(canonical(MONITORING_POLICY)),
            incidents=incidents,
        )
    )


def alerts_fixture() -> list[dict[str, Any]]:
    # Observed outputs are independently specified, including deliberate misses/duplicates.
    events = [
        ("DATA_GAP", 10),
        ("DATA_GAP", 20),
        ("SESSION_DUPLICATE", 15),
        ("RISK_LIMIT_BREACHED", 70),
        ("UNEXPECTED_ALERT", 25),
    ]
    start = datetime.fromisoformat("2026-10-05T04:00:00+00:00")
    return [
        alert(
            "a" * 64,
            "2026-10-05",
            iso(start + timedelta(seconds=s)),
            code,
            sha(canonical([code, s])),
        )
        for code, s in events
    ]


@pytest.fixture(scope="module")
def sample() -> tuple[dict[str, Any], list[dict[str, Any]]]:
    s = source_fixture()
    return s, observations_fixture(s)


def test_duration_attribution(sample: Any) -> None:
    s, rows = sample
    r = exposure(s, sha(canonical(s)), rows)
    assert r["status"] == "COMPUTED" and r["coverage"] == "1"
    assert r["capital_time"] == "1224000" and r["average_gross"] == "340"
    assert r["observed_time_in_market"] == "1200"
    assert r["observed_zero_exposure_seconds"] == "2400"
    assert len(r["symbols"]) == 1115 and len(r["strategies"]) == 6
    assert r["symbols"]["SBIN"]["capital_time"] == r["strategies"]["S06"]["capital_time"]
    assert r["symbols"]["SBIN"]["net_pnl"] == "-2.16958080"
    assert D(r["exposure_adjusted_return"]) < 0
    assert r["symbols"]["SBIN"]["maximum"] == "1020"
    assert r["symbols"]["RELIANCE"]["capital_time"] == "0"
    assert r["symbols"]["RELIANCE"]["efficiency"] is None


@pytest.mark.parametrize("which", [0, 1, 2, 3, 4, 5])
def test_missing_boundary_or_interval(sample: Any, which: int) -> None:
    s, rows = sample
    rows = [r for i, r in enumerate(rows) if i != which]
    r = exposure(s, sha(canonical(s)), rows)
    assert r["status"] == "NOT_COMPUTABLE"
    assert r["capital_time"] is None and r["exposure_adjusted_return"] is None


@pytest.mark.parametrize(
    "field,value",
    [
        ("schema_version", "unknown"),
        ("risk_hash", "0" * 64),
        ("execution_hash", "0" * 64),
        ("calendar_hash", "0" * 64),
        ("dataset_hash", "0" * 64),
        ("session_id", "0" * 64),
        ("equity", "0"),
        ("equity", "NaN"),
        ("equity", "10001"),
        ("available_at", "2026-10-05T04:00:00Z"),
        ("until", "2026-10-05T04:16:00Z"),
        ("trading_date", "2026-10-04"),
    ],
)
def test_invalid_observation(sample: Any, field: str, value: str) -> None:
    s, original = sample
    rows = copy.deepcopy(original)
    rows[0][field] = value
    rows[0] = seal(rows[0])
    with pytest.raises(ValueError):
        exposure(s, sha(canonical(s)), rows)


@pytest.mark.parametrize(
    "field,value",
    [
        ("symbol", "RELIANCE"),
        ("strategy_id", "S01"),
        ("quantity", 11),
        ("position_id", "0" * 64),
        ("side", "SHORT"),
        ("price", "NaN"),
        ("price", "0"),
        ("source", "CAUSAL_DECISION_QUOTE"),
        ("available_at", "2026-10-05T04:10:00Z"),
    ],
)
def test_invalid_mark(sample: Any, field: str, value: Any) -> None:
    s, original = sample
    rows = copy.deepcopy(original)
    rows[1]["positions"][0][field] = value
    rows[1] = seal(rows[1])
    with pytest.raises(ValueError):
        exposure(s, sha(canonical(s)), rows)


def test_duplicate_and_overlap(sample: Any) -> None:
    s, rows = sample
    with pytest.raises(ValueError, match="DUPLICATE_OBSERVATION"):
        exposure(s, sha(canonical(s)), rows[:1] + rows)
    altered = copy.deepcopy(rows)
    altered[0]["until"] = "2026-10-05T04:06:00Z"
    altered[0] = seal(altered[0])
    with pytest.raises(ValueError, match="POSITION_CHANGE_INTERVAL"):
        exposure(s, sha(canonical(s)), altered)


def test_empty_and_precision(sample: Any) -> None:
    s, rows = sample
    assert exposure(s, sha(canonical(s)), [])["capital_time"] is None
    first = exposure(s, sha(canonical(s)), rows)
    with localcontext() as ctx:
        ctx.prec = 8
        assert exposure(s, sha(canonical(s)), rows) == first


def test_causal_prefix(sample: Any) -> None:
    s, original = sample
    rows = copy.deepcopy(original)
    rows[4]["positions"][0]["price"] = "103"
    rows[4]["positions"][0]["market_value"] = "1030"
    rows[4]["equity"] = str(D(rows[4]["equity"]) + 10)
    rows[4] = seal(rows[4])
    a = exposure(s, sha(canonical(s)), original)
    b = exposure(s, sha(canonical(s)), rows)
    assert a["sessions"][0] == b["sessions"][0]
    assert D(b["capital_time"]) - D(a["capital_time"]) == 6000


def test_monitoring_counts() -> None:
    alerts = alerts_fixture()
    r = monitoring(truth_fixture(), alerts, sha(canonical(alerts)))
    assert (r["true_positives"], r["false_positives"], r["false_negatives"]) == (2, 3, 8)
    assert r["precision"] == "0.4" and r["recall"] == "0.2"
    with localcontext(PRECISION):
        assert D(r["f1"]) == D(4) / 15
    assert len(r["duplicate_alerts"]) == len(r["late_alerts"]) == 1
    assert r["max_detection_delay"] == "15"
    assert len(r["missed_critical"]) == 8
    assert len(r["unresolved_incidents"]) == 10 and not r["halt_clearance"]


def test_missing_truth_and_empty_denominators() -> None:
    assert monitoring(None, [], sha(canonical([])))["status"] == "NOT_EVALUATED"
    t = truth_fixture()
    t["incidents"] = []
    r = monitoring(seal(t), [], sha(canonical([])))
    assert r["precision"] is None and r["recall"] is None and r["f1"] is None


def test_competing_matches() -> None:
    t = truth_fixture()
    i = copy.deepcopy(t["incidents"][0])
    i["incident_id"] = "f" * 64
    t["incidents"].append(i)
    a = alerts_fixture()[:1]
    r = monitoring(seal(t), a, sha(canonical(a)))
    assert r["true_positives"] == 1 and r["false_negatives"] == 10
    assert len({m["alert_id"] for m in r["matches"]}) == 1


def test_corrupted_alert_and_duplicate_id() -> None:
    a = alerts_fixture()
    a[0]["reason"] = "POLICY_MISMATCH"
    with pytest.raises(ValueError, match="ALERT_HASH"):
        monitoring(truth_fixture(), a, sha(canonical(a)))
    a = alerts_fixture()[:1] * 2
    with pytest.raises(ValueError, match="DUPLICATE_ALERT_ID"):
        monitoring(truth_fixture(), a, sha(canonical(a)))


def test_changed_truth_hash() -> None:
    t = truth_fixture()
    t["incidents"][0]["severity"] = "WARNING"
    with pytest.raises(ValueError, match="GROUND_TRUTH_HASH"):
        monitoring(t, [], sha(canonical([])))


def test_acknowledgment_not_resolution() -> None:
    a = alerts_fixture()
    body = dict(
        alert_id=a[0]["alert_id"],
        previous="0" * 64,
        status="ACKNOWLEDGED",
        resume_authorized=False,
        timestamp="2026-10-05T04:00:30Z",
        run_id="a" * 64,
    )
    ack = dict(value=body, fingerprint=sha(canonical(body)))
    r = monitoring(truth_fixture(), a, sha(canonical(a)), [ack])
    assert len(r["unresolved_incidents"]) == 10 and not r["halt_clearance"]
    assert r["resolutions"] == []


def test_determinism_and_publication(tmp_path: Path, sample: Any) -> None:
    s, rows = sample
    r = exposure(s, sha(canonical(s)), rows)
    assert exposure(s, sha(canonical(s)), rows) == r
    bindings = dict(source=sha(canonical(s)), observations=sha(canonical(rows)))
    h = publication(tmp_path, r, bindings)
    assert publication(tmp_path, r, bindings) == h
    with pytest.raises(ValueError, match="REPLAY_REJECTED"):
        publication(tmp_path, r, bindings | {"observations": "0" * 64})
    a = alerts_fixture()
    assert monitoring(truth_fixture(), a, sha(canonical(a))) == monitoring(
        truth_fixture(), a, sha(canonical(a))
    )


def test_no_broker_dependency() -> None:
    import ast

    p = ROOT / "apps/strategy-engine/src/strategy_engine/phase1424/analytics.py"
    tree = ast.parse(p.read_text())
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            assert not any(
                x in (node.module or "")
                for x in ("kiteconnect", "requests", "httpx", "socket", "websocket")
            )


def test_integration_and_readiness(sample: Any) -> None:
    from strategy_engine.phase1421.analytics import analyze, readiness
    from strategy_engine.phase1423.quality import assess

    s, rows = sample
    h = sha(canonical(s))
    a = analyze(s, h)
    e = exposure(s, h, rows)
    q = assess(s, h, [])
    assert D(a["portfolio"]["cumulative_net"]) == D(e["symbols"]["SBIN"]["net_pnl"])
    assert q["portfolio"]["slippage_added_to_cash"] == "0"
    ready = readiness("2026-10-06T04:15:00Z", h, True)
    assert ready["engineering"]["status"] == "VALIDATED_OFFLINE"
    assert ready["genuine_strategy"]["status"] != "VALIDATED_OFFLINE"


@pytest.mark.parametrize("change", ["hash", "completion", "journal"])
def test_upstream_corruption(sample: Any, change: str) -> None:
    source, rows = sample
    s = copy.deepcopy(source)
    if change == "completion":
        s["summary"]["complete"] = False
    elif change == "journal":
        s["records"].pop()
    with pytest.raises(ValueError):
        exposure(s, "0" * 64 if change == "hash" else sha(canonical(s)), rows)


def test_resolution_does_not_clear_halt() -> None:
    t = truth_fixture()
    row = seal(
        dict(
            incident_id=t["incidents"][0]["incident_id"],
            run_id="a" * 64,
            timestamp="2026-10-05T04:02:00Z",
            status="RESOLVED",
            halt_cleared=False,
        )
    )
    r = monitoring(t, [], sha(canonical([])), resolutions=[row])
    assert len(r["unresolved_incidents"]) == 9 and not r["halt_clearance"]
    assert r["resolutions"][0]["delay_seconds"] == "120"


def test_monitor_precision_and_order() -> None:
    a = alerts_fixture()
    first = monitoring(truth_fixture(), a, sha(canonical(a)))
    with localcontext() as c:
        c.prec = 6
        assert monitoring(truth_fixture(), a, sha(canonical(a))) == first
    a.reverse()
    other = monitoring(truth_fixture(), a, sha(canonical(a)))
    assert other["matches"] == first["matches"]


def test_interrupted_publication(tmp_path: Path) -> None:
    (tmp_path / "measurement.json.pending").write_bytes(b"incomplete")
    with pytest.raises(FileExistsError):
        publication(tmp_path, {"status": "SYNTHETIC"}, {})
    assert not (tmp_path / "measurement.json").exists()


def test_missing_mark(sample: Any) -> None:
    s, rows = sample
    altered = copy.deepcopy(rows)
    del altered[1]["positions"][0]["price"]
    altered[1] = seal(altered[1])
    with pytest.raises(ValueError):
        exposure(s, sha(canonical(s)), altered)


@pytest.mark.parametrize("offsets,hours", [((4, 7), 0), ((0, 2), 0), ((0, 1), 8)])
def test_calendar_and_zero_exposure(tmp_path: Path, offsets: tuple[int, ...], hours: int) -> None:
    from test_phase1420 import fixture

    from strategy_engine.phase1420.multi import report_views, run

    days, policy, control, calendar, limits = fixture(offsets, hours)
    if offsets == (0, 2):
        calendar = calendar.model_copy(
            update={"holidays": (days[0].config.trading_date + timedelta(days=1),)}
        )
    summary = run(days, policy, control, calendar, limits, tmp_path)
    inputs = dict(
        days=[d.model_dump(mode="json") for d in days],
        policy=policy.model_dump(mode="json"),
        control=control.model_dump(mode="json"),
        calendar=calendar.model_dump(mode="json"),
        limits=limits.model_dump(mode="json"),
    )
    source = dict(
        input=inputs,
        input_hash=sha(canonical(inputs)),
        summary=summary,
        outcomes=[
            json.loads(p.read_bytes()) for p in sorted((tmp_path / "outcomes").glob("*.json"))
        ],
        records=[json.loads(p.read_bytes()) for p in sorted((tmp_path / "records").glob("*.json"))],
        reports=report_views(summary, limits),
    )
    rows = observations_fixture(source)
    r = exposure(source, sha(canonical(source)), rows)
    assert r["eligible_seconds"] == "3600" and r["coverage"] == "1"
    if hours:
        assert r["capital_time"] == "0"
        assert r["exposure_adjusted_return"] is None
        assert r["adjusted_status"] == "NOT_COMPUTABLE"
    else:
        assert r["capital_time"] == "1224000"


def test_unresolved_eod() -> None:
    p = ROOT / "research/phase-14.20/generation-1/synthetic-multi-sessions.json"
    s = json.loads(p.read_bytes())["scenarios"]["unresolved-eod"]
    r = exposure(s, sha(canonical(s)), observations_fixture(s))
    assert r["status"] == "NOT_COMPUTABLE" and r["exposure_adjusted_return"] is None


def test_utc_ist_normalization(sample: Any) -> None:
    from datetime import timezone

    from strategy_engine.phase1424.analytics import Observation

    _, rows = sample
    body = copy.deepcopy(rows[0])
    for key in ("at", "available_at", "until"):
        body[key] = (
            datetime.fromisoformat(body[key])
            .astimezone(timezone(timedelta(hours=5, minutes=30)))
            .isoformat()
        )
    with pytest.raises(ValueError, match="UTC_REQUIRED"):
        Observation.model_validate(body)
    for key in ("at", "available_at", "until"):
        body[key] = iso(datetime.fromisoformat(body[key]).astimezone(UTC))
    assert Observation.model_validate(body).at == datetime.fromisoformat(rows[0]["at"])


def test_monitor_wrong_run_and_late() -> None:
    a = alerts_fixture()[:1]
    a[0]["run_id"] = "f" * 64
    a[0]["alert_id"] = sha(canonical({k: v for k, v in a[0].items() if k != "alert_id"}))
    r = monitoring(truth_fixture(), a, sha(canonical(a)))
    assert r["true_positives"] == 0 and r["false_negatives"] == 10
    assert r["false_positives"] == 1


@pytest.mark.parametrize(
    "field,value",
    [("schema_version", "legacy"), ("policy_hash", "0" * 64), ("source", "COPIED_FROM_ALERTS")],
)
def test_truth_binding(field: str, value: str) -> None:
    t = truth_fixture()
    t[field] = value
    with pytest.raises(ValueError):
        monitoring(seal(t), [], sha(canonical([])))
