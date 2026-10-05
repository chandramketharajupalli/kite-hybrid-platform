"""Pure, explicitly supplied NSE share intraday research tariff; no broker access."""
from datetime import date, datetime
from decimal import (
    Context,
    Decimal,
    DivisionByZero,
    Inexact,
    InvalidOperation,
    Overflow,
    localcontext,
)
from typing import Literal

from pydantic import model_validator

from strategy_engine.backtest.dataset import NSE, Frozen, Label, Money, TradingDate

EXACT = Context(prec=80, traps=[InvalidOperation, DivisionByZero, Overflow, Inexact])

class Charges(Frozen):
    brokerage: Decimal
    stt: Decimal
    exchange: Decimal
    ipft: Decimal
    sebi: Decimal
    gst: Decimal
    stamp: Decimal

    @property
    def total(self) -> Decimal:
        with localcontext(EXACT):
            return sum((self.brokerage, self.stt, self.exchange, self.ipft,
                        self.sebi, self.gst, self.stamp), Decimal(0))


class IntradayCostSchedule(Frozen):
    version: Label
    source_version: Label
    effective_from: TradingDate
    effective_to: TradingDate  # exclusive; unknown dates must not silently reuse a tariff
    basis: Literal["HISTORICAL", "FIXED_AS_OF"]
    as_of: TradingDate | None
    scope: Literal["NSE_CASH_SHARES_RESIDENT_RETAIL_FULL_CASH"]
    rounding: Literal["EXACT_ACCRUAL_NOT_CONTRACT_NOTE"]
    stt_basis: Literal["FLAT_ROUND_TRIP_AVERAGE"]
    brokerage_rate: Money
    brokerage_cap: Money
    stt_rate: Money
    exchange_rate: Money
    ipft_rate: Money
    sebi_rate: Money
    gst_rate: Money
    stamp_rate: Money

    @model_validator(mode="after")
    def valid(self) -> "IntradayCostSchedule":
        if self.effective_from >= self.effective_to:
            raise ValueError("INVALID_COST_PERIOD")
        if (self.basis == "FIXED_AS_OF") != (self.as_of is not None):
            raise ValueError("COST_DATE_BASIS_MISMATCH")
        if self.as_of is not None:
            self.check_date(self.as_of)
        rates = (self.brokerage_rate, self.stt_rate, self.exchange_rate, self.ipft_rate,
                 self.sebi_rate, self.gst_rate, self.stamp_rate)
        if any(rate > 1 for rate in rates):
            raise ValueError("INVALID_COST_RATE")
        return self

    def check_date(self, trading_date: date) -> None:
        if not self.effective_from <= trading_date < self.effective_to:
            raise ValueError("COST_SCHEDULE_NOT_EFFECTIVE")

    def quote(self, side: Literal["BUY", "SELL"], notional: Decimal,
              executed_at: datetime, entry_notional: Decimal | None) -> Charges:
        if executed_at.tzinfo is None or side not in {"BUY", "SELL"}:
            raise ValueError("INVALID_COST_CONTEXT")
        self.check_date(self.as_of or executed_at.astimezone(NSE).date())
        if not notional.is_finite() or notional <= 0:
            raise ValueError("INVALID_NOTIONAL")
        if side == "SELL" and (entry_notional is None or not entry_notional.is_finite()
                               or entry_notional <= 0):
            raise ValueError("ENTRY_BASIS_REQUIRED")
        with localcontext(EXACT):
            brokerage = min(self.brokerage_cap, notional * self.brokerage_rate)
            exchange = notional * self.exchange_rate
            ipft = notional * self.ipft_rate
            sebi = notional * self.sebi_rate
            stt = (Decimal(0) if side == "BUY" else
                   (notional + (entry_notional or Decimal(0))) / 2 * self.stt_rate)
            return Charges(brokerage=brokerage, stt=stt, exchange=exchange, ipft=ipft, sebi=sebi,
                           gst=(brokerage + exchange + ipft + sebi) * self.gst_rate,
                           stamp=notional * self.stamp_rate if side == "BUY" else Decimal(0))
