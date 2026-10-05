# Phase 10.7 — first-live candidate planning and SBIN rehearsal

## Decision

**No real order is authorized or attempted. Real first-live readiness remains
NOT_READY / RECHECK_REQUIRED.** A synthetic SBIN candidate can be sized and
passed through the actual operator, risk, PostgreSQL admission and loopback
transport boundaries. This does not certify current broker/account/session
conditions. In particular, the existing operator READY report does not certify
a new successful account read after risk approval or directly gate on the
authentication use case's `initializationReady` field.

The human ceiling of **INR 10,000 is a conservative PRE-TRADE valuation ceiling**.
A MARKET fill can exceed that value. No stale real tick was used to choose a
quantity, and the synthetic quantities below are not live recommendations.

## Baseline and isolation

Before edits: clean `develop`; HEAD and local `origin/develop` both
`ea72e00888f3184633d823cc2393668cc3a4197b`. The requested five Git checks passed.
No fetch, commit, push, reset, stash, clean, checkout or discard was performed.

Real Kite mutations: **0**. Real development database test mutations: **0**.
Real Kite reads in this phase: **0**. No real launcher, diagnostic, arm, resume,
candidate order, risk approval or authorization was invoked. Tests construct
Testcontainers PostgreSQL databases, synthetic account/session/reference data
and guarded `127.0.0.1` HTTP transports. No environment/configuration, universe,
allowlist, migration, token or real reconciliation evidence is changed.

The supplied request ended mid-sentence in section 27. This report covers the
requirements received through that point; no omitted continuation is assumed.

## Current SBIN candidate plan

| Field | Real evidence / decision |
|---|---|
| Exchange / symbol | NSE / SBIN |
| Universe membership | Exactly one normalized matching row, exactly one enabled row; zero duplicate or conflicting memberships |
| Intended shape | BUY / MARKET / DELIVERY / DAY / REGULAR |
| Human ceiling | INR 10,000, immutable input; stricter platform limits win |
| Arm request | At most 30 seconds; a smaller configured maximum wins; zero maximum denies |
| Maximum mutations / retry | One attempt / NEVER |
| Current InstrumentId, lot, tick, broker mapping, entitlement | CURRENT_REFERENCE_DATA = RECHECK_REQUIRED |
| Current market evidence / quantity / notional | RECHECK_REQUIRED / NONE / NONE |
| Current account and authentication | RECHECK_REQUIRED |
| Real database | V10_PREFLIGHT = RECHECK_REQUIRED; must be READY before a future attempt |
| Market session | MARKET_SESSION = OPERATOR_RECHECK_REQUIRED |
| Overall | NOT_READY for a real attempt |

Repository membership proves neither a current broker mapping nor exchange or
account tradability. No initialized local registry was established as current
through the available repository evidence; no production diagnostic was added.
The `Instrument` model has no enabled/tradable entitlement flag. Registry cash
semantics are canonical segment `CASH`, positive lot/tick, no expiry/strike;
an internal broker mapping is mandatory. Platform identity is derived from
canonical instrument attributes, never from the broker token. Current SBIN
attributes must be checked against a fresh initialized registry; this report
does not assert the synthetic lot 1 / tick 0.05 as current exchange facts.

DELIVERY maps to Kite `CNC`, MARKET to `MARKET`, DAY to `DAY`, with the fixed
regular-order route. The cash risk path permits BUY DELIVERY with MARKET/LIMIT.
This shape uses no derivatives, short selling, MTF, leverage or collateral credit.

## Implementation

`ConservativeOrderQuantitySizer` is broker-independent, immutable-result domain
arithmetic. It accepts explicit price, validated shared buffer, budget, lot and
nonnegative hard caps. It obtains unit value from `ConservativeOrderValuation`,
divides with scale zero / DOWN, clamps in decimal space before exact long
conversion, rounds the effective cap down to a whole lot, and recomputes exact
notional through the canonical valuation. Empty/negative/invalid inputs fail
closed. Zero budget/cap or an unaffordable lot produces zero quantity. There is
no fallback, upward rounding or automatic cap/buffer change.

