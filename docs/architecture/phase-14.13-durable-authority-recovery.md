# Phase 14.13 durable authority recovery

Bounded offline model; production authority is not established. Existing code and
historical evidence are unchanged. Two new runtime classes compose existing ports;
three test-only classes supply fixture initialization, child processes and failures.

## Audit and call graph

SignedHaltAuthority stores highest epoch, last clock/state time and sticky revocation/
quarantine in JVM memory. FileAttemptAuthority durably retires an attempt but has no
trusted external checkpoint. SessionCustody remains metadata-only. GateAHaltBoundary
and Phase13.9 FenceWitness/Phase13.10 probes are test-only. Their retained Root survives
selected fixture rollbacks; restoring every Root remains a demonstrated counterexample.
No adapter has independently provisioned live HALT, owner or credential authority.

```text
GateAOfflinePolicy (unchanged, closed response bytes only)
 -> RecoverableGateAAuthority.permits
    -> matching local/witness checkpoint history
    -> exact authorization binding + durable highest epoch/time
    -> existing SignedHaltAuthority signature/current-state checks
    -> force checkpoint advance before permission acknowledgment
 -> available -> consume
    -> compare-and-advance CONSUMED in witness then local
    -> existing FileAttemptAuthority forced retirement
 -> immediate permission recheck, only successful consuming wrapper incarnation
 -> existing parser/resolver -> final recheck -> outcome, no reopening
```

The journal records version, complete operation/method/endpoint/process/key-ID/attempt/
approval/time authorization binding (base64 canonical bytes), current/highest epoch,
sequence, state, observation time, consumed flag, predecessor digest, scope digest and
SYNTHETIC_FIXTURE_ONLY provenance. Hashes detect accidental corruption; they are not
MACs, signatures or administrator-proof authenticity. No record contains a private key
or genuine session credential. Diagnostics and toString omit records/identifiers.

## State machine

| State | Entry evidence | Allowed next state |
|---|---|---|
| UNINITIALIZED | Both directories absent; no permission | Explicit new fixture provisioning only -> ACTIVE_SYNTHETIC |
| ACTIVE_SYNTHETIC | Matched intact history and signed authorization checks | ACTIVE_SYNTHETIC checkpoint advance, CONSUMED, REVOKED, QUARANTINED |
| CONSUMED | Forced witness/local consumption checkpoint | CONSUMED recheck by winning in-memory wrapper only; REVOKED or QUARANTINED |
| REVOKED | Denial-only fixture event | REVOKED or QUARANTINED; never active |
| QUARANTINED | Invalid signature/scope, stale epoch, clock reversal or prior quarantine | QUARANTINED only |
| RECOVERY_PENDING | Missing, corrupt, partial or mismatched trusted histories | No automatic transition, repair, truncation or grant |

RECOVERY_PENDING is a fail-closed read result, not a repair command. Restoring both
histories by an administrator can defeat it; this is outside the trusted surviving
checkpoint model. Consumption flag is monotonic even when state becomes revoked.
New wrapper incarnations never obtain permission for CONSUMED. Availability recovery
does not clear persisted REVOKED/QUARANTINED. Negative signed evidence is conservatively
persisted as QUARANTINED; the denial-only revokeFixture control records REVOKED explicitly.
Neither is operational HALT authority. A newer epoch fences the bound old permit; this
phase deliberately provides no automatic rebinding, key rotation or new grant workflow.

## Atomicity, recovery and limits

Witness file lock serializes bounded compare-and-advance across cooperating processes.
Expected sequence must match; stale CAS loses. Immutable sequential files use CREATE_NEW
and force(true). The witness is written first, then local; only both completed writes
acknowledge. Crash/lost ACK between copies leaves RECOVERY_PENDING. Lost ACK after both
copies retains CONSUMED. No retry/refund. Chain scanning is capped at256 records,8192
bytes per record; exhaustion denies. No network, DB, background service or launcher.

Local rollback while witness survives and witness rollback while local survives both
deny on mismatch. Full history/identity/attempt-marker restoration permits a second
synthetic observation, explicitly tested. Two directories are under one administrator;
no claim of physical or administrative independence. There is no external minimum
checkpoint or immutable hardware root. A hostile administrator can rewrite hash-valid
histories, replace locks, directories or pinned trust. Path checks do not solve those
attacks. INDEPENDENT_ROLLBACK_AUTHORITY_NOT_ESTABLISHED.

The external signing-key material/owner-reference provisioning remains unverified.
The durable journal binds key identity; it cannot independently authenticate a supplied
public-key replacement. Existing signed verifier checks signatures against its pinned
key. Short-lived signed current-state replay windows and provenance limits remain as
documented in Phase14.12; no genuine issuer or revocation-delivery guarantee is added.

File flush/process termination are measured within fixtures. OS reboot, container reboot,
directory-entry persistence under sudden power loss, controller/cache behavior and
replicated failover are not measured. PHYSICAL_POWER_LOSS_NOT_VERIFIED. No destructive
host experiments. At-most-once fixture observation under surviving state is not universal
exactly-once network delivery; no real network dispatch exists.

Full standalone suites cover integration with existing signed authority, forced attempt
marker, policy and parser. Prior sources are compiled unchanged via the Phase14.12
allowlist. Artifact/dependency inspection excludes network/SQL/Spring/trading transports,
test probes and Main-Class. The host/custody decision remains separate from these tests.
