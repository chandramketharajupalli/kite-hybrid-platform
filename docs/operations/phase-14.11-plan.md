# Phase 14.11 frozen bounded plan

Baseline develop HEAD/local origin/develop:
8753bdbef2ab699d2b47d2acd6789e8c1ba6150f. Initially clean; 1104 tracked files,
no staged/untracked files. Phase 14.0-14.10 and supplements, 13 newline reconciliations,
G1/18/63/K/L and 1140/25/1115 universe passed before edits.

Existing GateAHaltBoundary/AttemptJournal are test-only. SyntheticHalt is standalone
runtime with a synthetic issuer; prior HALT/ledger tests do not establish production
authority. The offline parser/resolver is trading-core runtime without network input.
No screenshot file was found in supplied input or matching repository resources.
Do not infer image bytes, exact sent body, timezone, delivery or a reply.

Implement only a new standalone production-intended offline policy library, outside
the root reactor and trading runtime. No launcher, HTTP/socket client, credentials,
session restoration, database, order, historical or WebSocket dependencies. Authority
ports must verify full scope including owner approval reference; durable consume ACK
must precede final HALT revalidation. Closed byte-array fixtures only. Reuse existing
CSV parser through a validation port design; do not duplicate it in this library.
No production issuer/store/session adapters will be invented. Their absence means
PARTIAL rather than production certification. Existing files remain unchanged.

Threats: route/proxy substitution, wrong process/key/scope/epoch, stale or revoked
authorization, clock rollback, missing authority, uncertain consumption/outcome,
replay and leakage. Exact route, bounded bytes, checked sequencing and redacted
failures are local guarantees. Independent witness, power-loss, multi-host recovery,
real HTTP limits, OS egress/logging and legal clearance are explicit non-guarantees.

Permitted additions:
- docs/operations/phase-14.11-plan.md
- docs/architecture/phase-14.11-gate-a-production-boundary.md
- docs/operations/phase-14.11-compliance-submission-evidence.md
- docs/operations/phase-14.11-compliance-response-ledger.md
- docs/operations/phase-14.11-runbook.md
- docs/operations/phase-14.11-validation.md
- research/phase-14.11/generation-1/evidence-manifest.json
- research/phase-14.11/generation-2/compliance-submission.json
- apps/gate-a-offline-policy/pom.xml
- apps/gate-a-offline-policy/src/main/java/com/kitehybrid/gatea/GateAOfflinePolicy.java
- apps/gate-a-offline-policy/src/test/java/com/kitehybrid/gatea/GateAOfflinePolicyTest.java

The generation-2 record is necessary for the newly reported actual submission;
provenance USER_REPORTED_SENT_WITH_SCREENSHOT, resource missing, all source hashes
and unknown exact questions null. It never replaces prior NOT_SENT records.

Test gates: full new standalone policy suite; retained Gate A/HALT/security unit
suite and GateA/GateAHalt process-ledger ITs; existing offline parser/resolution tests.
Keep initial failures and corrected attempts in evidence. At most one Maven run at
a time, one test JVM per suite, existing 30-second bounded process probes; no DB,
container/image/VM/host changes. Reuse retained witness/OS evidence as historical,
not new production results. Additional gaps yield PARTIAL, safety failure BLOCKED.

Hash plan before implementation. Scope corrections additive. Preserve all old bytes,
run secrets/canaries, exact inventory/LF/Git-byte checks and replay manifest twice.
No real credentials, API calls, vendor messages, market downloads, orders, operational
HALT mutation, deployment, staging, commit, push, fetch, reset, clean or config edits.
Preserve NO_GO, ZERO_CALL_ABORT, Phase12 BLOCK, H1/H2 FROZEN, July SEALED, capital limits
and pending governance. Gate A/B NOT_AUTHORIZED. Stop after reporting.
