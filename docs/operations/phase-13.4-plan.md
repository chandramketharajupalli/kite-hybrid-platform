# Phase 13.4 frozen implementation plan

Registered 2026-10-10 before production edits. Clean develop; HEAD and
origin/develop both `d61f6b28e84a1edab7b0ff7f8391d36aba16d16b`. G1 SHA and 18
source checks passed; 63 tracked research files hashed from the clean baseline.
No reset/restore/stash/clean/pull/commit/push. JDK 21.0.12, wrapper Maven 3.9.11,
uv 0.12.10, Docker client/server 29.8.0 are available.

## Inventory, gaps and minimal design

Existing KiteRestTransport GET allowlist: /user/profile, /instruments, /orders,
/trades, /portfolio/positions, /portfolio/holdings, /user/margins. Add only
`GET /user/margins/equity` at 64 KiB wire/decoded size. Existing timeout 10s/30s,
no redirects/retries, session monitor and bounded errors are reused. Existing
calculation-only /margins/orders and all order permissions remain unchanged.

KiteTradingReadMapper already has strict JSON duplicate/trailing rejection,
bounded exact BigDecimal parsing and SegmentMargin normalization. Add a separate
single-equity entry point reusing those helpers; do not weaken all-segment
BrokerMargins (which requires both segments), invent a commodity row or aggregate
segments. Expect success/data with a direct segment object. Reject nested
equity/commodity/segments wrappers and contradictory explicit segment labels.
The public documentation illustrates the all-segment body, so the direct shape
is an explicit single-segment assumption tested synthetically, not real evidence.
Known required fields absent/null/type-invalid fail; unknown bounded fields
are ignored. Signed values are observations, never eligibility approval.

Add package-local KiteEquityMarginReadAdapter, reusing transport and mapper,
with disabled default constructor and explicit opt-in constructor; no bean,
configuration property, endpoint, Actuator, scheduler or normal startup wiring.
Capture an internal typed snapshot (existing SegmentMargin) with local receipt
time, private adapter/session binding and transport provenance. Raw fields and
session identifiers are not emitted/logged. The production factory provenance
must differ from injected synthetic transport; neither is independent attestation.
Caller-normalized input remains unverified under the existing V1 contract.

Reconcile to existing IntradayFundingEvidence.CollateralContract, preserving its
quote/request/reference fingerprints and freshness checks. Overlay only observed
equity-field presence/classification; no PROVEN path, amount calculation or
cash-field eligibility inference. A single equity response lacks full account
positions/orders and cannot independently approve cash-only funding: report
unavailable for that projection. Existing complete synthetic cash-only arithmetic
continues unchanged. No risk, cash capacity, CNC, preflight or final-dispatch
policy change. Public-source cash-rule/mapping conflicts remain documented.

## Safety and failure taxonomy

Default real Kite requests ZERO. No operational orders, arm/execute/permit,
HALT resume, login/exchange, real WS/candles, development DB/token writes, .env,
safety/resource configuration, migrations, risk/reserve/notional changes or
research evaluation. H1/H2 frozen, SBIN July sealed, five-member acquisition BLOCK.
Existing fake execution fixtures are isolated regressions, not operational trading.

Before HTTP: disabled -> CONFIGURATION; absent/unverified/expired session ->
AUTHENTICATION. Snapshot/session/owner mismatch denies reuse; post-response auth
loss denies. 401/403 and valid TokenException invalidate memory only; 429/5xx ->
BROKER_API; timeout/connectivity -> TRANSPORT; malformed/oversized/invalid shapes
-> INVALID_RESPONSE. Existing strict TokenException precedence is retained.
No exception causes/messages from upstream, credentials, raw bodies/balances,
account IDs, cookies, headers or token/session fingerprints in diagnostics/logs.
Unknown/stale/future evidence never becomes broker-authoritative.

No token store or writable repository dependency in the adapter; no startup
restore/reset. Disposable integration seeds synthetic credentials before the
observation window, uses a read-only DB role/transaction during observation,
and compares token bytes plus trading-table counts/content before/after. No
claim that one read-only connection constrains a different startup connection.
No ordinary app startup in the new isolated harness.

