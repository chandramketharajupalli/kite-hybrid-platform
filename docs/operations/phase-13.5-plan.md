# Phase 13.5 Stage A frozen plan

Registered 2026-10-10 before production changes or external source requests.
Clean develop; HEAD and origin/develop: 4aaf9f78637736224cfb846ef7004659ac5603e0.
JDK 21.0.12, wrapper Maven 3.9.11, uv 0.12.10, Docker 29.8.0 available.
G1 manifest SHA and existing 18-source verifier passed; 63 protected research
files hashed. Five-member Phase 12 acquisition remains BLOCK; July TEST sealed.

## Reviewed construction and gaps

Existing package-local KiteEquityMarginReadAdapter defaults disabled, uses the
existing transport/session, strict single-segment mapper and private snapshot
ownership. Its per-invocation GET has no run-wide budget or DB integrity guard.
KiteAuthenticationUseCase.restore can fetch profile/instruments and clean tokens;
it is forbidden in this harness. Accept only an already authenticated in-memory
KiteSession; never install/profile-validate a real token to bypass the check.
Existing IntradayFundingEvidence.equityObservation remains the pure unverified
projection; broker infrastructure must not depend on risk. No funding edits.

Add isolated, package-local, opt-in single-use harness; no Spring bean, CLI that
loads credentials, controller, scheduler or normal application startup. Add a
SELECT-only PostgreSQL integrity guard using an existing Connection, never a
DataSource or writable store. Verify server read-only transaction, restricted
role (no superuser/role creation/replication/bypass), no trading-table writes or
sequence privileges, no other client connections to that database and no prepared
transactions. Compare sorted counts/SHA-256 row fingerprints before/after in
READ COMMITTED snapshots; do not hide writes behind one repeatable-read snapshot.
Reject missing required tables, schema inventory changes and existing permits.
Capture token ciphertext only through fingerprints in private memory; no values
in output. Guard does SELECT only, never changes roles/transactions/DB settings.

Require active startup and runtime HALT; no execution components exist in the
isolated composition. Check same HALT epoch/session and database isolation before
and after, including error outcomes. An operator must independently verify no
other trading process can reconnect during Stage B; DB checks cannot prove future
external inactivity. If that assurance or a safe pre-authenticated session handoff
is unavailable, Stage B aborts with zero calls. Do not start a trading app to get it.

## Wire controls and allowed changes

Only GET https://api.kite.trade/user/margins/equity in real mode; exact URI, no
query/fragment/userinfo/alternate host/port/path. Synthetic factory allows literal
loopback only. Hard one-attempt budget consumed even on failure, shared by all
requests of the run; default disabled. No retry/redirect/pagination/auth challenge
follow-up. Existing URLConnection automatic recovery is not sufficient evidence
for a strict no-retry guarantee: use a narrowly scoped request factory with an
HTTP client explicitly disabling retries, redirects, cookies and auth caching,
inside the existing KiteRestTransport/adapter (no second Kite service).
If needed, add Spring-Boot-managed Apache HttpClient5 dependency solely for these
controls. Preserve generic transport permissions/configuration and normal clients.
Bound connect/read to 10s/30s, response to existing 64 KiB wire/decoded limits,
TLS verification defaults intact, no wire logging. Recheck official docs and
primary HTTP-client documentation before implementation.

Authorized inventory: new KiteEquityReadHarness, KiteEquityReadRequestFactory,
KiteEquityReadIntegrity (broker infrastructure); narrow KiteRestTransport factory;
optional managed dependency in apps/trading-core/pom.xml; matching new unit and
integration tests and additive architecture guards. Four Phase 13.5 operations
documents. No application configuration, migrations, risk limits, cash/CNC policy,
authentication lifecycle, operator/preflight/dispatch, frozen research or Python edits.

## Schema, evidence and output

Verify official user/funds route and response docs. Current direct success/data
single-segment object is synthetic assumption; reject nested/extra segment
wrappers. Reuse mapper fields: enabled/net; available adhoc_margin/cash/
opening_balance/live_balance/collateral/intraday_payin; utilised debits/exposure/
m2m_realised/m2m_unrealised/option_premium/payout/span/holding_sales/turnover/
liquid_collateral/stock_collateral/delivery. Missing/null/type-invalid fails;
bounded unknown fields ignored, exact BigDecimal retained transiently.

Minimal result: bounded outcome, attempt count, local receipt/provenance, integrity
match and HALT-preserved booleans. Raw snapshot stays local; no account identifiers,
balances, credentials, headers, tokens, cookies or session IDs emitted. Internal
hashes/counts compare evidence; no raw rows or token fingerprints committed/logged.
Existing V1 caller-normalized projection preserves request/reference/freshness;
no instrument or executable quantity selected. All four required terms remain
UNKNOWN (public rule/mapping conflicts documented separately), NOT_READY.

## Synthetic acceptance matrix

Loopback HTTP + fixed/mutable clocks: default disabled zero requests; exact GET;
one request across repeat/concurrent attempts and response loss; wrong method/
path/host denied; redirect not followed; 401/403/429/5xx/timeout/connectivity;
strict malformed/duplicate/trailing/oversized JSON; missing/extra segment,
signed/zero/invalid decimals and unknown fields; high net/fully utilised and
contradictory cash remain unverified. Session replacement/loss, future/stale
receipt and HALT/DB state changes deny. No calculator/orders/profile/WS reachable.

Disposable PostgreSQL seeds before observation, observer SELECT-only + read-only
transaction; all success/error outcomes preserve token/trading tables and HALT.
Negative isolation cases reject before HTTP; simulated external changes are
disposable-only faults detected after response. Redaction and architecture guards
exclude execution, token lifecycle and startup capabilities. Reuse complete
existing cash/reserve/notional/CNC/preflight/reconciliation/freeze regressions.
No acceptance relaxation, no synthetic eligibility claims.

## Validation and stop conditions

Focused new harness/wire tests plus adapter, mapper, collateral and architecture;
full `./mvnw.cmd test`, full `./mvnw.cmd -Pintegration verify` because transport/
DB/architecture paths change. JDK21 wrapper only; deployment/JVM override names
removed only in child test processes as Phase13.4. No .env read. Python unchanged;
pytest/Ruff/mypy conditional only. Always uv run python scripts/verify-project.py,
uv run python scripts/check-secrets.py and git diff --check. Set
PYTHONDONTWRITEBYTECODE=1 and -B for hash tools. Record actual results and reruns.
Recheck G1 SHA, 18 sources, 63 protected artifacts, gate and plan SHA; exact Git
tracked/untracked inventory. No commit/push/discard. Stop on unexpected broker
network, secret disclosure, state write, protected drift or failed isolation.

## Stage B approval and retention

Default real budget ZERO. Only after Stage A passes, finish the concrete approval
document and request separate explicit user approval for exactly ONE equity GET,
zero other endpoints/retries. Absent approval: REAL_READ_AWAITING_APPROVAL, finish
Stage A. Even approval cannot waive session/isolation/independent process controls.
No normal startup, token exchange, HALT resume, permits or real orders ever.
Raw response transient only; close isolated HTTP/DB resources, release memory,
keep redacted report only. Do not clean user files or unrelated Docker resources.
