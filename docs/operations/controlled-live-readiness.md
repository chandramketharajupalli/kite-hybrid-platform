# Controlled live readiness

**NO REAL ORDER IS SENT BY THIS PHASE.** Defaults remain disabled. This is a future operator procedure, not authorization to trade. No production instrument or execution cap has been selected.

For disposable test-only verification that stops at `preflight(OrderId)`, use the
[operator preflight dry-run harness](../runbooks/operator-preflight-dry-run.md).
Its synthetic READY result does not authorize live trading or invoke execution.

## Hard stop and operator boundary

Strategy → signal → trade intent → VALIDATED → RiskService → RISK_APPROVED → **STOP**.
Only an explicit trusted in-process `OperatorExecutionService.execute(OrderId)` requests submission. It reloads the persisted order, checks readiness and delegates to `OrderApplicationService.executeRiskApproved`. The application repeats `ExecutionSafetyPolicy`, obtains account admission, persists SUBMITTING and validates again immediately before transport. The persisted command and correlation are authoritative; execute accepts no broker ID, symbol, instrument, quantity or price override.

There are no operator HTTP endpoints, browser mutation controls, batch operations, listeners, pending-order scans or execution schedulers. The explicit terminal host and its deployment prerequisites are documented in the [controlled one-order procedure](../runbooks/controlled-one-order-live-test.md). It invokes this service in the **same running JVM** and does not authorize live use by itself. Starting another JVM cannot arm the running instance. Do not improvise an unauthenticated HTTP bridge. HTTP method, Host, Origin, forwarded-header and CSRF rules do not apply to this service-only boundary; they require separate review if an HTTP transport is introduced.

`arm(OrderId, Duration)` requires operator opt-in, execution capability, a usable authenticated session, emergency stop OFF, configured first-live limits and a positive bounded duration. Denial revokes an existing arm. Its response contains only armed, armedAt, expiresAt and an enum reason. It executes nothing. `disarm()` is idempotent. Arming is memory-only, bound to exact OrderId/approved version and session, expires at its boundary instant and invalidates on session replacement. Arm requires all non-arm readiness gates to pass; every execute invocation disarms and an owning attempt consumes its one-use permit. Emergency stop always denies execution, including after arming. Restart creates a disarmed bean; persisted approvals stay stopped.

## Independent first-live configuration

| Environment setting | Default | Restriction |
|---|---|---|
| `KITE_OPERATOR_CONTROL_ENABLED` | false | Independent operator opt-in |
| `KITE_LIVE_TEST_ENABLED` | false | Independent first-live mode |
| `KITE_LIVE_TEST_ALLOWED_INSTRUMENTS` | empty | Explicit platform InstrumentId UUIDs, maximum five |
| `KITE_LIVE_TEST_MAX_QUANTITY` | 0 | Independent positive integral unit cap |
| `KITE_LIVE_TEST_MAX_NOTIONAL` | 0 | Independent positive BigDecimal cap |
| `KITE_LIVE_TEST_ARM_MAX_DURATION` | 0s | Positive duration ceiling, maximum 1h |

All normal `KITE_ORDER_EXECUTION_*` gates still apply. Effective instrument permission is the intersection of registry membership, normal execution allowlist and first-live allowlist. Universe membership grants no permission. Missing/zero values deny; negatives, malformed values, unsafe decimal precision/scale, more than five instruments or duration over an hour fail startup. Both notional caps use quantity × max(current tick price, persisted limit price if present), from the same generation-fenced market store evidence. This pre-trade bound cannot guarantee a market-order fill price. No profile is activated by this change.

## Readiness and auditing

`preflight(OrderId)` returns READY/NOT_READY and bounded per-gate reasons. Before arm, overall status is NOT_READY/DISARMED even when all other prerequisites pass. After arm, every gate must pass. Readiness is an observation, not a reservation or permission to bypass later checks.

The 24 gates cover capability, usable authentication, runtime arm, session binding, emergency stop, RISK_APPROVED state, current approved/version-matched/policy-matched risk, normal instrument permission, quantity, correlation, account-wide blocking exposure, connected/fully subscribed/healthy market data, tick receipt/exchange freshness, normal notional, operator opt-in, live-test mode, first-live instrument/quantity/notional/arm-duration limits, V10 database readiness, readable reconciliation tables, absence of recorded reconciliation conflicts and configured order/trade read ports.

