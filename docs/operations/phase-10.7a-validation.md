# Phase 10.7A ? final readiness evidence revalidation

No real order is authorized. This remediation addresses the two technical gaps
identified in [Phase 10.7](phase-10.7-validation.md). Current real reference,
market, account, initialization and V10 evidence remain RECHECK_REQUIRED;
MARKET_SESSION remains OPERATOR_RECHECK_REQUIRED. No live quantity was chosen.
INR 10,000 remains a conservative pre-trade ceiling, not a MARKET fill guarantee.

## Baseline and preservation

Before editing, branch was `develop`; HEAD and local `origin/develop` were both
`ea72e00888f3184633d823cc2393668cc3a4197b`. Status was deliberately dirty;
`git diff --check` passed. All eleven existing files were classified before edits:

| Class | Existing Phase 10.7 files (Java basenames) |
|---|---|
| A ? production | FirstLiveCandidatePlanner, ConservativeOrderQuantitySizer, CashAccountCapacity, CashOrderRiskRules, KiteOrderAdapter |
| B ? tests | OneOrderOperatorIntegrationTest, SbinCandidateRehearsalIntegrationTest, ConservativeValuationArchitectureTest, FirstLiveCandidatePlannerTest, ConservativeOrderQuantitySizerTest |
| C ? documentation | docs/operations/phase-10.7-validation.md |
| D ? unrelated | None |

The initial paths and SHA-256 values were captured in the local ignored artifact
`tmp/phase107a-starting-inventory.json`. Existing work was retained. The planner,
sizer and adapter final reference check are unchanged by 10.7A. The old account
characterization assertion in the SBIN rehearsal now expects denial.

No reset, restore, checkout, stash, clean, discard, commit or push was performed.
No migration, execution default, allowlist, quantity/notional cap, arm duration,
startup HALT, `.env`, universe or real credential was changed.

## Reproduction before remediation

Seven characterization tests ran against the existing production code before any
production fix. Six approved an order with clean synthetic account evidence,
then changed cash, holdings, net positions, broker pending orders, read availability
or completeness. Each still reached READY and one fake loopback POST. The seventh
used the real authentication use case with a synthetic authenticated session and a
failed instrument initialization: token and execution identity existed,
`initializationReady=false`, yet execution reached one fake POST.

Confirmed baseline: 7 tests passed, `tmp/phase107a-baseline-confirmed.log`.
The first attempt exposed a test fixture missing the mandatory commodity margin
segment; the fixture was corrected before the confirmed run. No production code
was changed to make the reproduction pass. These tests now assert denial and
zero POSTs. All account state, tokens, orders and databases in these tests are fake
or disposable. No real broker read or mutation was made.

## Current account contract

`CurrentAccountExecutionChecks` calls only the existing positions, holdings,
margins and orders read ports. It constructs the existing `OrderRiskInput` and
uses `CashAccountCapacity.inspect` and `CashAccountCapacity.check`. The historical
risk calculation calls that same exact-order capacity comparison. It does not
rerun `RiskService`, replace approval, resize an order, create a candidate, persist
a snapshot or change the historical risk row.

Required broker evidence:

- Net and day positions; net DELIVERY quantities contribute to capacity, while
  day entries are validated without being counted twice.
- Holdings, including the existing conservative quantity + unsettled + collateral
  quantity upper bound. Holdings are not treated as intraday positions. Unsupported
  MTF, discrepancy, products, shorts and multipliers fail closed.
- Usable equity cash = min(cash, openingBalance, liveBalance, net) minus
  max(debits, 0), max(payout, 0), max(holdingSales, 0). No collateral credit,
  leverage, discretionary credit or hypothetical proceeds are added.
- Orders: the existing risk contract permits terminal FILLED/CANCELLED/REJECTED
  observations only. Other broker statuses block; this rule was not newly invented.

