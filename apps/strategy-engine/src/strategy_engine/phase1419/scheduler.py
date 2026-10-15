"""Explicit-clock synthetic sessions over existing strategy and portfolio components."""

import csv
import os
from datetime import date, datetime
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any, Literal

from pydantic import Field, model_validator

from strategy_engine.backtest.dataset import NSE, Frozen, Hash, Instant, Money, digest
from strategy_engine.phase14.engine import PRECISION, Candle, Session
from strategy_engine.phase1415.execution import (
    EXECUTION_VERSION,
    execution_fingerprint,
    exit_reference,
)
from strategy_engine.phase1415.hardening import ROOT, UNIVERSE
from strategy_engine.phase1415.paper import event
from strategy_engine.phase1415.strategies import entry_signal, registry
from strategy_engine.phase1416.imports import canonical, external, publish, read_json, sha
from strategy_engine.phase1417.paper import admission
from strategy_engine.phase1418.canonical_policy import VERSION
from strategy_engine.phase1418.portfolio import (
    Control,
    Coordinator,
    Policy,
    Request,
    inventory,
    reports,
)

RISK_HASH = "3dd7a1cf99042ce027011f6a99ca1f298ab62c6254bc85e584171250a925c7d1"
EXECUTION_HASH = "2078a10ecad7df0faef638e3e6e37eb4808c4a8c33af5fb81883b427d0ed5925"
SCHEMA = "Phase1419Session.v1"
LIMIT = 200000
MAX_BARS = 100000
TRANSITIONS = {
    "CREATED": {"PREMARKET_CHECK"},
    "PREMARKET_CHECK": {"READY"},
    "READY": {"SESSION_OPEN"},
    "SESSION_OPEN": {"RUNNING", "ENTRY_CUTOFF", "EOD_LIQUIDATION"},
    "RUNNING": {"ENTRY_CUTOFF", "EOD_LIQUIDATION"},
    "ENTRY_CUTOFF": {"EOD_LIQUIDATION"},
    "EOD_LIQUIDATION": {"RECONCILING"},
    "RECONCILING": {"COMPLETED"},
    "PAPER_HALTED": {"EOD_LIQUIDATION"},
    "FAILED": set(),
    "COMPLETED": set(),
    "RECOVERY_PENDING": {"CREATED", "READY", "SESSION_OPEN", "RUNNING", "ENTRY_CUTOFF"},
}


class Calendar(Frozen):
    version: str = Field(min_length=1)
    source_hash: Hash
    synthetic: Literal[True] = True
    sessions: tuple[Session, ...]
    holidays: tuple[date, ...] = ()

    @model_validator(mode="after")
    def valid(self) -> "Calendar":
        days = [s.start.astimezone(NSE).date() for s in self.sessions]
        if len(set(days)) != len(days) or set(days) & set(self.holidays):
            raise ValueError("CALENDAR_CONFLICT")
        if tuple(sorted(self.sessions, key=lambda s: s.start)) != self.sessions:
            raise ValueError("CALENDAR_ORDER")
        return self

    @property
    def fingerprint(self) -> str:
        return sha(canonical(self.model_dump(mode="json")))


class Configuration(Frozen):
    name: str = Field(min_length=1)
    trading_date: date
    cutoff: Instant
    cutoff_version: Literal["SYNTHETIC_CUTOFF.v1"] = "SYNTHETIC_CUTOFF.v1"
    authorization: Hash
    prior_session_resolved: bool = False
    risk_version: str = VERSION
    risk_hash: Hash = RISK_HASH
    execution_version: str = EXECUTION_VERSION
    execution_hash: Hash = EXECUTION_HASH


class Completed(Frozen):
    symbol: str
    instrument_token: str
    bar: Candle
    complete: bool = True


class Opening(Frozen):
    symbol: str
    instrument_token: str
    price: Money = Field(gt=0)


class Command(Frozen):
    kind: Literal["preflight", "open", "step", "close"]
    at: Instant
    completed: tuple[Completed, ...] = Field(default=(), max_length=1115)
    openings: tuple[Opening, ...] = Field(default=(), max_length=1115)


class Portfolio(Coordinator):
    crash: str | None = None

    def emit(
        self, key: str, state: str, at: datetime, request: Request | None = None, **values: Any
    ) -> None:
        super().emit(key, state, at, request, **values)
        if state == self.crash:
            raise InterruptedError("SYNTHETIC_CRASH:" + state)


