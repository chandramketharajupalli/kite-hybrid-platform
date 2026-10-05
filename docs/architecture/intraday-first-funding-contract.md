# Intraday-first funding and strategy boundary

Phase 10.8D decision: retain the existing fail-closed implementation and make its
accepted evidence contract explicit. No new production funding formula, evidence
provider, configuration switch or execution surface is introduced.

## Current product and scope

The primary current requirement is **NSE cash-equity INTRADAY/MIS**. The reviewed
first-live shape is SBIN BUY / MARKET / MIS / DAY / REGULAR, full conservative
pre-trade notional at most INR 10,000, future arm at most 30 seconds (or a stricter
configured bound), one submission attempt, no automatic retry. This is not
authorization to enable, resume, arm or execute.

Historical-data-driven intraday strategies are the current strategy direction.
F&O, DELIVERY expansion, Forex and Commodity are future product families. The
existing CNC safety path remains intact. This MIS contract does not generalize
its accepted funding evidence to those products, SELL, derivatives, MTF or
unsupported order shapes.

## Minimum machine-enforceable contract

Existing types already carry the narrow contract:

| Evidence | Existing owner/type | Accepted meaning |
| --- | --- | --- |
| Exact intent | `OrderMarginQuote.Request` | Platform identity, NSE symbol, BUY/MARKET/MIS/DAY/REGULAR and exact Q |
| Price and policy | `Tick`, `RiskLimits`, `ConservativeOrderValuation` | Fresh positive price, shared validated buffer, exact full notional |
| Required margin and charges | `OrderMarginQuote` | Successful bounded calculation for the locally bound exact request; positive margin, nonnegative charges |
| Current equity/account evidence | Normalized read ports and `OrderRiskInput` | Complete synchronous positions, holdings, equity margins and orders; no cached cross-attempt snapshot |
| Accepted cash | `CashAccountCapacity`, `IntradayAccountCapacity.Funding` | Conservative application lower bound defined below, not a claim to reproduce broker RMS |
| Collateral terms, if needed | Optional `OrderMarginQuote.CollateralTerms` | Authoritative current eligible adjusted aggregate and exact-request minimum cash; absence is UNKNOWN |
| Reserve | `RiskLimits.cashReserve` | Positive configured cash reserve, also included in total funding requirement |
| Freshness/binding | Local quote receipt time, current synchronous reads, reference/session identities | No invented broker timestamp, atomic snapshot or reservation |

For exact Q, all of these are independent requirements:

```text
N(Q) = freshPrice * validatedBuffer * Q
N(Q) <= each applicable full-notional cap
projected target units <= maxPositionQuantity
existing buffered exposure + N(Q) <= maxExposure
accepted cash >= minimum cash requirement + estimated charges + cash reserve
eligible total funding >= required margin + estimated charges + cash reserve
```

The human INR 10,000 is an immutable review ceiling. For a future controlled
order, the reviewed risk max-order-value, normal max-notional and first-live
max-notional configuration must also enforce that ceiling or a stricter one.
There is no generic production hard-code for SBIN or INR 10,000. No such real
configuration is set in this phase.

## Application cash policy versus broker eligibility

The application deliberately accepts a lower bound instead of choosing a
positive-looking field. Define `positive(x)=max(x,0)`:

```text
CNC_cash = min(cash, openingBalance, liveBalance, equity.net)
           - positive(debits) - positive(payout) - positive(holdingSales)
excluded = intradayPayin + adhocMargin
           + positive(realisedM2M) + positive(unrealisedM2M)
           + abs(optionPremium)
C = CNC_cash - excluded
B = equity.net - excluded - positive(debits) - payout - holdingSales
```

This is the existing application acceptance policy. It can understate broker
capacity through overlapping deductions, including pay-in against an opening
floor. It does not claim that the broker prohibits those funds, nor that the
result is broker-reported debt. The decision to retain it is conservative and
does not resolve which JSON field broker RMS uses for its positive-cash rule.
No later positive live balance can independently override a negative floor.

