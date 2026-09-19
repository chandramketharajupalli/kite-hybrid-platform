# PostgreSQL

Authoritative future trading ledger. Flyway migrations belong in
apps/trading-core/src/main/resources/db/migration and must remain versioned.
V1 creates only the trading namespace. The local named volume preserves state.
Never use Redis as a fallback when durable state is unavailable.
