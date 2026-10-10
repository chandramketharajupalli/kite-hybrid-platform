# Read-only Kite deployment isolation review

Reviewed 2026-10-10 against db3e5d697b80b95a806010258426b14d590ba831.
This is a future architecture review, not a deployed runtime or permission to read
an account. Phase 13.5 Stage B remains ZERO_CALL_ABORT. Operational decision NO_GO.

## Candidate topology

| Option | Feasible boundary | Unresolved requirement | Decision |
|---|---|---|---|
| Dedicated JVM with minimal dependency allowlist | No Spring bootstrap, runners, operator, order, strategy or market-data components; exact controlled transport | No such minimal distribution is built here. Current application jar contains execution classes. A pre-existing authenticated session cannot cross JVMs as private references. | Future review only |
| Disposable sidecar/container, read-only filesystem | Separate process/user, restricted mounts, network and log policy; dedicated observer | Read-only root filesystem does not prevent network/DB writes, secret environment inheritance, host mounts or privileged escape. No authenticated token-free cross-process handoff exists. | Future review only |
| Offline/synthetic fixtures and independent disposable PostgreSQL | Test-owned synthetic session, private-reference handoff, loopback server and Testcontainers role controls | Proves fixture behavior, not host-wide network isolation or live provenance | Validated scope of this phase |

Windows process-name scans miss services, scheduled tasks, IDE configurations,
non-Java clients and remote hosts. Docker Desktop/WSL add separate process/network
namespaces; host.docker.internal, host networking, mounted sockets and shared
volumes can defeat intended separation. Future packaging must prohibit unnecessary
mounts, Docker socket access, inherited secrets and unrestricted egress, and record
image/dependency/configuration digests. No deployment manifest or live launcher is
provided. A container label or readonly mount is not a writer-exclusion certificate.

## Existing activation and effect inventory

Paths below are relative to apps/trading-core/src/main/java/com/kitehybrid/platform.
These are code routes, not commands to run. No route was invoked against real systems.

| Source/path | Potential effect | Diagnostic boundary |
|---|---|---|
| bootstrap/TradingCoreApplication.main | Normal Spring trading startup | Never called |
| bootstrap/OperatorConsoleApplication.main | Starts TradingCoreApplication; obtains operator/reconciliation services | Never called |
| broker/infrastructure/kite/KiteInfrastructureConfiguration.restoreKiteAuthentication ApplicationRunner | Calls authentication.restore; durable token load/decryption/cleanup and broker validation/initialization may occur | No Spring context or runner |
| broker/infrastructure/kite/KiteRestDiagnostic.main | Environment credentials, legacy session construction, profile/instruments broker reads | Not a safe harness launcher; excluded |
| KiteAuthenticationController /api/broker/kite/auth: GET /status, /login, /callback; POST /reset | Status passive; login can restore/create login attempt; callback consumes attempt/exchanges token/validates/persists; reset clears token/session | No controller/auth lifecycle dependency |
| KiteAuthenticationAdapter login URL and POST /session/token | Interactive login URL construction and token exchange | Excluded |
| PostgresKiteAccessTokenStore and durable login-attempt store | Token load/decrypt/save/delete; attempt create/consume/cleanup | No store dependency or token loader |
| KiteTradingReadDiagnosticController /api/development/trading-read: GET /orders, /trades, /positions, /holdings, /margins | Read-only account HTTP when enabled | No controller startup or supplementary read |
| KiteMarketDataDiagnosticController /api/development/market-data: POST /start, /subscriptions; DELETE /subscriptions/{id}; POST /stop; GET /status, /latest | Start/subscription lifecycle can connect WebSocket | No market-data component |
| JdkKiteWebSocketTransport | wss://ws.kite.trade connection | Excluded |
| UniverseValidationDiagnosticController /api/development/universe: /validate, /unresolved, /lookup | Instrument/universe diagnostics over registry | Not used; no Spring endpoint exposure |
| health TradingStatusEndpoint, KiteStatusEndpoint, MarketDataStatusEndpoint | Read local status; do not independently attest another process or global writer exclusion | No new Actuator exposure or runtime startup |
| KiteRestTransport GET enum | /user/profile, /instruments, /orders, /trades, /portfolio/positions, /portfolio/holdings, /user/margins, /user/margins/equity | Controlled factory permits only exact equity GET |
| KiteRestTransport.historicalMinute | /instruments/historical/{token}/minute | No historical calls; continuity gate unchanged |
| KiteRestTransport.calculateOrderMargin | POST /margins/orders calculation | Not allowed by read factory |
| KiteRestTransport postRegularOrder/putRegularOrder/deleteRegularOrder and KiteOrderAdapter | POST /orders/regular, PUT/DELETE /orders/regular/{id} | No calls from diagnostic; exact method/path fence |
| TrustedOperatorConsole / OperatorExecutionService | Runtime resume, arm, permit claim, execution/reconciliation | No diagnostic dependency or invocation |

The shared transport class still contains order methods. Architecture checks prove
the diagnostic calls only controlledEquity and excludes execution/auth lifecycle
dependencies; request-factory checks prevent other routes. They do not claim order
bytecode is absent from the full application artifact or resist hostile reflection.
No new component was necessary. Existing package-private harness/handoff remain
unwired and synthetic handoff rejects official-origin clients.

## Session ownership and trust

