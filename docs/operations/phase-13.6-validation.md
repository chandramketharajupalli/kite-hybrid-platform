# Phase 13.6 validation

Result: **SYNTHETIC_OWNER_VALIDATED**, limited to the fixture model below. No live
credential-owner implementation or operational assurance has been certified.

Baseline and local origin/develop both remain
`fc473eab7b2c83a9543cda769972e4928a418495` on develop. Initial tree: 25 known untracked
K/L documents/evidence files, no tracked or staged changes. They were inventoried
with raw SHA-256 before work and remain byte-for-byte unchanged. No tracked file was
modified; the prototype and documentation are additive and untracked. No commit,
push, stage, reset, stash, clean, restore or pull occurred.

Frozen plan raw SHA-256:
`C945CA56A77EF199EFE02B19B86ECD92A1E5FCBA644E01610C50CC9B2A828392`.
The plan was written and hashed before prototype source edits; criteria unchanged.

## Actual execution and failure history

Toolchain: installed Java/JDK 21.0.12, Maven wrapper 3.9.11, Docker server 29.8.0.
New test dependencies: JUnit Jupiter 5.12.2, PostgreSQL JDBC 42.7.11, Testcontainers
1.21.4, disposable postgres:17.6. No JDK install or trading service launch.

| Run | Actual result |
|---|---|
| Initial ledger Maven invocation | No tests: PowerShell split unquoted `-Dit.test=LedgerIT`, producing unknown lifecycle `.test=LedgerIT`; argument quoted on rerun |
| First correctly invoked ledger run | 1 setup error, 0 failures, 0 skips: PostgreSQL rejected host alias Asia/Calcutta |
| UTC test-JVM rerun | 69 unit/package tests passed; LedgerIT 8 tests, 1 failure, 0 errors/skips: cached port invalid after container restart |
| Ledger gate recovery rerun | 69 unit/package tests and 8 LedgerIT tests passed, 0 failures/errors/skips; fresh Docker inspection showed port changed; old consumed row survived and replay denied |
| Final standalone `-Pintegration verify` | 69 unit/package tests plus 44 integration tests passed, 0 failures/errors/skips; native Maven exit 0 |
| Root `mvnw.cmd test` | 1,508 Java unit/architecture tests passed, 0 failures/errors/skips; BUILD SUCCESS |

Final standalone breakdown: PackagingTest 3, SyntheticSecurityTest 66, LedgerIT 8,
OwnerIT 36. Counts come from freshly generated XML, not historical reports. Runs are
not added together as unique tests. Final standalone elapsed Maven time 1m17s;
root regression 2m12s. The earlier PowerShell wrapper reported a native stderr
diagnostic even with Maven BUILD SUCCESS; final invocation explicitly captured
LASTEXITCODE=0. SLF4J's missing-provider warning belongs to Testcontainers' test
classpath; the runtime JAR has no SLF4J dependency.

The crash fixture correction did not relax durability settings or expected result.
It follows the current mapping of the same owned container after SIGKILL/restart.
Rollback and uncertain acknowledgment faults remain enabled. No simulated HTTP
integration ran until the durable ledger gate passed. Test fixture setup and
before-dispatch ledger probes are not broker requests.

Earlier path discovery encountered a nonexistent optional JDK directory and an
invalid Windows rg wildcard; these were read-only discovery failures, not test
results. Actual installed JDK and paths were subsequently resolved. Raw build logs
and original failed XML were retained locally in ignored tmp/phase136, not copied
into public evidence. Redacted failure summaries are retained in the manifest.

Python application/unit tests, Ruff and strict mypy: NOT_APPLICABLE (no Python source
or executable validator introduced). Ad hoc Python used only offline hash/XML/JSON
inspection. Existing broad trading integration: NOT_RUN; no shared production
transport, authentication, startup or persistence changed. All new disposable
integration was run. Root unit tests use existing synthetic/mocked fixtures, not
normal trading application startup.

## Acceptance evidence and limits

| Requirement | Exercised evidence | Limit |
|---|---|---|
| Synthetic custody, scope and identity | Generated-only private credential; wrong owner/account/caller/recipient/version/authority/time/method/route denied before ledger; no credential in wire/response, erased on close | Trusted fixture supplies principal/incarnation; no network caller authentication or broker provenance |
| Durable at-most-once consumption | fsync/full_page_writes/synchronous_commit ON; unique reservation; 8-worker and 2-process race; abrupt worker exit, new worker rejection, database SIGKILL/recovery; digest binding | One disposable primary with fsync assumptions, no replicated failover or host rollback certification |
| Uncertain outcomes | Rollback after reservation, lost consume acknowledgment, failure after acknowledgment, truncated/lost response, terminal write failure leave reservation/consumption spent | Pre-reservation connection failure gives no grant; production uncertain-issuance retirement is not implemented |
| Synthetic HALT | Generated Ed25519; forged/unsigned/missing/future/expired/scope/epoch/key/digest failures; nonce replay, rotation, revoked key, issuer loss; before-dispatch and result rechecks | Synthetic local authority; nonce admission map process-local, durable attempt fencing separate |
| Loopback one GET | Numeric literal and pinned port, one socket; 2xx, 401/403/429/5xx; 301/302/307/308 target receives zero requests | No real network/OS firewall certification |
| Bounded HTTP | Malformed/oversized JSON/header, missing/duplicate length, chunking/compression, wrong schema, timeout/drip/loss and connection refusal | Deliberately narrow fixture grammar, no production HTTP compatibility claim |
| Egress/configuration denial | Alias/alternate port/path/query/method rejection without network; proxy environment child-JVM tests and JVM-property checks | Arbitrary host code/agents/administrators outside Java boundary |
| Logs/serialization | 17 non-allowlisted sink categories deny startup; policy drift denies dispatch/result; native serialization rejected; captured stdout/stderr empty; result lacks secrets/account/balance | No independent inspection of external sinks, dump settings or reflective serializers |
| Minimal packaging | Runtime dependency tree has only root artifact, jdeps java.base; 12 runtime classes, no Main-Class or test helper; forbidden bytecode/class-resolution checks; root/trading POM unchanged | Library prototype only; no deployed OS/service isolation |
| No evidence promotion | Shape-only outcome; synthetic-marker requirement; unchanged historical contract/governance/funding records | No funds eligibility, haircut, cash-field mapping or trading permission |

