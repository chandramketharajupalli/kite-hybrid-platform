# Phase 13.0 validation

**PHASE 13.0 RESULT: KITE_RUNTIME_HARDENING_VALIDATED.**
Planned synthetic/integration scope passed. This is not LIVE_READY,
COLLATERAL_READY or live/paper trading authorization.

## Baseline and tools

Before edits, all six requested baseline commands passed. `git status --short`
and `git diff --check` were empty; branch was `develop`; HEAD and local
`origin/develop` both resolved to
`ac87a96df4ff0ab0484cc08f9ac3a1aec7be5b61`. The eight-entry log began with
`ac87a96 Document pending corporate-action evidence closure`.
No reset, stash, pull, clean, commit or push was performed.

Installed tools: Oracle JDK 21.0.12 (javac available), repository Maven wrapper
3.9.11, uv 0.12.10, Docker client/server 29.8.0. No toolchain installation or
global environment change. All Maven invocations used a child process with
ambient deployment/JVM-option variables removed; no dotenv helper was sourced.

The pre-edit [plan](phase-13.0-plan.md) records the inspected code paths,
gap matrix, synthetic error expectations, zero-real-call budget and stop rules.
Required architecture and Phase 10.8D/11.1/12.1C reports were reviewed. Public
Kite primary documentation was checked for login, margins and current rate
limits; no authenticated API or instrument download was used.

## Implementation and evidence

* Authentication status is now passive with respect to durable storage. Expired
  in-memory state still fails closed. Explicit restore/reset/callback cleanup
  and persistence retain their existing contracts. Unit tests distinguish status
  from explicit cleanup; disposable tests compare the complete encrypted row
  fingerprint across 401, 403, 429, 500 and 503 reads and repeated status checks.
  They assert effective HALT, false trading status and zero order/permit rows.
* REST and WebSocket auth-envelope detection now reject duplicate keys and
  trailing documents. REST requires an explicit error status. Malformed or
  success envelopes cannot revoke an authenticated identity. Valid auth errors
  retain existing invalidation semantics; historical auth remains preserving.
* `IntradayFundingEvidence` adds a pure, redacted Java view using existing
  cash-only equations. Cash sufficiency and evidence provenance are separate.
  Available/utilised collateral and net are observed; local cash is derived;
  exact quote margin/charges use the existing broker-provider contract; eligible
  collateral/cash terms and cash-field eligibility remain UNKNOWN. The view is
  always NOT_READY and has no fetch/cache/endpoint/dispatch capability.
* No account mapping change was needed. Existing strict mapper tests cover
  net/day, segments, MIS/CNC, null/missing quantities, precision, timestamps,
  partial/rejected orders, duplicates and empty days. New composition assertions
  check COMPLETE -> FILLED is terminal at account inspection, with OPEN/pending/
  unknown orders still blocking.
* Existing WebSocket loopback and fake-clock suites cover handshake, modes,
  deduplication, reconnect/backoff/restoration, session rotation, stale/older
  ticks, idle timeout, heartbeat, overflow/backpressure, malformed frames and
  exhaustion. No stream lifecycle or production subscription setting changed.
* Historical interval-start, ZERODHA identity, UTC/IST, half-open ranges, strict
  decimals/JSON, pacing/chunking, immutable replay/provenance and no-look-ahead
  cutoffs remain unchanged. Gate denial tests count zero provider calls and
  verify no repository interactions. Valid certificates are synthetic only.
* Runtime defaults/exposure/HALT remain unchanged. Trading status stays false;
  liveness and infrastructure readiness are not trading authorization. Tests
  cover disabled diagnostics, profile guards, local checks, authentication,
  market health and operational-readiness denials.

The rehearsal composes existing suites rather than adding another execution
framework: Spring authentication restart/account tests; actual JDK loopback
WebSocket-to-gateway/store recovery; calculation-only margin tests; historical
HTTP-to-disposable-PostgreSQL replay plus certified multi-instrument fixtures;
operator/preflight and response-loss reconciliation regressions. No real
end-to-end broker-readiness claim is made. The new read/status regression never
resumes HALT or creates orders/permits. The mandated retained full suites do
exercise synthetic execution, permits and HALT transitions inside isolated
fixtures; operational zero counters below do not conceal those simulations.

## Executed validation

| Check | Actual result |
| --- | --- |
| Focused five-class unit run | 119 passed / 0 failed / 0 errors / 0 skipped; 38.522 seconds |
| Initial full unit/architecture | 1235 passed / 0 failed / 0 errors / 0 skipped; 1m25s |
| Final full unit/architecture | 1243 passed / 0 failed / 0 errors / 0 skipped; 79 XML suites; 1m44s, finished 2026-10-10 14:06:56 +05:30; includes terminal-status/architecture/quote-binding additions |
| Full `-Pintegration verify` | BUILD SUCCESS; 1235 unit/architecture + 509 disposable integration passed, 0 failures/errors/skips; 16m32s, finished 2026-10-10 14:04:54 +05:30 |
| Project verifier | PASS: Maven/JDK requirements, profiles, defaults, JSON |
| Secret scan | PASS: final scan of 1653 text files, zero potential secret locations |
| Diff whitespace | PASS; tracked diff and all six new text files checked |
| Python pytest / Ruff / strict mypy | NOT_RUN: no Python source changes |

