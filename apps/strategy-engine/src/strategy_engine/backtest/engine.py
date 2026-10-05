"""Deterministic next-open, full-cash, flat/long/flat research engine v1."""
import json
from datetime import UTC, datetime
from decimal import (
    ROUND_HALF_EVEN,
    Context,
    Decimal,
    DecimalException,
    DivisionByZero,
    Inexact,
    InvalidOperation,
    Overflow,
    localcontext,
)
from typing import Annotated, Any, Literal, Protocol

from pydantic import Field, model_validator

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import (
    NSE,
    Bar,
    Dataset,
    Frozen,
    Hash,
    Instant,
    Label,
    LocalMinute,
    Money,
    Quantity,
    decimal_text,
    digest,
    instant_text,
)

ENGINE_VERSION = "intraday-next-open-v1"
ZERO = Decimal(0)
ARITHMETIC = Context(prec=80, rounding=ROUND_HALF_EVEN,
                     traps=[InvalidOperation, DivisionByZero, Overflow, Inexact])
Side = Literal["BUY", "SELL"]


class SlippageModel(Protocol):
    def price(self, side: Side, reference: Decimal) -> Decimal: ...


class TransactionCostModel(Protocol):
    def costs(self, notional: Decimal) -> Decimal: ...


class AdverseBps(Frozen):
    bps: Money

    @model_validator(mode="after")
    def valid(self) -> "AdverseBps":
        if self.bps >= 10_000:
            raise ValueError("INVALID_SLIPPAGE")
        return self

    def price(self, side: Side, reference: Decimal) -> Decimal:
        if side not in {"BUY", "SELL"} or not reference.is_finite() or reference <= 0:
            raise ValueError("INVALID_REFERENCE_PRICE")
        with localcontext(ARITHMETIC):
            adjustment = self.bps / Decimal(10_000)
            return reference * (1 + adjustment if side == "BUY" else 1 - adjustment)


class ConfiguredCosts(Frozen):
    fixed_per_fill: Money
    notional_bps: Money

    @model_validator(mode="after")
    def valid(self) -> "ConfiguredCosts":
        if self.notional_bps >= 10_000:
            raise ValueError("INVALID_COSTS")
        return self

    def costs(self, notional: Decimal) -> Decimal:
        if not notional.is_finite() or notional < 0:
            raise ValueError("INVALID_NOTIONAL")
        with localcontext(ARITHMETIC):
            return self.fixed_per_fill + notional * self.notional_bps / Decimal(10_000)


class Config(Frozen):
    initial_cash: Money
    entry_start: LocalMinute
    last_entry_time: LocalMinute
    forced_exit_time: LocalMinute
    slippage: AdverseBps
    costs: ConfiguredCosts | IntradayCostSchedule

    @model_validator(mode="after")
    def valid(self) -> "Config":
        if self.initial_cash <= 0 or not (
            self.entry_start <= self.last_entry_time < self.forced_exit_time
        ):
            raise ValueError("INVALID_CONFIG")
        return self


class Parameter(Frozen):
    name: Label
    value: Annotated[str, Field(max_length=256, pattern=r"^[ -~]*$")]


class StrategySpec(Frozen):
    name: Label
    version: Label
    implementation_fingerprint: Hash
    parameters: tuple[Parameter, ...]

    @model_validator(mode="after")
    def valid(self) -> "StrategySpec":
        names = tuple(p.name for p in self.parameters)
        if len(names) > 100 or tuple(sorted(set(names))) != names:
            raise ValueError("PARAMETERS_MUST_BE_UNIQUE_AND_SORTED")
        return self


class Intent(Frozen):
    """Callback proposal only. The engine assigns identity, instrument and event time."""
    side: Side
    quantity: Quantity
    reason: Label


class Position(Frozen):
    quantity: int
    entry_price: Decimal
    entry_costs: Decimal
    unrealized_gross: Decimal


