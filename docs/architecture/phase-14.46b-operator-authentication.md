# Trading API authentication boundary

Three trust domains remain separate:

1. HTTP operator authentication identifies an application user and maps approved server-side permissions.
2. Governance owner authorization approves exact implementation/runtime scope using independently verified authority, time, revocation and replay controls.
3. Kite authentication establishes broker access; it grants neither operator permissions nor governance authority.

Future flow: standard verified operator authenticator -> endpoint authorization -> read-only projection service -> existing verified local snapshot/evidence. No fallback to broker session, loopback identity, user headers or feature flags. Scope approval is not an HTTP credential; an HTTP login is not implementation approval.

Preserve TradingCapabilityProjection.v1/strategy/instrument/qualification versioned envelopes with source hash, provenance, stable IDs, supported as-of timestamps and explicit unavailable states. Do not manufacture freshness from request time. Instruments reuse InstrumentRegistry snapshot; require identity lineage/source metadata before exposure, omit token if unavailable or disallowed. No automatic registry refresh or second registry.

Strategy catalog is source metadata only: verified RSI/EMA, Bollinger, VWAP and ORB research definitions remain methodology/version-bound. MACD/Supertrend/volume-breakout are NOT_IMPLEMENTED or UNVERIFIED unless implementation evidence is found; no Python execution for catalog generation.

Qualification metadata must retain instrument identity, strategy/timeframe/methodology, sample period, trade count, win rate, drawdown, cost assumptions and evidence fingerprint. Missing genuine evidence means unavailable count, not zero. Synthetic results never qualify, and70% historical win rate alone grants no trading permission. Do not merge incompatible families or create a parallel research store.

Current broker diagnostic providers are unsuitable for no-broker projections. Existing Java order/risk/reconciliation services stay untouched. Account projections cannot imply successful empty portfolios when storage/evidence is unavailable. Future audit events are separately scoped metadata; no domain mutation through GET.

Governance owner trust, trusted time, revocation and independent replay authority remain unverified. No custom cryptographic authentication, real keys or accounts. Live execution remains disabled.
