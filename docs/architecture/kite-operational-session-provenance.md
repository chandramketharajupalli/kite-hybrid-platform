# Kite operational session provenance contract

Phase 13.5C, 2026-10-10. Synthetic composition only. Operational NO_GO.
Real authenticated-session provenance and independent live isolation are not
established. Phase 13.5 Stage B remains ZERO_CALL_ABORT with zero real requests.

## Existing composition, not a new authentication service

KiteEquityReadHandoff is package-private, unwired and synthetic-only. It accepts a
pre-existing test-authenticated KiteSession, private Owner and Recipient references,
HALT object/epoch, clock, isolation lease, existing wire and observer. The issuer
must already possess these references; a UUID is not a credential. Atomic consume
allows one winner and rejects replay. Wrong owner/recipient, session replacement,
expiry, observed clock rollback, owner closure, lease loss and HALT change revoke.
The handoff rejects the official-origin factory before it can dispatch.

The concrete final KiteSession and Kite equity adapter, plus exact route, bind the
intended broker scope to Kite/Zerodha; there is no caller-selectable broker identity.
This is type/route scope, not proof of independent broker authentication. A fixture
calling profileValidated() proves only local state. Tests explicitly project a
successful synthetic handoff receipt into the existing V1 collateral contract:
all four required terms remain UNKNOWN. Source catalog identifiers, token-shaped
strings and officialOrigin flags cannot supply missing attestation.

The private execution identity binds one installed session generation. It is not
an account identifier or independently authenticated principal. Owner/recipient
possession is an in-process trust boundary, not protection from hostile code,
reflection, Java agents, memory inspection or OS administrators. No capability or
session export exists. JSON/private-field mapping returns an empty handoff object;
native serialization fails. The redacted receipt exposes only local receipt time
and synthetic provenance, never a session/token reference or continuing authority.

KiteAuthenticationUseCase.restore() and the Spring ApplicationRunner are explicitly
excluded: restore may load/decrypt durable tokens, call profile/instrument APIs or
clean rejected/expired credentials. OperatorExecutionService reaches arm/claim/
execute/resume; architecture tests exclude it and order/risk/startup services from
diagnostic dependencies. The new metrics/tracing dependency guard prevents these
internal diagnostic types from exporting labels to Micrometer/OpenTelemetry.
No auth, HALT, transport, persistence or execution production source changes occur
in this phase. Existing one-GET and official-origin rejection remain intact.

## HALT attestation boundary

knownHaltedAt(epoch) requires a known true startup signal and unchanged HALTED
epoch. Unknown startup evidence denies diagnostics while execution remains stopped
under its original fail-safe semantics. Existing tests cover unavailable/false
signals and stale/changing epochs. A new synthetic concurrent-resume case verifies
post-read integrity is still attempted, the observation is discarded and no second
request can occur. No operational HALT method is invoked.

An independent live attestation would have to identify the actual runtime epoch,
its configuration source, disabled execution capability and the authority issuing
the attestation, with freshness and revocation. A second local boolean is not an
independent witness. No live attester, signer or runtime connection is implemented.

## Proposed future isolation topology: review only

An independently controlled maintenance boundary would need all of the following:

1. A complete inventory of writers and credential holders across Windows processes,
   services, scheduled tasks, IDE launches, Docker/WSL, remote hosts and privileged
   DB roles. Inventory alone cannot enforce exclusion.
2. A separate isolation controller which prevents relaunch/reconnect for the whole
   window, drains existing sessions/transactions and verifies effective controls
   independently of the diagnostic process. Network access and credentials for
   alternate paths must be covered, including administrative exceptions.
3. An already-reviewed authenticated runtime composition with no execution service,
   normal app startup, token load or login side effect. The present synthetic
   handoff cannot be used for official requests. A new review must address the
   tension between retaining an authenticated context and excluding its other
   capabilities; no live solution is claimed here.
4. A separate restricted observer, complete catalog/statistics visibility and
   private before/after integrity evidence. It must not hold provisioning, DDL,
   mutation or role-escalation capabilities. Independent exclusion must outlast
   the request and post-checks, failing closed on loss of its attestation.
5. Controlled log/tracing/dump sinks and a scoped approval for any eventual
   operation. Changes to live services, roles or firewalls need their own review;
   this document supplies no operational commands or authorization to change them.

The disposable NOLOGIN experiment demonstrates only denial of new connections for
one fixture role: a previously connected writer can still update a row. The
observer detects that visible session and the subsequent persistent change. It
does not prevent either. An independent child ignoring a cooperative file lock
also changes its own fixture state while the lease remains valid. These are
counterexamples to insufficient controls, not an implemented global exclusion
topology. Advisory locks have the same participation requirement; no claim of
global protection from an advisory lock or process listing is made.

The earlier committed-write/restoration and rollback counterexamples are retained.
Matching content/count/schema snapshots cannot prove absence of transient changes.
Autocommit observations are not an atomic multi-table snapshot. Current schema
fingerprints cover columns, not every function/trigger/constraint definition.
No live writer exclusion, privilege proof or baseline was collected.

## Observer and wire evidence

Existing integrity checks require server read-only transactions, autocommit READ
COMMITTED, complete statistics visibility, limited role membership, no table write/
TRIGGER, sequence, schema-creation or executable application-function permissions.
The guarded sequence catalog query remains unchanged. Added disposable tests prove
effective inherited UPDATE/TRIGGER and direct TRIGGER privileges are detected and
deny before HTTP. Existing direct ACL tests disable transaction read-only only in
fixtures to distinguish ACL protection from a transaction flag. System functions
are not a blanket SQL sandbox; the fixed SELECT/SHOW query set remains essential.

The official factory's attempt budget is JVM-wide; tests reserve without execute
or broker sockets. Loopback factories have per-fixture budgets, not one shared
budget across unrelated test cases. Every request object executes at most once.
Errors, timeouts and response loss never cause retries, redirects or supplementary
calls. Wire/header DEBUG is checked at admission, request creation and execute.
Actual live agents, sinks, ACLs and retention remain unverified.

## Funding and operational decisions

Eligible haircut-adjusted collateral, actually free collateral, applicable NSE MIS
cash rule and qualifying API cash-field mapping remain unresolved. General prior
documentation conflicts are not resolved by synthetic observations. No new broker
policy research or account data was collected. Collateral-assisted MIS NOT_READY;
cash-only policy, reserve, CNC and INR 10,000 buffered-notional ceiling unchanged.

The [prerequisite matrix](../operations/phase-13.5c-prerequisite-matrix.md) applies
the decision rule: any absent, stale, conflicting or blocked independent live
prerequisite yields NO_GO. Synthetic success never grants a real request or trade.
