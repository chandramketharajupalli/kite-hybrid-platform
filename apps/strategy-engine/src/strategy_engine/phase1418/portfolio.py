"""Single-writer portfolio simulation over frozen, qualified paper candidate streams."""

import csv
from datetime import datetime
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any, Literal

from pydantic import Field

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import NSE, Frozen, Hash, Instant, Money, digest
from strategy_engine.phase14.engine import FIVE, PRECISION, Settings
from strategy_engine.phase1415.hardening import ROOT, UNIVERSE
from strategy_engine.phase1415.research import Dataset
from strategy_engine.phase1415.strategies import Definition, registry
from strategy_engine.phase1416.imports import canonical, external, publish, read_json, sha
from strategy_engine.phase1417.paper import Authorization, Risk, simulate, size
from strategy_engine.phase1417.qualification import Row
from strategy_engine.phase1417.qualification import inventory as previous_inventory
from strategy_engine.phase1418.canonical_policy import VERSION, Version, payload

D = Decimal
ORDERING = "timestamp-exits-marks-canonical-symbol-strategy-signal-v1"
LIMIT = 200000
TRANSITIONS: dict[str | None, set[str]] = {
    None: {"PROPOSED"},
    "PROPOSED": {"RISK_CHECKED", "REJECTED", "CANCELLED"},
    "RISK_CHECKED": {"RESERVED", "REJECTED", "CANCELLED"},
    "RESERVED": {"ACCEPTED", "CANCELLED"},
    "ACCEPTED": {"FILLED", "CANCELLED"},
    "FILLED": {"EXIT_PENDING"},
    "EXIT_PENDING": {"CLOSED"},
}


class Policy(Frozen):
    fingerprint_version: Version
    base: Risk = Risk()
    strategy_exposure: Money | None = None
    sector_exposure: Money | None = None
    sectors: dict[str, str] = Field(default_factory=dict)
    sector_evidence: Hash | None = None
    correlation_positions: int | None = Field(default=None, ge=1)

    @property
    def fingerprint(self) -> str:
        return sha(payload(self.model_dump(mode="python"), self.fingerprint_version))

    def gaps(self, symbols: set[str]) -> tuple[str, ...]:
        missing = list(self.base.gaps())
        for name in (
            "strategy_exposure",
            "sector_exposure",
            "sector_evidence",
            "correlation_positions",
        ):
            if not getattr(self, name):
                missing.append("RISK_POLICY_GAP:" + name)
        if any(not self.sectors.get(s) for s in symbols):
            missing.append("RISK_POLICY_GAP:sector_mapping")
        return tuple(missing)


class Request(Frozen):
    direction: Literal["LONG"] = "LONG"
    qualification: Row
    data: Dataset
    definition: Definition
    settings: Settings
    costs: IntradayCostSchedule
    authorization: Authorization

    @property
    def fingerprint(self) -> str:
        return digest(self.model_dump_json())


class Control(Frozen):
    policy_fingerprint_version: Version
    policy_hash: Hash
    authorization_evidence: Hash
    enabled: bool = False
    paper_halt: bool = False
    # A declared feed cutoff models missing delivery, never fabricated market prices.
    delivery_end: Instant | None = None
    cancel_signals: tuple[Hash, ...] = ()


def inventory() -> dict[str, str]:
    result = previous_inventory()
    directory = ROOT / "apps/strategy-engine/src/strategy_engine/phase1418"
    if {p.name for p in directory.glob("*.py")} != {
        "__init__.py",
        "portfolio.py",
        "canonical_policy.py",
    }:
        raise ValueError("SOFTWARE_INVENTORY_CONFLICT")
    for p in directory.glob("*.py"):
        result[p.relative_to(ROOT).as_posix()] = sha(p.read_bytes())
    return dict(sorted(result.items()))


