package com.kitehybrid.platform;

import java.sql.DriverManager;
import java.util.TimeZone;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Isolated("Temporarily sets the JVM default timezone for PostgreSQL JDBC startup")
class PostgresMigrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    private static TimeZone originalTimeZone;

    @BeforeAll static void useUtcForJdbcStartup() {
        // pgJDBC sends the JVM default as a separate startup parameter; URL options cannot override it.
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll static void restoreTimeZone() {
        if (originalTimeZone != null) {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test void v9UpgradePreservesNullsAndCreatesAUniquePartialIndex() throws Exception {
        var jdbc = migrationDatabase("rc_upgrade");
        seedOrder(jdbc, "null-one", null); seedOrder(jdbc, "null-two", null);
        seedOrder(jdbc, "known", "synthetic-broker");
        var flyway = Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM trading.orders", Integer.class));
        seedOrder(jdbc, "null-three", null);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> seedOrder(jdbc, "duplicate", "synthetic-broker"));
        assertTrue(jdbc.queryForObject("SELECT indisunique AND indisvalid FROM pg_index WHERE indexrelid='trading.orders_broker_order_id_unique'::regclass", Boolean.class));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM trading.orders WHERE broker_order_id IS NULL", Integer.class));
    }

    @Test void v10DuplicateHistoryFailsWithoutChangingRowsOrSchemaVersion() throws Exception {
        var jdbc = migrationDatabase("rc_duplicate_history");
        seedOrder(jdbc, "historical-one", "same-historical-id");
        seedOrder(jdbc, "historical-two", "same-historical-id");
        var before = jdbc.queryForList("SELECT * FROM trading.orders ORDER BY idempotency_key");
        var flyway = Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration").load();
        assertThrows(org.flywaydb.core.api.FlywayException.class, flyway::migrate);
        assertEquals(before, jdbc.queryForList("SELECT * FROM trading.orders ORDER BY idempotency_key"));
        assertEquals("9", flyway.info().current().getVersion().toString());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='10'", Integer.class));
        assertNull(jdbc.queryForObject("SELECT to_regclass('trading.orders_broker_order_id_unique')", String.class));
    }

    private org.testcontainers.containers.Container.ExecResult preflight(String database) throws Exception {
        var script = java.nio.file.Path.of("../../scripts/v10-preflight.sql").toAbsolutePath().normalize();
        assertTrue(java.nio.file.Files.isRegularFile(script));
        postgres.copyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath(script), "/tmp/v10-preflight.sql");
        return postgres.execInContainer("psql", "-X", "-U", postgres.getUsername(), "-d", database,
                "-v", "ON_ERROR_STOP=1", "-f", "/tmp/v10-preflight.sql");
    }

    @Test void readOnlyPreflightAllowsCleanV9AndValidatedV10() throws Exception {
        var jdbc = migrationDatabase("operator_preflight_clean");
        seedOrder(jdbc,"null-a",null); seedOrder(jdbc,"null-b",null);
        var before=jdbc.queryForList("SELECT * FROM trading.orders ORDER BY idempotency_key");
        var result=preflight("operator_preflight_clean");
        assertEquals(0,result.getExitCode(),result.getStdout()+result.getStderr());
        assertTrue(result.getStdout().contains("V10_PREFLIGHT=READY"));
        assertEquals(before,jdbc.queryForList("SELECT * FROM trading.orders ORDER BY idempotency_key"));
        assertEquals("9",jdbc.queryForObject("SELECT version FROM public.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",String.class));
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration").load().migrate();
        assertEquals(0,preflight("operator_preflight_clean").getExitCode());
    }

    @Test void readOnlyPreflightStopsOnDuplicateHistoryWithoutRepair() throws Exception {
        var jdbc=migrationDatabase("operator_preflight_duplicates");
        seedOrder(jdbc,"one","duplicate"); seedOrder(jdbc,"two","duplicate");
        var before=jdbc.queryForList("SELECT * FROM trading.orders ORDER BY idempotency_key");
        assertEquals(3,preflight("operator_preflight_duplicates").getExitCode());
        assertEquals(before,jdbc.queryForList("SELECT * FROM trading.orders ORDER BY idempotency_key"));
    }

    @Test void readOnlyPreflightStopsOnConflictingIndexName() throws Exception {
        var jdbc=migrationDatabase("operator_preflight_conflict");
        jdbc.execute("CREATE INDEX orders_broker_order_id_unique ON trading.orders(quantity)");
        assertEquals(3,preflight("operator_preflight_conflict").getExitCode());
    }

    @Test void readOnlyPreflightRejectsInvalidConstraintAndWrongV10Index() throws Exception {
        var jdbc=migrationDatabase("operator_preflight_constraint");
        jdbc.execute("ALTER TABLE trading.orders ADD CONSTRAINT unsafe_history CHECK(quantity>0) NOT VALID");
        assertEquals(3,preflight("operator_preflight_constraint").getExitCode());
        jdbc.execute("ALTER TABLE trading.orders VALIDATE CONSTRAINT unsafe_history");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration").load().migrate();
        jdbc.execute("DROP INDEX trading.orders_broker_order_id_unique");
        jdbc.execute("CREATE UNIQUE INDEX orders_broker_order_id_unique ON trading.orders(order_id)");
        assertEquals(3,preflight("operator_preflight_constraint").getExitCode());
        var operational = new com.kitehybrid.platform.operator.infrastructure.PostgresOperationalReadiness(jdbc,()->true);
        assertFalse(operational.inspect().databaseReady());
    }

    @Test void customBackupCanBeListedRestoredAndValidatedInAnIsolatedDatabase() throws Exception {
        var jdbc=migrationDatabase("operator_backup_source");
        seedOrder(jdbc,"backup-proof",null);
        assertEquals(0,postgres.execInContainer("pg_dump","-U",postgres.getUsername(),"-d","operator_backup_source",
                "--format=custom","--file=/tmp/operator-backup.dump").getExitCode());
        assertEquals(0,postgres.execInContainer("pg_restore","--list","/tmp/operator-backup.dump").getExitCode());
        assertEquals(0,postgres.execInContainer("createdb","-U",postgres.getUsername(),"operator_backup_restore").getExitCode());
        var restore=postgres.execInContainer("pg_restore","-U",postgres.getUsername(),"--dbname=operator_backup_restore",
                "--exit-on-error","--single-transaction","/tmp/operator-backup.dump");
        assertEquals(0,restore.getExitCode(),restore.getStderr());
        assertEquals(0,preflight("operator_backup_restore").getExitCode());
        var restored=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                postgres.getJdbcUrl().replace("/"+postgres.getDatabaseName(),"/operator_backup_restore"),postgres.getUsername(),postgres.getPassword()));
        assertEquals(jdbc.queryForList("SELECT * FROM trading.orders"),restored.queryForList("SELECT * FROM trading.orders"));
        Flyway.configure().dataSource(restored.getDataSource()).locations("classpath:db/migration").target("9").load().validate();
    }

    private org.springframework.jdbc.core.JdbcTemplate migrationDatabase(String name) throws Exception {
        // Separate disposable database per scenario; never clean or alter the developer's ledger.
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + name), postgres.getUsername(), postgres.getPassword());
        assertEquals(9, Flyway.configure().dataSource(source).locations("classpath:db/migration").target("9").load().migrate().migrationsExecuted);
        return new org.springframework.jdbc.core.JdbcTemplate(source);
    }

    private void seedOrder(org.springframework.jdbc.core.JdbcTemplate jdbc, String key, String brokerId) {
        jdbc.update("""
                INSERT INTO trading.orders(order_id,idempotency_key,instrument_id,side,quantity,order_type,
                product,validity,disclosed_quantity,variety,state,broker_order_id,created_at,updated_at,version)
                VALUES(gen_random_uuid(),?,gen_random_uuid(),'BUY',1,'MARKET','DELIVERY','DAY',0,'REGULAR',
                'SUBMITTED',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)
                """, key, brokerId);
    }

    @Test void baselineMigratesAndCanBeValidatedOnRestart() throws Exception {
        var previous = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()).locations("classpath:db/migration").target("8").load();
        assertEquals(8, previous.migrate().migrationsExecuted);
        previous.validate();
        var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()).locations("classpath:db/migration").load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
             var query = connection.prepareStatement("SELECT schema_name, current_setting('TimeZone') AS session_timezone FROM information_schema.schemata WHERE schema_name = 'trading'");
             var rows = query.executeQuery()) {
            assertTrue(rows.next());
            assertEquals("UTC", rows.getString("session_timezone"));
        }
        for (var profile : java.util.List.of("development", "production")) {
        try (var context = new org.springframework.boot.builder.SpringApplicationBuilder(
                com.kitehybrid.platform.bootstrap.TradingCoreApplication.class)
                .web(org.springframework.boot.WebApplicationType.NONE).run(
                        "--spring.profiles.active=" + profile,
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--kite.rest-enabled=false", "--kite.order-execution.enabled=false",
                        "--kite.market-data.enabled=false", "--kite.trading-read.enabled=true")) {
            assertNotNull(context.getBean(com.kitehybrid.platform.order.application.OrderApplicationService.class));
            assertNotNull(context.getBean(com.kitehybrid.platform.order.application.ExecutionSafetyPolicy.class));
            assertEquals(com.kitehybrid.platform.order.application.ExecutionDenialReason.OPERATOR_CONTROL_DISABLED,
                    context.getBean(com.kitehybrid.platform.operator.application.OperatorExecutionService.class)
                            .arm(new com.kitehybrid.platform.shared.domain.Identifiers.OrderId(java.util.UUID.randomUUID()), java.time.Duration.ofSeconds(1)).reason());
            assertFalse(context.getBean(com.kitehybrid.platform.operator.application.LiveTestProperties.class).configured());
            assertNotNull(context.getBean(com.kitehybrid.platform.order.application.ExecutionAuthorizationAuditStore.class));
            assertNotNull(context.getBean(com.kitehybrid.platform.strategy.application.StrategyOrderCoordinator.class));
            assertNotNull(context.getBean(com.kitehybrid.platform.reconciliation.application.ReconciliationStore.class));
            assertNotNull(context.getBean(com.kitehybrid.platform.reconciliation.application.OrderReconciliationService.class));
            assertNotNull(context.getBean(com.kitehybrid.platform.risk.application.RiskService.class));
            assertNotNull(context.getBean(com.kitehybrid.platform.broker.application.auth.KiteAuthenticationSession.class));
            assertNotNull(context.getBean(com.kitehybrid.platform.instrument.application.InstrumentRegistry.class));
            assertNotNull(context.getBean(com.kitehybrid.platform.marketdata.application.MarketDataGateway.class));
            assertInstanceOf(com.kitehybrid.platform.order.infrastructure.DisabledOrderExecutionGateway.class,
                    context.getBean(com.kitehybrid.platform.order.application.OrderExecutionGateway.class));
            assertFalse(context.getBean(com.kitehybrid.platform.order.application.RuntimeExecutionArming.class)
                    .armed(java.time.Instant.now()));
            var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            var repository = context.getBean(com.kitehybrid.platform.order.application.OrderRepository.class);
            var key = "spring-transaction-" + profile;
            seedOrder(jdbc, key, null);
            var id = new com.kitehybrid.platform.shared.domain.Identifiers.OrderId(jdbc.queryForObject(
                    "SELECT order_id FROM trading.orders WHERE idempotency_key=?", java.util.UUID.class, key));
            var existing = repository.find(id).orElseThrow();
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> repository.createIfAbsent(existing, "a".repeat(64)));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM trading.order_idempotency WHERE idempotency_key=?", Integer.class, key));
            var transaction = new org.springframework.transaction.support.TransactionTemplate(
                    context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> {
                context.getBean(com.kitehybrid.platform.order.application.ExecutionAuthorizationAuditStore.class)
                        .record(new com.kitehybrid.platform.order.application.ExecutionAuthorizationDecision(false,
                                com.kitehybrid.platform.order.application.ExecutionDenialReason.DISARMED,
                                java.time.Instant.now(), id, existing.version(), "spring-rollback-test"));
                status.setRollbackOnly();
            });
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM trading.execution_authorizations WHERE order_id=?", Integer.class, id.value()));
        }
        }
    }
}
