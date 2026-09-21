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
import com.kitehybrid.platform.risk.application.RiskService;
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
        jdbc.update("TRUNCATE trading.strategy_evaluations, trading.reconciliation_trades, trading.reconciliation_decisions, trading.risk_decisions, trading.orders, trading.order_idempotency");
        orders = new PostgresOrderRepository(jdbc);
        broker = new LocalFakeKiteServer();
        broker.start();
        var registry = new InMemoryInstrumentRegistry();
        registry.replace(List.of(INSTRUMENT), NOW);
        var session = new KiteSession(new KiteProperties("syntheticKey", "syntheticSecret", "syntheticToken", true));
        session.profileValidated();
        assertTrue(session.authenticated(), "synthetic local session must be authenticated");
        var transport = new KiteRestTransport(RestClient.builder().baseUrl(broker.baseUrl()).build(), session);
        var gateway = new KiteOrderAdapter(transport, registry, new OrderExecutionProperties(true));
        application = new OrderApplicationService(orders, new com.kitehybrid.platform.order.application.OrderCommandValidator(registry),
                gateway, new OrderExecutionProperties(true), Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        risk = riskService(orders, registry);
    }
    @AfterEach void tearDown() { if (broker != null) broker.close(); }

    @Test void placeRiskApproveThenExplicitExecuteModifyAndCancel() {
        var placed = application.place(place("e2e-place", OrderType.LIMIT, Optional.of(new BigDecimal("10.25"))));
        assertEquals(OrderState.VALIDATED, placed.state());
        var decision = risk.evaluate(placed.id());
        assertTrue(decision.approved());
        assertEquals(OrderState.RISK_APPROVED, orders.find(placed.id()).orElseThrow().state());
        assertEquals(0, broker.placeCount());

        var submitted = application.executeRiskApproved(placed.id());
        assertEquals(OrderState.SUBMITTED, submitted.state());
        assertEquals(Optional.of("synthetic-broker-1"), submitted.brokerOrderId());
        assertEquals(1, broker.placeCount());
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

        application.modify(new ModifyOrder("modify-e2e", submitted.id(), OrderType.LIMIT, 1,
                Optional.of(new BigDecimal("11.00")), Optional.empty(), 0, OrderValidity.DAY));
        assertEquals("PUT", broker.requests().get(1).method());
        assertTrue(broker.requests().get(1).body().contains("price=11"));
        var cancelled = application.cancel(new CancelOrder("cancel-e2e", submitted.id()));
        assertEquals(OrderState.CANCELLED, cancelled.state());
        assertEquals("DELETE", broker.requests().get(2).method());
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
                Optional.of(risk), Clock.fixed(NOW, ZoneOffset.UTC), new TradingProperties(TradingMode.PAPER, false, false), new SimpleMeterRegistry());
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
        var observed = new BrokerOrder("synthetic-broker-1", Optional.empty(), Optional.empty(), INSTRUMENT.id(),
                TradingReadTypes.Side.BUY, TradingReadTypes.OrderType.MARKET, TradingReadTypes.Product.DELIVERY,
                TradingReadTypes.Validity.DAY, TradingReadTypes.Variety.REGULAR, TradingReadTypes.OrderStatus.OPEN,
                1, 0, 1, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, NOW, Optional.of(NOW),
                local.brokerCorrelationId(), Optional.of(NOW));
        var reconciliation = new OrderReconciliationService(orders, () -> List.of(observed), List::of,
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
                assertThrows(OrderExecutionException.class, () -> adapter.place(record(place("auth", OrderType.MARKET, Optional.empty())))).category());
        var placed = application.place(place("malformed-e2e", OrderType.MARKET, Optional.empty()));
        assertTrue(risk.evaluate(placed.id()).approved());
        broker.mode = LocalFakeKiteServer.Mode.MALFORMED;
        assertEquals(OrderExecutionException.Category.MALFORMED_RESPONSE,
                assertThrows(OrderExecutionException.class, () -> application.executeRiskApproved(placed.id())).category());
    }

    @Test void localExecutionHostGuardRejectsProductionAndExternalHosts() {
        assertDoesNotThrow(() -> requireLoopback(URI.create(broker.baseUrl())));
        assertThrows(IllegalArgumentException.class, () -> requireLoopback(URI.create("https://api.kite.trade")));
        assertThrows(IllegalArgumentException.class, () -> requireLoopback(URI.create("http://192.0.2.1:8080")));
        assertThrows(IllegalArgumentException.class, () -> requireLoopback(URI.create("http://kite.zerodha.com")));
    }

    private RiskService riskService(PostgresOrderRepository repository, InMemoryInstrumentRegistry registry) {
        var market = new InMemoryLatestMarketDataStore();
        market.update(new Tick(INSTRUMENT.id(), new BigDecimal("10.00"), NOW));
        var zero = BigDecimal.ZERO;
        var available = new BrokerMargins.AvailableMargin(zero, new BigDecimal("1000"), new BigDecimal("1000"),
                new BigDecimal("1000"), zero, zero);
        var utilised = new BrokerMargins.UtilisedMargin(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero);
        var enabled = new BrokerMargins.SegmentMargin(true, new BigDecimal("1000"), available, utilised);
        var disabled = new BrokerMargins.SegmentMargin(false, zero,
                new BrokerMargins.AvailableMargin(zero, zero, zero, zero, zero, zero),
                new BrokerMargins.UtilisedMargin(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero));
        var margins = new BrokerMargins(Map.of(MarginSegment.EQUITY, enabled, MarginSegment.COMMODITY, disabled));
        var health = new MarketDataHealth(MarketDataGateway.State.CONNECTED, MarketDataHealth.Status.FRESH,
                MarketDataHealth.Reason.NONE, Optional.of(NOW), Optional.of(NOW), Optional.of(NOW), 1, 1, 0, 1, 1, 0, 0, 0, 0);
        var limits = new RiskLimits(true, 100, new BigDecimal("10000"), 100, new BigDecimal("10000"),
                Duration.ofMinutes(1), Duration.ofMinutes(1), BigDecimal.ONE, BigDecimal.ONE);
        return new RiskService(new PostgresRiskDecisionStore(jdbc, repository),
                new RiskEngine(List.of(new EmergencyStopRiskRule(() -> false), new PositiveReferencePriceRiskRule()), Clock.fixed(NOW, ZoneOffset.UTC)),
                limits, () -> false, registry, market, () -> health,
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
        if (uri == null || !List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
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
        LocalFakeKiteServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/orders/regular", this::handle);
            server.setExecutor(null);
        }
        void start() { server.start(); }
        String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        List<Request> requests() { return List.copyOf(requests); }
        int placeCount() { return (int) requests.stream().filter(r -> r.method().equals("POST")).count(); }
        Request lastRequest() { return requests.get(requests.size() - 1); }
        private void handle(HttpExchange exchange) throws IOException {
            var bytes = exchange.getRequestBody().readAllBytes();
            var auth = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Authorization")).orElse("");
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
