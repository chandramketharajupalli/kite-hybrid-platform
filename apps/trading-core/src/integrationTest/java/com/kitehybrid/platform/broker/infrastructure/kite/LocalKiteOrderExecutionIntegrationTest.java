package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.marketdata.infrastructure.InMemoryLatestMarketDataStore;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.order.infrastructure.PostgresOrderRepository;
import com.kitehybrid.platform.order.infrastructure.PostgresExecutionAuthorizationAuditStore;
import com.kitehybrid.platform.risk.application.RiskService;
import com.kitehybrid.platform.risk.application.RiskDecisionStore;
import com.kitehybrid.platform.risk.domain.*;
import com.kitehybrid.platform.risk.infrastructure.PostgresRiskDecisionStore;
import com.kitehybrid.platform.reconciliation.application.OrderReconciliationService;
import com.kitehybrid.platform.reconciliation.infrastructure.PostgresReconciliationStore;
import com.kitehybrid.platform.reconciliation.domain.ReconciliationOutcome;
import com.kitehybrid.platform.strategy.application.*;
import com.kitehybrid.platform.strategy.domain.*;
import com.kitehybrid.platform.strategy.infrastructure.PostgresStrategyEvaluationStore;
import com.kitehybrid.platform.config.TradingProperties;
import com.kitehybrid.platform.shared.domain.TradingMode;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.MarginSegment;
import static org.junit.jupiter.api.Assertions.*;