The shared check requires held target units + exact order quantity <= position
cap, current buffered existing exposure + current conservative order notional <=
account exposure cap, and usable cash >= conservative order notional + cash reserve.
Equality passes. Existing exposure is repriced from fresh local ticks using the
same validated buffer. No new portfolio/concentration coverage is claimed beyond
these existing cash-risk limits. There is one account-wide read per component,
not a broker request per holding/instrument. Trades are not an input to these
capacity formulas; reconciliation still reads trades through its own read port.

Missing components, malformed adapter/domain evidence, unsupported evidence,
read failures, stale required ticks and changed registry evidence deny. Stable
bounded reasons include ACCOUNT_EVIDENCE_UNAVAILABLE, ACCOUNT_POSITION_LIMIT,
ACCOUNT_EXPOSURE_LIMIT, ACCOUNT_INSUFFICIENT_MARGIN, ACCOUNT_OPEN_ORDERS and
ACCOUNT_STATE_UNSUPPORTED. No balance or raw broker payload is logged.

These four reads are sequential, **not an atomic broker snapshot**. Their models
have no common observation timestamp. Evidence is used only in the current
synchronous operation and never cached across attempts. Structural inconsistency
and unsupported fields fail closed; undetectable external changes between reads
or after the final observation cannot be represented as a transactional guarantee.

Local durable evidence remains separate: V10 readiness, account advisory lock,
order CAS/version, SUBMITTING, unresolved exposure and reconciliation blockers
remain PostgreSQL responsibilities. Broker reads replace none of them.

## Initialization source of truth

`KiteAuthenticationUseCase` implements the non-I/O `ExecutionInitialization` port.
Successful application instrument initialization publishes the current execution
identity; replacement, clearing or loss of that identity makes readiness false.
The auth status response and execution use this same getter. Execution never calls
an HTTP controller, `status()`, token storage, login, restore or reference refresh.

The getter does not acquire the authentication workflow monitor. This avoids
inverting the authentication-workflow/session lock order inside transport's
existing session monitor. A test holds the workflow monitor while reading the
port on another thread and verifies no credential-store or refresh calls occur.
Authenticated, token available and execution identity remain separate mandatory
checks. Missing/failed initialization provider denies with
AUTHENTICATION_NOT_INITIALIZED. Missing providers and legacy policy constructors
fail closed; explicit ready substitutes exist only in synthetic tests.

## Execution timing and final fence

```text
trusted operator
  -> observational preflight (four broker account reads + local evidence)
  -> exact-order one-shot permit CLAIMED
  -> safety authorization (historical approval + current local/session evidence)
  -> PostgreSQL account admission / repeated fences / CAS SUBMITTING / commit
  -> post-admission local validation
  -> KiteOrderAdapter captures command and reference mapping
  -> KiteRestTransport takes existing session monitor
       -> local dispatch validation
       -> four synchronous broker account GETs
       -> re-read durable/local evidence and recompute conservative valuation
       -> shared account capacity comparison
       -> current auth initialization, identity, arm, HALT and market fences
       -> adapter exact captured-reference equality check
       -> one broker POST, only if every check passed
```

The final callback checks the submitted row/version/command/correlation; historical
risk identity/version/policy/age; V10 and reconciliation; exact permit/order/session
binding and expiry; current registry mapping; fresh connected market evidence;
normal/risk/first-live conservative caps; current account capacity; initialization;
and HALT. It samples freshness after potentially blocking evidence reads. Tests
change initialization, price and HALT during the account read itself. The original
Phase 10.7 adapter reference equality check remains after the callback.

Account reads occur once at the final transport boundary, not at each admission
fence. A change after preflight can therefore pass admission and is denied at the
final callback with the committed SUBMITTING checkpoint handled conservatively. A direct application execution therefore cannot bypass them. No broker POST
occurs on a detected violation. Quantity, buffer, human ceiling and approval are
never changed automatically. A revised candidate needs a new order/risk identity.

Preflight READY describes only the observed moment; it reserves no cash, position,
price, session or market state. Preflight does not write authorization or claim a
permit. The existing arm procedure observes preflight both before and after the
local arm operation; failed initialization cannot leave a valid grant.

