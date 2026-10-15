"""Read-only evidence measurements over validated offline portfolio outcomes."""

from datetime import date, datetime
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any, Literal

from pydantic import Field, model_validator

from strategy_engine.backtest.dataset import NSE, Frozen, Hash, Instant, Money
from strategy_engine.phase14.engine import PRECISION
from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, external, publish, sha
from strategy_engine.phase1419.scheduler import EXECUTION_HASH, RISK_HASH, Calendar
from strategy_engine.phase1421.analytics import (
    acknowledgment_view,
    number,
    require,
    symbols,
    validate,
)
from strategy_engine.phase1423.quality import accepted_fills
from strategy_engine.phase1423.quality import inventory as upstream_inventory

D = Decimal
LIMIT = 50000
EXPOSURE_POLICY = dict(
    version="Phase1424Exposure.v1",
    scope="SYNTHETIC_FIXTURE",
    convention="EXPLICIT_CONSTANT_SYNTHETIC_QUOTE_INTERVALS",
    units="INR_SECONDS",
    fraction="gross_market_value/causal_equity",
    adjusted_return="net_return/average_gross_fraction",
    annualization=False,
    interpolation=False,
    shorts=False,
)
MONITORING_POLICY = dict(
    version="Phase1424Monitoring.v1",
    scope="SYNTHETIC_FIXTURE",
    matching="incident_start_id_then_alert_time_id_one_to_one",
    timely="start<=alert<=deadline",
    late="deadline<alert<=window_end",
    false_positives="all_unmatched_alerts_including_duplicates_and_late",
    false_negatives="all_incidents_without_timely_match",
    acknowledgment_is_resolution=False,
    automatic_halt_clearance=False,
)
CATEGORIES = (
    "DATA_GAP",
    "SESSION_DUPLICATE",
    "RISK_LIMIT_BREACHED",
    "PAPER_HALT_TRIGGERED",
    "JOURNAL_CORRUPTED",
    "EOD_RECONCILIATION_FAILED",
    "POLICY_MISMATCH",
    "REPORT_MISSING",
    "CLOCK_ROLLBACK",
    "RECONCILIATION_FAILED",
)


def seconds(start: datetime, end: datetime) -> Decimal:
    delta = end - start
    return D(delta.days * 86400 + delta.seconds) + D(delta.microseconds) / D(1000000)


def seal(body: dict[str, Any]) -> dict[str, Any]:
    value = {k: v for k, v in body.items() if k != "fingerprint"}
    return value | {"fingerprint": sha(canonical(value))}


class Mark(Frozen):
    position_id: Hash
    symbol: str
    strategy_id: Literal["S01", "S02", "S03", "S04", "S05", "S06"]
    quantity: int = Field(gt=0, strict=True)
    side: Literal["LONG"]
    price: Money = Field(gt=0)
    market_value: Money = Field(gt=0)
    source: Literal["SYNTHETIC_CONSTANT_QUOTE.v1"]
    source_id: Hash
    observed_at: Instant
    available_at: Instant
    valid_until: Instant


class Observation(Frozen):
    schema_version: Literal["Phase1424Observation.v1"]
    source: Literal["SYNTHETIC_FIXTURE"]
    run_id: Hash
    session_id: Hash
    trading_date: date
    observation_id: Hash
    at: Instant
    available_at: Instant
    until: Instant
    positions: tuple[Mark, ...]
    equity: Money = Field(gt=0)
    calendar_hash: Hash
    dataset_hash: Hash
    risk_hash: Hash
    execution_hash: Hash
    policy_hash: Hash
    fingerprint: Hash

    @model_validator(mode="after")
    def verified(self) -> "Observation":
        require(self.available_at <= self.at <= self.until, "OBSERVATION_TIME")
        require(self.at.astimezone(NSE).date() == self.trading_date, "OBSERVATION_DATE")
        require(
            self.risk_hash == RISK_HASH and self.execution_hash == EXECUTION_HASH, "POLICY_MISMATCH"
        )
        require(self.policy_hash == sha(canonical(EXPOSURE_POLICY)), "EXPOSURE_POLICY")
        require(
            self.fingerprint
            == sha(canonical(self.model_dump(mode="json", exclude={"fingerprint"}))),
            "OBSERVATION_HASH",
        )
        require(
            len({p.position_id for p in self.positions}) == len(self.positions),
            "DUPLICATE_POSITION",
        )
        with localcontext(PRECISION):
            for p in self.positions:
                require(
                    p.observed_at <= p.available_at <= self.at <= self.until <= p.valid_until,
                    "MARK_NOT_AVAILABLE",
                )
                require(p.market_value == p.price * p.quantity, "MARK_VALUE")
        return self