/** Full local execution path. The fake broker binds loopback and never forwards traffic. */
@Testcontainers
@Isolated
class LocalKiteOrderExecutionIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    private static final Instant NOW = Instant.parse("2026-09-21T05:00:00Z");
    private static final Instrument INSTRUMENT = Instrument.create(new BrokerInstrumentId("ZERODHA", "123"),
            "ABC", "NSE", "CASH", InstrumentType.CASH, Optional.empty(), Optional.empty(),
            new BigDecimal("0.05"), 1);
    private static TimeZone originalTimeZone;
    private LocalFakeKiteServer broker;
    private JdbcTemplate jdbc;
    private PostgresOrderRepository orders;
    private OrderApplicationService application;
    private RiskService risk;
    private InMemoryInstrumentRegistry registry;
    private InMemoryLatestMarketDataStore market;
    private MarketDataHealth marketHealth;
    private KiteSession session;
    private RuntimeExecutionArming arming;
    private RiskDecisionStore riskDecisions;
    private OrderExecutionProperties executionProperties;
    private SimpleMeterRegistry metrics;
    private AtomicBoolean emergencyStop;
    private KiteRestTransport transport;
    private KiteOrderAdapter executionGateway;
    private final java.util.concurrent.atomic.AtomicReference<Instant> operatorNow = new java.util.concurrent.atomic.AtomicReference<>(NOW);

    @BeforeAll static void utcJdbc() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
    @AfterAll static void restoreTimeZone() {
        if (originalTimeZone != null) TimeZone.setDefault(originalTimeZone);
    }
    @BeforeEach void setUp() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        DataSource source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.update("TRUNCATE trading.execution_authorizations, trading.strategy_evaluations, trading.reconciliation_trades, trading.reconciliation_decisions, trading.risk_decisions, trading.orders, trading.order_idempotency");
        orders = new PostgresOrderRepository(jdbc);
        broker = new LocalFakeKiteServer();
        broker.start();
        requireLoopback(URI.create(broker.baseUrl()));
        registry = new InMemoryInstrumentRegistry();
        registry.replace(List.of(INSTRUMENT), NOW);
        session = new KiteSession(new KiteProperties("syntheticKey", "syntheticSecret", "", true), Clock.fixed(NOW, ZoneOffset.UTC));
        session.install(new com.kitehybrid.platform.broker.application.auth.KiteAccessToken("syntheticToken", NOW.minusSeconds(1), NOW.plusSeconds(3600)));
        session.profileValidated();
        assertTrue(session.authenticated(), "synthetic local session must be authenticated");
        transport = new KiteRestTransport(RestClient.builder().baseUrl(broker.baseUrl()).build(), session);
        metrics = new SimpleMeterRegistry();
        market = new InMemoryLatestMarketDataStore();
        market.update(new Tick(INSTRUMENT.id(), new BigDecimal("10.00"), NOW));
        marketHealth = new MarketDataHealth(MarketDataGateway.State.CONNECTED, MarketDataHealth.Status.FRESH,
                MarketDataHealth.Reason.NONE, Optional.of(NOW), Optional.of(NOW), Optional.of(NOW), 1, 1, 0, 1, 1, 0, 0, 0, 0);
        var limits = new RiskLimits(true, 100, new BigDecimal("10000"), 100, new BigDecimal("10000"),
                Duration.ofMinutes(1), Duration.ofMinutes(1), BigDecimal.ONE, BigDecimal.ONE);
        riskDecisions = new PostgresRiskDecisionStore(jdbc, orders);
        risk = riskService(orders, registry, riskDecisions, market, limits);
        executionProperties = new OrderExecutionProperties(true, Set.of(INSTRUMENT.id()), 1,
                new BigDecimal("1000"), Duration.ofMinutes(1), Duration.ofMinutes(1), limits, "phase9-test");
        emergencyStop = new AtomicBoolean(false);
        arming = new RuntimeExecutionArming(metrics, session::executionIdentity);
        arming.arm(Duration.ofHours(1), NOW);
        var policy = new ExecutionSafetyPolicy(executionProperties, arming, emergencyStop::get, session, riskDecisions,
                registry, market, () -> marketHealth, orders, Clock.fixed(NOW, ZoneOffset.UTC), metrics,
                new PostgresExecutionAuthorizationAuditStore(jdbc), RehearsalIsolation.accounts(executionProperties,registry,market,Clock.fixed(NOW,ZoneOffset.UTC)), () -> true);
        var gateway = org.mockito.Mockito.spy(new KiteOrderAdapter(transport, registry, executionProperties));
        executionGateway = gateway;
        application = new OrderApplicationService(orders, new com.kitehybrid.platform.order.application.OrderCommandValidator(registry),
                gateway, executionProperties, Clock.fixed(NOW, ZoneOffset.UTC), metrics, policy);
    }
    @AfterEach void tearDown() { if (broker != null) broker.close(); }

    private org.springframework.context.annotation.AnnotationConfigApplicationContext operatorContext(String... overrides) {
        var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        org.springframework.boot.test.util.TestPropertyValues.of(
                "kite.order-execution.enabled=true", "kite.order-execution.allowed-instruments=" + INSTRUMENT.id().value(),
                "kite.order-execution.max-quantity=1", "kite.order-execution.max-notional=1000",
                "kite.order-execution.risk-decision-max-age=1m", "kite.order-execution.market-data-max-age=5s",
                "kite.operator-control.enabled=true", "kite.live-test.enabled=true",
                "kite.live-test.allowed-instruments=" + INSTRUMENT.id().value(),
                "kite.live-test.max-quantity=1", "kite.live-test.max-notional=20", "kite.live-test.arm-max-duration=30s")
                .applyTo(context);
        org.springframework.boot.test.util.TestPropertyValues.of(overrides).applyTo(context);
        context.registerBean(JdbcTemplate.class, () -> jdbc);
        context.registerBean(Clock.class, () -> {
            var clock = org.mockito.Mockito.mock(Clock.class);
            org.mockito.Mockito.when(clock.instant()).thenAnswer(call -> operatorNow.get());
            return clock;
        });
        context.registerBean(io.micrometer.core.instrument.MeterRegistry.class, () -> metrics);
        context.registerBean(com.kitehybrid.platform.broker.application.auth.KiteAuthenticationSession.class, () -> session);
        context.registerBean(com.kitehybrid.platform.instrument.application.InstrumentRegistry.class, () -> registry);
        context.registerBean(LatestMarketDataStore.class, () -> market);
        context.registerBean(MarketDataGateway.class, () -> {
            var gateway = org.mockito.Mockito.mock(MarketDataGateway.class);
            org.mockito.Mockito.when(gateway.health()).thenAnswer(call -> marketHealth);
            return gateway;
        });
        context.registerBean(com.kitehybrid.platform.shared.application.ExecutionInitialization.class, () -> () -> true);
        context.registerBean("syntheticAccountChecks", AccountExecutionChecks.class, () -> RehearsalIsolation.accounts(context.getBean(OrderExecutionProperties.class),registry,market,context.getBean(Clock.class)), definition -> definition.setPrimary(true));
        context.registerBean(RiskDecisionStore.class, () -> riskDecisions);
        context.registerBean(RiskLimits.class, () -> new RiskLimits(true, 100, new BigDecimal("10000"), 100,
                new BigDecimal("10000"), Duration.ofMinutes(1), Duration.ofMinutes(1), BigDecimal.ONE, BigDecimal.ONE));
        context.registerBean(TradingProperties.class, () -> {
            var trading = org.mockito.Mockito.mock(TradingProperties.class);
            org.mockito.Mockito.when(trading.emergencyStop()).thenAnswer(call -> emergencyStop.get());
            return trading;
        });
        if (context.getEnvironment().getProperty("kite.order-execution.enabled", Boolean.class, false))
            context.registerBean(OrderExecutionGateway.class, () -> executionGateway);
        var reads = new KiteTradingReadAdapter(transport, session, new KiteTradingReadMapper(registry), metrics);
        context.registerBean(KiteTradingReadAdapter.class, () -> reads);
        context.register(com.kitehybrid.platform.order.infrastructure.OrderConfiguration.class,
                com.kitehybrid.platform.operator.infrastructure.OperatorControlConfiguration.class);
        context.refresh();
        application = context.getBean(OrderApplicationService.class);
        executionProperties = context.getBean(OrderExecutionProperties.class);
        return context;
    }

    @Test void operatorSpringPostgresPreflightArmExecuteDisarmAndReconcile() {
        try (var context = operatorContext()) {
            var operator = context.getBean(com.kitehybrid.platform.operator.application.OperatorExecutionService.class);
            var runtime=context.getBean(com.kitehybrid.platform.shared.application.RuntimeTradingHalt.class);
            assertTrue(runtime.getAsBoolean()); assertTrue(runtime.resume(runtime.epoch()));
            var placed = application.place(place("operator-e2e", OrderType.MARKET, Optional.empty()));
            assertTrue(risk.evaluate(placed.id()).approved());
            var before = orders.find(placed.id()).orElseThrow();
            var prearm = operator.preflight(placed.id());
            assertFalse(prearm.ready());
            assertEquals(ExecutionDenialReason.DISARMED, prearm.reason());
            assertEquals(ExecutionReadiness.Gate.values().length, prearm.gates().size());
            assertEquals(before, orders.find(placed.id()).orElseThrow());
            assertEquals(0, broker.requests().size());
            assertTrue(operator.arm(placed.id(), Duration.ofSeconds(20)).armed());
            assertTrue(operator.preflight(placed.id()).ready(), operator.preflight(placed.id()).toString());
            assertEquals(0, broker.requests().size());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM trading.execution_authorizations", Integer.class));
            broker.assertBeforeRequest(() -> orders.find(placed.id()).orElseThrow().state() == OrderState.SUBMITTING);
            var submitted = operator.execute(placed.id());
            assertEquals(OrderState.SUBMITTED, submitted.state());
            assertTrue(broker.submittingObserved());
            assertEquals(1, broker.placeCount());
            assertFalse(operator.disarm().armed());
            broker.ordersResponse = """
                    {"status":"success","data":[{"order_id":"synthetic-broker-1","instrument_token":123,
                    "exchange":"NSE","tradingsymbol":"ABC","transaction_type":"BUY","order_type":"MARKET",
                    "product":"CNC","validity":"DAY","variety":"regular","status":"OPEN","quantity":1,
                    "filled_quantity":0,"pending_quantity":1,"cancelled_quantity":0,"disclosed_quantity":0,
                    "price":0,"trigger_price":0,"average_price":0,"order_timestamp":"2026-09-21 10:30:00",
                    "tag":"%s"}]}
                    """.formatted(submitted.brokerCorrelationId().orElseThrow().value());
            var reconcile = new OrderReconciliationService(orders, context.getBean(BrokerOrdersProvider.class),
                    context.getBean(BrokerTradesProvider.class), new PostgresReconciliationStore(jdbc),
                    Clock.fixed(NOW, ZoneOffset.UTC), metrics);
            assertEquals(ReconciliationOutcome.ADVANCED, reconcile.reconcile(placed.id()).outcome());
            assertEquals(OrderState.OPEN, orders.find(placed.id()).orElseThrow().state());
            assertEquals(1, broker.placeCount());
        }
    }

    @Test void operatorContextRestartLeavesPersistedApprovalStopped() {
        OrderId id;
        try (var first = operatorContext()) {
            id = approved("operator-restart").id();
            var operator = first.getBean(com.kitehybrid.platform.operator.application.OperatorExecutionService.class);
            assertEquals("RESUME_SUCCESS DISARMED",operator.resume(operator.prepareResume()));
            assertTrue(operator.arm(id, Duration.ofSeconds(20)).armed());
            assertTrue(operator.preflight(id).ready());
        }
        try (var second = operatorContext()) {
            var operator = second.getBean(com.kitehybrid.platform.operator.application.OperatorExecutionService.class);
            assertTrue(operator.haltStatus().effectiveHalted());
            assertEquals(ExecutionDenialReason.DISARMED, operator.preflight(id).reason());
            assertThrows(OrderCommandValidationException.class, () -> operator.execute(id));
            assertEquals(OrderState.RISK_APPROVED, orders.find(id).orElseThrow().state());
            assertEquals(0, broker.requests().size());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"kite.operator-control.enabled=false", "kite.live-test.enabled=false",
            "kite.live-test.allowed-instruments=", "kite.live-test.max-quantity=0", "kite.live-test.max-notional=9",
            "kite.order-execution.enabled=false"})
    void operatorConfigurationDenialsNeverSendHttp(String override) {
        try (var context = operatorContext(override)) {
            var order = approved("operator-denied");
            var operator = context.getBean(com.kitehybrid.platform.operator.application.OperatorExecutionService.class);
            var runtime=context.getBean(com.kitehybrid.platform.shared.application.RuntimeTradingHalt.class);
            assertTrue(runtime.getAsBoolean()); assertTrue(runtime.resume(runtime.epoch()));
            operator.arm(order.id(), Duration.ofSeconds(20));
            assertFalse(operator.preflight(order.id()).ready());
            assertThrows(OrderCommandValidationException.class, () -> operator.execute(order.id()));
            // Even a direct lower-level caller cannot bypass first-live restrictions in production wiring.
            context.getBean(RuntimeExecutionArming.class).arm(Duration.ofSeconds(20), NOW);
            assertThrows(OrderCommandValidationException.class, () -> application.executeRiskApproved(order.id()));
            assertEquals(OrderState.RISK_APPROVED, orders.find(order.id()).orElseThrow().state());
            assertEquals(0, broker.requests().size());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"expiry", "session", "stop", "risk", "market", "health",
            "submitting", "conflict", "store", "database", "livePrice", "fence"})
    void operatorChangedEvidenceAfterArmNeverSendsHttp(String change) {
        try (var context = operatorContext()) {
            var order = approved("operator-changing");
            var operator = context.getBean(com.kitehybrid.platform.operator.application.OperatorExecutionService.class);
            var runtime=context.getBean(com.kitehybrid.platform.shared.application.RuntimeTradingHalt.class);
            assertTrue(runtime.getAsBoolean()); assertTrue(runtime.resume(runtime.epoch()));
            assertTrue(operator.arm(order.id(), Duration.ofSeconds(20)).armed());
            assertTrue(operator.preflight(order.id()).ready());
            switch (change) {
                case "expiry" -> operatorNow.set(NOW.plusSeconds(20));
                case "session" -> {
                    session.install(new com.kitehybrid.platform.broker.application.auth.KiteAccessToken("replacementSynthetic", NOW, NOW.plusSeconds(3600)));
                    session.profileValidated();
                }
                case "stop" -> emergencyStop.set(true);
                case "risk" -> jdbc.update("UPDATE trading.risk_decisions SET evaluated_at=? WHERE order_id=?", java.sql.Timestamp.from(NOW.minusSeconds(60)), order.id().value());
                case "market" -> operatorNow.set(NOW.plusSeconds(5));
                case "health" -> marketHealth = null;
                case "submitting" -> {
                    var other = application.place(place("other", OrderType.MARKET, Optional.empty()));
                    jdbc.update("UPDATE trading.orders SET state='SUBMITTING' WHERE order_id=?", other.id().value());
                }
                case "conflict" -> jdbc.update("INSERT INTO trading.reconciliation_decisions VALUES (gen_random_uuid(),?,'RISK_APPROVED',NULL,'CONFLICT','ORDER_IDENTITY_CONFLICT',?,2)", order.id().value(), java.sql.Timestamp.from(NOW));
                case "store" -> jdbc.execute("ALTER TABLE trading.reconciliation_trades RENAME TO reconciliation_trades_unavailable");
                case "database" -> jdbc.execute("ALTER INDEX trading.orders_broker_order_id_unique RENAME TO unavailable_identity_index");
                case "livePrice" -> market.update(new Tick(INSTRUMENT.id(), new BigDecimal("21"), NOW));
                case "fence" -> {
                    var permit = new PublicationPermit();
                    assertTrue(market.update(new Tick(INSTRUMENT.id(), new BigDecimal("11"), NOW), permit));
                    permit.revoke();
                }
                default -> fail();
            }
            try {
                assertFalse(operator.preflight(order.id()).ready(), change);
                assertThrows(OrderCommandValidationException.class, () -> operator.execute(order.id()), change);
                assertEquals(OrderState.RISK_APPROVED, orders.find(order.id()).orElseThrow().state());
                assertEquals(0, broker.requests().size(), change);
            } finally {
                if (change.equals("store")) jdbc.execute("ALTER TABLE trading.reconciliation_trades_unavailable RENAME TO reconciliation_trades");
                if (change.equals("database")) jdbc.execute("ALTER INDEX trading.unavailable_identity_index RENAME TO orders_broker_order_id_unique");
            }
        }
    }

    @Test void positiveFirstLiveQuantityCapIsIndependentOfNormalRiskAndExecutionCaps() {
        try (var context = operatorContext("kite.order-execution.max-quantity=2")) {
            var order = application.place(new PlaceOrder("live-quantity", INSTRUMENT.id(), OrderSide.BUY, 2,
                    OrderType.MARKET, OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0, OrderVariety.REGULAR));
            assertTrue(risk.evaluate(order.id()).approved());
            var operator = context.getBean(com.kitehybrid.platform.operator.application.OperatorExecutionService.class);
            var runtime=context.getBean(com.kitehybrid.platform.shared.application.RuntimeTradingHalt.class);
            assertTrue(runtime.getAsBoolean()); assertTrue(runtime.resume(runtime.epoch()));
            assertFalse(operator.arm(order.id(), Duration.ofSeconds(20)).armed());
            assertEquals(ExecutionDenialReason.LIVE_TEST_QUANTITY_CAP, operator.preflight(order.id()).gates().get(ExecutionReadiness.Gate.LIVE_TEST_QUANTITY_WITHIN_CAP));
            assertThrows(OrderCommandValidationException.class, () -> operator.execute(order.id()));
            assertThrows(OrderCommandValidationException.class, () -> application.executeRiskApproved(order.id()));
            assertEquals(0, broker.requests().size());
        }
    }

    private void rebuild(OrderExecutionProperties properties) {
        rebuild(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void rebuild(OrderExecutionProperties properties, Clock clock) {
        executionProperties = properties;
        var policy = new ExecutionSafetyPolicy(properties, arming, emergencyStop::get, session, riskDecisions,
                registry, market, () -> marketHealth, orders, clock, metrics,
                new PostgresExecutionAuthorizationAuditStore(jdbc), RehearsalIsolation.accounts(properties,registry,market,Clock.fixed(NOW,ZoneOffset.UTC)), () -> true);
        executionGateway = org.mockito.Mockito.spy(new KiteOrderAdapter(transport, registry, properties));
        application = new OrderApplicationService(orders, new OrderCommandValidator(registry),
                executionGateway, properties,
                clock, metrics, policy);
    }

    private OrderRecord approved(String key) {
        var order = application.place(place(key, OrderType.MARKET, Optional.empty()));
        jdbc.update("UPDATE trading.orders SET state='RISK_APPROVED', version=2, updated_at=? WHERE order_id=?",
                java.sql.Timestamp.from(NOW), order.id().value());
        jdbc.update("INSERT INTO trading.risk_decisions(order_id, order_version, outcome, reason, evaluated_at, policy_version) VALUES (?,?,?,?,?,?)",
                order.id().value(), 1, "APPROVED", "APPROVED", java.sql.Timestamp.from(NOW), executionProperties.riskPolicyVersion());
        return orders.find(order.id()).orElseThrow();
    }

    private void assertDenied(String key, ExecutionDenialReason reason) {
        var order = approved(key);
        assertThrows(OrderCommandValidationException.class, () -> application.executeRiskApproved(order.id()));
        assertEquals(OrderState.RISK_APPROVED, orders.find(order.id()).orElseThrow().state());
        assertEquals(0, broker.placeCount());
        var auditReason = jdbc.queryForObject("SELECT reason FROM trading.execution_authorizations WHERE order_id=?", String.class, order.id().value());
        assertEquals(reason.name(), auditReason);
        assertFalse(jdbc.queryForObject("SELECT allowed FROM trading.execution_authorizations WHERE order_id=?", Boolean.class, order.id().value()));
        assertEquals(0, broker.requests().size());
        org.mockito.Mockito.verifyNoInteractions(executionGateway);
    }

    @Test void policyEnabledApplicationDenialMatrixNeverReachesLoopbackBroker() {
        rebuild(new OrderExecutionProperties(false, Set.of(INSTRUMENT.id()), 1, new BigDecimal("1000"), Duration.ofMinutes(1), Duration.ofMinutes(1), executionProperties.riskLimits(), "disabled"));
        assertDenied("disabled", ExecutionDenialReason.EXECUTION_DISABLED);

        executionProperties = new OrderExecutionProperties(true, Set.of(INSTRUMENT.id()), 1, new BigDecimal("1000"), Duration.ofMinutes(1), Duration.ofMinutes(1), executionProperties.riskLimits(), "enabled");
        arming.disarm(); rebuild(executionProperties); assertDenied("disarmed", ExecutionDenialReason.DISARMED);
        arming.arm(Duration.ofHours(1), NOW); emergencyStop.set(true); rebuild(executionProperties); assertDenied("stop", ExecutionDenialReason.EMERGENCY_STOP);
        emergencyStop.set(false); session.clear(); rebuild(executionProperties); assertDenied("auth", ExecutionDenialReason.AUTHENTICATION_UNAVAILABLE);
        var sessionNow = NOW;
        session.install(new com.kitehybrid.platform.broker.application.auth.KiteAccessToken("syntheticToken", sessionNow.minusSeconds(1), sessionNow.plusSeconds(3600))); session.profileValidated();
        arming.arm(Duration.ofHours(1), NOW);

        var missing = approved("missing-risk"); jdbc.update("DELETE FROM trading.risk_decisions WHERE order_id=?", missing.id().value()); assertDeniedExisting(missing, ExecutionDenialReason.RISK_APPROVAL_MISSING);
        var rejected = approved("rejected-risk"); jdbc.update("UPDATE trading.risk_decisions SET outcome='REJECTED', reason='RISK_DISABLED' WHERE order_id=?", rejected.id().value()); assertDeniedExisting(rejected, ExecutionDenialReason.RISK_APPROVAL_MISSING);
        var expired = approved("expired-risk"); jdbc.update("UPDATE trading.risk_decisions SET evaluated_at=? WHERE order_id=?", java.sql.Timestamp.from(NOW.minus(Duration.ofHours(1))), expired.id().value()); assertDeniedExisting(expired, ExecutionDenialReason.RISK_APPROVAL_EXPIRED);
        var mismatch = approved("version-risk"); jdbc.update("UPDATE trading.risk_decisions SET order_version=0 WHERE order_id=?", mismatch.id().value()); assertDeniedExisting(mismatch, ExecutionDenialReason.ORDER_VERSION_CHANGED);
        rebuild(new OrderExecutionProperties(true, Set.of(), 1, new BigDecimal("1000"), Duration.ofMinutes(1), Duration.ofMinutes(1), executionProperties.riskLimits(), "allowlist")); assertDenied("allowlist", ExecutionDenialReason.INSTRUMENT_NOT_ALLOWED);
        rebuild(new OrderExecutionProperties(true, Set.of(INSTRUMENT.id()), 0, new BigDecimal("1000"), Duration.ofMinutes(1), Duration.ofMinutes(1), executionProperties.riskLimits(), "quantity")); assertDenied("quantity", ExecutionDenialReason.QUANTITY_CAP_EXCEEDED);
        rebuild(new OrderExecutionProperties(true, Set.of(INSTRUMENT.id()), 1, BigDecimal.ZERO, Duration.ofMinutes(1), Duration.ofMinutes(1), executionProperties.riskLimits(), "notional")); assertDenied("notional", ExecutionDenialReason.NOTIONAL_CAP_EXCEEDED);
        market = new InMemoryLatestMarketDataStore(); rebuild(new OrderExecutionProperties(true, Set.of(INSTRUMENT.id()), 1, new BigDecimal("1000"), Duration.ofMinutes(1), Duration.ofMinutes(1), executionProperties.riskLimits(), "missing-md")); assertDenied("missing-md", ExecutionDenialReason.MARKET_DATA_UNAVAILABLE);
        market.update(new Tick(INSTRUMENT.id(), new BigDecimal("10"), NOW.minus(Duration.ofHours(1)))); rebuild(executionProperties); assertDenied("stale-md", ExecutionDenialReason.MARKET_DATA_STALE);
        market.update(new Tick(INSTRUMENT.id(), new BigDecimal("10"), NOW)); marketHealth = new MarketDataHealth(MarketDataGateway.State.DEGRADED, MarketDataHealth.Status.DEGRADED, MarketDataHealth.Reason.BROKER_ERROR, Optional.empty(), Optional.empty(), Optional.empty(), 1, 0, 1, 0, 0, 0, 0, 0, 0); rebuild(executionProperties); assertDenied("degraded-md", ExecutionDenialReason.MARKET_DATA_UNAVAILABLE);
        marketHealth = new MarketDataHealth(MarketDataGateway.State.CONNECTED, MarketDataHealth.Status.FRESH, MarketDataHealth.Reason.NONE, Optional.of(NOW), Optional.of(NOW), Optional.of(NOW), 1, 1, 0, 1, 1, 0, 0, 0, 0);
        var noCorrelation = approved("correlation"); jdbc.update("UPDATE trading.orders SET broker_correlation_id=NULL WHERE order_id=?", noCorrelation.id().value()); assertDeniedExisting(noCorrelation, ExecutionDenialReason.CORRELATION_MISSING);
        var wrongState = approved("wrong-state"); jdbc.update("UPDATE trading.orders SET state='VALIDATED' WHERE order_id=?", wrongState.id().value()); assertDeniedExisting(wrongState, ExecutionDenialReason.INVALID_ORDER_STATE);
        var blocked = approved("blocked"); var dangerous = application.place(place("dangerous", OrderType.MARKET, Optional.empty())); jdbc.update("UPDATE trading.orders SET state='SUBMITTING' WHERE order_id=?", dangerous.id().value()); assertDeniedExisting(blocked, ExecutionDenialReason.RECONCILIATION_REQUIRED);
        arming.arm(Duration.ofSeconds(1), NOW.minusSeconds(2)); rebuild(executionProperties); assertDenied("arm-expired", ExecutionDenialReason.DISARMED);
    }

    @Test void restartRecreatesServiceDisarmedAndCannotExecutePersistedApproval() {
        var order = approved("restart-disarm");
        arming = new RuntimeExecutionArming(metrics, session::executionIdentity);
        rebuild(executionProperties);
        assertThrows(OrderCommandValidationException.class, () -> application.executeRiskApproved(order.id()));
        assertEquals(OrderState.RISK_APPROVED, orders.find(order.id()).orElseThrow().state());
        assertEquals(0, broker.placeCount());
        assertEquals(ExecutionDenialReason.DISARMED.name(), jdbc.queryForObject("SELECT reason FROM trading.execution_authorizations WHERE order_id=?", String.class, order.id().value()));
    }

    @Test void armBoundaryUsesDeterministicClockAndDoesNotReachBrokerAfterExpiry() {
        var beforeExpiry = approved("arm-before-expiry");
        arming.arm(Duration.ofSeconds(1), NOW);
        rebuild(executionProperties, Clock.fixed(NOW.plusMillis(999), ZoneOffset.UTC));
        assertEquals(OrderState.SUBMITTED, application.executeRiskApproved(beforeExpiry.id()).state());
        assertEquals(1, broker.placeCount());

        rebuild(executionProperties);
        var atExpiry = approved("arm-at-expiry");
        arming.arm(Duration.ofSeconds(1), NOW);
        rebuild(executionProperties, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
        assertThrows(OrderCommandValidationException.class, () -> application.executeRiskApproved(atExpiry.id()));
        assertEquals(OrderState.RISK_APPROVED, orders.find(atExpiry.id()).orElseThrow().state());
        assertEquals(1, broker.placeCount());
    }

    @Test void postgresCasStopsPolicyAllowedOrderWhenVersionChangesBeforeSubmit() {
        var order = approved("toctou");
        var delegate = orders;
        var mutating = new OrderRepository() {
            boolean changed;
            public IdempotencyClaim claimIdempotency(String k,String f,OrderId id){return delegate.claimIdempotency(k,f,id);}
            public IdempotencyClaim createIfAbsent(OrderRecord r,String f){return delegate.createIfAbsent(r,f);}
            public void create(OrderRecord r){delegate.create(r);}
            public Optional<OrderRecord> find(OrderId id){return delegate.find(id);}
            public Optional<OrderRecord> findByIdempotencyKey(String k){return delegate.findByIdempotencyKey(k);}
            public void requireIndependentExecution(){delegate.requireIndependentExecution();}
            public boolean hasBlockingExposureExcept(OrderId id){return delegate.hasBlockingExposureExcept(id);}
            public boolean compareAndSet(OrderRecord expected,OrderRecord next){return delegate.compareAndSet(expected,next);}
            public boolean beginSubmission(OrderRecord expected,OrderRecord next,Runnable validation){
                if(!changed){changed=true; jdbc.update("UPDATE trading.orders SET state='VALIDATED', version=version+1 WHERE order_id=? AND version=?", expected.id().value(), expected.version());}
                return delegate.beginSubmission(expected,next,validation);
            }
            public boolean attachBrokerOrderId(OrderRecord e,OrderRecord n){return delegate.attachBrokerOrderId(e,n);}
            public boolean hasDangerousUnresolvedOrders(){return delegate.hasDangerousUnresolvedOrders();}
        };
        application = new OrderApplicationService(mutating, new OrderCommandValidator(registry), new KiteOrderAdapter(transport, registry, executionProperties), executionProperties, Clock.fixed(NOW, ZoneOffset.UTC), metrics,
                new ExecutionSafetyPolicy(executionProperties, arming, emergencyStop::get, session, riskDecisions, registry, market, () -> marketHealth, orders, Clock.fixed(NOW, ZoneOffset.UTC), metrics, new PostgresExecutionAuthorizationAuditStore(jdbc), RehearsalIsolation.accounts(executionProperties,registry,market,Clock.fixed(NOW,ZoneOffset.UTC)), () -> true));
        assertThrows(OrderExecutionException.class, () -> application.executeRiskApproved(order.id()));
        assertEquals(0, broker.placeCount());
        assertEquals(OrderState.VALIDATED, orders.find(order.id()).orElseThrow().state());
        assertTrue(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM trading.execution_authorizations WHERE order_id=? AND allowed)", Boolean.class, order.id().value()));
    }

    @Test void concurrentExplicitExecutionUsesPostgresCasAndOneHttpRequest() throws Exception {
        var order = approved("concurrent");
        var bothAuthorized = new CountDownLatch(2);
        var durableAudit = new PostgresExecutionAuthorizationAuditStore(jdbc);
        ExecutionAuthorizationAuditStore barrierAudit = decision -> {
            durableAudit.record(decision);
            if (!decision.allowed()) return;
            bothAuthorized.countDown();
            try { assertTrue(bothAuthorized.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        };
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        application = new OrderApplicationService(orders, new OrderCommandValidator(registry), executionGateway,
                executionProperties, clock, metrics, new ExecutionSafetyPolicy(executionProperties, arming,
                emergencyStop::get, session, riskDecisions, registry, market, () -> marketHealth, orders,
                clock, metrics, barrierAudit, RehearsalIsolation.accounts(executionProperties,registry,market,Clock.fixed(NOW,ZoneOffset.UTC)), () -> true));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> attempt(order.id()));
            var second = pool.submit(() -> attempt(order.id()));
            first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(1, broker.placeCount());
        org.mockito.Mockito.verify(executionGateway, org.mockito.Mockito.times(1)).place(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertEquals(OrderState.SUBMITTED, orders.find(order.id()).orElseThrow().state());
    }

    private void attempt(OrderId id) { try { application.executeRiskApproved(id); } catch (RuntimeException ignored) { } }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"stop", "disarm", "risk", "market", "health", "session", "auth", "risk-policy", "risk-age", "market-age", "arm-age", "notional", "version", "correlation"})
    void changedEvidenceAfterAuthorizationCannotReachHttp(String change) {
        var order = approved("changed-" + change);
        var audit = new PostgresExecutionAuthorizationAuditStore(jdbc);
        ExecutionAuthorizationAuditStore changingAudit = decision -> {
            audit.record(decision);
            if (!decision.allowed()) return;
            switch (change) {
                case "stop" -> emergencyStop.set(true);
                case "disarm" -> arming.disarm();
                case "risk" -> jdbc.update("DELETE FROM trading.risk_decisions WHERE order_id=?", order.id().value());
                case "market" -> market = new InMemoryLatestMarketDataStore();
                case "health" -> marketHealth = null;
                case "auth" -> session.clear();
                case "risk-policy" -> jdbc.update("UPDATE trading.risk_decisions SET policy_version=? WHERE order_id=?", "cash-v1:" + "1".repeat(64), order.id().value());
                case "risk-age" -> jdbc.update("UPDATE trading.risk_decisions SET evaluated_at=? WHERE order_id=?", java.sql.Timestamp.from(NOW.minusSeconds(3600)), order.id().value());
                case "market-age" -> { market = new InMemoryLatestMarketDataStore(); market.update(new Tick(INSTRUMENT.id(), BigDecimal.TEN, NOW.minusSeconds(3600))); }
                case "arm-age" -> arming.arm(Duration.ofSeconds(1), NOW.minusSeconds(2));
                case "notional" -> market.update(new Tick(INSTRUMENT.id(), new BigDecimal("100000"), NOW));
                case "version" -> jdbc.update("UPDATE trading.orders SET version=version+1 WHERE order_id=?", order.id().value());
                case "correlation" -> jdbc.update("UPDATE trading.orders SET broker_correlation_id=NULL WHERE order_id=?", order.id().value());
                case "session" -> { session.clear(); var now = NOW;
                    session.install(new com.kitehybrid.platform.broker.application.auth.KiteAccessToken("replacementSynthetic", now.minusSeconds(1), now.plusSeconds(3600)));
                    session.profileValidated(); }
            }
        };
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var changingMarket = new LatestMarketDataStore() {
            public boolean update(Tick t) { return market.update(t); }
            public boolean update(Tick t, PublicationPermit p) { return market.update(t, p); }
            public Optional<Tick> latest(InstrumentId id) { return market.latest(id); }
            public Map<InstrumentId, Tick> snapshot(Set<InstrumentId> ids) { return market.snapshot(ids); }
        };
        application = new OrderApplicationService(orders, new OrderCommandValidator(registry), executionGateway,
                executionProperties, clock, metrics, new ExecutionSafetyPolicy(executionProperties, arming,
                emergencyStop::get, session, riskDecisions, registry, changingMarket, () -> marketHealth,
                orders, clock, metrics, changingAudit, RehearsalIsolation.accounts(executionProperties,registry,market,Clock.fixed(NOW,ZoneOffset.UTC)), () -> true));
        assertThrows(RuntimeException.class, () -> application.executeRiskApproved(order.id()));
        assertEquals(0, broker.requests().size());
        assertTrue(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM trading.execution_authorizations WHERE order_id=? AND NOT allowed)", Boolean.class, order.id().value()));
        var expected = switch (change) {
            case "stop" -> ExecutionDenialReason.EMERGENCY_STOP;
            case "disarm", "session", "arm-age" -> ExecutionDenialReason.DISARMED;
            case "risk" -> ExecutionDenialReason.RISK_APPROVAL_MISSING;
            case "risk-age" -> ExecutionDenialReason.RISK_APPROVAL_EXPIRED;
            case "risk-policy" -> ExecutionDenialReason.RISK_POLICY_MISMATCH;
            case "auth" -> ExecutionDenialReason.AUTHENTICATION_UNAVAILABLE;
            case "market", "health" -> ExecutionDenialReason.MARKET_DATA_UNAVAILABLE;
            case "market-age" -> ExecutionDenialReason.MARKET_DATA_STALE;
            case "notional" -> ExecutionDenialReason.NOTIONAL_CAP_EXCEEDED;
            default -> ExecutionDenialReason.ORDER_VERSION_CHANGED;
        };
        assertEquals(expected.name(), jdbc.queryForObject("SELECT reason FROM trading.execution_authorizations WHERE order_id=? AND NOT allowed", String.class, order.id().value()));
    }

    @Test void transportEntryRechecksAfterGatewayEntryAndDoesNotSend() {
        var order = approved("transport-recheck");
        org.mockito.Mockito.doAnswer(call -> { emergencyStop.set(true); return call.callRealMethod(); })
                .when(executionGateway).place(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThrows(OrderCommandValidationException.class, () -> application.executeRiskApproved(order.id()));
        assertEquals(0, broker.requests().size());
        assertEquals(OrderState.SUBMITTING, orders.find(order.id()).orElseThrow().state());
        assertEquals(Optional.empty(), orders.find(order.id()).orElseThrow().failureCategory());
        assertThrows(RuntimeException.class, () -> application.executeRiskApproved(order.id()));
        assertEquals(0, broker.requests().size());
    }

    @Test void differentApprovedOrdersSharePostgresAdmissionAcrossServiceInstances() throws Exception {
        var first = approved("account-first"); var second = approved("account-second");
        var both = new CountDownLatch(2); var audit = new PostgresExecutionAuthorizationAuditStore(jdbc);
        ExecutionAuthorizationAuditStore barrier = d -> {
            audit.record(d); if (!d.allowed()) return;
            both.countDown();
            try { assertTrue(both.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        };
        var otherRepository = new PostgresOrderRepository(jdbc);
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var firstService = new OrderApplicationService(orders, new OrderCommandValidator(registry), executionGateway,
                executionProperties, clock, metrics, new ExecutionSafetyPolicy(executionProperties, arming, emergencyStop::get,
                session, riskDecisions, registry, market, () -> marketHealth, orders, clock, metrics, barrier, RehearsalIsolation.accounts(executionProperties,registry,market,Clock.fixed(NOW,ZoneOffset.UTC)), () -> true));
        var otherService = new OrderApplicationService(otherRepository, new OrderCommandValidator(registry),
                new KiteOrderAdapter(transport, registry, executionProperties), executionProperties, clock, metrics,
                new ExecutionSafetyPolicy(executionProperties, arming, emergencyStop::get, session, riskDecisions,
                registry, market, () -> marketHealth, otherRepository, clock, metrics, barrier, RehearsalIsolation.accounts(executionProperties,registry,market,Clock.fixed(NOW,ZoneOffset.UTC)), () -> true));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { try { firstService.executeRiskApproved(first.id()); } catch (RuntimeException ignored) {} });
            var b = pool.submit(() -> { try { otherService.executeRiskApproved(second.id()); } catch (RuntimeException ignored) {} });
            a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS);
        }
        assertEquals(1, broker.requests().size());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM trading.orders WHERE state='SUBMITTED'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM trading.orders WHERE state='RISK_APPROVED'", Integer.class));
    }

    @Test void ambientTransactionCannotSendAndDeniedAuditSurvivesRollback() {
        var order = approved("ambient");
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(status -> {
            assertThrows(IllegalStateException.class, () -> application.executeRiskApproved(order.id()));
            new PostgresExecutionAuthorizationAuditStore(jdbc).record(new ExecutionAuthorizationDecision(false,
                    ExecutionDenialReason.DISARMED, NOW, order.id(), order.version(), "rollback-test"));
            status.setRollbackOnly();
        });
        assertEquals(0, broker.requests().size());
        assertEquals(OrderState.RISK_APPROVED, orders.find(order.id()).orElseThrow().state());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM trading.execution_authorizations WHERE order_id=?", Integer.class, order.id().value()));
    }

    private void assertDeniedExisting(OrderRecord order, ExecutionDenialReason reason) {
        assertThrows(RuntimeException.class, () -> application.executeRiskApproved(order.id()));
        assertNotEquals(OrderState.SUBMITTING, orders.find(order.id()).orElseThrow().state());
        assertEquals(0, broker.placeCount());
        assertEquals(reason.name(), jdbc.queryForObject("SELECT reason FROM trading.execution_authorizations WHERE order_id=? ORDER BY evaluated_at DESC LIMIT 1", String.class, order.id().value()));
        assertFalse(jdbc.queryForObject("SELECT allowed FROM trading.execution_authorizations WHERE order_id=?", Boolean.class, order.id().value()));
        assertEquals(0, broker.requests().size());
        org.mockito.Mockito.verifyNoInteractions(executionGateway);
    }

    @Test void placeRiskApproveThenExplicitExecuteModifyAndCancel() {
        var placed = application.place(place("e2e-place", OrderType.LIMIT, Optional.of(new BigDecimal("10.25"))));
        assertEquals(OrderState.VALIDATED, placed.state());
        var decision = risk.evaluate(placed.id());
        assertTrue(decision.approved());
        assertEquals(OrderState.RISK_APPROVED, orders.find(placed.id()).orElseThrow().state());
        assertEquals(0, broker.placeCount());
        broker.assertBeforeRequest(() -> orders.find(placed.id()).orElseThrow().state() == OrderState.SUBMITTING);

        var submitted = application.executeRiskApproved(placed.id());
        assertEquals(OrderState.SUBMITTED, submitted.state());
        assertEquals(Optional.of("synthetic-broker-1"), submitted.brokerOrderId());
        assertEquals(1, broker.placeCount());
        assertTrue(broker.submittingObserved());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trading.execution_authorizations WHERE order_id=? AND allowed", Integer.class, placed.id().value()));
        assertTrue(broker.lastRequest().body().contains("tradingsymbol=ABC"));
        assertTrue(broker.lastRequest().body().contains("exchange=NSE"));
        assertTrue(broker.lastRequest().body().contains("transaction_type=BUY"));
        assertTrue(broker.lastRequest().body().contains("order_type=LIMIT"));
        assertTrue(broker.lastRequest().body().contains("quantity=1"));
        assertTrue(broker.lastRequest().body().contains("product=CNC"));
        assertTrue(broker.lastRequest().body().contains("validity=DAY"));
        assertTrue(broker.lastRequest().body().contains("price=10.25"));
        assertTrue(broker.lastRequest().body().contains("tag=" + placed.brokerCorrelationId().orElseThrow().value()));
        assertTrue(broker.lastRequest().authorization().startsWith("token syntheticKey:"));
        assertThrows(OrderCommandValidationException.class, () -> application.executeRiskApproved(placed.id()));
        assertEquals(1, broker.placeCount());

        assertEquals(OrderExecutionException.Category.DISABLED, assertThrows(OrderExecutionException.class,
                () -> application.modify(new ModifyOrder("modify-e2e", submitted.id(), OrderType.LIMIT, 1,
                        Optional.of(new BigDecimal("11.00")), Optional.empty(), 0, OrderValidity.DAY))).category());
        assertEquals(OrderExecutionException.Category.DISABLED, assertThrows(OrderExecutionException.class,
                () -> application.cancel(new CancelOrder("cancel-e2e", submitted.id()))).category());
        assertEquals(1, broker.requests().size());
        assertEquals(OrderState.SUBMITTED, orders.find(submitted.id()).orElseThrow().state());
    }

    @Test void marketOrderSuccessUsesFreshReferencePriceForRiskAndOmitsLimitPrice() {
        var placed = application.place(place("market-e2e", OrderType.MARKET, Optional.empty()));
        assertTrue(risk.evaluate(placed.id()).approved());
        var submitted = application.executeRiskApproved(placed.id());
        assertEquals(OrderState.SUBMITTED, submitted.state());
        assertEquals(1, broker.placeCount());
        var body = broker.lastRequest().body();
        assertTrue(body.contains("order_type=MARKET"));
        assertFalse(body.contains("price="));
    }

    @Test void acknowledgedLocalSubmissionCanBeReconciledFromBrokerReadObservation() {
        var placed = application.place(place("reconcile-e2e", OrderType.LIMIT, Optional.of(new BigDecimal("10.25"))));
        assertTrue(risk.evaluate(placed.id()).approved());
        var submitted = application.executeRiskApproved(placed.id());
        var observed = new BrokerOrder(submitted.brokerOrderId().orElseThrow(), Optional.empty(), Optional.empty(),
                INSTRUMENT.id(), TradingReadTypes.Side.BUY, TradingReadTypes.OrderType.LIMIT,
                TradingReadTypes.Product.DELIVERY, TradingReadTypes.Validity.DAY, TradingReadTypes.Variety.REGULAR,
                TradingReadTypes.OrderStatus.OPEN, 1, 0, 1, 0, 0, new BigDecimal("10.25"), BigDecimal.ZERO,
                BigDecimal.ZERO, NOW, Optional.of(NOW), Optional.of(NOW));
        var reconciliation = new OrderReconciliationService(orders, () -> List.of(observed), List::of,
                new PostgresReconciliationStore(jdbc), Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        var decision = reconciliation.reconcile(submitted.id());
        assertEquals(ReconciliationOutcome.ADVANCED, decision.outcome());
        assertEquals(OrderState.OPEN, orders.find(submitted.id()).orElseThrow().state());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trading.reconciliation_decisions", Integer.class));
    }

    @Test void strategyToRiskStopsBeforeGatewayAndFakeBroker() {
        var marketHealth = new MarketDataHealth(MarketDataGateway.State.CONNECTED, MarketDataHealth.Status.FRESH,
                MarketDataHealth.Reason.NONE, Optional.of(NOW), Optional.of(NOW), Optional.of(NOW), 1, 1, 0, 1, 1, 0, 0, 0, 0);
        var strategy = new ReferenceThresholdStrategy(new StrategyDefinition(new StrategyId("reference"), "v1"), INSTRUMENT.id(), new BigDecimal("20"), 1);
        var coordinator = new StrategyOrderCoordinator(new PostgresStrategyEvaluationStore(jdbc), application,
                Optional.of(risk), Clock.fixed(NOW, ZoneOffset.UTC), () -> false, new SimpleMeterRegistry());
        var result = coordinator.evaluate("strategy-event-1", strategy,
                new StrategyInput(INSTRUMENT.id(), Optional.of(new Tick(INSTRUMENT.id(), new BigDecimal("10"), NOW)), marketHealth, NOW));
        assertEquals(Optional.of(result.orderId().orElseThrow()), result.orderId());
        assertEquals(OrderState.RISK_APPROVED, orders.find(result.orderId().orElseThrow()).orElseThrow().state());
        assertEquals(0, broker.placeCount());
    }

    @Test void ambiguousAcceptedRequestRemainsSubmittingAndNeverRetries() {
        var placed = application.place(place("ambiguous-e2e", OrderType.MARKET, Optional.empty()));
        assertTrue(risk.evaluate(placed.id()).approved());
        broker.mode = LocalFakeKiteServer.Mode.CLOSE_AFTER_CAPTURE;
        assertThrows(OrderExecutionException.class, () -> application.executeRiskApproved(placed.id()));
        assertEquals(OrderState.SUBMITTING, orders.find(placed.id()).orElseThrow().state());
        assertEquals(1, broker.placeCount());
        assertThrows(OrderCommandValidationException.class, () -> application.executeRiskApproved(placed.id()));
        assertEquals(1, broker.placeCount());
        var local = orders.find(placed.id()).orElseThrow();
        assertTrue(broker.lastRequest().body().contains("tag=" + local.brokerCorrelationId().orElseThrow().value()));
        broker.ordersResponse = """
                {"status":"success","data":[{"order_id":"synthetic-broker-1","instrument_token":123,
                "exchange":"NSE","tradingsymbol":"ABC","transaction_type":"BUY","order_type":"MARKET",
                "product":"CNC","validity":"DAY","variety":"regular","status":"OPEN","quantity":1,
                "filled_quantity":0,"pending_quantity":1,"cancelled_quantity":0,"disclosed_quantity":0,
                "price":0,"trigger_price":0,"average_price":0,"order_timestamp":"2026-09-21 10:30:00",
                "tag":"%s"}]}
                """.formatted(local.brokerCorrelationId().orElseThrow().value());
        orders = new PostgresOrderRepository(jdbc);
        arming = new RuntimeExecutionArming(metrics, session::executionIdentity);
        rebuild(executionProperties);
        assertTrue(orders.find(placed.id()).orElseThrow().brokerOrderId().isEmpty());
        assertThrows(OrderCommandValidationException.class, () -> application.executeRiskApproved(placed.id()));
        var reads = new KiteTradingReadAdapter(transport, session, new KiteTradingReadMapper(registry), metrics);
        var reconciliation = new OrderReconciliationService(orders, reads, reads,
                new PostgresReconciliationStore(jdbc), Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        assertEquals(com.kitehybrid.platform.reconciliation.domain.ReconciliationReason.CORRELATION_RECOVERED,
                reconciliation.reconcile(placed.id()).reason());
        assertEquals(OrderState.SUBMITTED, orders.find(placed.id()).orElseThrow().state());
        assertEquals(Optional.of("synthetic-broker-1"), orders.find(placed.id()).orElseThrow().brokerOrderId());
        assertEquals(1, broker.placeCount());
    }

    @Test void brokerFailuresAreBoundedAndDoNotExposeResponseBodies() {
        var placed = application.place(place("rejected-e2e", OrderType.MARKET, Optional.empty()));
        assertTrue(risk.evaluate(placed.id()).approved());
        broker.mode = LocalFakeKiteServer.Mode.REJECT;
        var failure = assertThrows(OrderExecutionException.class, () -> application.executeRiskApproved(placed.id()));
        assertEquals(OrderExecutionException.Category.BROKER_REJECTED, failure.category());
        assertEquals(OrderState.FAILED, orders.find(placed.id()).orElseThrow().state());
        assertFalse(failure.getMessage().contains("sensitive"));
    }

    @Test void missingAuthenticationAndMalformedResponsesFailClosed() {
        var registry = new InMemoryInstrumentRegistry(); registry.replace(List.of(INSTRUMENT), NOW);
        var unauthenticated = new KiteSession(new KiteProperties("syntheticKey", "syntheticSecret", "", true));
        var adapter = new KiteOrderAdapter(new KiteRestTransport(RestClient.builder().baseUrl(broker.baseUrl()).build(), unauthenticated),
                registry, new OrderExecutionProperties(true));
        assertEquals(OrderExecutionException.Category.AUTHENTICATION,
                assertThrows(OrderExecutionException.class, () -> adapter.place(record(place("auth", OrderType.MARKET, Optional.empty())), () -> {})).category());
        var placed = application.place(place("malformed-e2e", OrderType.MARKET, Optional.empty()));
        assertTrue(risk.evaluate(placed.id()).approved());
        broker.mode = LocalFakeKiteServer.Mode.MALFORMED;
        assertEquals(OrderExecutionException.Category.MALFORMED_RESPONSE,
                assertThrows(OrderExecutionException.class, () -> application.executeRiskApproved(placed.id())).category());
        assertEquals(OrderState.SUBMITTING, orders.find(placed.id()).orElseThrow().state());
        assertTrue(orders.hasDangerousUnresolvedOrders());
        assertThrows(OrderCommandValidationException.class, () -> application.executeRiskApproved(placed.id()));
        assertEquals(1, broker.placeCount());
    }

    @Test void localExecutionHostGuardRejectsProductionAndExternalHosts() {
        assertDoesNotThrow(() -> requireLoopback(URI.create(broker.baseUrl())));
        assertThrows(IllegalArgumentException.class, () -> requireLoopback(URI.create("https://api.kite.trade")));
        assertThrows(IllegalArgumentException.class, () -> requireLoopback(URI.create("http://192.0.2.1:8080")));
        assertThrows(IllegalArgumentException.class, () -> requireLoopback(URI.create("http://kite.zerodha.com")));
    }

    private RiskService riskService(PostgresOrderRepository repository, InMemoryInstrumentRegistry registry,
                                    RiskDecisionStore decisions, InMemoryLatestMarketDataStore market, RiskLimits limits) {
        var zero = BigDecimal.ZERO;
        var available = new BrokerMargins.AvailableMargin(zero, new BigDecimal("1000"), new BigDecimal("1000"),
                new BigDecimal("1000"), zero, zero);
        var utilised = new BrokerMargins.UtilisedMargin(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero);
        var enabled = new BrokerMargins.SegmentMargin(true, new BigDecimal("1000"), available, utilised);
        var disabled = new BrokerMargins.SegmentMargin(false, zero,
                new BrokerMargins.AvailableMargin(zero, zero, zero, zero, zero, zero),
                new BrokerMargins.UtilisedMargin(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero));
        var margins = new BrokerMargins(Map.of(MarginSegment.EQUITY, enabled, MarginSegment.COMMODITY, disabled));
        return new RiskService(decisions,
                new RiskEngine(List.of(new EmergencyStopRiskRule(() -> false), new PositiveReferencePriceRiskRule()), Clock.fixed(NOW, ZoneOffset.UTC)),
                limits, () -> false, registry, market, () -> marketHealth,
                () -> new BrokerPositions(List.of(), List.of()), List::of, () -> margins, List::of,
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
    }
    private static PlaceOrder place(String key, OrderType type, Optional<BigDecimal> limit) {
        return new PlaceOrder(key, INSTRUMENT.id(), OrderSide.BUY, 1, type, OrderProduct.DELIVERY,
                OrderValidity.DAY, limit, Optional.empty(), 0, OrderVariety.REGULAR);
    }
    private static OrderRecord record(PlaceOrder command) {
        return new OrderRecord(new OrderId(UUID.randomUUID()), command, OrderState.RISK_APPROVED,
                Optional.empty(), Optional.empty(), NOW, NOW, 1);
    }
    private static void requireLoopback(URI uri) {
        if (uri == null || !List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || !Set.of("127.0.0.1", "localhost", "::1", "[::1]").contains(uri.getHost()) || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Execution test target must be loopback");
        }
        try {
            if (!InetAddress.getByName(uri.getHost()).isLoopbackAddress()) {
                throw new IllegalArgumentException("Execution test target must be loopback");
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Execution test target must be loopback", ex);
        }
    }

    private static final class LocalFakeKiteServer implements AutoCloseable {
        enum Mode { SUCCESS, REJECT, MALFORMED, CLOSE_AFTER_CAPTURE }
        private final HttpServer server;
        private final List<Request> requests = new CopyOnWriteArrayList<>();
        private volatile Mode mode = Mode.SUCCESS;
        private volatile BooleanSupplier beforeRequest = () -> true;
        private volatile boolean submittingObserved;
        private volatile String ordersResponse = "{\"status\":\"success\",\"data\":[]}";
        LocalFakeKiteServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/orders/regular", this::handle);
            server.createContext("/orders", this::read);
            server.createContext("/trades", this::read);
            server.setExecutor(null);
        }
        void start() { server.start(); }
        String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        List<Request> requests() { return List.copyOf(requests); }
        int placeCount() { return (int) requests.stream().filter(r -> r.method().equals("POST")).count(); }
        void assertBeforeRequest(BooleanSupplier check) { beforeRequest = check; }
        boolean submittingObserved() { return submittingObserved; }
        Request lastRequest() { return requests.get(requests.size() - 1); }
        private void read(HttpExchange exchange) throws IOException {
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), "", ""));
            if (!exchange.getRequestMethod().equals("GET")) { reply(exchange, 405, "{}"); return; }
            reply(exchange, 200, exchange.getRequestURI().getPath().equals("/orders")
                    ? ordersResponse : "{\"status\":\"success\",\"data\":[]}");
        }
        private void handle(HttpExchange exchange) throws IOException {
            var bytes = exchange.getRequestBody().readAllBytes();
            var auth = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Authorization")).orElse("");
            if (exchange.getRequestMethod().equals("POST")) submittingObserved = beforeRequest.getAsBoolean();
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    new String(bytes, StandardCharsets.UTF_8), auth));
            if (mode == Mode.CLOSE_AFTER_CAPTURE) { exchange.close(); return; }
            if (mode == Mode.REJECT) { reply(exchange, 400, "sensitive broker response"); return; }
            if (mode == Mode.MALFORMED) { reply(exchange, 200, "{malformed"); return; }
            reply(exchange, 200, "{\"status\":\"success\",\"data\":{\"order_id\":\"synthetic-broker-1\"}}");
        }
        private static void reply(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        }
        @Override public void close() { server.stop(0); }
        record Request(String method, String path, String body, String authorization) {}
    }
}
