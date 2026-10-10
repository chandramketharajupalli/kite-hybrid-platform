# Phase 13.5C operational prerequisite matrix

Review date: 2026-10-10 UTC. Baseline 95ef537ede15ee5b3f32f814e5d8ad6f3e0c6805.
Synthetic evidence window completed at 2026-10-10T16:15:31Z; all five rows below
are SYNTHETICALLY_VALIDATED within their stated limitations, never live attestations.
OPERATIONAL_CERTIFICATION = **NO_GO**. Previous Stage B ZERO_CALL_ABORT preserved.
There is no independent live observation timestamp, account binding or attestation
fingerprint for any row. Local test dates are not broker effective dates.

## Deterministic classification

- SYNTHETICALLY_VALIDATED: the specified fixture checks passed in this phase;
  exact newly executed totals and completion times are in validation.
- LIVE_NOT_ESTABLISHED: independent live evidence was not collected/verified.
- STALE: evidence expired or its session/epoch/scope no longer matches.
- CONFLICTING: required sources disagree; neither is silently preferred.
- BLOCKED: a required check cannot complete or denies the precondition.

For each live row, require an independently identified source, intended scope,
current effective/observation time, bounded validity and revocation semantics.
Only every row being independently established and current could satisfy a future
GO review. Synthetic, absent, stale, conflicting or blocked evidence yields NO_GO.
This is a review rule, not an activation switch or executable permission.

| Prerequisite | Synthetic evidence / source | Timestamp and scope | Independent live evidence | Limitation | Denial reason |
| --- | --- | --- | --- | --- | --- |
| Authenticated-session provenance | Handoff/adapter tests: private ownership, execution identity, lease/expiry/replay/concurrency; synthetic receipt leaves V1 terms UNKNOWN | Executed 2026-10-10; fake Kite session and exact loopback equity GET only; see validation completion times | LIVE_NOT_ESTABLISHED; no authenticated runtime/account attestation collected | Local profileValidated(), UUID, token-shaped value or concrete Kite type does not authenticate a real principal | REAL_AUTH_SESSION_NOT_ESTABLISHED |
| Independent HALT | RuntimeTradingHaltTest and handoff tests: knownHaltedAt, unavailable/false/stale epoch denial, concurrent fixture resume and post-response invalidation | Executed 2026-10-10; supplied process-local fixture latch/epoch, no live runtime contacted | LIVE_NOT_ESTABLISHED; actual runtime epoch and independent issuer absent | A local fallback or latch cannot attest another process or exclusion of its execution capabilities | LIVE_HALT_NOT_ESTABLISHED |
| Writer exclusion | Isolation child-process tests; disposable NOLOGIN/existing-writer and write/restore counterexamples; lease/TOCTOU denial | Executed 2026-10-10; owned temp files/child JVM and PostgreSQL 17.6 container only | LIVE_NOT_ESTABLISHED; no enforced all-host exclusion or independent shutdown/reconnect prevention | Cooperative locks miss nonparticipants; NOLOGIN misses existing sessions; equal hashes miss transient restoration | LIVE_WRITER_EXCLUSION_NOT_ESTABLISHED |
| Observer privileges / integrity | Disposable harness suite: SELECT-only ACLs, inherited UPDATE/TRIGGER, visibility, role/function/sequence/DDL checks; private row/count/column-schema comparisons | Executed 2026-10-10; disposable observer/fixture roles, synthetic encrypted row, no development DB | LIVE_NOT_ESTABLISHED; real ACLs, statistics coverage and private baselines absent | Fixed queries are not a general SQL sandbox; snapshots are non-atomic and do not prevent writes | LIVE_OBSERVER_AND_BASELINES_NOT_ESTABLISHED |
| Logging / HTTP safety | Wire/harness/architecture tests: exact route, one execute, no retries/redirects, error matrix, late DEBUG denial, redaction/serialization and no metrics/tracing dependency | Executed 2026-10-10; loopback transport; official budget reservation without execute only | LIVE_NOT_ESTABLISHED; actual sink/agent/tracing/ACL proof and live composition absent | Tests cannot certify actual sink safety or prevent hostile reconfiguration after a check | LIVE_LOGGING_AND_HTTP_COMPOSITION_NOT_ESTABLISHED |

Synthetic row classification becomes SYNTHETICALLY_VALIDATED only upon the passing
runs recorded in [validation](phase-13.5c-validation.md); it never alters the live
column. No independent live timestamp is invented. Source fingerprint is the
baseline plus frozen plan SHA and exact changed-file inventory in validation,
not a secret token/session fingerprint.

## Required independent evidence for a later review

Session: legitimate pre-existing authentication history and intended account/token
binding, trusted owner and recipient, safe composition without startup/restore.
HALT: actual runtime/epoch and configuration provenance, disabled execution
capability, independent issuer and freshness/revocation. Writer exclusion: complete
credential/process/host inventory plus enforceable prevention for both existing
and future writers, including privileged exceptions. Observer: effective ACLs,
inherited memberships, catalog/statistics coverage and private stable baselines.
Logging/HTTP: actual sink/tracing/dump permissions and retention, reviewed process
budget and exact approved route with no follow-ups. None is collected in this phase.

Fresh go/no-go review and separately scoped authorization are required before any
future operation. No real token loading, role provisioning, service shutdown or
network change is authorized by this matrix. Four collateral funding terms remain
UNKNOWN or previously documented public-source CONFLICTING; MIS NOT_READY.
