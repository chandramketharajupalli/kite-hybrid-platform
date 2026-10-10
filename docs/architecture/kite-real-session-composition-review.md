# Kite real-session composition review

Phase 13.5B, 2026-10-10. Engineering fixtures only. **Operational NO_GO**.
REAL_AUTH_SESSION_NOT_ESTABLISHED. LIVE_WRITER_EXCLUSION_NOT_ESTABLISHED.
Previous Phase 13.5 Stage B remains ZERO_CALL_ABORT, zero real requests.

## Actual composition and trust boundaries

The package-private KiteEquityReadHandoff accepts only a loopback
KiteEquityReadRequestFactory. Official-origin clients remain explicitly rejected.
The issuer and recipient retain private object references; the opaque UUID is
neither exported nor accepted as authentication. Atomic consumption, bounded
expiry, monotonic observed clock, owner lifetime, session execution identity,
HALT epoch and independently supplied synthetic lease fence a single attempt.
The capability cannot be renewed, persisted or recovered on restart. A failed
consume burns it. JSON, including private-field autodetection, exports an empty
object; Java native serialization fails. Trusted JVM composition is required:
reflection, malicious agents, heap dumps and compromised administrators are not
defeated by object identity or Jackson annotations.

KiteSession's authenticated state in these tests is created with fixture tokens
and test-only profileValidated(), with no broker request. It is not proof of a
real account. Production KiteInfrastructureConfiguration has an ApplicationRunner
calling KiteAuthenticationUseCase.restore(). That path can load/decrypt tokens,
validate a profile, refresh instruments and clear rejected/expired durable tokens.
Neither startup nor restore is a permissible way to manufacture a session here.
PostgresKiteAccessTokenStore remains outside the diagnostic dependency graph.

Future design review must identify a legitimate already-authenticated runtime,
prove intended account/token binding without exporting credentials, and establish
a trusted diagnostic composition that contains no execution services. This review
does not implement that composition, IPC, token loader or real-origin launcher.
Same-process injection minimizes credential transfer but cannot isolate hostile
code already in that JVM. Cross-process transfer would require a separate OS-bound,
authenticated, expiring channel and credential-exposure review; no such channel
exists in this phase. Starting the normal application is not an acceptable shortcut.

## HALT evidence and result lifetime

RuntimeTradingHalt continues to fail safe for execution when its startup supplier
throws: existing status/getAsBoolean/resume/runningAt semantics are unchanged.
The additive knownHaltedAt(epoch) returns false for unavailable startup evidence,
false startup HALT, wrong epoch or an epoch changed while reading the supplier.
Handoff and harness now use this stricter diagnostic predicate. It proves only
the supplied process-local object at a check, not another process or host.

Checks occur at issuance, admission, after the database baseline, request creation,
immediately before execute and after response. Post-read database capture is
attempted before checking a revoked session/lease, so auth loss does not suppress
durable verification. Detected invalidation discards the observation; there is no
retry. Concurrent owner closure, epoch replacement and session rejection are tested.
No discrete checks can prevent arbitrary changes between observations.

The returned Observation contains only provenance and local receipt time. It is
a historical receipt, not a reusable capability or assurance of current validity.
After logout, the receipt may still describe an earlier synthetic observation;
the handoff cannot be inspected as valid or consumed again. There is no result
consumer that can grant an execution permit, risk approval or candidate quantity.
Any future account-evidence consumer must independently revalidate freshness and
binding; it cannot use the redacted receipt as continuing session authority.

## Wire and logging boundary

The existing official factory has a JVM-wide non-resettable attempt budget.
Synthetic factories have per-fixture budgets; they do not claim a global budget
across independent test cases. Each returned request object executes at most once.
Scope is exactly GET /user/margins/equity. No redirects, retries, auth follow-ups,
cookies or supplementary routes; timeout, response loss and malformed payload
spend the attempt. Official-budget tests construct request objects only, without
execute or broker sockets. All executed HTTP uses literal loopback fixtures.

Wire/header DEBUG checks now run at createRequest and immediately before execute,
in addition to harness admission. A late detected logging change closes the factory
and cannot recover its attempt. Static guards still cannot certify live tracing,
agents, sink ACLs, retention, dumps or changes after the final check. Live logging
safety remains NOT_ESTABLISHED. No raw response or token enters committed output.

## Database and external isolation limitations

KiteEquityReadIntegrity is unchanged. Its caller-owned connection uses fixed
SELECT/SHOW queries, requires read-only autocommit READ COMMITTED, restricted
role membership, pg_read_all_stats, SELECT access, no other client sessions or
prepared transactions, no application function/DDL/table/sequence write rights,
and all required tables. The relation-kind CASE guard on sequence permissions is
retained. System functions are not a blanket-revoked SQL sandbox; the fixed query
set is part of the safety boundary. No general-purpose SQL facility is exposed.

Private SHA-256 fingerprints cover sorted content/counts and column schema shape,
including empty tables. They are not an atomic multi-table snapshot and do not
cover every possible database object definition. Missing visibility/privileges,
rows/schema drift and observed competing sessions deny. Disposable tests directly
attempt forbidden operations with transaction-read-only disabled in the fixture,
proving ACL denial independently; the harness never changes connection settings.

An intentional counterexample commits a token-row timestamp change and then its
inverse before the next capture. Counts/content/schema and final snapshots match.
This demonstrates why snapshots cannot prove that no writer existed or acted.
Rollback similarly leaves no committed difference. Cooperative file-lock tests
detect another lock participant and lost ownership; writers which ignore the lock,
other hosts and credential holders are outside that exclusion mechanism.

## Funding authority remains separate

The Phase 13.2 V1 collateral contract and Phase 13.4 observation mapper are unchanged.
SYNTHETIC_TRANSPORT, caller-provided source labels, net and utilised collateral
cannot establish broker-authoritative free capacity. Eligible adjusted collateral
and actually free collateral remain UNKNOWN; exact cash-rule and qualifying-field
public-source conflicts remain unresolved, account-specific terms UNKNOWN.
No new public broker research or account evidence was collected in this phase.
Collateral-assisted MIS remains NOT_READY. Cash reserve, CNC, final dispatch,
permit fencing and INR 10,000 full buffered-notional policy remain unchanged.

See [operational matrix](../operations/phase-13.5b-operational-isolation.md) for
the independent live evidence required before any separately reviewed next step.
