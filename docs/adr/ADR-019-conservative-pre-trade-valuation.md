# ADR-019: One conservative pre-trade valuation contract

Status: Implemented for local/disposable verification; no live authorization.

## Problem and contract

The first-live parameter review found that risk used buffered notional while
execution and first-live caps used raw notional. At synthetic price 9500,
buffer 1.10 and quantity 1, risk rejected 10450 against 10000 while execution
accepted 9500. The baseline was reproduced before production edits with seven
deterministic cases; switching those tests to the required common decision
produced three assertion failures.

`ConservativeOrderValuation` in `order.domain` is the single implementation:

- MARKET reference = current positive market price; limit price must be absent.
- LIMIT reference = max(current positive market price, positive limit price).
- Conservative unit price = reference × buffer.
- Conservative notional = conservative unit price × positive integral quantity.
- A positive configured ceiling passes exactly when notional <= ceiling.

All arithmetic is BigDecimal without MathContext, floating point or currency-scale
rounding. Inputs are bounded to precision 36 and absolute scale 18; buffer remains
within 1..2. Products are exact and may have greater precision/scale than inputs.
The immutable result is created only by the evaluator. Freshness, health, lot size,
account state and authorization remain caller responsibilities and retain their
existing checks. Missing or invalid valuation evidence denies.

**₹10,000 is a conservative PRE-TRADE valuation ceiling. It is not an absolute
realized fill-value guarantee for MARKET orders.** Prices can change after the
last observation and broker dispatch is not atomic with that observation.

## Ownership and boundaries

There is still only `risk.price-buffer` / `RISK_PRICE_BUFFER`, default 1.0.
No execution buffer property is introduced. Production execution configuration
receives the same immutable `RiskLimits` bean as risk. Its approval-policy version,
buffer and risk order-value ceiling are derived from that single object. The old
constructor accepting an independently supplied risk-policy version was removed.
Absent limits deny; changed limits invalidate the persisted approval fingerprint.
Execution application code does not depend on risk infrastructure or Spring.

Cash risk uses the shared evaluator for the order and held-instrument unit
valuation. Normal execution uses its result and passes that same result to the
first-live checks. Neither first-live checks, console nor broker adapter computes
an independent notional. Execution rechecks both normal and risk order-value
ceilings; the independent first-live check enforces its ceiling. Thus the strictest
of all three applies to the same current conservative observation. There is no
production symbol or 10000 special case.

The evaluator runs on each preflight/authorization, at account-admission fences
(before the transaction, after lock acquisition, before commit), after durable
SUBMITTING and in the existing session-locked final transport validator. Admission
checks remain read-only until the existing repository CAS. Admission valuation
denial rolls back any uncommitted SUBMITTING and records bounded denial evidence
after rollback. It does not rerun broker account reads or create a new risk decision.
An earlier READY result never freezes price or reserves permission.

Admission's additional operational probe uses the existing independent read-only
transaction while the admission transaction holds its connection. Pool capacity
must accommodate that read; unavailable evidence denies. The additional reads do
not extend the arm or relax freshness. No pool or timeout configuration is changed.

## Preserved recovery and operational contract

Preflight observation alone does not consume a permit. An execute invocation still
revokes an UNUSED permit even if its initial preflight denies. After claim, the
owning attempt consumes and disarms in finally. No denial restores UNUSED.
Admission denial leaves RISK_APPROVED after rollback; a non-halt final-dispatch
denial retains the existing FAILED transition. HALT after committed admission
retains SUBMITTING. None of these outcomes triggers an automatic retry.

Startup HALTED/DISARMED, halt epoch invalidation, order/session/version binding,
account admission, broker-ID/correlation reconciliation and failure handling are
unchanged. Existing uncertain submission outcomes require explicit reconciliation.
No migration, retry, public API, scheduler, order creator or quantity allocator is
added. Budget-to-quantity allocation is deferred; this phase enforces caller-supplied
quantities only.

## Configuration and future use

All production defaults and real configuration remain unchanged and fail closed.
Normal and first-live max-notional properties now mean **buffered conservative
notional**, not raw quote notional. Do not compensate by raising caps. For a future
human-authorized 10000 ceiling, independently configured applicable caps must each
retain any stricter limit. No division by the buffer is needed for the first-live
cap after this change. A 30-second arm is an authorization duration, not a fill-time
or order-lifetime guarantee. No live quantity, SBIN identity or allowlist is selected.

Synthetic tests cover exact arithmetic, LIMIT regression, configuration rejection,
raw-below/buffered-above denial, cap composition, production preflight, admission
price changes and a final-dispatch barrier with zero loopback POSTs. Existing halt,
crash/restart, one-shot failure and reconciliation tests remain mandatory. See
the Phase 10.6 validation report for executed results; test source alone is not a
claim that tests passed.
