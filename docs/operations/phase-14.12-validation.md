# Phase 14.12 validation

PHASE_14_12_PARTIAL. Baseline develop HEAD/local origin/develop:
cf5db2b87d33f44cea6745d8e4d7fbf9a04d69e2. Initial tree/index clean; eleven Phase14.11
files committed. Baseline1115 tracked files; original artifacts preserved raw byte-for-byte.
Frozen plan SHA256 e3b8f62dbe83aa4d11ba8a2914ff7d53a50cd1bd140e78724148c991332ff91c.

Sixteen additions: ten standalone-module POM/source/test files, plan, additive build
amendment, architecture, runbook, this validation and evidence manifest. No existing
file modified. Exact paths and hashes are recorded in the manifest. No staging.

## Fresh attempts and results

All Maven commands offline; no downloads. No failures were discarded as successes.

1. Read-only preservation traversal initially raised AttributeError: older parent-hash
   fields are scalar, not maps. Corrected raw-table traversal and existing verifier passed.
2. Adapter verify attempt1: exit1 before compilation/tests; cached build-helper3.6.1
   lacked file-management3.2.0. No test cases ran. Additive plan amendment records the
   switch to cached compiler3.14.1 compileSourceRoots, verified from local plugin descriptor.
3. Adapter verify attempt2: main eighteen allowed sources compiled; testCompile incorrectly
   inherited source roots and attempted221 production sources, failing on absent Spring/
   logging/DB dependencies. No test cases ran and no application executed. Correction:
   source roots/includes scoped to default-compile execution only. TestCompile now five
   test sources. No unrelated dependency was added to make that compilation succeed.
4. Adapter verify attempt3:71unit+8process integration passed,0failures/errors/skips.
5. Unchanged policy full verify:40unit passed,0failures/errors/skips.
6. Review hardening: observed revocation made sticky within verifier instance; added one
   replay-of-unrevoked-state regression. Adapter verify attempt4:72unit+8process passed,
   0failures/errors/skips. Final XML hashes bind this run; earlier run totals retained here.

Final validation set:120tests =112unit+8process/filesystem integration. Across successful
runs199 test executions occurred, including reruns; these are not199 distinct tests.
There were two build failures, no failing test assertion. Historical334 Phase14.11
tests are historical only, not included as fresh Phase14.12 totals.

| Final suite | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| AdapterTest |47|0|0|0|
| ParserTest |25|0|0|0|
| StoreProcessIT |8|0|0|0|
| GateAOfflinePolicyTest |40|0|0|0|

Commands: mvnw.cmd -o -f apps/gate-a-offline-adapters/pom.xml -Pintegration verify;
mvnw.cmd -o -f apps/gate-a-offline-policy/pom.xml verify.
No full trading-core/Python/DB/container suites rerun: their sources/contracts unchanged;
new standalone includes reused parser sources and tests the bridge directly. Existing
synthetic-owner/witness/OS evidence reviewed, not presented as newly run. No provisioning.

## Measured controls and limitations

Signed permit/current-state verification, wrong scope/key/attempt, missing/expired/revoked
state, stale epoch, clock rollback, sticky revocation, exact method/route/proxy restrictions,
consume-before-observe, post-force revocation, uncertain ACK, partial/corrupt/missing store,
response uncertainty, redaction and parser schema/encoding/hash/size/ambiguity/missing/
formula-symbol checks passed. Session metadata exposes no secret method. No issuer,
real owner approval, credentials, HTTP, orders, candles or launcher in runtime.

Process tests: two JVMs produce exactly one synthetic accepted observation; restart denies.
Crash BEFORE_CREATE leaves no marker/observation and a later explicit run can consume.
Crashes AFTER_CREATE/AFTER_FORCE/AFTER_OBSERVATION leave consumed state and restart denies.
Corrupt marker denies. Restored process state with retained marker denies. Deliberately
restoring all authority permits a second observation: preserved counterexample, not a pass
for universal rollback prevention. Local-disk rollback, independent witness, durable
epoch/quarantine across restart, physical power-loss and multi-host recovery remain gaps.
No sudden-power-loss/full-disk or real network delivery claim.

Final JAR49classes, eighteen allowed main source files, five test sources, no Main-Class.
Constant-pool inspection found no network client/socket/SQL/Spring/KiteRestTransport/
KiteHistoricalAdapter/env-reader references in packaged classes. Classpath is Jackson
CSV/core/databind/annotations plus test-only JUnit dependencies. No trading-core artifact.
Runtime hash and exact source hashes recorded in manifest. Configuration alone does not
establish OS confinement. Historical Phase13.11 Linux observations and Phase13.12 failure
remain preserved; Phase13.14/13.16 production gaps remain unresolved.

## Preservation and decision

Phase14.0-14.11 manifests/supplements, original978 evidence entries, Phase14.0A13 newline
reconciliations, G1/18source/63research/25K-L,17plans/11governance envelopes checked.
Canonical universe1140source-25duplicates=1115unique enabled NSE; original order unchanged.
No genuine master:0genuine resolved,1115MASTER_UNAVAILABLE. No actual historical results.

Final replay checks all baseline/new raw hashes, canonical manifest fingerprint, exact
sixteen-file inventory, LF/Git byte equivalence, whitespace and secret/canary patterns
twice. Generated JAR/XML/cache files remain ignored and excluded; no staged changes.

PRODUCTION_AUTHORITY_NOT_ESTABLISHED; ROLLBACK_RESISTANT_AUTHORITY_NOT_ESTABLISHED;
PRODUCTION_EGRESS_NOT_VERIFIED; AUTHENTICATED_SESSION_NOT_ESTABLISHED.
Compliance submission stays USER_REPORTED_SENT_WITH_SCREENSHOT, original bytes/body
unavailable, all eleven topics UNRESOLVED, no delivery or response invented.
ZERODHA_TERMS_UNRESOLVED. Gate A/B NOT_AUTHORIZED. Agent real Kite calls, credentials,
messages, orders, deployments, trading-DB writes and operational HALT changes all zero.
NO_GO, ZERO_CALL_ABORT, Phase12BLOCK, H1/H2FROZEN, JulySEALED, INR10000 ceiling and
V2/governance/technical-review pending states unchanged. No commit or push.