def inventory() -> dict[str, str]:
    files = sorted((ROOT / "apps/strategy-engine/src/strategy_engine/phase1424").glob("*.py"))
    require({p.name for p in files} == {"__init__.py", "analytics.py"}, "SOFTWARE_INVENTORY")
    return dict(
        sorted(
            (
                upstream_inventory()
                | {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in files}
            ).items()
        )
    )


def bounded(value: Any) -> None:
    require(len(canonical(value)) <= 64 * 1024 * 1024, "INPUT_BUDGET")


def exposure(
    source: dict[str, Any], expected: str, observations: list[dict[str, Any]]
) -> dict[str, Any]:
    """Measure explicit intervals. Never extend an observation across an evidence gap."""
    bounded(source)
    bounded(observations)
    require(sha(canonical(source)) == expected, "SOURCE_HASH")
    require(len(observations) <= LIMIT, "OBSERVATION_BUDGET")
    outcomes = validate(source)
    fills = accepted_fills(source, expected)
    calendar = Calendar.model_validate(source["input"]["calendar"])
    rows = [Observation.model_validate(v) for v in observations]
    require(len({o.observation_id for o in rows}) == len(rows), "DUPLICATE_OBSERVATION")
    require(len({(o.session_id, o.at) for o in rows}) == len(rows), "DUPLICATE_OBSERVATION_TIME")
    require(rows == sorted(rows, key=lambda o: (o.at, o.observation_id)), "OBSERVATION_ORDER")
    dates = {o["binding"]["date"] for o in outcomes}
    require(all(o.trading_date.isoformat() in dates for o in rows), "UNKNOWN_SESSION")
    with localcontext(PRECISION):
        return _exposure(source, outcomes, fills, calendar, rows)


