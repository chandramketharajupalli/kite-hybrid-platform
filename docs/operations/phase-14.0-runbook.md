# Phase 14.0 offline runbook

From repository root, use the existing JDK21 and offline uv environment. Do not
load .env, run development infrastructure helpers, authenticate, restore a token,
connect a WebSocket or start the trading application for this phase.

```powershell
$env:PYTHONDONTWRITEBYTECODE = '1'
uv run --offline --project apps/strategy-engine python scripts/research/run_phase140.py
uv run --offline --project apps/strategy-engine pytest apps/strategy-engine/tests
uv run --offline --project apps/strategy-engine ruff check apps/strategy-engine/src apps/strategy-engine/tests
uv run --offline --project apps/strategy-engine mypy apps/strategy-engine/src apps/strategy-engine/tests
.\mvnw.cmd -pl apps/trading-core test
.\mvnw.cmd -Pintegration '-DskipUnitTests=true' verify
uv run --offline python scripts/verify-project.py
uv run --offline python scripts/check-secrets.py
```

Integration uses Testcontainers-created disposable PostgreSQL and synthetic/mock
HTTP only. It does not use Compose/shared databases or read live credentials.
No automatic JDK/Docker install or machine-wide settings changes. Tests do not
silently skip missing infrastructure. PYTHONDONTWRITEBYTECODE prevents updates to
a pre-existing tracked Python cache artifact; generated test output stays under target.

Generator validates all supplied hashes, reconciles all 25 physical duplicate lines,
copies exact canonical bytes to config/universes, emits unresolved rows, null-result
pair summaries, schemas, dry-run estimates and a short clearly simulated example.
It refuses to overwrite a differing canonical copy. It has no broker or DB dependency.
Do not present its missing-history or simulated rows as actual six/twelve-month returns.

For future approved offline candle inputs, Java HistoricalResearchExporter emits
HistoricalResearchDataset.five-minute.v1. Python phase14.imports validates each
chunk; compose requires explicit hashed calendars including non-trading dates,
master hash and corporate-action review. phase14.reporting.evaluate_pairs requires
predeclared windows, OOS folds, exact strategy definitions, cash/slippage/fee policy
and all universe members. Missing pair inputs are retained as rejections.

Inspect evidence manifest and validation report before any next phase. Frozen
research/governance audits must pass; use registered universal-newline normalization
only in memory. Never mutate historical evidence. No commit or push is authorized.
