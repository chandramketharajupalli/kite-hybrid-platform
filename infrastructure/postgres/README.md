# PostgreSQL

Authoritative future trading ledger. Flyway migrations belong in
apps/trading-core/src/main/resources/db/migration and must remain versioned.
V1 creates the trading namespace. V2 adds Kite access tokens encrypted with
AES-256-GCM, keyed by configured API application and accompanied by issue/expiry
metadata. KITE_TOKEN_ENCRYPTION_KEY remains outside the database. Keep that key
and the local named volume to reuse valid tokens after an application restart.
Never use Redis as a fallback when durable state is unavailable.

The official `postgres:17.6` image maps `DB_NAME`, `DB_USER` and `DB_PASSWORD`
from local configuration to its `POSTGRES_*` initialization variables. A nonempty
password is required. These variables initialize an empty volume only; editing
`.env` does not rotate an existing database password. `pg_isready` checks server
readiness; the runbook separately checks an authenticated SQL connection.
