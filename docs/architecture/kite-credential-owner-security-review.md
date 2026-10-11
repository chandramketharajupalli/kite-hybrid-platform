# Credential-owner security review

SecurityReview.13.5H.v1, prepared 2026-10-11 against
`6ac8b9dda6c0613d8fd3405f27d58acb83c471bc`.
Engineering review only, not a decision by any of the four human review roles.
V2 draft 1 SHA-256:
`5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7`.
Controlling proposal and OwnerBoundary.v1 remain byte-for-byte unchanged.
Architecture C: REVIEWED / NOT IMPLEMENTED. Operational NO_GO.

## Clause-by-clause comparison

O = [original Phase 13.5 approval](../operations/phase-13.5-real-read-approval.md),
including its original mandatory preconditions, not merely its approval annotation.
V = [V2 proposal](kite-db-free-assurance-contract-v2-proposal.md).
B = [OwnerBoundary.v1](kite-credential-owner-service-boundary.md).
The labels below classify proposed changes, never changes to the contract in force.

| Clause / classification | Original requirement and actual evidence limit | Proposed V2 / source | Residual risk and decision obligation |
|---|---|---|---|
| 1a Global invariance: NARROWED | O preconditions 3-5 and postconditions require exclusive window, no reconnecting writers, private token/trading snapshots and comparisons | V Normative claim disclaims global invariance; no evidence that other writers are inactive | Material removal requires explicit acceptance, especially Runtime/DB and User/operator. Global DB nonmutation NOT_PROVEN |
| 1b Own-process proof: REPLACED | Restricted SELECT observer and no writable/lifecycle components; snapshots cannot themselves prevent writes | V Admission and B Minimal runtime require absence of direct/indirect trading-write authority through package/OS/mount/network controls | Proposed preventive proof is not present in the full JAR. Omitting one connection proves neither isolation nor global safety |
| 2 Snapshot limitation: RETAINED as a limitation, REPLACED as proof mechanism | O combines snapshots with independent exclusion; Integrity.capture observes at separate times | V drops DB snapshots from new boundary; I test committedWriteAndRestorationBetweenSnapshotsCannotCertifyWriterExclusion shows equal snapshots despite two writes | Write-and-restore can be invisible to those observations. Never reinterpret old snapshots as global proof or bypass old capture() |
| 3a Credential custody: REPLACED | O precondition 1 requires existing authenticated in-memory session and safe handoff; no restore/startup | V custody and B Legitimate authentication origin require isolated owner-originated session, no token transfer, narrow read delegation | Service wrapping trading JVM fails separation; in-memory secret remains exposed to trusted host/admin compromise |
| 3b Authentic origin: RETAINED requirement; NEW explicit trust chain | O already required independent intended-account/session binding, never merely local authenticated state | V admission binds broker app/account/owner/incarnation/session; B describes independent provenance authority and separately authorized enrollment | Broker compliance/origin not established. UUID, signed local claim, fixture or bearer possession is insufficient |
| 4a One attempt/no retry: RETAINED | O maximum one attempt across runs, no retry/redirect/follow-up; factory only enforces JVM-local atomic guard | V Lifecycle retains budget and uncertainty consumption | Local tests cannot prove cross-restart budget; exactly-once delivery never follows |
| 4b Durable enforcement: NEW | Static REAL_PROCESS_ATTEMPT disappears with process; no persistent approval ledger | B Cross-process ledger specifies atomic durable claim and rollback/failover fencing | Quorum, storage, epoch anchor, codecs and independent recovery not implemented or verified |
| 5 HALT proof: REPLACED mechanism, RETAINED live prerequisite | O required known stable runtime HALT; local knownHaltedAt and synthetic witness do not attest other runtimes | V/B require independent issuer, complete runtime scope, signed epochs/challenges, time/revocation before dispatch and acceptance | Signed false or incomplete assertion is still false; TOCTOU and compromised authority remain explicit risks |
| 6 Log containment: RETAINED secrecy objective; NEW complete enforcement specification | O precondition 6 forbids sensitive logs/traces/dumps; code checks known wire/header DEBUG and source redaction | B sink inventory/allowlist, OS/proxy/container/IDE evidence, startup/pre-dispatch drift denial | No full runtime containment implementation. Source tests cannot certify external sinks, hostile agent or host |
| 7 Package/network boundary: REPLACED proof boundary | O isolated call composition avoids startup/order calls but full artifact contains them and JDBC | V/B require minimal loadable artifact, separate identity, no trading route/credentials/mounts or writer RPC | L3 host allowlist cannot constrain HTTPS method/path; restricted client and artifact integrity are also required |
| Exact operation and funding limits: RETAINED | One GET equity margins; no supplementary endpoints or trading/funding promotion | V exact literal route and bounded receipt; four funding terms remain unresolved | Funds observation is not proof of eligible/free adjusted collateral, applicable NSE MIS cash rule or cash-field mapping; MIS NOT_READY |

