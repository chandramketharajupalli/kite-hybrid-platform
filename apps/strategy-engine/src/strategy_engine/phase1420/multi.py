"""Immutable session outcomes, explicit carry-forward and offline health events."""

import json
import os
from datetime import datetime
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any, Literal

from pydantic import Field

from strategy_engine.backtest.dataset import NSE, Frozen, Hash, Money
from strategy_engine.phase14.engine import PRECISION
from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, external, publish, sha
from strategy_engine.phase1418.portfolio import Control, Policy, Request
from strategy_engine.phase1419.scheduler import (
    Calendar,
    Command,
    Configuration,
    Engine,
    Portfolio,
)
from strategy_engine.phase1419.scheduler import (
    software as scheduler_software,
)

D = Decimal
ZERO = "0" * 64
SCHEMA = "Phase1420MultiSession.v1"


class Day(Frozen):
    requests: tuple[Request, ...] = Field(min_length=1, max_length=6690)
    config: Configuration
    commands: tuple[Command, ...] = Field(min_length=1, max_length=1000)


class Limits(Frozen):
    schema_version: Literal["Phase1420SyntheticLimits.v1"] = "Phase1420SyntheticLimits.v1"
    scope: Literal["SYNTHETIC_FIXTURE"] = "SYNTHETIC_FIXTURE"
    cumulative_drawdown: Money | None = Field(default=None, gt=0)
    daily_reset_authorization: Hash | None = None


def software() -> dict[str, str]:
    directory = ROOT / "apps/strategy-engine/src/strategy_engine/phase1420"
    files = sorted(directory.glob("*.py"))
    if {p.name for p in files} != {"__init__.py", "multi.py"}:
        raise ValueError("SOFTWARE_INVENTORY")
    return dict(
        sorted(
            (
                scheduler_software()
                | {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in files}
            ).items()
        )
    )


