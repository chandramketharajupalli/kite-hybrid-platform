# Configuration ownership

Java application*.yml files define development, test, paper and production profiles.
Profiles do not override safety defaults. TRADING_MODE and profiles are distinct.
The production profile requires DB_URL, DB_USER and DB_PASSWORD environment values.

.env.example documents variables; populated .env is ignored. Compose loads .env
for its own substitution. Spring Boot does NOT automatically load it. Export the
required variables in PowerShell before Maven `spring-boot:run` (see root README).
Python settings use STRATEGY_ prefix and do not receive Kite credentials.

KITE_REST_ENABLED defaults to false. Set it to true for official browser
authentication and profile/instrument initialization. The application uses
KITE_API_KEY, KITE_API_SECRET,
KITE_REDIRECT_URL=http://localhost:8080/api/broker/kite/auth/callback, and
KITE_TOKEN_ENCRYPTION_KEY (Base64-encoded 32 random bytes retained across restarts).
The development helper loads these from Compose's resolved environment without
printing them; process environment values take precedence over `.env`.
KITE_ACCESS_TOKEN is retained only for optional legacy standalone diagnostics.
Missing interactive authentication does not break application startup.
See [Kite Authentication](../README.md#kite-authentication) for setup and the
browser flow. Never commit populated credentials or encryption keys.