## Synthetic matrix and acceptance fixed before tests

Unit tests: exactly one equity GET, required synthetic auth headers, no other
route/method, disabled/auth denial before HTTP; direct success/exact decimals;
all-segment and wrong-segment rejection; missing/null/unknown/type-invalid values;
duplicate/trailing/empty/malformed/nonfinite/oversized responses; signed amounts;
401/403/429/5xx/timeout/connectivity and sanitized causes; session change during
and after read; stale/future receipt; wrong request/reference mapping; large net,
negative cash, fully utilised and unknown category/cash mapping remain unproven.
Snapshot output and diagnostic serialization must not disclose raw values/IDs.
Architecture guards restrict the new adapter to GET transport and exclude order,
operator, permit, HALT mutation, auth lifecycle, SQL and Spring-startup capabilities.

Reuse existing full suites for margin/fees/cash exact equality and one-paisa loss,
pending/open/unknown vs COMPLETE->FILLED orders, changed positions, competing
instances, stale market, final-refresh deterioration, CNC and INR 10,000 full
buffered-notional limits. Disposable test: success and all failure categories,
unchanged encrypted token/trading tables/HALT, no operator permits or mutations.
No new tests claim synthetic balances prove actual eligibility.

Success: narrow adapter + redacted V1 reconciliation + required tests pass;
four terms unproven -> NOT_READY, independent of engineering PASS. Record actual
failures/skips/reruns; no acceptance relaxation. Stop for baseline/protected drift,
unexpected real route, secret exposure or inability to isolate persistent state.

## Authorized inventory and validation

Modify KiteRestTransport.java, KiteTradingReadMapper.java and
TradingReadArchitectureTest.java. Add KiteEquityMarginReadAdapter.java,
KiteEquityMarginReadAdapterTest.java and KiteEquityMarginReadIntegrationTest.java.
Optional narrow fixture reuse in existing test classes only if required.
Create this plan, phase-13.4-validation.md, phase-13.4-runbook.md,
phase-13.4-real-read-proposal.md and architecture/kite-read-only-equity-margins.md.
No other production path is planned. Document any necessary narrow safety fix.

Use deployment/JVM overrides removed only in child processes (KITE_, RISK_,
SPRING_, DB_, REDIS_, TRADING_, SERVER_, MANAGEMENT_, ENABLE_LIVE_TRADING,
EMERGENCY_STOP, JAVA_TOOL_OPTIONS, JDK_JAVA_OPTIONS, _JAVA_OPTIONS, MAVEN_OPTS),
never print values/load .env. Run Maven sequentially with JDK 21:

```powershell
.\mvnw.cmd '-Dtest=KiteEquityMarginReadAdapterTest,KiteTradingReadMapperTest,KiteTradingReadAdapterTest,KiteOrderMarginAdapterTest,KiteAuthenticationUseCaseTest,IntradayCollateralContractTest,IntradayFundingDiagnosticsTest,TradingReadArchitectureTest,ConservativeValuationArchitectureTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

Set PYTHONDONTWRITEBYTECODE=1 and use python -B for freeze/hash checks; do not
regenerate tracked bytecode. No Python changes planned; pytest/Ruff/mypy if any.
Record Surefire/Failsafe counts, logs, full Git/untracked/production/resource
inventory; recheck G1/18 hashes/63 protected hashes/Phase12/plan hash.

## Optional real-read checkpoint and recovery

Create a concrete proposal for at most one GET /user/margins/equity, no profile,
calculator, orders, WS/candles or retries. Request separate explicit approval
under section 9, then STOP at the real-read boundary; synthetic work continues.
Approval is not supplied by this phase or prior proposals. No real runnable
credential-loading harness is activated now. Any later run needs independently
verified HALT/live-disable, safe existing auth/account binding, SELECT-only DB,
before/after integrity checks and no startup side effects.

Rollout: Java-only opt-in construction, unwired by default; no runtime deployment.
Recovery: stop the isolated process, preserve HALT, close test resources, report
bounded denial. Never retry, repair tokens, relax policy or restore user files.