Preflight performs local reads and bounded logging only. It never calls the broker, launches authentication, repairs reconciliation, writes authorization rows or transitions an order. Authentication means the existing locally verified session is currently usable; zero-request preflight cannot prove the broker has not revoked it remotely. Read capability means configured ports exist, not guaranteed network reachability. Verify actual broker reads explicitly in the eventual procedure.

Unavailable evidence fails closed. Historical AMBIGUOUS, CONFLICT, BROKER_ORDER_MISSING and BROKER_STATE_UNAVAILABLE reconciliation outcomes conservatively block account-wide, even after later observations. No automatic conflict-clear workflow is added; investigate and obtain a separately reviewed resolution. Account admission remains authoritative if exposure changes after a READY report.

Bounded structured logs record ARM_REQUEST, ARM_SUCCESS/ARM_DENIED, DISARM, PREFLIGHT_DENIED, EXECUTE_REQUEST, EXECUTE_DENIED, EXECUTE_ATTEMPT and explicit RECONCILE_REQUEST with enum reasons. Existing durable authorization auditing and bounded execution metrics remain in use. No credentials, tokens, account bodies or session identities are returned/logged by operator control. No high-cardinality metric labels are added.

## Backup is mandatory before V10 deployment

The application does **not** back up PostgreSQL. Stop every application instance and other database writers; keep them stopped through verification, preflight and deployment. Compose uses PostgreSQL 17.6, service `postgres`, and named volume `postgres-data`. A volume is not a backup.

Run from the repository in PowerShell. Provision an access-restricted, encrypted backup directory outside the database volume first. The archive contains trading state and encrypted session records; the globals file can contain password hashes. Do not print credentials or emit `docker compose config` into logs. Container environment variables supply the existing identity.

```powershell
function Assert-Exit { if ($LASTEXITCODE -ne 0) { throw 'Database procedure failed; STOP' } }
$backupDir = 'D:\secure-backups\kite-pre-v10'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --file=/tmp/kite-pre-v10.dump'
Assert-Exit
docker compose exec -T postgres sh -c 'pg_dumpall -U "$POSTGRES_USER" --globals-only --file=/tmp/kite-pre-v10-globals.sql'
Assert-Exit
docker compose cp postgres:/tmp/kite-pre-v10.dump "$backupDir\kite-pre-v10.dump"
Assert-Exit
docker compose cp postgres:/tmp/kite-pre-v10-globals.sql "$backupDir\kite-pre-v10-globals.sql"
Assert-Exit
Get-FileHash -Algorithm SHA256 "$backupDir\kite-pre-v10.dump", "$backupDir\kite-pre-v10-globals.sql" |
    Export-Csv -NoTypeInformation "$backupDir\sha256.csv"
docker compose exec -T postgres pg_restore --list /tmp/kite-pre-v10.dump
Assert-Exit
```

Listing is insufficient: restore into an isolated PostgreSQL container with no ports or network. Use a fresh container name. Never point verification at production. These commands reproduce the reported history in `trading`; the restore administrator needs an explicitly matched search path because its `$user` would otherwise select a different schema. For a deployment whose reviewed authoritative history is elsewhere, match that source schema instead. Never change the source search path merely to obtain READY.

```powershell
docker run -d --name kite-v10-restore-check --network none -e POSTGRES_HOST_AUTH_METHOD=trust postgres:17.6
Assert-Exit
docker exec kite-v10-restore-check pg_isready -U postgres
# Repeat pg_isready until exit 0 before continuing; stop if readiness never arrives.
Assert-Exit
docker cp "$backupDir\kite-pre-v10.dump" kite-v10-restore-check:/tmp/backup.dump
Assert-Exit
docker exec kite-v10-restore-check createdb -U postgres restore_check
Assert-Exit
docker exec kite-v10-restore-check pg_restore -U postgres --dbname=restore_check --no-owner --no-acl --exit-on-error --single-transaction /tmp/backup.dump
Assert-Exit
docker cp scripts/v10-preflight.sql kite-v10-restore-check:/tmp/v10-preflight.sql
Assert-Exit
docker exec -e 'PGOPTIONS=-c search_path=trading,public' kite-v10-restore-check psql -X -U postgres -d restore_check -v ON_ERROR_STOP=1 -v expected_history_schema=trading -f /tmp/v10-preflight.sql
Assert-Exit
$inventory = @'
SELECT current_schema() AS history_schema \gset
SELECT :'history_schema' AS authoritative_history_schema;
SELECT version,success FROM :"history_schema".flyway_schema_history ORDER BY installed_rank;
SELECT count(*) FROM trading.orders;
SELECT count(*) FROM trading.risk_decisions;
SELECT count(*) FROM trading.reconciliation_decisions;
SELECT count(*) FROM trading.reconciliation_trades;
'@
$inventory | docker exec -i -e 'PGOPTIONS=-c search_path=trading,public' kite-v10-restore-check psql -X -U postgres -d restore_check -v ON_ERROR_STOP=1 -f -
Assert-Exit
$inventory | docker compose exec -T postgres sh -c 'psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -f -'
Assert-Exit
```