def _exposure(
    source: dict[str, Any],
    outcomes: list[dict[str, Any]],
    fills: Any,
    calendar: Calendar,
    rows: list[Observation],
) -> dict[str, Any]:
    group = {s: dict(capital=D(0), time=D(0), maximum=D(0), pnl=D(0)) for s in symbols()}
    strategies = {
        f"S{i:02}": dict(capital=D(0), time=D(0), maximum=D(0), pnl=D(0)) for i in range(1, 7)
    }
    eligible = covered = capital = fraction_time = market_time = maximum = D(0)
    complete = True
    sessions = []
    for outcome in outcomes:
        day = outcome["binding"]["date"]
        session = next(
            s for s in calendar.sessions if s.start.astimezone(NSE).date().isoformat() == day
        )
        duration = seconds(session.start, session.end)
        eligible += duration
        local = [o for o in rows if o.trading_date.isoformat() == day]
        orders = outcome["reports"]["orders.json"]
        day_fills = [f for f in fills if f.session_date.isoformat() == day]
        dataset = sha(canonical(source["input"]["days"][outcome["binding"]["sequence"]]))
        cursor = session.start
        observed = D(0)
        session_capital = D(0)
        full = bool(local) and local[0].at == session.start
        for o in local:
            require(
                o.run_id == outcome["binding"]["run_id"]
                and o.session_id == sha(canonical(outcome["binding"]))
                and o.calendar_hash == calendar.fingerprint
                and o.dataset_hash == dataset,
                "OBSERVATION_BINDING",
            )
            require(
                session.start <= o.at <= o.until <= session.end and o.at >= cursor,
                "INTERVAL_OVERLAP_OR_SESSION",
            )
            full &= o.at == cursor
            cursor = o.until
            active = {
                p["order_id"]: p
                for p in orders
                if p.get("entry_at")
                and datetime.fromisoformat(p["entry_at"]) <= o.at
                and (not p.get("exit_at") or datetime.fromisoformat(p["exit_at"]) > o.at)
            }
            require(set(active) == {p.position_id for p in o.positions}, "POSITION_OWNERSHIP")
            require(not any(o.at < f.at < o.until for f in day_fills), "POSITION_CHANGE_INTERVAL")
            cash = number(outcome["start"]["cash"]) + sum(
                (f.cash_delta for f in day_fills if f.at <= o.at), D(0)
            )
            gross = sum((p.market_value for p in o.positions), D(0))
            require(o.equity == cash + gross, "EQUITY_RECONCILIATION")
            dt = seconds(o.at, o.until)
            observed += dt
            capital += gross * dt
            session_capital += gross * dt
            fraction_time += gross / o.equity * dt
            market_time += dt if gross else D(0)
            maximum = max(maximum, gross)
            by_strategy: dict[str, Decimal] = {}
            for p in o.positions:
                order = active[p.position_id]
                require(
                    (p.symbol, p.strategy_id, p.quantity)
                    == (order["symbol"], order["strategy_id"], order["quantity"]),
                    "POSITION_OWNERSHIP",
                )
                require(p.symbol in group, "UNKNOWN_SYMBOL")
                g = group[p.symbol]
                g["capital"] += p.market_value * dt
                g["time"] += dt
                g["maximum"] = max(g["maximum"], p.market_value)
                by_strategy[p.strategy_id] = by_strategy.get(p.strategy_id, D(0)) + p.market_value
            for sid, value in by_strategy.items():
                g = strategies[sid]
                g["capital"] += value * dt
                g["time"] += dt
                g["maximum"] = max(g["maximum"], value)
        full &= bool(local) and local[-1].at == session.end and local[-1].until == session.end
        full &= observed == duration and outcome["complete"] and not outcome["end"]["positions"]
        full &= not outcome["end"]["reservations"]
        complete &= full
        covered += observed
        for order in orders:
            if order["state"] == "CLOSED":
                for g in (group[order["symbol"]], strategies[order["strategy_id"]]):
                    g["pnl"] += number(order["net"])
        sessions.append(
            dict(
                date=day,
                seconds=str(duration),
                covered=str(observed),
                complete=full,
                observed_capital_time=str(session_capital),
            )
        )
    complete &= source["summary"]["complete"]
    pnl = number(outcomes[-1]["end"]["equity"]) - number(outcomes[0]["start"]["equity"])
    require(
        sum((g["capital"] for g in group.values()), D(0))
        == capital
        == sum((g["capital"] for g in strategies.values()), D(0)),
        "EXPOSURE_ATTRIBUTION",
    )
    if complete:
        require(
            sum((g["pnl"] for g in group.values()), D(0))
            == pnl
            == sum((g["pnl"] for g in strategies.values()), D(0)),
            "PNL_ATTRIBUTION",
        )

    def attribution(values: dict[str, Any]) -> dict[str, Any]:
        return {
            k: dict(
                status="COMPUTED" if complete else "NOT_COMPUTABLE",
                eligible_seconds=str(eligible),
                observed_seconds=str(covered),
                time_in_position=str(v["time"]),
                observed_capital_time=str(v["capital"]),
                capital_time=str(v["capital"]) if complete else None,
                average=str(v["capital"] / eligible) if complete else None,
                maximum=str(v["maximum"]) if complete else None,
                net_pnl=str(v["pnl"]) if complete else None,
                efficiency=str(v["pnl"] / v["capital"]) if complete and v["capital"] else None,
                coverage=str(covered / eligible),
                reason=None if complete else "INCOMPLETE_EXPOSURE",
            )
            for k, v in values.items()
        }

    avg_fraction = fraction_time / eligible if complete else None
    return dict(
        scope="SYNTHETIC_FIXTURE",
        policy_hash=sha(canonical(EXPOSURE_POLICY)),
        source_hash=sha(canonical(source)),
        evidence_hash=sha(canonical([o.model_dump(mode="json") for o in rows])),
        sessions=sessions,
        status="COMPUTED" if complete else "NOT_COMPUTABLE",
        eligible_seconds=str(eligible),
        observed_seconds=str(covered),
        coverage=str(covered / eligible),
        observed_capital_time=str(capital),
        capital_time=str(capital) if complete else None,
        average_gross=str(capital / eligible) if complete else None,
        average_net=str(capital / eligible) if complete else None,
        average_short="0" if complete else None,
        average_fraction=str(avg_fraction) if complete else None,
        maximum=str(maximum) if complete else None,
        observed_time_in_market=str(market_time),
        observed_zero_exposure_seconds=str(covered - market_time),
        exposure_adjusted_return=str(pnl / number(outcomes[0]["start"]["equity"]) / avg_fraction)
        if avg_fraction
        else None,
        pnl_per_capital_second=str(pnl / capital) if complete and capital else None,
        adjusted_status="COMPUTED" if complete and capital else "NOT_COMPUTABLE",
        symbol_order=list(group),
        strategies=attribution(strategies),
        symbols=attribution(group),
        genuine_qualified=0,
        operational="NO_GO",
    )


