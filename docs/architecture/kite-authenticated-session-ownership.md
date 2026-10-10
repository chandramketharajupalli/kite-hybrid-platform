# Authenticated-session ownership architecture decision

Phase 13.5F, 2026-10-10. **Architecture recommended: C, a separately isolated,
DB-free custody-and-read service.** Its physical runtime may be a minimal JVM as
proposed in Phase E (B), but it must own legitimate authentication rather than
receive a portable KiteSession. No candidate currently meets all live prerequisites.
C is a conditional design recommendation, not deployed software or operational GO.

## Comparison

| Dimension | A: trading JVM + observer | B: separate diagnostic JVM | C: isolated custody/read service |
|---|---|---|---|
| Startup and attack surface | Trading runners, restore, operator and writable state | Can omit trading startup; packaging unresolved | Can omit trading startup; narrow service/channel adds attack surface |
| Session ownership | Existing object possible, but shared privileged process | No legitimate source for transferred private references | Owner retains token and executes bounded read; requester never receives bearer |
| Token exposure | Shared memory/agents/dumps | Transfer/loader risk unresolved | In-memory custody still exposed to compromised owner/host |
| HALT | Local latch not independent | External attestation required | External attestation and requester binding required |
| DB writers | Observer samples; no prevention | Proposed no DB access, others unaffected | Same no-access objective; independent authorization ledger outside trading DB |
| Windows/WSL/Docker | Easy reuse, poor authority separation | Dedicated account/process/namespace feasible but unproven | Service identity, authenticated channel and deployment controls add operational burden |
| Packaging/logging | Full artifact unsuitable | Minimal allowlist and sink controls needed | Same plus channel/server audit and strict request schema |
| Audit/revocation | Local references work only in same JVM | Cross-process ownership unresolved | Independent issuance/revocation and durable one-use authority required |
| Decision | Reject for new contract | Retain physical deployment option, insufficient ownership architecture | Prefer subject to authentication-origin and deployment review |

Do not use an existing trading JVM as C's custody service: that reintroduces A's
execution and persistence authority. Do not call a service DB-free if it forwards
to a trading process, writable store or generic credential service that grants
broader operations. Operator burden and key/issuer compromise are residual risks.

## Legitimate authentication origin: still unresolved

No non-transferable secret can appear in a new process from a pre-existing object
in another process without a transfer or a new authenticated origin. This design
does not claim otherwise. The viable future branch is an isolated owner that was
itself legitimately authenticated earlier under a separately approved interactive
Zerodha authentication lifecycle, then retains that session privately. Account and
session binding must come from authenticated broker evidence, not a synthetic flag.

That earlier lifecycle does not exist as reviewed isolated code here. Its login,
callback/exchange/profile requests and any state handling would need their own
scope, budget and security review; they are not included in a one-GET diagnostic
authorization or this phase. No password/MFA automation, real login or token load
is implemented. A service restart loses authority and must deny; no restoration
fallback. If no such already-authenticated owner exists, the design cannot run.
REAL_AUTH_SESSION_NOT_ESTABLISHED remains the correct result.

## Owner, requester and channel trust

Future issuer must independently authenticate an approved owner process/artifact
and its legitimate broker/account/session provenance. Trusted requester identity
must be bound to the specific approval and recipient, not IP address, UUID or
caller-supplied label. A reviewed authenticated channel (for example mutually
authenticated TLS with pinned service identities and rotation policy) carries only
narrow request authorization and redacted receipts, never broker bearer material.
Windows-local IPC ACL alone is not sufficient if privileged or other-host actors
are in scope. Channel choice, credential provisioning and keys remain unimplemented.

Authority record must bind contract digest, deployment/owner/requester identities,
private account/session mapping, NSE equity MIS purpose, exact endpoint, one attempt,
HALT attestation/epoch, issuance/expiry, revocation and isolation scope. Requester
must possess authenticated identity plus valid one-use approval; a copied nonce
alone gives no authority. Public receipts contain no private binding fingerprints.
Service exposes no arbitrary URL, token retrieval, order route, login/restore,
DB query or execute endpoint. No such service API is created in this phase.

Single atomic winner consumes authority before HTTP. An independent approval
authority tracks consumption across replicas and restarts outside trading state;
unavailable/ambiguous ledger means deny, not a local fallback. Timeout/response loss
is spent/indeterminate and cannot retry. Recipient disconnect, owner crash,
session expiry/replacement/auth failure, lease loss and HALT change revoke/discard.
Restart cannot resurrect an in-memory session or replay an approval. Memory
zeroization cannot be guaranteed for JVM strings; prohibit dumps/agents and keep
host compromise explicitly outside the assurance assumption, not silently solved.

## External HALT attestation proposal

Authorized issuer is a deployment/runtime controller independent of the requester
and custody service. It must authenticate the actual execution-capable instances
and cover their complete set, not merely sign the caller's halted=true value.
Attestation binds issuer/key version, audience, contract/deployment scope, instance
set, HALTED-known status, disabled execution authority, monotonic epoch, challenge,
issuance/expiry and revocation. A signed false assertion remains false; issuer
operational competence and coverage require separate proof.

Reject unknown instance, unavailable issuer, stale/expired/future evidence, excess
clock skew, replayed challenge, older epoch, mismatched audience/session, conflicting
instance state or missing revocation status. Epoch floor and anti-replay state must
survive verifier restart through independently trusted authority, or restart denies.
Recheck immediately before dispatch and after response. Key compromise requires
revocation of the issuer/key and outstanding attestations, explicit recovery review
and no fallback to a local latch. No key service, signature protocol or live
attestation is implemented or tested here.

RuntimeTradingHalt.knownHaltedAt supplies the existing synthetic process-local
check only; execution semantics are unchanged. Current tests deny unknown startup,
changed epoch and midflight invalidation. They cannot authenticate an external
issuer, enumerate live runtimes or establish issuer key security.

## Current source boundary and assurance gap

KiteEquityReadHandoff binds private Owner/Recipient references, authenticated local
KiteSession.executionIdentity, HALT epoch, injected clock and expiring isolation.
Atomic consume, replay rejection and revocation are tested; official-origin factory
is rejected. It is not a portable capability or cross-process protocol. JSON/native
serialization cannot export it. No new source identifier changes this fact.

KiteAuthenticationUseCase.restore and PostgresKiteAccessTokenStore load/decrypt/
save/clear are excluded; ApplicationRunner restoreKiteAuthentication must not run.
OperatorExecutionService and OrderExecutionGateway are outside the diagnostic
call graph. A caller-created profileValidated session proves only fixture state.

No new implementation is justified until owners approve the v1 assurance changes,
legitimate authentication-origin lifecycle, minimal dependency extraction and
channel/attestation/ledger trust model. These are concrete design decisions to
review, not a request to run another real read or repeat synthetic tests as proof.
All four collateral/cash terms remain UNKNOWN/CONFLICTING; MIS NOT_READY.