The Phase F v1 was also a proposal, not an intervening approval. Its ownership
review and Phase E minimal-runtime document explain evolution of the design, not
authority to change the old contract. Phase E's label C meant a synthetic fixture;
Phase G/H's C means the isolated credential-owner architecture. Do not conflate them.

## Actual source findings

Java production base: apps/trading-core/src/main/java/com/kitehybrid/platform/.
Kite classes are under broker/infrastructure/kite unless qualified below.

| Inspected source / actual signature | Finding and consequence |
|---|---|
| KiteEquityReadHarness.run() and constructors requiring KiteEquityReadIntegrity | Captures before/after; cannot omit or stub integrity to activate V2 |
| KiteEquityReadHandoff.consume(Owner,Recipient,String,String); inspect(Owner,Recipient) | Reference identity, CAS consumption and expiry are local; constructor rejects officialOrigin |
| KiteEquityReadIsolation(Clock,Duration,BooleanSupplier).valid() | Synthetic lease/witness, maximum 30 seconds; unknown/rollback denies, not independent host isolation |
| KiteEquityReadIntegrity(Connection).capture() | Fixed SELECT/SHOW and private table/schema hashes under observer checks; multi-query snapshots cannot exclude intervening writes |
| KiteEquityReadRequestFactory.createRequest(URI,HttpMethod) | Exact GET/URI, per-factory and static process budget, one execute, known loggers rechecked; no durable multi-process ledger |
| KiteSession.install(KiteAccessToken), profileValidated(), executionIdentity() | Local state transitions can authenticate a fixture; UUID is not broker attestation; legacy constructor installs configured credential |
| shared/application/RuntimeTradingHalt.knownHaltedAt(Epoch) | Local known epoch/startup check; unavailable startup denies diagnostic, no independent live coverage |
| broker/application/auth/KiteAuthenticationUseCase.restore(), complete(String,String,String,String), reset() | restore loads store; complete exchanges/validates/persists/initializes instruments; reset clears store. Exclude this lifecycle from diagnostic |
| KiteAuthenticationAdapter.exchange(String) | Session exchange and application-key check are source behavior, not proof of an independently authenticated isolated owner |
| KiteInfrastructureConfiguration.restoreKiteAuthentication(KiteAuthenticationUseCase) | ApplicationRunner invokes restore; no normal Spring startup |
| bootstrap/TradingCoreApplication.main(String[]), OperatorConsoleApplication.main(String[]) | Broad Spring context and operator access; never diagnostic launchers |
| apps/trading-core/pom.xml | Boot web/JDBC/Redis/Actuator, Flyway, PostgreSQL, metrics and full application start-class remain; not a minimal service |

Existing ControlledEquityReadArchitectureTest constrains direct dependencies and
calls, not transitive artifact loadability or hostile reflection. Handoff tests
cover local identity/replay/expiry/serialization; RequestFactory tests cover exact
route/no retry/lost response/log drift. The disposable integration counterexample
changes and restores a fixture token timestamp between captures; equality holds.
Its PostgreSQL explicitly uses fsync=off and cannot certify future ledger durability.
All are source-inspected and historically validated; none was rerun in Phase H.

## Owner, caller and legitimate provenance

Only the intended account user or an independently verified broker-permitted
authorized principal may complete future authentication, with explicit consent
for the registered application and isolated owner. No actual person or service
principal has been verified here. A requester can ask for an approved observation;
it cannot authenticate itself as the broker account, supply a session, import
credentials, impersonate the user or approve its own request.

