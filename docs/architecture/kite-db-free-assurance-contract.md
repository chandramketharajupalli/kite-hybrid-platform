# DiagnosticAssuranceContract.v1 — proposed DB-free assurance

Version identifier: DiagnosticAssuranceContract.v1. Revision: draft 1, 2026-10-10.
Status: **APPROVAL_PENDING / CONTRACT_CHANGE_PENDING_APPROVAL**.
This is a normative documentation proposal, not an accepted runtime contract,
schema implementation, permission, packaged artifact or real-read launcher.
Baseline e7868c479779636e6dc9e749a2e9440a2369e492. Operational NO_GO.
Previous Phase 13.5 Stage B ZERO_CALL_ABORT remains historical fact.

## Claim and scope

The proposed postcondition is exactly: **this diagnostic did not mutate trading
state**. Its prevention argument additionally requires **this diagnostic had no
trading database access**, direct or indirect, during its admitted lifetime.
It MUST NOT assert **no other process changed trading state**. Matching hashes,
readonly flags or lack of observed writes cannot substitute for absent authority.

This claim is conditional on independently verified code, deployment and host trust.
A compromised host/administrator, unreviewed agent or hidden persistence route
invalidates it. It is not proven by this document or the current full application
jar. An observation says nothing about atomic funds, absence of broker-side changes,
eligible/free collateral, the cash rule or a qualifying cash field. No risk approval,
candidate, quantity, execution permit or trading readiness can be produced.

## Old versus proposed guarantees

| Existing Phase 13.5 contract | Proposed v1 | Change status |
|---|---|---|
| Caller supplies restricted trading-DB connection | No trading DB credentials, driver, route or connection in diagnostic | CONTRACT_CHANGE_PENDING_APPROVAL |
| Capture token/trading content/count/schema before/after | Prove absence of diagnostic DB/persistence authority through artifact and deployment evidence; do not capture trading rows | CONTRACT_CHANGE_PENDING_APPROVAL; different proof, not equivalent |
| Independently exclude other writers plus observer visibility | Do not claim global exclusion; other actors may write | CONTRACT_CHANGE_PENDING_APPROVAL; stronger original condition removed only in proposal |
| Same-process private owner/recipient references | Custody-bound owner plus independently authenticated requester and narrow capability | CONTRACT_CHANGE_PENDING_APPROVAL; not implemented cross-process |
| Stable runtime HALT/session epoch | Independent fresh scoped HALT plus owner/session/epoch fencing | Retained objective; new attestation unimplemented |
| One exact GET, no retry/redirect/follow-up | Same request scope; one approved attempt across retries, replicas and restart | Retained; deployment-wide enforcement still unimplemented |
| No token lifecycle, orders, execution or normal startup | Same exclusions throughout diagnostic operation | Retained |
| Strict bounded parsing, redacted observation, no readiness promotion | Same; provenance must distinguish synthetic/observed/attested | Retained |

Old code and approval remain unchanged. KiteEquityReadIntegrity cannot be stubbed,
omitted or treated as satisfied to activate this proposal. No direct adapter bypass.
If a reviewer requires global state invariance, v1 is inadequate: retain the old
contract and NO_GO until independent global controls are established.

## Mandatory admission evidence (logical specification, not executable fields)

An evidence bundle MUST bind contract version/digest, approved artifact/deployment,
owner and recipient principals, protected account binding, session execution
identity, broker identity, NSE cash-equity MIS purpose, exact request method/origin/
path, one-attempt authorization identity, issuance/expiry, HALT issuer/epoch and
revocation status. Protected identities remain within authenticated authority
boundaries; public reports carry no account/session hashes or raw identifiers.
No real order identity/quantity is required or generated for this account snapshot.
There is no assertion that a segment GET is an exact-order funding quote.

Admission MUST deny on absent, stale, contradictory, unauthenticated or unsupported
evidence; unknown contract version; wrong recipient/account/session/product/route;
clock uncertainty; duplicate/consumed authorization; missing real-read approval;
unsafe logging; packaging or egress mismatch. UUIDs, source labels and local flags
are not signatures, owner proof or independent broker authentication.

Required proof domains:

1. **No DB authority.** No JDBC/PostgreSQL/Redis clients or writers, trading-store
   credentials, .env loader, token repositories, shared writable trading storage,
   persistence RPC, subprocess/general SQL/URL facility or indirect writer service.
   Filesystem/network/identity controls must reinforce the dependency boundary.
2. **No trading startup/authority.** No Spring Boot trading main/ApplicationRunner,
   component scan, order gateway/adapter, risk approval, operator, permits, live
   strategy, reconciliation or WebSocket capability.
3. **Egress default DENY.** Future separately authorized runtime permits only GET
   /user/margins/equity at the reviewed broker origin, verified TLS, no redirects,
   retries, supplementary endpoints or automatic auth calls. Host-level egress alone
   cannot enforce an HTTP path; reviewed client and authenticated deployment must
   enforce method/path. Current phase permits no real origin dispatch at all.
