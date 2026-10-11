# Phase 13.7 validation

Result: **SYNTHETIC_FAILURE_RECOVERY_VALIDATED**, for the bounded synthetic model only.
No production readiness, real identity/session, live HALT, OS isolation or live sink
containment is certified. See the [recovery architecture](../architecture/kite-synthetic-owner-failure-recovery.md)
for the state machine, crash classifications and explicit assumptions.

## Baseline, frozen plan and amendment

Clean develop was verified before edits: HEAD and local origin/develop both
`34cc3e5b242547774a5225d30aa460699db2b2d9`. No tracked/untracked changes, staged changes
or diff errors existed. Local remote-tracking ref only: no fetch/remote refresh.
History inspected: 34cc3e5, 6c2a31c, fc473ea. No reset/stash/clean/checkout/restore,
staging, commit or push occurred.

Original frozen plan raw SHA-256:
`D79F7251482A1A8529C47CFD9B877DA607AEFDDC163BE3C82179E541FE274E81`.
Additive plan amendment 1 raw SHA-256:
`66AD85AAEE27B0205C5F941C2E5D49684F4B7924CAE25DDB6E65392FDE52C30F`.
Both remain unchanged. Original criteria were strengthened, never relaxed.

A deeper packaging check after initial passing suites found that inherited Ed25519
loads jdk.crypto.ec dynamically. Local JDK provider inspection proved this; static
jdeps had missed it. Initial static-only packaging evidence was therefore insufficient
for the requested java.base-only boundary. Amendment 1 changes only generated
synthetic assertions to RSA-3072/SHA256withRSA, versions their payload domain and
adds actual-provider and restricted-JVM verification. Wrong algorithms/short keys
deny. Historical Phase13.6 evidence and governing contracts are untouched.

## Actual tests and execution order

Installed JDK 21.0.12, Maven wrapper 3.9.11; cached PostgreSQL 17.6 and Ryuk 0.12.0
images inspected before test execution. Every Maven command used `-o`; PostgreSQL
fixture pull policy is disabled. UTC is explicit in test JVMs and subprocesses.
No dependency download, real broker probe or trading application launch was needed.

| Stage | Actual passing counts |
|---|---|
| Initial focused unit/security/packaging | 75 |
| Initial ledger gate | 18 integration, plus repeated unit phase |
| Initial HTTP gate | 51 integration, plus repeated unit phase |
| Initial full standalone after ticket-race test | 76 unit + 69 integration |
| Initial existing Java unit/architecture | 1,508 |
| Provider-aware focused rerun | 78 |
| Provider-aware ledger gate | 78 unit + 18 integration |
| Provider-aware HTTP gate | 78 unit + 51 integration |
| Final full standalone | 78 unit + 69 integration |
| Final existing Java unit/architecture | 1,508 |

All listed completed suites have zero failures/errors/skips and native Maven exit 0.
No failed test assertion was concealed or changed to pass. The provider finding is
retained as a validation deficiency, not mislabeled as a JUnit failure. All required
gates were rerun after correction in the prescribed order. Raw logs remain ignored
under tmp/phase137; no logs/class/JAR/target output are added to Git.

Final standalone classes: PackagingTest 3, RecoverySecurityTest 9,
SyntheticSecurityTest 66; LedgerIT 8, RecoveryLedgerIT 10, OwnerIT 36,
RecoveryOwnerIT 15. Do not sum repeated invocations as distinct tests. Earlier
`-DskipUnitTests=true` arguments did not skip units in this standalone POM; the logs
show actual repeated unit execution. SLF4J's test-classpath missing-provider warning
was not a test failure; LASTEXITCODE was captured explicitly.

Commands are in the [runbook](phase-13.7-runbook.md) and machine-readable manifest.
Ledger tests passed before simulated HTTP integration. Full standalone passed before
each existing root Java run. Existing broad trading integration was NOT_RUN because
no shared trading/authentication/transport/persistence code changed. Python tests,
Ruff and mypy NOT_APPLICABLE: no Python source or executable validator introduced.
Offline ad hoc Python inspected hashes, JSON, JAR and XML only.

## What passed

- Forced immutable retirement marker before any DB action; partial marker, missing/
  conflicting identity, connection failure and reservation rollback remain spent.
- PostgreSQL durable reservation/consumption with settings ON; uncertain reservation
  and consume acknowledgments grant no send; distinct digests/HALT evidence sharing
  an ID deny; illegal/repeated terminal writes reject instead of silently succeeding.
- Eight-thread/two-process claim races, abrupt worker exit and PostgreSQL SIGKILL/
  restart with fresh port inspection. Persisted CONSUMED row survives; no replay.
- Eight owner fault windows plus fixture-confirmed receipt/no response; partial send,
  connection reset/refusal, truncation, malformed/oversized/non-2xx/slow responses.
  Two owner JVMs with different incarnations produce one send and one observed
  request; a third incarnation cannot revive the ID. Old-scope approval denies.
- Private, scope-bound one-use ticket; transport count before first possible byte;
  no ticket/no send; concurrent ticket claims admit once. Delivery classification
  does not imply business success or permit retry.
- HALT signature/nonce/digest/scope/time/revocation checks, monotonic epochs, clock
  reversal denial, key-ID conflict denial, pre-socket/pre-send/result rechecks.