class Decision(Frozen):
    instrument_id: str
    decision_time: datetime
    completed_bars: tuple[Bar, ...]
    position: Position
    cash: Decimal
    realized_net: Decimal
    parameters: tuple[Parameter, ...]
    session_open: Instant | None = None
    session_close: Instant | None = None


class BacktestStrategy(Protocol):
    def on_decision(self, context: Decision) -> Intent | None: ...


class IntentOutcome(Frozen):
    intent_id: int
    created_at: Instant
    resolved_at: Instant
    side: Side
    quantity: int
    reason: str
    outcome: str


class Fill(Frozen):
    intent_id: int
    instrument_id: str
    side: Side
    quantity: int
    execution_time: Instant
    reference_price: Decimal
    adverse_slippage: Decimal
    fill_price: Decimal
    gross_notional: Decimal
    costs: Decimal
    forced: bool


class Trade(Frozen):
    entry: Fill
    exit: Fill
    gross_pnl: Decimal
    costs: Decimal
    net_pnl: Decimal


class EquityPoint(Frozen):
    decision_time: Instant
    cash: Decimal
    position_quantity: int
    mark_price: Decimal
    unrealized_gross: Decimal
    realized_net: Decimal
    equity: Decimal


class Metrics(Frozen):
    trade_count: int
    wins: int
    losses: int
    breakeven: int
    gross_pnl: Decimal
    costs: Decimal
    net_pnl: Decimal
    return_percent: Decimal
    win_rate_percent: Decimal
    max_drawdown: Decimal
    max_drawdown_percent: Decimal


class Result(Frozen):
    engine_version: str
    dataset_fingerprint: str
    dataset_artifact_fingerprint: str
    instrument_id: str
    interval: str
    from_inclusive: Instant
    to_exclusive: Instant
    dataset_cutoff: Instant
    decision_cutoff: Instant
    strategy: StrategySpec
    config: Config
    outcomes: tuple[IntentOutcome, ...]
    fills: tuple[Fill, ...]
    trades: tuple[Trade, ...]
    equity_curve: tuple[EquityPoint, ...]
    final_cash: Decimal
    final_position: int
    metrics: Metrics

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


def canonical_json(model: Frozen) -> str:
    def normalize(value: Any) -> Any:
        if isinstance(value, Decimal):
            return decimal_text(value)
        if isinstance(value, datetime):
            return instant_text(value)
        if isinstance(value, dict):
            return {key: normalize(item) for key, item in value.items()}
        if isinstance(value, (tuple, list)):
            return [normalize(item) for item in value]
        if hasattr(value, "isoformat"):
            return value.isoformat()
        return value

    return json.dumps(normalize(model.model_dump()), sort_keys=True,
                      separators=(",", ":"), ensure_ascii=True, allow_nan=False)


def percent(numerator: Decimal, denominator: Decimal) -> Decimal:
    if not denominator:
        return ZERO
    with localcontext(Context(prec=80, rounding=ROUND_HALF_EVEN)):
        return (numerator * 100 / denominator).quantize(Decimal("0.00000001"))


def run(dataset: Dataset, config: Config, spec: StrategySpec, strategy: BacktestStrategy) -> Result:
    """Pure inputs; no I/O. Invalid data/forced exits yield no successful result."""
    # Validate again even if a caller used Pydantic's unsafe construct/copy helpers.
    dataset = Dataset.model_validate(dataset.model_dump())
    config = Config.model_validate(config.model_dump())
    spec = StrategySpec.model_validate(spec.model_dump())
    for day in dataset.calendar.days:
        for session in day.sessions:
            if not (session.open <= config.entry_start <= config.last_entry_time
                    < config.forced_exit_time < session.close):
                raise ValueError("SESSION_POLICY_OUTSIDE_SESSION")
    if isinstance(config.costs, IntradayCostSchedule):
        for bar in dataset.bars:
            config.costs.check_date(config.costs.as_of or bar.start.astimezone(NSE).date())
    try:
        with localcontext(ARITHMETIC):
            return _simulate(dataset, config, spec, strategy)
    except DecimalException:
        raise ValueError("ARITHMETIC_FAILURE") from None


