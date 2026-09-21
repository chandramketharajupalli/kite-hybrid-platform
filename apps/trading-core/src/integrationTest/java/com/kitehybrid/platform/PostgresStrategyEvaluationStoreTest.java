package com.kitehybrid.platform;

import com.kitehybrid.platform.order.domain.Signal;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.order.infrastructure.PostgresOrderRepository;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import com.kitehybrid.platform.strategy.application.*;
import com.kitehybrid.platform.strategy.infrastructure.PostgresStrategyEvaluationStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.TimeZone;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers @Isolated
class PostgresStrategyEvaluationStoreTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    private static final Instant NOW = Instant.parse("2026-09-21T05:00:00Z");
    private JdbcTemplate jdbc; private PostgresStrategyEvaluationStore store;
    @BeforeAll static void utc() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")); }
    @BeforeEach void setup() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.update("TRUNCATE trading.strategy_evaluations, trading.reconciliation_trades, trading.reconciliation_decisions, trading.risk_decisions, trading.orders, trading.order_idempotency");
        store = new PostgresStrategyEvaluationStore(jdbc);
    }
    @Test void claimIsReplaySafeAndOrderAttachmentIsIdempotent() {
        var signalId = new SignalId(UUID.randomUUID()); var strategy = new StrategyId("reference"); var instrument = new InstrumentId(UUID.randomUUID());
        var signal = new Signal(signalId, strategy, instrument, Signal.Side.BUY, 1, BigDecimal.TEN, NOW, "v1", Signal.Reason.BELOW_THRESHOLD);
        var evaluation = new StrategyEvaluation("event-1", strategy, "v1", signal, Optional.of(new OrderIntentId(UUID.randomUUID())), Optional.empty(), NOW);
        assertEquals(StrategyEvaluationStore.Claim.CREATED, store.claim(evaluation));
        assertEquals(StrategyEvaluationStore.Claim.EXISTING, store.claim(evaluation));
        var orderRecord = new OrderRecord(new OrderId(UUID.randomUUID()), new PlaceOrder("strategy:event-1", instrument, OrderSide.BUY, 1,
                OrderType.MARKET, OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0, OrderVariety.REGULAR),
                OrderState.VALIDATED, Optional.empty(), Optional.empty(), NOW, NOW, 1);
        var orderRepo = new PostgresOrderRepository(jdbc); orderRepo.createIfAbsent(orderRecord, "a".repeat(64));
        var order = orderRecord.id(); var completed = new StrategyEvaluation("event-1", strategy, "v1", signal, evaluation.intentId(), Optional.of(order), NOW);
        assertTrue(store.attachOrder(evaluation, completed)); assertTrue(store.attachOrder(evaluation, completed));
        assertEquals(Optional.of(order), store.find("event-1", "reference", "v1").orElseThrow().orderId());
    }
}