Normalized values must be complete and bounded. Equity must be enabled.
Prohibited negative usage/collateral components deny; malformed/missing evidence
denies. Commodity is never an NSE funding source. No collateral or leverage is
relabeled as cash. The reserve stays cash-only, not a generic permission to use
extra collateral.

## Two accepted funding modes

### Cash-only

When collateral terms are absent:

```text
E = 0
minimumCash = requiredMargin
F = min(C, B)
```

Both cash and total funding comparisons must pass including charges/reserve.
Collateral may exist in the account without being needed by this exact order.
If cash suffices, lack of collateral terms is irrelevant to that funding gate;
other account/risk/session gates still apply. If cash is insufficient and
collateral would be needed, absence of terms returns `COLLATERAL_UNSUPPORTED`.
Without reported collateral, insufficient cash returns `MIS_MARGIN_INSUFFICIENT`.

### Collateral-assisted

Only a trusted current provider may supply complete terms `T`:

```text
E = max(0, min(availableCollateral, T.eligibleAdjustedCollateral)
           - utilisedLiquidCollateral - utilisedStockCollateral)
minimumCash = T.minimumCash
F = min(C + E, B)
```

The existing utilised deductions are conservative application fences, not an
authoritative reconstruction of broker free collateral. They may overlap broker
accounting. They cannot manufacture funding. The same amount in available and
utilised stock collateral yields no added eligible collateral under this policy,
never twice that amount. Net is solely a tightening bound, never an extra pool.

The numeric domain type cannot authenticate the provenance of terms. Production
must obtain them from an authoritative provider for the same account, product,
exact request and current observation. It must not populate them from a user
toggle, old report, pledge label, successful estimate, guessed haircut, or
arithmetic on net. **The current Kite adapter always leaves terms absent.**

An authoritative **aggregate** eligible adjusted amount plus applicable cash
terms could satisfy this boundary without reconstructing every pledged security.
Per-security composition is one possible evidence route, not a required new
general-purpose collateral engine. This narrows the future evidence route
described in Phase 10.8C; it does not turn raw aggregate collateral into complete
terms. No such aggregate eligibility/cash-terms provider is currently established.

## Why aggregate collateral plus a calculator result is insufficient today