## Failure-state matrix

All execute invocations complete/disarm their one-shot permit: UNUSED -> CLAIMED
-> CONSUMED when claimed; even denial before claim consumes an existing UNUSED
permit. Preflight alone does not consume it. No automatic retry is permitted in
any row. States below assume no concurrent durable change and successful failure
persistence; uncertain persistence is left for explicit inspection/reconciliation.

| Failure | Observed before admission? | Observed after admission? | Durable order state (before / after) | Permit state | HTTP possible? | Retry allowed? | Recovery |
|---|---|---|---|---|---|---|---|
| Account evidence unavailable/incomplete | Yes: preflight | Yes: final callback | RISK_APPROVED / FAILED | CONSUMED | No | No | Restore readable evidence; new reviewed/risked candidate as required |
| Cash decreased | Yes: preflight | Yes: final callback | RISK_APPROVED / FAILED | CONSUMED | No | No | Reassess capacity; never resize/reapprove automatically |
| Holdings/position/exposure changed | Yes: preflight | Yes: final callback | RISK_APPROVED / FAILED | CONSUMED | No | No | New reviewed/risked candidate |
| Blocking broker order | Yes: preflight | Yes: final callback | RISK_APPROVED / FAILED | CONSUMED | No | No | Resolve account/order evidence explicitly |
| initializationReady false/unavailable | Yes: preflight, arm, admission | Yes: final callback | RISK_APPROVED / FAILED | CONSUMED | No | No | Explicitly restore initialization, then new review |
| Session identity changed | Yes: preflight, arm, admission | Yes: final callback | RISK_APPROVED / FAILED | CONSUMED | No | No | New session-bound review and permit |
| HALT | Yes | Yes | RISK_APPROVED / SUBMITTING retained | CONSUMED | No if caught before transport; yes if already escaped | No | Inspect/reconcile; HALT does not authorize retry |
| Market stale/unavailable | Yes | Yes | RISK_APPROVED / FAILED | CONSUMED | No | No | Obtain fresh evidence and review |
| Conservative price cap breached | Yes | Yes | RISK_APPROVED / FAILED | CONSUMED | No | No | New candidate/order/risk identity; no in-place resizing |
| Reference changed | Yes when current checks detect it | Yes; adapter also compares captured mapping | RISK_APPROVED / FAILED | CONSUMED | No | No | Fresh reference review; no automatic remapping |
| DB admission/CAS failure | Yes: admission transaction | Committed winner remains authoritative | Rollback/no new submission; inspect winner state | CONSUMED | No for losing invocation | No | Inspect durable order/reconciliation |
| Response loss / timeout / malformed acknowledgement | Not applicable | Yes: transport outcome | SUBMITTING, ambiguous | CONSUMED | Yes | No | Explicit read-only reconciliation; never resubmit |
| Post-response persistence failure | Not applicable | Yes: after response | SUBMITTING if acknowledgement cannot attach; inspect committed state | CONSUMED | Yes | No | Explicit read-only reconciliation; never resubmit |

A provider/audit/persistence failure that prevents recording a denial can retain
SUBMITTING conservatively. Fresh account evidence is never a retry authorization.
Runtime HALT remains local and immediate, independent of account/auth providers.
Read-only reconciliation while HALTED remains supported by the unchanged path.

## Read cost and limits

| Operation | Added account reads (successful path) |
|---|---:|
| One explicit preflight | 4 (positions, holdings, margins, orders) |
| Arm request | 8 (two existing preflight observations) |
| Operator execute | 8 total: 4 implicit preflight + 4 final transport |
| Direct OrderApplicationService execute | 4 at final transport |
| Admission/local dispatch fences | 0 |
| Final transport callback alone | 4 |

Failures may short-circuit before four reads. Repeated operator preflights make
new observations. No volatile evidence is cached or reused across attempts.
The integration suite verifies exact successful preflight/execute read counts and
zero trade reads. The read-port implementation contains no per-instrument remote
loop. Local exposure work scales with holdings/positions; broker calls do not.

