# Phase 14.7 frozen plan

Baseline develop HEAD/local origin/develop:
83e42b4fd3e2ec79f82b6d897cae18da41000b9e. Initial index/worktree clean.
Exact8 Phase14.6 additions committed; Phase14.0-14.6 preservation and13 newline
reconciliations passed before edits. Preserve all existing bytes and historical tests.

Threat model: stale/missing/wrong-scope HALT, conflicting keys, clock reversal,
revocation races, spent/uncertain/corrupt journal, competing/restarted processes,
and leakage. Privileged/all-authority rollback, physical power loss, external logs
and production egress remain unresolved; no exactly-once delivery claim.

Allowed implementation is an additive test-scope HALT-enforced composition over the
Phase14.5 boundary and existing AttemptJournal/SyntheticHalt. Historical unguarded
fixture is retained only as prior evidence, never a selectable fallback from the new
entry point. No production startup/transport/runtime dependency changes. Exact paths:
- docs/operations/phase-14.7-plan.md
- docs/architecture/phase-14.7-gate-a-halt-boundary.md
- docs/operations/phase-14.7-runbook.md
- docs/operations/phase-14.7-validation.md
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/GateAHaltBoundary.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/GateAHaltBoundaryTest.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/GateAHaltProcessProbe.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/GateAHaltLedgerIT.java
- research/phase-14.7/generation-1/evidence-manifest.json

Contract: instrument-master-only operation, exact GET https://api.kite.trade/instruments/NSE,
request UUID, owner/process incarnation, pinned signing key, issued/expiry (<=30s),
monotonic HALT epoch, revocation/quarantine. Missing authorization/service/journal or
uncertainty denies. Synthetic signatures never establish real session/owner approval.

Ordering: identity -> owner scope -> HALT admit/process/epoch -> durable retirement
-> HALT recheck in final journal callback -> at most one closed-fixture dispatch ->
post-dispatch state/result check. Serialize state changes and dispatch admission on
one controller monitor. Test revocation at every modeled boundary; after-send revocation
can reject result but cannot undo send. Document closed-fixture delivery versus real
network limits, and any missing mandatory evidence honestly.

Mandatory tests: missing/invalid/expired/wrong-operation/endpoint/process/key/epoch,
revoked/unavailable/clock-rollback quarantine, missing/corrupt/uncertain ledger,
replay/restart,competing owners,kill before/after reservation and send,local/all-root
rollback,route/method/proxy/redirect/no-retry,GateB isolation and redacted diagnostics.
Use synthetic keys only. Separate JVM integration uses disposable filesystem ledger;
no database/container/live network. Full standalone unit tests plus focused new ledger
integration; retain prior tests. No full trading/Python rerun without shared production
changes. Record initial failures and corrections rather than erase evidence.

Inventory and test results live in manifest to avoid duplicate artifacts. Hash plan
before implementation. Seal canonical manifest, all baseline/new raw hashes, Git/LF
byte equivalence,secret/canary scans and two replays. Unexpected preservation failure
means BLOCKED; mandatory test gaps mean PARTIAL. Corrections to scope are additive.

No credentials,.env,broker calls,Zerodha contact,historical downloads,orders,HALT
resume,deployment,host changes,staging,commit,push,fetch,reset or clean. GateA/B both
PENDING/NOT_AUTHORIZED. Preserve1115 symbols,NO_GO,ZERO_CALL_ABORT,Phase12 BLOCK,
H1/H2 FROZEN,July SEALED,capital limits and governance/review pending states.