`CashAccountCapacity` extracts the existing conservative holdings, positions,
exposure and cash arithmetic into a shared pure component. `CashOrderRiskRules`
and the planner's `Headroom.from` now use that same implementation. Risk still
checks order quantity/value and projected position/exposure/cash plus reserve.
Unsupported/incomplete evidence denies; account evidence errors can now be
reported before a simultaneously breached position/exposure limit, with no
expansion of the accepted set.

`FirstLiveCandidatePlanner` combines the human, normal execution, first-live,
risk order-value, risk exposure, account exposure and cash ceilings, and the
normal/first-live/risk/remaining-position quantity caps. It verifies every
independent ceiling after recomputation. It requires explicit fresh snapshots,
fresh account headroom, per-instrument desired and active subscription membership,
CONNECTED/FRESH/NONE feed health, positive price, and fresh receipt/exchange
timestamps. Absent exchange timestamp remains supported, as in Phase 10.6.
Freshness expires at equality with the applicable age limit.

The planner neither reads services nor creates an OrderId, order command,
correlation, risk decision, authorization, arm or permit. It is not a Spring
bean, HTTP endpoint, scheduler or new production diagnostic. A positive result
is **SIZED_RECHECK_REQUIRED**, never execution READY. A plan contains only bounded
platform instrument/price/quantity/notional/policy evidence. It contains no
broker token, account identifier, holdings payload or credentials.

Replanning and `stillCurrent` require exact agreement with the reviewed plan,
including registry generation, timestamps, policy and headroom fingerprint.
Changed evidence requires a new review. This comparison is observational and
is not a persisted authorization binding. No existing order is automatically
resized. A later order must be created normally and receive fresh risk approval.

`KiteOrderAdapter` additionally compares the exact instrument used to encode the
request with the registry immediately after final dispatch validation. A lot,
tick, mapping or resolution change during that interval denies before HTTP.
The existing production market gateway also reports DEGRADED /
INSTRUMENT_REGISTRY_CHANGED when its subscribed mapping generation changes.

## Actual call graph and valuation

```text
Explicit OperatorConsoleApplication --interactive-operator + controlling terminal
  -> TrustedOperatorConsole: preflight, exact arm confirmation, READY preflight,
                             separate exact execute confirmation
  -> OperatorExecutionService.execute
     -> observational preflight (does not consume UNUSED)
     -> RuntimeExecutionArming.claim (order/version/session/halt epoch binding)
     -> OrderApplicationService.executeRiskApproved
        -> independent-transaction requirement + safety.evaluate + audit
        -> PostgresOrderRepository.beginSubmission
           -> admission validation before transaction
           -> account advisory lock 606001 + blocking-exposure check
           -> admission validation + order/version CAS to SUBMITTING
           -> admission validation before commit
        -> committed SUBMITTING + safety.validateDispatch
        -> KiteOrderAdapter: encode normal platform correlation and order fields
        -> KiteRestTransport: synchronized KiteSession
           -> final safety.validateDispatch + encoded-instrument comparison
           -> one HTTP request to loopback in these tests
        -> attach broker ID / SUBMITTED, or retain ambiguity for reconciliation
     -> finally disarm and consume owned permit
```

Differences from the conceptual graph: preflight does not create a permit;
explicit arm installs UNUSED. Safety also runs inside admission at three fences
and twice after admission. The final callback runs inside the session monitor.
Account admission is a PostgreSQL lock/CAS and local exposure check; it is not
a new broker account snapshot or broker-side cash reservation.

Phase 10.6 remains canonical: MARKET reference is current price; LIMIT reference
is max(current, limit). BigDecimal reference × shared validated buffer × long
quantity governs risk max-order-value, normal and first-live max-notional,
admission and final dispatch. Equality passes and above-cap fails. Risk approval
policy identity derives from the same `RiskLimits` instance. Neither SBIN nor
10000 is hard-coded in generic production logic.