The adapter and process tests implement the prototype ledger only. They do not
implement or verify a production durable approval ledger, replicated split-brain
fencing, independently issued capabilities or real-session custody. A failed
connection before a durable reservation is never dispatched or internally retried;
retirement of such uncertain requests by a future external issuer remains a separate
design prerequisite. No exactly-once delivery promise is made.

## Protection and final audit

Project verifier: PASS (`uv run python scripts/verify-project.py`). Secret scanner:
PASS, zero potential secret locations (initial final audit scanned 1,814 text files;
the post-manifest rerun is recorded in the final response). `git diff --check`,
tracked diff/name-status/stat and staged name-status are clean. Untracked files are
explicitly inventoried in the manifest; new text whitespace is inspected separately
because git diff does not include untracked files.

| Protected identity | Verification |
|---|---|
| G1 raw | `56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656` |
| V2 raw | `5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7` |
| K amendment raw | `24F3FBD86E6FB662EDB6890C5150AB03C3E9FCE23D7B7B5DA709F43B1F636EDB` |
| H1/H2 sources | 18/18 pass registered UTF-8 text/newline normalization; 15 raw matches; candidates.py, diagnostics.py, opening_features.py need their registered normalization; no rewrite |
| Original research | 63/63 raw hashes pass J generation-1 preservation baseline |
| Historical parent inputs | 132/132 raw hashes pass K generation-1 parent inventory |
| Pre-existing K/L | 25/25 raw hashes preserved; all 11 fingerprinted K JSON envelopes recomputed; candidate submission retained raw |
| Phase12 / July | Acquisition and strategy evaluation flags false; BLOCK; SEALED_NOT_EVALUATED |

Manifest format: sorted-key compact ASCII JSON body, UTF-8, no trailing newline for
body SHA-256; envelope has `fingerprint` and `manifest`. Each source artifact uses
raw-byte SHA-256. The manifest excludes itself from its raw-file inventory to avoid
a self-reference cycle. Two offline replays verify canonical fingerprint, all new
source hashes, prior K/L hashes, protected counts and fail-closed statuses and yield
the identical fingerprint. Replay proves artifact consistency only, not test
re-execution, human approval or live isolation.

## Exact additive inventory

All paths are new; no original file modified:

```text
apps/synthetic-credential-owner/pom.xml
apps/synthetic-credential-owner/src/main/java/com/kitehybrid/synthetic/LoopbackPeer.java
apps/synthetic-credential-owner/src/main/java/com/kitehybrid/synthetic/SyntheticHalt.java
apps/synthetic-credential-owner/src/main/java/com/kitehybrid/synthetic/SyntheticOwner.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/DisposableLedger.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/LedgerIT.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/LedgerWorker.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/OwnerIT.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/PackagingTest.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/SyntheticSecurityTest.java
docs/architecture/kite-synthetic-credential-owner.md
docs/operations/phase-13.6-plan.md
docs/operations/phase-13.6-runbook.md
docs/operations/phase-13.6-validation.md
research/phase-13.6/generation-1/evidence-manifest.json
```

Ignored generated material: Maven target/classes/JAR/reports, and tmp/phase136
before-work snapshot, audits and execution logs. None staged. Disposable containers
are owned by test fixtures and stopped after tests. No external messages sent.

## Retained states and next prerequisites

Phase K acceptance USER_DECLARED_ACCEPTANCE_RECORDED; authority user asserted,
independently unverified. Effective adoption/24-hour second review PENDING; no
timestamp promoted or second decision manufactured. 90-day cadence, conflict
disclosure and external review obligations intact. V2 and all four technical reviews
PENDING. The user's Phase13.6 authorization covers this synthetic prototype only;
operational credential-owner implementation/deployment and real reads remain
NOT_AUTHORIZED. Real session ownership and independent live HALT NOT_ESTABLISHED;
live logging NOT_VERIFIED; minimal live service NOT_DEPLOYED; operational NO_GO;
collateral-assisted MIS NOT_READY. Own-process DB noninterference is not live
certified; global DB nonmutation NOT_PROVEN. Stage B ZERO_CALL_ABORT preserved.

Real Kite requests/WebSockets/orders, real token load/restore/exchange, development
DB mutations and operational HALT resume/arm/execute: **0**. INR10,000 full
buffered-notional ceiling, cash reserve, CNC rules and all safety defaults unchanged.
Phase12 BLOCK, H1/H2 FROZEN, SBIN July TEST SEALED. No commit/push.

Next: independently review the synthetic evidence and unresolved deployment/issuance
limitations; resolve governance timing and fresh second review; obtain attributable
V2/domain decisions; separately scope any real-owner engineering, independent live
controls and eventual read authorization. No automatic service activation or GET.
