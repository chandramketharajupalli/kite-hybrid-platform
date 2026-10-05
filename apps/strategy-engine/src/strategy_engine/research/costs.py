"""Observed tariff snapshot factory. No HTTP and no automatic rate updates."""
from datetime import date

from strategy_engine.backtest.costs import Charges, IntradayCostSchedule
from strategy_engine.backtest.engine import Result


def nse_intraday_snapshot(*, fixed_as_of: date | None = None) -> IntradayCostSchedule:
    """Current-rate scenario for older data must be explicit, never silently backdated."""
    return IntradayCostSchedule.model_validate(dict(
        version="nse-retail-intraday-20261006-v1",
        source_version="zerodha-nse-reviewed-20261006",
        effective_from="2026-10-06", effective_to="2026-10-07",
        basis="HISTORICAL" if fixed_as_of is None else "FIXED_AS_OF", as_of=fixed_as_of,
        scope="NSE_CASH_SHARES_RESIDENT_RETAIL_FULL_CASH",
        rounding="EXACT_ACCRUAL_NOT_CONTRACT_NOTE", stt_basis="FLAT_ROUND_TRIP_AVERAGE",
        brokerage_rate="0.0003", brokerage_cap="20", stt_rate="0.00025",
        exchange_rate="0.000030699", ipft_rate="0.000000001", sebi_rate="0.000001",
        gst_rate="0.18", stamp_rate="0.00003"))


def audit_costs(result: Result) -> tuple[Charges, ...]:
    if not isinstance(result.config.costs, IntradayCostSchedule):
        return ()
    entry = None
    audit: list[Charges] = []
    for fill in result.fills:
        quote = result.config.costs.quote(fill.side, fill.gross_notional,
                                         fill.execution_time, entry)
        if quote.total != fill.costs:
            raise ValueError("COST_AUDIT_MISMATCH")
        audit.append(quote)
        entry = fill.gross_notional if fill.side == "BUY" else None
    return tuple(audit)