def _simulate(dataset: Dataset, config: Config, spec: StrategySpec,
              strategy: BacktestStrategy) -> Result:
    cash = config.initial_cash
    realized = ZERO
    entry: Fill | None = None
    pending: tuple[int, datetime, Intent] | None = None
    next_id = 1
    outcomes: list[IntentOutcome] = []
    fills: list[Fill] = []
    trades: list[Trade] = []
    curve: list[EquityPoint] = []

    def outcome(item: tuple[int, datetime, Intent], at: datetime, status: str) -> None:
        identity, created, intent = item
        outcomes.append(IntentOutcome(intent_id=identity, created_at=created, resolved_at=at,
                                      side=intent.side, quantity=intent.quantity,
                                      reason=intent.reason, outcome=status))

    for index, bar in enumerate(dataset.bars):
        local = bar.start.astimezone(NSE)
        forced = local.time() == config.forced_exit_time and entry is not None
        if forced:
            if pending is not None:
                outcome(pending, bar.start, "SUPERSEDED_BY_FORCED_EXIT")
            assert entry is not None
            pending = (next_id, bar.start,
                       Intent(side="SELL", quantity=entry.quantity, reason="FORCED_EXIT"))
            next_id += 1
        if pending is not None:
            identity, created, intent = pending
            rejection: str | None = None
            if intent.quantity % dataset.lot_size:
                rejection = "LOT_SIZE_VIOLATION"
            elif bar.volume == 0:
                rejection = "NO_EXECUTABLE_VOLUME"
            elif intent.side == "BUY":
                if entry is not None:
                    rejection = "ALREADY_LONG"
                elif not config.entry_start <= local.time() <= config.last_entry_time:
                    rejection = "OUTSIDE_ENTRY_WINDOW"
            elif entry is None or intent.quantity > entry.quantity:
                rejection = "OVERSELL"
            elif intent.quantity != entry.quantity:
                rejection = "PARTIAL_EXIT_UNSUPPORTED"
            price = config.slippage.price(intent.side, bar.open)
            notional = price * intent.quantity
            if isinstance(config.costs, IntradayCostSchedule):
                # Invalid sells have no entry basis; reject without requesting a quote.
                fee = (ZERO if rejection is not None else config.costs.quote(
                    intent.side, notional, bar.start,
                    None if entry is None else entry.gross_notional).total)
            else:
                fee = config.costs.costs(notional)
            if rejection is None and (
                (intent.side == "BUY" and cash < notional + fee)
                or (intent.side == "SELL" and cash + notional < fee)
            ):
                rejection = "INSUFFICIENT_CASH"
            if rejection is not None:
                if forced:
                    raise ValueError("FORCED_EXIT_UNAVAILABLE")
                outcome(pending, bar.start, rejection)
            else:
                fill = Fill(intent_id=identity, instrument_id=dataset.instrument_id,
                            side=intent.side, quantity=intent.quantity, execution_time=bar.start,
                            reference_price=bar.open, adverse_slippage=abs(price - bar.open),
                            fill_price=price, gross_notional=notional, costs=fee, forced=forced)
                fills.append(fill)
                outcome(pending, bar.start, "FILLED")
                if intent.side == "BUY":
                    cash -= notional + fee
                    entry = fill
                else:
                    assert entry is not None
                    cash += notional - fee
                    gross = notional - entry.gross_notional
                    costs = entry.costs + fee
                    trade = Trade(entry=entry, exit=fill, gross_pnl=gross,
                                  costs=costs, net_pnl=gross - costs)
                    trades.append(trade)
                    realized += trade.net_pnl
                    entry = None
            pending = None

        quantity = 0 if entry is None else entry.quantity
        entry_price = ZERO if entry is None else entry.fill_price
        entry_fee = ZERO if entry is None else entry.costs
        unrealized = (bar.close - entry_price) * quantity
        equity = cash + bar.close * quantity
        if equity != config.initial_cash + realized + unrealized - entry_fee:
            raise ValueError("LEDGER_INCONSISTENT")
        curve.append(EquityPoint(decision_time=bar.end, cash=cash, position_quantity=quantity,
                                 mark_price=bar.close, unrealized_gross=unrealized,
                                 realized_net=realized, equity=equity))
        day = next(d for d in dataset.calendar.days if d.date == local.date())
        session = day.sessions[0]  # Dataset validation requires one confirmed complete session.
        context = Decision(instrument_id=dataset.instrument_id, decision_time=bar.end,
                           completed_bars=dataset.bars[:index + 1],
                           position=Position(quantity=quantity, entry_price=entry_price,
                                             entry_costs=entry_fee, unrealized_gross=unrealized),
                           cash=cash, realized_net=realized, parameters=spec.parameters,
                           session_open=datetime.combine(day.date, session.open, NSE)
                           .astimezone(UTC),
                           session_close=datetime.combine(day.date, session.close, NSE)
                           .astimezone(UTC))
        try:
            # A callback cannot change the engine's Decimal precision or traps.
            with localcontext(ARITHMETIC):
                proposal = strategy.on_decision(context)
                if proposal is not None:
                    proposal = Intent.model_validate(proposal.model_dump())
        except Exception:
            raise ValueError("STRATEGY_FAILURE") from None
        if proposal is not None:
            pending = (next_id, bar.end, proposal)
            next_id += 1
        final = index == len(dataset.bars) - 1
        session_end = final or dataset.bars[index + 1].start.astimezone(NSE).date() != local.date()
        if session_end:
            if entry is not None:
                raise ValueError("OVERNIGHT_POSITION_FORBIDDEN")
            if pending is not None:
                outcome(pending, bar.end,
                        "UNFILLED_END_OF_DATA" if final else "UNFILLED_SESSION_END")
                pending = None

    peak = config.initial_cash
    drawdown = ZERO
    drawdown_percent = ZERO
    for point in curve:
        peak = max(peak, point.equity)
        drawdown = max(drawdown, peak - point.equity)
        drawdown_percent = max(drawdown_percent, percent(peak - point.equity, peak))
    costs = sum((f.costs for f in fills), ZERO)
    gross = sum((t.gross_pnl for t in trades), ZERO)
    if entry is not None or cash != config.initial_cash + realized or realized != gross - costs:
        raise ValueError("FINAL_LEDGER_INCONSISTENT")
    wins = sum(t.net_pnl > 0 for t in trades)
    losses = sum(t.net_pnl < 0 for t in trades)
    metrics = Metrics(trade_count=len(trades), wins=wins, losses=losses,
                      breakeven=len(trades) - wins - losses, gross_pnl=gross, costs=costs,
                      net_pnl=realized, return_percent=percent(realized, config.initial_cash),
                      win_rate_percent=percent(Decimal(wins), Decimal(len(trades))),
                      max_drawdown=drawdown, max_drawdown_percent=drawdown_percent)
    return Result(engine_version=(ENGINE_VERSION if isinstance(config.costs, ConfiguredCosts)
                                  else "intraday-next-open-v2-costs"),
                  dataset_fingerprint=dataset.content_fingerprint,
                  dataset_artifact_fingerprint=digest(canonical_json(dataset)),
                  instrument_id=dataset.instrument_id, interval=dataset.interval,
                  from_inclusive=dataset.from_inclusive, to_exclusive=dataset.to_exclusive,
                  decision_cutoff=dataset.decision_cutoff, dataset_cutoff=dataset.dataset_cutoff,
                  strategy=spec, config=config, outcomes=tuple(outcomes), fills=tuple(fills),
                  trades=tuple(trades), equity_curve=tuple(curve), final_cash=cash,
                  final_position=0, metrics=metrics)
