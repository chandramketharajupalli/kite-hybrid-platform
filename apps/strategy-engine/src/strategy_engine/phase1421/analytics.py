"""Bounded, independently replayed synthetic analytics and separate readiness views."""

import csv
import json
from datetime import datetime
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any

from strategy_engine.backtest.costs import Charges, IntradayCostSchedule
from strategy_engine.phase14.engine import PRECISION
from strategy_engine.phase1415.hardening import ROOT, frozen_policy
from strategy_engine.phase1416.imports import canonical, external, publish, sha
from strategy_engine.phase1418.portfolio import Control, Policy
from strategy_engine.phase1419.scheduler import Calendar
from strategy_engine.phase1419.scheduler import software as scheduler_software
from strategy_engine.phase1420.multi import Day, Limits, execute, report_views, software

D = Decimal
SCHEMA = "Phase1421Analytics.v1"
METRICS: dict[str, Any] = dict(
    version="Phase1421DescriptiveMetrics.v1",
    frequency="completed_session",
    minimum_sessions=30,
    annual_sessions=252,
    annual_risk_free="0",
    scope="SYNTHETIC_ASSUMPTIONS_NOT_QUALIFICATION_POLICY",
)


def require(ok: bool, reason: str) -> None:
    if not ok:
        raise ValueError(reason)


def number(value: Any) -> Decimal:
    require(
        isinstance(value, (str, int, Decimal)) and not isinstance(value, bool), "UNSUPPORTED_NUMBER"
    )
    result = D(value)
    require(result.is_finite(), "NONFINITE_METRIC")
    return result


def unavailable(reason: str) -> dict[str, Any]:
    return dict(status="NOT_COMPUTABLE", value=None, reason=reason)


def metric(value: Decimal) -> dict[str, Any]:
    return dict(status="COMPUTED", value=str(number(value)), reason=None)


def risk_metrics(returns: list[Decimal], complete: bool, drawdown: Decimal) -> dict[str, Any]:
    with localcontext(PRECISION):
        values = [number(v) for v in returns]
        require(number(drawdown) >= 0, "INVALID_DRAWDOWN")
        names = ("volatility", "sharpe", "sortino", "downside_deviation", "calmar")
        if not complete or len(values) < METRICS["minimum_sessions"]:
            return {n: unavailable("INCOMPLETE_OR_INSUFFICIENT_SESSIONS") for n in names}
        n = D(len(values))
        mean = sum(values, D(0)) / n
        variance = sum(((v - mean) ** 2 for v in values), D(0)) / (n - 1)
        sd = variance.sqrt()
        downside = (sum((min(v, D(0)) ** 2 for v in values), D(0)) / n).sqrt()
        annual = D(METRICS["annual_sessions"])
        wealth = D(1)
        for v in values:
            require(v >= -1, "INVALID_RETURN")
            wealth *= 1 + v
        calmar = unavailable("REQUIRES_252_SESSIONS_AND_POSITIVE_DRAWDOWN")
        if len(values) >= 252 and drawdown > 0 and wealth > 0:
            calmar = metric((wealth ** (annual / n) - 1) / drawdown)
        return dict(
            volatility=metric(sd * annual.sqrt()),
            downside_deviation=metric(downside * annual.sqrt()),
            sharpe=metric(mean / sd * annual.sqrt()) if sd else unavailable("ZERO_VOLATILITY"),
            sortino=metric(mean / downside * annual.sqrt())
            if downside
            else unavailable("ZERO_DOWNSIDE"),
            calmar=calmar,
        )


def inventory() -> dict[str, str]:
    directory = ROOT / "apps/strategy-engine/src/strategy_engine/phase1421"
    paths = sorted(directory.glob("*.py"))
    require({p.name for p in paths} == {"__init__.py", "analytics.py"}, "SOFTWARE_INVENTORY")
    return dict(
        sorted(
            (
                software() | {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in paths}
            ).items()
        )
    )