class Coordinator:
    """All mutations are serialized; events record complete reconciled post-state."""

    def __init__(self, binding: dict[str, Any], policy: Policy) -> None:
        self.binding = binding
        self.run_id = sha(canonical(binding))
        self.policy = policy
        self.cash = policy.base.capital or D(0)
        self.opening = self.cash
        self.fees = D(0)
        self.realized = D(0)
        self.orders: dict[str, dict[str, Any]] = {}
        self.positions: dict[str, str] = {}
        self.reservations: dict[str, Decimal] = {}
        self.marks: dict[str, Decimal] = {}
        self.consumed: set[str] = set()
        self.events: list[dict[str, Any]] = []
        self.halted = False
        self.day: str | None = None
        self.day_equity = self.cash

    def account(self) -> dict[str, Any]:
        with localcontext(PRECISION):
            reserved = sum(self.reservations.values(), D(0))
            basis = sum(
                (
                    D(self.orders[k]["entry"]) * self.orders[k]["quantity"]
                    for k in self.positions.values()
                ),
                D(0),
            )
            marked = sum(
                (
                    self.marks.get(s, D(self.orders[k]["entry"])) * self.orders[k]["quantity"]
                    for s, k in self.positions.items()
                ),
                D(0),
            )
            unrealized = sum(
                (
                    (self.marks.get(s, D(self.orders[k]["entry"])) - D(self.orders[k]["entry"]))
                    * self.orders[k]["quantity"]
                    - D(self.orders[k]["entry_fee"])
                    for s, k in self.positions.items()
                ),
                D(0),
            )
            if not self.cash >= reserved >= 0:
                raise ValueError("PAPER_HALTED:INVALID_ACCOUNTING")
            if self.cash + marked != self.opening + self.realized + unrealized:
                raise ValueError("PAPER_HALTED:UNBALANCED_EQUITY")
            return dict(
                cash=str(self.cash),
                available=str(self.cash - reserved),
                reserved=str(reserved),
                cost_basis=str(basis),
                marked_value=str(marked),
                equity=str(self.cash + marked),
                realized=str(self.realized),
                unrealized=str(unrealized),
                fees=str(self.fees),
                positions={s: dict(self.orders[k]) for s, k in sorted(self.positions.items())},
                reservations={k: str(v) for k, v in sorted(self.reservations.items())},
                halted=self.halted,
            )

    def emit(
        self, key: str, state: str, at: datetime, request: Request | None = None, **values: Any
    ) -> None:
        if len(self.events) >= LIMIT:
            raise ValueError("PAPER_HALTED:JOURNAL_LIMIT")
        if self.events and at < datetime.fromisoformat(self.events[-1]["timestamp"]):
            raise ValueError("PAPER_HALTED:OUT_OF_ORDER_EVENT")
        before = self.account()
        order = self.orders.get(key)
        previous = order["state"] if order else None
        if state not in {"MARK", "SESSION_END", "PAPER_HALTED"}:
            if state not in TRANSITIONS.get(previous, set()):
                raise ValueError("INVALID_OR_DUPLICATE_TRANSITION")
            if state == "PROPOSED":
                assert request is not None
                order = dict(
                    state=state,
                    symbol=request.data.series.symbol,
                    strategy_id=request.definition.identifier,
                    strategy_version=request.definition.version,
                    signal=values["signal"],
                    signal_at=at.isoformat(),
                    qualification_hash=request.qualification.fingerprint,
                    quantity=0,
                    entry="0",
                    entry_fee="0",
                    stop=None,
                    target=None,
                    entry_at=None,
                    order_id=key,
                )
                self.orders[key] = order
            assert order is not None
            order["state"] = state
        if state == "RESERVED":
            if key in self.reservations:
                raise ValueError("DOUBLE_RESERVATION")
            self.reservations[key] = D(values["amount"])
        elif state == "FILLED":
            assert order is not None
            if order["symbol"] in self.positions:
                raise ValueError("SYMBOL_ALREADY_OWNED")
            debit = D(values["price"]) * values["quantity"] + D(values["fees"])
            if self.reservations.get(key) != debit:
                raise ValueError("PAPER_HALTED:UNRECONCILED_RESERVATION")
            del self.reservations[key]
            self.cash -= debit
            self.fees += D(values["fees"])
            order.update(
                quantity=values["quantity"],
                entry=values["price"],
                entry_fee=values["fees"],
                stop=values["stop"],
                target=values["target"],
                entry_at=at.isoformat(),
            )
            self.positions[order["symbol"]] = key
            self.marks[order["symbol"]] = D(values["price"])
        elif state == "CLOSED":
            assert order is not None
            if self.positions.get(order["symbol"]) != key:
                raise ValueError("PAPER_HALTED:POSITION_OWNERSHIP_MISMATCH")
            del self.positions[order["symbol"]]
            sell = D(values["price"]) * order["quantity"]
            fee = D(values["fees"])
            net = sell - D(order["entry"]) * order["quantity"] - D(order["entry_fee"]) - fee
            self.cash += sell - fee
            self.fees += fee
            self.realized += net
            order.update(
                exit=values["price"],
                exit_at=at.isoformat(),
                net=str(net),
                exit_reason=values["reason"],
            )
        elif state == "CANCELLED":
            self.reservations.pop(key, None)
        elif state == "MARK":
            self.marks[values["symbol"]] = D(values["price"])
        elif state == "PAPER_HALTED":
            self.halted = True
        after = self.account()
        event = dict(
            sequence=len(self.events),
            run_id=self.run_id,
            event_id=digest(self.run_id + str(len(self.events))),
            timestamp=at.isoformat(),
            order_id=key,
            state=state,
            previous_state=previous,
            symbol=order["symbol"] if order else values.get("symbol"),
            strategy_id=order["strategy_id"] if order else None,
            signal_fingerprint=order["signal"] if order else None,
            qualification_fingerprint=order["qualification_hash"] if order else None,
            risk_policy_fingerprint=self.policy.fingerprint,
            risk_policy_fingerprint_version=self.policy.fingerprint_version,
            scope=self.binding["scope"],
            cash_delta=str(D(after["cash"]) - D(before["cash"])),
            reservation_delta=str(D(after["reserved"]) - D(before["reserved"])),
            position_delta=len(after["positions"]) - len(before["positions"]),
            fees=values.get("fees", "0"),
            details=values,
            account=after,
            previous_hash=self.events[-1]["current_hash"] if self.events else "0" * 64,
        )
        event["current_hash"] = sha(canonical(event))
        self.events.append(event)

    def halt(self, at: datetime, reason: str) -> None:
        if not self.halted:
            self.emit("portfolio", "PAPER_HALTED", at, reason=reason)

    def guard(self, at: datetime) -> None:
        day = at.astimezone(NSE).date().isoformat()
        if day != self.day:
            self.day = day
            self.day_equity = D(self.account()["equity"])
        limit = self.policy.base.daily_loss
        if limit and self.day_equity - D(self.account()["equity"]) >= limit:
            self.halt(at, "DAILY_LOSS_BREACH")

    def enter(
        self, key: str, request: Request, event: dict[str, Any], at: datetime, control: Control
    ) -> None:
        order = self.orders[key]
        if order["state"] != "PROPOSED":
            return
        self.emit(key, "RISK_CHECKED", at)
        symbol = order["symbol"]
        base = self.policy.base
        reason = (
            "PAPER_HALT"
            if self.halted
            else "ONE_POSITION_PER_SYMBOL"
            if symbol in self.positions
            else None
        )
        if at >= datetime.fromisoformat(order["signal_at"]) + FIVE:
            reason = "EXPIRED_SIGNAL"
        if len(self.positions) + len(self.reservations) >= min(
            base.simultaneous_positions or 0, self.policy.correlation_positions or 0
        ):
            reason = reason or "POSITION_OR_CORRELATION_LIMIT"
        if reason:
            self.emit(key, "REJECTED", at, reason=reason)
            return
        price = D(event["price"])
        total = strategy = sector = D(0)
        for s, k in self.positions.items():
            value = self.marks[s] * self.orders[k]["quantity"]
            total += value
            if self.orders[k]["strategy_id"] == order["strategy_id"]:
                strategy += value
            if self.policy.sectors[s] == self.policy.sectors[symbol]:
                sector += value
        assert (
            base.portfolio_exposure
            and self.policy.strategy_exposure
            and self.policy.sector_exposure
        )
        remaining = min(
            base.portfolio_exposure - total,
            self.policy.strategy_exposure - strategy,
            self.policy.sector_exposure - sector,
        )
        bars = {b.start: b for b in request.data.series.bars}
        candle = bars.get(at)
        if candle is None:
            self.halt(at, "MISSING_ENTRY_DATA")
            self.emit(key, "REJECTED", at, reason="MISSING_ENTRY_DATA")
            return
        qty = (
            0
            if remaining <= 0
            else size(
                base.model_copy(update={"portfolio_exposure": remaining}),
                request.settings.model_copy(update={"quantity": event["quantity"]}),
                request.costs,
                price,
                at,
                D(self.account()["available"]),
                candle.volume,
                request.definition.stop,
            )
        )
        if qty <= 0:
            self.emit(key, "REJECTED", at, reason="CASH_EXPOSURE_LIQUIDITY_OR_RISK")
            return
        fee = request.costs.quote("BUY", price * qty, at, None).total
        self.emit(key, "RESERVED", at, amount=str(price * qty + fee), quantity=qty)
        if order["signal"] in control.cancel_signals:
            self.emit(key, "CANCELLED", at, reason="EXPLICIT_PAPER_CANCEL")
            return
        self.emit(key, "ACCEPTED", at)
        self.emit(
            key,
            "FILLED",
            at,
            price=str(price),
            quantity=qty,
            fees=str(fee),
            stop=str(price * (1 - request.definition.stop)),
            target=str(price * (1 + request.definition.target)),
        )


