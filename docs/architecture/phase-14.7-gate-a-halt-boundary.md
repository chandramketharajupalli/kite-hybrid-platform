# Phase 14.7 synthetic HALT boundary

The new test-scope GateAHaltBoundary composes SyntheticHalt, AttemptJournal and
the sealed Phase 14.5 GateABoundary. Existing files remain unchanged. This is an
offline security fixture, not a production acquisition client or launcher.

Call graph: acquire -> exact identity/owner checks -> SyntheticHalt.admit ->
in-memory attempt retirement -> recheck -> AttemptJournal.retire -> forced marker
-> final SyntheticHalt.recheck -> closed response fixture -> result rechecks.
The legacy fixture is not exposed by this entry point; it remains a historical
test class. There is no claim that all unrelated callers have been migrated.

The signed request fingerprint binds UUID, synthetic owner, process incarnation,
signing-key identity, operation SYNTHETIC_GATE_A_INSTRUMENT_MASTER, version
SYNTHETIC_GATE_A_V1, GET, exact https://api.kite.trade/instruments/NSE, validity
and HALT epoch. Existing length-framed fingerprint encoding includes the historical
SyntheticOwner proposal constant; the distinct version and operation separate this
contract. No SyntheticOwner observation, broker transport or trading startup runs.
Assertions expire within 30 seconds and use synthetic RSA keys only. Signature,
nonce, issuer, audience, process, epoch, time and revocation checks are reused.
Conflicting keys and observed clock rollback quarantine the verifier.

Control owns the verifier privately. Its monitor serializes state mutation and
closed-fixture dispatch admission. The final recheck after journal force is the
linearization point. A revocation ordered before that point denies sending;
revocation ordered after it cannot undo sending. Reentrant fixture hooks exercise
revocation after reservation and immediately before admission. External thread
revocation during result delivery rejects the result after one observed send.
This delivery window is not a measurement of real socket traffic or mid-byte
revocation. A real transport would require a separately reviewed integration.

| Revocation boundary | Synthetic sends | Completed fixture receipts | Result |
|---|---:|---:|---|
| Before authorization | 0 | 0 | denied |
| Before reservation | 0 | 0 | denied |
| After journal force | 0 | 0 | denied, attempt spent |
| Immediately before dispatch | 0 | 0 | denied, attempt spent |
| During result delivery | 1 | 1 | discarded, attempt spent |
| After peer observation | 1 | 1 | discarded, attempt spent |

CREATE_NEW journal markers and force(true) precede dispatch. Corrupt, missing or
uncertain journals deny. Restart and competing JVM tests share the journal; fixture
IDs are non-secret references, never bearer permissions. Each child generates fresh
synthetic trust, so restart tests establish journal behavior, not real custody.
A surviving Control's retired set additionally denies reuse after local marker
rollback. It is process memory, not an independent durable witness. Restoring all
authority, including fresh verifier state and a removed marker, demonstrably permits
reuse. Physical power-loss durability, directory persistence and multi-host failover
are NOT_ESTABLISHED. No universal rollback resistance or exactly-once delivery claim.

Exact string route matching denies alternate ports, hosts, schemes, queries,
fragments, userinfo, relative/normalized paths and methods. Non-null proxy input is
denied. The delegate has no HTTP implementation, retry or redirect dispatch path.
Failures return a fixed redacted exception without a cause. Assertions, requests
and controls have redacted diagnostics. Test hooks are trusted fixture instrumentation.

All four additions are under src/test/java. Runtime packaging excludes Gate A and
process probes. Existing packaging tests inspect the standalone runtime. No runtime
dependency or POM changes occurred. PRODUCTION_EGRESS_NOT_VERIFIED; external logging,
independent custody and production HALT authority remain unverified. Gate A and Gate B
are both NOT_AUTHORIZED; operational NO_GO and prior restrictions remain unchanged.