Future enrollment needs separate explicit authorization and independent review
against then-current authoritative Kite requirements: legitimate user interaction
and required MFA at the broker; registered application/callback; one-use transaction
state; authenticated callback/exchange evidence; intended account/application match;
trusted clock, expiry and revocation. Independent verification must follow evidence
from the broker-authenticated event to the exact approved owner artifact and boot
incarnation. Merely signing the owner's own claim does not supply that chain.
This offline phase does not certify protocol compliance or perform enrollment.

Provenance verifier, authorizer, HALT issuer, deployment operator and requester have
separate responsibilities and keys. Their authenticated assertions must bind
audience, contract/policy digest, owner identity/incarnation, protected account/app/
session epoch, scope, issue/expiry and revocation. Public receipts omit identities
and token-derived hashes. No role can substitute its own assertion for another's.

Seal separately authorized enrollment before enabling any diagnostic read; prove
the transition removes enrollment egress and entrypoints. Unverifiable seal denies.
The read service refuses credential import/export, serialized KiteSession, cookies,
token restoration, trading DB rows, generic URL/method/SQL/RPC/command inputs,
profile/history/WebSocket/order endpoints and authentication fallback. It exposes
only the narrow operation, not login, reset or exchange.

Require independently authenticated caller identity and exact owner incarnation;
bind protected account, equity segment, purpose EQUITY_MARGIN_SHAPE_OBSERVATION,
GET https://api.kite.trade/user/margins/equity, proposal approvals, one attempt,
expiry and HALT evidence. No caller-selected alternate account/segment/URL or bearer
handoff. Unknown, stale, forged, conflicting or missing evidence denies.

Owner crash loses in-memory session and invalidates incarnation; no restoration
or token transfer to a failover replica. Logout/rejection/expiry/revocation/session
replacement denies further use. Local invalidation does not claim broker logout;
any actual broker revocation call would need separate permission. Real ownership
remains AUTHENTICATED_OWNERSHIP_NOT_ESTABLISHED / NOT_ESTABLISHED.

## Durable one-use specification

Status: DESIGNED / NOT_IMPLEMENTED / NOT_LIVE_VERIFIED. The state name
CONSUMED_BEFORE_DISPATCH below makes V2's existing CONSUMED semantics explicit;
it does not change or implement the committed proposal.

```text
ISSUED -- atomic durable claim --> CONSUMED_BEFORE_DISPATCH
ISSUED -- revoke/expire --------> REVOKED / EXPIRED (no dispatch)
CONSUMED_BEFORE_DISPATCH -------> CONSUMED_NO_DISPATCH
CONSUMED_BEFORE_DISPATCH -------> OBSERVATION_ACCEPTED
CONSUMED_BEFORE_DISPATCH -------> REJECTED_RESULT
CONSUMED_BEFORE_DISPATCH -------> INDETERMINATE
All terminal outcomes retain consumed/tombstone identity; no arrow returns to ISSUED.
```

Use a separate non-trading authority with linearizable synchronous durability and
independently protected epoch/rollback anchor. The owner accesses a narrow claim/
revocation API, never trading PostgreSQL/Redis or generic SQL. Bind unique approval
ID, exact approved proposal digest and verified decision references, policy digest,
authorizer, recipient/caller identity, owner incarnation, protected account/app/
session epoch, equity segment, exact route/method/purpose, not-before/expiry,
budget=1, ledger epoch, revocation generation and HALT assertion digest/epoch/challenge.

Before claim, validate all evidence and authenticated channels. In one transaction,
compare immutable bindings and ISSUED state, time and revocation; consume, commit
durably, then acknowledge. Only the successful live invocation gets a non-reusable
dispatch grant. Local CAS prevents duplicate execution within that owner. Revalidate
HALT/provenance/revocation/time/isolation/sinks immediately before dispatch and
before accepting the result. Failure after claim never refunds the budget.