## Account and authentication meaning

Existing DELIVERY holdings are not automatically unsafe. Holding quantity +
unsettled quantity + collateral quantity forms a deliberate upper bound;
DELIVERY net positions are added. Day rows are checked but not added a second
time. Negative/unsupported positions, multipliers, discrepancy or margin-funded
holdings deny. Every nonzero held instrument requires current cash reference
data and a fresh tick for buffered exposure. The risk engine implements total
buffered exposure and per-instrument quantity limits, not a broader percentage
concentration, liquidity, fee or portfolio risk model.

Usable cash is exactly:

```text
min(equity.available.cash, openingBalance, liveBalance, equity.net)
 - max(utilised.debits, 0)
 - max(utilised.payout, 0)
 - max(utilised.holdingSales, 0)
```

Notional plus configured cash reserve must fit. Collateral, leverage,
discretionary credit and sale proceeds do not increase usable cash. Sufficient
cash does not increase the human ceiling. Broker snapshots are separate reads,
not an atomic account reservation, and manual broker activity is not fenced.

Future review must obtain current positions, holdings, orders, trades and
margins read-only, including fresh ticks for all held exposure. Prior zero
positions/orders/trades are not current evidence. `RiskService` reads positions,
holdings, margins and orders; reconciliation reads orders/trades.
`OperationalReadiness.tradingReadAvailable` currently establishes provider
presence, not successful reads. A retained characterization test demonstrates
that post-approval margin unreadability does not by itself change operator
preflight READY. **This is an unresolved real-readiness limitation.**

`KiteSession.executionIdentity` is present only for a usable authenticated,
unexpired, non-rejected token generation. Replacement/clear/expiry revokes arm
validity, and transport authenticates under the same session monitor. No
execution re-login occurs. `initializationReady` belongs to
`KiteAuthenticationUseCase.Status`, and is not directly checked by the execution
policy. A future operator must explicitly verify authenticated, tokenAvailable,
initializationReady and execution identity; no automatic certification is claimed.

There is no authoritative exchange-session/calendar gate in the reviewed path.
Local wall-clock time is not proof of OPEN. A future human decision must include
an explicit intended-session/order-support check.

## One attempt, database readiness and reconciliation

The rehearsal asks for 30 seconds. `kite.live-test.arm-max-duration` maps to
`KITE_LIVE_TEST_ARM_MAX_DURATION`, default 0s (deny). Exact expiry denies; no
auto-extension. Restart is DISARMED/NONE; HALT revokes via epoch; wrong order,
changed version or replaced session denies.

UNUSED -> CLAIMED -> CONSUMED is irreversible for a permit. Observational
preflight leaves UNUSED intact. An execute invocation denied before claim
disarms and consumes an existing UNUSED permit; a claimed invocation consumes
in finally. Success, rejection, timeout, reset, response loss, malformed ack,
admission/dispatch denial and persistence failure never restore UNUSED or retry.
This is per permit/invocation, not an assertion that operators cannot later
explicitly create a different order. That action remains separately controlled.

V10 readiness checks exact successful migration history, expected latest
version/count, no duplicate broker identities/unvalidated constraints, and the
valid/ready unique partial broker-order-id index with the exact key/predicate.
Readiness also checks reconciliation table accessibility and blocking outcomes.
No migration is added and the real database was not inspected or repaired.

Normal `OrderApplicationService.place` generates the platform OrderId and
`BrokerCorrelationId.generate()`; the plan generates neither. Ambiguous delivery
retains SUBMITTING. Explicit reconciliation prefers persisted broker ID, otherwise
requires one exact correlation match and matching order identity, checks fills,
and persists the decision with CAS. Missing/conflicting/ambiguous evidence blocks
readiness; reconciliation never resubmits. Existing restart/response-loss tests
exercise recovery without another broker mutation.

