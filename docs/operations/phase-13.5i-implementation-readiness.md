# Phase 13.5I implementation readiness

**IMPLEMENTATION_READINESS = IMPLEMENTATION_NOT_AUTHORIZED / GAPS_OPEN**.
Contract decision CONTRACT_APPROVAL_PENDING; OPERATIONAL_GO_NO_GO = NO_GO.
This is a source-based reassessment of [H readiness](phase-13.5h-implementation-readiness.md)
and [owner security review](../architecture/kite-credential-owner-security-review.md),
not new implementation or live verification. Historical synthetic guards do not
validate the proposed cross-process service. Contract approval would not authorize implementation.

| Requirement | DESIGNED | IMPLEMENTED (proposed service) | TESTED_SYNTHETICALLY this phase | INDEPENDENTLY_VERIFIED | APPROVED | LIVE_VERIFIED |
|---|---|---|---|---|---|---|
| V2 decision protocol | Yes | Documentary ledger only | Offline pending replay only | Human attribution absent | PENDING | Not applicable |
| Legitimate auth origin/custody | Requirements only | No | NOT_RUN | NOT_ESTABLISHED | PENDING | No |
| Owner/caller/account/segment binding | Yes | No | NOT_RUN | No | PENDING | No |
| Durable cross-process/restart one-use ledger | Yes | No | NOT_RUN | No | PENDING | No |
| External all-runtime HALT | Yes | No | NOT_RUN | NOT_ESTABLISHED | PENDING | No |
| Minimal packaging/dependency/startup isolation | Yes | No | NOT_RUN | No | PENDING | No |
| Endpoint/egress allowlist | Yes | No | NOT_RUN | No | PENDING | No |
| Complete logging containment | Yes | No | NOT_RUN | NOT_VERIFIED | PENDING | No |
| Recovery/fencing and split-brain denial | Yes | No | NOT_RUN | No | PENDING | No |
| Own-process DB noninterference | Proposed | No | NOT_RUN | No | PENDING | No |
| Service negative cases | Specified | No service | NOT_RUN | No | PENDING | No |
| Operations ownership | Duties specified | Named accountable owners UNKNOWN | NOT_RUN | No | PENDING | No |

Architecture C remains the proposed boundary: isolated owner retains credentials,
authenticates callers, binds account/application/segment and incarnation, and returns
only bounded observations. Legitimate broker-compliant user authentication requires
separate authorization and independent origin evidence; no trading token restoration,
cookie scraping, MFA bypass, caller-supplied bearer or trading startup import qualifies.
Real authenticated ownership remains NOT_ESTABLISHED.

The future independent non-trading ledger must atomically and durably move ISSUED
to CONSUMED_BEFORE_DISPATCH before the exact authorized request. Bind contract
approval, owner incarnation, recipient, method/endpoint, purpose, expiry and HALT
epoch. Lost acknowledgment, crash, uncertain response, network partition or replica
race denies another dispatch. Preserve consumption through restart and rollback
fencing; no retry, redirect, renewal or supplementary endpoint. This is at-most-once
attempt authorization, not exactly-once delivery. Existing DB fixtures do not prove
future durable crash behavior. The Phase I review ledger is not this runtime store.

An independent competent HALT authority must cover all relevant runtimes with
signature, issuer/audience, epoch, freshness, challenge/replay protection, revocation
and trusted time. Verify before dispatch and before accepting results; unknown,
missing, stale, forged or conflicting assertions deny. Local knownHaltedAt is not
live proof. HALT neither grants permission to read nor authorizes resume/arming.

Minimal packaging must exclude trading ApplicationRunner, order gateway/permits,
historical acquisition, WebSockets, trading persistence, Flyway and DB credentials.
Require SBOM/transitive build inspection plus runtime reachability under separate OS
identity, immutable configuration, bounded mounts/environment and network confinement.
Deny trading PostgreSQL/Redis and order paths regardless of other active writers.
Future separately authorized request scope is exactly GET
https://api.kite.trade/user/margins/equity, with no redirects/retries/proxy or
environment escape. No such request occurred. Current trading-core JAR is not a
minimal certified owner; no launcher or deployment was created.

Sink allowlisting must cover stdout/stderr/IDE, Java appenders/files/network,
HTTP headers/wire/proxy/mesh, telemetry/metrics/traces/APM/JMX/Actuator, exception
handlers/object serialization, debugging/JFR/agents, heap/core/crash dumps,
Windows Event Log/WER, container collectors, CI artifacts and backups. Owner-controlled
fixed event fields, redaction, retention, access and deletion require independent
proof; forbid credentials, cookies, capabilities, account IDs and raw balances in
serialization/errors. Reject unsafe startup or pre-dispatch drift; fail closed and
shut down without dispatch or credential export. Source redaction alone is inadequate.

Own-process inability to mutate requires verified absence of direct/indirect write
authority. Global DB nonmutation remains NOT_PROVEN; snapshots miss write-and-restore.
Funds observations establish neither eligible haircut-adjusted collateral, applicable
NSE MIS cash rule nor qualifying cash mapping. MIS stays NOT_READY; cash reserve,
CNC and INR 10,000 full buffered-notional ceiling remain unchanged. No Phase 12,
H1/H2 or sealed SBIN July promotion follows from a contract decision.

Next gates: obtain four attributable digest-bound decisions through authorized
coordination; resolve conditions/conflicts; only then seek separately scoped
implementation authorization. Authentication, deployment, independent live safety
certification and any real read each remain separately gated. No work follows automatically.
