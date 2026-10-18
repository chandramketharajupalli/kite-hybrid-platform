# Phase 14.46A frozen authorization assessment plan

Baseline: 2acf229dfb1a53140b6dfcb33f83b992f2bce4df; develop equals local origin/develop.
Preparation permission: APPROVE_ASSESSMENT_SCOPE_ONLY, EXPLICIT_OWNER_CHAT_INSTRUCTION.
Owner authentication NOT_ESTABLISHED. Java implementation NOT_AUTHORIZED.

Phase 14.46 plan SHA-256: 020f4944cc35b726e41a8248b1ed224557f9c7fd373152b0a4c4f05899505a58.
Owner-review document SHA-256: 608d2fad02f55b7881f55b4fe9ecc5862479a5b41549fa80bb00e60f1cbfe7be.
Its canonical decision payload SHA-256: a789713a0bb32a8ab3ca51f88662dad6d7264cf86f5af9c5feaed41ec56ff056.
Decision APPROVE_SCOPE_IN_PRINCIPLE remains OWNER_DECISION_PROPOSED_NOT_AUTHENTICATED and NOT_AUTHORIZED_PENDING_INDEPENDENT_VERIFICATION.

## Exact assessment inventory

- docs/operations/phase-14.46a-plan.md
- docs/operations/phase-14.46a-authentication-assessment.md
- docs/operations/phase-14.46a-owner-authority.md
- docs/operations/phase-14.46a-scope-binding.md
- docs/operations/phase-14.46a-time-replay.md
- docs/operations/phase-14.46a-validation.md
- research/phase-14.46a/generation-1/authorization-readiness.json
- research/phase-14.46a/generation-1/evidence-manifest.json
- scripts/research/verify_phase1446a.py

## Exact proposed Java inventory (do not create)

- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/application/TradingApiAuthorization.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/application/TradingProjectionService.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/domain/TradingProjection.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingReadConfiguration.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingReadController.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingApiAuthorizationTest.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingProjectionTest.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingReadControllerTest.java

## Source bindings

InstrumentRegistry.java: a8248b8a27ebe2177ad05793877b0faf5c1c16956674490aea5a8513749232f2.
phase1415/strategies.py: a750329f690932f7806ddf0999c8a5d4430205cdc927ec012de663852df18e88.
phase1417/qualification.py: 73cad48eaf42a91d718306ba861ae3dda704c960785658d701d6554b5e5b17a7.
Phase14.45 manifest canonical fb878cf60db8fe3fffd79fa502f523e40b1b9ecce6cbdde03364d5dd613d5c72; raw ac1401da030518db2d836b4c4d6f8bb10b13bcf950e3afdcdb9bdb26bacb3363.
Bind the existing docs/api/trading-v1.openapi.json raw hash in the readiness packet.

## Discovery and assessment

Inspect local Phase14.44E-J owner/time/replay contracts and synthetic enforcement model; Phase14.46 records; synthetic-credential-owner source; existing broker authentication and trusted operator console descriptions. Hash these sources. Read no private credentials, key stores or user-profile secrets. No network discovery, dependencies, real signatures, keys or enrollment. Synthetic HMAC, boolean signature fixtures, broker login and interactive console confirmation do not establish approved owner authority.

Require independently verified identity/role, approved trust root and signature algorithm, exact canonical payload binding, trusted UTC approval time with uncertainty/freshness and explicit expiry, independently verified revocation, unique decision/nonce, atomic durable consumption and rollback-resistant independent checkpoint. Unknown values stay null. No workstation-clock validity countdown. No implementation, runtime or live authority.

## Validation and custody

Use the approved existing maintenance Python interpreter for file/Git preservation and in-process preparation checks only; verifier itself has no subprocess, network or application imports. Do not run Maven/JUnit/pytest/Ruff/mypy or original46/18/31 workloads. Use installed jsonschema in process (no installation), compile verifier source in memory without pyc, run pure synthetic rejection cases and two separate fresh-namespace verifier invocations with complete receipts. Receipts include completion, exception, output hash and canonical fingerprint; local duration is measurement, not trusted approval time. Successful synthetic checks never authorize implementation.

Custody: D:/Yogendra/offline-validation-evidence/phase-14.46A/generation-1, permitted only for this preparation evidence under the assessment request. Verify ancestor paths/reparse points, ownership, operator/SYSTEM/Administrators ACL, capacity >=100 MiB and destination/phase nonexistence before creation. Preserve ancestor ACLs. Capture baseline1876 raw hashes, existing untracked records and predecessor evidence map, source inventory, schemas, checks, both replay receipts and certificate. No overwrites; retain failures and stop without retries. Custody integrity certificate is not authenticated governance evidence.

Canonical fingerprints use sorted compact JSON, ASCII escaped, UTF-8, no NaN. Manifest excludes its own raw hash and later certificate/replay receipts to avoid cycles; external certificate binds all repository hashes and receipt hashes. Frozen plan copied to custody before remaining artifacts. No later plan changes without amendment.

Final preservation: current tracked1876, previous Phase14.45 baseline1866, predecessor maps,13newline reconciliations, frozen1115symbol/13380family,73historical unavailable receipts, Phase14.22 unchanged/untracked. Verify exact9new paths, LF/Git-byte equivalence, whitespace, repository secret patterns and synthetic canaries. Stop on any mandatory failure. No staging/commit/push. Expected classification AUTHENTICATION_PENDING if preparation succeeds without genuine authority. Operational readiness NO_GO.
