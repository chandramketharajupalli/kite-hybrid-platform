# Phase 13.10 bounded plan v1

Frozen before implementation and new fault tests. Clean develop, HEAD and local
origin/develop 07d94f5055f74d26cb8f4ac5cc96e76b417bca34. No fetch/pull; remote
freshness unverified. Raw SHA-256 recorded separately; material changes require
an additive numbered amendment. Root AGENTS.md applies.

Inputs: committed standalone POM, three runtime classes and test fixtures; Phase
13.6-13.9 plans/reports/architecture/manifests; registered G1, 18 normalized source
hashes, 63 protected research hashes, 162 historical files and 25 K/L files. Before
inventory contains 837 tracked raw hashes. All registered checks passed. Phase13.9
canonical parent: 906a484a9ee5cc6f5dc341e304956f5387c7f55873ec48524ce6214dc7156229.
Canonical evidence: sorted compact ASCII-escaped JSON, allow_nan=False, UTF-8/no
newline; hash body independently of envelope. Raw and normalized hashes differ.

## Scope and environmental gate

Add only test-package RootRecoveryIT.java and RootRecoveryWorker.java; no runtime,
existing test, POM, reactor, trading, configuration or historical evidence edits.
Add this plan, docs/architecture/kite-synthetic-owner-sandbox-and-root-recovery.md,
docs/operations/phase-13.10-{runbook,validation}.md and research/phase-13.10/generation-1/
evidence-manifest.json. Ignored tmp/phase1310 retains inventories and raw test logs.

Available: Windows JDK21.0.12 (MZ executable), wrapper Maven3.9.11, Docker29.8.0;
only docker-desktop WSL, cached postgres17.6/redis7.4.5/Ryuk0.12.0/hello-world images.
No cached Linux JDK image or verified Linux JDK archive in inspected repository and
installed JDK locations; user .jdks directory absent. Windows binaries cannot build
a Linux JVM image. Docker reports builtin seccomp and cgroup namespaces; this does
not verify a Java owner's effective policy. No image download, package install,
host-policy change or unverified artifact construction. JAVA_OWNER_SANDBOX_NOT_EXECUTED
is the explicit gate result. Do not invent a skipped passing sandbox test or count
old shell probes as owner isolation. Continue independently useful recovery tests.

## Hypotheses, graph and negative requirements

Owner JVM -> signed/pinned witness claim -> local retirement journal -> local PG
RESERVED -> CONSUMED -> private ticket -> one numeric-loopback HTTP attempt ->
terminal observation. Witness coordinator retains key, retired IDs, high-water mark
and quarantine; separate witness PG transaction persists claim/sequence. Coordinator
memory is a conditional surviving trust root, not hardware or physically independent
durability. All-authority rollback counterexample must remain executable and visible.

Hypothesis: after an acknowledged witness claim, killing the owner before local
consumption, around local commit or after peer receipt cannot permit a replacement
send while that root survives. Measure local state, retired set and peer count.
Hypothesis: retained-root reconstruction preserves uncertain-ACK retirement and
quarantine; root loss/pinned key conflict denies. Do not invent recovery from an
untrusted old checkpoint. Existing fixed-clock/key conflict/epoch tests retained.

Normal failure is distinct from an administrator restoring every key/checkpoint/
store. At-most-once attempted send is not exactly-once delivery. Unknown outcomes
remain spent; no reset, refund, retry, TTL reuse or automatic quarantine clearing.

## Frozen execution matrix and budgets

1. Run existing FenceLedgerIT and IsolationLedgerIT first (no owner HTTP). Failure
   blocks new owner dispatch tests until corrected within scope; never weaken guards.
2. Add parent-controlled kill barriers AFTER_WITNESS_ACK, BEFORE_CONSUMPTION_COMMIT,
   AFTER_CONSUMPTION_COMMIT, AFTER_RECEIPT and BEFORE_OUTCOME. Child uses actual
   SyntheticOwner/FencedLedger-equivalent ordering, private stdin bootstrap, fake
   keys/credentials, explicit fixed clock and numeric loopback. Require replacement
   denied and peer count unchanged (0 pre-send; 1 after proven complete receipt).
   Lost control pipe must terminate, never advance. No credential data in argv/logs.
3. New retained-root tests: lost witness ACK then coordinator reconstruction; missing
   root; fresh wrong root versus retained PG; conflicting pinned key; quarantine
   cannot clear after availability toggle/reconstruction. Reuse existing negative
   rollback, unavailable/reservation, expiry/stale response, race and PG-restart tests.
4. Full standalone unit/security/packaging + disposable integration, root unit/
   architecture. No shared production/Python changes: root integration, Python tests,
   Ruff/mypy not required. Preserve failures and reruns; corroborate XML and native exits.
5. Fresh JAR inspection, dependency tree, jdeps and isolated --limit-modules java.base
   RSA probe. No runtime launcher/adapters/providers/trading imports. Inventory known
   dumpstreams and current bounded canary tests; external sinks/dumps remain unverified.
6. Project verifier, secret scan, tracked/untracked/staged/whitespace audits, all 837
   hashes, G1/18/63/KL and parent canonical evidence, final manifest replay twice.

Offline Maven; cached images only, numeric loopback/disposable PG. Up to two PG
containers per class, two competing JVMs, eight fixture threads, socket/query <=3s,
worker/recovery <=25s, owned worker kill <=5s; total <=60 minutes and <=2 corrective
reruns per failing gate. Kill/cleanup only owned test processes/container/UUID state.
No public egress probes. Existing shell network-none probes are historical regression
coverage only. Actual Java seccomp/AppArmor/user-namespace/filesystem/spawn/egress
proof remains NOT_TESTED here, as do container Java diagnostics and sandbox-local HTTP.

Stop on protected mismatch, unexpected modifications, actual external egress, real
credential requirement, unsafe infrastructure or inability to establish pre-dispatch
consumption. Missing Linux JVM blocks only sandbox work, not independent recovery.
Use PROVEN_IN_FIXTURE, NEGATIVE_DEMONSTRATED, NOT_TESTED, UNRESOLVED. Overall PARTIAL
unless every mandatory control is measured; passing recovery tests cannot certify OS.

Zero real Kite/token/session/WebSocket/order/trading DB/app startup/HALT resume/arm/
execute. No .env/config/migration/risk changes, commit/push or destructive Git. Preserve
INR10,000/cash/CNC, Phase12 BLOCK, H1/H2 FROZEN, July SEALED and ZERO_CALL_ABORT.
V2/governance second review/four domains PENDING; real access and operational
implementation NOT_AUTHORIZED; NO_GO/MIS NOT_READY. Stop after evidence and report.