4. **Owner custody.** No restored token, environment/CLI/file bearer, token export,
   undocumented fallback or caller-controlled authenticated flag. See ownership ADR.
5. **Independent HALT.** Authenticated authorized issuer, full runtime scope,
   monotonic epoch, bounded validity, replay/revocation and compromise handling.
6. **Containment.** Explicit sink allowlist and restricted agents/dumps, private
   ephemeral workspace, closed environment, least-privileged process, no secret
   serialization, protected memory under the stated host trust assumption.

## Attempt lifecycle and postconditions

States proposed: DENIED -> no attempt; ADMITTED -> RESERVED -> COMPLETED or
FAILED/INDETERMINATE -> CLOSED. Reservation must be atomic before dispatch and
irrevocable, even for timeout, response loss or pre-send uncertainty. No resume
after owner/recipient crash, restart, expired lease or revoked epoch. A fresh
process cannot renew the old authorization. An independent control authority would
need a one-use ledger outside the trading store, containing no bearer/account data;
its ownership/storage and restart semantics require separate implementation review.
Current JVM-local budget is not proof of this multi-process property.

Check owner/session/HALT/containment immediately before dispatch and after response.
On post-check failure discard observation and report bounded INDETERMINATE/REJECTED;
do not retry or repair state. Validate bounded strict JSON and freshness. Never
emit raw response/account balances. Claim DB noninterference only when preventive
evidence stayed valid; otherwise report NOT_PROVEN, not a successful postcondition.
No DB fingerprint is produced by v1. No post-check can retract a request already sent.

## Minimal packaging proposal and actual obstacle

Current apps/trading-core/pom.xml start-class is TradingCoreApplication; dependencies
include Boot web/JDBC/Actuator/Redis, PostgreSQL runtime, Flyway, Prometheus and
HttpClient5. The plain classifier changes packaging, not dependency/authority scope.
The exec plugin targets KiteRestDiagnostic, which is not an approved launcher.
Parent reactor/build inheritance must be reviewed before extracting any module.

| Proposed allowlist | Denylist / required exclusion |
|---|---|
| Pure redacted receipt, exact decimal mapper, clock/expiry/epoch checks | Risk/execution/order/operator/strategy/reconciliation packages |
| Narrow read-only transport interface using existing implementation logic | General KiteRestTransport endpoint surface, arbitrary URL/method facilities |
| Owner-private nonexporting session capability, one-attempt state | Authentication restore/reset/exchange loaders, token stores, .env handling |
| Minimal JSON/HTTP/logging libraries with reviewed transitive graph | Boot startup/scanning/runners, JDBC/PostgreSQL/Flyway, Redis, WebSockets, telemetry export |
| Explicit bounded local receipt sink | File/network appenders, dump/agent/debug attachment and shared writable stores |

Actual code cannot simply be copied: KiteRestTransport includes order methods;
KiteSession includes credential/market-data and legacy initialization paths;
KiteEquityReadHarness requires integrity and handoff is package-private/synthetic.
A future module extraction needs a narrow interface and full class-load/transitive
dependency audit without a second broker client. No POM or source change here.
Packaging is UNRESOLVED / DESIGNED_NOT_DEPLOYED, not a verified no-DB artifact.

## Writers and residual risks

Other JVMs, Windows services/tasks/IDE runs, Docker/WSL, remote sessions, alternate
roles, superusers, triggers/functions and shared storage writers remain capable
of changing state. Locks/process scans/NOLOGIN/scoped draining do not cover them
all. Temporary write/restore can evade snapshots. DB-free design neither prevents
these changes nor certifies their absence. Broker balances/utilization can change
independently between receipt and use. Host compromise can expose in-memory tokens.
These residuals must be accepted explicitly, never hidden by a synthetic PASS.

## Logging and evidence policy

Inventory includes stdout/stderr, framework/HTTP debug, IDE consoles, file/network
appenders, metrics/traces, OS events, container logs, crash/heap/thread dumps and
exception/JSON serialization. Default deny all sinks except a specifically reviewed
bounded receipt sink; verify appenders/config digest at startup and before dispatch,
prevent or detect configuration changes, reject unknown sinks and fail closed.
Define approved sink ACL/owner, retention deadline and deletion responsibility;
absence of an approved policy denies. No current live sink is certified.

Proposed public event allowlist: contract version, bounded phase/outcome/reason,
attempt count 0/1, synthetic-or-observed classification and local UTC receipt time.
No free-form exception text, URL query/header, token/key/cookie/state, account/session
identity, private fingerprint, raw payload/balance or capability. Endpoint identity
is a fixed enum, not a caller string. No metric labels containing sensitive values.
Current tests prove marker redaction and known wire DEBUG rejection, not a complete
sink allowlist, OS policy or dump exclusion. LIVE_LOGGING_NOT_VERIFIED.

## Approval boundary

Approval of this proposal would approve only a contract/design scope, not deployment,
credential acquisition or a real request. Separate implementation/security review,
independent live prerequisites and explicit bounded request authorization are still
required. All remain absent here. Real read NOT_AUTHORIZED under v1; operational NO_GO.
