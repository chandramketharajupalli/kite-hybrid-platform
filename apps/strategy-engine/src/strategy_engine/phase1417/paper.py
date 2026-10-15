"""Isolated serialized paper simulation, frozen fills, explicit risk and chained audit."""

import json
from datetime import datetime
from decimal import Decimal, localcontext
from pathlib import Path
from typing import Any, Literal

from pydantic import Field

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import NSE, Frozen, Hash, Instant, Money, digest
from strategy_engine.phase14.engine import FIVE, PRECISION, Settings, Trade
from strategy_engine.phase1415.execution import execute
from strategy_engine.phase1415.hardening import UNIVERSE
from strategy_engine.phase1415.paper import event
from strategy_engine.phase1415.research import Dataset
from strategy_engine.phase1415.strategies import Definition, registry
from strategy_engine.phase1416.imports import canonical, external, publish, sha
from strategy_engine.phase1417.qualification import Row, software_hash

D = Decimal
TRANSITIONS = {
    None: {"SIGNAL_RECEIVED"},
    "SIGNAL_RECEIVED": {"ELIGIBILITY_CHECKED", "PAPER_HALTED"},
    "ELIGIBILITY_CHECKED": {"RISK_APPROVED", "PAPER_ORDER_REJECTED", "PAPER_HALTED"},
    "RISK_APPROVED": {"PAPER_ORDER_CREATED"},
    "PAPER_ORDER_CREATED": {"PAPER_ORDER_ACCEPTED", "PAPER_ORDER_REJECTED", "PAPER_CANCELLED"},
    "PAPER_ORDER_ACCEPTED": {"PAPER_FILLED", "PAPER_CANCELLED", "PAPER_HALTED"},
    "PAPER_FILLED": {"PAPER_POSITION_OPEN"},
    "PAPER_POSITION_OPEN": {"PAPER_EXIT_PENDING", "PAPER_HALTED"},
    "PAPER_EXIT_PENDING": {"PAPER_POSITION_CLOSED", "PAPER_HALTED"},
}


class Risk(Frozen):
    fixture_only: bool = False
    reviewed_evidence: Hash | None = None
    capital: Money | None = None
    risk_per_trade: Money | None = None
    daily_loss: Money | None = None
    simultaneous_positions: int | None = Field(default=None, ge=1)
    symbol_exposure: Money | None = None
    portfolio_exposure: Money | None = None
    max_quantity: int | None = Field(default=None, ge=1)
    correlation_reviewed: bool = False

    @property
    def fingerprint(self) -> str:
        return digest(self.model_dump_json())

    def gaps(self) -> tuple[str, ...]:
        values = self.model_dump()
        return tuple(
            "RISK_POLICY_GAP:" + name
            for name in (
                "reviewed_evidence",
                "capital",
                "risk_per_trade",
                "daily_loss",
                "simultaneous_positions",
                "symbol_exposure",
                "portfolio_exposure",
                "max_quantity",
                "correlation_reviewed",
            )
            if not values[name]
        )


class Authorization(Frozen):
    mode: Literal["SYNTHETIC_FIXTURE", "AUTHORIZED_LOCAL_PAPER"]
    enabled: bool = False
    evidence_hash: Hash
    qualification_hash: Hash
    risk_hash: Hash
    dataset_hash: Hash
    valid_from: Instant
    valid_to: Instant
    partial_fills: bool = False
    paper_halt: bool = False


def size(
    risk: Risk,
    settings: Settings,
    costs: IntradayCostSchedule,
    price: Decimal,
    at: datetime,
    cash: Decimal,
    volume: int,
    stop_fraction: Decimal,
) -> int:
    if risk.gaps() or price <= 0 or cash <= 0:
        return 0
    assert risk.risk_per_trade and risk.symbol_exposure and risk.portfolio_exposure
    assert risk.max_quantity
    # Stops do not cap gap losses; this is a pre-trade planned-risk limit only.
    with localcontext(PRECISION):
        quantity = min(
            settings.quantity,
            risk.max_quantity,
            int(risk.risk_per_trade / (price * stop_fraction)),
            int(
                min(risk.symbol_exposure, risk.portfolio_exposure, settings.order_ceiling)
                / (price * settings.price_buffer)
            ),
            int(D(volume) * settings.participation),
            int(cash / price),
        )
        while quantity > 0:
            buy = price * quantity
            buy_fee = costs.quote("BUY", buy, at, None).total
            stop_value = buy * (1 - stop_fraction) * (1 - settings.slippage_bps / 10000)
            exit_fee = costs.quote("SELL", stop_value, at, buy).total
            if (
                buy + buy_fee <= cash
                and buy - stop_value + buy_fee + exit_fee <= risk.risk_per_trade
            ):
                return quantity
            quantity -= 1
        return 0