def software() -> dict[str, str]:
    paths = ROOT / "apps/strategy-engine/src/strategy_engine/phase1419"
    if {p.name for p in paths.glob("*.py")} != {"__init__.py", "scheduler.py"}:
        raise ValueError("SOFTWARE_INVENTORY_CONFLICT")
    return dict(
        sorted(
            (
                inventory()
                | {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in paths.glob("*.py")}
            ).items()
        )
    )


class Engine:
    def __init__(
        self,
        requests: tuple[Request, ...],
        policy: Policy,
        control: Control,
        calendar: Calendar,
        config: Configuration,
        crash: str | None = None,
    ) -> None:
        if not requests or len(requests) > 6690:
            raise ValueError("SESSION_RESOURCE_LIMIT")
        symbols = [
            r["symbol"]
            for r in csv.DictReader(UNIVERSE.read_text(encoding="utf-8-sig").splitlines())
        ]
        if len(symbols) != 1115 or len(set(symbols)) != 1115:
            raise ValueError("UNIVERSE_CONFLICT")
        self.rank = {s: i for i, s in enumerate(symbols)}
        self.requests = tuple(
            sorted(
                requests,
                key=lambda r: (self.rank.get(r.data.series.symbol, 1115), r.definition.identifier),
            )
        )
        pairs = [(r.data.series.symbol, r.definition.identifier) for r in self.requests]
        if len(set(pairs)) != len(pairs):
            raise ValueError("DUPLICATE_PAIR")
        self.sources = {r.data.series.symbol: r for r in self.requests}
        if sum(len(r.data.series.bars) for r in self.sources.values()) > MAX_BARS:
            raise ValueError("SESSION_RESOURCE_LIMIT")
        if len(self.sources) > 1115 or any(
            r.data.dataset_sha256 != self.sources[r.data.series.symbol].data.dataset_sha256
            for r in requests
        ):
            raise ValueError("CONFLICTING_DATASET")
        self.policy, self.control, self.calendar, self.config = policy, control, calendar, config
        sessions = [
            s for s in calendar.sessions if s.start.astimezone(NSE).date() == config.trading_date
        ]
        self.session = sessions[0] if sessions else None
        self.binding = dict(
            schema=SCHEMA,
            config=config.model_dump(mode="json"),
            calendar=calendar.model_dump(mode="json"),
            calendar_hash=calendar.fingerprint,
            policy=policy.model_dump(mode="json"),
            risk_version=VERSION,
            risk_hash=policy.fingerprint,
            execution_version=EXECUTION_VERSION,
            execution_hash=execution_fingerprint(),
            control=control.model_dump(mode="json"),
            requests=[r.fingerprint for r in self.requests],
            qualifications=[r.qualification.fingerprint for r in self.requests],
            datasets={s: r.data.dataset_sha256 for s, r in self.sources.items()},
            registry=sha(canonical([d.model_dump(mode="json") for d in registry()])),
            universe=sha(UNIVERSE.read_bytes()),
            family_size=13380,
            software=software(),
            scope="SYNTHETIC_FIXTURE",
        )
        self.identifier = sha(canonical(self.binding))
        self.portfolio = Portfolio(self.binding, policy)
        self.portfolio.crash = crash
        self.crash = crash
        self.state = "CREATED"
        self.clock: datetime | None = None
        self.history: dict[str, tuple[Candle, ...]] = {s: () for s in self.sources}
        self.pending: dict[str, Request] = {}
        self.seen: set[tuple[str, datetime]] = set()
        self.audit: list[dict[str, Any]] = []
        self.transitions: list[dict[str, str]] = []
        self.gaps: list[str] = []
        self.signal_decisions: list[dict[str, Any]] = []

    def transition(self, state: str) -> None:
        if self.state in {"COMPLETED", "FAILED"}:
            raise ValueError("TERMINAL_SESSION")
        allowed = TRANSITIONS[self.state] | {"PAPER_HALTED", "FAILED"}
        if self.state not in {"COMPLETED", "PAPER_HALTED", "FAILED"}:
            allowed |= {"RECOVERY_PENDING"}
        if state not in allowed or state == self.state:
            raise ValueError("INVALID_SESSION_TRANSITION:" + self.state + ":" + state)
        self.transitions.append(dict(previous=self.state, state=state))
        self.state = state
        if self.crash == state:
            raise InterruptedError("SYNTHETIC_CRASH:" + state)

    def halt(self, reason: str, at: datetime) -> None:
        self.portfolio.halt(at, reason)
        if self.state != "PAPER_HALTED":
            self.transition("PAPER_HALTED")

    def readiness(self) -> list[str]:
        failures = list(self.policy.gaps(set(self.sources)))
        if self.session is None or self.config.trading_date in self.calendar.holidays:
            failures.append("NON_TRADING_DAY")
        elif not self.session.start < self.config.cutoff < self.session.end:
            failures.append("INVALID_CUTOFF")
        if not self.config.prior_session_resolved:
            failures.append("UNRESOLVED_PRIOR_SESSION")
        if (
            not self.control.enabled
            or self.control.paper_halt
            or self.config.authorization != self.control.authorization_evidence
        ):
            failures.append("PAPER_AUTHORIZATION_OR_HALT")
        if (
            self.config.risk_version != VERSION
            or self.policy.fingerprint_version != VERSION
            or self.control.policy_fingerprint_version != VERSION
            or self.config.risk_hash != RISK_HASH
            or self.policy.fingerprint != RISK_HASH
            or self.control.policy_hash != RISK_HASH
            or self.config.execution_version != EXECUTION_VERSION
            or self.config.execution_hash != EXECUTION_HASH
            or execution_fingerprint() != EXECUTION_HASH
        ):
            failures.append("POLICY_MISMATCH")
        for r in self.requests:
            failures.extend(
                admission(
                    r.qualification,
                    r.data,
                    r.definition,
                    r.settings,
                    self.policy.base,
                    r.authorization,
                )
            )
            if (
                r.authorization.mode != "SYNTHETIC_FIXTURE"
                or not self.policy.base.fixture_only
                or r.authorization.paper_halt
                or r.data.series.symbol not in self.rank
            ):
                failures.append("SYNTHETIC_ONLY_OR_AUTHORIZATION")
            if self.session and (
                r.data.series.sessions != (self.session,)
                or r.data.series.calendar_hash != self.calendar.source_hash
            ):
                failures.append("CALENDAR_BINDING")
            if r.definition != next(
                d for d in registry() if d.identifier == r.definition.identifier
            ):
                failures.append("STRATEGY_MUTATION")
        return sorted(set(failures))

    def apply(self, command: Command, recovered: bool = False) -> dict[str, Any]:
        with localcontext(PRECISION):
            return self._apply(command, recovered)

    def _apply(self, command: Command, recovered: bool) -> dict[str, Any]:
        if self.state in {"COMPLETED", "FAILED"}:
            raise ValueError("TERMINAL_SESSION")
        self.transitions = []
        begin = len(self.portfolio.events)
        if recovered:
            prior = self.state
            if prior in {"PAPER_HALTED", "FAILED", "COMPLETED"}:
                raise ValueError("RECOVERY_REJECTED")
            self.transition("RECOVERY_PENDING")
            self.transition(prior)
        at = command.at
        reason: str | None = None
        candle_ids: list[str] = []
        try:
            if self.state in {"COMPLETED", "FAILED"}:
                raise ValueError("TERMINAL_SESSION")
            if self.clock and at <= self.clock:
                raise ValueError("CLOCK_ROLLBACK_OR_DUPLICATE")
            if (
                self.policy.fingerprint != self.binding["risk_hash"]
                or execution_fingerprint() != self.binding["execution_hash"]
            ):
                raise ValueError("POLICY_MUTATION")
            if command.kind == "preflight":
                if (
                    command.completed
                    or command.openings
                    or (self.session and at >= self.session.start)
                ):
                    raise ValueError("INVALID_PREFLIGHT_TIME")
                self.transition("PREMARKET_CHECK")
                self.gaps = self.readiness()
                if self.gaps:
                    raise ValueError(";".join(self.gaps))
                self.transition("READY")
            elif command.kind == "open":
                if (
                    not self.session
                    or at != self.session.start
                    or command.completed
                    or command.openings
                ):
                    raise ValueError("INVALID_OPEN")
                self.transition("SESSION_OPEN")
            else:
                if not self.session or self.state not in {
                    "SESSION_OPEN",
                    "RUNNING",
                    "ENTRY_CUTOFF",
                    "PAPER_HALTED",
                }:
                    raise ValueError("SESSION_NOT_OPEN")
                if not self.session.start < at <= self.session.end:
                    raise ValueError("OUT_OF_SESSION")
                if (command.kind == "close") != (at == self.session.end):
                    raise ValueError("CLOSE_BOUNDARY")
                if at >= self.config.cutoff and self.state in {"SESSION_OPEN", "RUNNING"}:
                    self.transition("ENTRY_CUTOFF")
                elif self.state == "SESSION_OPEN":
                    self.transition("RUNNING")
                completed = {o.symbol: o for o in command.completed}
                opening = {o.symbol: o for o in command.openings}
                if len(completed) != len(command.completed) or len(opening) != len(
                    command.openings
                ):
                    raise ValueError("DUPLICATE_OBSERVATION")
                if set(completed) != set(self.sources):
                    raise ValueError("MANDATORY_CANDLE_MISSING")
                for symbol, o in completed.items():
                    r = self.sources[symbol]
                    expected = (
                        self.history[symbol][-1].end if self.history[symbol] else self.session.start
                    )
                    if not o.complete or o.bar.end != at or o.bar.start != expected:
                        raise ValueError("INCOMPLETE_OR_NONCONTIGUOUS_CANDLE")
                    if (symbol, o.bar.start) in self.seen:
                        raise ValueError("DUPLICATE_CANDLE")
                    if (
                        o.instrument_token != r.data.series.instrument_token
                        or o.bar not in r.data.series.bars
                    ):
                        raise ValueError("DATASET_OBSERVATION_MISMATCH")
                for symbol, opening_event in opening.items():
                    if symbol not in self.sources or at == self.session.end:
                        raise ValueError("UNEXPECTED_OPENING")
                    r = self.sources[symbol]
                    b = next((b for b in r.data.series.bars if b.start == at), None)
                    if (
                        b is None
                        or opening_event.instrument_token != r.data.series.instrument_token
                        or opening_event.price != b.open
                    ):
                        raise ValueError("OPENING_IDENTITY_OR_PRICE")
                if command.kind == "close":
                    self.transition("EOD_LIQUIDATION")
                for symbol in sorted(completed, key=self.rank.__getitem__):
                    bar = completed[symbol].bar
                    key = self.portfolio.positions.get(symbol)
                    if key:
                        order = self.portfolio.orders[key]
                        req = next(
                            r
                            for r in self.requests
                            if r.data.series.symbol == symbol
                            and r.definition.identifier == order["strategy_id"]
                        )
                        reference, why = exit_reference(
                            bar, Decimal(order["entry"]), req.definition, at == self.session.end
                        )
                        if why:
                            if order["state"] == "FILLED":
                                self.portfolio.emit(key, "EXIT_PENDING", at, reason=why)
                            if Decimal(bar.volume) * req.settings.participation < order["quantity"]:
                                self.halt("EXIT_LIQUIDITY_UNVALUED", at)
                            else:
                                price = reference * (1 - req.settings.slippage_bps / 10000)
                                fee = req.costs.quote(
                                    "SELL",
                                    price * order["quantity"],
                                    bar.start,
                                    Decimal(order["entry"]) * order["quantity"],
                                ).total
                                self.portfolio.emit(
                                    key, "CLOSED", at, price=str(price), fees=str(fee), reason=why
                                )
                    self.portfolio.emit(
                        "portfolio", "MARK", at, symbol=symbol, price=str(bar.close)
                    )
                    self.portfolio.guard(at)
                    self.history[symbol] += (bar,)
                    self.seen.add((symbol, bar.start))
                    candle_ids.append(digest(symbol + bar.model_dump_json()))
                if self.portfolio.halted and self.state != "PAPER_HALTED":
                    self.transition("PAPER_HALTED")
                for r in self.requests:
                    symbol = r.data.series.symbol
                    if not entry_signal(self.history[symbol], r.definition):
                        continue
                    signal = event(symbol, r.definition, completed[symbol].bar, at)
                    reject = (
                        "PAPER_HALT"
                        if self.portfolio.halted
                        else "ENTRY_CUTOFF"
                        if at >= self.config.cutoff
                        else None
                    )
                    key = digest(self.identifier + signal.idempotency_key)
                    self.signal_decisions.append(
                        dict(
                            timestamp=at.isoformat(),
                            symbol=symbol,
                            strategy=r.definition.identifier,
                            signal=signal.idempotency_key,
                            rejection=reject,
                        )
                    )
                    self.portfolio.emit(key, "PROPOSED", at, r, signal=signal.idempotency_key)
                    if reject or signal.idempotency_key in self.portfolio.consumed:
                        self.portfolio.emit(
                            key, "REJECTED", at, reason=reject or "DUPLICATE_SIGNAL"
                        )
                    else:
                        self.portfolio.consumed.add(signal.idempotency_key)
                        self.pending[key] = r
                for key, r in list(self.pending.items()):
                    symbol = r.data.series.symbol
                    if at >= self.config.cutoff or self.portfolio.halted or symbol not in opening:
                        self.portfolio.emit(
                            key, "CANCELLED", at, reason="CUTOFF_HALT_OR_MISSING_OPENING"
                        )
                        if at < self.config.cutoff and symbol not in opening:
                            self.halt("MISSING_ENTRY_OPENING", at)
                    else:
                        price = opening[symbol].price * (1 + r.settings.slippage_bps / 10000)
                        self.portfolio.enter(
                            key,
                            r,
                            dict(price=str(price), quantity=r.settings.quantity),
                            at,
                            self.control,
                        )
                    del self.pending[key]
                if command.kind == "close":
                    if self.state == "PAPER_HALTED":
                        self.transition("EOD_LIQUIDATION")
                    self.transition("RECONCILING")
                    account = self.portfolio.account()
                    if self.portfolio.halted or account["positions"] or account["reservations"]:
                        self.halt("EOD_INCOMPLETE", at)
                    else:
                        self.transition("COMPLETED")
                    self.portfolio.emit(
                        "portfolio", "SESSION_END", at, complete=self.state == "COMPLETED"
                    )
        except ValueError as exc:
            reason = str(exc)
            self.halt(reason, max(at, self.clock) if self.clock else at)
        self.clock = max(at, self.clock) if self.clock else at
        if len(self.audit) >= LIMIT or len(self.portfolio.events) >= LIMIT:
            raise ValueError("SESSION_JOURNAL_LIMIT")
        row = dict(
            sequence=len(self.audit),
            session_id=self.identifier,
            trading_date=self.config.trading_date.isoformat(),
            timestamp=self.clock.isoformat(),
            command=command.model_dump(mode="json"),
            recovered=recovered,
            state=self.state,
            transitions=self.transitions,
            calendar_hash=self.calendar.fingerprint,
            registry_hash=self.binding["registry"],
            qualifications=self.binding["qualifications"],
            datasets=self.binding["datasets"],
            risk_version=VERSION,
            risk_hash=self.policy.fingerprint,
            execution_version=EXECUTION_VERSION,
            execution_hash=execution_fingerprint(),
            candle_ids=candle_ids,
            portfolio_events=self.portfolio.events[begin:],
            account=self.portfolio.account(),
            rejection=reason,
            previous_hash=self.audit[-1]["current_hash"] if self.audit else "0" * 64,
        )
        row["current_hash"] = sha(canonical(row))
        self.audit.append(row)
        return row

    def output(self) -> dict[str, Any]:
        return reports(self.portfolio) | {
            "readiness.json": dict(
                scope="SYNTHETIC_FIXTURE", genuine_ready=False, gaps=self.gaps, binding=self.binding
            ),
            "session-events.json": self.audit,
            "candle-processing.json": [
                dict(symbol=s, completed=len(bars)) for s, bars in self.history.items()
            ],
            "session-signals.json": self.signal_decisions,
            "session-eod.json": dict(
                state=self.state,
                complete=self.state == "COMPLETED",
                account=self.portfolio.account(),
            ),
            "replay-verification.json": dict(
                schema=SCHEMA,
                session_id=self.identifier,
                final_hash=self.audit[-1]["current_hash"] if self.audit else "0" * 64,
                scope="DETERMINISTIC_LOCAL_CHAIN_NOT_ROLLBACK_RESISTANT",
            ),
        }