class Incident(Frozen):
    incident_id: Hash
    run_id: Hash
    session_date: date
    category: str
    severity: Literal["INFO", "WARNING", "ERROR", "CRITICAL"]
    start: Instant
    end: Instant | None
    expected_alert: str
    deadline: Instant
    window_end: Instant
    source_id: Hash

    @model_validator(mode="after")
    def valid(self) -> "Incident":
        require(
            self.category in CATEGORIES and self.expected_alert == self.category,
            "INCIDENT_CATEGORY",
        )
        require(
            self.start <= self.deadline <= self.window_end
            and (self.end is None or self.end >= self.start),
            "INCIDENT_TIME",
        )
        require(self.start.astimezone(NSE).date() == self.session_date, "INCIDENT_DATE")
        return self


class Truth(Frozen):
    schema_version: Literal["Phase1424IndependentTruth.v1"]
    source: Literal["INDEPENDENT_SYNTHETIC_SCENARIO"]
    policy_hash: Hash
    incidents: tuple[Incident, ...]
    fingerprint: Hash

    @model_validator(mode="after")
    def valid(self) -> "Truth":
        require(len(self.incidents) <= LIMIT, "INCIDENT_BUDGET")
        require(
            len({i.incident_id for i in self.incidents}) == len(self.incidents),
            "DUPLICATE_INCIDENT",
        )
        require(self.policy_hash == sha(canonical(MONITORING_POLICY)), "MONITORING_POLICY")
        require(
            self.fingerprint
            == sha(canonical(self.model_dump(mode="json", exclude={"fingerprint"}))),
            "GROUND_TRUTH_HASH",
        )
        return self


