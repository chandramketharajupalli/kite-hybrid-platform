# Phase 13.3 evidence and validation

**PHASE 13.3 RESULT: BROKER_TERMS_UNRESOLVED**. Planned synthetic engineering
validation and final audit passed. Public-source findings do not establish
current-account collateral-assisted readiness.

## Baseline and isolation

Before edits the six required baseline commands passed: clean develop, HEAD and
origin/develop both `18608d49f6f5f481e4f7889c801b3c72b452ebba`, subject
`Add broker-verified MIS collateral evidence contract`. No pull/reset/restore/
stash/clean/commit/push. Root AGENTS.md and the six required committed documents
were read; actual funding, quote, mapper/transport, session/store, HALT and
preflight implementations were inspected.

Oracle JDK 21.0.12, Maven wrapper 3.9.11, uv 0.12.10, Docker client/server 29.8.0.
No installation or global environment change. Deployment/JVM environment
overrides were removed only from Maven child processes, without printing values.
No dotenv helper or ordinary app startup. Tests use fake/loopback services and
disposable Testcontainers PostgreSQL, never development credentials/datasources.
Python verifiers disable bytecode generation to preserve tracked bytecode.

The [plan](phase-13.3-plan.md) was frozen before public-source investigation and
the new fixture cases. Its SHA-256 is
`4bdd6e18255daea580955e378ad09bfbccb0a1b328c28b4bfacc4ff91787a597`.
Expected G1 SHA and all 18 source hashes passed before work; all 63 tracked
research files were hashed from the clean baseline without evaluating strategies.

## Four-question outcome and public evidence

The complete source/date/scope/freshness/missing-field matrix is in
[the public-source review](../architecture/kite-mis-authoritative-evidence.md).
Access date for all sources: 2026-10-10. Undated articles were not assigned an
effective date; dated historical circulars are not claimed as current exemptions.

| Question | Status | Current-account proof / scope |
| --- | --- | --- |
| Eligible haircut-adjusted collateral | UNKNOWN | No authenticated category/eligible adjusted aggregate, restrictions or contribution |
| Actually free collateral | UNKNOWN | No authoritative free amount net of every commitment/reservation |
| Applicable cash-component rule | CONFLICTING | General positive-cash versus cash-equivalent coverage wording remains unreconciled for exact NSE MIS scope |
| Qualifying cash-field mapping | CONFLICTING | Current/mixed versus prior-closing UI cash descriptions conflict; precise API mapping remains unknown |

All four have no actual account/request binding, no effectiveAt/expiry and no
real observation fingerprint. **REAL_ACCOUNT_VERIFICATION_NOT_PERFORMED**;
real account evidence NOT_COLLECTED. **Collateral-assisted readiness NOT_READY**.
The current V1 runtime inputs remain unverified and produce UNKNOWN terms on
valid synthetic input; public-source conflicts are recorded separately, not
injected into risk policy or passed off as conflicting account balances.

Public assessment SHA-256 values (not fetched-page or account/token hashes):

| Descriptor | SHA-256 |
| --- | --- |
| Q1 | `dd6cbf29c89f0983d9700d0dcf218d6231f54dcf79a9c0d436fb6ccb274e033b` |
| Q2 | `849dfb20b9de01c40a37def5b4f162ca303f0e2acb9e29b01d51ff96f5331dcf` |
| Q3 | `62f2ffbb66865b67dec4ac91a23b66fd729f63fe93ce6016165c4418e0dc71b7` |
| Q4 | `17c9e7a093e41f871fd879a590811e1c83d8a2803796095796fe78fbead0b078` |