Four sequential calls add network latency and rate-limit demand. Existing transport
connect/read timeouts remain 10/30 seconds per request; a slow read can outlast the
proposed <=30-second arm. Final expiry/freshness rechecks then deny; no extension
or retry is added. This phase makes no claim about current broker rate-limit
allowance or live latency. Operator repetition must account for the fixed read
cost. The existing session monitor spans the final reads; HALT never acquires it.

## Validation and isolation

Final result: both identified technical blockers are closed in the controlled
rehearsal. Latest reports contain **1,098 unit/architecture tests and 456 disposable
integration tests**, all passing with zero skips. This is not a real READY verdict.

| Run/check | Result |
|---|---|
| Baseline characterization before production changes | 7/7 passed, demonstrating both old gaps |
| Full regression (`tmp/phase107a-full-validation.log`) | 1,098 unit tests passed; 448 integration tests executed, with four new SBIN fixture setup errors and no other failures |
| Final targeted rerun (`tmp/phase107a-final-targeted.log`) | BUILD SUCCESS; 35 targeted unit/architecture tests and 41 evidence integration tests passed |
| Latest aggregate (`tmp/phase107a-validation-summary.json`) | 68 unit suites / 1,098 tests and 16 integration suites / 456 tests; zero failures/errors/skips |
| `scripts/verify-project.py` | Passed: artifacts, profiles, safety defaults and JSON |
| `scripts/check-secrets.py` | Passed: zero potential secret locations |
| `git diff --check` | Passed |
| Final branch / HEAD / local origin/develop | develop / ea72e00888f3184633d823cc2393668cc3a4197b / same |

The four full-run errors were confined to new tests using the generic fixture's
order instrument against an SBIN registry. The tests now create the intended
synthetic SBIN order through the normal application path. The final rerun includes
those four cases plus eight additional missing-component/exposure/HALT cases.
No production change was needed after the full regression. Aggregate totals use
the latest result for each suite, not a claim that the first full command passed.

Coverage includes:

- `FinalReadinessEvidenceIntegrationTest`: historical approval versus changed
  cash/holdings/positions/pending orders and missing/unavailable components;
  final-boundary denial with unchanged historical risk rows; current SBIN holdings
  within limits, projected-position denial and exposure-only denial; cash plus
  reserve equality; initialization false with unchanged identity and replacement
  identity with initialization true; independent service-instance contention;
  HALT during blocked account reads; bounded read counts and consumed permits.
- `KiteAuthenticationUseCaseTest`: shared status/port semantics, session replacement,
  reset, and nonblocking readiness observation without credential I/O.
- `ConservativeValuationArchitectureTest`: pure sizing/planning, shared valuation,
  shared capacity comparisons and account revalidation restricted to read ports.
- Existing operator, SBIN, protocol, HALT, reconciliation and PostgreSQL suites:
  price/buffer/reference changes, arm expiry, response loss, post-response storage
  failure, reconciliation while halted, durable CAS/admission, V10/malformed
  identity-index/duplicate broker-identity scenarios.

Commands use the repository Maven wrapper and the existing JDK/Docker. The full
regression command is `./mvnw.cmd -Pintegration verify`. Targeted reruns use
`-Dtest=ExecutionSafetyPolicyTest,KiteAuthenticationUseCaseTest,ConservativeValuationArchitectureTest`
and `-Dit.test=FinalReadinessEvidenceIntegrationTest`; all execution integrations
are explicitly selected through the integration profile.

All mutation-capable rehearsal transports use guarded literal `127.0.0.1` URLs,
reject redirects/non-loopback targets and remove ambient deployment properties.
They use synthetic credentials and Testcontainers PostgreSQL dynamic URLs, never
`jdbc:postgresql://localhost:5432/trading`. No real application was started.

Real Kite mutations = 0. Real Kite reads = 0. Real development database test
mutations = 0. No real arm, resume, candidate, approval or authorization occurred.
The technical remediation is subject to the recorded validation; it is not a
separate human decision to attempt a live order.