| Fault | Required behavior and remaining proof obligation |
|---|---|
| Lost ledger acknowledgment | No dispatch; no claim retry that can recover a send grant. Treat unresolved commit as spent/quarantined. Even if later observed ISSUED, original uncertain invocation cannot dispatch; authority must fence it before any recovery decision |
| Duplicate delivery / replica race | Exactly one committed claim under linearizability; duplicates return spent/denied, never a replayable send grant |
| Split brain / partition / unavailable quorum | Deny; no local-cache fallback or minority claim. Certify quorum and owner/ledger epoch fencing before implementation acceptance |
| Crash before or after dispatch | Consumed remains; new owner incarnation cannot use prior grant or session. Zero sends or unknown sends are possible |
| Timeout / response loss | INDETERMINATE, permanently consumed. No retry, redirect, supplementary endpoint or automatic approval renewal |
| Clock uncertainty / expiry | Enforce validity using trusted UTC uncertainty interval plus monotonic elapsed time. Reject rollback, stale synchronization, out-of-window bounds; no expiry grace |
| Ledger rollback / backup restore | Reject stale epoch and retire affected approvals under independent recovery. Never revive ISSUED from a restored backup |
| Audit replay / tampering | Audit cannot authorize dispatch; protected sequence/digest continuity and external epoch anchor required; missing continuity denies |
| Revocation racing dispatch | Pre/post checks deny/reject as observed. They cannot retract a sent request; do not claim atomic broker-send/revocation ordering |

Spent IDs/tombstones survive all possible replay windows; seven-day receipt logging
retention is not permission to delete live replay protection. No approval ID reuse.
Storage technology, quorum configuration, transaction isolation, durable ack proof,
fencing mechanism and recovery authority still need concrete engineering decisions.
Tests must kill processes around commit/ack/send, partition replicas and restore
stale snapshots on disposable infrastructure with actual durability enabled.
The promise is at-most-once authorized attempt, not exactly-once broker delivery.

## Independent HALT and isolation

Future authority: independently administered runtime controller, separate from
owner/requester, with actual inventory of every execution-capable trading runtime.
Bind issuer/key/schema, contract/deployment/audience, instance-set digest, each
HALTED_KNOWN state and disabled execution authority, monotonic epoch, fresh stage
challenge, issue/expiry and revocation. Signed canonical data needs provisioned
trusted keys; do not trust caller-provided keys or halted=true.

Verify freshness, complete coverage, signature and current revocation before
dispatch and result acceptance. Reject unknown/stale/forged/conflicting/missing
assertions, reused challenges, epoch rollback, key compromise or coverage drift.
Anti-replay and epoch floors must survive verifier restart independently; otherwise
deny. Audit signed assertion identity and outcome privately without session/account
payload; verify issuer competence as well as signature validity. No actual issuer,
key ownership or live all-runtime HALT has been established.

Even if another runtime remains active, the proposed owner has no loadable order
gateway, general broker client, permit/risk/strategy authority, trading DB/Redis
route, writable trading mount or writer-service RPC. Exact-path code and immutable
artifact enforce the allowed HTTP operation in addition to host egress controls.
If active runtime contradicts required HALT coverage, admission denies any read.
Those controls protect the owner's own authority; they cannot prevent another
runtime or privileged external actor writing trading state. Global nonmutation
NOT_PROVEN; own-process noninterference PROPOSED, NOT LIVE CERTIFIED.

## Packaging, egress, confinement and shutdown

Minimal future artifact: dedicated sealed read-mode entrypoint; reviewed TLS/HTTP,
bounded strict parser/decimal model, clocks, signature verification, narrow control
clients, local execute guard and bounded receipt encoder. No current trading-core
JAR, including plain classifier, qualifies. No live launcher is created here.

Require reproducible build provenance, SBOM/transitive dependency allowlist, artifact
digest/signature, service-loader/reflection/config-hook review and negative class-
loadability tests. Exclude ApplicationRunner/Spring scanning, auth restore/reset,
order gateway/execution permit/risk approval, historical acquisition, WebSockets,
trading persistence/JDBC/Redis/Flyway and credentials. Independently check build
inputs and actual deployed digest before crediting any implementation claim.

Separate least-privileged OS identity; no administrator, trading groups, process
attach/debug/agent access, arbitrary subprocess or general filesystem authority.
Read-only artifact/config/trust roots; private volatile workspace; no user home,
.env, trading mounts, Docker socket, host networking, privileged container, shared
write volume or reparse/symlink escape. Closed environment allowlist by name; no
secret/proxy/agent/classpath/config inheritance, including JAVA_TOOL_OPTIONS,
JDK_JAVA_OPTIONS and _JAVA_OPTIONS. Never log rejected variable values.

