# NSE cash-share intraday research cost calibration

Reviewed 2026-10-05/06; snapshot observation date **2026-10-06**. No Kite API,
account, margin, contract-note or market-data calls. Sources below are public
official documentation. Scope is NSE ordinary equity shares, resident retail,
full-cash intraday simulation; not delivery, ETFs with different exemptions,
F&O, MTF, NRI, debit-balance or special account tariffs.

## Accepted rates and applicability

| Component | Research calculation | Side | Authority |
| --- | --- | --- | --- |
| Brokerage | min(0.0003 × executed notional, INR 20), per executed order | Both | [Zerodha charges](https://zerodha.com/charges/) |
| STT | 0.00025 × unrounded non-deliverable netted value | Sell obligation | [Zerodha STT](https://support.zerodha.com/category/account-opening/resident-individual/ri-charges/articles/how-is-the-securities-transaction-tax-stt-calculated), [NSE methodology](https://nsearchives.nseindia.com/content/circulars/cmtr5588.htm) |
| Exchange | 0.000030699 × executed notional | Both | [NSE FA73061](https://nsearchives.nseindia.com/content/circulars/FA73061.pdf) |
| IPFT | 0.000000001 × executed notional | Both | [NSE FA73061](https://nsearchives.nseindia.com/content/circulars/FA73061.pdf) |
| SEBI | 0.000001 × executed notional | Both | [NSE levies](https://www.nseindia.com/static/invest/first-time-investor-sebi-turnover-fees-stt-other-levies), [Zerodha charges](https://zerodha.com/charges/) |
| GST | 0.18 × (brokerage + exchange + IPFT + SEBI) | Both | [Zerodha charges](https://zerodha.com/charges/) |
| Stamp | 0.00003 × executed notional | Buy only | [Zerodha July 2020 notice](https://zerodha.com/marketintel/bulletin/259741/), [NSE levies](https://www.nseindia.com/static/invest/first-time-investor-sebi-turnover-fees-stt-other-levies) |

All coefficients above are **ratios**, not percentages. Money is INR. One
simulation fill represents one completely executed order, consistent with the
engine's no-partial-fill model. Fees use slippage-adjusted fill notional.

## Exchange/IPFT reconciliation and date evidence

The February 27, 2026 NSE circular applies from March 1, 2026. Its cash-market
table changes exchange/IPFT allocation from INR 297/10 per crore each side to
INR 306.99/0.01, leaving total INR 307. We inspected the official PDF/table.
The current broker page's 0.00307% is consistent with that combined amount.
The model splits the components; it does **not** add IPFT on top of a second
307-per-crore pool. GST includes that combined exchange/IPFT amount once.
[NSE circular](https://nsearchives.nseindia.com/content/circulars/FA73061.pdf)

The stamp notice establishes its July 1, 2020 change; the current STT support
page explicitly separates unchanged equity rates from April 2026 F&O changes.
Those derivative rates are not imported into this model.
[Stamp notice](https://zerodha.com/marketintel/bulletin/259741/),
[STT explanation](https://support.zerodha.com/category/account-opening/resident-individual/ri-charges/articles/how-is-the-securities-transaction-tax-stt-calculated)

## Snapshot dates versus historical rate claims

Version: `nse-retail-intraday-20261006-v1`.
Source version: `zerodha-nse-reviewed-20261006`.
Application effective window: **[2026-10-06, 2026-10-07)**, NSE local trading date.
This deliberately bounded window is the verified observation-day contract, not
a claim that every component first took effect on October 6. Although the NSE
split has a documented March start, the present broker tariff page is not a
complete historical fee archive. No earlier/future bundle is silently certified.

Default `HISTORICAL` mode rejects dates outside that window, even a no-trade run.
For older research, callers explicitly choose `FIXED_AS_OF` with as_of=2026-10-06.
The entire schedule, basis and date enter engine/experiment fingerprints. Such a
report means **historical prices evaluated under the October 6 fee scenario**,
not historical realized costs. Later observations require a new immutable
version; there is no online updater, cache refresh or guessed future validity.

## STT basis and settlement limits

NSE's netted-settlement method aggregates security/day buy and sell value and
quantity, then uses their volume-weighted average for non-deliverable value.
For this engine every completed round trip has equal buy/sell quantity. Thus
the sum of `(entryNotional + exitNotional)/2` across closed trades equals the
**unrounded** session non-deliverable value. STT accrues only when each sale is
known. This requires no later trade, future price or revised historical fee.
The old circular's numeric tax rates are obsolete and are NOT used; its
methodology is corroborated by the current broker's intraday example.
[NSE method](https://nsearchives.nseindia.com/content/circulars/cmtr5588.htm),
[current broker example](https://support.zerodha.com/category/account-opening/resident-individual/ri-charges/articles/how-is-the-securities-transaction-tax-stt-calculated)

`EXACT_ACCRUAL_NOT_CONTRACT_NOTE` is an explicit research approximation. NSE's
method rounds daily VWAP to two decimals; the broker describes nearest-rupee STT
settlement (half up). Neither daily adjustment is applied to simulation cash.
Other invoice aggregation/rounding is also not certified. Consequently exact
Decimal calculations need not equal an actual bill, particularly for tiny trades.
No per-fill fake rupee rounding, hindsight adjustment or hidden refund is used.
This limitation is recorded with every schedule and result.

## Included/excluded operational charges

GST applies to service charges, not STT or stamp. The IPFT component carries GST.
The published broker page separately identifies dealer/auto-square-off,
debit-balance and special-account charges. Those are not ordinary self-directed
full-cash order fees. The engine's scheduled exit represents a planned strategy
liquidation, not broker RMS intervention. Funding interest, pledge/account/DP
costs and income tax are outside this trading-cost scenario. No real account
eligibility is inferred. [Broker charge scope](https://zerodha.com/charges/)

## Deterministic examples

For buy notional 10000: brokerage 3, exchange 0.30699, IPFT 0.00001,
SEBI 0.01, GST 0.59706, stamp 0.3, STT 0; total **4.21406**.
For its complete exit at 11000: brokerage 3.3, exchange/IPFT 0.3377 combined,
SEBI 0.011, GST 0.656766, STT 2.625, stamp 0; total **6.930466**.
These are exact research accruals, not an asserted contract-note invoice.

Tests cover sides, caps, rate units, nonnegative components, exact totals,
date rejection, explicit scenario identity, no exchange/IPFT double counting,
cash admission including costs, adverse-slippage notional and forced-exit fees.