Commands are recorded in the [runbook](phase-13.0-runbook.md). Ignored logs:
`tmp/phase130-focused-unit.log`, `tmp/phase130-unit.log`,
`tmp/phase130-integration.log`, `tmp/phase130-final-unit.log`,
`tmp/phase130-secrets.log`. Failsafe XML independently totals 509 tests in 19
suites, zero failures/errors/skips. No Testcontainers-labelled container remained
after full integration. The final unit run is necessary because eight additional
test-only cases were added during integration; production code did not change.
No failed check is counted as passing. Maven warnings about existing deprecated
Jackson API usage and Mockito dynamic attachment are not test failures.
Redis-specific integration was not invoked: the existing suites use disposable
PostgreSQL and Redis is not required by the changed paths. No development Redis
or database was accessed. The full suite's simulated broker requests are fake
HTTP or loopback; no real account/session restoration occurred.

## Freeze and final safety audit

Initial G1 SHA-256 matched
`56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`.
`run_phase116.verify_freeze` passed all 18 source fingerprints, returning
`b04dc1c1ef72419f601a8ce494f3082f35992a1120a18625a8df77eb0e31d740`.
No evaluation/acquisition CLI was invoked. The first Python import regenerated
one already-tracked `__init__.cpython-312.pyc`; only that generated change was
replaced with its exact HEAD blob bytes. Subsequent Python verification disabled
bytecode writes. No bytecode change is part of the delivery.

All 63 tracked research evidence files from Phase 11.4 through Phase 12.1C were
inventoried with byte SHA-256 in ignored `tmp/phase130-protected-hashes.json`;
the tree was initially clean and none of those paths was edited. Final byte
comparison, G1 and all 18 source checks: PASS; all 63 protected files unchanged.

Phase 12.1C artifact retains `acquisition_allowed=false` and
`strategy_evaluation_allowed=false`; HDFCBANK, ICICIBANK, LT, RELIANCE and SBIN
all have CORPORATE_ACTION_UNRESOLVED. Acquisition gate BLOCK, unchanged.
H1/H2 G1 remain FROZEN and SBIN July TEST SEALED. Synthetic tests containing
July timestamps generate their own fixtures; no retained sealed bars are read.

Operational counts: real Kite account/profile/margin reads **0**; real historical
candle GETs **0**; real WebSockets **0**; real order mutations **0**; development
DB/token mutations **0**; real/paper dispatch, HALT resume, arm/execute and permits
**0**. Development tokens/rows were not read, so no before/after development
token fingerprint is claimed. No `.env`, secrets, configuration, risk limits,
reserve, production safety defaults, trading migration or frozen evidence was
edited. INR 10,000 first-live buffered-notional ceiling is unchanged.

## Exact Git inventory

Final branch/HEAD/origin match the exact baseline. Audited inventory is
10 modified tracked files and 6 new untracked files, unstaged, no commit/push.
No production resources changed. Tracked Git diff stat: 10 files, 150 insertions,
7 deletions; this excludes the six new files below. Ignored Maven outputs and
test logs are not deliverables. No generated artifact appears in Git status.

Modified tracked files:

```text
apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteAuthenticationRestartIntegrationTest.java
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/application/auth/KiteAuthenticationUseCase.java
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/JdkKiteWebSocketTransport.java
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteRestTransport.java
apps/trading-core/src/test/java/com/kitehybrid/platform/ConservativeValuationArchitectureTest.java
apps/trading-core/src/test/java/com/kitehybrid/platform/TradingReadArchitectureTest.java
apps/trading-core/src/test/java/com/kitehybrid/platform/broker/application/auth/KiteAuthenticationUseCaseTest.java
apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/JdkKiteWebSocketTransportTest.java
apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteRestReadTest.java
apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteTradingReadMapperTest.java
```

New untracked files:

```text
apps/trading-core/src/main/java/com/kitehybrid/platform/risk/domain/IntradayFundingEvidence.java
apps/trading-core/src/test/java/com/kitehybrid/platform/risk/domain/IntradayFundingEvidenceTest.java
docs/architecture/kite-connect-runtime-hardening.md
docs/operations/phase-13.0-plan.md
docs/operations/phase-13.0-runbook.md
docs/operations/phase-13.0-validation.md
```

## Remaining limitations and bounded follow-up

Collateral eligibility, applicable cash component and broker cash-field mapping
remain unresolved, independently of cash-only synthetic policy passing. Real
broker behavior, live data quality and multi-process pacing are not certified.
No real reads are needed to establish this phase's software contracts. A future
phase may review authoritative eligibility evidence and current operational
inputs under a separately approved bounded read plan, while remaining halted.
Phase 12 certification closure is separate work; there is no automatic live,
paper, historical acquisition or strategy promotion.
