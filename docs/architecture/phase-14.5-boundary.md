# Offline Gate A boundary and threat model

Boundary is a package-private test-scope component in apps/synthetic-credential-owner.
It is excluded from the runtime JAR, reactor and trading application. Existing
PackagingTest remains unchanged and forbids broker origin/trading dependencies in
packaged runtime. There is no production client, launcher, credential API or boolean
that enables real I/O. Test process helper has a main only in test classes.

Call graph:

```text
GateABoundaryTest / GateAProcessProbe (separate disposable test JVM)
  -> GateABoundary.acquire(exact route, synthetic approval, closed response fixture)
     -> existing AttemptJournal.retire(UUID, request digest)
        -> CREATE_NEW marker -> bounded digest write -> FileChannel.force(true)
     -> simulated dispatch count -> envelope/UTF8 checks -> immutable bytes/hash/time
Existing Phase141/142 tests -> KiteOfflineInstrumentMaster -> existing CSV mapper
  -> DatedInstrumentMaster -> OfflineUniverseResolution -> HistoricalUniverseResolver
```

No arbitrary transport callback exists. The boundary uses no java.net, database,
trading-core, WebSocket, session, System/environment or logging APIs. It does not
inspect or load proxy/credential environment settings. Every non-null proxy proposal
is denied; no network stack exists that could consult a system proxy. Byte inspection,
packaging tests and process tests support this bounded claim, not host-wide egress.

Exact raw URL equality denies normalization ambiguities, explicit ports (even443),
encoded components, relative URLs, root instruments, NSE suffixes,NFO,historical and
order paths. Only uppercase GET is accepted. Approval binds owner, GateA, UUID and
five-minute maximum synthetic lifetime; retention denial and non-synthetic approvals
fail. No signed assertion is represented as independently verified approval.

Journal reuse: Phase13 fixture AttemptJournal already supplies CREATE_NEW/force and
partial-marker denial. Phase13 distributed witness requires test databases/coordinator
and different authority model; it is not silently imported as a production guarantee.
This phase uses an intact disposable local authority. Any existing marker denies,
even empty/partial. Reopening on another process uses the same journal identity.
Timeout, redirect, error and uncertain acknowledgement consume once without refund.
Crash before marker creation leaves no prior dispatch; after creation denies replay.
Two processes sharing authority have one winner. Authority IDs/stores must not be
reissued by an untrusted issuer: no production authority issuer is implemented.

Threat limits: privileged replacement/deletion/symlink races, restoration of the whole
authority, clock rollback after authority rollback, independent root survival and
physical power-loss/directory-entry durability are not solved by FileChannel.force.
The rollback test deliberately demonstrates another grant after complete state
restoration. No claim of universal durable enforcement or secure host containment.
Independent OS/egress containment NOT_VERIFIED; no live TLS/proxy/ACL attestation.

Response handling: fixture creation bounds bytes before clone at32MiB; acquisition
requires exact text/csv; charset=utf-8, valid UTF8, no BOM/NUL/empty data. Response,
observation and approval renderings are redacted; errors drop values and causes.
Raw bytes/hash/observation timestamp are immutable and labeled synthetic. Approximate
memory is bounded per response but includes multiple32MiB copies plus decoded text;
no unbounded response stream exists. Network decompression/timeouts remain future work.
CSV schema parsing is intentionally reused via existing importer tests, not duplicated
or imported into this isolated component. Envelope success is NOT instrument validity.
No spreadsheet evaluation/export is performed; formula-like input stays untrusted raw
bytes, and any future human-facing spreadsheet export must neutralize formula execution
without modifying canonical symbols. No genuine retention or deletion claim.

Existing mapper preserves ZERODHA; resolver's comparison projects KITE without changing
original tokens. Generic KiteRestTransport includes order methods and root /instruments;
it is not called. Dated provenance and prior resolution/quarantine reporting remain
unchanged. Missing-master ledger is reused:1115 unavailable,0 genuine resolved.
