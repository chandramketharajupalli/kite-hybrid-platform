package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.operator.application.*;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.risk.application.RiskService;
import com.kitehybrid.platform.broker.domain.read.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.kitehybrid.platform.broker.infrastructure.kite.OneOrderOperatorIntegrationTest.NOW;

/** SBIN identity with entirely synthetic prices/reference/account evidence and disposable PostgreSQL. */
@Testcontainers @Isolated
class SbinCandidateRehearsalIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = OneOrderOperatorIntegrationTest.POSTGRES;
    static final Instrument SBIN = Instrument.create(new BrokerInstrumentId("ZERODHA", "202"), "SBIN", "NSE", "CASH",
            InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);
    static final BigDecimal CAP = new BigDecimal("10000");
    static TimeZone zone;
    @BeforeAll static void utc() { zone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")); }
    @AfterAll static void restore() { TimeZone.setDefault(zone); }
    private OneOrderOperatorIntegrationTest.Fixture fixture() throws Exception {
        return new OneOrderOperatorIntegrationTest().new Fixture(SBIN, "risk.price-buffer=1.10",
                "kite.order-execution.max-quantity=100", "kite.live-test.max-quantity=100",
                "kite.order-execution.max-notional=10000", "kite.live-test.max-notional=10000");
    }
    private FirstLiveCandidatePlanner.Plan plan(OneOrderOperatorIntegrationTest.Fixture f, long holding) {
        f.market.update(new Tick(SBIN.id(), new BigDecimal("800"), NOW), f.publication);
        var headroom = FirstLiveCandidatePlanner.Headroom.from(new com.kitehybrid.platform.risk.domain.OrderRiskInput(
                f.registry.snapshot(), f.market.snapshot(Set.of(SBIN.id())), true, f.reads.positions(), f.reads.holdings(),
                f.reads.margins(), f.reads.orders()), f.context.getBean(com.kitehybrid.platform.risk.domain.RiskLimits.class),
                SBIN.id(), NOW, NOW, Duration.ofSeconds(5));
        var plan = new FirstLiveCandidatePlanner().plan("NSE", "SBIN", CAP, Duration.ofSeconds(30),
                f.context.getBean(OrderExecutionProperties.class), f.context.getBean(LiveTestProperties.class),
                new FirstLiveCandidatePlanner.Evidence(f.registry.snapshot(), f.market.latest(SBIN.id()).orElseThrow(),
                        f.health, Set.of(SBIN.id()), Set.of(SBIN.id()), true, headroom), NOW);
        assertEquals(FirstLiveCandidatePlanner.Status.SIZED_RECHECK_REQUIRED, plan.status());
        for (var table : List.of("orders", "risk_decisions", "execution_authorizations", "reconciliation_decisions", "strategy_evaluations"))
            assertEquals(0L, f.jdbc.queryForObject("SELECT count(*) FROM trading." + table, Long.class));
        assertTrue(f.operator.haltStatus().effectiveHalted());
        assertEquals(RuntimeExecutionArming.PermitState.NONE, f.operator.status().permitState()); f.assertCounts(0);
        return plan;
    }
    private void approve(OneOrderOperatorIntegrationTest.Fixture f, long quantity) {
        assertEquals("RESUME_SUCCESS DISARMED", f.operator.resume(f.operator.prepareResume()));
        f.id = f.application.place(new PlaceOrder("synthetic-sbin", SBIN.id(), OrderSide.BUY, quantity, OrderType.MARKET,
                OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0, OrderVariety.REGULAR)).id();
        assertTrue(f.orders.find(f.id).orElseThrow().brokerCorrelationId().isPresent());
        assertTrue(f.context.getBean(RiskService.class).evaluate(f.id).approved()); f.arm();
    }
    @ParameterizedTest @ValueSource(strings={"clean", "holding", "position"})
    void supportedAccountsReachReadyWithMaximumSafeSyntheticQuantity(String account) throws Exception {
        try (var f = fixture()) {
            long existing = account.equals("clean") ? 0 : 2;
            if (account.equals("holding")) when(f.reads.holdings()).thenReturn(List.of(new BrokerHolding(SBIN.id(), "SYNTHETIC", TradingReadTypes.Product.DELIVERY,
                    existing, 0, 0, 0, 0, 0, 0, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, Optional.empty())));
            if (account.equals("position")) {
                var position = mock(BrokerPosition.class);
                when(position.instrumentId()).thenReturn(SBIN.id()); when(position.quantity()).thenReturn(existing);
                when(position.product()).thenReturn(TradingReadTypes.Product.DELIVERY); when(position.multiplier()).thenReturn(BigDecimal.ONE);
                when(f.reads.positions()).thenReturn(new BrokerPositions(List.of(position), List.of(position)));
            }
            var p = plan(f, existing); assertEquals(existing == 0 ? 11 : 9, p.quantity());
            var evidencePath = java.nio.file.Path.of("target", "phase107", "sbin-synthetic-" + account + "-plan.txt");
            java.nio.file.Files.createDirectories(evidencePath.getParent());
            java.nio.file.Files.writeString(evidencePath, "SYNTHETIC ONLY; NOT A LIVE QUANTITY\nBUY MARKET DELIVERY DAY REGULAR\n" + p + "\n");
            approve(f, p.quantity());
            assertTrue(f.operator.preflight(f.id).ready());
            assertEquals(RuntimeExecutionArming.PermitState.UNUSED, f.operator.status().permitState()); f.assertCounts(0);
            assertEquals(OrderState.SUBMITTED, f.operator.execute(f.id).state()); f.assertCounts(1); f.consumed();
            assertTrue(f.form.get().contains("tradingsymbol=SBIN")); assertTrue(f.form.get().contains("product=CNC"));
            assertTrue(f.form.get().contains("validity=DAY")); assertTrue(f.form.get().contains("order_type=MARKET"));
        }
    }
    @ParameterizedTest @ValueSource(strings={"preflight", "dispatch"})
    void risingPriceNeverResizesApprovedOrderOrPosts(String fence) throws Exception {
        try (var f = fixture()) {
            var p = plan(f, 0); approve(f, p.quantity());
            Runnable rise = () -> f.market.update(new Tick(SBIN.id(), new BigDecimal("900"), NOW), f.publication);
            if (fence.equals("preflight")) rise.run(); else f.beforeDispatch = rise;
            assertThrows(RuntimeException.class, () -> f.operator.execute(f.id));
            assertEquals(11, f.orders.find(f.id).orElseThrow().command().quantity()); f.assertCounts(0); f.consumed();
        }
    }
    @ParameterizedTest @ValueSource(strings={"lot", "tick", "removed"})
    void referenceChangeAtTransportDeniesEvenWithSyntheticHealthyFeed(String change) throws Exception {
        try (var f = fixture()) {
            var p = plan(f, 0); approve(f, p.quantity());
            f.beforeDispatch = () -> f.registry.replace(List.of(change.equals("removed") ? OneOrderOperatorIntegrationTest.INSTRUMENT
                    : Instrument.create(SBIN.brokerId(), "SBIN", "NSE", "CASH", InstrumentType.CASH, Optional.empty(), Optional.empty(),
                    new BigDecimal(change.equals("tick") ? "0.10" : "0.05"), change.equals("lot") ? 11 : 1)), NOW);
            assertThrows(RuntimeException.class, () -> f.operator.execute(f.id)); f.assertCounts(0); f.consumed();
        }
    }
    @Test void increasedBufferInvalidatesImmutableApprovalAndSendsNothing() throws Exception {
        try (var f = fixture()) {
            var p = plan(f, 0); approve(f, p.quantity());
            var before = f.context.getBean(com.kitehybrid.platform.risk.application.RiskDecisionStore.class).find(f.id).orElseThrow();
            var other = f.boot(f.session, "risk.price-buffer=1.25", "kite.order-execution.max-quantity=100",
                    "kite.live-test.max-quantity=100", "kite.order-execution.max-notional=10000", "kite.live-test.max-notional=10000");
            var operator = other.getBean(OperatorExecutionService.class);
            assertEquals(ExecutionDenialReason.RISK_POLICY_MISMATCH,
                    operator.preflight(f.id).gates().get(ExecutionReadiness.Gate.RISK_DECISION_CURRENT));
            assertTrue(new BigDecimal("800").multiply(new BigDecimal("1.25")).multiply(BigDecimal.valueOf(p.quantity())).compareTo(CAP) > 0);
            assertThrows(RuntimeException.class, () -> operator.execute(f.id)); f.assertCounts(0);
            assertEquals(before, f.context.getBean(com.kitehybrid.platform.risk.application.RiskDecisionStore.class).find(f.id).orElseThrow());
        }
    }
    @ParameterizedTest @ValueSource(strings={"position", "open-order", "unreadable", "holding-discrepancy"})
    void unsupportedAccountEvidenceCannotBecomeRiskApproved(String change) throws Exception {
        try (var f = fixture()) {
            var p = plan(f, 0);
            switch (change) {
                case "position" -> {
                    var position = mock(BrokerPosition.class);
                    when(position.instrumentId()).thenReturn(SBIN.id()); when(position.quantity()).thenReturn(100L);
                    when(position.product()).thenReturn(TradingReadTypes.Product.DELIVERY); when(position.multiplier()).thenReturn(BigDecimal.ONE);
                    when(f.reads.positions()).thenReturn(new BrokerPositions(List.of(position), List.of()));
                }
                case "open-order" -> {
                    var order = mock(BrokerOrder.class); when(order.status()).thenReturn(TradingReadTypes.OrderStatus.OPEN);
                    when(f.reads.orders()).thenReturn(List.of(order));
                }
                case "unreadable" -> when(f.reads.margins()).thenThrow(new IllegalStateException("synthetic unavailable"));
                case "holding-discrepancy" -> {
                    var holding = mock(BrokerHolding.class); when(holding.instrumentId()).thenReturn(SBIN.id());
                    when(holding.discrepancy()).thenReturn(true); when(f.reads.holdings()).thenReturn(List.of(holding));
                }
                default -> fail(change);
            }
            assertEquals("RESUME_SUCCESS DISARMED", f.operator.resume(f.operator.prepareResume()));
            f.id = f.application.place(new PlaceOrder("unsafe-account", SBIN.id(), OrderSide.BUY, p.quantity(), OrderType.MARKET,
                    OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0, OrderVariety.REGULAR)).id();
            assertFalse(f.context.getBean(RiskService.class).evaluate(f.id).approved());
            assertFalse(f.operator.arm(f.id, Duration.ofSeconds(30)).armed());
            assertThrows(RuntimeException.class, () -> f.operator.execute(f.id)); f.assertCounts(0);
        }
    }
    @ParameterizedTest @ValueSource(strings={"missing-v10", "malformed-index", "duplicate-identity", "submitting", "ambiguous", "conflict"})
    void durableReadinessFailuresDenyAndConsumeUnusedPermit(String change) throws Exception {
        try (var f = fixture()) {
            var p = plan(f, 0); approve(f, p.quantity());
            switch (change) {
                case "missing-v10" -> f.jdbc.update("DELETE FROM public.flyway_schema_history WHERE version='10'");
                case "malformed-index" -> {
                    f.jdbc.execute("DROP INDEX trading.orders_broker_order_id_unique");
                    f.jdbc.execute("CREATE INDEX orders_broker_order_id_unique ON trading.orders(broker_order_id)");
                }
                case "duplicate-identity", "submitting" -> {
                    var other = f.application.place(new PlaceOrder("other", SBIN.id(), OrderSide.BUY, 1, OrderType.MARKET,
                            OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0, OrderVariety.REGULAR));
                    if (change.equals("submitting")) f.jdbc.update("UPDATE trading.orders SET state='SUBMITTING' WHERE order_id=?", other.id().value());
                    else {
                        f.jdbc.execute("DROP INDEX trading.orders_broker_order_id_unique");
                        f.jdbc.execute("UPDATE trading.orders SET broker_order_id='synthetic-duplicate'");
                    }
                }
                case "ambiguous", "conflict" -> f.jdbc.update("INSERT INTO trading.reconciliation_decisions VALUES(gen_random_uuid(),?,'RISK_APPROVED',NULL,?,'ORDER_IDENTITY_CONFLICT',?,2)",
                        f.id.value(), change.toUpperCase(Locale.ROOT), java.sql.Timestamp.from(NOW));
                default -> fail(change);
            }
            assertFalse(f.operator.preflight(f.id).ready());
            assertEquals(RuntimeExecutionArming.PermitState.UNUSED, f.operator.status().permitState());
            assertThrows(RuntimeException.class, () -> f.operator.execute(f.id)); f.assertCounts(0); f.consumed();
        }
    }
    @Test void preflightNowRequiresCurrentAccountAfterRiskApproval() throws Exception {
        try (var f = fixture()) {
            var p = plan(f, 0); approve(f, p.quantity());
            when(f.reads.margins()).thenThrow(new IllegalStateException("synthetic unavailable"));
            assertFalse(f.operator.preflight(f.id).ready()); f.assertCounts(0);
            f.operator.disarm();
        }
    }
}
