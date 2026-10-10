# Phase 13.3 frozen plan

Registered 2026-10-10 before public-source investigation, new fixtures or behavior
changes. Baseline: clean develop, HEAD and origin/develop both
`18608d49f6f5f481e4f7889c801b3c72b452ebba`. G1 SHA and all 18 source hashes
passed; all 63 tracked research files were hashed from this clean baseline.

## Questions and evidence threshold

Independently establish (1) eligible haircut-adjusted NSE MIS collateral,
(2) the currently free portion after all commitments, (3) the exact applicable
cash/cash-equivalent/category rule, and (4) the qualifying funds field or
broker-confirmed computation. Record PROVEN, UNKNOWN, STALE or CONFLICTING with
source, account/product/request scope, effective time, freshness/expiry and
evidence fingerprint. Missing scope/date is absent, not a guessed zero/date.

Authority hierarchy: applicable official dated policy establishes general
rules; authenticated account/product/date-specific broker attestation may
establish scoped terms; authenticated API responses establish observations
only within documented field semantics. Public schema names, caller flags,
synthetic inputs and derived arithmetic cannot authenticate account evidence.
Contradictory public-policy interpretations stay CONFLICTING pending resolution.
No ratio or exemption is imported from F&O into NSE cash-equity MIS by inference.

## Inventory and minimal gap response

| Current path | Observed boundary / gap | Planned response |
| --- | --- | --- |
| risk/domain/IntradayFundingEvidence | V1 records four terms, source IDs, local freshness and request/reference checksums; no PROVEN-producing factory | Reuse unchanged; public-source findings are a separate evidence ledger, not runtime authority |
| IntradayAccountCapacity / CashAccountCapacity | Existing conservative arithmetic; numeric optional terms require a trusted provider; cash-only diagnostic strips supplied terms | Preserve policy and test equality, deterioration, net/utilisation exclusions |
| OrderMarginQuote / KiteOrderMarginAdapter | Exact supported BUY/MARKET/NSE/MIS/DAY/REGULAR shape; calculation only; optional collateral terms absent | Preserve; test malformed/mismatch/error cases and zero order-mutation routes |
| BrokerMargins / KiteTradingReadMapper | Explicit equity/commodity normalization; aggregates have no category/haircut/encumbrance attestation | Document missing terms; never fabricate category eligibility or free amount |
| CurrentAccountExecutionChecks / operator preflight | Shared current account, quote, reference, market and final transport checks | Reuse synthetic regressions; no new risk engine |
| KiteSession / KiteAuthenticationUseCase / PostgresKiteAccessTokenStore | Passive status preserves durable tokens; normal restore/reset may write/clear; startup unsuitable for passive harness | No normal app startup; no real store access; run fake/disposable regressions |
| RuntimeTradingHalt / execution fencing | HALT and one-shot authorization independent of diagnostic success | Preserve; new evidence work has no gateway/permit capability |

## Allowed files and stop conditions

Create phase-13.3-plan, validation, broker-clarification and real-read-proposal
under docs/operations, plus docs/architecture/kite-mis-authoritative-evidence.md.
If existing tests lack meaningful authority/category perturbation coverage,
extend only IntradayCollateralContractTest. No production change is planned or
needed merely to improve documentation. A demonstrated safety defect requires
a narrowly documented fix and full integration validation, not relaxed criteria.
No harness is implemented until explicitly authorized and independently tested.

Do not modify .env, configuration, migrations, risk limits, cash reserve, CNC,
INR 10,000 full buffered-notional ceiling, protected research or token material.
No operational order dispatch, HALT resume, arm, execute, permit, DB write,
historical acquisition, real WS, strategy evaluation, sealed TEST access,
commit or push. Existing tests may use isolated fake execution fixtures; these
are regressions, not real/paper operational actions or permission to trade.
Stop for baseline mismatch, protected-file drift, unexpected real endpoint,
credential exposure or inability to isolate tests. Never discard user work.

## Real-read checkpoint and redaction

Default real Kite budget ZERO, including profile, calculation and account GETs.
Create a separate proposal after verifying official endpoint paths and local
transport allowlists. Prefer one equity-margin GET; profile only if necessary.
Request explicit approval of that concrete proposal. Until approval, execute
zero calls and finish synthetic work with REAL_ACCOUNT_VERIFICATION_NOT_PERFORMED.
No approval is implied by silence or continuation. Approval does not waive
no-write harness tests. No normal Spring startup, token exchange or cleanup.

Do not retain raw account bodies, identities, holdings, balances, credentials,
headers, cookies or token fingerprints. Source-ledger fingerprints cover public
source/claim descriptors only, never private evidence. Synthetic examples must
be marked synthetic and must not select a real executable candidate/quantity.
Broker clarification is an unsent draft.

## Negative matrix and fixed acceptance

Run existing exact BigDecimal/fixed-clock/fake transport cases for cash equality
including fees/reserve; one-paisa deterioration; increased quotes/charges; high
net/positive live balance without qualifying cash; utilised vs free/fully used
collateral; unsupported commodity; missing category/haircut/cash mapping;
stale/future snapshots; request/segment/reference mismatch; 401/403/429/5xx,
malformed JSON/timeout; pending/open/unknown orders vs terminal FILLED; stale
market; competing instances; final-refresh deterioration; CNC and full-notional.
Add a small fixture test only for a demonstrated coverage gap around changed
unverified haircut/category claims. Such claims must never create PROVEN.
No documented positive-cash policy is presented as an exact qualifying field.

Success means reproducible public-source findings with explicit unresolved or
conflicting terms, complete unsent clarification/proposal, required synthetic
tests passed, unchanged frozen/safety boundaries and zero unapproved real reads.
Synthetic PASS is not broker-terms resolution or live readiness. Four current
terms lacking independent proof keep collateral-assisted readiness NOT_READY.
Use BROKER_TERMS_UNRESOLVED if none of the four scoped questions is established;
partial/resolved only for independently supported scoped terms. Report technical
blockers separately; never describe an unrun/failed suite as PASS.

## Validation commands

Use JDK 21 and the repository wrapper. Remove ambient KITE_, RISK_, SPRING_, DB_,
REDIS_, TRADING_, SERVER_, MANAGEMENT_ deployment overrides plus live/stop and JVM
option variables in the child process only, without printing values. Never load
.env. Set PYTHONDONTWRITEBYTECODE=1 for Python verifiers. Maven runs sequentially.

```powershell
.\mvnw.cmd '-Dtest=IntradayCollateralContractTest,IntradayFundingDiagnosticsTest,IntradayFundingEvidenceTest,IntradayAccountCapacityTest,KiteOrderMarginAdapterTest,KiteTradingReadMapperTest,KiteAuthenticationUseCaseTest,ConservativeValuationArchitectureTest,TradingReadArchitectureTest,OperatorControlArchitectureTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

Full integration is chosen even without production changes to exercise the
existing preflight/final-refresh/auth persistence matrix in disposable
Testcontainers PostgreSQL. No development datasource/token store. Redis is not
needed by this evidence path. Record actual Surefire/Failsafe totals and reruns.
No Python edits planned; pytest/Ruff/mypy apply only if Python changes.
Recheck G1/18 hashes, all protected baseline byte hashes, five-member BLOCK,
Git inventory/untracked files, resource/production diffs and plan hash at end.
No runtime rollout: documentation/tests only. Recovery is stop the isolated
test/harness and preserve HALT; never repair credentials, retry or resume.
