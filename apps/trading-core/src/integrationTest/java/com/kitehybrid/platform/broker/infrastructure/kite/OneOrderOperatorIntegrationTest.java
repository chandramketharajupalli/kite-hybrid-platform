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
        volatile Runnable beforeDispatch=()->{}, beforeGateway=()->{};
        OrderId id;

        Fixture(String... overrides) throws Exception {
            when(clock.instant()).thenAnswer(call->now.get()); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
            registry.replace(List.of(INSTRUMENT),NOW);
            market.update(new Tick(INSTRUMENT.id(),BigDecimal.TEN,NOW),publication);
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
                methods.add(exchange.getRequestMethod());
                form.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                submittingObserved.set(jdbc.queryForObject("SELECT state FROM trading.orders WHERE order_id=?",String.class,id.value()).equals("SUBMITTING"));
                received.countDown();
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
            TestPropertyValues.of("kite.order-execution.enabled=true", "kite.order-execution.allowed-instruments="+INSTRUMENT.id().value(),
                    "kite.order-execution.max-quantity=1", "kite.order-execution.max-notional=20", "kite.order-execution.risk-decision-max-age=60s",
                    "kite.order-execution.market-data-max-age=5s", "kite.operator-control.enabled=true", "kite.live-test.enabled=true",
                    "kite.live-test.allowed-instruments="+INSTRUMENT.id().value(), "kite.live-test.max-quantity=1", "kite.live-test.max-notional=20",
                    "kite.live-test.arm-max-duration=30s", "kite.trading-read.enabled=true", "risk.enabled=true", "risk.max-order-quantity=100",
                    "risk.max-order-value=10000", "risk.max-position-quantity=100", "risk.max-exposure=10000", "risk.market-data-max-age=60s",
                    "risk.registry-max-age=60s", "risk.cash-reserve=1").applyTo(c);
            TestPropertyValues.of(overrides).applyTo(c);
            c.registerBean(JdbcTemplate.class,()->new JdbcTemplate(source)); c.registerBean(Flyway.class,()->flyway);
            c.registerBean(Clock.class,()->clock); c.registerBean(MeterRegistry.class,SimpleMeterRegistry::new);
            c.registerBean(KiteAuthenticationSession.class,()->currentSession); c.registerBean(InstrumentRegistry.class,()->registry);
            c.registerBean(LatestMarketDataStore.class,()->market); c.registerBean(KiteTradingReadAdapter.class,()->reads);
            c.registerBean(MarketDataGateway.class,()->{ var gateway=mock(MarketDataGateway.class); when(gateway.health()).thenReturn(OperatorPreflightDryRunTest.healthy()); return gateway; });
            c.registerBean(TradingProperties.class,()->{ var trading=mock(TradingProperties.class); when(trading.emergencyStop()).thenAnswer(call->stop.get()); return trading; });
            c.registerBean(RiskEngine.class,()->new RiskEngine(List.of(new PositiveReferencePriceRiskRule()),clock));
            c.registerBean(OrderExecutionGateway.class,()->{
                var factory=new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(1000); factory.setReadTimeout(250);
                var client=RestClient.builder().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).requestFactory(factory).build();
                var adapter=new KiteOrderAdapter(new KiteRestTransport(client,currentSession),registry,c.getBean(OrderExecutionProperties.class));
                return new OrderExecutionGateway() {
                    @Override public String place(OrderRecord order, Runnable validation) {
                        assertEquals(RuntimeExecutionArming.PermitState.CLAIMED,c.getBean(RuntimeExecutionArming.class).status(clock.instant()).permitState());
                        beforeGateway.run(); return adapter.place(order,()->{ beforeDispatch.run(); validation.run(); });
                    }
                    @Override public void modify(OrderRecord order, ModifyOrder command) { throw new AssertionError("No modify in one-order test"); }
                    @Override public void cancel(OrderRecord order, CancelOrder command) { throw new AssertionError("No cancel in one-order test"); }
                };
            });
            c.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean,String name) { return bean instanceof OrderRepository ? spy(bean) : bean; }
            });
            c.register(OrderConfiguration.class,OperatorControlConfiguration.class,RiskConfiguration.class,ReconciliationConfiguration.class);
            c.refresh(); contexts.add(c); assertFalse(c.getBean(OperatorExecutionService.class).status().armed()); return c;
        }
        void approve() {
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
            String capturedTag=Arrays.stream(form.get().split("&")).filter(field->field.startsWith("tag="))
                    .map(field->java.net.URLDecoder.decode(field.substring(4),StandardCharsets.UTF_8)).findFirst().orElseThrow();
            when(reads.orders()).thenReturn(List.of(new BrokerOrder("synthetic-only-broker-1",Optional.empty(),Optional.empty(),INSTRUMENT.id(),
                    TradingReadTypes.Side.BUY,TradingReadTypes.OrderType.MARKET,TradingReadTypes.Product.DELIVERY,TradingReadTypes.Validity.DAY,
                    TradingReadTypes.Variety.REGULAR,TradingReadTypes.OrderStatus.OPEN,1,0,1,0,0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
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
                case "cas" -> doReturn(false).when(f.orders).beginSubmission(any(),any());
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
                assertTrue(f.orders.beginSubmission(before,before.transitionTo(OrderState.SUBMITTING,NOW)));
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

    @ParameterizedTest @ValueSource(strings={"unused","claimed","authorized","admitted","before-http","after-http","acknowledged"})
    void actualChildJvmCrashPreservesDurableAdmissionAndNeverRestoresPermission(String checkpoint) throws Exception {
        try(var f=new Fixture()) {
            f.approve();
            String classpath=java.nio.file.Path.of("target/integration-test-classes").toAbsolutePath()+java.io.File.pathSeparator
                    +System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
            var log=java.nio.file.Files.createTempFile(java.nio.file.Path.of("target"),"operator-crash-",".log");
            var process=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",classpath,
                    OneOrderCrashProbe.class.getName(),f.source.getUrl(),POSTGRES.getUsername(),POSTGRES.getPassword(),
                    "http://127.0.0.1:"+f.server.getAddress().getPort(),f.id.value().toString(),checkpoint)
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(process.waitFor(30,TimeUnit.SECONDS),"Crash probe must terminate");
                assertEquals(73,process.exitValue(),"Child did not reach checkpoint; inspect "+log.getFileName());
            } finally { if (process.isAlive()) process.destroyForcibly().waitFor(10,TimeUnit.SECONDS); }
            int posts=Set.of("after-http","acknowledged").contains(checkpoint) ? 1 : 0;
            f.assertCounts(posts);
            var state=f.orders.find(f.id).orElseThrow().state();
            assertEquals(Set.of("unused","claimed","authorized").contains(checkpoint) ? OrderState.RISK_APPROVED
                    : checkpoint.equals("acknowledged") ? OrderState.SUBMITTED : OrderState.SUBMITTING,state);
            var restarted=f.boot(f.authenticated()); var operator=restarted.getBean(OperatorExecutionService.class);
            assertFalse(operator.status().armed()); assertFalse(operator.preflight(f.id).ready());
            assertThrows(RuntimeException.class,()->operator.execute(f.id)); f.assertCounts(posts);
            if (state != OrderState.RISK_APPROVED) assertFalse(operator.arm(f.id,Duration.ofSeconds(30)).armed());
        }
    }
}
