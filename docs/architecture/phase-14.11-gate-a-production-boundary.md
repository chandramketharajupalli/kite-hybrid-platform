# Phase 14.11 production-intended offline policy

Status PARTIAL. The new apps/gate-a-offline-policy module is a production-intended
sequencing library in src/main, outside the root reactor. It is not an acquisition
service: no main method, HTTP/socket dependency, network callback, production authority
adapter or credential interface implementation. It accepts only closed in-memory
FixtureResponse bytes. Existing source and evidence remain byte-for-byte unchanged.

| Existing component | Classification / reuse decision |
|---|---|
| GateAHaltBoundary, GateABoundary, AttemptJournal | Test-only; retain and rerun strongest applicable local process tests |
| SyntheticHalt / SyntheticOwner / LoopbackPeer | Standalone runtime but explicitly synthetic; fixed synthetic issuer and loopback assumptions, not production identity |
| Witness/root/PG fixtures | Test-only; retained Phase 13.7-13.11 recovery evidence, not independent administration |
| KiteRestTransport | Trading-core runtime with POST/PUT/DELETE order paths and historical paths; forbidden dependency for this module |
| KiteOfflineInstrumentMaster / KiteInstrumentCsvMapper | Trading-core offline parser runtime; caller-supplied bytes, no network; do not duplicate |
| DatedInstrumentMaster / OfflineUniverseResolution / HistoricalUniverseResolver | Runtime offline provenance/matching; original token namespace preserved |
| OfflineHistoricalPilot and Phase 14.3 preview | Offline planning; no acquisition approval |
| Phase 14.8 isolation assessment | Evidence/design; not enforcement |

New call graph:

```text
observeFixture -> exact method/URL/no-proxy
 -> full authorization shape/process/key/owner-reference/time
 -> HaltAuthority.permits
 -> AttemptAuthority.available -> atomic consume ACK
 -> final full HALT recheck -> one synthetic observation
 -> strict UTF-8 + MasterValidator -> HALT recheck
 -> SHA-256 synthetic receipt -> recordOutcome
```

Runtime dependencies: java.base only; external dependencies none. JUnit test scope
only. No dependency on trading-core or synthetic-owner. MasterValidator is a port,
not a replacement parser. A future reviewed adapter would call the existing offline
importer/resolver after authorized receipt, outside the network process. That bridge
is not implemented here; tests of it are not implied by separately passing suites.

Authorization includes operation, exact endpoint/method, process incarnation, attempt
UUID, pinned key identity, epoch, issued/expiry and explicit owner approval reference.
No supplied value is genuine approval. HaltAuthority must authenticate the full object
including owner reference and signature, compare current epoch and revocation/quarantine.
Missing ports deny construction; false/throwing authorities deny observation. No
default-allow authority is shipped. Signature verification is a REQUIRED ADAPTER
CONTRACT, not newly implemented crypto. New tests inject authority rejection; real
signature-negative coverage remains in the separate synthetic verifier tests.

The policy checks time on admission and after consumption; clock reversal permanently
quarantines that instance. Local retired UUIDs reject replay. A shared AttemptAuthority
must atomically consume before ACK across processes. UNCERTAIN/DENIED/errors never
grant observation. Outcome recording cannot refund or reopen an attempt. An in-memory
test store is not durable production storage. Prior journal tests verify retained
markers after abrupt child exit, but do not wire that journal into the new library.

Production HALT service, owner-reference verification, trusted signing-key provisioning,
monotonic epochs across restart and durable authority adapters remain absent. Local
monitor ordering covers fixture calls; external revocation between a final check and
a future socket send requires a separately reviewed linearization contract. No claim
of real-network revocation, exactly-once delivery or rollback immunity is made.
ROLLBACK_RESISTANT_AUTHORITY_NOT_ESTABLISHED. Retained all-authority rollback grants
twice; local surviving control/witness helps only while that authority survives.

Response allocation is bounded per fixture array at 32MiB, then defensively copied;
UTF-8 is strict and BOM/NUL/empty input rejected. Hash/time receipt is synthetic.
This is not a streaming wire/decompression deadline or a whole-process RSS bound.
CSV schema/equity/ambiguity validation belongs to the parser port. CSV-injection-safe
export and secure physical erase remain unverified end-to-end; no data export/store
was added. JVM memory clearing/GC is not secure deletion. Diagnostics are fixed and
redacted, with no input values or exception causes exposed. External sinks are unverified.

Future owner-held session injection is assessed, not implemented: an opaque scoped
capability would need an independently approved custodian, no serialization/CLI/env/
logging/token restore/login path. No raw credential type is accepted by this API.
AUTHENTICATED_SESSION_NOT_ESTABLISHED. No genuine persistence until terms permit it.

OS controls: retain Phase 13.11 measured UID/GID65534, CapEff0, NNP1, Seccomp2,
read-only root/private scratch, process denial, memory256MiB/PIDs64/CPU quota and
network-none denial as historical fixture evidence only. Phase 13.12's teardown and
resource-budget failure stays BLOCKED historical evidence. AppArmor/SELinux and
remapped user namespaces were not established; DNS/proxy/domain allowlisting and
external logs are not proven by network-none. No current host/container probe or
configuration change. PRODUCTION_EGRESS_NOT_VERIFIED. All Gate A/B permissions closed.
