# Phase 14.5 frozen plan

Authoritative requirements: user's complete 17-section Phase14.5 prompt, supplied
in this conversation. This plan preserves its scope and acceptance gates below.
Actual baseline: develop, HEAD/local origin/develop
2c3632ae0ccadbf70da26acbaf95986e9bb79c43. Initial index/worktree clean; all11
Phase14.4 additions committed. Earlier 4431bfb is historical, not current HEAD.

## 1. Mission
Close offline Gate A technical gaps and draft unsent terms clarification. Technical
tests never establish terms clearance or owner authorization. No real Kite calls,
credentials, vendor messages, candles, orders, deployment, staging, commit or push.

## 2. Preservation
Require develop/synchronized local refs. Verify Phase14.0-14.4, exact13 CRLF/LF
reconciliations, G1,18 frozen sources,63 research,K/L,plans/manifests and safeguards.
Mismatch means BLOCKED, never repair. No fetch/reset/clean/Git configuration edits.
After this expected plan addition, recheck preservation before implementation.

## 3. Freeze
Hash this plan before implementation. Later scope corrections must be additive.
All pre-existing tracked bytes remain unchanged. Permitted additions only:
- docs/operations/phase-14.5-plan.md
- docs/operations/phase-14.5-terms-and-clarification.md
- docs/operations/phase-14.5-owner-decision.md
- docs/operations/phase-14.5-validation.md
- docs/architecture/phase-14.5-boundary.md
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/GateABoundary.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/GateABoundaryTest.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/GateAProcessProbe.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/GateALedgerIT.java
- research/phase-14.5/generation-1/summary.json
- research/phase-14.5/generation-1/test-results.json
- research/phase-14.5/generation-1/git-inventory.json
- research/phase-14.5/generation-1/evidence-manifest.json
- scripts/research/verify_phase145.py

## 4. Audit/reuse
Reuse existing mapper/importer/DatedInstrumentMaster/resolvers, generic transport
inspection, synthetic-owner AttemptJournal and Phase14.3 preview. The existing
synthetic module forbids broker origin in packaged runtime. Put this offline
boundary in test scope, not weaken packaging or add a production launcher.
No CSV parser duplication, trading-core/startup/order/session/HALT dependencies.

## 5. Terms
Read current official instrument docs and terms; record URLs, observation times,
clauses and limited excerpts. Distinguish caching guidance from persistence,
derived data, research, sharing, termination and exchange licensing rights.
Ambiguity remains TERMS_UNRESOLVED, not inferred permission.

## 6. Clarification
Prepare DRAFT / NOT_SENT to an attributable published support channel. Ask about
one private authenticated NSE CSV, temporary holding,1115-symbol mapping, retained
mapping/hash/audit/research records, deletion/termination/distribution and separate
future historical use. No personal/account/credential data or vendor contact.

## 7. Owner decision
Separate UNAPPROVED packet: exact source, use basis,budget,auth provenance,retention,
logs,expiry,process,egress,response/time limits,evidence and stop conditions.
Unsupplied identities/timestamps/decisions PENDING. Synthetic assertions are not consent.

## 8. Boundary
DB-free Java fixture only. Exact string GET https://api.kite.trade/instruments/NSE;
deny all other methods, relative/normalized/encoded URLs, ports, hosts,userinfo,
fragments,queries,suffixes,NFO,root instruments,historical/orders,redirects/proxies.
Closed synthetic response fixture; no network client or arbitrary transport callback.

## 9. Containment
Inspect dependency/call graph; no orders,DB,WebSockets,token restore or startup.
Independent OS/egress containment NOT_VERIFIED; fixture tests are not production proof.

## 10. One-use
Reuse fixture journal CREATE_NEW retirement marker, write/force before simulated
dispatch. Existing/partial/uncertain state denies; no retry after any uncertain result.
Test competing owners,crash before send,restart/replay/failover,persistence failure.
Retain all-authority rollback counterexample and physical power-loss limitations;
never claim stronger production durability than tests establish.

## 11. Custody
No .env/token/login access. Synthetic owner identity only, no real session proof.
AUTHENTICATED_SESSION_NOT_ESTABLISHED. Future ownership/entitlement independently required.

## 12. Response
Bounded fixture memory, strict content type/UTF8,hash/time provenance,redacted errors.
Use existing CSV validation tests for valid,malformed,oversized,duplicates,ambiguity,
missing/wrong exchange. CSV bytes are data, never formula-executed; raw symbols retained.
Unauthorized retention denies. Real raw persistence/deletion policy remains unresolved.

## 13. Universe
Reconcile1140/25/1115,original spelling/order. Reuse full ledger with0 genuine RESOLVED,
1115 MASTER_UNAVAILABLE; no tokens invented. Document supported status vocabulary.

## 14. Gate B
PENDING/NOT_AUTHORIZED independently; no candles/backtests using new genuine data,
WebSockets,orders,HALT resume or capital-limit change.

## 15. Tests
Offline focused boundary Java and existing mapper/resolver/architecture tests; Python
Phase14 tests. Run whole synthetic-module unit suite. Disposable filesystem ledger
integration with separate JVMs for crash/restart/concurrency; no DB changes so PostgreSQL
NOT_RUN unless justified. Full trading-core/Python suites if production/shared contracts
change. Required negative cases include missing/expired approval,auth absence,route,
redirect/proxy,uncertain ack,persistence,size,CSV,logs,storage,Gate B isolation.
Retain failures/reruns. No genuine broker client invocation.

## 16. Evidence
Add plan,terms/draft,owner packet,architecture/threat model,validation,inventory,manifest.
Fresh exact totals,failed attempts,hashes,secret scan,LF/Git byte identity. Verifier
checks manifest twice without edits. Generated target/temp fixtures are excluded from Git.

## 17. Outcomes/stops
Use TERMS_PENDING_BOUNDARY_VALIDATED only for tested offline boundary; AUTHORIZATION_READY
requires every real preflight except approval; PARTIAL for unresolved mandatory tests/
containment; BLOCKED for baseline/preservation/safety. Prefix all with PHASE_14_5_.
Report SHA,inventory,tests,terms,custody/retention,mappings,zero real requests,Gate A/B,
NO_GO. Preserve ZERO_CALL_ABORT,Phase12 BLOCK,H1/H2 FROZEN,July SEALED and capital limits.
