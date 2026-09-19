# PostgreSQL

Authoritative future trading ledger. Flyway migrations belong in
apps/trading-core/src/main/resources/db/migration and must remain versioned.
V1 creates only the trading namespace. The local named volume preserves state.
Never use Redis as a fallback when durable state is unavailable.

The official `postgres:17.6` image maps `DB_NAME`, `DB_USER` and `DB_PASSWORD`
from local configuration to its `POSTGRES_*` initialization variables. A nonempty
password is required. These variables initialize an empty volume only; editing
`.env` does not rotate an existing database password. `pg_isready` checks server
readiness; the runbook separately checks an authenticated SQL connection.