def reports(coordinator: Coordinator) -> dict[str, Any]:
    events = coordinator.events
    daily: dict[str, str] = {}
    strategies: dict[str, str] = {}
    symbols: dict[str, str] = {}
    for order in coordinator.orders.values():
        if order["state"] == "CLOSED":
            day = datetime.fromisoformat(order["exit_at"]).astimezone(NSE).date().isoformat()
            for table, key in [
                (daily, day),
                (strategies, order["strategy_id"]),
                (symbols, order["symbol"]),
            ]:
                table[key] = str(D(table.get(key, "0")) + D(order["net"]))
    return {
        "signal-decisions.json": [
            e for e in events if e["state"] in {"PROPOSED", "REJECTED", "RISK_CHECKED"}
        ],
        "orders.json": list(coordinator.orders.values()),
        "positions.json": coordinator.account()["positions"],
        "cash-ledger.json": [
            dict(
                sequence=e["sequence"],
                timestamp=e["timestamp"],
                cash_delta=e["cash_delta"],
                reservation_delta=e["reservation_delta"],
                account=e["account"],
            )
            for e in events
        ],
        "equity-curve.json": [
            dict(timestamp=e["timestamp"], equity=e["account"]["equity"]) for e in events
        ],
        "daily-pnl.json": daily,
        "strategy-pnl.json": strategies,
        "symbol-pnl.json": symbols,
        "exposure.json": [
            dict(
                timestamp=e["timestamp"],
                positions=e["account"]["positions"],
                marked_value=e["account"]["marked_value"],
                reserved=e["account"]["reserved"],
            )
            for e in events
        ],
        "risk-rejections.json": [e for e in events if e["state"] in {"REJECTED", "CANCELLED"}],
        "paper-halt.json": [e for e in events if e["state"] == "PAPER_HALTED"],
        "eod-reconciliation.json": [e for e in events if e["state"] == "SESSION_END"],
    }


