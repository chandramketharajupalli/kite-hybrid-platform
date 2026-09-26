# ADR-017: Durable execution admission and revocable market-data publication

Status: accepted. Refines ADR-016; does not enable live trading.

## Execution

Initial safety authorization is a snapshot, not permission to use stale evidence later. The explicit application path now performs:

RISK_APPROVED -> authorization/audit -> PostgreSQL account admission and order CAS -> committed SUBMITTING -> revalidation -> gateway -> transport-session lock -> final revalidation -> dispatch.

Execution rejects an ambient Spring transaction before any side effect. Admission uses PostgreSQL advisory transaction lock 606001, shared with risk, a five-second lock timeout, and the order version CAS. The transaction commits before gateway entry. Different orders cannot both pass admission while either has outstanding local exposure. No database lock or transaction is held across HTTP. The admission blocker includes SUBMITTING, SUBMITTED, ACKNOWLEDGED, OPEN, PARTIALLY_FILLED, FILLED and CANCEL_PENDING, excluding the candidate itself. FILLED remains conservatively blocked until the platform gains an explicit account reservation/release model. Historical audit rows do not block admission.

Every mutable gate is checked again after admission, and again when the transport owns the authenticated session immediately before initiating the request. This final check is the dispatch linearization point: revocation observed before it denies; a later revocation is an in-flight request and cannot recall bytes already dispatched. This does not claim that clocks, networks and a remote exchange participate in an atomic transaction. There is no retry and no panic cancel.

Age comparisons sample the injected clock after potentially blocking evidence reads. The authenticated-session identity is an immutable published view, checked for current expiry/invalidation without waiting for the REST monitor. Audit timestamps describe completion of authorization evaluation. A slow PostgreSQL read therefore cannot preserve an earlier, still-valid clock sample through dispatch.

Post-admission safety denial records a DENIED audit at the submitting version and makes a successful CAS to FAILED/PRE_DISPATCH_DENIED. If recording/transition fails or the order changes, it remains conservatively unresolved; no HTTP is invoked by that denied check. Initial denial leaves RISK_APPROVED untouched. If admission loses, its denial is audited independently. Audit ALLOWED means only the initial authorization passed; it is not a claim about submission or broker acceptance. Audit inserts use REQUIRES_NEW, bounded timeout and configuration fingerprints.

Arming is memory-only, no more than one hour, valid only from its issue instant until its exclusive expiry, and bound to a random non-secret authenticated session identity. Clearing/replacing/invalidating a session cannot preserve usable authorization. A default arm without a session supplier cannot arm. No production code automatically arms or executes. Mutation commands remain disabled pending their own authorization, amended risk, versioning and reconciliation protocol.

The policy depends on a broker-independent ExecutionSession port. The Kite adapter cannot place via the old no-validation gateway method; only the callback-bearing dispatch method is supported. The transport invokes that validation after acquiring the session monitor. Architecture tests constrain gateway call sites and prohibit strategy/risk/reconciliation/startup execution dependencies.

## Market data

An epoch check before an unlocked update cannot fence a delayed consumer. The adapter now assigns revocable PublicationPermits per connection/subscription mode. Stop, unsubscribe, mode change and connection retirement revoke the relevant permits without waiting for the consumer. The store retains the permit with each tick; revoked ticks are filtered from every read and cannot displace a valid newer-generation value. Per-instrument compute serializes publication; revocation is nonblocking. Only one current entry per instrument is retained, not an unbounded generation history.

The linearization boundary for a reader is its permit-validity observation. A tick already returned before revocation cannot be recalled; risk/execution independently re-read health and age. This is normal snapshot semantics, not stale publication after revocation.

## Recovery and scope

HTTP timeouts, response loss, 5xx, non-validation 4xx and malformed/ambiguous acknowledgement JSON preserve possible submission. Recovery is exact broker ID or persisted correlation plus immutable order checks. V10 makes non-null broker order IDs unique in the current single-account ledger; upgrade deliberately fails on historical duplicates rather than guessing ownership. Cross-observation conflicting trade identities roll back reconciliation writes.

These controls coordinate this platform's processes sharing one PostgreSQL database and account. They do not coordinate manual/external broker activity or guarantee a MARKET fill price. Multi-account support requires account identity in tables, uniqueness scopes, session/arming bindings and advisory-lock keys. All validation here uses synthetic sessions and local fake endpoints.
