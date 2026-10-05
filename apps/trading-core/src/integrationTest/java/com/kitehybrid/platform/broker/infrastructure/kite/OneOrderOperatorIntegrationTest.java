package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.*;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.config.TradingProperties;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.marketdata.infrastructure.InMemoryLatestMarketDataStore;
import com.kitehybrid.platform.operator.application.*;
import com.kitehybrid.platform.operator.console.TrustedOperatorConsole;
import com.kitehybrid.platform.operator.infrastructure.OperatorControlConfiguration;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.order.infrastructure.OrderConfiguration;
import com.kitehybrid.platform.risk.application.RiskService;
import com.kitehybrid.platform.risk.domain.*;
import com.kitehybrid.platform.risk.infrastructure.RiskConfiguration;
import com.kitehybrid.platform.reconciliation.application.OrderReconciliationService;
import com.kitehybrid.platform.reconciliation.infrastructure.ReconciliationConfiguration;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Every HTTP mutation is a loopback request. No production launcher or real deployment is started. */
@Testcontainers @Isolated
class OneOrderOperatorIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres","-c","fsync=off","-c","timezone=UTC");
    static final Instant NOW=Instant.parse("2026-09-28T06:00:00Z");
    static final Instrument INSTRUMENT=Instrument.create(new BrokerInstrumentId("ZERODHA","101"),"SYNTHETIC_ONLY","NSE","CASH",
            InstrumentType.CASH,Optional.empty(),Optional.empty(),new BigDecimal("0.05"),1);
    static TimeZone zone;
    @BeforeAll static void utc() { zone=TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")); }
    @AfterAll static void restoreZone() { TimeZone.setDefault(zone); }

    enum Mode { SUCCESS, REJECT, AUTH, MALFORMED, RESET, RESPONSE_LOST, TIMEOUT }
    final class Fixture implements AutoCloseable {
        final AtomicReference<Instant> now=new AtomicReference<>(NOW);
        final AtomicBoolean stop=new AtomicBoolean();
        final AtomicReference<com.kitehybrid.platform.shared.application.ExecutionInitialization> initialization =
                new AtomicReference<>(() -> true);
        final AtomicReference<com.kitehybrid.platform.broker.application.read.OrderMarginEstimator> marginEstimator =
                new AtomicReference<>(com.kitehybrid.platform.broker.application.read.OrderMarginEstimator.UNAVAILABLE);
        final AtomicInteger gatewayCalls=new AtomicInteger();
        volatile MarketDataHealth health=OperatorPreflightDryRunTest.healthy();
        final List<String> trace=new CopyOnWriteArrayList<>();
        final List<String> methods=new CopyOnWriteArrayList<>();
        final AtomicReference<String> form=new AtomicReference<>();
        final AtomicBoolean submittingObserved=new AtomicBoolean();
        final CountDownLatch received=new CountDownLatch(1), release=new CountDownLatch(1);
        final List<AnnotationConfigApplicationContext> contexts=new ArrayList<>();
        final Clock clock=mock(Clock.class);
        final InMemoryInstrumentRegistry registry=new InMemoryInstrumentRegistry();
        final InMemoryLatestMarketDataStore market=new InMemoryLatestMarketDataStore();
        final PublicationPermit publication=new PublicationPermit();
        final KiteTradingReadAdapter reads=mock(KiteTradingReadAdapter.class);
        final HttpServer server;
        final DriverManagerDataSource source;
        final JdbcTemplate jdbc;
        final Flyway flyway;
        final KiteSession session;
        final AnnotationConfigApplicationContext context;
        final OrderRepository orders;
        final OrderApplicationService application;
        final OperatorExecutionService operator;
        volatile Mode mode=Mode.SUCCESS;
        volatile Runnable beforeDispatch=()->{}, beforeGateway=()->{}, afterReceipt=()->{};
        OrderId id;

        final Instrument instrument;
        Fixture(String... overrides) throws Exception { this(INSTRUMENT, overrides); }
        Fixture(Instrument instrument, String... overrides) throws Exception {
            this.instrument = instrument;
            when(clock.instant()).thenAnswer(call->now.get()); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
            registry.replace(List.of(instrument),NOW);
            market.update(new Tick(instrument.id(),BigDecimal.TEN,NOW),publication);
            when(reads.positions()).thenReturn(new BrokerPositions(List.of(),List.of()));
            when(reads.holdings()).thenReturn(List.of()); when(reads.orders()).thenReturn(List.of());
            when(reads.trades()).thenReturn(List.of()); when(reads.margins()).thenReturn(OperatorPreflightDryRunTest.margins());
            var admin=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
            String name="one_order_"+UUID.randomUUID().toString().replace("-",""); admin.execute("CREATE DATABASE "+name);
            source=new DriverManagerDataSource(POSTGRES.getJdbcUrl().replace("/"+POSTGRES.getDatabaseName(),"/"+name),POSTGRES.getUsername(),POSTGRES.getPassword());
            jdbc=new JdbcTemplate(source);
            flyway=Flyway.configure().dataSource(source).defaultSchema("public").locations("classpath:db/migration").load(); flyway.migrate();
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/orders/regular",exchange->{
                methods.add(exchange.getRequestMethod()); trace.add("HTTP_"+exchange.getRequestMethod());
                form.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                submittingObserved.set(jdbc.queryForObject("SELECT state FROM trading.orders WHERE order_id=?",String.class,id.value()).equals("SUBMITTING"));
                received.countDown();
                afterReceipt.run();
                if (mode == Mode.RESET || mode == Mode.RESPONSE_LOST) { exchange.close(); return; }
                if (mode == Mode.TIMEOUT) { try { release.await(5,TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); } exchange.close(); return; }
                int status=mode == Mode.REJECT ? 400 : mode == Mode.AUTH ? 401 : 200;
                String body=mode == Mode.MALFORMED ? "{invalid" : status != 200 ? "synthetic sensitive response"
                        : "{\"status\":\"success\",\"data\":{\"order_id\":\"synthetic-only-broker-1\"}}";
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status,bytes.length);
                try(var output=exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start(); session=authenticated(); context=boot(session,overrides);
            orders=context.getBean(OrderRepository.class); application=context.getBean(OrderApplicationService.class);
            operator=context.getBean(OperatorExecutionService.class);
        }
        KiteSession authenticated() {
            var result=new KiteSession(new KiteProperties("syntheticKey","syntheticSecret","",true),clock);
            result.install(new KiteAccessToken("syntheticToken",NOW.minusSeconds(1),NOW.plusSeconds(3600))); result.profileValidated(); return result;
        }
        AnnotationConfigApplicationContext boot(KiteSession currentSession, String... overrides) {
            var c=new AnnotationConfigApplicationContext();
            c.setEnvironment(RehearsalIsolation.environment());
            TestPropertyValues.of("kite.order-execution.enabled=true", "kite.order-execution.allowed-instruments="+instrument.id().value(),
                    "kite.order-execution.max-quantity=1", "kite.order-execution.max-notional=20", "kite.order-execution.risk-decision-max-age=60s",
                    "kite.order-execution.market-data-max-age=5s", "kite.operator-control.enabled=true", "kite.live-test.enabled=true",
                    "kite.live-test.allowed-instruments="+instrument.id().value(), "kite.live-test.max-quantity=1", "kite.live-test.max-notional=20",
                    "kite.live-test.arm-max-duration=30s", "kite.trading-read.enabled=true", "risk.enabled=true", "risk.max-order-quantity=100",
                    "risk.max-order-value=10000", "risk.max-position-quantity=100", "risk.max-exposure=10000", "risk.market-data-max-age=60s",
                    "risk.registry-max-age=60s", "risk.cash-reserve=1").applyTo(c);
            TestPropertyValues.of(overrides).applyTo(c);
            c.registerBean(JdbcTemplate.class,()->new JdbcTemplate(source)); c.registerBean(Flyway.class,()->flyway);
            c.registerBean(Clock.class,()->clock); c.registerBean(MeterRegistry.class,SimpleMeterRegistry::new);
            c.registerBean(KiteAuthenticationSession.class,()->currentSession); c.registerBean(InstrumentRegistry.class,()->registry);
            c.registerBean(com.kitehybrid.platform.shared.application.ExecutionInitialization.class,
                    () -> () -> initialization.get().initializationReady());
            c.registerBean(com.kitehybrid.platform.broker.application.read.OrderMarginEstimator.class,
                    () -> request -> marginEstimator.get().estimate(request));
            c.registerBean(LatestMarketDataStore.class,()->market); c.registerBean(KiteTradingReadAdapter.class,()->reads);
            c.registerBean(MarketDataGateway.class,()->{ var gateway=mock(MarketDataGateway.class); when(gateway.health()).thenAnswer(call->health); return gateway; });
            c.registerBean(TradingProperties.class,()->{ var trading=mock(TradingProperties.class); when(trading.emergencyStop()).thenAnswer(call->stop.get()); return trading; });
            c.registerBean(RiskEngine.class,()->new RiskEngine(List.of(new PositiveReferencePriceRiskRule()),clock));
            if (c.getEnvironment().getProperty("kite.order-execution.enabled",Boolean.class,false)) c.registerBean(OrderExecutionGateway.class,()->{
                var client=RehearsalIsolation.client("http://127.0.0.1:"+server.getAddress().getPort());
                var adapter=new KiteOrderAdapter(new KiteRestTransport(client,currentSession),registry,c.getBean(OrderExecutionProperties.class));
                return new OrderExecutionGateway() {
                    @Override public String place(OrderRecord order, Runnable validation) {
                        gatewayCalls.incrementAndGet(); trace.add("GATEWAY_CLAIMED");
                        assertEquals(RuntimeExecutionArming.PermitState.CLAIMED,c.getBean(RuntimeExecutionArming.class).status(clock.instant()).permitState());
                        beforeGateway.run(); return adapter.place(order,()->{ beforeDispatch.run(); validation.run(); trace.add("FINAL_DISPATCH_VALIDATED"); });
                    }
                    @Override public void modify(OrderRecord order, ModifyOrder command) { throw new AssertionError("No modify in one-order test"); }
                    @Override public void cancel(OrderRecord order, CancelOrder command) { throw new AssertionError("No cancel in one-order test"); }
                };
            });
            c.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean,String name) {
                    if (bean instanceof OrderRepository repository) {
                        var observed=spy(repository);
                        doAnswer(call->{ trace.add("ADMISSION_ATTEMPT"); boolean admitted=(boolean)call.callRealMethod();
                            if (admitted) trace.add("SUBMITTING_COMMITTED"); return admitted; }).when(observed).beginSubmission(any(),any(),any());
                        return observed;
                    }
                    if (bean instanceof ExecutionSafetyPolicy policy) {
                        var observed=spy(policy);
                        doAnswer(call->{ trace.add("POLICY_EVALUATE"); return call.callRealMethod(); }).when(observed).evaluate(any());
                        doAnswer(call->{ trace.add("PREFLIGHT_INSPECT"); return call.callRealMethod(); }).when(observed).inspect(any());
                        return observed;
                    }
                    return bean;
                }
            });
            c.register(OrderConfiguration.class,OperatorControlConfiguration.class,RiskConfiguration.class,ReconciliationConfiguration.class);
            c.refresh(); contexts.add(c); assertFalse(c.getBean(OperatorExecutionService.class).status().armed());
            assertTrue(c.getBean(com.kitehybrid.platform.shared.application.RuntimeTradingHalt.class).getAsBoolean()); return c;
        }
        void approve() {
            if (operator.haltStatus().effectiveHalted()) assertEquals("RESUME_SUCCESS DISARMED",operator.resume(operator.prepareResume()));
            var placed=application.place(command("one-candidate")); id=placed.id();
            assertEquals(OrderState.VALIDATED,placed.state());
            assertTrue(context.getBean(RiskService.class).evaluate(id).approved());
            assertEquals(OrderState.RISK_APPROVED,orders.find(id).orElseThrow().state()); assertCounts(0);
        }
        void arm() {
            assertTrue(OperatorExecutionService.canArm(operator.preflight(id)));
            assertTrue(operator.arm(id,Duration.ofSeconds(30)).armed()); assertTrue(operator.preflight(id).ready()); assertCounts(0);
        }
        void assertCounts(int posts) {
            assertEquals(posts,methods.stream().filter("POST"::equals).count());
            assertEquals(0,methods.stream().filter("PUT"::equals).count()); assertEquals(0,methods.stream().filter("DELETE"::equals).count());
        }
        void consumed() {
            assertFalse(operator.status().armed()); assertEquals(RuntimeExecutionArming.PermitState.CONSUMED,operator.status().permitState());
            long before=methods.size(); assertThrows(RuntimeException.class,()->operator.execute(id)); assertEquals(before,methods.size());
        }
        void brokerObservation() {
            var observedCommand=orders.find(id).orElseThrow().command();
            String capturedTag=Arrays.stream(form.get().split("&")).filter(field->field.startsWith("tag="))
                    .map(field->java.net.URLDecoder.decode(field.substring(4),StandardCharsets.UTF_8)).findFirst().orElseThrow();
            when(reads.orders()).thenReturn(List.of(new BrokerOrder("synthetic-only-broker-1",Optional.empty(),Optional.empty(),instrument.id(),
                    TradingReadTypes.Side.BUY,TradingReadTypes.OrderType.MARKET,TradingReadTypes.Product.valueOf(observedCommand.product().name()),TradingReadTypes.Validity.DAY,
                    TradingReadTypes.Variety.REGULAR,TradingReadTypes.OrderStatus.OPEN,observedCommand.quantity(),0,observedCommand.quantity(),0,0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
                    NOW,Optional.of(NOW),Optional.of(new com.kitehybrid.platform.shared.domain.BrokerCorrelationId(capturedTag)),Optional.of(NOW))));
        }
        List<String> console(String... commands) {
            var input=new ArrayDeque<>(List.of(commands)); var output=new ArrayList<String>();
            new TrustedOperatorConsole(operator,Optional.of(context.getBean(OrderReconciliationService.class))).run(input::poll,output::add); return output;
        }
        @Override public void close() { release.countDown(); contexts.forEach(AnnotationConfigApplicationContext::close); server.stop(0); }
    }
    static PlaceOrder command(String key) { return new PlaceOrder(key,INSTRUMENT.id(),OrderSide.BUY,1,OrderType.MARKET,OrderProduct.DELIVERY,
            OrderValidity.DAY,Optional.empty(),Optional.empty(),0,OrderVariety.REGULAR); }

    @ParameterizedTest
    @CsvSource({
        "9500,1.10,10000,12000,20000,false",
        "9500,1.10,12000,10000,20000,false",
        "7999.992,1.25,10000,10000,20000,true",
        "8000,1.25,10000,10000,20000,true",
        "8000.008,1.25,10000,10000,20000,false",
        "8500,1,8000,10000,20000,false",
        "10500,1,12000,10000,20000,false",
        "10000,1,10000,10000,20000,true",
        "9500,1,10000,10000,9000,false"
    })
    void conservativeCapsComposeThroughProductionPreflightAndExecution(String price, String buffer,
            String normal, String firstLive, String risk, boolean allowed) throws Exception {
        try (var f = new Fixture("risk.price-buffer=" + buffer, "risk.max-order-value=" + risk,
                "kite.order-execution.max-notional=" + normal, "kite.live-test.max-notional=" + firstLive)) {
            f.approve(); // Legitimate earlier approval at synthetic price 10, never a manufactured decision.
            f.market.update(new Tick(INSTRUMENT.id(), new BigDecimal(price), NOW), f.publication);
            var report = f.operator.preflight(f.id);
            var value = new BigDecimal(price).multiply(new BigDecimal(buffer));
            assertEquals(value.compareTo(new BigDecimal(normal)) <= 0 && value.compareTo(new BigDecimal(risk)) <= 0,
                    report.gates().get(ExecutionReadiness.Gate.NOTIONAL_WITHIN_CAP) == ExecutionDenialReason.NONE);
            assertEquals(value.compareTo(new BigDecimal(firstLive)) <= 0,
                    report.gates().get(ExecutionReadiness.Gate.LIVE_TEST_NOTIONAL_WITHIN_CAP) == ExecutionDenialReason.NONE);
            assertEquals(allowed, OperatorExecutionService.canArm(report));
            assertSame(f.context.getBean(RiskLimits.class), f.context.getBean(OrderExecutionProperties.class).riskLimits());
            if (allowed) {
                f.arm(); assertTrue(f.operator.preflight(f.id).ready());
                assertEquals(OrderState.SUBMITTED, f.operator.execute(f.id).state());
                f.assertCounts(1); f.consumed();
            } else {
                assertFalse(f.operator.arm(f.id, Duration.ofSeconds(30)).armed());
                assertThrows(RuntimeException.class, () -> f.operator.execute(f.id));
                assertEquals(OrderState.RISK_APPROVED, f.orders.find(f.id).orElseThrow().state());
                f.assertCounts(0);
            }
        }
    }

    @Test void riskCapIndependentlyRejectsEvenWhenBothExecutionCapsAllow() throws Exception {
        try (var f = new Fixture("risk.max-order-value=9000", "kite.order-execution.max-notional=10000",
                "kite.live-test.max-notional=10000")) {
            assertEquals("RESUME_SUCCESS DISARMED", f.operator.resume(f.operator.prepareResume()));
            f.market.update(new Tick(INSTRUMENT.id(), new BigDecimal("9500"), NOW), f.publication);
            f.id = f.application.place(command("risk-cap-only")).id();
            var decision = f.context.getBean(RiskService.class).evaluate(f.id);
            assertFalse(decision.approved()); assertEquals(RiskReason.ORDER_VALUE_LIMIT, decision.reason());
            assertFalse(OperatorExecutionService.canArm(f.operator.preflight(f.id))); f.assertCounts(0);
        }
    }

    private Fixture conservativeFixture() throws Exception {
        return new Fixture("risk.price-buffer=1.25", "risk.max-order-value=20000",
                "kite.order-execution.max-notional=10000", "kite.live-test.max-notional=10000");
    }
    private void readyAt9900(Fixture f) {
        f.approve();
        f.market.update(new Tick(INSTRUMENT.id(), new BigDecimal("7920"), NOW), f.publication);
        f.arm(); assertTrue(f.operator.preflight(f.id).ready());
    }
    private void riseTo10001(Fixture f) {
        f.market.update(new Tick(INSTRUMENT.id(), new BigDecimal("8000.8"), NOW), f.publication);
    }
    @Test void earlierReadyDoesNotFreezeTheValuationAndExecuteDenialConsumesUnusedPermit() throws Exception {
        try (var f = conservativeFixture()) {
            readyAt9900(f); riseTo10001(f);
            assertFalse(f.operator.preflight(f.id).ready());
            assertEquals(RuntimeExecutionArming.PermitState.UNUSED, f.operator.status().permitState(), "Observation does not consume");
            assertThrows(RuntimeException.class, () -> f.operator.execute(f.id));
            assertEquals(OrderState.RISK_APPROVED, f.orders.find(f.id).orElseThrow().state());
            f.consumed(); f.assertCounts(0);
        }
    }
    @ParameterizedTest @ValueSource(ints={1,2,3})
    void priceRiseAtAdmissionFencesRollsBackBeforeCommit(int fence) throws Exception {
        try (var f = conservativeFixture()) {
            readyAt9900(f);
            var calls = new AtomicInteger(); var policy = f.context.getBean(ExecutionSafetyPolicy.class);
            doAnswer(call -> { if (calls.incrementAndGet() == fence) riseTo10001(f); return call.callRealMethod(); })
                    .when(policy).validateAdmission();
            assertThrows(RuntimeException.class, () -> f.operator.execute(f.id));
            assertEquals(OrderState.RISK_APPROVED, f.orders.find(f.id).orElseThrow().state());
            assertEquals(0, f.gatewayCalls.get()); f.consumed(); f.assertCounts(0);
            assertEquals(1L, f.jdbc.queryForObject(
                    "SELECT count(*) FROM trading.execution_authorizations WHERE reason='NOTIONAL_CAP_EXCEEDED' AND NOT allowed", Long.class));
        }
    }
    @ParameterizedTest
    @CsvSource({"10000,12000,NOTIONAL_CAP_EXCEEDED", "12000,10000,LIVE_TEST_NOTIONAL_CAP"})
    void finalTransportBarrierRevaluesAfterAdmissionAndBeforeAnyHttp(String normal, String firstLive, String denial) throws Exception {
        try (var f = new Fixture("risk.price-buffer=1.25", "risk.max-order-value=20000",
                "kite.order-execution.max-notional=" + normal, "kite.live-test.max-notional=" + firstLive)) {
            readyAt9900(f);
            var entered = new CountDownLatch(1); var released = new CountDownLatch(1);
            f.beforeDispatch = () -> {
                entered.countDown();
                try { if (!released.await(5, TimeUnit.SECONDS)) throw new AssertionError("Dispatch barrier timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            };
            try (var pool = Executors.newSingleThreadExecutor()) {
                var execution = pool.submit(() -> assertThrows(RuntimeException.class, () -> f.operator.execute(f.id)));
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    assertEquals(OrderState.SUBMITTING, f.orders.find(f.id).orElseThrow().state());
                    riseTo10001(f); f.assertCounts(0);
                } finally { released.countDown(); }
                assertEquals(denial, execution.get(5, TimeUnit.SECONDS).getMessage());
            }
            assertEquals(OrderState.FAILED, f.orders.find(f.id).orElseThrow().state());
            assertTrue(f.orders.find(f.id).orElseThrow().brokerOrderId().isEmpty());
            f.consumed(); f.assertCounts(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"NOTIONAL_CAP_EXCEEDED", "LIVE_TEST_NOTIONAL_CAP", "RISK_APPROVAL_MISSING", "unrecognized", "NONE"})
    void admissionAuditsOnlyBoundedDenialReasonsAfterRollback(String reason) throws Exception {
        try (var f = conservativeFixture()) {
            readyAt9900(f);
            doThrow(new OrderCommandValidationException(reason)).when(f.context.getBean(ExecutionSafetyPolicy.class))
                    .validateAdmission(any(OrderRecord.class));
            assertThrows(OrderCommandValidationException.class, () -> f.operator.execute(f.id));
            var expected = Set.of("unrecognized", "NONE").contains(reason) ? "EVIDENCE_UNAVAILABLE" : reason;
            assertEquals(expected, f.jdbc.queryForObject(
                    "SELECT reason FROM trading.execution_authorizations WHERE NOT allowed", String.class));
            assertEquals(OrderState.RISK_APPROVED, f.orders.find(f.id).orElseThrow().state());
            f.consumed(); f.assertCounts(0);
        }
    }

    @ParameterizedTest @ValueSource(strings={"missing", "zero", "stale", "future", "exchange-stale", "exchange-future", "unhealthy"})
    void invalidMarketEvidenceStillDeniesWithConservativeValuation(String change) throws Exception {
        try (var f = conservativeFixture()) {
            readyAt9900(f);
            switch (change) {
                case "missing" -> f.publication.revoke();
                case "zero" -> f.market.update(new Tick(INSTRUMENT.id(), BigDecimal.ZERO, NOW), f.publication);
                case "stale" -> f.now.set(NOW.plusSeconds(6));
                case "future" -> f.market.update(new Tick(INSTRUMENT.id(), BigDecimal.TEN, NOW.plusSeconds(1)), f.publication);
                case "exchange-stale", "exchange-future" -> f.market.update(new Tick(INSTRUMENT.id(), BigDecimal.TEN, NOW,
                        Optional.of(change.equals("exchange-stale") ? NOW.minusSeconds(6) : NOW.plusSeconds(1)),
                        Optional.empty(), Optional.empty()), f.publication);
                case "unhealthy" -> f.health = mock(MarketDataHealth.class);
                default -> throw new AssertionError();
            }
            assertFalse(f.operator.preflight(f.id).ready());
            assertThrows(RuntimeException.class, () -> f.operator.execute(f.id)); f.consumed(); f.assertCounts(0);
        }
    }
    @Test void differentBufferInvalidatesEarlierRiskPolicyAndCannotBeConfiguredIndependently() throws Exception {
        try (var f = conservativeFixture()) {
            f.approve();
            var replacement = f.boot(f.session, "risk.price-buffer=1.10");
            var properties = replacement.getBean(OrderExecutionProperties.class);
            assertSame(replacement.getBean(RiskLimits.class), properties.riskLimits());
            assertEquals(0, new BigDecimal("1.10").compareTo(properties.riskLimits().priceBuffer()));
            var report = replacement.getBean(OperatorExecutionService.class).preflight(f.id);
            assertEquals(ExecutionDenialReason.RISK_POLICY_MISMATCH, report.gates().get(ExecutionReadiness.Gate.RISK_DECISION_CURRENT));
            f.assertCounts(0);
        }
    }

    @Test void confirmedConsoleWorkflowSendsOnePostThenDisarmsAndRequiresExplicitReconciliation() throws Exception {
        try(var f=new Fixture()) {
            f.approve(); String id=f.id.value().toString();
            var output=f.console("status","preflight "+id,"arm "+id+" 30s","CONFIRM arm "+id,"preflight "+id,
                    "execute "+id,"CONFIRM execute "+id,"execute "+id,"status");
            assertTrue(output.contains("EXECUTION_RESULT SUBMITTED"),output.toString());
            f.assertCounts(1); assertTrue(f.submittingObserved.get()); f.consumed();
            assertTrue(f.form.get().contains("tag="+f.orders.find(f.id).orElseThrow().brokerCorrelationId().orElseThrow().value()));
            assertEquals(0,f.jdbc.queryForObject("SELECT count(*) FROM trading.reconciliation_decisions",Integer.class));
            assertTrue(output.stream().noneMatch(s->s.contains("syntheticToken") || s.contains("synthetic-only-broker") || s.contains("SYNTHETIC_ONLY")));
            f.brokerObservation(); f.console("reconcile "+id);
            assertEquals(OrderState.OPEN,f.orders.find(f.id).orElseThrow().state()); f.assertCounts(1); assertFalse(f.operator.status().armed());
        }
    }
    @ParameterizedTest @EnumSource(Mode.class)
    void everyHttpOutcomeConsumesAndDisarms(Mode mode) throws Exception {
        try(var f=new Fixture()) {
            f.approve(); f.arm(); f.mode=mode;
            if (mode == Mode.SUCCESS) assertEquals(OrderState.SUBMITTED,f.operator.execute(f.id).state());
            else assertThrows(RuntimeException.class,()->f.operator.execute(f.id));
            f.assertCounts(1); assertTrue(f.submittingObserved.get()); f.consumed();
            if (Set.of(Mode.RESET,Mode.RESPONSE_LOST,Mode.TIMEOUT,Mode.MALFORMED).contains(mode)) {
                assertEquals(OrderState.SUBMITTING,f.orders.find(f.id).orElseThrow().state());
                assertFalse(f.operator.arm(f.id,Duration.ofSeconds(30)).armed()); f.assertCounts(1);
            }
        }
    }
    @Test void responseLossRecoversOnlyByExplicitCorrelationReconciliation() throws Exception {
        try(var f=new Fixture()) {
            f.approve(); f.arm(); f.mode=Mode.RESPONSE_LOST;
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id)); f.assertCounts(1); f.consumed();
            assertTrue(f.orders.find(f.id).orElseThrow().brokerOrderId().isEmpty());
            f.brokerObservation(); f.console("reconcile "+f.id.value());
            var recovered=f.orders.find(f.id).orElseThrow();
            assertEquals(OrderState.SUBMITTED,recovered.state()); assertEquals("synthetic-only-broker-1",recovered.brokerOrderId().orElseThrow());
            f.assertCounts(1); assertFalse(f.operator.status().armed());
        }
    }
    @ParameterizedTest @ValueSource(strings={"unknown","other","expired"})
    void wrongOrderAndExpiredArmSendNothing(String change) throws Exception {
        try(var f=new Fixture()) {
            f.approve(); f.arm(); OrderId target=f.id;
            if (change.equals("unknown")) target=new OrderId(new UUID(0,999));
            if (change.equals("other")) target=f.application.place(command("other")).id();
            if (change.equals("expired")) f.now.set(NOW.plusSeconds(30));
            var selected=target; var readiness=f.operator.preflight(selected); assertFalse(readiness.ready());
            if (change.equals("other")) assertEquals(ExecutionDenialReason.ORDER_NOT_ARMED,readiness.gates().get(ExecutionReadiness.Gate.SESSION_BOUND));
            assertThrows(RuntimeException.class,()->f.operator.execute(selected)); f.assertCounts(0); f.consumed();
            assertEquals(OrderState.RISK_APPROVED,f.orders.find(f.id).orElseThrow().state());
        }
    }
    @ParameterizedTest @ValueSource(strings={"stop","session","version","risk-expiry","risk-policy","market","generation","reconciliation","exposure"})
    void lastTransportValidationRejectsChangedEvidence(String change) throws Exception {
        try(var f=new Fixture()) {
            f.approve(); f.arm();
            f.beforeDispatch=()->{
                switch(change) {
                    case "stop" -> f.stop.set(true);
                    case "session" -> { f.session.install(new KiteAccessToken("replacementSynthetic",NOW,NOW.plusSeconds(3600))); f.session.profileValidated(); }
                    case "version" -> f.jdbc.update("UPDATE trading.orders SET version=version+1 WHERE order_id=?",f.id.value());
                    case "risk-expiry" -> f.jdbc.update("UPDATE trading.risk_decisions SET evaluated_at=? WHERE order_id=?",Timestamp.from(NOW.minusSeconds(60)),f.id.value());
                    case "risk-policy" -> f.jdbc.update("UPDATE trading.risk_decisions SET policy_version=? WHERE order_id=?","cash-v1:"+"0".repeat(64),f.id.value());
                    case "market" -> f.now.set(NOW.plusSeconds(5));
                    case "generation" -> f.publication.revoke();
                    case "reconciliation" -> f.jdbc.update("INSERT INTO trading.reconciliation_decisions VALUES(gen_random_uuid(),?,'SUBMITTING',NULL,'CONFLICT','ORDER_IDENTITY_CONFLICT',?,3)",f.id.value(),Timestamp.from(NOW));
                    case "exposure" -> {
                        var other=f.application.place(command("blocking-exposure"));
                        f.jdbc.update("UPDATE trading.orders SET state='OPEN' WHERE order_id=?",other.id().value());
                    }
                    default -> fail(change);
                }
            };
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id)); f.assertCounts(0); f.consumed();
        }
    }
    @ParameterizedTest @ValueSource(strings={"cas","authorization-store","ack-store","unexpected","error"})
    void localFailuresAlwaysRevokeThePermit(String failure) throws Exception {
        try(var f=new Fixture()) {
            f.approve(); f.arm();
            switch(failure) {
                case "cas" -> doReturn(false).when(f.orders).beginSubmission(any(),any(),any());
                case "authorization-store" -> f.jdbc.execute("ALTER TABLE trading.execution_authorizations ADD CONSTRAINT deny_authorization CHECK (false)");
                case "ack-store" -> f.jdbc.execute("ALTER TABLE trading.orders ADD CONSTRAINT deny_ack CHECK (broker_order_id IS NULL)");
                case "unexpected" -> f.beforeGateway=()->{ throw new IllegalStateException("synthetic unexpected"); };
                case "error" -> f.beforeGateway=()->{ throw new AssertionError("synthetic fatal boundary"); };
                default -> fail(failure);
            }
            if (failure.equals("error")) assertThrows(AssertionError.class,()->f.operator.execute(f.id));
            else assertThrows(RuntimeException.class,()->f.operator.execute(f.id));
            f.assertCounts(failure.equals("ack-store") ? 1 : 0); f.consumed();
        }
    }
    @Test void twoIndependentContextsCannotSubmitSameOrderTwice() throws Exception {
        try(var f=new Fixture()) {
            f.approve(); f.arm();
            var other=f.boot(f.authenticated()); var second=other.getBean(OperatorExecutionService.class);
            assertEquals("RESUME_SUCCESS DISARMED",second.resume(second.prepareResume()));
            assertTrue(second.arm(f.id,Duration.ofSeconds(30)).armed()); assertTrue(second.preflight(f.id).ready());
            var start=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                var a=pool.submit(()->attempt(start,f.operator,f.id)); var b=pool.submit(()->attempt(start,second,f.id)); start.countDown();
                assertEquals(1,(a.get(10,TimeUnit.SECONDS) ? 1 : 0)+(b.get(10,TimeUnit.SECONDS) ? 1 : 0));
            }
            f.assertCounts(1); assertTrue(f.submittingObserved.get()); assertFalse(second.status().armed()); f.consumed();
        }
    }
    @Test void concurrentCallsSharingPermitNeverAddAnotherPost() throws Exception {
        try(var f=new Fixture()) {
            f.approve(); f.arm(); var start=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                var a=pool.submit(()->attempt(start,f.operator,f.id)); var b=pool.submit(()->attempt(start,f.operator,f.id)); start.countDown();
                a.get(10,TimeUnit.SECONDS); b.get(10,TimeUnit.SECONDS);
            }
            assertTrue(f.methods.size() <= 1); f.consumed();
        }
    }
    static boolean attempt(CountDownLatch start,OperatorExecutionService operator,OrderId id) throws InterruptedException {
        start.await(); try { operator.execute(id); return true; } catch(RuntimeException denied) { return false; }
    }
    @ParameterizedTest @ValueSource(strings={"stop","market","risk","conflict"})
    void preArmNeverIgnoresAnyOtherDenial(String change) throws Exception {
        try(var f=new Fixture()) {
            f.approve(); assertTrue(OperatorExecutionService.canArm(f.operator.preflight(f.id)));
            switch(change) {
                case "stop" -> f.stop.set(true);
                case "market" -> f.publication.revoke();
                case "risk" -> f.jdbc.update("DELETE FROM trading.risk_decisions WHERE order_id=?",f.id.value());
                case "conflict" -> f.jdbc.update("INSERT INTO trading.reconciliation_decisions VALUES(gen_random_uuid(),?,'RISK_APPROVED',NULL,'CONFLICT','ORDER_IDENTITY_CONFLICT',?,2)",f.id.value(),Timestamp.from(NOW));
                default -> fail(change);
            }
            assertFalse(f.operator.arm(f.id,Duration.ofSeconds(30)).armed()); f.assertCounts(0);
        }
    }
    @ParameterizedTest @ValueSource(strings={"unused","claimed","admitted","acknowledged"})
    void restartAtCrashCheckpointsCannotRestoreArmOrRetrySubmission(String checkpoint) throws Exception {
        try(var f=new Fixture()) {
            f.approve(); f.arm();
            if (checkpoint.equals("claimed")) f.context.getBean(RuntimeExecutionArming.class).claim(f.id,NOW);
            if (checkpoint.equals("admitted")) {
                var before=f.orders.find(f.id).orElseThrow();
                assertTrue(f.orders.beginSubmission(before,before.transitionTo(OrderState.SUBMITTING,NOW),()->{}));
            }
            if (checkpoint.equals("acknowledged")) f.operator.execute(f.id);
            f.context.close(); var restarted=f.boot(f.authenticated()); var operator=restarted.getBean(OperatorExecutionService.class);
            assertFalse(operator.status().armed()); assertEquals(RuntimeExecutionArming.PermitState.NONE,operator.status().permitState());
            assertFalse(operator.preflight(f.id).ready()); assertThrows(RuntimeException.class,()->operator.execute(f.id));
            f.assertCounts(checkpoint.equals("acknowledged") ? 1 : 0);
        }
    }
    @ParameterizedTest @ValueSource(strings={"no-preflight","wrong-confirmation","no-ready-preflight","cancel-execute"})
    void hostRequiresSeparatePreflightsAndExactConfirmations(String missing) throws Exception {
        try(var f=new Fixture()) {
            f.approve(); String id=f.id.value().toString();
            switch(missing) {
                case "no-preflight" -> f.console("arm "+id+" 30s");
                case "wrong-confirmation" -> f.console("preflight "+id,"arm "+id+" 30s","CONFIRM arm 00000000-0000-0000-0000-000000000999");
                case "no-ready-preflight" -> f.console("preflight "+id,"arm "+id+" 30s","CONFIRM arm "+id,"execute "+id);
                case "cancel-execute" -> f.console("preflight "+id,"arm "+id+" 30s","CONFIRM arm "+id,"preflight "+id,"execute "+id,"NO");
                default -> fail(missing);
            }
            f.assertCounts(0); assertFalse(f.operator.status().armed());
        }
    }

    @ParameterizedTest @ValueSource(strings={"running","unused","claimed","authorized","admitted","before-http","after-http","persisted","http-in-flight","acknowledged"})
    void actualChildJvmCrashPreservesDurableAdmissionAndNeverRestoresPermission(String checkpoint) throws Exception {
        try(var f=new Fixture()) {
            f.approve();
            if (checkpoint.equals("http-in-flight")) f.mode=Mode.TIMEOUT;
            String classpath=java.nio.file.Path.of("target/integration-test-classes").toAbsolutePath()+java.io.File.pathSeparator
                    +System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
            var log=java.nio.file.Files.createTempFile(java.nio.file.Path.of("target"),"operator-crash-",".log");
            var process=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",classpath,
                    OneOrderCrashProbe.class.getName(),f.source.getUrl(),POSTGRES.getUsername(),POSTGRES.getPassword(),
                    "http://127.0.0.1:"+f.server.getAddress().getPort(),f.id.value().toString(),checkpoint)
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                if (checkpoint.equals("http-in-flight")) {
                    assertTrue(f.received.await(20,TimeUnit.SECONDS),"Wait for actual HTTP receipt before abrupt process termination");
                    assertTrue(process.isAlive()); process.destroyForcibly();
                    assertTrue(process.waitFor(10,TimeUnit.SECONDS)); assertNotEquals(0,process.exitValue());
                } else {
                    assertTrue(process.waitFor(30,TimeUnit.SECONDS),"Crash probe must terminate");
                    assertEquals(73,process.exitValue(),"Child did not reach checkpoint; inspect "+log.getFileName());
                }
            } finally { if (process.isAlive()) process.destroyForcibly().waitFor(10,TimeUnit.SECONDS); }
            int posts=Set.of("after-http","persisted","http-in-flight","acknowledged").contains(checkpoint) ? 1 : 0;
            f.assertCounts(posts);
            var state=f.orders.find(f.id).orElseThrow().state();
            assertEquals(Set.of("running","unused","claimed","authorized").contains(checkpoint) ? OrderState.RISK_APPROVED
                    : Set.of("acknowledged","persisted").contains(checkpoint) ? OrderState.SUBMITTED : OrderState.SUBMITTING,state);
            var restarted=f.boot(f.authenticated()); var operator=restarted.getBean(OperatorExecutionService.class);
            assertFalse(operator.status().armed()); assertFalse(operator.preflight(f.id).ready());
            assertEquals(RuntimeExecutionArming.PermitState.NONE,operator.status().permitState());
            assertThrows(RuntimeException.class,()->operator.execute(f.id)); f.assertCounts(posts);
            if (state != OrderState.RISK_APPROVED) assertFalse(operator.arm(f.id,Duration.ofSeconds(30)).armed());
        }
    }
}