def load(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise ValueError("REPORT_MISSING")
    if path.stat().st_size > 64 * 1024 * 1024:
        raise ValueError("RESOURCE_BUDGET")
    value: dict[str, Any] = json.loads(path.read_bytes())
    if not isinstance(value, dict):
        raise ValueError("JOURNAL_CORRUPTED")
    return value


def envelope(value: dict[str, Any]) -> bytes:
    return canonical(dict(fingerprint=sha(canonical(value)), value=value))


def unpack(path: Path) -> dict[str, Any]:
    e = load(path)
    if set(e) != {"fingerprint", "value"} or sha(canonical(e["value"])) != e["fingerprint"]:
        raise ValueError("JOURNAL_CORRUPTED")
    value: dict[str, Any] = e["value"]
    return value


def alert(
    run_id: str, day: str, at: str, code: str, evidence: str, severity: str = "CRITICAL"
) -> dict[str, Any]:
    if severity not in {"INFO", "WARNING", "ERROR", "CRITICAL"}:
        raise ValueError("INVALID_ALERT_SEVERITY")
    row = dict(
        run_id=run_id,
        session_date=day,
        timestamp=at,
        severity=severity,
        source="phase1420",
        reason=code,
        evidence=evidence,
        acknowledgment="UNACKNOWLEDGED",
    )
    return dict(alert_id=sha(canonical(row)), **row)


class Rejected(ValueError):
    def __init__(self, notification: dict[str, Any]) -> None:
        super().__init__(notification["reason"])
        self.alert = notification


class GuardedPortfolio(Portfolio):
    high_water: Decimal = D(0)
    maximum_drawdown: Decimal = D(0)
    cumulative_limit: Decimal = D(0)

    def emit(
        self, key: str, state: str, at: datetime, request: Request | None = None, **values: Any
    ) -> None:
        super().emit(key, state, at, request, **values)
        if state in {"FILLED", "CLOSED"}:
            self.guard(at)

    def guard(self, at: datetime) -> None:
        super().guard(at)
        equity = D(self.account()["equity"])
        self.high_water = max(self.high_water, equity)
        self.maximum_drawdown = max(self.maximum_drawdown, self.high_water - equity)
        if self.high_water - equity >= self.cumulative_limit:
            self.halt(at, "CUMULATIVE_DRAWDOWN_BREACH")


def execute(
    day: Day,
    policy: Policy,
    control: Control,
    calendar: Calendar,
    limits: Limits,
    start: dict[str, Any],
    binding: dict[str, Any],
) -> dict[str, Any]:
    """Only uncommitted sessions enter this function; reuse scheduler decisions."""
    engine = Engine(day.requests, policy, control, calendar, day.config)
    engine.binding = engine.binding | {"multi_session": binding, "starting_state": start}
    engine.identifier = sha(canonical(engine.binding))
    portfolio = GuardedPortfolio(engine.binding, policy)
    portfolio.cash = portfolio.opening = portfolio.day_equity = D(start["cash"])
    portfolio.day = day.config.trading_date.isoformat()
    portfolio.high_water = D(start["high_water"])
    portfolio.maximum_drawdown = D(start["maximum_drawdown"])
    assert limits.cumulative_drawdown is not None
    portfolio.cumulative_limit = limits.cumulative_drawdown
    engine.portfolio = portfolio
    if start["halted"] or start["positions"] or start["reservations"]:
        raise ValueError("UNRESOLVED_PRIOR_SESSION")
    for command in day.commands:
        engine.apply(command)
    if engine.state != "COMPLETED":
        if engine.state != "PAPER_HALTED":
            engine.halt("INCOMPLETE_SESSION", day.commands[-1].at)
    account = portfolio.account()
    completed = (
        engine.state == "COMPLETED" and not account["positions"] and not account["reservations"]
    )
    end = dict(
        cash=account["cash"],
        equity=account["equity"],
        cumulative_net=str(D(start["cumulative_net"]) + D(account["realized"])),
        cumulative_fees=str(D(start["cumulative_fees"]) + D(account["fees"])),
        high_water=str(portfolio.high_water),
        maximum_drawdown=str(portfolio.maximum_drawdown),
        positions=account["positions"],
        reservations=account["reservations"],
        halted=not completed or account["halted"],
    )
    if D(end["equity"]) != D(start["equity"]) + D(account["realized"]) + D(account["unrealized"]):
        raise ValueError("PORTFOLIO_RECONCILIATION")
    return dict(
        binding=binding,
        start=start,
        end=end,
        complete=completed,
        daily=account,
        reports=engine.output(),
        session_id=engine.identifier,
    )


def records(directory: Path) -> list[dict[str, Any]]:
    paths = sorted((directory / "records").glob("*"))
    if [p.name for p in paths] != [f"{i:04}.json" for i in range(len(paths))]:
        raise ValueError("SESSION_RECORD_MISSING")
    result: list[dict[str, Any]] = []
    previous = ZERO
    for p in paths:
        row = unpack(p)
        if row["previous"] != previous or row["sequence"] != len(result):
            raise ValueError("JOURNAL_CORRUPTED")
        previous = sha(p.read_bytes())
        result.append(row)
    return result


def head(directory: Path) -> str:
    rows = records(directory)
    return sha((directory / "records" / f"{len(rows) - 1:04}.json").read_bytes()) if rows else ZERO


def run(
    days: tuple[Day, ...],
    policy: Policy,
    control: Control,
    calendar: Calendar,
    limits: Limits,
    directory: Path,
    *,
    resume: bool = False,
    verify_only: bool = False,
    expected_head: str | None = None,
    crash: str | None = None,
) -> dict[str, Any]:
    directory = external(directory)
    binding = dict(
        schema=SCHEMA,
        days=[sha(canonical(d.model_dump(mode="json"))) for d in days],
        calendar=calendar.fingerprint,
        policy=policy.fingerprint,
        policy_version=policy.fingerprint_version,
        control=control.model_dump(mode="json"),
        limits=limits.model_dump(mode="json"),
        software=software(),
        scheduler_software=sha(canonical(scheduler_software())),
    )
    run_id = sha(canonical(binding))
    try:
        with localcontext(PRECISION):
            return _run(
                days,
                policy,
                control,
                calendar,
                limits,
                directory,
                binding,
                run_id,
                resume,
                verify_only,
                expected_head,
                crash,
            )
    except InterruptedError:
        raise
    except (ValueError, KeyError, OSError) as exc:
        notification = alert(
            run_id,
            str(days[0].config.trading_date) if days else "UNAVAILABLE",
            days[0].commands[0].at.isoformat() if days else "UNAVAILABLE",
            str(exc),
            sha(canonical(binding)),
        )
        raise Rejected(notification) from exc


def _run(
    days: tuple[Day, ...],
    policy: Policy,
    control: Control,
    calendar: Calendar,
    limits: Limits,
    directory: Path,
    binding: dict[str, Any],
    run_id: str,
    resume: bool,
    verify_only: bool,
    expected_head: str | None,
    crash: str | None,
) -> dict[str, Any]:
    if not days or len(days) > 64 or not calendar.sessions or len(calendar.sessions) > 64:
        raise ValueError("RESOURCE_BUDGET")
    if not limits.cumulative_drawdown or not limits.daily_reset_authorization:
        raise ValueError("RISK_POLICY_GAP")
    if (
        sum(
            sum(
                len(r.data.series.bars)
                for r in {r.data.series.symbol: r for r in d.requests}.values()
            )
            for d in days
        )
        > 200000
    ):
        raise ValueError("RESOURCE_BUDGET")
    dates = [d.config.trading_date for d in days]
    if len(set(dates)) != len(dates):
        raise ValueError("SESSION_DUPLICATE")
    if dates != sorted(dates):
        raise ValueError("SESSION_OUT_OF_ORDER")
    expected = [s.start.astimezone(NSE).date() for s in calendar.sessions]
    if set(dates) - set(expected):
        raise ValueError("CALENDAR_MISMATCH")
    directory.mkdir(parents=True, exist_ok=True)
    plan = directory / "plan.json"
    if plan.exists() and plan.read_bytes() != canonical(binding):
        raise ValueError("POLICY_DATASET_SOFTWARE_BINDING_MISMATCH")
    if (directory / "summary.json").exists() and not verify_only:
        raise ValueError("TERMINAL_RUN_NO_AUTOMATIC_RESUME")
    prior = records(directory)
    if (prior or plan.exists()) and not (resume or verify_only):
        raise ValueError("EXPLICIT_RECOVERY_REQUIRED")
    if (resume or verify_only) and (expected_head is None or expected_head != head(directory)):
        raise ValueError("EXTERNAL_HEAD_MISMATCH")
    if verify_only and not (directory / "summary.json").is_file():
        raise ValueError("REPORT_MISSING")
    if (directory / "owner.lock").exists() or list(directory.rglob("*.pending")):
        raise ValueError("AMBIGUOUS_OWNER")
    fd = None
    if not verify_only:
        fd = os.open(directory / "owner.lock", os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        if not verify_only:
            publish(plan, canonical(binding))
        if crash == "before_first":
            raise InterruptedError(crash)
        state = dict(
            cash=str(policy.base.capital or 0),
            equity=str(policy.base.capital or 0),
            cumulative_net="0",
            cumulative_fees="0",
            high_water=str(policy.base.capital or 0),
            maximum_drawdown="0",
            positions={},
            reservations={},
            halted=False,
        )
        accepted = []
        notifications = []
        daily = []
        previous = ZERO
        strategies: dict[str, Decimal] = {}
        symbols: dict[str, Decimal] = {}
        total_bytes = 0
        for i, trading_date in enumerate(expected):
            if state["halted"]:
                break
            d = next((d for d in days if d.config.trading_date == trading_date), None)
            session_binding = dict(
                run_id=run_id,
                sequence=i,
                date=str(trading_date),
                previous=previous,
                starting_state=sha(canonical(state)),
                calendar=calendar.fingerprint,
                day=sha(canonical(d.model_dump(mode="json"))) if d else None,
            )
            path = directory / "outcomes" / f"{i:04}.json"
            record_path = directory / "records" / f"{i:04}.json"
            if i < len(prior) and not path.is_file():
                raise ValueError("REPORT_MISSING")
            if path.exists():
                outcome = unpack(path)
                if outcome["binding"] != session_binding or outcome["start"] != state:
                    raise ValueError("CARRY_FORWARD_MISMATCH")
            elif verify_only:
                raise ValueError("REPORT_MISSING")
            else:
                if d is None:
                    outcome = dict(
                        binding=session_binding,
                        start=state,
                        end=state | {"halted": True},
                        complete=False,
                        daily=None,
                        reports={},
                        session_id=None,
                    )
                else:
                    outcome = execute(d, policy, control, calendar, limits, state, session_binding)
                raw = envelope(outcome)
                if len(raw) > 64 * 1024 * 1024 or total_bytes + len(raw) > 256 * 1024 * 1024:
                    raise ValueError("RESOURCE_BUDGET")
                publish(path, raw)
            total_bytes += path.stat().st_size
            if total_bytes > 256 * 1024 * 1024:
                raise ValueError("RESOURCE_BUDGET")
            if crash == "before_record" and i == 0:
                raise InterruptedError(crash)
            at = calendar.sessions[i].end.isoformat()
            alerts = []
            if d:
                alerts.append(
                    alert(
                        run_id,
                        str(trading_date),
                        d.commands[0].at.isoformat(),
                        "SESSION_STARTED",
                        sha(canonical(session_binding)),
                        "INFO",
                    )
                )
            code = (
                "SESSION_COMPLETED"
                if outcome["complete"]
                else "SESSION_MISSING"
                if not d
                else "PAPER_HALT_TRIGGERED"
            )
            alerts.append(
                alert(
                    run_id,
                    str(trading_date),
                    at,
                    code,
                    sha(path.read_bytes()),
                    "INFO" if outcome["complete"] else "CRITICAL",
                )
            )
            row = dict(
                sequence=i,
                previous=previous,
                binding=session_binding,
                outcome_hash=sha(path.read_bytes()),
                alerts=alerts,
            )
            raw_record = envelope(row)
            if i < len(prior):
                if record_path.read_bytes() != raw_record:
                    raise ValueError("JOURNAL_CORRUPTED")
            elif verify_only:
                raise ValueError("SESSION_RECORD_MISSING")
            else:
                publish(record_path, raw_record)
            if crash in {"after_record", "during_carry", "after_completed"} and i == 0:
                raise InterruptedError(crash)
            previous = sha(raw_record)
            state = outcome["end"]
            accepted.append(
                dict(
                    date=str(trading_date),
                    complete=outcome["complete"],
                    head=previous,
                    starting_state=outcome["start"],
                    closing_state=state,
                )
            )
            notifications.extend(alerts)
            if outcome["daily"]:
                daily.append(dict(date=str(trading_date), **outcome["daily"]))
                for target, name in (
                    (strategies, "strategy-pnl.json"),
                    (symbols, "symbol-pnl.json"),
                ):
                    for key, value in outcome["reports"][name].items():
                        target[key] = target.get(key, D(0)) + D(value)
        if len(prior) > len(accepted):
            raise ValueError("UNEXPECTED_SESSION_RECORD")
        if len(list((directory / "outcomes").glob("*"))) != len(accepted):
            raise ValueError("UNEXPECTED_SESSION_OUTCOME")
        report = dict(
            schema=SCHEMA,
            scope="SYNTHETIC_FIXTURE",
            run_id=run_id,
            head=previous,
            complete=not state["halted"] and len(accepted) == len(expected),
            state=state,
            sessions=accepted,
            daily=daily,
            alerts=notifications,
            strategies={k: str(v) for k, v in sorted(strategies.items())},
            symbols={k: str(v) for k, v in sorted(symbols.items())},
            winning_sessions=sum(D(d["realized"]) > 0 for d in daily),
            losing_sessions=sum(D(d["realized"]) < 0 for d in daily),
            genuine_qualified=0,
            operational="NO_GO",
            Gate_A="NOT_AUTHORIZED",
            Gate_B="NOT_AUTHORIZED",
        )
        views = report_views(report, limits)
        report["reports"] = {n: sha(canonical(v)) for n, v in views.items()}
        for name, view in views.items():
            payload = canonical(view)
            if verify_only:
                if not (directory / name).is_file() or (directory / name).read_bytes() != payload:
                    raise ValueError("REPORT_MISSING_OR_CHANGED")
            else:
                publish(directory / name, payload)
        raw = canonical(report)
        if verify_only:
            if (directory / "summary.json").read_bytes() != raw:
                raise ValueError("REPORT_REPLAY_MISMATCH")
        else:
            publish(directory / "summary.json", raw)
        return report
    finally:
        if fd is not None:
            os.close(fd)
            (directory / "owner.lock").unlink()


def report_views(report: dict[str, Any], limits: Limits) -> dict[str, Any]:
    sessions = report["sessions"]
    return {
        "session-status.json": sessions,
        "daily-pnl.json": report["daily"],
        "equity-curve.json": [
            dict(date=s["date"], equity=s["closing_state"]["equity"]) for s in sessions
        ],
        "drawdown.json": [
            dict(
                date=s["date"],
                high_water=s["closing_state"]["high_water"],
                maximum_drawdown=s["closing_state"]["maximum_drawdown"],
            )
            for s in sessions
        ],
        "strategy-pnl.json": report["strategies"],
        "symbol-pnl.json": report["symbols"],
        "risk-health.json": dict(
            limits=limits.model_dump(mode="json"),
            halted=report["state"]["halted"],
            reconciled=True,
            complete=report["complete"],
            scope="SYNTHETIC_FIXTURE",
        ),
        "cross-session-halt.json": [s for s in sessions if s["closing_state"]["halted"]],
        "missing-sessions.json": [a for a in report["alerts"] if a["reason"] == "SESSION_MISSING"],
        "alerts.json": report["alerts"],
        "carry-forward.json": sessions,
        "recovery.json": dict(
            head=report["head"],
            completed_sessions_reexecuted=False,
            independent_rollback_resistance=False,
            external_anchor_required=True,
        ),
    }


def acknowledgments(directory: Path, expected_head: str | None = None) -> list[dict[str, Any]]:
    report = load(directory / "summary.json")
    ids = {a["alert_id"] for a in report["alerts"]}
    rows = []
    previous = ZERO
    seen = set()
    for i, p in enumerate(sorted((directory / "acks").glob("*"))):
        row = unpack(p)
        if (
            p.name != f"{i:04}.json"
            or row["previous"] != previous
            or row["alert_id"] not in ids
            or row["alert_id"] in seen
        ):
            raise ValueError("ACK_JOURNAL_CORRUPTED")
        seen.add(row["alert_id"])
        rows.append(row)
        previous = sha(p.read_bytes())
    if expected_head is not None and expected_head != previous:
        raise ValueError("ACK_EXTERNAL_HEAD_MISMATCH")
    return rows


def acknowledge(directory: Path, alert_id: str, at: datetime, evidence: str) -> dict[str, Any]:
    directory = external(directory)
    report = load(directory / "summary.json")
    found = [a for a in report["alerts"] if a["alert_id"] == alert_id]
    if (
        not found
        or at.tzinfo is None
        or at < datetime.fromisoformat(found[0]["timestamp"])
        or len(evidence) != 64
    ):
        raise ValueError("INVALID_ACKNOWLEDGMENT")
    rows = acknowledgments(directory)
    prior = next((r for r in rows if r["alert_id"] == alert_id), None)
    if prior:
        if prior["timestamp"] != at.isoformat() or prior["evidence"] != evidence:
            raise ValueError("ACK_CONFLICT")
        return prior
    row = dict(
        alert_id=alert_id,
        run_id=report["run_id"],
        timestamp=at.isoformat(),
        evidence=evidence,
        status="ACKNOWLEDGED",
        resume_authorized=False,
        previous=sha((directory / "acks" / f"{len(rows) - 1:04}.json").read_bytes())
        if rows
        else ZERO,
    )
    publish(directory / "acks" / f"{len(rows):04}.json", envelope(row))
    return row


def alert_view(directory: Path, expected_ack_head: str | None = None) -> list[dict[str, Any]]:
    rows = acknowledgments(directory, expected_ack_head)
    acknowledged = {r["alert_id"] for r in rows}
    report = load(directory / "summary.json")
    return [
        a
        | {"acknowledgment": "ACKNOWLEDGED" if a["alert_id"] in acknowledged else "UNACKNOWLEDGED"}
        for a in report["alerts"]
    ]
