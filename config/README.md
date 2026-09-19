# Configuration ownership

Java application*.yml files define development, test, paper and production profiles.
Profiles do not override safety defaults. TRADING_MODE and profiles are distinct.
The production profile requires DB_URL, DB_USER and DB_PASSWORD environment values.

.env.example documents variables; populated .env is ignored. Compose loads .env
for its own substitution. Spring Boot does NOT automatically load it. Export the
required variables in PowerShell before bootRun (see root README).
Python settings use STRATEGY_ prefix and do not receive Kite credentials.