def symbols() -> list[str]:
    with (ROOT / "config/universes/phase14_unique_universe.csv").open(encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))
    require(
        len(rows) == 1115
        and all(r["exchange"] == "NSE" and r["enabled"].lower() == "true" for r in rows),
        "UNIVERSE",
    )
    result = [r["symbol"] for r in rows]
    require(len(set(result)) == 1115, "UNIVERSE_DUPLICATE")
    return result


def validate(source: dict[str, Any]) -> list[dict[str, Any]]:
    """Reproduce accepted outcomes; hashes alone are not semantic validation."""
    require(source["summary"]["scope"] == "SYNTHETIC_FIXTURE", "UNSUPPORTED_PROVENANCE")
    inputs = source["input"]
    require(sha(canonical(inputs)) == source["input_hash"], "INPUT_HASH")
    days = tuple(Day.model_validate(d) for d in inputs["days"])
    require(0 < len(days) <= 252, "SESSION_BUDGET")
    policy, control = (
        Policy.model_validate(inputs["policy"]),
        Control.model_validate(inputs["control"]),
    )
    calendar, limits = (
        Calendar.model_validate(inputs["calendar"]),
        Limits.model_validate(inputs["limits"]),
    )
    require(calendar.synthetic and policy.base.fixture_only, "UNSUPPORTED_PROVENANCE")
    run_binding = dict(
        schema="Phase1420MultiSession.v1",
        days=[sha(canonical(d.model_dump(mode="json"))) for d in days],
        calendar=calendar.fingerprint,
        policy=policy.fingerprint,
        policy_version=policy.fingerprint_version,
        control=control.model_dump(mode="json"),
        limits=limits.model_dump(mode="json"),
        software=software(),
        scheduler_software=sha(canonical(scheduler_software())),
    )
    require(source["summary"]["run_id"] == sha(canonical(run_binding)), "RUN_BINDING")
    outcomes, records = source["outcomes"], source["records"]
    require(0 < len(outcomes) == len(records) <= len(days), "SESSION_RECORD_MISSING")
    require(len({d.config.trading_date for d in days}) == len(days), "DUPLICATE_SESSION")
    require(
        [d.config.trading_date for d in days] == sorted(d.config.trading_date for d in days),
        "SESSION_ORDER",
    )
    previous, last = "0" * 64, None
    result = []
    snapshots = 0
    for i, (out, rec) in enumerate(zip(outcomes, records, strict=True)):
        o, record = out["value"], rec["value"]
        require(
            sha(canonical(o)) == out["fingerprint"]
            and sha(canonical(record)) == rec["fingerprint"],
            "ENVELOPE_HASH",
        )
        require(
            record["sequence"] == i
            and record["previous"] == previous
            and record["outcome_hash"] == sha(canonical(out))
            and record["binding"] == o["binding"],
            "SESSION_CHAIN",
        )
        binding = o["binding"]
        require(
            binding["date"] == days[i].config.trading_date.isoformat()
            and binding["day"] == sha(canonical(days[i].model_dump(mode="json")))
            and binding["calendar"] == calendar.fingerprint
            and binding["run_id"] == source["summary"]["run_id"]
            and binding["previous"] == previous
            and binding["sequence"] == i
            and binding["starting_state"] == sha(canonical(o["start"])),
            "SESSION_BINDING",
        )
        if last is not None:
            require(last == o["start"] and not last["halted"], "CARRY_FORWARD")
        else:
            require(
                number(o["start"]["cash"]) == policy.base.capital
                and number(o["start"]["equity"]) == policy.base.capital
                and number(o["start"]["cumulative_net"]) == 0
                and number(o["start"]["cumulative_fees"]) == 0,
                "OPENING_STATE",
            )
        snapshots += len(o["reports"]["cash-ledger.json"])
        require(snapshots <= 250000, "SNAPSHOT_BUDGET")
        with localcontext(PRECISION):
            require(
                execute(days[i], policy, control, calendar, limits, o["start"], binding) == o,
                "OUTCOME_REPLAY_MISMATCH",
            )
        require(
            o["reports"]["readiness.json"]["binding"]["software"] == scheduler_software(),
            "SOFTWARE_BINDING",
        )
        result.append(o)
        last, previous = o["end"], sha(canonical(rec))
    summary = source["summary"]
    require(summary["state"] == last and summary["head"] == previous, "SUMMARY_STATE")
    require(
        summary["complete"] == (len(result) == len(days) and all(o["complete"] for o in result)),
        "FALSE_COMPLETION",
    )
    require(
        len(summary["sessions"]) == len(result)
        and summary["daily"] == [dict(date=o["binding"]["date"], **o["daily"]) for o in result],
        "SUMMARY_LEDGER",
    )
    for i, (session, o) in enumerate(zip(summary["sessions"], result, strict=True)):
        require(
            session
            == dict(
                date=o["binding"]["date"],
                complete=o["complete"],
                starting_state=o["start"],
                closing_state=o["end"],
                head=sha(canonical(records[i])),
            ),
            "SUMMARY_SESSION",
        )
    alerts = [a for record in records for a in record["value"]["alerts"]]
    require(alerts == summary["alerts"], "ALERT_LEDGER")
    require(len({a["alert_id"] for a in alerts}) == len(alerts), "DUPLICATE_ALERT")
    for a in alerts:
        require(
            sha(canonical({k: v for k, v in a.items() if k != "alert_id"})) == a["alert_id"],
            "ALERT_HASH",
        )
    require(
        report_views(summary, limits) == source["reports"]
        and summary["reports"] == {k: sha(canonical(v)) for k, v in source["reports"].items()},
        "REPORT_HASH",
    )
    return result