SYNTHETIC_AUTHENTICATED is a fixture session installed and profileValidated locally,
not independently authenticated broker evidence. LIVE_SESSION_PROVENANCE_NOT_ESTABLISHED
is the current real state. LIVE_SESSION_PROVENANCE_VERIFIED would require separately
authenticated operational evidence in a future scope; no local flag can produce it.

Private owner/recipient reference equality, session execution identity, current
known HALT epoch, expiring lease, monotonic clock observations and atomic consume
bind one capability to one synthetic GET. Failure/replay/revocation denies; owner
close/session replacement/epoch change invalidate. UUID is a label, not a bearer
credential. JSON private-field mapping emits no capability data; Java serialization
is rejected. Same-JVM malicious agents/reflection, heap dumps, debugger access and
OS administrators remain outside this trust boundary. Process termination destroys
in-memory capability; it is never restored after restart.

A dedicated JVM cannot receive these references from an existing trading JVM.
Serializing KiteSession or sending tokens via CLI/environment/files/IPC would break
the design. A future design must either prove an already-authenticated owner in the
same deliberately isolated process, or review a separate authenticated capability
service that retains credentials at the owner and delegates only the bounded read.
Neither exists here; the latter also requires proving the owner has no competing
execution authority. No real session acquisition or token transfer was implemented.

## HALT attestation design

RuntimeTradingHalt.knownHaltedAt checks the same epoch, explicit halted state and
available startup signal before and after observing it. Execution's fail-closed
fallback remains unchanged: unavailable startup can mean HALTED_UNKNOWN, not an
independent HALTED_KNOWN attestation. Synthetic tests cover unavailable/false signal,
replaced epoch, concurrent resume/revocation and response-time invalidation.

Future attestation must be controlled independently of the diagnostic recipient,
authenticated against an approved authority, and cover every runtime capable of
execution. Bind runtime/session/deployment identity, epoch, disabled execution
configuration, issuance/expiry, challenge nonce and permitted operation. Require
bounded clock skew, revocation and fresh checks immediately before/after dispatch.
Conflicting or missing instances deny. No keys, signing service or live attestation
is created; local latch state is not evidence about other processes.

## Server-side writer exclusion proposal

An independent deployment/database controller would inventory all login and
inherited roles, credentials, existing sessions, prepared transactions, jobs,
triggers/functions, local/remote clients and privileged administrators. Under a
separate change authorization, it would prevent new writer connections, drain
already-connected writers, freeze reconnect/credential paths and maintain exclusion
through the diagnostic window. An independent observer must verify that scope and
continuously fail closed on loss. Administrators capable of undoing controls must
be covered by the operational trust model; excluding one role is not sufficient.

Disposable evidence demonstrates NOLOGIN plus termination of the specific owned
backend blocks that role's old and new connections, while a different privileged
connection remains possible and causes observer denial. NOLOGIN alone leaves old
connections usable. Cooperative file locks do not stop non-participants. No real
session was terminated and no live role/network setting changed. An independent
disposable database is the only currently exercised topology and proves nothing
about exclusion around the real trading store.

## Observer and integrity boundaries

Existing guard requires explicit SELECT, pg_read_all_stats visibility, no unexpected
membership/escalation/DDL/TEMP/table-write/TRIGGER/sequence/application-function
rights, no competing sessions or prepared transactions, and suitable read-only
autocommit isolation. The type-guarded sequence privilege query is retained.
Private fingerprints cover token row and trading row contents/counts/column shapes,
including empty tables. Post-read capture is attempted even after auth/lease loss.

Phase D adds a private test-only SHA-256 permission capture over ordered role flags,
effective memberships, database/schema/table/sequence/application-function rights.
It uses length-delimited values and query boundaries; no password catalogs or row
values are read and no hashes are printed. Granting TRIGGER changes it and denies
HTTP; revoking restores it. This is a reproducible fixture measurement, not a new
production observer API. Neither it nor multi-query content capture is atomic or
preventive. Rollback, write/restore and grant/revoke between snapshots can escape
detection; privileged writers can reconnect after a clean observation.

## Network and log containment

Existing factory reserves one process-wide official attempt before request creation,
wraps each request for one execute and has no retries/redirects/cookies/auth retries.
Loopback outcome tests cover redirects, auth/rate/server failures, response loss,
timeouts and strict size/JSON parsing. Official budget tests construct but never
execute official requests; synthetic handoff rejects them. These complementary
tests are not an exercised live network path. One failure never grants a second
attempt. No new public activation switch exists.

Future review must independently check log configuration immediately at dispatch
and inspect sink ACLs/owners/retention, Windows process arguments/environment
inheritance, debugger/agent access, heap/JFR/crash dumps, TLS/proxy/wire logging and
container stdout/log-driver storage. Static marker/redaction tests cannot prove
actual sink safety. Disable unsafe dumps/logging through separately reviewed
deployment controls; do not place tokens, raw payloads or private fingerprints in
reports. Existing logging checks and redacted receipts remain unchanged.

Observed funds do not resolve eligible haircut-adjusted collateral, actually free
collateral, the NSE MIS cash rule or qualifying cash field. All remain unproven;
collateral-assisted MIS NOT_READY. Cash-only policy, reserve, CNC, final dispatch
and INR 10,000 full buffered-notional ceiling remain unchanged.
