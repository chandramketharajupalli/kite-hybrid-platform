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
