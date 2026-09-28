package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.bootstrap.TradingCoreApplication;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.config.TradingProperties;
import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.marketdata.infrastructure.InMemoryLatestMarketDataStore;
import com.kitehybrid.platform.operator.application.*;
import com.kitehybrid.platform.operator.infrastructure.PostgresOperationalReadiness;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.order.infrastructure.PostgresOrderRepository;
import com.kitehybrid.platform.risk.application.RiskService;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import com.kitehybrid.platform.shared.domain.TradingMode;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static com.kitehybrid.platform.order.application.ExecutionDenialReason.*;
import static com.kitehybrid.platform.order.application.ExecutionReadiness.Gate.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Test-only observation harness. Deliberately has no execution invocation, even on READY. */
@Testcontainers
@Isolated
class OperatorPreflightDryRunTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    static final Instant NOW = Instant.parse("2026-09-28T06:00:00Z");
    static final Instrument INSTRUMENT = Instrument.create(new BrokerInstrumentId("SYNTHETIC", "101"),
            "PREFLIGHT_ONLY", "NSE", "CASH", InstrumentType.CASH, Optional.empty(), Optional.empty(),
            new BigDecimal("0.05"), 1);
    static TimeZone previousZone;
    @BeforeAll static void utc() { previousZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")); }
    @AfterAll static void restoreZone() { TimeZone.setDefault(previousZone); }

    /** Every fixture gets a NEW database inside this class's container; no deployment settings are read. */
    final class Harness implements AutoCloseable {
        final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicInteger gatewayCalls = new AtomicInteger();
        final List<String> http = new CopyOnWriteArrayList<>();
        final List<String> sql = new CopyOnWriteArrayList<>();
        final Clock clock = mock(Clock.class);
        final InMemoryInstrumentRegistry registry = new InMemoryInstrumentRegistry();
        final InMemoryLatestMarketDataStore market = new InMemoryLatestMarketDataStore();
        final KiteSession session;
        final KiteTradingReadAdapter reads = mock(KiteTradingReadAdapter.class);
        final HttpServer server;
        final String url;
        final JdbcTemplate jdbc;
        final Map<String, String> overrides = new LinkedHashMap<>();
        PublicationPermit permit = new PublicationPermit();
        MarketDataHealth health = healthy();
        ConfigurableApplicationContext context;
        OrderRepository orders;
        OrderApplicationService application;
        OperatorExecutionService operator;
        OrderId id;

        Harness(String... properties) throws Exception {
            when(clock.instant()).thenAnswer(call -> now.get());
            when(clock.getZone()).thenReturn(ZoneOffset.UTC);
            session = new KiteSession(new KiteProperties("syntheticKey", "syntheticSecret", "", true), clock);
            registry.replace(List.of(INSTRUMENT), NOW);
            tick("10.00", NOW, Optional.empty());
            when(reads.positions()).thenReturn(new BrokerPositions(List.of(), List.of()));
            when(reads.holdings()).thenReturn(List.of());
            when(reads.orders()).thenReturn(List.of());
            when(reads.trades()).thenReturn(List.of());
            when(reads.margins()).thenReturn(margins());
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                http.add(exchange.getRequestMethod());
                exchange.sendResponseHeaders(503, -1); exchange.close();
            });
            server.start();
            String name = "dry_run_" + UUID.randomUUID().toString().replace("-", "");
            var admin = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
            admin.execute("CREATE DATABASE " + name);
            url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name);
            jdbc = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
            for (String property : properties) { var pair = property.split("=", 2); overrides.put(pair[0], pair[1]); }
            try { boot(); } catch (RuntimeException | Error failure) { close(); throw failure; }
            // A transient SUBMITTING transition fails at the DB boundary, even if later rolled back.
            jdbc.execute("ALTER TABLE trading.orders ADD CONSTRAINT dry_run_no_submission CHECK (state <> 'SUBMITTING' AND broker_order_id IS NULL)");
        }

        void boot() {
            var properties = new LinkedHashMap<String, String>();
            properties.put("spring.profiles.active", "development");
            properties.put("spring.datasource.url", url);
            properties.put("spring.datasource.username", POSTGRES.getUsername());
            properties.put("spring.datasource.password", POSTGRES.getPassword());
            properties.put("spring.flyway.default-schema", "public");
            properties.put("kite.rest-enabled", "false");
            properties.put("kite.api-key", ""); properties.put("kite.api-secret", ""); properties.put("kite.access-token", "");
            properties.put("kite.auth.encryption-key", "");
            properties.put("kite.market-data.enabled", "false");
            properties.put("kite.market-data.diagnostic-enabled", "false");
            properties.put("kite.trading-read.diagnostic-enabled", "false");
            properties.put("universe.diagnostic.enabled", "false");
            properties.put("kite.trading-read.enabled", "true");
            properties.put("kite.order-execution.enabled", "true");
            properties.put("kite.order-execution.allowed-instruments", INSTRUMENT.id().value().toString());
            properties.put("kite.order-execution.max-quantity", "2");
            properties.put("kite.order-execution.max-notional", "20");
            properties.put("kite.order-execution.risk-decision-max-age", "60s");
            properties.put("kite.order-execution.market-data-max-age", "5s");
            properties.put("kite.operator-control.enabled", "true");
            properties.put("kite.live-test.enabled", "true");
            properties.put("kite.live-test.allowed-instruments", INSTRUMENT.id().value().toString());
            properties.put("kite.live-test.max-quantity", "2");
            properties.put("kite.live-test.max-notional", "20");
            properties.put("kite.live-test.arm-max-duration", "30s");
            properties.put("risk.enabled", "true"); properties.put("risk.max-order-quantity", "100");
            properties.put("risk.max-order-value", "10000"); properties.put("risk.max-position-quantity", "100");
            properties.put("risk.max-exposure", "10000"); properties.put("risk.market-data-max-age", "60s");
            properties.put("risk.registry-max-age", "60s"); properties.put("risk.price-buffer", "1");
            properties.put("risk.cash-reserve", "1"); properties.put("trading.mode", "PAPER");
            properties.put("trading.enable-live-trading", "false"); properties.put("trading.emergency-stop", "false");
            properties.put("logging.level.root", "ERROR"); properties.put("spring.main.banner-mode", "off");
            properties.putAll(overrides);
            context = new SpringApplicationBuilder(TradingCoreApplication.class).web(WebApplicationType.NONE)
                    .initializers(c -> c.addBeanFactoryPostProcessor(factory -> {
                        var beans = (DefaultListableBeanFactory) factory;
                        replace(beans, "clock", clock);
                        replace(beans, "kiteSession", session);
                        replace(beans, "kiteAuthenticationGateway", mock(com.kitehybrid.platform.broker.application.auth.KiteAuthenticationGateway.class,
                                call -> { throw new AssertionError("Dry run reached authentication transport"); }));
                        replace(beans, "kiteWebSocketTransport", mock(KiteWebSocketTransport.class));
                        replace(beans, "instrumentRegistry", registry);
                        replace(beans, "latestMarketDataStore", market);
                        var gateway = mock(MarketDataGateway.class);
                        when(gateway.health()).thenAnswer(call -> health);
                        replace(beans, "marketDataGateway", gateway);
                        // Even accidental REST traffic is confined to loopback and counted.
                        replace(beans, "kiteRestTransport", new KiteRestTransport(RestClient.builder()
                                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build(), session));
                        replace(beans, "kiteTradingReadAdapter", reads);
                        if (beans.containsBeanDefinition("kiteOrderAdapter")) {
                            replace(beans, "kiteOrderAdapter", mock(OrderExecutionGateway.class, call -> {
                                gatewayCalls.incrementAndGet(); throw new AssertionError("Dry run reached execution gateway");
                            }));
                        }
                        var trading = mock(TradingProperties.class);
                        when(trading.emergencyStop()).thenAnswer(call -> stop.get());
                        when(trading.mode()).thenReturn(TradingMode.PAPER);
                        for (String name : beans.getBeanNamesForType(TradingProperties.class, false, false)) replace(beans, name, trading);
                        beans.addBeanPostProcessor(new BeanPostProcessor() {
                            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                                if (name.equals("dataSource") && bean instanceof javax.sql.DataSource source)
                                    return new ObservedDataSource(source,sql);
                                return bean instanceof OrderApplicationService || bean instanceof PostgresOrderRepository ? spy(bean) : bean;
                            }
                        });
                    })).run(properties.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue()).toArray(String[]::new));
            session.install(new KiteAccessToken("syntheticToken", NOW.minusSeconds(1), NOW.plusSeconds(3600)));
            session.profileValidated();
            orders = context.getBean(OrderRepository.class);
            application = context.getBean(OrderApplicationService.class);
            operator = context.getBean(OperatorExecutionService.class);
            assertInstanceOf(PostgresOrderRepository.class, orders);
            assertInstanceOf(PostgresOperationalReadiness.class, context.getBean(OperationalReadiness.class));
            assertNotNull(context.getBean(ExecutionSafetyPolicy.class));
            assertFalse(context.getBean(RuntimeExecutionArming.class).armed(now.get()));
        }

        void approve(long quantity, String limit) {
            assertNull(id, "Exactly one primary synthetic order per fixture");
            var placed = application.place(command("dry-run", quantity, limit));
            id = placed.id();
            assertEquals(OrderState.VALIDATED, placed.state());
            var decision = context.getBean(RiskService.class).evaluate(id);
            assertTrue(decision.approved(), decision.toString());
            assertEquals(OrderState.RISK_APPROVED, orders.find(id).orElseThrow().state());
            assertTrue(orders.find(id).orElseThrow().brokerCorrelationId().isPresent());
            assertZeroMutation();
        }
        void arm() { assertTrue(operator.arm(id, Duration.ofSeconds(30)).armed()); assertZeroMutation(); }
        void tick(String price, Instant received, Optional<Instant> exchange) {
            permit.revoke(); permit = new PublicationPermit();
            assertTrue(market.update(new Tick(INSTRUMENT.id(), new BigDecimal(price), received, exchange, Optional.empty(), Optional.empty()), permit));
        }
        ExecutionReadiness observe() {
            var before = snapshot();
            int readCalls = mockingDetails(reads).getInvocations().size();
            sql.clear();
            var report = operator.preflight(id);
            var queries = List.copyOf(sql);
            assertTrue(queries.size() <= 9, queries.toString());
            for (String query : queries) {
                String normalized = query.strip().toUpperCase(Locale.ROOT);
                assertTrue(normalized.startsWith("SELECT") || normalized.startsWith("WITH"), query);
                assertFalse(normalized.contains("FOR UPDATE") || normalized.contains("PG_ADVISORY"), query);
            }
            if (report.ready()) assertEquals(8,queries.size(), "Explicit Flyway schema: eight SELECT statements");
            assertEquals(EnumSet.allOf(ExecutionReadiness.Gate.class), report.gates().keySet());
            assertEquals(before, snapshot(), "Preflight must leave all durable evidence unchanged");
            assertEquals(readCalls, mockingDetails(reads).getInvocations().size(), "Preflight must not perform broker reads");
            assertZeroMutation();
            return report;
        }
        List<Object> snapshot() {
            return List.of(jdbc.queryForList("SELECT * FROM trading.orders ORDER BY order_id"),
                    jdbc.queryForList("SELECT * FROM trading.risk_decisions ORDER BY order_id"),
                    jdbc.queryForList("SELECT * FROM trading.execution_authorizations ORDER BY authorization_id"),
                    jdbc.queryForList("SELECT * FROM trading.reconciliation_decisions ORDER BY reconciliation_id"),
                    jdbc.queryForList("SELECT * FROM " + (jdbc.queryForObject("SELECT to_regclass('trading.reconciliation_trades') IS NOT NULL",Boolean.class)
                            ? "trading.reconciliation_trades" : "trading.unavailable_trades") + " ORDER BY broker_trade_id,broker_order_id"));
        }
        void assertZeroMutation() {
            assertEquals(0, gatewayCalls.get());
            for (String method : List.of("POST", "PUT", "DELETE")) assertEquals(0, http.stream().filter(method::equals).count(), method);
            assertTrue(http.isEmpty(), "No broker HTTP, including reads, belongs to this harness");
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM trading.execution_authorizations", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM trading.orders WHERE state='SUBMITTING' OR broker_order_id IS NOT NULL", Integer.class));
            for (Object bean : List.of(application, orders)) {
                assertTrue(mockingDetails(bean).getInvocations().stream().noneMatch(i -> Set.of(
                        "executeRiskApproved", "beginSubmission", "attachBrokerOrderId").contains(i.getMethod().getName())));
            }
        }
        void denied(ExecutionReadiness.Gate gate, ExecutionDenialReason reason) {
            var report = observe(); assertFalse(report.ready()); assertEquals("NOT_READY", report.status());
            assertEquals(reason, report.gates().get(gate), report.toString());
        }
        @Override public void close() { if (context != null) context.close(); server.stop(0); }
    }

    /** Records SQL shapes only, never parameters; delegates transaction semantics unchanged. */
    static final class ObservedDataSource extends org.springframework.jdbc.datasource.DelegatingDataSource {
        private final List<String> sql;
        ObservedDataSource(javax.sql.DataSource source, List<String> sql) { super(source); this.sql=sql; }
        @Override public java.sql.Connection getConnection() throws java.sql.SQLException { return observe(super.getConnection()); }
        @Override public java.sql.Connection getConnection(String user, String password) throws java.sql.SQLException {
            return observe(super.getConnection(user,password));
        }
        private java.sql.Connection observe(java.sql.Connection connection) {
            return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{java.sql.Connection.class}, (proxy,method,args) -> {
                        if (method.getName().equals("prepareStatement")) sql.add((String) args[0]);
                        Object result = invoke(connection,method,args);
                        if (method.getName().equals("createStatement")) {
                            var statement = (java.sql.Statement) result;
                            return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Statement.class},
                                    (p,m,a) -> {
                                        if (m.getName().startsWith("execute") && a != null && a.length > 0 && a[0] instanceof String query) sql.add(query);
                                        return invoke(statement,m,a);
                                    });
                        }
                        return result;
                    });
        }
        private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
            try { return method.invoke(target,args); } catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
        }
    }

    static void replace(DefaultListableBeanFactory beans, String name, Object value) {
        if (beans.containsBeanDefinition(name)) beans.removeBeanDefinition(name);
        beans.registerSingleton(name, value);
    }
    static PlaceOrder command(String key, long quantity, String limit) {
        return new PlaceOrder(key, INSTRUMENT.id(), OrderSide.BUY, quantity, limit == null ? OrderType.MARKET : OrderType.LIMIT,
                OrderProduct.DELIVERY, OrderValidity.DAY, Optional.ofNullable(limit).map(BigDecimal::new), Optional.empty(), 0, OrderVariety.REGULAR);
    }
    static MarketDataHealth healthy() { return health(MarketDataGateway.State.CONNECTED, MarketDataHealth.Status.FRESH, MarketDataHealth.Reason.NONE, 1, 1); }
    static MarketDataHealth health(MarketDataGateway.State state, MarketDataHealth.Status status, MarketDataHealth.Reason reason, int desired, int active) {
        return new MarketDataHealth(state, status, reason, Optional.of(NOW), Optional.of(NOW), Optional.of(NOW), desired, active, 0, 1, 1, 0, 0, 0, 0);
    }
    static BrokerMargins margins() {
        var z = BigDecimal.ZERO; var cash = new BigDecimal("100000");
        var used = new BrokerMargins.UtilisedMargin(z,z,z,z,z,z,z,z,z,z,z,z);
        var available = new BrokerMargins.AvailableMargin(z,cash,cash,cash,z,z);
        var segment = new BrokerMargins.SegmentMargin(true,cash,available,used);
        return new BrokerMargins(Map.of(TradingReadTypes.MarginSegment.EQUITY,segment,TradingReadTypes.MarginSegment.COMMODITY,segment));
    }

    @Test void fullSpringDisarmedThenReadyAndRestartNeverAuthorizeOrSubmit() throws Exception {
        try (var h = new Harness()) {
            h.approve(2, null);
            var disarmed = h.observe(); assertEquals(DISARMED, disarmed.reason());
            disarmed.gates().forEach((gate, reason) -> assertEquals(
                    gate == RUNTIME_ARMED || gate == SESSION_BOUND ? DISARMED : NONE, reason, gate.name()));
            h.arm(); assertTrue(h.observe().ready());
            assertEquals(OrderState.RISK_APPROVED, h.orders.find(h.id).orElseThrow().state());
            h.context.close(); h.boot();
            assertEquals(OrderState.RISK_APPROVED, h.orders.find(h.id).orElseThrow().state());
            assertEquals(DISARMED, h.observe().reason());
        }
    }

    @ParameterizedTest @EnumSource(ExecutionReadiness.Gate.class)
    void everyCurrentGateHasAnExplicitDeterministicDenial(ExecutionReadiness.Gate gate) throws Exception {
        String property = switch (gate) {
            case EXECUTION_CAPABILITY_CONFIGURED -> "kite.order-execution.enabled=false";
            case OPERATOR_CONTROL_ENABLED -> "kite.operator-control.enabled=false";
            case INSTRUMENT_ALLOWED -> "kite.order-execution.allowed-instruments=";
            case QUANTITY_WITHIN_CAP -> "kite.order-execution.max-quantity=1";
            case NOTIONAL_WITHIN_CAP -> "kite.order-execution.max-notional=19.99";
            case LIVE_TEST_MODE_ENABLED -> "kite.live-test.enabled=false";
            case LIVE_TEST_INSTRUMENT_ALLOWED -> "kite.live-test.allowed-instruments=";
            case LIVE_TEST_QUANTITY_WITHIN_CAP -> "kite.live-test.max-quantity=1";
            case LIVE_TEST_NOTIONAL_WITHIN_CAP -> "kite.live-test.max-notional=19.99";
            case LIVE_TEST_ARM_DURATION_VALID -> "kite.live-test.arm-max-duration=0s";
            case DATABASE_READY -> "spring.flyway.target=9";
            default -> "logging.level.root=ERROR";
        };
        try (var h = new Harness(property)) {
            h.approve(2, null);
            // Configuration denials can make arming impossible; assertions target the specific gate.
            h.operator.arm(h.id, Duration.ofSeconds(30));
            ExecutionDenialReason reason = switch (gate) {
                case EXECUTION_CAPABILITY_CONFIGURED -> EXECUTION_DISABLED;
                case OPERATOR_CONTROL_ENABLED -> OPERATOR_CONTROL_DISABLED;
                case LIVE_TEST_MODE_ENABLED -> LIVE_TEST_DISABLED;
                case INSTRUMENT_ALLOWED -> INSTRUMENT_NOT_ALLOWED;
                case QUANTITY_WITHIN_CAP -> QUANTITY_CAP_EXCEEDED;
                case NOTIONAL_WITHIN_CAP -> NOTIONAL_CAP_EXCEEDED;
                case LIVE_TEST_INSTRUMENT_ALLOWED -> LIVE_TEST_INSTRUMENT_DENIED;
                case LIVE_TEST_QUANTITY_WITHIN_CAP -> LIVE_TEST_QUANTITY_CAP;
                case LIVE_TEST_NOTIONAL_WITHIN_CAP -> LIVE_TEST_NOTIONAL_CAP;
                case LIVE_TEST_ARM_DURATION_VALID -> ARM_DURATION_INVALID;
                case DATABASE_READY -> DATABASE_NOT_READY;
                case AUTHENTICATED -> { h.session.clear(); yield AUTHENTICATION_UNAVAILABLE; }
                case RUNTIME_ARMED -> { h.operator.disarm(); yield DISARMED; }
                case SESSION_BOUND -> { h.session.install(new KiteAccessToken("replacementSynthetic", NOW, NOW.plusSeconds(3600))); h.session.profileValidated(); yield DISARMED; }
                case EMERGENCY_STOP_CLEAR -> { h.stop.set(true); yield EMERGENCY_STOP; }
                case ORDER_RISK_APPROVED -> { h.jdbc.update("UPDATE trading.orders SET state='VALIDATED' WHERE order_id=?",h.id.value()); yield INVALID_ORDER_STATE; }
                case RISK_DECISION_CURRENT -> { h.jdbc.update("DELETE FROM trading.risk_decisions WHERE order_id=?",h.id.value()); yield RISK_APPROVAL_MISSING; }
                case CORRELATION_PRESENT -> { h.jdbc.update("UPDATE trading.orders SET broker_correlation_id=NULL WHERE order_id=?",h.id.value()); yield CORRELATION_MISSING; }
                case RECONCILIATION_CLEAR -> {
                    var blocker = h.application.place(command("synthetic-blocker",1,null));
                    h.jdbc.update("UPDATE trading.orders SET state='OPEN' WHERE order_id=?",blocker.id().value()); yield RECONCILIATION_REQUIRED;
                }
                case MARKET_DATA_HEALTHY -> { h.health = null; yield MARKET_DATA_UNAVAILABLE; }
                case MARKET_DATA_FRESH -> { h.now.set(NOW.plusSeconds(5)); yield MARKET_DATA_STALE; }
                case RECONCILIATION_STORE_HEALTHY -> { h.jdbc.execute("ALTER TABLE trading.reconciliation_trades RENAME TO unavailable_trades"); yield RECONCILIATION_STORE_UNAVAILABLE; }
                case RECONCILIATION_CONFLICT_CLEAR -> { conflict(h,"CONFLICT"); yield RECONCILIATION_CONFLICT; }
                case TRADING_READ_AVAILABLE -> {
                    ((DefaultListableBeanFactory) h.context.getBeanFactory()).destroySingleton("kiteTradingReadAdapter"); yield TRADING_READ_UNAVAILABLE;
                }
            };
            h.denied(gate, reason);
        }
    }

    static void conflict(Harness h, String outcome) {
        h.jdbc.update("INSERT INTO trading.reconciliation_decisions VALUES (gen_random_uuid(),?,'RISK_APPROVED',NULL,?,'ORDER_IDENTITY_CONFLICT',?,2)",
                h.id.value(),outcome,Timestamp.from(NOW));
    }

    @ParameterizedTest @ValueSource(strings={"market", "stop", "arm", "session", "version", "conflict"})
    void readyIsOnlyAnObservation(String change) throws Exception {
        try (var h = new Harness()) {
            h.approve(2,null); h.arm(); assertTrue(h.observe().ready());
            switch (change) {
                case "market" -> { h.now.set(NOW.plusSeconds(5)); h.denied(MARKET_DATA_FRESH,MARKET_DATA_STALE); }
                case "stop" -> { h.stop.set(true); h.denied(EMERGENCY_STOP_CLEAR,EMERGENCY_STOP); }
                case "arm" -> { h.now.set(NOW.plusSeconds(30)); h.denied(RUNTIME_ARMED,DISARMED); }
                case "session" -> { h.session.install(new KiteAccessToken("replacementSynthetic",NOW,NOW.plusSeconds(3600))); h.session.profileValidated(); h.denied(SESSION_BOUND,DISARMED); }
                case "version" -> { h.jdbc.update("UPDATE trading.orders SET version=version+1 WHERE order_id=?",h.id.value()); h.denied(RISK_DECISION_CURRENT,ORDER_VERSION_CHANGED); }
                case "conflict" -> { conflict(h,"CONFLICT"); h.denied(RECONCILIATION_CONFLICT_CLEAR,RECONCILIATION_CONFLICT); }
                default -> fail(change);
            }
        }
    }

    @Test void boundedArmingExpiryAndIdempotentDisarmHaveNoDurableSideEffects() throws Exception {
        try (var h = new Harness()) {
            h.approve(2,null); var before = h.snapshot();
            for (var duration : List.of(Duration.ZERO,Duration.ofNanos(-1),Duration.ofSeconds(30).plusNanos(1))) {
                h.arm(); var result = h.operator.arm(h.id, duration);
                assertFalse(result.armed()); assertEquals(ARM_DURATION_INVALID,result.reason());
                assertEquals(before,h.snapshot()); h.assertZeroMutation();
            }
            h.arm(); h.now.set(NOW.plusSeconds(30).minusNanos(1));
            assertTrue(h.context.getBean(RuntimeExecutionArming.class).armed(h.now.get()));
            h.now.set(NOW.plusSeconds(30)); h.denied(RUNTIME_ARMED,DISARMED);
            assertEquals(h.operator.disarm(),h.operator.disarm());
            h.now.set(NOW); h.stop.set(true);
            assertEquals(EMERGENCY_STOP,h.operator.arm(h.id, Duration.ofSeconds(1)).reason());
            assertEquals(before,h.snapshot()); h.assertZeroMutation();
        }
    }

    @ParameterizedTest @ValueSource(strings={"missing", "rejected", "expired", "future", "version", "policy", "just-fresh", "current"})
    void persistedRiskEvidenceBoundaries(String change) throws Exception {
        try (var h = new Harness()) {
            h.approve(2,null); h.arm();
            var reason = switch (change) {
                case "missing" -> { h.jdbc.update("DELETE FROM trading.risk_decisions WHERE order_id=?",h.id.value()); yield RISK_APPROVAL_MISSING; }
                case "rejected" -> { h.jdbc.update("UPDATE trading.risk_decisions SET outcome='REJECTED',reason='ORDER_VALUE_LIMIT' WHERE order_id=?",h.id.value()); yield RISK_APPROVAL_MISSING; }
                case "expired" -> { riskTime(h,NOW.minusSeconds(60)); yield RISK_APPROVAL_EXPIRED; }
                case "future" -> { riskTime(h,NOW.plusNanos(1000)); yield RISK_APPROVAL_EXPIRED; }
                case "version" -> { h.jdbc.update("UPDATE trading.risk_decisions SET order_version=0 WHERE order_id=?",h.id.value()); yield ORDER_VERSION_CHANGED; }
                case "policy" -> { h.jdbc.update("UPDATE trading.risk_decisions SET policy_version=? WHERE order_id=?","cash-v1:" + "0".repeat(64),h.id.value()); yield RISK_POLICY_MISMATCH; }
                case "just-fresh" -> { riskTime(h,NOW.minusSeconds(60).plusNanos(1000)); yield NONE; }
                case "current" -> NONE;
                default -> throw new AssertionError(change);
            };
            if (reason == NONE) assertTrue(h.observe().ready()); else h.denied(RISK_DECISION_CURRENT,reason);
        }
    }
    static void riskTime(Harness h, Instant at) {
        h.jdbc.update("UPDATE trading.risk_decisions SET evaluated_at=? WHERE order_id=?",Timestamp.from(at),h.id.value());
    }

    @ParameterizedTest @ValueSource(strings={"stopped", "starting", "no-active", "mismatch", "no-tick", "stale", "future",
            "exchange-stale", "exchange-future", "degraded", "generation", "mode", "subscription", "just-fresh", "fresh"})
    void marketEvidenceAndPublicationFences(String change) throws Exception {
        try (var h = new Harness()) {
            h.approve(2,null); h.arm();
            ExecutionReadiness.Gate gate = MARKET_DATA_HEALTHY;
            ExecutionDenialReason reason = MARKET_DATA_UNAVAILABLE;
            switch (change) {
                case "stopped" -> h.health = health(MarketDataGateway.State.STOPPED,MarketDataHealth.Status.STOPPED,MarketDataHealth.Reason.NONE,1,0);
                case "starting" -> h.health = health(MarketDataGateway.State.STARTING,MarketDataHealth.Status.STARTING,MarketDataHealth.Reason.NONE,1,0);
                case "no-active" -> h.health = health(MarketDataGateway.State.CONNECTED,MarketDataHealth.Status.FRESH,MarketDataHealth.Reason.NONE,1,0);
                case "mismatch" -> h.health = health(MarketDataGateway.State.CONNECTED,MarketDataHealth.Status.FRESH,MarketDataHealth.Reason.NONE,2,1);
                case "no-tick", "generation", "mode", "subscription" -> {
                    h.permit.revoke();
                    assertFalse(h.market.update(new Tick(INSTRUMENT.id(),BigDecimal.TEN,NOW.plusNanos(1)),h.permit));
                    assertTrue(h.market.latest(INSTRUMENT.id()).isEmpty());
                }
                case "degraded" -> h.health = health(MarketDataGateway.State.CONNECTED,MarketDataHealth.Status.DEGRADED,MarketDataHealth.Reason.BACKPRESSURE,1,1);
                case "stale" -> { h.tick("10",NOW.minusSeconds(5),Optional.empty()); gate=MARKET_DATA_FRESH; reason=MARKET_DATA_STALE; }
                case "future" -> { h.tick("10",NOW.plusNanos(1),Optional.empty()); gate=MARKET_DATA_FRESH; reason=MARKET_DATA_STALE; }
                case "exchange-stale" -> { h.tick("10",NOW,Optional.of(NOW.minusSeconds(5))); gate=MARKET_DATA_FRESH; reason=MARKET_DATA_STALE; }
                case "exchange-future" -> { h.tick("10",NOW,Optional.of(NOW.plusNanos(1))); gate=MARKET_DATA_FRESH; reason=MARKET_DATA_STALE; }
                case "just-fresh" -> { h.tick("10",NOW.minusSeconds(5).plusNanos(1),Optional.of(NOW.minusSeconds(5).plusNanos(1))); reason=NONE; }
                case "fresh" -> reason=NONE;
                default -> fail(change);
            }
            if (reason == NONE) assertTrue(h.observe().ready()); else h.denied(gate,reason);
        }
    }

    @ParameterizedTest @CsvSource({"normal,2,true", "normal,3,false", "live,2,true", "live,3,false"})
    void quantityCapsAreInclusive(String cap, long quantity, boolean ready) throws Exception {
        try (var h = new Harness("kite.order-execution.max-notional=100", "kite.live-test.max-notional=100",
                cap.equals("normal") ? "kite.live-test.max-quantity=100" : "kite.order-execution.max-quantity=100")) {
            h.approve(quantity,null); h.operator.arm(h.id, Duration.ofSeconds(30));
            if (ready) assertTrue(h.observe().ready()); else h.denied(cap.equals("normal") ? QUANTITY_WITHIN_CAP : LIVE_TEST_QUANTITY_WITHIN_CAP,
                    cap.equals("normal") ? QUANTITY_CAP_EXCEEDED : LIVE_TEST_QUANTITY_CAP);
        }
    }

    @ParameterizedTest @CsvSource({"normal,20,true", "normal,20.000000000000000001,false", "live,20,true", "live,20.000000000000000001,false"})
    void notionalCapsUseExactDecimalEvidence(String cap, String price, boolean ready) throws Exception {
        try (var h = new Harness(cap.equals("normal") ? "kite.live-test.max-notional=100" : "kite.order-execution.max-notional=100")) {
            h.approve(1,null); h.arm(); h.tick(price,NOW,Optional.empty());
            if (ready) assertTrue(h.observe().ready()); else h.denied(cap.equals("normal") ? NOTIONAL_WITHIN_CAP : LIVE_TEST_NOTIONAL_WITHIN_CAP,
                    cap.equals("normal") ? NOTIONAL_CAP_EXCEEDED : LIVE_TEST_NOTIONAL_CAP);
        }
    }

    @ParameterizedTest @CsvSource({"20,10,true", "20.05,10,false", "10,20,true", "10,20.05,false"})
    void limitValuationUsesMaximumOfPersistedLimitAndAcceptedMarket(String limit, String market, boolean ready) throws Exception {
        try (var h = new Harness()) {
            h.approve(1,limit); h.operator.arm(h.id, Duration.ofSeconds(30)); h.tick(market,NOW,Optional.empty());
            if (ready) assertTrue(h.observe().ready()); else {
                var report = h.observe(); assertFalse(report.ready());
                assertEquals(NOTIONAL_CAP_EXCEEDED,report.gates().get(NOTIONAL_WITHIN_CAP));
                assertEquals(LIVE_TEST_NOTIONAL_CAP,report.gates().get(LIVE_TEST_NOTIONAL_WITHIN_CAP));
            }
        }
    }

    @ParameterizedTest @ValueSource(strings={"AMBIGUOUS", "CONFLICT", "BROKER_ORDER_MISSING", "BROKER_STATE_UNAVAILABLE"})
    void anyRecordedReconciliationBlockerDeniesWithoutReconciliation(String outcome) throws Exception {
        try (var h = new Harness()) {
            h.approve(2,null); h.arm(); assertTrue(h.observe().ready());
            conflict(h,outcome); h.denied(RECONCILIATION_CONFLICT_CLEAR,RECONCILIATION_CONFLICT);
        }
    }

    @ParameterizedTest @ValueSource(strings={"orders", "trades", "both", "neither"})
    void readCapabilityPresenceDoesNotClaimSuccessfulLiveRead(String missing) throws Exception {
        try (var h = new Harness()) {
            h.approve(2,null); h.arm();
            var beans = (DefaultListableBeanFactory) h.context.getBeanFactory();
            beans.destroySingleton("kiteTradingReadAdapter");
            if (!Set.of("orders","both").contains(missing)) beans.registerSingleton("syntheticOrders",mock(com.kitehybrid.platform.broker.application.read.BrokerOrdersProvider.class));
            if (!Set.of("trades","both").contains(missing)) beans.registerSingleton("syntheticTrades",mock(com.kitehybrid.platform.broker.application.read.BrokerTradesProvider.class));
            if (missing.equals("neither")) assertTrue(h.observe().ready()); else h.denied(TRADING_READ_AVAILABLE,TRADING_READ_UNAVAILABLE);
        }
    }

    @Test void malformedV10IndexFailsThroughRealPreflight() throws Exception {
        try (var h = new Harness()) {
            h.approve(2,null); h.arm(); assertTrue(h.observe().ready());
            h.jdbc.execute("ALTER INDEX trading.orders_broker_order_id_unique RENAME TO original_identity_index");
            h.jdbc.execute("CREATE INDEX orders_broker_order_id_unique ON trading.orders(broker_order_id)");
            h.denied(DATABASE_READY,DATABASE_NOT_READY);
        }
    }

    @Test void missingAndWrongAuthoritativeHistoryNeverFallBackOrRepair() throws Exception {
        try (var h = new Harness()) {
            h.approve(2,null); h.arm(); assertTrue(h.observe().ready());
            var history = h.jdbc.queryForList("SELECT * FROM public.flyway_schema_history ORDER BY installed_rank");
            assertFalse(new PostgresOperationalReadiness(h.jdbc,()->true,"absent_schema","flyway_schema_history").inspect().databaseReady());
            assertFalse(new PostgresOperationalReadiness(h.jdbc,()->true,"trading","flyway_schema_history").inspect().databaseReady());
            assertFalse(new PostgresOperationalReadiness(h.jdbc,()->true,"public","absent_history").inspect().databaseReady());
            assertEquals(history,h.jdbc.queryForList("SELECT * FROM public.flyway_schema_history ORDER BY installed_rank"));
            assertTrue(h.observe().ready());
        }
    }

    @Test void readinessLoggingAndMetricsContainOnlyBoundedLabels() throws Exception {
        try (var h = new Harness()) {
            h.approve(2,null);
            var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OperatorExecutionService.class);
            var oldLevel = logger.getLevel(); var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
            appender.start(); logger.addAppender(appender); logger.setLevel(ch.qos.logback.classic.Level.INFO);
            try {
                assertEquals(DISARMED,h.observe().reason());
                assertEquals(List.of("Operator control action=PREFLIGHT_DENIED reason=DISARMED"),
                        appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList());
                h.arm(); appender.list.clear(); assertTrue(h.observe().ready()); assertTrue(appender.list.isEmpty());
            } finally { logger.detachAppender(appender); logger.setLevel(oldLevel); appender.stop(); }
            var metrics = h.context.getBean(io.micrometer.core.instrument.MeterRegistry.class);
            assertTrue(metrics.find("execution.authorization").meters().isEmpty());
            for (var meter : metrics.getMeters()) if (meter.getId().getName().startsWith("execution.")) {
                assertTrue(meter.getId().getTags().stream().allMatch(t -> Set.of("result","reason").contains(t.getKey())));
            }
        }
    }

    @Test void harnessHasNoExecutionOrOperatorTransportInvocation() {
        var types = new ArrayList<Class<?>>(); types.add(OperatorPreflightDryRunTest.class);
        for (int i=0;i<types.size();i++) types.addAll(List.of(types.get(i).getDeclaredClasses()));
        var classes = new com.tngtech.archunit.core.importer.ClassFileImporter().importClasses(types);
        for (var type : classes) for (var call : type.getMethodCallsFromSelf()) {
            String owner = call.getTargetOwner().getName();
            assertFalse(owner.startsWith("com.kitehybrid.platform.") && Set.of("execute", "executeRiskApproved", "beginSubmission")
                    .contains(call.getTarget().getName()),call.toString());
            assertFalse(Set.of("OrderExecutionGateway", "KiteOrderAdapter", "KiteRestTransport").contains(call.getTargetOwner().getSimpleName()),call.toString());
        }
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web.bind.annotation..", "org.springframework.scheduling.annotation..").check(classes);
    }
}
