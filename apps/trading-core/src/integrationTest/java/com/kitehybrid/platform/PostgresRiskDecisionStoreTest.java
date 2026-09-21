package com.kitehybrid.platform;

import com.kitehybrid.platform.order.application.OrderRepository;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.order.infrastructure.PostgresOrderRepository;
import com.kitehybrid.platform.risk.domain.*;
import com.kitehybrid.platform.risk.infrastructure.PostgresRiskDecisionStore;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.TimeZone;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Isolated
class PostgresRiskDecisionStoreTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    private JdbcTemplate jdbc;
    private PostgresOrderRepository orders;
    private PostgresRiskDecisionStore decisions;
    private static final Instant NOW = Instant.parse("2026-09-21T05:00:00Z");
    private static TimeZone originalTimeZone;

    @BeforeAll static void useUtcForJdbcStartup() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
    @AfterAll static void restoreTimeZone() {
        if (originalTimeZone != null) TimeZone.setDefault(originalTimeZone);
    }

    @BeforeEach void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        DataSource source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source); orders = new PostgresOrderRepository(jdbc);
        decisions = new PostgresRiskDecisionStore(jdbc, orders);
        jdbc.update("TRUNCATE trading.strategy_evaluations, trading.reconciliation_trades, trading.reconciliation_decisions, trading.risk_decisions, trading.orders, trading.order_idempotency");
    }

    @Test void approvedAndRejectedDecisionsAtomicallyTransitionAndSurviveReload() {
        var approved = create("approved");
        var result = decisions.evaluate(approved.id(), order -> approved(order));
        assertTrue(result.approved());
        assertEquals(OrderState.RISK_APPROVED, orders.find(approved.id()).orElseThrow().state());
        assertEquals(result, decisions.find(approved.id()).orElseThrow());

        var rejected = create("rejected");
        var denial = decisions.evaluate(rejected.id(), order -> rejected(order, RiskReason.ORDER_VALUE_LIMIT));
        assertFalse(denial.approved());
        assertEquals(OrderState.REJECTED, orders.find(rejected.id()).orElseThrow().state());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM trading.risk_decisions", Integer.class));
    }

    @Test void duplicateAndConcurrentSameOrderEvaluationHaveOneDecision() throws Exception {
        var record = create("duplicate");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Future<RiskDecision> a = pool.submit(() -> { start.await(); return decisions.evaluate(record.id(), this::approved); });
            Future<RiskDecision> b = pool.submit(() -> { start.await(); return decisions.evaluate(record.id(), this::approved); });
            start.countDown();
            assertEquals(a.get(), b.get());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trading.risk_decisions", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void accountRiskScopePreventsTwoOrdersFromConsumingTheSameBudget() throws Exception {
        var first = create("budget-a");
        var second = create("budget-b");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Future<RiskDecision> a = pool.submit(() -> { start.await(); return decisions.evaluate(first.id(), this::approved); });
            Future<RiskDecision> b = pool.submit(() -> { start.await(); return decisions.evaluate(second.id(), this::approved); });
            start.countDown();
            var results = java.util.List.of(a.get(), b.get());
            assertEquals(1, results.stream().filter(RiskDecision::approved).count());
            assertEquals(1, results.stream().filter(r -> r.reason() == RiskReason.CONCURRENT_EXPOSURE_UNAVAILABLE).count());
        } finally { pool.shutdownNow(); }
    }

    @Test void failedDecisionPersistenceRollsBackOrderTransition() {
        var record = create("rollback");
        jdbc.execute("ALTER TABLE trading.risk_decisions ADD CONSTRAINT test_reject_reason CHECK (reason <> 'ORDER_VALUE_LIMIT')");
        try {
            assertThrows(RuntimeException.class, () -> decisions.evaluate(record.id(), order ->
                    new RiskDecision(order.id(), order.version(), RiskDecision.Outcome.REJECTED,
                            RiskReason.ORDER_VALUE_LIMIT, NOW, "cash-v1:" + "1".repeat(64))));
            assertEquals(OrderState.VALIDATED, orders.find(record.id()).orElseThrow().state());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM trading.risk_decisions", Integer.class));
        } finally { jdbc.execute("ALTER TABLE trading.risk_decisions DROP CONSTRAINT test_reject_reason"); }
    }

    private OrderRecord create(String key) {
        var command = new PlaceOrder(key, new InstrumentId(UUID.randomUUID()), OrderSide.BUY, 1,
                OrderType.MARKET, OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0,
                OrderVariety.REGULAR);
        var record = new OrderRecord(new OrderId(UUID.randomUUID()), command, OrderState.VALIDATED,
                Optional.empty(), Optional.empty(), NOW, NOW, 1);
        assertEquals(OrderRepository.IdempotencyClaim.CREATED, orders.createIfAbsent(record, "a".repeat(64)));
        return record;
    }
    private RiskDecision approved(OrderRecord order) {
        return new RiskDecision(order.id(), order.version(), RiskDecision.Outcome.APPROVED,
                RiskReason.APPROVED, NOW, "cash-v1:" + "1".repeat(64));
    }
    private RiskDecision rejected(OrderRecord order, RiskReason reason) {
        return new RiskDecision(order.id(), order.version(), RiskDecision.Outcome.REJECTED,
                reason, NOW, "cash-v1:" + "1".repeat(64));
    }
}