class Audit:
    def __init__(self, binding: dict[str, Any]) -> None:
        self.binding = binding
        self.run_id = sha(canonical(binding))
        self.events: list[dict[str, Any]] = []
        self.states: dict[str, str] = {}
        self.keys: set[str] = set()

    def emit(self, signal: str, state: str, at: datetime, **values: Any) -> None:
        previous = self.states.get(signal)
        if state not in TRANSITIONS.get(previous, set()):
            raise ValueError("INVALID_OR_REPEATED_PAPER_TRANSITION")
        key = digest(self.run_id + signal + state)
        if key in self.keys or len(self.events) >= 200000:
            raise ValueError("PAPER_IDEMPOTENCY_OR_RESOURCE_LIMIT")
        if self.events and at < datetime.fromisoformat(self.events[-1]["timestamp"]):
            raise ValueError("NON_CHRONOLOGICAL_PAPER_EVENT")
        row = dict(
            event_id=key,
            run_id=self.run_id,
            signal_fingerprint=signal,
            qualification_fingerprint=self.binding["qualification_hash"],
            risk_policy_fingerprint=self.binding["risk_hash"],
            symbol=self.binding["symbol"],
            strategy_id=self.binding["strategy_id"],
            strategy_version=self.binding["strategy_version"],
            scope=self.binding["scope"],
            simulated_order_id=digest(self.run_id + signal),
            timestamp=at.isoformat(),
            previous_state=previous,
            state=state,
            previous_hash=self.events[-1]["current_hash"] if self.events else "0" * 64,
            price=None,
            quantity=0,
            fees=None,
            cash=None,
            position=0,
            rejection=None,
        )
        row.update(values)
        row["current_hash"] = sha(canonical(row))
        self.events.append(row)
        self.states[signal] = state
        self.keys.add(key)

    @classmethod
    def replay(cls, binding: dict[str, Any], events: list[dict[str, Any]]) -> "Audit":
        audit = cls(binding)
        fixed = {
            "event_id",
            "run_id",
            "signal_fingerprint",
            "qualification_fingerprint",
            "risk_policy_fingerprint",
            "symbol",
            "strategy_id",
            "strategy_version",
            "scope",
            "simulated_order_id",
            "timestamp",
            "previous_state",
            "state",
            "previous_hash",
            "current_hash",
        }
        for value in events:
            audit.emit(
                value["signal_fingerprint"],
                value["state"],
                datetime.fromisoformat(value["timestamp"]),
                **{k: v for k, v in value.items() if k not in fixed},
            )
            if audit.events[-1] != value:
                raise ValueError("PAPER_AUDIT_TAMPERED")
        return audit


def simulate(
    qualification: Row,
    data: Dataset,
    definition: Definition,
    settings: Settings,
    costs: IntradayCostSchedule,
    risk: Risk,
    authorization: Authorization,
    directory: Path,
) -> dict[str, Any]:
    """Bind all paper accounting to the existing frozen Decimal precision policy."""
    with localcontext(PRECISION):
        return _simulate(
            qualification, data, definition, settings, costs, risk, authorization, directory
        )