Compare histories/counts while writers remain stopped; retain hashes, exit statuses and verification evidence. Check expected constraints/indexes. A pre-V10 restore should be V9: do not start the application and accidentally migrate it before comparison. Run Flyway validation using the matching deployed migrations against the restore. A restorable backup with duplicate history still blocks deployment: preserve it and investigate.

For recovery, keep writers stopped and provision a **new empty** PostgreSQL 17.6 database. Restore reviewed roles from the protected globals file as administrator (`psql -X -v ON_ERROR_STOP=1 -f ...`), then restore with `pg_restore --exit-on-error --single-transaction --dbname=<new-database> ...`. Retain ownership/ACLs for production; verification's `--no-owner --no-acl` remaps them only inside the isolated container. Resolve existing-role conflicts through review, never by ignoring errors. Verify data, history, permissions and Flyway checksums before changing application connections. Start disarmed with execution opt-ins disabled. Do not overwrite the original volume as an experiment. Remove the isolated container only after retaining evidence; retain protected archives per deployment policy.

## Read-only V10 preflight

Run before application startup, which automatically invokes Flyway:

```powershell
docker compose cp scripts/v10-preflight.sql postgres:/tmp/v10-preflight.sql
Assert-Exit
docker compose exec -T postgres sh -c 'psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v expected_history_schema=trading -f /tmp/v10-preflight.sql'
Assert-Exit
```

### Authoritative history contract

Application configuration leaves `spring.flyway.default-schema`, `spring.flyway.schemas` and the history table name unset. The datasource uses `DB_URL`/`DB_USER`; V1 only runs `CREATE SCHEMA IF NOT EXISTS trading`. It does **not** declare where Flyway keeps history. Without an explicit Flyway schema, the installed PostgreSQL driver resolves the default from `SELECT current_schema`. PostgreSQL selects the first existing, accessible schema in `search_path`.

For the reported role `trading`, existing schema `trading`, JDBC URL without `currentSchema`, and default `"$user", public` search path, `current_schema()` is `trading`. Thus **`trading.flyway_schema_history` is authoritative**. Public V1 must neither override it nor cause failure merely because another history exists. Other roles/search paths can legitimately use `public`; substituting a different hard-coded schema would break those deployments.

The script resolves the schema once in its read-only transaction and fully qualifies the selected history. It never chooses the highest version, merges histories or uses an unqualified lookup that could fall through to another table. Diagnostics show connection user/database/search path/current schema, selected history, and discovered history-table locations with an authoritative flag. Non-authoritative histories are never deleted or repaired.

Use the **same application login, database and effective search path**, not a convenient administrator login. The local command assumes Compose `POSTGRES_USER` equals application `DB_USER=trading`; otherwise use the actual application role. First check these read-only diagnostics:

```sql
SELECT current_database(), current_user, current_setting('search_path'), current_schema();
SELECT n.nspname AS history_schema, c.relname AS history_table
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE c.relname='flyway_schema_history' ORDER BY n.nspname;
```

Remote example for the reported deployment:

```powershell
psql -X -h <target> -U trading -d trading -v ON_ERROR_STOP=1 -v expected_history_schema=trading -f scripts/v10-preflight.sql
Assert-Exit
```

Use a protected password file or approved credential mechanism. Mirror JDBC `currentSchema`, connection initialization SQL and role/database search-path settings in psql. `expected_history_schema` is an assertion: mismatch fails; it never changes selection. Keep it set for deployment checks so a wrong connection cannot accidentally validate a different complete history.