Reproduce from the exact descriptor lines in the architecture document:

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
uv run python -B -c "import hashlib;from pathlib import Path;p=Path('docs/architecture/kite-mis-authoritative-evidence.md');[(print(line.split('|')[1],hashlib.sha256(line.encode()).hexdigest())) for line in p.read_text(encoding='utf-8').splitlines() if line.startswith('phase13.3|')]"
```

The [clarification](phase-13.3-broker-clarification.md) is DRAFT / NOT_SENT and
uses synthetic examples only. The [one-GET proposal](phase-13.3-real-read-proposal.md)
is DENY / UNAPPROVED. Separate explicit authorization was requested under section
8; no real call has occurred. The documented equity route is not in the current
transport allowlist and its isolated no-write harness is not implemented. No
all-segment fallback, profile call or normal app startup is authorized.

## Changes and actual tests

No production change was necessary. Six added cases in the existing collateral
contract test vary unverified adjusted amounts and minimum-cash assumptions.
Even a claimed zero cash requirement cannot repair a one-paisa cash deficit or
produce authoritative category/eligibility evidence. There is no category
attestation input; we do not fabricate a test claiming accepted real categories.

| Command / suite | Actual result |
| --- | --- |
| Focused command in frozen plan | 305 passed / 0 failed / 0 errors / 0 skipped; BUILD SUCCESS |
| Full `mvnw.cmd test` | 1331 passed / 0 failed / 0 errors / 0 skipped across 81 suites; BUILD SUCCESS |
| Full `mvnw.cmd -Pintegration verify` | Repeated 1331 unit tests; 514 disposable integration tests across 19 integration suites; zero failures/errors/skips; BUILD SUCCESS in 14:33 minutes |
| Project verifier | PASS: Maven/JDK requirements, profiles, safety defaults and JSON syntax |
| Secrets scanner | PASS: initial and final content scans each covered 1673 text files, zero potential secret locations |
| Final diff/freeze audit | PASS: expected G1 SHA, 18 source hashes, 63 research files, frozen plan hash and five-member acquisition BLOCK |
| Python pytest / Ruff / strict mypy | Not run: no Python source/config/dependency changes |

Ignored logs: tmp/phase133-focused.log, phase133-unit.log,
phase133-integration.log, phase133-project.log, phase133-secrets.log and
phase133-secrets-final.log.
Surefire/Failsafe XML totals agree with the logs. Repeated unit tests are not
summed as unique tests. No test failed and no failure-driven rerun was needed.

Coverage reused: IntradayFundingDiagnosticsTest, IntradayAccountCapacityTest,
KiteOrderMarginAdapterTest, KiteTradingReadMapperTest, auth and architecture
tests. These cover cash/net/segment/utilisation, equality/deficit/fees, malformed
values, 401/403/429/5xx/timeout, missing quote, exact request/reference, freshness,
orders/positions, terminal COMPLETE->FILLED, market and full-notional guards.
Disposable operator/MIS suites cover final refresh, competition and response
loss; auth restart/storage suites distinguish passive status from deliberate
lifecycle mutation using synthetic tokens. Existing architecture guards exclude
execution/permit/HALT/I/O dependencies from diagnostics. No new endpoint added.

Full integration is deliberately run despite unchanged production code to cover
the requested preflight/persistence matrix. Existing isolated fixtures may
exercise fake execution and write disposable test tables; none is operational
paper/live dispatch, real token lifecycle or development DB mutation. The halted
diagnostic fixture asserts zero gateway/order/authorization effects and unchanged
HALT. Calculation-only fake POSTs never authorize real requests.

## Final inventory and safety audit

Final Git review: develop, HEAD and origin/develop unchanged at the full baseline
SHA above. Required status/diff-check/stat/name-status and untracked inventory
were inspected. Tracked diff is one test file, 17 insertions, no deletions;
five new documents are listed separately below. Production Java and resource
diffs are empty. All changes are unstaged. No .env, application configuration,
trading migration, token material, raw account response, Python bytecode or
generated build artifact appears in the Git inventory. Local document links pass.
The INR 10,000 full buffered-notional ceiling, cash-only reserve, CNC policy,
risk limits, final-dispatch checks and safety defaults are unchanged. H1/H2 G1
remains FROZEN; SBIN July TEST remains SEALED. All five Phase 12 members remain
CORPORATE_ACTION_UNRESOLVED; acquisition and strategy evaluation remain denied.
No runtime HALT release, permit or authorization was created outside isolated
test fixtures. No public diagnostic or Actuator exposure was added.

Modified tracked file:

```text
apps/trading-core/src/test/java/com/kitehybrid/platform/risk/domain/IntradayCollateralContractTest.java
```

New untracked files:

```text
docs/architecture/kite-mis-authoritative-evidence.md
docs/operations/phase-13.3-plan.md
docs/operations/phase-13.3-broker-clarification.md
docs/operations/phase-13.3-real-read-proposal.md
docs/operations/phase-13.3-validation.md
```

Real Kite requests (including account/profile/calculator): 0. Real historical
GETs/WebSockets/order mutations: 0. Development DB/token mutations: 0.
Operational HALT resume/arm/execute/permits: 0. Commit/push: NONE.
No development token fingerprints are claimed because the store was not accessed.

Next bounded action: user-mediated broker clarification and authenticated,
dated scope review. Any later account read needs the exact proposal approval
and all harness checks. No automatic eligibility, live/paper execution or Phase
12 acquisition promotion; no policy relaxation to make the report pass.