Network default deny: permit only separately authorized exact broker TLS destination
and reviewed named ledger/identity/HALT/time control channels, with no forwarding
to trading services. Verified TLS, fixed route/method, no redirect/retry/cookies/
auth challenge, unknown proxy or DNS/metadata/tunnel bypass. HTTPS host allowlisting
alone cannot prevent order paths; narrow code plus trusted artifact must enforce
method/path. Unknown effective proxy/environment/route denies startup or dispatch.
Application-secret provisioning/enrollment custody is an unresolved separately
authorized design task, never a shortcut through a trading token file or .env.

Failure closes admission; after claim preserve consumption and discard unsafe
results. Shutdown revokes owner incarnation/local grants, closes only owned
resources, releases memory without claiming guaranteed JVM zeroization, and never
restores tokens, resumes HALT, issues permits, repairs DB state or refunds budget.
Crash recovery is fail-closed re-enrollment only under a new explicit scope.

## All-sink containment and proof

| Sink class | Required policy and independent evidence |
|---|---|
| Console stdout/stderr, IDE terminal/capture | No owner payload stream; bounded launcher exit code only; inspect launch redirection and IDE capture |
| Java console/file/network appenders, async buffers | Single reviewed receipt sink/encoder; enumerate effective appenders, levels and configuration digest; reject extras/dynamic changes |
| HTTP wire/header/TLS, reverse proxy/service mesh | No headers/bodies or TLS interception; no intermediary retries, access-query capture or trace propagation; verify actual proxy policy |
| Metrics, traces, exporters, JMX, Actuator, APM | Disabled; no account/session labels or general object capture; dependency and runtime agent/exporter audit |
| Exception handlers, uncaught exceptions, serializers | Bounded fixed reason enums only; no message/cause/stack/raw response/header/object; fault-inject encoder and parser failures |
| Heap/core/crash/thread dumps, JFR, debugger/agents | Disabled and restricted by OS/container policy; reject unknown collector/attach capability; no claimed zeroization proof |
| Windows Event Log, WER, host diagnostics | Independently verify crash forwarding/event collectors cannot capture owner memory/payload; unknown policy denies |
| Docker/container log drivers and collectors | No raw stdout or external forwarding; certify runtime driver, host collector and access controls |
| CI/build/test artifacts, backups and log shipping | Synthetic markers only; no real service execution or credential/env/body dumps; verify retention, destination and backup exclusions |

Allowed receipt fields exactly follow B: schema/contract version, fixed endpoint
enum, bounded phase/outcome/reason, conservative attempt count 0/1, provenance
classification and UTC receipt time. Reject all free text, Authorization/cookies,
account identifiers or hashes, token/session/capability objects, raw balances and
response bodies. Generic serialization forbidden; redacted toString alone is not
proof. Dedicated control encoders carry only protected approval/attestation fields
over authenticated channels and cannot feed logging serializers.

Proof must enumerate every effective sink at startup and immediately before
synthetic dispatch, verify immutable settings/ACLs/dumps/agents/proxies, inject
synthetic sensitive markers through all success/failure paths and inspect every
permitted capture. Unknown sink or drift denies; postcheck drift rejects result.
Owner-controlled bounded sink with independently verified operator access; proposed
seven-day receipt retention/no backups from B still requires approval and actual
ownership. A unit test of source redaction does not certify external sinks.
Live containment NOT_VERIFIED.

## Readiness conclusion

C remains a defensible design direction because requester bearer custody and
trading authority are excluded by design. Its implementation is not ready to be
authorized on current evidence: four attributable contract decisions are absent,
and concrete auth provenance, ledger durability/fencing, HALT authority, minimal
artifact, sink enforcement and operational ownership still need evidence.
No reviewer rejection is invented from those engineering gaps. The result is
CONTRACT_APPROVAL_PENDING, not CONTRACT_REJECTED. V2 approval, if later genuine,
still would not authorize implementation, tokens, deployment, reads or trading.