def acknowledgment_view(alerts: list[dict[str, Any]], rows: list[dict[str, Any]]) -> dict[str, Any]:
    """Existing acknowledgment envelopes do not establish incident resolution."""
    index = {a["alert_id"]: a for a in alerts}
    seen: set[str] = set()
    previous = "0" * 64
    delays = []
    for envelope in rows:
        row = envelope["value"]
        require(
            sha(canonical(row)) == envelope["fingerprint"] and row["previous"] == previous,
            "ACK_CHAIN",
        )
        identifier = row["alert_id"]
        require(
            identifier in index
            and identifier not in seen
            and row["status"] == "ACKNOWLEDGED"
            and row["resume_authorized"] is False,
            "ACK_INVALID",
        )
        event = index[identifier]
        at = datetime.fromisoformat(row["timestamp"])
        start = datetime.fromisoformat(event["timestamp"])
        require(
            at.tzinfo is not None and at >= start and row["run_id"] == event["run_id"], "ACK_TIME"
        )
        delta = at - start
        delays.append(
            dict(
                alert_id=identifier,
                seconds=str(
                    D(delta.days * 86400 + delta.seconds) + D(delta.microseconds) / D(1000000)
                ),
            )
        )
        seen.add(identifier)
        previous = sha(canonical(envelope))
    return dict(
        acknowledged=len(seen), delays=delays, resolved_incidents=0, resume_authorized=False
    )


