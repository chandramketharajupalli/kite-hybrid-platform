# Phase 14.12 offline adapter integration

PHASE_14_12_PARTIAL. Runtime adapters now compose the unchanged Phase14.11 policy,
but authoritative provisioning, restart-resistant revocation and independent storage
custody are not established. No production dispatch, credentials or launcher exists.

## Reuse and dependency boundary

apps/gate-a-offline-adapters is outside the root reactor. Its compiler default-compile
execution selects eighteen exact runtime source files: four new adapters, the existing
policy and thirteen existing parser/resolution/domain/support sources. No files copied
or old sources edited. The only external runtime dependencies are Jackson CSV/core/
databind/annotations. The explicit allowlist and implicit=none prevent incidental
compilation; testCompile uses only this module's tests. The final artifact has no
Main-Class, test probes, broker transport, Spring, SQL, session restoration or networking
implementation. Shared Identifiers contains OrderId value types, not order execution.

| Component | Status and reuse |
|---|---|
| GateAOfflinePolicy | Existing runtime; exact route, ordering, fixture-only observation and redaction reused unchanged |
| SignedHaltAuthority | New runtime; verifies supplied RSA3072-or-stronger permit and current-state signatures, no issuer |
| FileAttemptAuthority | New runtime; isolated one-attempt filesystem store, no operational DB |
| NseMasterAdapter | New runtime bridge to unchanged KiteOfflineInstrumentMaster and OfflineUniverseResolution |
| SessionCustody | Metadata-only future contract; no token method or implementation |
| SyntheticHalt | Earlier synthetic runtime; signing format reviewed, not imported as genuine authority |
| GateABoundary/GateAHaltBoundary/AttemptJournal/witness fixtures | Historical test-only guarantees reviewed, not shipped |
| KiteRestTransport/KiteHistoricalAdapter | Excluded; no order-capable or historical transport imported |

```text
closed FixtureResponse -> GateAOfflinePolicy.observeFixture
 -> exact GET/URL/no-proxy + authorization shape
 -> SignedHaltAuthority: pinned key verifies permit and current HALT evidence
 -> FileAttemptAuthority.available -> CREATE_NEW spent -> write -> force -> ACK
 -> immediate SignedHaltAuthority recheck -> synthetic observation counter
 -> NseMasterAdapter -> existing parser -> existing resolver
 -> final HALT check -> synthetic hash/time receipt -> write-once outcome
```

## Authority and revocation limits

Permit signature binds operation, exact endpoint/method, process incarnation, UUID,
key ID, epoch, issued/expiry (maximum30s) and approval reference. Signed state binds
epoch, revoked/quarantined flags, observation/expiry (maximum5s), key/process/approval.
Constructor pins the actual public key and minimum epoch. Missing or forged evidence,
wrong scope, expired/stale state, unavailable supplier and key conflict fail closed.
Clock/epoch/state-time rollback quarantines the verifier instance. Observed revocation
or quarantine is sticky for that instance. Final revalidation follows consumption;
revocation there yields zero observations and a spent attempt. No retry/refund.

Synthetic signing private keys exist only in tests. There is no runtime issuer or
default-allow provider. A signed approval-reference string is not independent proof
of an owner's decision. Trusted key provisioning, current-state authenticity/custody,
durable minimum epochs, freshness across restart and revocation delivery require
external authority. A valid unrevoked snapshot can remain replayable within its short
validity window before revocation is observed; restarting without retained revocation
authority can lose local quarantine. PRODUCTION_AUTHORITY_NOT_ESTABLISHED.
The final check is a fixture boundary, not a real socket-send linearization guarantee.

## Filesystem authority model

Fixture initialization creates a new directory/identity exactly once, bound to store
UUID and the full authorization digest. Resume never initializes. CREATE_NEW of the
single spent marker is the local linearization point, shared by competing JVMs.
ACK follows complete marker write and FileChannel.force(true). Empty, malformed or
partial existing markers deny reuse. Missing/corrupt identity, wrong binding, symlink
paths, failed writes and uncertain acknowledgments deny. Outcome creation is write-once
and never reopens the attempt. No store deletion/recovery method is provided.

Eight process tests exercise two competitors, termination before/after reservation,
after force/observation, restart, corrupt marker and rollback. Children have128MiB heaps,
30s timeouts, maximum two concurrent children and owned JUnit temporary directories.
Restoring process state while the marker survives denies reuse. The deliberate complete
authority rollback test removes only its owned spent/outcome files and gets a second
synthetic observation. This retains the counterexample rather than hiding it.

No independent witness is newly integrated. Local disk rollback or a privileged actor
replacing the directory defeats this single authority. Parent-path checks do not stop
an administrator racing filesystem replacement. Directory-entry/power-loss persistence,
physical media guarantees, remote filesystems and multi-host failover are not tested.
ROLLBACK_RESISTANT_AUTHORITY_NOT_ESTABLISHED. Exactly-once network delivery is not claimed.

## Parser and response boundary

The unchanged importer verifies provenance SHA256/length, strict UTF8/no BOM/NUL,
required unique headers, numeric tokens and CSV semantics. The adapter is synthetic-only,
caps supplied arrays at32MiB, requires NSE CASH, rejects duplicate tokens/identities and
formula-prefixed canonical symbols (=,+,-,@). It never rewrites supplied universe symbols.
No CSV export exists; future spreadsheet export requires independent cell escaping for
all fields. Formula policy may reject a genuine unusual symbol; do not silently remap it.
Ignored CSV fields are not exported or treated as trusted executable content.

Resolver returns ambiguous/missing rows without inventing tokens. Original ZERODHA
namespace/token remains unchanged; the existing KITE comparison projection is reused.
Provenance contains fixed synthetic observation time, source reference and raw hash.
Parser rows are research validation output, not approval to publish/trade; any policy
failure invalidates that observation's use. Bounds cover input arrays, not real wire,
decompression, whole-process RSS or secure physical deletion. No genuine persistence.

## OS, custody and compliance

Historical evidence: Phase13.7-13.11 validates bounded local/witness recovery; Phase13.11
measured UID/GID65534, CapEff0, NNP1, Seccomp2, read-only root, network-none and resource
limits in a Linux fixture. Phase13.12 BLOCKED teardown/resource incident remains intact.
Phase13.14 platform matrix and Phase13.16 G03-G14 retain missing enforcing LSM/remapped
namespaces/selective egress/diagnostic sinks/independent custody. No new Docker, host,
network, DNS or OS-security test was performed. PRODUCTION_EGRESS_NOT_VERIFIED.

SessionCustody describes references/timestamps only. No credential accessor, CLI/env
loader, token serialization, restore or interactive login. AUTHENTICATED_SESSION_NOT_ESTABLISHED.
External logging/backup/dump custody and secure erasure remain unverified.

Reported compliance submission and all eleven unresolved topics remain in the unchanged
Phase14.11 generation-2 record. No original screenshot/body hash, delivery acknowledgment
or Zerodha response supplied. ZERODHA_TERMS_UNRESOLVED. Gate A/B NOT_AUTHORIZED; NO_GO.