def run(
    requests: tuple[Request, ...],
    policy: Policy,
    control: Control,
    calendar: Calendar,
    config: Configuration,
    commands: tuple[Command, ...],
    directory: Path,
    *,
    resume: bool = False,
    verify_only: bool = False,
    crash: str | None = None,
) -> dict[str, Any]:
    directory = external(directory)
    if not commands or len(commands) > 1000:
        raise ValueError("COMMAND_BUDGET")
    engine = Engine(requests, policy, control, calendar, config, crash)
    plan = canonical(engine.binding)
    directory.mkdir(parents=True, exist_ok=True)
    if (directory / "session-plan.json").exists() and (
        directory / "session-plan.json"
    ).read_bytes() != plan:
        raise ValueError("CHECKPOINT_BINDING_MISMATCH")
    if (directory / "summary.json").exists() and not verify_only:
        raise ValueError("COMPLETED_OR_HALTED_SESSION_CANNOT_REOPEN")
    existing = sorted((directory / "journal").glob("*")) if (directory / "journal").exists() else []
    if [p.name for p in existing] != [f"{i:06d}.json" for i in range(len(existing))] or len(
        existing
    ) > len(commands):
        raise ValueError("CORRUPT_OR_MISSING_JOURNAL")
    if existing and not resume and not verify_only:
        raise ValueError("EXPLICIT_RECOVERY_REQUIRED")
    if verify_only and (
        len(existing) != len(commands) or not (directory / "summary.json").is_file()
    ):
        raise ValueError("INCOMPLETE_REPLAY")
    lock = directory / "session.lock"
    if lock.exists() or list(directory.rglob("*.pending")):
        raise ValueError("AMBIGUOUS_OWNER_OR_PENDING_WRITE")
    fd = None
    if not verify_only:
        fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        if not verify_only:
            publish(directory / "session-plan.json", plan)
        if crash == "CREATED":
            raise InterruptedError("SYNTHETIC_CRASH:CREATED")
        journal_bytes = 0
        for i, command in enumerate(commands):
            recovered = (
                bool(read_json(existing[i])[1]["recovered"])
                if i < len(existing)
                else resume and i == len(existing) and i > 0
            )
            row = engine.apply(command, recovered)
            raw = canonical(row)
            journal_bytes += len(raw)
            if len(raw) > 4 * 1024 * 1024 or journal_bytes > 128 * 1024 * 1024:
                raise ValueError("SESSION_JOURNAL_BYTE_LIMIT")
            if i < len(existing):
                if existing[i].read_bytes() != raw:
                    raise ValueError("JOURNAL_REPLAY_MISMATCH")
            elif not verify_only:
                publish(directory / "journal" / f"{i:06d}.json", raw)
        if engine.state not in {"COMPLETED", "PAPER_HALTED", "FAILED"}:
            if verify_only:
                raise ValueError("INCOMPLETE_REPLAY")
            return dict(
                schema=SCHEMA,
                state=engine.state,
                checkpointed=True,
                complete=False,
                account=engine.portfolio.account(),
            )
        payloads = {n: canonical(v) for n, v in engine.output().items()}
        if sum(map(len, payloads.values())) > 256 * 1024 * 1024:
            raise ValueError("REPORT_BUDGET")
        summary = dict(
            schema=SCHEMA,
            session_id=engine.identifier,
            state=engine.state,
            scope="SYNTHETIC_FIXTURE",
            complete=engine.state == "COMPLETED",
            genuine_qualified=0,
            account=engine.portfolio.account(),
            risk_version=VERSION,
            risk_hash=policy.fingerprint,
            execution_version=EXECUTION_VERSION,
            execution_hash=execution_fingerprint(),
            calendar_hash=calendar.fingerprint,
            cutoff_hash=sha(canonical(config.model_dump(mode="json"))),
            reports={n: sha(v) for n, v in payloads.items()},
            final_hash=engine.audit[-1]["current_hash"],
            operational="NO_GO",
            Gate_A="NOT_AUTHORIZED",
            Gate_B="NOT_AUTHORIZED",
        )
        payloads["summary.json"] = canonical(summary)
        for n, raw in payloads.items():
            if verify_only:
                if not (directory / n).is_file() or (directory / n).read_bytes() != raw:
                    raise ValueError("COMPLETED_REPORT_MISMATCH")
            else:
                publish(directory / n, raw)
        return summary
    finally:
        if fd is not None:
            os.close(fd)
            lock.unlink()
