# Phase 13.9 bounded implementation plan v1

Frozen before executable edits. Baseline develop, HEAD and local origin/develop
66c7dfec1fb9a835b32545e3f48cf62312c349d4; clean tree; no fetch. Record raw SHA-256
separately. Criteria changes require a numbered additive amendment.

Inputs: unchanged 13.6-13.8 runtime/test sources, POM, plans, architecture, reports
and manifests; root AGENTS.md. Verified 13.8 canonical d8a03b826041a29921493597b17fbaa471b92e45b50cd9e7163bd43a8fcb7ca3,
13.7 canonical cb87e01cfccf84698f5290ef293b4d5893ea774b9e608f7d131365503e7b2c5d,
registered raw plans/amendment, 162 historical files, 63 research files, 25 K/L
files and 18 registered normalized sources. Preserve all historical bytes and
counterexamples, including unfenced dual-store rollback and java.base not sandbox.

## Scope and architectures

Add only test-package FenceWitness.java, FencedLedger.java, FenceWorker.java,
FenceLedgerIT.java, FenceOwnerIT.java and ContainmentIT.java. Do not modify runtime,
existing tests, POM/reactor, trading/config/migrations, historical evidence or policies.
Add this plan, architecture/kite-synthetic-owner-anti-rollback-containment.md,
operations/phase-13.9-{runbook,validation}.md and research/phase-13.9/generation-1/
evidence-manifest.json. Ignored tmp/phase139 retains raw logs and initial inventory.

Compare A: independent fixture witness with authenticated compare-and-advance and
signed receipts; B: externally witnessed append-only sequence/checkpoints. Select A
for bounded tests: witness coordinator/keys/checkpoint live outside owner JVMs and
both local restorable stores, with a separate disposable PostgreSQL container.
Coordinator lifetime is an explicit trusted fixture root, not durable hardware.
Loss of root/pinned identity denies; no automatic genesis/bootstrap recovery.
B needs a separately operated retained checkpoint/transparency authority unavailable
here. Neither resists rollback of every store plus every trusted checkpoint.

The witness owns generated RSA-3072 signing material. A generated fixture-only
authentication canary travels only over private bootstrap stdin and bound loopback
protocol, never arguments/logs. Pin witness identity/public key; bind signed receipts
to nonce, ID, request/HALT digests, incarnation, expiry, epoch and previous/next
sequence. Retire IDs at the trusted coordinator before persistence; durable DB commit
and signed acknowledgment must precede local consumption and possible dispatch.
Missing root, unknown outcome, unavailable storage, stale sequence/epoch, invalid
signature/scope/expiry or duplicate ID grants nothing. No retry/automatic renewal.
Client receipt verification is mandatory; synthetic authority is not broker authority.

## Threats and limits

Separately assess conditional at-most-once grant, local rollback detection, witness
rollback/loss, crash durability, single-host policy and privileged/multi-host resistance.
Normal caller/process failure is distinct from an administrator restoring all state,
replacing keys or injecting code. Signed counters without surviving external trust
cannot detect a complete rollback. Keep ANTI_ROLLBACK_UNRESOLVED for that case.
Fixture checkpoint memory survives local/DB faults only while its coordinator lives;
it is not independent physical storage. Witness-process destruction without retained
root must deny rather than claim restart continuity. Preserve negative all-root-reset
demonstration as UNRESOLVED/NO_GO and never run a second real/simulated HTTP request
as a consequence of that negative control.

## Validation sequence and budgets

1. Focused new witness/ledger integration (no owner HTTP), retaining old rollback
   counterexample. Test local journal/DB rollback individually/together, surviving
   witness refusal, missing/invalid receipt, stale owner/scope, expiry, epoch, sequence,
   authentication, uncertain reservation/ack and root loss. Model torn/partial writes
   and regression explicitly; do not call them physical failure. Fail gate => stop
   before owner dispatch integration until smallest correction and successful rerun.
2. Security/containment, two JVMs, witness DB crash/restart with fresh mapped port,
   competing claims, replay and split-brain-like pinned-root mismatch. Add owner
   wrapper that verifies fence before local consume; count actual fixture requests.
   Exercise before witness/local persistence, after witness ACK, existing transport
   faults and outcome failure. Unknown dispatch stays consumed.
3. Disposable OS fixture: cached postgres image only, overridden probe entrypoint,
   network=none, unprivileged UID, read-only root, explicit tmpfs scratch, cap-drop ALL,
   no-new-privileges, memory/CPU/PID bounds and no host mounts/secrets. Inspect policy
   before probes. Test denied root/host-path writes, scratch allow, process/PID limit,
   network routes and DNS/connection attempts only inside verified network-none
   namespace; use documentation IP, never broker routes. Loopback-only simulation
   may be tested where available. Windows has only docker-desktop WSL and no cached
   Linux JDK image: shell policy evidence cannot certify the Java owner's confinement.
   No pulls, installs, host firewall/global Docker/security changes. Required owner
   containment unproven => PARTIAL, not invented certification.
4. Full standalone unit/integration then root Java unit/architecture; offline wrapper,
   UTC; JAR/java.base/provider restricted JVM, dependency inventory; inspect new
   dumpstreams and canary sinks without deleting old diagnostics. No Python changes.
5. Project/secrets/diff/untracked/staged audit; G1/18/63, K/L and all previous tracked
   hashes; deterministic canonical manifest replay twice. Canonical sorted compact
   ASCII-escaped JSON, UTF-8/no newline, SHA-256 body envelope; raw hashes separate.

Budget <=90 minutes, <=2 disposable PG containers per new class, <=2 competing owner
JVMs, <=8 fixture threads, process/recovery <=25s, sockets <=3s, frames <=4096 bytes,
queries <=3s. Three corrective reruns maximum per failing gate. Kill/delete only
owned process/container/UUID fixture state; preserve failed reports before reruns.
Unexpected Git/protected change, real secret/network requirement, missing trusted
root or unsafe container policy => fail closed/stop affected execution and report.

Use PROVEN_IN_FIXTURE, NEGATIVE_DEMONSTRATED, NOT_TESTED, UNRESOLVED. Overall PARTIAL
if mandatory OS/rollback assurance remains unverified even if bounded tests pass.
No universal rollback, power-loss, privileged host or multi-host guarantee.

Zero real Kite/token/WebSocket/order/trading DB/startup/HALT resume/arm/execute.
No .env/config/risk/funding edits, stage/commit/push or destructive Git. INR10,000,
cash/CNC, Phase12 BLOCK, H1/H2 FROZEN, July SEALED, Stage B ZERO_CALL_ABORT preserved.
V2/four reviews/governance second review PENDING; operational implementation/read/
deployment/trading NOT_AUTHORIZED; NO_GO and MIS NOT_READY. Stop after reporting.
