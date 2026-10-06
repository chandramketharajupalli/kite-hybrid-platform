"""Frozen, offline-only H1/H2 callbacks; no optimizer, routing or live integration."""

from datetime import UTC, datetime
from decimal import Decimal
from typing import Literal

from pydantic import model_validator

from strategy_engine.backtest.dataset import MINUTE, NSE, Frozen, Hash, Instant
from strategy_engine.backtest.engine import Config, Decision, Intent, Parameter, StrategySpec
from strategy_engine.research.candidate_features import signed_efficiency_30
from strategy_engine.research.features import CompletedSession, vwap
from strategy_engine.research.strategy import BaselineSpec, BaselineStrategy

REGISTRATION = "2fd4b740d3459e366a7d0a26483936d1cf81d700a6b270c334c65e5e951e78d2"
JULY_START = datetime(2026, 6, 30, 18, 30, tzinfo=UTC)
JULY_END = datetime(2026, 7, 31, 18, 30, tzinfo=UTC)


def reject_sealed_window(start: datetime, end: datetime) -> None:
    if start.tzinfo is None or end.tzinfo is None or start >= end:
        raise ValueError("INVALID_CANDIDATE_WINDOW")
    if start < JULY_END and end > JULY_START:
        raise ValueError("JULY_TEST_SEALED")


class CandidateSpec(Frozen):
    hypothesis: Literal["H1", "H2"]
    version: Literal["G1"] = "G1"
    registration_fingerprint: Literal[
        "2fd4b740d3459e366a7d0a26483936d1cf81d700a6b270c334c65e5e951e78d2"
    ] = "2fd4b740d3459e366a7d0a26483936d1cf81d700a6b270c334c65e5e951e78d2"
    implementation_fingerprint: Hash
    comparator: BaselineSpec

    @model_validator(mode="after")
    def fixed_rules(self) -> "CandidateSpec":
        expected = (
            {"kind": "EMA_CROSS", "quantity": 1, "fast": 9, "slow": 21}
            if self.hypothesis == "H1"
            else {"kind": "VWAP_CROSS", "quantity": 1}
        )
        if self.comparator.parameters.model_dump() != expected:
            raise ValueError("FROZEN_COMPARATOR_MISMATCH")
        return self

    def engine_spec(self) -> StrategySpec:
        values = {p.name: p.value for p in self.comparator.engine_spec().parameters}
        values.update(hypothesis=self.hypothesis, registration=self.registration_fingerprint)
        if self.hypothesis == "H1":
            values.update(changes="30", minimum_signed_efficiency="0.20")
        else:
            values.update(confirmation_bars="1", confirmation="STRICTLY_ABOVE_PREFIX_VWAP")
        return StrategySpec(
            name=(
                "EMA_DIRECTIONAL_PERSISTENCE_G1"
                if self.hypothesis == "H1"
                else "VWAP_ONE_BAR_CONFIRMATION_G1"
            ),
            version=self.version,
            implementation_fingerprint=self.implementation_fingerprint,
            parameters=tuple(Parameter(name=k, value=v) for k, v in sorted(values.items())),
        )


class PendingEntry(Frozen):
    session_open: Instant
    crossover_at: Instant


class CandidateStrategy:
    """One fresh instance per engine run. State belongs only to this simulation."""

    def __init__(self, specification: CandidateSpec, config: Config) -> None:
        self.specification = CandidateSpec.model_validate(specification.model_dump())
        self.config = Config.model_validate(config.model_dump())
        self._baseline = BaselineStrategy(
            specification=self.specification.comparator, config=self.config
        )
        self._pending: PendingEntry | None = None
        self._last_session: datetime | None = None
        self._last_decision: datetime | None = None

    @property
    def pending(self) -> PendingEntry | None:
        return self._pending

    def on_decision(self, context: Decision) -> Intent | None:
        if context.parameters != self.specification.engine_spec().parameters:
            self._pending = None
            raise ValueError("CANDIDATE_PARAMETER_BINDING_MISMATCH")
        try:
            current = CompletedSession.from_decision(context)
            reject_sealed_window(current.opened_at, current.closes_at)
        except ValueError:
            self._pending = None
            raise
        baseline_context = context.model_copy(
            update={"parameters": self.specification.comparator.engine_spec().parameters}
        )
        if self.specification.hypothesis == "H1":
            proposal = self._baseline.on_decision(baseline_context)
            if proposal is not None and proposal.side == "BUY":
                efficiency = signed_efficiency_30(current)
                return (
                    proposal if efficiency is not None and efficiency >= Decimal("0.20") else None
                )
            return proposal

        same_session = self._last_session == current.opened_at
        consecutive = (
            same_session
            and self._last_decision is not None
            and current.decision_at == self._last_decision + MINUTE
        )
        broken = same_session and not consecutive
        if not same_session or broken:
            self._pending = None
        self._last_session, self._last_decision = current.opened_at, current.decision_at
        # Do not infer a crossover on a skipped/reversed/repeated decision callback.
        if broken:
            return None
        proposal = self._baseline.on_decision(baseline_context)
        if context.position.quantity:
            self._pending = None
            return proposal  # Original sell/forced-exit behavior, never confirmation-delayed.
        clock = current.decision_at.astimezone(NSE).time()
        if (
            current.decision_at >= current.closes_at
            or not self.config.entry_start <= clock <= self.config.last_entry_time
        ):
            self._pending = None
            return None
        pending = self._pending
        self._pending = None  # Confirmation is single-use, including a failed/no-volume fill.
        if pending is not None:
            if (
                consecutive
                and pending.session_open == current.opened_at
                and current.decision_at == pending.crossover_at + MINUTE
            ):
                value = vwap(current)
                if value is not None and current.bars[-1].close > value:
                    return Intent(side="BUY", quantity=1, reason="H2_CONFIRMED")
            return None
        if proposal is not None and proposal.side == "BUY":
            self._pending = PendingEntry(
                session_open=current.opened_at, crossover_at=current.decision_at
            )
        return None