The official [calculation response](https://kite.trade/docs/connect/v3/margins/)
supplies required margin and charges; it does not certify eligible adjusted
account collateral or a cash-component condition. `KiteOrderMarginAdapter`
accepts only its supported single-equity shape, binds it to the local request,
and supplies no collateral terms. The [margin field definitions](https://kite.trade/docs/connect/v3/user/#funds-and-margins)
distinguish available from utilised collateral and several cash/balance fields;
they do not supply that missing exact-account eligibility contract. Sources
rechecked 2026-10-05. No generic intraday-collateral feasibility investigation was
needed or repeated.

Consequently `availableCollateral >= margin` or `net >= margin` cannot independently
authorize funding. This is a missing evidence input, not a failure to recognize
collateral-assisted intraday trading. Keep UNKNOWN absent and deny when required.
No production correction has been demonstrated that would justify removing this
condition.

## Full-notional sizing is independent

```text
unit = freshPrice * shared validated buffer
budgetUnits = floor(human full-notional ceiling / unit)
Q = lot-floor(min(budgetUnits, applicable hard quantity/position caps))
```

The planner also applies stricter normal/first-live/risk/exposure ceilings and
account headroom, then recomputes exact notional through the shared evaluator.
INR 9,999.99 and 10,000.00 pass a 10,000 ceiling; 10,000.01 fails. No upward
rounding or margin-per-share division. More collateral cannot increase Q.
The margin calculation is a subsequent **exact-Q** gate, not a sizing multiplier.
Changed quantity requires a new reviewed candidate/order/risk identity.

Planning is observational. No OrderId, executable order, authorization, correlation,
permit or runtime arm is created. Its positive status remains
`SIZED_RECHECK_REQUIRED`, not a dispatch authorization. Phase 10.8D does not
start real price collection while funding prerequisites remain unresolved.
MARKET fill value is not guaranteed by a pre-trade ceiling.

## Actual timing and final checks

```text
fresh market observation + shared ConservativeOrderValuation
  -> pure quantity sizing + observational FirstLiveCandidatePlanner
  -> separately created exact order / RiskService (current account + quote)
       -> CashOrderRiskRules: full notional/exposure + MIS funding gate
  -> explicit operator preflight (current account + quote; no permit consumption)
  -> separately authorized one-shot permit / execution invocation
  -> ExecutionSafetyPolicy, historical risk identity and current local evidence
  -> PostgreSQL account admission / repeated valuation fences / CAS SUBMITTING
  -> post-admission checks / KiteOrderAdapter captures reference and encodes intent
  -> KiteRestTransport session lock, latest pre-HTTP callback:
       refresh account + exact quote
       repeat durable/local market, full valuation, funding, auth/init,
         identity, permit/expiry, HALT and reference checks
       compare captured instrument with current reference
  -> at most one order HTTP attempt; ambiguous outcomes require reconciliation
```

The final account refresh runs inside the adapter/transport callback, not ahead
of adapter construction as a purely linear conceptual graph might suggest.
Admission fences revalue but do not issue duplicate account reads. Risk,
preflight and final transport each use the same capacity semantics; historical
risk evidence is never rewritten by revalidation. Each successful MIS account
observation costs four account GETs plus one calculation POST, with no N+1 fetch
per holding. These are sequential, not an atomic broker snapshot. Rechecks do
not reserve funds or eliminate the race after the final observation.

Current holdings/positions still contribute to exposure, including conservative
overlapping holding buckets. They require fresh prices for existing exposure;
an SBIN-only stream alone is not a claim of portfolio-wide evidence coverage.
First-live review additionally requires zero nonzero positions and no unresolved
broker/SBIN orders. Generic risk can support positions within its reviewed caps;
this phase does not replace that policy with a new blanket rule.

HALT remains local and immediate during blocked reads. Session replacement and
initialization loss deny. Execute consumes the one-shot attempt even on denial;
preflight does not. No funding recovery revives a consumed permit or authorizes
retry. Explicit reconciliation, including MIS identity, remains available while
halted without resubmission.

## Historical-data and strategy handoff (design only)

```text
historical market data -> normalized bars/ticks -> features/indicators
  -> strategy signal -> backtest -> cost/slippage model
  -> walk-forward / out-of-sample validation -> paper execution
  -> separately risk-approved and authorized live MIS execution
```

Java owns broker credentials, reference normalization, authoritative order state,
risk, authorization, execution and reconciliation. Python owns research/strategy
calculations and emits versioned signals only. Signal reference prices are
informational and cannot replace current trusted price evidence for live risk.

Future datasets must record instrument/time identity, provenance, revision and
adjustment policy, exchange timestamps/timezone, gaps/duplicates and session
coverage. Features must use only data available at the simulated decision time.
Backtests need realistic costs, slippage, latency, partial/no fills and intraday
session/exit assumptions; immutable dataset/strategy versions and time-ordered
out-of-sample evaluation prevent leakage. These are handoff requirements, not
new components implemented here.

```text
Strategy -> order intent -> RiskService
  -> explicit operator / separately reviewed future automation authorization
  -> ExecutionSafetyPolicy -> Kite adapter
```

Strategies never call Kite or execution/arming services. Current
`StrategyOrderCoordinator` creates DELIVERY proposals and optionally evaluates
risk; it does not execute. MIS intent routing and historical strategy plumbing
remain future implementation work and must not be advertised as already wired.
No scheduler or automation authorization is added now. Execution stays
independent of any particular strategy, feature set or backtest framework.

F&O, DELIVERY expansion, Forex and Commodity require separate reviewed product,
funding, lifecycle and session contracts. Keep existing domain/read-port seams;
do not add speculative product abstractions or relax current MIS controls.