If the application already has a Flyway override, pass `-v flyway_schema=<effective-schema>` (default-schema, otherwise the **first** schemas entry) and, if applicable, `-v flyway_table=<configured-table>`. These must reflect deployed settings; they are not repair switches. Runtime readiness reads these overrides from the application's Flyway configuration and denies if Flyway and the application's JdbcTemplate use different datasource instances. Separate credentials, pools or wrappers require review; independently configured connections are not assumed equivalent. Unknown effective connection/schema settings mean STOP, not a version-based guess.

### Why two histories may exist

The origin of the real public V1 row is **not proven**. Disposable tests demonstrate one plausible sequence without moving history: the `trading` role initially connects before its schema exists, so Flyway records V1 in `public`; V1 creates `trading`; a later connection resolves `$user` to `trading`, and a subsequent Flyway run creates history there and applies V1–V9. This reproduces the reported shape but does not establish the real deployment's history. Preserve both tables and investigate deployment records if provenance is needed.

### READY / NOT_READY

The repeatable-read READ ONLY transaction has a statement timeout and ends with ROLLBACK. V9 requires exactly one successful entry for each V1–V9, no failed/duplicate/unexpected version, latest installed version exactly 9, zero duplicate non-null broker IDs, validated order constraints and a free V10 index name.

Already-V10 requires successful V1–V10 without duplicate/failed/missing baseline entries, latest version exactly 10, the expected SQL migration record (`V10__unique_broker_order_identity.sql`, expected description), and the exact unique partial broker-ID index. The index must be valid and ready, with the expected single column, no expression/extra included column, and the non-null predicate. Duplicate broker IDs and unvalidated constraints still deny. Flyway schema-creation metadata rows are permitted; unrelated unversioned migrations are not.

Only `V10_PREFLIGHT=READY` **and exit 0** means prerequisites passed. NOT_READY, any SQL/connection error, missing output or nonzero exit means STOP. Missing authoritative history, missing/unusable schema, expected-schema mismatch and unsupported history relations never fall back to another table. PostgreSQL 17 does not support an exit-code argument to `\quit`, so deliberate denials raise a non-mutating SQL error under ON_ERROR_STOP (exit 3). No historical rows are repaired/deleted and broker-ID values are not printed. This check does not replace Flyway checksum validation. Keep writers stopped to prevent races before migration.

### Spring configuration decision

Production configuration remains unchanged. Globally pinning Flyway to `trading` would redirect installations with authoritative history in `public`, potentially treating a populated schema as unmigrated. That requires a separate deployment inventory and migration plan. Tests cover public-history clean install/upgrade, the discovered dual-history V9 preflight, and production Spring startup/restart upgrading **only trading history** to V10 inside a disposable database. Nothing here authorizes Spring startup or V10 migration against the real database.

## Eventual operator sequence

1. Stop writers, back up DB and prove restore.
2. Run V10 preflight; stop on conflicts.
3. Start the reviewed build; verify migrations and DISARMED.
4. Authenticate explicitly through the existing interactive flow.
5. Verify instrument registry identity/metadata.
6. Verify universe independently of execution allowlists.
7. Verify fresh generation-fenced market data and healthy subscriptions.
8. Create/identify one persisted RISK_APPROVED OrderId with correlation.
9. Run readiness; require all prerequisites except arm/session binding to pass. Overall is NOT_READY before arm.
10. Explicitly arm the exact OrderId for a bounded duration in the same JVM; confirm exact selection.
11. Run readiness again; require READY on every gate.
12. Separately confirm and explicitly execute that one OrderId once; never retry an ambiguous request.
13. Immediately disarm, including on error.
14. Explicitly reconcile.
15. Verify broker orders/trades and local state agree.
16. Stop on any ambiguity, conflict or missing evidence.

**Do not perform real mutation steps in this phase.** Automated tests use synthetic sessions/instruments and a loopback fake broker. A reviewed operator invocation host, explicit instrument/caps, verified production backup/schema, current session/market evidence and explicit live authorization remain deployment prerequisites.

## Single-account scope

One brokerage account per database/deployment is assumed. Broker-order uniqueness, PostgreSQL advisory/admission locks and reconciliation blockers are account-wide. Arm applies to one runtime/session; multiple application instances have independent ephemeral arms but share durable admission. Before multiple accounts/users: add authoritative account ownership to orders/risk/correlations/audits/reads, scope unique keys and locks by account, bind arms to account and authorized operator, partition evidence/blockers and add tenant isolation tests. Session UUID binding alone is not multi-account authorization.