def readiness(at: str, evidence: str, complete: bool) -> dict[str, Any]:
    def row(status: str, role: str, gaps: list[str], action: str) -> dict[str, Any]:
        return dict(
            status=status,
            evidence=evidence,
            evidence_timestamp=at,
            responsible_role=role,
            owner_approval=None,
            missing_prerequisites=gaps,
            next_action=action,
        )

    return dict(
        engineering=row(
            "VALIDATED_OFFLINE" if complete else "PARTIAL",
            "Engineering reviewer",
            ["Independent durability and rollback authority"],
            "Review offline validation receipts; no production guarantee",
        ),
        genuine_strategy=row(
            "BLOCKED",
            "Research and data owner",
            [
                "Genuine mappings",
                "Authorized historical data",
                "Calendar provenance",
                "Corporate actions",
                "Validated costs",
                "OOS qualification and coverage",
            ],
            "Obtain separately authorized evidence; preserve frozen qualification",
        ),
        operational=row(
            "BLOCKED",
            "Accountable owner and compliance/security reviewers",
            [
                "Terms clearance",
                "Gate A",
                "Gate B",
                "Credential custody",
                "Production isolation",
                "Independent authority",
                "External logging",
                "Approved capital/risk",
                "Broker entitlement",
                "Owner approval",
            ],
            "Separate explicit approvals required; remain NO_GO",
        ),
    )


def analyze(source: dict[str, Any], expected_hash: str) -> dict[str, Any]:
    require(sha(canonical(source)) == expected_hash, "SOURCE_HASH")
    require(len(canonical(source)) <= 64 * 1024 * 1024, "INPUT_BUDGET")
    with localcontext(PRECISION):
        return _analyze(source, validate(source), expected_hash)


