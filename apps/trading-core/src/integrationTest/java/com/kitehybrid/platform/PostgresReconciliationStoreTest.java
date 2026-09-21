package com.kitehybrid.platform;

import com.kitehybrid.platform.broker.domain.read.BrokerTrade;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.order.infrastructure.PostgresOrderRepository;
import com.kitehybrid.platform.reconciliation.domain.*;
import com.kitehybrid.platform.reconciliation.infrastructure.PostgresReconciliationStore;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.TimeZone;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers @Isolated
class PostgresReconciliationStoreTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    private static final Instant NOW = Instant.parse("2026-09-21T05:00:00Z");
    private static TimeZone original;
    private JdbcTemplate jdbc; private PostgresOrderRepository orders; private PostgresReconciliationStore store;
    @BeforeAll static void utc() { original = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")); }
    @AfterAll static void restore() { TimeZone.setDefault(original); }
    @BeforeEach void setup() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration").load().migrate();
        DataSource source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source); orders = new PostgresOrderRepository(jdbc); store = new PostgresReconciliationStore(jdbc);
        jdbc.update("TRUNCATE trading.strategy_evaluations, trading.reconciliation_trades, trading.reconciliation_decisions, trading.risk_decisions, trading.orders, trading.order_idempotency");
    }
    @Test void auditAndLifecycleAreAtomicAndTradeObservationIsDeduplicated() {
        var record = create(); var next = record.transitionTo(OrderState.OPEN, NOW);
        var trade = new BrokerTrade("trade-1", "broker-1", Optional.empty(), record.command().instrumentId(), Side.BUY,
                Product.DELIVERY, 1, BigDecimal.TEN, NOW, Optional.of(NOW));
        var decision = new ReconciliationDecision(UUID.randomUUID(), record.id(), record.state(), Optional.of(next.state()),
                ReconciliationOutcome.ADVANCED, ReconciliationReason.STATE_ADVANCED, NOW, record.version());
        assertTrue(store.apply(record, next, decision, List.of(trade, trade)));
        assertEquals(OrderState.OPEN, orders.find(record.id()).orElseThrow().state());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trading.reconciliation_decisions", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trading.reconciliation_trades", Integer.class));
    }
    @Test void staleVersionDoesNotWriteAnAudit() {
        var record = create(); var next = record.transitionTo(OrderState.OPEN, NOW);
        var changed = record.transitionTo(OrderState.OPEN, NOW); assertTrue(orders.compareAndSet(record, changed));
        var decision = new ReconciliationDecision(UUID.randomUUID(), record.id(), record.state(), Optional.of(next.state()),
                ReconciliationOutcome.ADVANCED, ReconciliationReason.STATE_ADVANCED, NOW, record.version());
        assertFalse(store.apply(record, next, decision, List.of()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM trading.reconciliation_decisions", Integer.class));
    }
    @Test void brokerCorrelationIsUniqueAndHistoricalNullRemainsAllowed() {
        var correlation = new com.kitehybrid.platform.shared.domain.BrokerCorrelationId("0123456789abcdef0123");
        var first = createWithCorrelation(correlation);
        var command = new PlaceOrder("duplicate-" + UUID.randomUUID(), first.command().instrumentId(), OrderSide.BUY, 1,
                com.kitehybrid.platform.order.domain.command.OrderType.MARKET, OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0, OrderVariety.REGULAR);
        var duplicate = new OrderRecord(new OrderId(UUID.randomUUID()), command, OrderState.SUBMITTED,
                Optional.of("broker-2"), Optional.of(correlation), Optional.empty(), NOW, NOW, 1);
        assertThrows(RuntimeException.class, () -> orders.createIfAbsent(duplicate, "b".repeat(64)));
    }
    private OrderRecord create() {
        var command = new PlaceOrder("reconcile-" + UUID.randomUUID(), new InstrumentId(UUID.randomUUID()), OrderSide.BUY, 1,
                com.kitehybrid.platform.order.domain.command.OrderType.MARKET, OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0, OrderVariety.REGULAR);
        var record = new OrderRecord(new OrderId(UUID.randomUUID()), command, OrderState.SUBMITTED, Optional.of("broker-1"), Optional.empty(), NOW, NOW, 1);
        assertEquals(com.kitehybrid.platform.order.application.OrderRepository.IdempotencyClaim.CREATED, orders.createIfAbsent(record, "a".repeat(64)));
        return record;
    }
    private OrderRecord createWithCorrelation(com.kitehybrid.platform.shared.domain.BrokerCorrelationId correlation) {
        var command = new PlaceOrder("correlation-" + UUID.randomUUID(), new InstrumentId(UUID.randomUUID()), OrderSide.BUY, 1,
                com.kitehybrid.platform.order.domain.command.OrderType.MARKET, OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0, OrderVariety.REGULAR);
        var record = new OrderRecord(new OrderId(UUID.randomUUID()), command, OrderState.SUBMITTED,
                Optional.of("broker-1"), Optional.of(correlation), Optional.empty(), NOW, NOW, 1);
        assertEquals(com.kitehybrid.platform.order.application.OrderRepository.IdempotencyClaim.CREATED, orders.createIfAbsent(record, "c".repeat(64)));
        return record;
    }
}
