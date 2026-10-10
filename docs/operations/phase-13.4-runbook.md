# Phase 13.4 operator runbook

Default: synthetic only, real budget zero. The adapter is package-local, unwired
and disabled by default; there is no new endpoint or application property to
enable. Do not start the normal app for this diagnostic: startup could restore
authentication or write state. No `.env`, risk, reserve, CNC or safety edits.

## Safe synthetic validation

Use JDK 21, the repository Maven wrapper, available Docker and disposable
Testcontainers PostgreSQL. From the repository root, use a child PowerShell
process without deployment overrides; do not inspect/print secret values or load
`.env`. The registered plan lists the environment names removed for validation.
Tests intercept HTTP or use loopback fakes; they need no real Kite credentials.

```powershell
.\mvnw.cmd '-Dtest=KiteEquityMarginReadAdapterTest,TradingReadArchitectureTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
$env:PYTHONDONTWRITEBYTECODE='1'
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

Full integration uses existing isolated operator/auth/reconciliation fixtures;
fake permit/HALT/order exercises in those suites are not operational trading.
The new equity integration test uses a seeded synthetic encrypted token,
SELECT-only role and all-table integrity checks; it never starts the application.
No development database is used. Let Testcontainers close its own resources;
never stop/remove unrelated Docker containers or clear databases.

## Expected diagnostic and triage

| Result | Action |
| --- | --- |
| CONFIGURATION | Adapter disabled or transport/session mismatch. Keep default disabled outside an explicitly scoped harness. |
| AUTHENTICATION before HTTP | Missing, expired or unvalidated session. Synthetic fixture may be repaired; no real login/exchange is authorized. |
| 401/403 or valid TokenException | Stop. In-memory authentication becomes unavailable; durable token remains untouched. No refresh/reset/retry. |
| 429 or 5xx | Stop with bounded BROKER_API error. Preserve token; do not retry the proposed real read. |
| Timeout/connectivity | TRANSPORT; outcome uncertain, count the attempt and stop. |
| Malformed/duplicate/trailing/oversized/null/type-invalid body | INVALID_RESPONSE; no fallback endpoint and no missing-to-zero conversion. |
| Changed session/reader binding | Reject snapshot reuse; no reconciliation with another session. |
| Stale/future snapshot or mismatched request/reference | Existing V1 STALE/CONFLICTING context; do not approve. |
| Cash-only MIS_MARGIN_UNAVAILABLE | A single equity snapshot lacks full account/positions/orders context. It cannot approve funding. Existing complete synthetic cash-only equality remains a separate policy calculation. |
| Collateral NOT_READY | Expected: no independent eligible/free amount, exact cash rule or qualifying cash mapping. Observed net/utilised balances cannot close those gaps. |
| Phase 12 BLOCK | Preserve corporate-action certification denial; zero historical acquisition/evaluation. |

No diagnostic success grants execution, consumes a permit or clears HALT.
Reserve remains cash-only and margin/charges stay separate from full buffered
notional. The INR 10,000 ceiling and CNC policy are unchanged. No quantity selected.

## Optional separately approved real observation

Read the [exact one-GET proposal](phase-13.4-real-read-proposal.md). It is
UNAPPROVED / DENY. No runnable real command is supplied, and normal application
startup is unsuitable. Require explicit approval plus independently reviewed
one-shot no-write harness, verified HALT/live-disable and valid existing session
binding. No profile/calculation/orders/candles/WS or retry is included. If any
precondition needs a forbidden write or extra read, abort; approval is not a
waiver. The GET may still be insufficient for every collateral question.

For shutdown, stop only the isolated diagnostic process and release its private
memory/resources. Leave HALT and all durable state unchanged; report a bounded
error without upstream text. Do not repair tokens, change policies, arm or trade.
Next evidence step is the existing unsent [broker clarification](phase-13.3-broker-clarification.md),
requiring dated NSE MIS category/free-collateral/cash-field semantics.
