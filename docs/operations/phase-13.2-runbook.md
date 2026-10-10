# Phase 13.2 safe evidence review

Synthetic engineering only unless the separate real-read proposal is explicitly
approved. No real/paper orders, HALT resume, arm, permits, execute, login/token
exchange, production configuration or development DB/token writes. No real WS,
historical acquisition, research evaluation, commit or push.

## Meaning of the result

`KiteMisCollateralEvidence.v1` distinguishes normalized observations from
independently verified broker evidence. Its four term statuses cannot become
PROVEN through a caller flag, numeric CollateralTerms or calculator success.
Cash-only APPROVED means only the unchanged margin+charges+cash-reserve test
passed. Collateral-assisted readiness is always NOT_READY. No diagnostic is a
risk approval or permit. The INR 10,000 ceiling concerns full buffered notional,
not required margin. No executable candidate/quantity is selected here.

Read [source/field contract](../architecture/kite-mis-collateral-contract.md),
[frozen plan](phase-13.2-plan.md) and [validation](phase-13.2-validation.md).

## Synthetic commands

Use JDK 21, repository wrapper and Docker in a disposable child PowerShell.
Do not load `.env`, use a diagnostics helper, or start the normal development
application; it may restore a real token or migrate the development database.

```powershell
cd D:\Yogendra\kite-hybrid-platform
$phase132Names = @([Environment]::GetEnvironmentVariables('Process').Keys | Where-Object {
    $_ -match '^(KITE_|RISK_|SPRING_|DB_|REDIS_|TRADING_|SERVER_|MANAGEMENT_|ENABLE_LIVE_TRADING$|EMERGENCY_STOP$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$|MAVEN_OPTS$)'
})
foreach ($phase132Name in $phase132Names) {
    [Environment]::SetEnvironmentVariable($phase132Name, $null, 'Process')
}
$env:PYTHONDONTWRITEBYTECODE = '1'
.\mvnw.cmd '-Dtest=IntradayCollateralContractTest,IntradayFundingDiagnosticsTest,IntradayFundingEvidenceTest,IntradayAccountCapacityTest,KiteOrderMarginAdapterTest,KiteAuthenticationUseCaseTest,ConservativeValuationArchitectureTest,TradingReadArchitectureTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

Run Maven sequentially. Unit tests use fixed times, synthetic normalized data,
fake HTTP and loopback WS. Integration uses isolated Testcontainers PostgreSQL
and injected fake providers; never substitute a development datasource. Redis
is not needed for this evidence path. Existing simulated execution fixtures
exercise safety gates and response-loss reconciliation; they are not operator
instructions. The new contract invocation in the halted fixture leaves order
and authorization rows empty and operator/permit state unchanged.

Invoke `collateralContract` only with synthetic inputs for this phase. Its
output is Java-only; no endpoint or Spring bean is added. Do not log/serialize
input records. The redacted output contains local times, scoped checksums,
source identifiers, evidence classifications and denials, with no balances,
account/order identifiers, credentials, raw instrument token or raw request.
Keep checksums private: they are identity checks, not anonymization guarantees.

## Triage

| Status or failure | Interpretation / safe action |
| --- | --- |
| UNKNOWN / INDEPENDENT_EVIDENCE_MISSING | All current inputs may be valid but no independent account attestation exists. Use the broker draft; do not fabricate zeros, haircut or cash mapping. |
| STALE / INPUT_STALE | Account, quote or reference is at/over its configured age limit. Recompute synthetic context; old evidence cannot be reused. No claim is made that unknown authority was previously proven. |
| CONFLICTING / FUTURE_OBSERVATION | A local timestamp is ahead of now. Fail closed; do not invent a broker effective date. |
| CONFLICTING / REQUEST_OR_REFERENCE_CONFLICT | Quote/request or reference identity/exchange/symbol/type/broker differs. Do not silently rebind. A token mapping change also changes the mapping checksum. |
| UNKNOWN / INPUT_UNAVAILABLE | Missing quote, reference, clock, capacity, segment or invalid evidence/policy. Unsupported commodity fields cannot repair equity evidence. |
| UNKNOWN / AUTH_REQUIRED | Missing/rejected/expired auth. Do not log in or exchange tokens. |
| Cash-only COLLATERAL_UNSUPPORTED / MIS_MARGIN_INSUFFICIENT | Cash lower bound fails margin+charges+cash-only reserve. Net, utilised amounts, pledge labels or successful calculation cannot override it. |
| HTTP 401/403 | Existing ordinary reads invalidate in-memory auth. Passive `/api/broker/kite/auth/status` preserves durable storage. Restore/reset are deliberate lifecycle actions and forbidden in a passive read harness. |
| HTTP 429 | Stop; no retries. Any real reattempt needs its own authorization/budget. |
| HTTP 5xx/timeout/connectivity | Bounded unavailable evidence; no cached success or fallback to orders. Strict explicit TokenException retains existing rejection behavior; otherwise transient reads do not revoke tokens. |
| Malformed REST/WS, duplicate/trailing JSON, negative/NaN values, missing charges | No partial accepted quote or fabricated eligibility. Existing strict mapping/error tests must pass. |
| Stale/unhealthy market or queue/reconnect issue | Deny preflight. Heartbeats/connected socket do not prove fresh ticks. Use loopback tests only. |
| Active/pending/partial/UNKNOWN order, changed positions, competition | Existing preflight/final fences apply. COMPLETE normalizes to FILLED; terminal orders remain distinct. Do not resume, arm, retry or repair permits. |
| HALT or absent authorization | Expected operating boundary. Do not bypass to make diagnostics pass. |
| Phase 12 unresolved certificate | Acquisition stays BLOCK with zero provider/repository calls. No sealed July access. |

General positive-cash policy is known; exact qualifying cash-field mapping and
complete NSE MIS cash/category requirement remain unresolved. Approved-list
wording and F&O rules cannot be combined by guesswork. Current local timestamps
do not create an atomic broker snapshot or reserve funds.

## Shutdown and final audit

Inspect Maven exit codes and Surefire/Failsafe counts, including failures,
errors and skips. Test contexts close peers and disposable containers. If
interrupted, identify test-owned resources before stopping any; do not touch
unrelated services/volumes. Preserve user files, never reset/stash/clean/restore.
Fix demonstrated defects without changing acceptance or safety settings.

Inventory `git status --short`, `git diff --check`, `git diff --stat`,
`git diff --name-status` and untracked files; inspect production diffs. Verify
G1 SHA, all 18 frozen sources and protected research byte hashes without running
the research evaluator. No `.env`, resources, migration, risk/CNC policy,
reserve, notional ceiling or generated artifact changes. No commit/push.

## Optional real reads and broker clarification

[Two-read proposal](phase-13.2-real-read-proposal.md) is a proposal only, not an
executable command. Section 11 requires separate explicit authorization and
all token-preserving harness checks before any request. Without approval report
REAL_EVIDENCE_NOT_COLLECTED and complete synthetic validation. Silence is not
approval. No login/refresh or extra request is allowed if an existing credential
is unavailable. The proposal's successful execution would still not prove
the four missing terms automatically.

The [broker clarification](phase-13.2-broker-clarification.md) is an unsent draft.
It asks about available adjusted collateral, category/utilisation constraints,
the exact NSE MIS cash rule/field, calculator limitations and final refresh.
Sending it requires an explicit user instruction. A future response needs
source/effective-date/scope review before any trusted provider implementation.
No engineering result causes live/paper or historical-acquisition promotion.