def monitoring(
    truth: dict[str, Any] | None,
    alerts: list[dict[str, Any]],
    expected_alert_hash: str,
    acknowledgments: list[dict[str, Any]] | None = None,
    resolutions: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    with localcontext(PRECISION):
        return _monitoring(truth, alerts, expected_alert_hash, acknowledgments, resolutions)


def _monitoring(
    truth: dict[str, Any] | None,
    alerts: list[dict[str, Any]],
    expected_alert_hash: str,
    acknowledgments: list[dict[str, Any]] | None,
    resolutions: list[dict[str, Any]] | None,
) -> dict[str, Any]:
    bounded(alerts)
    require(len(alerts) <= LIMIT, "ALERT_BUDGET")
    require(sha(canonical(alerts)) == expected_alert_hash, "ALERT_LEDGER_HASH")
    require(len({a["alert_id"] for a in alerts}) == len(alerts), "DUPLICATE_ALERT_ID")
    times = {}
    for a in alerts:
        require(
            sha(canonical({k: v for k, v in a.items() if k != "alert_id"})) == a["alert_id"],
            "ALERT_HASH",
        )
        at = datetime.fromisoformat(a["timestamp"])
        require(
            at.tzinfo is not None and at.astimezone(NSE).date().isoformat() == a["session_date"],
            "ALERT_TIME",
        )
        times[a["alert_id"]] = at
    ack = acknowledgment_view(alerts, acknowledgments or [])
    if truth is None:
        return dict(
            status="NOT_EVALUATED",
            reason="INDEPENDENT_GROUND_TRUTH_ABSENT",
            acknowledgments=ack,
            halt_clearance=False,
        )
    bounded(truth)
    t = Truth.model_validate(truth)
    # Bound comparisons explicitly; refuse pathological matching workloads.
    require(len(t.incidents) * len(alerts) <= 2000000, "MATCHING_BUDGET")
    ordered = sorted(alerts, key=lambda a: (times[a["alert_id"]], a["alert_id"]))
    used: set[str] = set()
    matched = []
    missed = []
    duplicates: set[str] = set()
    late: set[str] = set()
    incidents = sorted(t.incidents, key=lambda i: (i.start, i.incident_id))
    for i in incidents:
        eligible = [
            a
            for a in ordered
            if a["alert_id"] not in used
            and a["run_id"] == i.run_id
            and a["session_date"] == i.session_date.isoformat()
            and a["reason"] == i.expected_alert
            and i.start <= times[a["alert_id"]] <= i.deadline
        ]
        if eligible:
            a = eligible[0]
            used.add(a["alert_id"])
            matched.append(
                dict(
                    incident_id=i.incident_id,
                    alert_id=a["alert_id"],
                    delay_seconds=str(seconds(i.start, times[a["alert_id"]])),
                )
            )
        else:
            missed.append(i.incident_id)
    for a in ordered:
        if a["alert_id"] in used:
            continue
        candidates = [
            i
            for i in incidents
            if a["run_id"] == i.run_id
            and a["session_date"] == i.session_date.isoformat()
            and a["reason"] == i.expected_alert
            and i.start <= times[a["alert_id"]] <= i.window_end
        ]
        if any(times[a["alert_id"]] <= i.deadline for i in candidates):
            duplicates.add(a["alert_id"])
        elif candidates:
            late.add(a["alert_id"])
    resolution = []
    seen: set[str] = set()
    for row in resolutions or []:
        require(seal(row) == row, "RESOLUTION_HASH")
        require(row["incident_id"] not in seen, "DUPLICATE_RESOLUTION")
        incident = next((i for i in incidents if i.incident_id == row["incident_id"]), None)
        require(incident is not None, "UNKNOWN_RESOLUTION")
        assert incident is not None
        at = datetime.fromisoformat(row["timestamp"])
        require(
            at.tzinfo is not None
            and at >= incident.start
            and row["run_id"] == incident.run_id
            and row["status"] == "RESOLVED"
            and row["halt_cleared"] is False,
            "RESOLUTION_INVALID",
        )
        seen.add(incident.incident_id)
        resolution.append(
            dict(incident_id=incident.incident_id, delay_seconds=str(seconds(incident.start, at)))
        )
    with localcontext(PRECISION):
        tp, fp, fn = len(matched), len(alerts) - len(matched), len(missed)
        precision = str(D(tp) / (tp + fp)) if tp + fp else None
        recall = str(D(tp) / (tp + fn)) if tp + fn else None
        f1 = str(D(2 * tp) / (2 * tp + fp + fn)) if 2 * tp + fp + fn else None
    return dict(
        status="EVALUATED_SYNTHETIC",
        policy_hash=sha(canonical(MONITORING_POLICY)),
        ground_truth_hash=t.fingerprint,
        alert_hash=expected_alert_hash,
        true_positives=tp,
        false_positives=fp,
        false_negatives=fn,
        precision=precision,
        recall=recall,
        f1=f1,
        undefined_status="NOT_COMPUTABLE",
        matches=matched,
        unmatched_incidents=missed,
        unmatched_alerts=sorted(set(times) - used),
        duplicate_alerts=sorted(duplicates),
        late_alerts=sorted(late),
        max_detection_delay=str(max(number(m["delay_seconds"]) for m in matched))
        if matched
        else None,
        missed_critical=[
            i.incident_id for i in incidents if i.severity == "CRITICAL" and i.incident_id in missed
        ],
        acknowledgments=ack,
        resolutions=resolution,
        unresolved_incidents=[i.incident_id for i in incidents if i.incident_id not in seen],
        halt_clearance=False,
        production_effectiveness="NOT_EVALUATED",
    )


def publication(directory: Path, report: dict[str, Any], bindings: dict[str, Any]) -> str:
    """Existing external content-addressed atomic publisher; incompatible replay fails."""
    external(directory)
    body = dict(report=report, bindings=bindings, software=inventory())
    digest = sha(canonical(body))
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / "measurement.json"
    if target.exists():
        require(target.read_bytes() == canonical(body), "REPLAY_REJECTED")
    else:
        publish(target, canonical(body))
    return digest
