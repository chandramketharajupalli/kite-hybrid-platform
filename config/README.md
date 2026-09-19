# Configuration ownership

Java application*.yml files define development, test, paper and production profiles.
Profiles do not override safety defaults. TRADING_MODE and profiles are distinct.
The production profile requires DB_URL, DB_USER and DB_PASSWORD environment values.

.env.example documents variables; populated .env is ignored. Compose loads .env
for its own substitution. Spring Boot does NOT automatically load it. Export the
required variables in PowerShell before Maven `spring-boot:run` (see root README).
Python settings use STRATEGY_ prefix and do not receive Kite credentials.

Phase 2 adds KITE_REST_ENABLED=false and external KITE_API_KEY/KITE_ACCESS_TOKEN.
KITE_API_SECRET is reserved for later request-token exchange. Credentials are
validated only on explicit REST use; missing credentials do not break startup.
See docs/runbooks/kite-rest-diagnostic.md for interactive PowerShell input.