## Rehearsal and verification

Synthetic example only: price 800, buffer 1.10, unit 880, human/platform ceiling
10000, lot 1, hard quantity caps 100 -> Q=11, notional=9680. The next unit would
be 10560. With two synthetic held units, exposure headroom reduces Q to 9 and
new-order notional to 7920; total buffered exposure remains 9680. Both cases
pass actual persisted risk and operator READY and send exactly one loopback POST.
Planning before those disposable orders leaves all relevant tables empty,
HALT active, arm absent and HTTP count zero.

At price 900, the previously approved Q=11 values at 10890: preflight/final
dispatch deny with zero POST and consume the attempt. Buffer 1.25 similarly
values at 11000 and invalidates the old policy approval without rewriting it.
Synthetic lot/tick/resolution changes at transport deny, even with a deliberately
healthy fake feed. Unit replanning tests additionally reject disabled membership,
generation/price/buffer/headroom changes, stale/future receipt/exchange times,
missing subscription and unavailable account evidence.

Tests also cover exact 10000 / 9999.99 / 10000.01, decimal buffers/prices,
scale differences, large prices, Long.MAX_VALUE, cap-before-conversion, lot >1,
zero capacity, strict sub-30-second arms, over-limit positions, open broker
orders, unsupported holdings, unreadable margin, SUBMITTING, ambiguous/conflicting
reconciliation, missing V10, malformed index and duplicate broker identities.

Executed on 2026-10-05 with the existing JDK 21 and Docker:

| Check | Result |
|---|---|
| Broad `mvnw.cmd -Pintegration verify` | BUILD SUCCESS; 1,088 unit/architecture and 401 integration cases; zero failures/errors/skips |
| Final unit/architecture suite after shared capacity extraction and additional cases | 1,096 passed; zero failures/errors/skips |
| Final affected integration run | 186 passed: 79 one-order, 83 operator rehearsal, 20 SBIN, 4 PostgreSQL risk-store; zero failures/errors/skips; BUILD SUCCESS |
| Latest results across all integration suites | 413 passed, combining the broad run and final affected rerun; not one final unrestricted run |
| Project/default verifier | PASS |
| Secret scan | 637 text files, zero findings |
| Package inspection | Normal TradingCoreApplication entrypoint; new components present; integration/crash helpers absent |
| Diff/protected files | PASS; no whitespace errors or protected configuration/universe/migration/halt/arming/strategy edits |

Final affected command (also reruns the full unit suite):

```powershell
.\mvnw.cmd -Pintegration '-Dit.test=SbinCandidateRehearsalIntegrationTest,PostgresRiskDecisionStoreTest,OneOrderOperatorIntegrationTest,OperatorRehearsalIntegrationTest' verify
```

The broad run began before the final shared-capacity extraction and expanded
cases. The final run recompiles the finished source and verifies affected paths;
no source changes follow it. Local ignored evidence is in
`tmp/phase107-verify.log`, `tmp/phase107-final-verify.log`, and
`apps/trading-core/target/phase107/`. The latter contains bounded synthetic
clean/holding/position candidate plans and a verification manifest with source
and report hashes. They are explicitly synthetic, not proposed live quantities.

No failures were left unresolved. HEAD and local origin/develop remain at the
required baseline; all changes are uncommitted.

## Remaining decision boundary

This phase supplies deterministic sizing and a controlled end-to-end rehearsal,
not a real candidate order. The broad statement that **every** possible evidence
change before dispatch fails closed cannot be made: independent broker account
changes after risk approval are not re-read by final safety, initializationReady
is not directly gated, and observational candidate fingerprints are not a durable
approval binding. These limitations must be resolved or explicitly addressed in
a separately reviewed readiness design before treating operator READY as a
complete first-live certificate. Current real evidence and market-session checks
remain mandatory. No instruction to resume, arm or execute the real application
is included here.