- Explicit Proxy.NO_PROXY plus exact numeric loopback route; injected global proxy
  receives zero requests. Existing URI/property/environment/redirect guards pass.
- Generated-only credentials; bounded result fields and redacted rendering; native
  serialization denial; captured success/failure console output empty; signature and
  account-like response content excluded from results. No secret process arguments.
- Runtime tree has no dependencies beyond the artifact; jdeps java.base; JAR has 15
  runtime classes, no Main-Class, test journal/worker/adapter, JDBC, Testcontainers,
  Spring/trading classes, real broker hostname or old Ed25519 profile. Actual RSA
  provider is java.base; verification passes with --limit-modules java.base.

These are synthetic controls. Journal force/atomic-create and primary PostgreSQL
proofs assume honest local storage shared by all fixture workers. Power-loss directory
metadata durability, replicated failover/filesystem, host rollback, privileged store
replacement, independent issuer authority and external sink/OS controls remain
unverified. Whole-fixture teardown is not a recovery mechanism or permission to reuse
an operational approval. No real owner service or independent reconciliation workflow
was implemented. Static scans and tests do not certify a live deployment.

## Protection, project and Git audit

G1 exact raw hash:
`56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`.
All 18 registered UTF-8 text/newline-normalized source hashes pass; 15 also match raw
bytes. The three original CRLF cases (candidates.py, diagnostics.py,
opening_features.py) were not rewritten. All 63 original research raw hashes pass.
All 132 historical parent raw inputs and 25 K/L files match the retained Phase13.6
manifest; all 11 fingerprinted K envelopes replay. Phase12's five-member gate has
both acquisition/evaluation false; July state remains SEALED_NOT_EVALUATED.

Phase13.6 manifest exact raw SHA-256 remains
`5AD3BEBC4AD22BC01262E0D1BA932A1EE9A706AB2A80217646909E732F7F7AFB`;
its canonical fingerprint remains
`67B266BB7A69491FB085EF80B54B2334D5C9849730F659585AC0B49D6C100368`.
Historical docs and ledgers are unchanged. Authorized prototype source changes have
before/after hashes in the new manifest; old source hashes are not falsely claimed
to describe current hardening.

V2 raw SHA-256 remains
`5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7`;
amendment raw SHA-256 remains
`24F3FBD86E6FB662EDB6890C5150AB03C3E9FCE23D7B7B5DA709F43B1F636EDB`.
Original/checkpoint timestamps remain unverified; no clock observation is promoted
to governance adoption or a second review.

Project verification PASS. Final artifact secret scan PASS: 1,827 text files,
zero potential secret locations. Diff/whitespace and staged
audit PASS; no staged changes. New/untracked text is inspected separately. No
unexpected tracked/untracked change, generated build output or secret is included.
Canonical evidence replay twice verifies current source, preserved inputs and
fail-closed statuses with identical fingerprints. This is artifact consistency,
not fresh test execution, human approval or live certification.

## Exact changed/new inventory and reason

Modified:

```text
apps/synthetic-credential-owner/src/main/java/com/kitehybrid/synthetic/LoopbackPeer.java
apps/synthetic-credential-owner/src/main/java/com/kitehybrid/synthetic/SyntheticHalt.java
apps/synthetic-credential-owner/src/main/java/com/kitehybrid/synthetic/SyntheticOwner.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/DisposableLedger.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/LedgerIT.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/OwnerIT.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/SyntheticSecurityTest.java
```

Runtime files add tickets, delivery classification, explicit no-proxy transport,
exact assertion binding and HALT/provider hardening. Adapter adds durable retirement,
binding and transition checks. Existing tests adapt to stricter contracts, use the
new synthetic signing profile and tear down only their owned journal.

Added:

```text
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/AttemptJournal.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/OwnerWorker.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/RecoveryLedgerIT.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/RecoveryOwnerIT.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/RecoverySecurityTest.java
docs/architecture/kite-synthetic-owner-failure-recovery.md
docs/operations/phase-13.7-plan.md
docs/operations/phase-13.7-plan-amendment-1.md
docs/operations/phase-13.7-runbook.md
docs/operations/phase-13.7-validation.md
research/phase-13.7/generation-1/evidence-manifest.json
```

New test helpers/probes prove retirement, process/send behavior and security/package
boundaries. Docs freeze criteria, preserve the provider finding and describe reproducible
proof/limits. Manifest is redacted, additive and fingerprinted. POM/root reactor,
trading code/config/migrations/policies and historical evidence were not modified.

## Independent statuses and stop

Operational NO_GO; V2 approval PENDING; governance second review PENDING; all four
technical reviews PENDING; real Kite access NOT_AUTHORIZED; collateral-assisted MIS
NOT_READY. Real session provenance/live HALT NOT_ESTABLISHED, live logging NOT_VERIFIED,
global trading-DB invariance NOT_PROVEN. Phase12 BLOCK; H1/H2 FROZEN; SBIN July SEALED;
StageB ZERO_CALL_ABORT preserved. INR10,000 full buffered-notional ceiling, cash
reserve, CNC and operational safety defaults unchanged.

Real Kite calls/WebSockets/orders, real token loads/restoration, development/production
DB mutations, trading application startup and operational HALT resume/arm/execute: 0.
No commit/push. Stop after reporting; no activation, governance signature, contract
approval, live read or trading follows from these results.
