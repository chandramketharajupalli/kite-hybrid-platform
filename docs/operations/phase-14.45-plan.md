# Phase 14.45 frozen implementation plan

Baseline: 1def1f2fab887f08bc109f2657fda80a385550a9, develop, equal to local origin/develop. No fetch. Preserve all 1,866 tracked files; Phase 14.22 remains unchanged and untracked. All changes are additive.

Phase E-Q manifest d25195c28bc71ab4b4c6b29c00e62ee36f5f21e5cc61b4f9693437d13f413b38, certificate 1e9b5280777f4aa3addeef2f5463e1fe5bdda23a9f7b0d01eed4131b8a84ca97, both receipt-backed replay fingerprints 839029e097e643860e9dab3a62fcb70b4a3b345485b90f20f9c69c5df227c1d8. The predecessor preservation preflight passes. Keep 73 historical unavailable receipts unavailable and 13 newline reconciliations unchanged.

## Exact additive inventory

- docs/operations/phase-14.45-plan.md
- docs/api/trading-v1.openapi.json
- docs/architecture/phase-14.45-trading-integration.md
- docs/operations/phase-14.45-validation.md
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/domain/TradingFoundationStatus.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingFoundationController.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingFoundationTest.java
- research/phase-14.45/generation-1/baseline-hashes.json
- research/phase-14.45/generation-1/validation-receipt.json
- research/phase-14.45/generation-1/evidence-manifest.json

## Implementation boundary

Freeze this plan and the OpenAPI specification before Java code. Implement only an opt-in GET /api/trading/status returning immutable foundation capabilities, not observed runtime health. It has no dependencies on accounts, orders, repositories, broker adapters, credentials or clocks. No sensitive data; no state changes. Default absence of trading.foundation-api.enabled leaves it unregistered. Enabling this metadata route grants no execution authority. JSON schema version is TradingApi.v1. Existing trading safety configuration remains untouched.

The remaining eleven routes are explicit design contracts, not registered handlers. Existing broker login is not independently established API operator authorization. Do not expose order, backtest or signal mutations until authentication, per-operation authorization, atomic idempotency and durable paper isolation are implemented and tested. No new engine, order model, broker adapter, database migration or dependency.

## Reuse and design

Java owns authoritative trading and PostgreSQL state. Reuse OrderApplicationService, OrderCommandValidator, OrderRecord, ExecutionSafetyPolicy, OrderReconciliationService, InstrumentRegistry and DisabledOrderExecutionGateway. Python emits SignalEvent.v1 only. Reuse phase1415 strategies, phase1417 research qualification and paper simulation as research contracts; no authoritative Python order ledger. Preserve Series v1 and all frozen methodology families. MACD, Supertrend and volume-breakout implementations are not verified and must remain unavailable candidates.

Document all nine strategy candidates, completed-candle timing, versioned evaluation configuration, dataset identity and corporate-action provenance, cost-adjusted qualification with 70% win rate and additional gates, Java paper persistence, restart, reconciliation and emergency stop. Preserve separate 46/18/31 workload exclusions.

## Validation and evidence

Use read-only Git/blob/hash maintenance for preservation. Run in-memory contract invariants in the available JavaScript tool runtime; no child process from those checks. Parse JSON and inspect new source bytes. Add focused JUnit tests but do not run Maven, pytest, Ruff or mypy through an unqualified runner. Java compilation, serialization and HTTP tests remain NOT_EXECUTED unless independently permitted; do not claim READY without them. Retain honest validation results in the allowlisted receipt. No external custody creation; predecessor custody remains read-only. Baseline map and final manifest use raw SHA-256; canonical manifest digest uses recursively key-sorted compact JSON, excluding its own fingerprint and the manifest file's raw hash.

Record plan SHA-256 before code; later changes require an additive amendment. Stop on preservation failure, unexpected modifications, secret access or prohibited workload. No live calls, infrastructure work, stage, commit or push. Rollback consists solely of a separately requested removal of these new artifacts; never alter history. Expected classification is PARTIAL while application-runtime validation and remaining API implementation are incomplete.
