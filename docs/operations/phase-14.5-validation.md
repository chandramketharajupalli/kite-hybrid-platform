# Phase 14.5 validation

Outcome: PHASE_14_5_PARTIAL. Offline boundary tests pass. Production containment and
rollback/power-loss durable authority remain unresolved; no acquisition path enabled.
Terms clearance, authenticated custody and owner authorization are separate pending
requirements, not consequences of synthetic success.

Baseline develop: HEAD/local origin/develop
2c3632ae0ccadbf70da26acbaf95986e9bb79c43. Clean initial index/worktree. All11
Phase14.4 additions committed and byte-identical. After freezing the expected plan,
status and preservation were rechecked before implementation.
Plan SHA256:18f6e41619bcb158f4ac72afb19b359cf46085b8ae6a408c13d30019dc26cbea.

Four new Java test-scope files implement the closed response boundary, unit tests,
test-JVM probe and filesystem-ledger integration. No production code, Maven settings,
runtime dependencies, broker mapper, CSV, existing test or evidence file changed.
The component is absent from the runtime JAR and is not a production launcher.
Reuse existing AttemptJournal and existing master/schema/resolver tests; no parser fork.

| Fresh suite | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| Full synthetic-module unit suite |151|0|0|0|
| Included final Gate A unit cases |54|0|0|0|
| Disposable filesystem/separate-JVM ledger integration |8|0|0|0|
| Trading-core focused master/quality/architecture |98|0|0|0|
| Python Phase14 |32|0|0|0|

Initial52-case boundary run passed; two additional diagnostics/formula cases brought
the final subset to54. These subsets are not additional full-suite totals. All Java
and Python test commands exited0. Ruff/mypy passed for43 Python files. The new verifier's
first mypy check failed on dynamic module attribute assignment; switching to the
existing __dict__ rebinding convention corrected that type issue. Retain the initial
failure and corrective rerun in test-results.json; no historical evidence changed.
Full trading-core/full Python and PostgreSQL NOT_RUN: no production/shared contract
or DB changes. Relevant integration uses disposable filesystem authority and separate
JVMs. All test child processes reaped and phase-specific journals cleaned by lifecycle.

Acceptance coverage: exact route/method and URL ambiguity denial; proxy rejection;
missing/expired/future/wrong-owner/non-synthetic/storage/GateB denial; response size,
UTF8/type checks; redirect/server/timeout/uncertain outcomes consume without refund;
partial or missing journal denies; simultaneous processes share one winning attempt;
crash after marker creation denies replay; crash before creation has no prior send.
Uncertain force acknowledgement denies dispatch and leaves the attempt consumed.
Redacted rendering, fixed safe exceptions and captured zero log output pass. Existing
CSV tests cover malformed schema,duplicates,ambiguity,missing/wrong exchange and provenance.

Limitations: local intact-journal process durability is demonstrated, not universal
durability. Whole-authority rollback counterexample passes by demonstrating another
grant after state restoration. Privileged filesystem replacement, independent witness,
physical power loss/directory durability, production TLS/proxy/egress, genuine owner
verification and live retention/deletion remain NOT_VERIFIED. No claim of a successful
real egress guard. Fixture approval has no genuine signature/custody authority.

Universe preserved:1140 original,25 duplicate,1115 unique enabled NSE, original order
and spelling. Fresh missing-master ledger equals pinned historical ledger. Genuine
RESOLVED0; MASTER_UNAVAILABLE1115; other statuses0. No master/candle/performance invented.
No real credentials read; no .env access, login, token restoration or vendor contact.
Real broker requests0. GateA/B PENDING/NOT_AUTHORIZED. Genuine storage NOT_AUTHORIZED.

Read-only verifier reuses Phase14.0-14.2 checks with baseline rebound only in memory,
including exact13 newline exceptions against committed blobs; verifies Phase14.3/14.4
canonical manifests, raw hashes and amendment. Protected18 source,63 research,K/L and
registered plans/governance remain intact. New evidence has raw hashes plus canonical
JSON manifest fingerprint, LF/Git clean-byte identity, exact inventory and two replays.
Original manifests are never rewritten. Test XML digests are recorded, not raw logs.

Operational NO_GO; HALT,StageB ZERO_CALL_ABORT,Phase12 BLOCK,H1/H2 FROZEN,July TEST SEALED,
INR10000 ceiling,V2/governance/technical reviews pending unchanged. No staging,commit,
push,deployment,host changes or live DB. The clarification draft remains DRAFT/NOT_SENT.
Stop here; future execution needs terms/custody clearance and independent control review.