def _analyze(
    source: dict[str, Any], outcomes: list[dict[str, Any]], fingerprint: str
) -> dict[str, Any]:
    universe = symbols()
    strategies = [f"S{i:02}" for i in range(1, 7)]
    opening = number(outcomes[0]["start"]["equity"])
    require(opening > 0, "INVALID_CAPITAL")

    def empty() -> dict[str, Any]:
        return dict(
            trades=0,
            wins=0,
            losses=0,
            gross=D(0),
            fees=D(0),
            net=D(0),
            turnover=D(0),
            rejected_signals=0,
            risk_rejections=0,
            peak_cost_basis=D(0),
            halt_involvement=0,
        )

    by_symbol, by_strategy = {s: empty() for s in universe}, {s: empty() for s in strategies}
    daily: list[dict[str, Any]] = []
    curve: list[dict[str, Any]] = []
    returns: list[Decimal] = []
    trades: list[Decimal] = []
    seen: set[str] = set()
    high, max_dd, max_pct = opening, D(0), D(0)
    components = {name: D(0) for name in Charges.model_fields}
    recovery_start: str | None = None
    recovered: list[dict[str, str]] = []
    for day_index, o in enumerate(outcomes):
        reports = o["reports"]
        requests = source["input"]["days"][day_index]["requests"]
        schedules = {
            (
                r["data"]["series"]["symbol"],
                r["definition"]["identifier"],
            ): IntradayCostSchedule.model_validate(r["costs"])
            for r in requests
        }
        for snapshot in reports["cash-ledger.json"]:
            account = snapshot["account"]
            equity = number(account["equity"])
            require(
                equity == number(account["cash"]) + number(account["marked_value"]),
                "EQUITY_RECONCILIATION",
            )
            require(
                number(account["available"])
                == number(account["cash"]) - number(account["reserved"]),
                "RESERVATION_RECONCILIATION",
            )
            if equity >= high:
                if recovery_start:
                    recovered.append(dict(start=recovery_start, recovered_at=snapshot["timestamp"]))
                recovery_start = None
                high = equity
            elif recovery_start is None:
                recovery_start = snapshot["timestamp"]
            dd = high - equity
            max_dd, max_pct = max(max_dd, dd), max(max_pct, dd / high)
            curve.append(
                dict(
                    timestamp=snapshot["timestamp"],
                    equity=str(equity),
                    peak=str(high),
                    drawdown=str(dd),
                    drawdown_fraction=str(dd / high),
                )
            )
            exposures = {s: D(0) for s in strategies}
            for position in account["positions"].values():
                sym, strategy = position["symbol"], position["strategy_id"]
                amount = number(position["entry"]) * number(position["quantity"])
                by_symbol[sym]["peak_cost_basis"] = max(by_symbol[sym]["peak_cost_basis"], amount)
                exposures[strategy] += amount
            for s, amount in exposures.items():
                by_strategy[s]["peak_cost_basis"] = max(by_strategy[s]["peak_cost_basis"], amount)
        closed_net = D(0)
        for order in reports["orders.json"]:
            require(order["order_id"] not in seen, "DUPLICATE_ORDER")
            seen.add(order["order_id"])
            sym, strategy = order["symbol"], order["strategy_id"]
            require(sym in by_symbol and strategy in by_strategy, "UNKNOWN_ATTRIBUTION")
            if order["entry_at"] is not None:
                schedule = schedules[(sym, strategy)]
                notional = number(order["entry"]) * number(order["quantity"])
                entry_charge = schedule.quote(
                    "BUY", notional, datetime.fromisoformat(order["entry_at"]), None
                )
                require(entry_charge.total == number(order["entry_fee"]), "ENTRY_FEE_COMPONENTS")
                for name, amount in entry_charge.model_dump().items():
                    components[name] += amount
                if order["state"] == "CLOSED":
                    exit_charge = schedule.quote(
                        "SELL",
                        number(order["exit"]) * number(order["quantity"]),
                        datetime.fromisoformat(order["exit_at"]),
                        notional,
                    )
                    for name, amount in exit_charge.model_dump().items():
                        components[name] += amount
            if order["state"] != "CLOSED":
                continue
            quantity = number(order["quantity"])
            gross = (number(order["exit"]) - number(order["entry"])) * quantity
            net = number(order["net"])
            fees = gross - net
            require(fees >= 0, "NEGATIVE_FEES")
            turnover = (number(order["exit"]) + number(order["entry"])) * quantity
            trades.append(net)
            closed_net += net
            for row in (by_symbol[sym], by_strategy[strategy]):
                row["trades"] += 1
                row["wins"] += int(net > 0)
                row["losses"] += int(net < 0)
                for key, val in (
                    ("gross", gross),
                    ("fees", fees),
                    ("net", net),
                    ("turnover", turnover),
                ):
                    row[key] += val
        require(closed_net == number(o["daily"]["realized"]), "TRADE_RECONCILIATION")
        for position in o["end"]["positions"].values():
            for row in (by_symbol[position["symbol"]], by_strategy[position["strategy_id"]]):
                row["fees"] += number(position["entry_fee"])
                row["turnover"] += number(position["entry"]) * number(position["quantity"])
                row["halt_involvement"] += int(o["end"]["halted"])
        for event in reports["risk-rejections.json"]:
            if (
                event["state"] == "REJECTED"
                and event["symbol"] in by_symbol
                and event["strategy_id"] in by_strategy
            ):
                for row in (by_symbol[event["symbol"]], by_strategy[event["strategy_id"]]):
                    row["rejected_signals"] += 1
                    row["risk_rejections"] += int(
                        event["details"].get("reason")
                        in {
                            "CASH_EXPOSURE_LIQUIDITY_OR_RISK",
                            "ONE_POSITION_PER_SYMBOL",
                            "POSITION_OR_CORRELATION_LIMIT",
                        }
                    )
        start, end = number(o["start"]["equity"]), number(o["end"]["equity"])
        require(start > 0, "INVALID_CAPITAL")
        ret = (end - start) / start
        if o["complete"]:
            returns.append(ret)
        daily.append(
            dict(
                date=o["binding"]["date"],
                complete=o["complete"],
                opening=str(start),
                closing=str(end),
                net=str(end - start),
                return_fraction=str(ret),
                realized=o["daily"]["realized"],
                unrealized=o["daily"]["unrealized"],
                fees=o["daily"]["fees"],
            )
        )
    end = outcomes[-1]["end"]
    fees = sum((number(o["daily"]["fees"]) for o in outcomes), D(0))
    require(sum(components.values(), D(0)) == fees, "FEE_COMPONENT_RECONCILIATION")
    realized = sum(trades, D(0))
    require(
        realized == number(end["cumulative_net"]) and fees == number(end["cumulative_fees"]),
        "CUMULATIVE_RECONCILIATION",
    )
    for table in (by_symbol, by_strategy):
        require(
            sum((r["net"] for r in table.values()), D(0)) == realized
            and sum((r["fees"] for r in table.values()), D(0)) == fees,
            "ATTRIBUTION_RECONCILIATION",
        )

    def table_rows(table: dict[str, Any], key: str) -> list[dict[str, Any]]:
        return [
            dict(
                **{key: name},
                **{k: str(v) if isinstance(v, Decimal) else v for k, v in row.items()},
                contribution_to_opening_capital=str(row["net"] / opening),
                data_status="SYNTHETIC_OBSERVED" if row["turnover"] else "NO_SIMULATED_TRADES",
                slippage=unavailable("NO_SEPARATE_REFERENCE_PRICE_LEDGER"),
            )
            for name, row in table.items()
        ]

    wins, losses = [v for v in trades if v > 0], [v for v in trades if v < 0]
    turnover = sum((r["turnover"] for r in by_strategy.values()), D(0))
    alerts = source["summary"]["alerts"]
    acknowledgments = acknowledgment_view(alerts, source.get("acknowledgments", []))
    incidents = [a for a in alerts if a["severity"] in ("ERROR", "CRITICAL")]
    at = outcomes[-1]["reports"]["session-events.json"][-1]["timestamp"]
    complete = source["summary"]["complete"]
    ready = readiness(at, fingerprint, complete)
    tariffs = [r["costs"] for d in source["input"]["days"] for r in d["requests"]]
    return dict(
        schema=SCHEMA,
        scope="SYNTHETIC_FIXTURE",
        source_hash=fingerprint,
        software_hash=sha(canonical(inventory())),
        metric_policy=METRICS,
        metric_policy_hash=sha(canonical(METRICS)),
        qualification_policy=frozen_policy().model_dump(mode="json"),
        qualification_policy_hash=sha(canonical(frozen_policy().model_dump(mode="json"))),
        family_size=13380,
        genuine_qualified=0,
        qualification_comparison="NOT_COMPARABLE_SYNTHETIC",
        policy_bindings=dict(
            risk=source["input"]["control"]["policy_hash"],
            risk_version=source["input"]["policy"]["fingerprint_version"],
            execution=source["input"]["days"][0]["config"]["execution_hash"],
            execution_version=source["input"]["days"][0]["config"]["execution_version"],
        ),
        portfolio=dict(
            opening=str(opening),
            closing_equity=end["equity"],
            closing_cash=end["cash"],
            cumulative_net=str(number(end["equity"]) - opening),
            realized=str(realized),
            cumulative_return=str(number(end["equity"]) / opening - 1),
            peak_equity=str(high),
            maximum_drawdown=str(max_dd),
            drawdown_fraction=str(max_pct),
            winning_sessions=sum(r > 0 for r in returns),
            losing_sessions=sum(r < 0 for r in returns),
            average_session_return=metric(sum(returns, D(0)) / len(returns))
            if returns
            else unavailable("NO_COMPLETED_SESSION"),
            exposure_adjusted_return=unavailable("NO_TIME_WEIGHTED_EXPOSURE_CONTRACT"),
        ),
        daily=daily,
        equity_drawdown=curve,
        strategy_attribution=table_rows(by_strategy, "strategy_id"),
        symbol_attribution=table_rows(by_symbol, "symbol"),
        cost=dict(
            fees=str(fees),
            turnover=str(turnover),
            synthetic_tariff_configurations=tariffs,
            tariff_hash=sha(canonical(tariffs)),
            components={k: str(v) for k, v in components.items()},
            component_method="REUSED_FROZEN_TARIFF_QUOTE_RECONCILED_TO_ACCEPTED_FILLS",
            slippage=unavailable("NO_SEPARATE_REFERENCE_PRICE_LEDGER"),
            average_per_completed_trade=metric(
                sum((r["gross"] - r["net"] for r in by_strategy.values()), D(0)) / len(trades)
            )
            if trades
            else unavailable("NO_COMPLETED_TRADE"),
            fraction_of_turnover=metric(fees / turnover)
            if turnover
            else unavailable("ZERO_TURNOVER"),
        ),
        risk_adjusted=risk_metrics(returns, complete, max_pct),
        trades=dict(
            count=len(trades),
            wins=len(wins),
            losses=len(losses),
            breakeven=len(trades) - len(wins) - len(losses),
            win_rate=metric(D(len(wins)) / len(trades)) if trades else unavailable("NO_TRADES"),
            expectancy=metric(realized / len(trades)) if trades else unavailable("NO_TRADES"),
            profit_factor=metric(sum(wins, D(0)) / -sum(losses, D(0)))
            if losses
            else unavailable("NO_LOSSES"),
            average_win=metric(sum(wins, D(0)) / len(wins)) if wins else unavailable("NO_WINS"),
            average_loss=metric(sum(losses, D(0)) / len(losses))
            if losses
            else unavailable("NO_LOSSES"),
        ),
        monitoring=dict(
            effectiveness="NOT_EVALUATED",
            reason="NO_INDEPENDENT_GROUND_TRUTH",
            generated_alerts=len(alerts),
            observed_reason_counts={
                reason: sum(a["reason"] == reason for a in alerts)
                for reason in sorted({a["reason"] for a in alerts})
            },
            duplicate_alerts=0,
            expected_alerts=None,
            missed_alerts=None,
            false_positives=None,
            false_negatives=None,
            detection_delay=None,
            acknowledgment_delay=None,
            resolution_delay=None,
            acknowledged=acknowledgments["acknowledged"],
            acknowledgment_evidence=acknowledgments,
            detected_incidents=len(incidents),
            resolved_incidents=0,
            unresolved_incidents=len(incidents),
            halted=end["halted"],
            incomplete_sessions=sum(not o["complete"] for o in outcomes),
            unprocessed_sessions=len(source["input"]["days"]) - len(outcomes),
            completion_rate=str(
                D(sum(o["complete"] for o in outcomes)) / len(source["input"]["days"])
            ),
        ),
        recovery=dict(
            local_outcome_replay="IDENTICAL",
            independently_rollback_resistant=False,
            production_durability="NOT_EVALUATED",
            recovered_drawdowns=recovered,
            unresolved_drawdown_since=recovery_start,
            recovery_duration="NOT_COMPUTABLE_UNRECOVERED"
            if recovery_start
            else "SEE_OBSERVED_RECOVERY_INTERVALS",
        ),
        readiness=ready,
        gaps=[dict(category=k, **v) for k, v in ready.items()],
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
    )


def run(path: Path, expected_raw_hash: str, directory: Path) -> dict[str, Any]:
    require(path.is_file() and path.stat().st_size <= 64 * 1024 * 1024, "INPUT_BUDGET")
    raw = path.read_bytes()
    require(sha(raw) == expected_raw_hash, "SOURCE_HASH")
    package = json.loads(raw)
    require(
        package["scope"] == "SYNTHETIC_FIXTURE"
        and package["software_hash"] == sha(canonical(software())),
        "SOURCE_BINDING",
    )
    output = {
        name: analyze(s, sha(canonical(s))) for name, s in sorted(package["scenarios"].items())
    }
    directory = external(directory)
    content = canonical(output)
    require(len(content) <= 64 * 1024 * 1024, "OUTPUT_BUDGET")
    publish(directory / "analytics.json", content)
    receipt = dict(
        schema=SCHEMA,
        source_hash=expected_raw_hash,
        report_hash=sha(content),
        software_hash=sha(canonical(inventory())),
    )
    publish(directory / "completion.json", canonical(receipt))
    return receipt