def run(
    requests: tuple[Request, ...],
    policy: Policy,
    control: Control,
    directory: Path,
    *,
    interrupt_after: int | None = None,
) -> dict[str, Any]:
    with localcontext(PRECISION):
        return _run(requests, policy, control, directory, interrupt_after=interrupt_after)


def _run(
    requests: tuple[Request, ...],
    policy: Policy,
    control: Control,
    directory: Path,
    *,
    interrupt_after: int | None,
) -> dict[str, Any]:
    directory = external(directory)
    if policy.fingerprint_version != VERSION or control.policy_fingerprint_version != VERSION:
        raise ValueError("PAPER_HALTED:UNSUPPORTED_POLICY_FINGERPRINT_VERSION")
    checkpoint = directory / "portfolio-plan.json"
    if checkpoint.exists():
        _, prior = read_json(checkpoint)
        if (
            prior.get("schema") != "Phase1418Portfolio.v2"
            or prior.get("policy_fingerprint_version") != VERSION
            or prior.get("policy", {}).get("fingerprint_version") != VERSION
            or prior.get("control", {}).get("policy_fingerprint_version") != VERSION
        ):
            raise ValueError("PAPER_HALTED:INCOMPATIBLE_CHECKPOINT_VERSION")
    if (
        not requests
        or len(requests) > 6690
        or sum(len(r.data.series.bars) for r in requests) > 100000
    ):
        raise ValueError("PORTFOLIO_INPUT_BUDGET")
    universe = [
        r["symbol"] for r in csv.DictReader(UNIVERSE.read_text(encoding="utf-8-sig").splitlines())
    ]
    assert len(universe) == 1115 and len(set(universe)) == 1115
    rank = {s: i for i, s in enumerate(universe)}
    requests = tuple(
        sorted(
            requests,
            key=lambda r: (
                rank.get(r.data.series.symbol, 1115),
                r.definition.identifier,
                r.fingerprint,
            ),
        )
    )
    datasets: dict[str, str] = {}
    for r in requests:
        symbol = r.data.series.symbol
        if symbol in datasets and datasets[symbol] != r.data.dataset_sha256:
            raise ValueError("CONFLICTING_SYMBOL_DATASETS")
        datasets[symbol] = r.data.dataset_sha256
    binding = dict(
        schema="Phase1418Portfolio.v2",
        policy_fingerprint_version=VERSION,
        ordering=ORDERING,
        universe_hash=sha(UNIVERSE.read_bytes()),
        registry_hash=sha(canonical([d.model_dump(mode="json") for d in registry()])),
        software=inventory(),
        policy=policy.model_dump(mode="json"),
        control=control.model_dump(mode="json"),
        requests=[r.fingerprint for r in requests],
        scope="SYNTHETIC_FIXTURE" if policy.base.fixture_only else "AUTHORIZED_LOCAL_PAPER",
        family_size=13380,
    )
    plan = canonical(binding)
    if len(plan) > 256 * 1024 * 1024:
        raise ValueError("PORTFOLIO_INPUT_BUDGET")
    try:
        publish(directory / "portfolio-plan.json", plan)
    except ValueError as exc:
        raise ValueError("PAPER_HALTED:REPLAY_BINDING_MISMATCH") from exc
    c = Coordinator(binding, policy)
    start = min(r.data.series.from_inclusive for r in requests)
    gaps = list(policy.gaps({r.data.series.symbol for r in requests}))
    if control.policy_hash != policy.fingerprint:
        gaps.append("RISK_POLICY_MISMATCH")
    if not control.enabled:
        gaps.append("PAPER_AUTHORIZATION_REQUIRED")
    if any(r.data.series.symbol not in rank for r in requests):
        gaps.append("NONCANONICAL_SYMBOL")
    if gaps or control.paper_halt:
        c.halt(start, ";".join(gaps) if gaps else "EXPLICIT_PAPER_HALT")
    else:
        actions: list[tuple[datetime, int, int, str, str, int, str, dict[str, Any]]] = []
        marks: set[tuple[str, datetime]] = set()
        ends: set[datetime] = set()
        for index, r in enumerate(requests):
            output = simulate(
                r.qualification,
                r.data,
                r.definition,
                r.settings,
                r.costs,
                policy.base,
                r.authorization,
                directory / "candidates" / r.fingerprint,
            )
            for event in output["events"]:
                state = event["state"]
                at = datetime.fromisoformat(event["timestamp"])
                if control.delivery_end and at > control.delivery_end:
                    continue
                kind = {
                    "SIGNAL_RECEIVED": "propose",
                    "PAPER_FILLED": "enter",
                    "PAPER_POSITION_CLOSED": "exit",
                    "PAPER_ORDER_REJECTED": "reject",
                    "PAPER_HALTED": "halt",
                }.get(state)
                if kind is None:
                    continue
                priority = {"exit": 0, "halt": 1, "propose": 3, "enter": 4, "reject": 5}[kind]
                actions.append(
                    (
                        at,
                        priority,
                        rank[r.data.series.symbol],
                        r.definition.identifier,
                        event["signal_fingerprint"],
                        index,
                        kind,
                        event,
                    )
                )
            for bar in r.data.series.bars:
                if control.delivery_end and bar.end > control.delivery_end:
                    continue
                mark = (r.data.series.symbol, bar.end)
                if mark not in marks:
                    marks.add(mark)
                    actions.append(
                        (
                            bar.end,
                            2,
                            rank[r.data.series.symbol],
                            "",
                            "",
                            index,
                            "mark",
                            {"price": str(bar.close)},
                        )
                    )
            ends.update(s.end for s in r.data.series.sessions)
        for at in ends:
            actions.append((at, 6, 0, "", "", 0, "end", {}))
        if control.delivery_end and control.delivery_end < max(ends):
            missing_at = max(start, min(control.delivery_end + FIVE, max(ends)))
            actions.append(
                (missing_at, 1, 0, "", "", 0, "halt", {"rejection": "MANDATORY_DELIVERY_MISSING"})
            )
        if len(actions) > LIMIT:
            raise ValueError("PORTFOLIO_EVENT_BUDGET")
        for at, _, __, ___, signal, index, kind, event in sorted(actions):
            r = requests[index]
            key = digest(c.run_id + str(index) + signal)
            c.guard(at)
            if kind == "propose":
                c.emit(key, "PROPOSED", at, r, signal=signal)
                if signal in c.consumed:
                    c.emit(key, "REJECTED", at, reason="DUPLICATE_SIGNAL")
                else:
                    c.consumed.add(signal)
            elif kind == "enter":
                c.enter(key, r, event, at, control)
            elif kind == "reject":
                if any(
                    reason in event["rejection"]
                    for reason in ("PAPER_DATA_QUALITY", "PAPER_BINDING_CONFLICT")
                ):
                    c.halt(at, event["rejection"])
                if c.orders[key]["state"] == "PROPOSED":
                    c.emit(key, "REJECTED", at, reason=event["rejection"])
            elif kind == "halt":
                c.halt(at, event["rejection"])
            elif kind == "mark":
                c.emit("portfolio", "MARK", at, symbol=r.data.series.symbol, price=event["price"])
                c.guard(at)
            elif kind == "exit":
                if c.orders.get(key, {}).get("state") == "FILLED":
                    order = c.orders[key]
                    qty = order["quantity"]
                    price = D(event["price"])
                    fee = r.costs.quote(
                        "SELL", price * qty, at - FIVE, D(order["entry"]) * qty
                    ).total
                    c.emit(key, "EXIT_PENDING", at, reason=event["rejection"])
                    c.emit(
                        key,
                        "CLOSED",
                        at,
                        price=str(price),
                        fees=str(fee),
                        reason=event["rejection"],
                    )
                    c.guard(at)
            else:
                for k, o in list(c.orders.items()):
                    if o["state"] in {"PROPOSED", "RISK_CHECKED", "RESERVED", "ACCEPTED"}:
                        c.emit(k, "CANCELLED", at, reason="EOD_CANCEL")
                if c.positions:
                    c.halt(at, "EOD_UNRESOLVED_POSITION")
                c.emit(
                    "portfolio", "SESSION_END", at, complete=not c.positions and not c.reservations
                )
    journal = directory / "journal"
    journal.mkdir(exist_ok=True)
    existing = sorted(journal.iterdir())
    if (directory / "run-summary.json").exists() and len(existing) != len(c.events):
        raise ValueError("PAPER_HALTED:TRUNCATED_COMPLETED_JOURNAL")
    if [p.name for p in existing] != [f"{i:06d}.json" for i in range(len(existing))] or len(
        existing
    ) > len(c.events):
        raise ValueError("PAPER_HALTED:CORRUPT_JOURNAL")
    for i, p in enumerate(existing):
        if p.read_bytes() != canonical(c.events[i]):
            raise ValueError("PAPER_HALTED:JOURNAL_REPLAY_MISMATCH")
    payloads = {name: canonical(value) for name, value in reports(c).items()}
    if (directory / "run-summary.json").exists():
        for name, payload in payloads.items():
            if not (directory / name).is_file() or (directory / name).read_bytes() != payload:
                raise ValueError("PAPER_HALTED:COMPLETED_REPORT_MISMATCH")
    if (
        sum(map(len, payloads.values())) + sum(len(canonical(e)) for e in c.events)
        > 256 * 1024 * 1024
    ):
        raise ValueError("PORTFOLIO_OUTPUT_BUDGET")
    if interrupt_after is not None and interrupt_after < 0:
        raise ValueError("INVALID_INTERRUPT")
    for i, event in enumerate(c.events):
        if interrupt_after is not None and i >= interrupt_after:
            raise InterruptedError("SYNTHETIC_CRASH_BEFORE_EVENT")
        publish(journal / f"{i:06d}.json", canonical(event))
    for name, payload in payloads.items():
        publish(directory / name, payload)
    summary = dict(
        schema="Phase1418PortfolioSummary.v2",
        policy_fingerprint_version=VERSION,
        risk_policy_fingerprint=policy.fingerprint,
        run_id=c.run_id,
        scope=binding["scope"],
        event_count=len(c.events),
        final_hash=c.events[-1]["current_hash"] if c.events else "0" * 64,
        account=c.account(),
        reports={name: sha(payload) for name, payload in payloads.items()},
        simulation_complete=not c.halted and not c.positions and not c.reservations,
        halted=c.halted,
        qualified_genuine=0
        if policy.base.fixture_only
        else len(
            {
                (r.qualification.result.symbol, r.qualification.result.strategy_id)
                for r in requests
                if r.qualification.status == "QUALIFIED"
            }
        ),
        operational="NO_GO",
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
    )
    publish(directory / "run-summary.json", canonical(summary))
    return summary
