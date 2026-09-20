# Permanent engineering rules

- Java owns the trading/control plane; Python owns strategy/quant research and emits signals only.
- Python must not authenticate for trading, execute/modify/cancel broker orders, own authoritative orders/positions, or bypass Java risk.
- PostgreSQL is authoritative durable trading state. Redis is ephemeral only.
- Risk approval is required before execution. Errors fail closed.
- Default to PAPER, ENABLE_LIVE_TRADING=false, EMERGENCY_STOP=true. Phase 1 has no live adapter.
- Domain types must not depend on infrastructure, Spring, or broker SDKs.
- Use versioned language-independent contracts; transport and encoding are separate decisions.
- Use BigDecimal/Decimal for money, integral instrument units for quantity, UTC timestamps and injected clocks.
- Never commit secrets. Do not print discovered credentials.
- Remain modular first. Justify every new infrastructure dependency.
- Accompany critical domain behavior with meaningful tests. Never claim unexecuted checks passed.
- Do not install a JDK or Docker automatically or change global JAVA_HOME.
- Keep unit tests independent of Docker; run integration tests explicitly.
- Phase 4 permits the existing official interactive Kite authentication, read-only profile/instrument GETs, and explicitly enabled market-data WebSocket streaming. No order placement/modification/cancellation, strategies, signals, risk decisions, position management, P&L or automated trading in market-data work.
- Market data must use the existing authenticated KiteSession, remain disabled by default, and never start real broker streaming in automated tests. Live diagnostics require explicit user action.
- Never run real Kite diagnostics as part of normal tests; report mock and real validation separately.
