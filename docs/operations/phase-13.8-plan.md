# Phase 13.8 isolation and recovery assurance plan v1

Frozen before new fault tests or source edits. Clean develop; HEAD and local
origin/develop: 2c54d9bd581139e26fff1775fcaeba7259b4591e. No fetch. Raw SHA-256
of this UTF-8 artifact is recorded separately in validation and the manifest.
Criteria changes require a separately fingerprinted additive amendment.

## Inputs and protection

Root AGENTS.md applies. Inspect all standalone runtime/test sources and POM,
Phase 13.6/13.7 architecture, plans, runbooks, validation and manifests. Registered
13.7 plan and amendment raw hashes and canonical manifest fingerprint match the
execution specification. Initial verification: 162 protected raw inputs, 63 research
raw hashes, 18 registered UTF-8 newline-normalized sources, 17 current source/docs
hashes, G1, V2 and K amendment identities match. Retain an initial raw inventory of
all tracked files, plus canonical K envelopes; do not read .env or credential files.
Historical manifests remain immutable; old build-report hashes are historical.

## Scope and dependency boundary

May add test-only IsolationSecurityTest.java, IsolationLedgerIT.java,
IsolationOwnerIT.java and IsolationWorker.java under the existing synthetic package.
May add bounded observer hooks to test-only AttemptJournal.java and
DisposableLedger.java for parent-controlled process termination. Existing helpers
may be reused. No new dependency, root-reactor/POM change or operational entrypoint.
The three existing runtime classes may change only after a concrete new test
demonstrates a bounded defect; retain the failing evidence and smallest correction.
JDK21/java.base including actual RSA-3072/SHA256withRSA providers remains required.

New documents: this plan, docs/architecture/kite-synthetic-owner-isolation-assurance.md,
docs/operations/phase-13.8-runbook.md, docs/operations/phase-13.8-validation.md,
research/phase-13.8/generation-1/evidence-manifest.json. Ignored tmp/phase138 is
reserved for raw run logs, snapshots and failure reports; no generated output staged.

## Threats, states and evidence taxonomy

UNSEEN -> JOURNAL_RETIRED -> DB_RESERVED -> DB_CONSUMED -> OUTCOME_RECORDED.
Every retirement remains spent across honest same-store restart. Only acknowledged
consumption grants a private transport ticket. NOT_DISPATCHED, MAY_HAVE_DISPATCHED
and OBSERVED_COMPLETED describe observations, never authorization renewal.
No exactly-once delivery claim. Independent processes share the fixture journal
and one disposable PostgreSQL primary, not a replicated quorum.

Threats include kills around journal creation/write/force, DB reservation/commit,
ticket issuance, send and receipt; uncertain acknowledgments; concurrent claims;
disk errors, truncation, stale/rolled-back stores; stale/replayed/revoked HALT;
proxy/DNS/redirect/IPv4/IPv6 bypass; error/log/serialization leaks. Distinguish normal
cooperating callers from a privileged actor able to replace every persistence store,
inject JVM code or inspect memory. Whole-store rollback has no external monotonic
anchor: require an explicit counterexample, not an invented protection claim.

Findings use PROVEN_IN_DISPOSABLE_TEST, SYNTHETICALLY_MODELED,
DESIGNED_NOT_IMPLEMENTED, UNVERIFIED or NOT_APPLICABLE. Logical row/file restoration
is modeled rollback, not physical media rollback or power loss. Parent-killed JVMs
are actual process tests; hook positions do not model torn hardware writes.

## Frozen test gates and acceptance criteria

1. Unit/security/packaging: inspect dependencies; exact numeric route rejects
   IPv6/mapped/alternative numeric/DNS/scheme variants without opening a socket.
   Check fatal HALT evidence cannot be revived by availability changes; preserve
   RSA and restricted-java.base provider test. Capture nested error canaries and
   allowlisted outcomes. A disposable base-only subprocess may read only its own
   canary and spawn a bounded child to prove module restriction is not OS confinement.
2. Ledger before HTTP: existing ledger suites plus parent-terminated workers at
   before journal, after create, after write, after force, before/after reservation
   and consumption commits. Observe a bounded stage signal, kill, await death,
   restart and verify no retired identifier grants again. Before any retirement,
   a never-issued identifier may still be claimed; no send occurred in this fixture.
   Truncated/changed existing markers remain spent; failed create denies. Restore
   DB-only or journal-only state and require the surviving store to deny. Explicitly
   demonstrate whole-store logical rollback can defeat replay protection; classify
   it as an open privileged-rollback gap, never a passing rollback-resistance claim.
3. HTTP: existing fault/race/restart suites plus actual parent kill after fixture
   receipt before response, durable claim before owner acknowledgment, and after
   response before outcome persistence where feasible without runtime hooks.
   Replacement owners cannot dispatch again. Preserve observed receipt versus
   uncertain delivery. Test malformed/oversized/redirect/timeout/revocation and
   canary containment using existing cases rather than redundant broad suites.
4. Full standalone integration then root Java unit/architecture; no shared trading
   changes, so broad trading integration unnecessary. No Python code/validator.
5. Inspect final JAR entries, runtime dependency tree, jdeps, actual restricted-JVM
   execution/providers; test adapters/launcher/services/trading classes absent.
   Assess OS identity, ACLs, env, spawn/listener/egress rights, read-only root and
   capabilities explicitly. Host isolation is not established by Java tests. Do not
   change host policy or pull an image to manufacture OS isolation evidence.
6. Project verifier, secret scan, diff/staged/untracked audit, protection checks,
   canonical manifest replay twice. Preserve failed runs before corrections.

Pass means all executed bounded assertions/gates pass and limitations are honestly
classified. Missing required executable coverage yields PARTIAL, infrastructure or
integrity blocker yields BLOCKED. Whole-snapshot immunity, multi-host failover,
physical power-loss durability, privileged process isolation and live logging are
not claimed even when tests pass. Cannot prove honest-store pre-dispatch retirement
means stop before HTTP. Never weaken assertions/timeouts to obtain green results.

Budgets: offline Maven/cached images only; numeric loopback, one disposable PG per
integration class; at most eight threads/two competing JVMs; process waits <=25s,
socket <=3s, wire <=4096 bytes, query <=3s, recovery <=25s. Overall planned work
<=90 minutes, at most three corrective gate reruns before reporting unresolved work.
Kill only process objects/containers created by these fixtures. Fixture restoration
or file removal is limited to UUID-owned disposable state and explicitly labeled
adversarial tests, never operational recovery or modifications to host storage.

## Evidence and prohibitions

Manifest envelope contains fingerprint plus manifest body. Body canonicalization:
sorted keys, compact comma/colon JSON, ensure_ascii=True, allow_nan=False, UTF-8,
no trailing newline. Raw artifact hashes remain separate; exclude raw self-reference.
Record exact commands, native exits, XML counts, failure history, taxonomy and hashes.

No real Kite/token/session/credential/account access, external egress probe, trading
application startup, development/production DB access, HALT resume/arm/execute,
order/live/paper action, config/.env/migration/policy edit or historical evidence edit.
No stage/commit/push/reset/clean/stash/restore/pull. Unexpected change/protected
mismatch/external network requirement => stop. Preserve INR10,000/cash/CNC, Phase12
BLOCK, H1/H2 FROZEN, July SEALED and Stage B ZERO_CALL_ABORT. V2/four reviews/governance
second review PENDING; implementation/live access NOT_AUTHORIZED; NO_GO/MIS NOT_READY.
Stop after reporting. Tests are neither human decisions nor operational certification.