def _simulate(
    qualification: Row,
    data: Dataset,
    definition: Definition,
    settings: Settings,
    costs: IntradayCostSchedule,
    risk: Risk,
    authorization: Authorization,
    directory: Path,
) -> dict[str, Any]:
    """Complete offline run; synthetic numerical proofs never grant genuine readiness."""
    directory = external(directory)
    series = data.series
    binding = dict(
        schema="Phase1417Paper.v1",
        universe_hash=sha(UNIVERSE.read_bytes()),
        registry_hash=sha(canonical([d.model_dump(mode="json") for d in registry()])),
        family_run_id=qualification.result.family_run_id,
        qualification_policy_hash=qualification.result.policy_hash,
        walk_forward=qualification.result.window.model_dump(mode="json"),
        dataset_provenance_hash=digest(data.model_dump_json()),
        corporate_action_policy=series.adjustment,
        qualification_hash=qualification.fingerprint,
        risk_hash=risk.fingerprint,
        dataset_hash=data.dataset_sha256,
        calendar_hash=series.calendar_hash,
        software_hash=software_hash(),
        strategy_hash=definition.fingerprint,
        symbol=series.symbol,
        strategy_id=definition.identifier,
        strategy_version=definition.version,
        scope=authorization.mode,
        costs=costs.model_dump(mode="json"),
        settings=settings.model_dump(mode="json"),
        authorization=authorization.model_dump(mode="json"),
    )
    audit = Audit(binding)
    start = series.from_inclusive
    synthetic = authorization.mode == "SYNTHETIC_FIXTURE"
    failures = list(risk.gaps())
    if (
        not authorization.enabled
        or authorization.valid_from > start
        or authorization.valid_to < series.to_exclusive
        or authorization.valid_from >= authorization.valid_to
    ):
        failures.append("PAPER_AUTHORIZATION_REQUIRED")
    if (
        authorization.qualification_hash != qualification.fingerprint
        or authorization.risk_hash != risk.fingerprint
        or authorization.dataset_hash != data.dataset_sha256
        or qualification.risk_policy_hash != risk.fingerprint
    ):
        failures.append("PAPER_BINDING_CONFLICT")
    r = qualification.result
    if (
        not qualification.numerical_gate()
        or (not synthetic and qualification.status != "QUALIFIED")
        or bool(r.synthetic) != synthetic
        or series.synthetic != synthetic
        or risk.fixture_only != synthetic
    ):
        failures.append("QUALIFIED_PAIR_REQUIRED")
    if (
        r.symbol != series.symbol
        or r.strategy_hash != definition.fingerprint
        or r.strategy_version != definition.version
        or r.window.end > start
    ):
        failures.append("STRATEGY_OR_HISTORY_BOUNDARY_CONFLICT")
    if (
        data.quarantined
        or not data.calendar_verified
        or not data.mapping_verified
        or not series.corporate_actions_reviewed
        or data.missing_sessions
        or data.duplicate_candles
        or data.out_of_order
        or series.quality() != ((), ())
    ):
        failures.append("PAPER_DATA_QUALITY")
    if risk.capital != settings.capital:
        failures.append("SIMULATED_CAPITAL_CONFLICT")
    if authorization.partial_fills:
        failures.append("PARTIAL_FILLS_UNSUPPORTED_FROZEN_EXECUTOR")
    cash = settings.capital
    if failures or authorization.paper_halt:
        signal = digest(audit.run_id + "admission")
        audit.emit(signal, "SIGNAL_RECEIVED", start)
        audit.emit(signal, "ELIGIBILITY_CHECKED", start)
        audit.emit(
            signal,
            "PAPER_HALTED" if authorization.paper_halt else "PAPER_ORDER_REJECTED",
            start,
            rejection="PAPER_HALT" if authorization.paper_halt else ";".join(failures),
            cash=str(cash),
        )
    else:
        # Frozen execution provides prices/times; sizing/risk uses entry-time evidence only.
        try:
            candidate = execute(
                series, definition, settings.model_copy(update={"quantity": 1}), costs
            )
        except ValueError as exc:
            signal = digest(audit.run_id + "quality")
            audit.emit(signal, "SIGNAL_RECEIVED", start)
            audit.emit(signal, "PAPER_HALTED", start, rejection=str(exc), cash=str(cash))
            candidate = None
        if candidate:
            day = None
            day_start_cash = cash
            by_start = {b.start: b for b in series.bars}
            candidates: list[tuple[datetime, Trade | None, str | None]] = [
                (t.entry_at, t, None) for t in candidate.trades
            ]
            candidates.extend(
                (datetime.fromisoformat(reason.rsplit(":", 1)[0]), None, reason)
                for reason in candidate.no_fills
            )
            for at, trade, denial in sorted(candidates, key=lambda item: item[0]):
                if trade is None:
                    denied_signal = event(series.symbol, definition, by_start[at - FIVE], at)
                    audit.emit(denied_signal.idempotency_key, "SIGNAL_RECEIVED", at)
                    audit.emit(denied_signal.idempotency_key, "ELIGIBILITY_CHECKED", at)
                    audit.emit(
                        denied_signal.idempotency_key,
                        "PAPER_ORDER_REJECTED",
                        at,
                        rejection=denial,
                        cash=str(cash),
                    )
                    continue
                candle = by_start[trade.signal_at - FIVE]
                signal_event = event(series.symbol, definition, candle, trade.entry_at)
                key = signal_event.idempotency_key
                at = trade.entry_at
                if at.astimezone(NSE).date() != day:
                    day = at.astimezone(NSE).date()
                    day_start_cash = cash
                audit.emit(key, "SIGNAL_RECEIVED", trade.signal_at)
                audit.emit(key, "ELIGIBILITY_CHECKED", at)
                quantity = size(
                    risk,
                    settings,
                    costs,
                    trade.entry,
                    at,
                    cash,
                    by_start[at].volume,
                    definition.stop,
                )
                assert risk.daily_loss is not None
                reason = (
                    "SIGNAL_EXPIRED"
                    if at >= signal_event.expiry
                    else "DAILY_LOSS_GUARD"
                    if day_start_cash - cash >= risk.daily_loss
                    else "POSITION_SIZE_OR_EXPOSURE_OR_LIQUIDITY"
                    if quantity < 1
                    else None
                )
                if reason:
                    audit.emit(key, "PAPER_ORDER_REJECTED", at, rejection=reason, cash=str(cash))
                    continue
                for state in ("RISK_APPROVED", "PAPER_ORDER_CREATED", "PAPER_ORDER_ACCEPTED"):
                    audit.emit(key, state, at, quantity=quantity)
                buy = trade.entry * quantity
                fee = costs.quote("BUY", buy, at, None).total
                cash -= buy + fee
                audit.emit(
                    key,
                    "PAPER_FILLED",
                    at,
                    price=str(trade.entry),
                    quantity=quantity,
                    fees=str(fee),
                    cash=str(cash),
                    position=quantity,
                )
                audit.emit(
                    key,
                    "PAPER_POSITION_OPEN",
                    at,
                    quantity=quantity,
                    cash=str(cash),
                    position=quantity,
                )
                exit_bar = by_start[trade.exit_at - FIVE]
                audit.emit(
                    key,
                    "PAPER_EXIT_PENDING",
                    trade.exit_at,
                    quantity=quantity,
                    rejection=trade.reason,
                    position=quantity,
                )
                if D(exit_bar.volume) * settings.participation < quantity:
                    audit.emit(
                        key,
                        "PAPER_HALTED",
                        trade.exit_at,
                        rejection="EXIT_LIQUIDITY_UNVALUED",
                        cash=str(cash),
                        position=quantity,
                        quantity=quantity,
                    )
                    break
                sell = trade.exit * quantity
                sell_fee = costs.quote("SELL", sell, exit_bar.start, buy).total
                cash += sell - sell_fee
                audit.emit(
                    key,
                    "PAPER_POSITION_CLOSED",
                    trade.exit_at,
                    price=str(trade.exit),
                    quantity=quantity,
                    fees=str(sell_fee),
                    cash=str(cash),
                    rejection=trade.reason,
                )
    Audit.replay(binding, audit.events)
    output = dict(
        binding=binding,
        run_id=audit.run_id,
        events=audit.events,
        cash=str(cash),
        final_hash=audit.events[-1]["current_hash"] if audit.events else "0" * 64,
        genuine_qualified=qualification.status == "QUALIFIED" and not synthetic,
        operational="NO_GO",
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
    )
    # Publish the immutable complete transcript; identical replay is idempotent.
    payload = canonical(output)
    if len(payload) > 256 * 1024 * 1024:
        raise ValueError("PAPER_OUTPUT_LIMIT")
    publish(directory / "paper-run.json", payload)
    stored = json.loads((directory / "paper-run.json").read_bytes())
    if stored != output:
        raise ValueError("PAPER_REPLAY_CONFLICT")
    return output
