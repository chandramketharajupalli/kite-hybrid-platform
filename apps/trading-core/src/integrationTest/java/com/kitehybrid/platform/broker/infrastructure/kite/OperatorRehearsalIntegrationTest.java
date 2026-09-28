package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.bootstrap.TradingCoreApplication;
import com.kitehybrid.platform.broker.infrastructure.kite.OneOrderOperatorIntegrationTest.Fixture;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.operator.application.*;
import com.kitehybrid.platform.operator.console.TrustedOperatorConsole;
import com.kitehybrid.platform.operator.infrastructure.OperatorControlConfigurationProperties;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.reconciliation.application.*;
import com.kitehybrid.platform.reconciliation.domain.*;
import com.kitehybrid.platform.risk.application.RiskService;
import com.kitehybrid.platform.risk.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static com.kitehybrid.platform.broker.infrastructure.kite.OneOrderOperatorIntegrationTest.*;
import static com.kitehybrid.platform.order.application.ExecutionDenialReason.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Operational rehearsal through real production boundaries. All data/HTTP is disposable and synthetic. */
@Testcontainers @Isolated @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OperatorRehearsalIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=OneOrderOperatorIntegrationTest.POSTGRES;
    static TimeZone zone;
    static RehearsalEvidence evidence;
    @BeforeAll static void prepare() throws Exception {
        zone=TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")); evidence=new RehearsalEvidence();
    }
    @AfterAll static void finish() throws Exception { try { evidence.finish(); } finally { TimeZone.setDefault(zone); } }
    @RegisterExtension final TestWatcher results=new TestWatcher() {
        @Override public void testSuccessful(ExtensionContext c) { evidence.test(c.getDisplayName(),"PASSED"); }
        @Override public void testFailed(ExtensionContext c,Throwable ignored) { evidence.test(c.getDisplayName(),"FAILED"); }
        @Override public void testAborted(ExtensionContext c,Throwable ignored) { evidence.test(c.getDisplayName(),"ABORTED"); }
    };
    private Fixture fixture() throws Exception { return new OneOrderOperatorIntegrationTest().new Fixture(); }
    private static long count(Fixture f,String table) { return f.jdbc.queryForObject("SELECT count(*) FROM trading."+table,Long.class); }
    private static Map<String,Object> snapshot(Fixture f) {
        var result=new LinkedHashMap<String,Object>();
        result.put("flywayVersion",f.flyway.info().current().getVersion().toString());
        for (String table:List.of("orders","risk_decisions","execution_authorizations","reconciliation_decisions","strategy_evaluations","reconciliation_trades"))
            result.put(table,count(f,table));
        result.put("armed",f.operator.status().armed()); result.put("permit",f.operator.status().permitState().name());
        result.put("gatewayCalls",f.gatewayCalls.get());
        for (String method:List.of("POST","PUT","DELETE")) result.put(method,f.methods.stream().filter(method::equals).count());
        if (f.id!=null) {
            var order=f.orders.find(f.id).orElseThrow(); result.put("state",order.state().name());
            result.put("brokerIdAttached",order.brokerOrderId().isPresent());
        }
        return result;
    }
    private static void untouched(Fixture f) {
        f.assertCounts(0); assertEquals(0,f.gatewayCalls.get()); assertEquals(0,count(f,"execution_authorizations"));
        assertEquals(OrderState.RISK_APPROVED,f.orders.find(f.id).orElseThrow().state());
        assertTrue(f.orders.find(f.id).orElseThrow().brokerOrderId().isEmpty());
    }
    private static List<String> armCommands(Fixture f) {
        String id=f.id.value().toString();
        return List.of("preflight "+id,"arm "+id+" 30s","CONFIRM arm "+id,"preflight "+id);
    }
    private static void appendExecute(List<String> commands,Fixture f) {
        commands.add("execute "+f.id.value()); commands.add("CONFIRM execute "+f.id.value());
    }

    @Test @Order(1) void normalApplicationStartsDisarmedWithUnmodifiedFailClosedDefaults() throws Exception {
        try(var f=fixture()) {
            ConfigurableApplicationContext normal;
            try(var c=new SpringApplicationBuilder(TradingCoreApplication.class).environment(RehearsalIsolation.environment())
                    .web(WebApplicationType.NONE).run("--spring.config.location=classpath:/application.yml",
                            "--spring.profiles.active=development","--spring.datasource.url="+f.source.getUrl(),
                            "--spring.datasource.username="+POSTGRES.getUsername(),"--spring.datasource.password="+POSTGRES.getPassword(),
                            "--spring.flyway.default-schema=public","--spring.main.banner-mode=off","--logging.level.root=ERROR")) {
                normal=c;
                assertFalse(c.getBean(RuntimeExecutionArming.class).status(NOW).armed());
                assertTrue(c.getBean(com.kitehybrid.platform.shared.application.RuntimeTradingHalt.class).getAsBoolean());
                assertFalse(c.getBean(OrderExecutionProperties.class).enabled());
                assertFalse(c.getBean(OperatorControlConfigurationProperties.class).enabled());
                var live=c.getBean(LiveTestProperties.class); assertFalse(live.enabled()); assertFalse(live.configured());
                assertTrue(live.allowedInstruments().isEmpty()); assertEquals(0,live.maxQuantity());
                assertEquals(0,live.maxNotional().signum());
                assertTrue(c.getBeansOfType(TrustedOperatorConsole.class).isEmpty());
                for (String table:List.of("orders","risk_decisions","execution_authorizations","reconciliation_decisions","strategy_evaluations"))
                    assertEquals(0,count(f,table));
                f.assertCounts(0); assertEquals(0,f.gatewayCalls.get());
                var observed=snapshot(f);
                observed.put("executionEnabled",c.getBean(OrderExecutionProperties.class).enabled());
                observed.put("operatorControlEnabled",c.getBean(OperatorControlConfigurationProperties.class).enabled());
                observed.put("liveTestEnabled",live.enabled()); observed.put("automaticConsole",false);
                observed.put("armed",c.getBean(RuntimeExecutionArming.class).status(NOW).armed());
                evidence.scenario("normalStartup",observed);
            }
            assertFalse(normal.isActive());
        }
    }

    @Test @Order(2) void primaryConfirmedLauncherRehearsalCapturesBoundedEvidence() throws Exception {
        try(var f=fixture()) {
            var checkpoints=new LinkedHashMap<String,Object>(); checkpoints.put("beforeCandidate",snapshot(f));
            assertEquals(0,count(f,"orders")); assertEquals("10",f.flyway.info().current().getVersion().toString());
            f.approve(); untouched(f);
            assertEquals(1,count(f,"orders")); assertEquals(1,count(f,"risk_decisions"));
            assertEquals(1,f.jdbc.queryForObject("SELECT count(DISTINCT broker_correlation_id) FROM trading.orders",Long.class));
            checkpoints.put("candidate",snapshot(f));
            String id=f.id.value().toString();
            var commands=List.of("status","preflight "+id,"arm "+id+" 30s","CONFIRM arm "+id,
                    "preflight "+id,"execute "+id,"CONFIRM execute "+id,"reconcile "+id,"reconcile "+id,"reconcile "+id,"execute "+id);
            var cursor=new AtomicInteger(); var output=new ArrayList<String>(); var filledVersion=new long[1];
            RehearsalTerminal.run(()->f.context,()->{
                int step=cursor.getAndIncrement();
                if (step==2) {
                    var report=f.operator.preflight(f.id);
                    assertTrue(OperatorExecutionService.canArm(report),"STOP: non-arm prerequisite failed");
                    assertEquals(DISARMED,report.reason()); checkpoints.put("preArmGates",report.gates()); untouched(f);
                }
                if (step==4) {
                    var status=f.operator.status(); assertTrue(status.armed());
                    assertEquals(NOW,status.armedAt()); assertEquals(NOW.plusSeconds(30),status.expiresAt());
                    assertEquals(RuntimeExecutionArming.PermitState.UNUSED,status.permitState());
                    untouched(f); checkpoints.put("armed",snapshot(f));
                }
                if (step==5) {
                    var report=f.operator.preflight(f.id); assertTrue(report.ready());
                    assertEquals(EnumSet.allOf(ExecutionReadiness.Gate.class),report.gates().keySet());
                    checkpoints.put("readyGates",report.gates()); untouched(f); f.trace.clear();
                }
                if (step==7) {
                    assertEquals(OrderState.SUBMITTED,f.orders.find(f.id).orElseThrow().state());
                    assertTrue(f.orders.find(f.id).orElseThrow().brokerOrderId().isPresent());
                    f.assertCounts(1); assertTrue(f.submittingObserved.get());
                    assertFalse(f.operator.status().armed());
                    assertEquals(RuntimeExecutionArming.PermitState.CONSUMED,f.operator.status().permitState());
                    assertEquals(1,count(f,"execution_authorizations")); assertEquals(0,count(f,"reconciliation_decisions"));
                    assertTrue(f.form.get().contains("tag="+f.orders.find(f.id).orElseThrow().brokerCorrelationId().orElseThrow().value()));
                    checkpoints.put("afterExecute",snapshot(f)); checkpoints.put("dispatchTrace",List.copyOf(f.trace));
                    f.brokerObservation();
                }
                if (step==8) {
                    assertEquals(OrderState.OPEN,f.orders.find(f.id).orElseThrow().state());
                    completeObservation(f);
                }
                if (step==9) {
                    assertEquals(OrderState.FILLED,f.orders.find(f.id).orElseThrow().state());
                    filledVersion[0]=f.orders.find(f.id).orElseThrow().version();
                }
                if (step==10) {
                    assertEquals(filledVersion[0],f.orders.find(f.id).orElseThrow().version());
                    assertEquals(1,count(f,"reconciliation_trades"));
                    assertEquals(1L,f.jdbc.queryForObject("SELECT sum(quantity) FROM trading.reconciliation_trades",Long.class));
                    assertEquals(0,BigDecimal.TEN.compareTo(f.jdbc.queryForObject("SELECT sum(quantity*price) FROM trading.reconciliation_trades",BigDecimal.class)));
                }
                return step<commands.size() ? commands.get(step) : null;
            },output::add,true,"--interactive-operator");
            assertFalse(f.context.isActive(),"Real launcher must close its disposable context");
            f.assertCounts(1); assertEquals(1,f.gatewayCalls.get()); assertEquals(3,count(f,"reconciliation_decisions"));
            assertTrue(output.contains("EXECUTION_RESULT SUBMITTED"));
            assertTrue(output.contains("DENIED PREFLIGHT_OR_CONFIRMATION_REQUIRED"));
            var expected=List.of("PREFLIGHT_INSPECT","POLICY_EVALUATE","ADMISSION_ATTEMPT","SUBMITTING_COMMITTED","GATEWAY_CLAIMED","FINAL_DISPATCH_VALIDATED","HTTP_POST");
            assertEquals(expected,f.trace);
            checkpoints.put("submittingCommittedBeforeHttp",f.submittingObserved.get());
            checkpoints.put("terminal",output); checkpoints.put("final",snapshot(f));
            evidence.scenario("success",checkpoints);
        }
    }
    private static void completeObservation(Fixture f) {
        var original=f.reads.orders().getFirst();
        // No correlation in the observation: persisted broker ID must be sufficient and preferred.
        when(f.reads.orders()).thenReturn(List.of(new BrokerOrder(original.brokerOrderId(),Optional.empty(),Optional.empty(),INSTRUMENT.id(),
                TradingReadTypes.Side.BUY,TradingReadTypes.OrderType.MARKET,TradingReadTypes.Product.DELIVERY,TradingReadTypes.Validity.DAY,
                TradingReadTypes.Variety.REGULAR,TradingReadTypes.OrderStatus.FILLED,1,1,0,0,0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.TEN,
                NOW,Optional.of(NOW),Optional.empty(),Optional.of(NOW))));
        var trade=new BrokerTrade("synthetic-fill",original.brokerOrderId(),Optional.empty(),INSTRUMENT.id(),TradingReadTypes.Side.BUY,
                TradingReadTypes.Product.DELIVERY,1,BigDecimal.TEN,NOW,Optional.of(NOW));
        when(f.reads.trades()).thenReturn(List.of(trade,trade));
    }

    @ParameterizedTest @ValueSource(strings={"before-arm","after-arm-eof","after-arm-disarm","input-failure","interrupt"})
    void abortNeverAuthorizesAndLauncherClosesContext(String boundary) throws Exception {
        try(var f=fixture()) {
            f.approve(); var commands=new ArrayDeque<>(boundary.equals("before-arm") ? List.of("preflight "+f.id.value()) : armCommands(f));
            if (boundary.equals("after-arm-disarm")) commands.add("disarm");
            var output=new ArrayList<String>();
            try {
                var owner=Thread.currentThread();
                RehearsalTerminal.run(()->f.context,()->{
                    if (commands.isEmpty() && boundary.equals("input-failure")) throw new IllegalStateException("private-exception-payload");
                    if (commands.isEmpty() && boundary.equals("interrupt")) {
                        owner.interrupt(); throw new IllegalStateException("private-exception-payload");
                    }
                    return commands.poll();
                },output::add,true,"--interactive-operator");
                assertEquals(boundary.equals("interrupt"),Thread.currentThread().isInterrupted(),"Preserve caller interrupt status");
            } finally { Thread.interrupted(); }
            assertFalse(f.operator.status().armed()); assertFalse(f.context.isActive()); untouched(f);
            assertTrue(output.stream().noneMatch(s->s.contains("private-exception-payload")));
            var restarted=f.boot(f.authenticated());
            assertFalse(restarted.getBean(OperatorExecutionService.class).status().armed());
            assertEquals(RuntimeExecutionArming.PermitState.NONE,restarted.getBean(OperatorExecutionService.class).status().permitState());
            var observed=snapshot(f); observed.put("restartArmed",false); observed.put("restartPermit","NONE");
            evidence.scenario("abort_"+boundary,observed);
        }
    }

    @Test void launcherRejectsMissingTerminalOrFlagsAndBoundsStartupFailure() throws Exception {
        var boots=new AtomicInteger(); var output=new ArrayList<String>();
        for (String[] args:List.of(new String[]{},new String[]{"--interactive-operator","--execute"}))
            assertThrows(IllegalStateException.class,()->RehearsalTerminal.run(()->{ boots.incrementAndGet(); return null; },()->null,output::add,true,args));
        assertThrows(IllegalStateException.class,()->RehearsalTerminal.run(()->{ boots.incrementAndGet(); return null; },()->null,output::add,false,"--interactive-operator"));
        assertEquals(0,boots.get());
        RehearsalTerminal.run(()->{ throw new IllegalStateException("private-exception-payload"); },()->null,output::add,true,"--interactive-operator");
        assertEquals(List.of("OPERATOR_HOST_FAILED_DISARMED"),output);
    }
    @ParameterizedTest @ValueSource(strings={"https://api.kite.trade","http://kite.zerodha.com","http://192.0.2.1:8080",
            "http://localhost:8080","http://127.0.0.1:8080@api.kite.trade","http://api.kite.trade:8080/127.0.0.1",
            "http://127.0.0.1:8080/?redirect=external","https://127.0.0.1:8080"})
    void hardGuardRejectsAnyNonFixtureTarget(String uri) {
        assertThrows(IllegalArgumentException.class,()->RehearsalIsolation.client(uri));
        assertDoesNotThrow(()->RehearsalIsolation.requireLoopback(URI.create("http://127.0.0.1:12345/orders/regular")));
    }

    @ParameterizedTest @ValueSource(strings={"wrong-text","wrong-id","missing","whitespace","oversized","unknown","uuid","argument","negative","zero","duration","configured-duration","case"})
    void confirmationAndParserDenialsRevokePermissionWithoutPayloadLeak(String fault) throws Exception {
        try(var f=fixture()) {
            f.approve(); String id=f.id.value().toString(); var commands=new ArrayList<>(armCommands(f));
            switch(fault) {
                case "wrong-text" -> { commands.add("execute "+id); commands.add("no"); }
                case "wrong-id" -> { commands.add("execute "+id); commands.add("CONFIRM execute 00000000-0000-0000-0000-000000000999"); }
                case "missing" -> commands.add("execute "+id);
                case "whitespace" -> { commands.add("execute "+id); commands.add("CONFIRM execute "+id+" "); }
                case "oversized" -> commands.add("x".repeat(129));
                case "unknown" -> commands.add("private-exception-payload");
                case "uuid" -> commands.add("execute non-uuid");
                case "argument" -> commands.add("execute "+id+" --force");
                case "negative" -> commands.add("arm "+id+" -1s");
                case "zero" -> commands.add("arm "+id+" 0s");
                case "duration" -> commands.add("arm "+id+" 99999s");
                case "configured-duration" -> { commands.clear(); commands.addAll(List.of("preflight "+id,"arm "+id+" 31s","CONFIRM arm "+id)); }
                case "case" -> commands.add("EXECUTE "+id);
                default -> fail(fault);
            }
            var output=f.console(commands.toArray(String[]::new));
            assertFalse(f.operator.status().armed()); untouched(f);
            assertTrue(output.stream().anyMatch(s->s.startsWith("DENIED") || s.contains("ARM_DURATION_INVALID")));
            assertTrue(output.stream().noneMatch(s->s.contains("private-exception-payload")));
            evidence.scenario("parser_"+fault,snapshot(f));
        }
    }

    @Test void secondCandidateCannotObtainRiskApprovalOrReplaceFirstOrderAuthorizationAcrossInstances() throws Exception {
        try(var f=fixture()) {
            f.approve(); f.arm(); var other=f.boot(f.authenticated());
            var resumed=other.getBean(OperatorExecutionService.class);
            assertEquals("RESUME_SUCCESS DISARMED",resumed.resume(resumed.prepareResume()));
            var b=other.getBean(OrderApplicationService.class).place(command("second-candidate"));
            var risk=other.getBean(RiskService.class).evaluate(b.id());
            assertFalse(risk.approved()); assertEquals(RiskReason.CONCURRENT_EXPOSURE_UNAVAILABLE,risk.reason());
            var second=other.getBean(OperatorExecutionService.class);
            assertFalse(second.arm(b.id(),Duration.ofSeconds(30)).armed());
            assertThrows(RuntimeException.class,()->second.execute(b.id()));
            assertFalse(f.operator.arm(b.id(),Duration.ofSeconds(30)).armed());
            f.assertCounts(0); assertEquals(0,f.gatewayCalls.get()); assertFalse(f.operator.status().armed());
            assertEquals(OrderState.RISK_APPROVED,f.orders.find(f.id).orElseThrow().state());
            evidence.scenario("secondCandidate",Map.of("riskReason",risk.reason().name(),"POST",0,"secondArmDenied",true));
        }
    }
    @Test void wrongAndUnknownOrdersAreDeniedThroughConsole() throws Exception {
        try(var f=fixture()) {
            f.approve(); var b=f.application.place(command("wrong-candidate")); var commands=new ArrayList<>(armCommands(f));
            commands.add("execute "+b.id().value()); commands.add("CONFIRM execute "+b.id().value());
            String unknown="00000000-0000-0000-0000-000000000999";
            commands.addAll(List.of("preflight "+unknown,"arm "+unknown+" 30s","execute "+unknown));
            var output=f.console(commands.toArray(String[]::new)); untouched(f); assertFalse(f.operator.status().armed());
            assertTrue(output.stream().anyMatch(s->s.contains("ORDER_NOT_FOUND")));
            assertFalse(f.operator.arm(new OrderId(UUID.fromString(unknown)),Duration.ofSeconds(30)).armed());
        }
    }

    @ParameterizedTest @ValueSource(longs={-1,0,1})
    void exactArmExpiryIsIndependentOfMarketAndRiskFreshness(long offsetMillis) throws Exception {
        try(var f=fixture()) {
            f.approve(); f.arm(); f.now.set(NOW.plusSeconds(30).plusMillis(offsetMillis));
            f.market.update(new Tick(INSTRUMENT.id(),BigDecimal.TEN,f.now.get()),f.publication);
            var report=f.operator.preflight(f.id);
            assertEquals(offsetMillis<0,report.ready());
            if (offsetMillis>=0) { assertEquals(DISARMED,report.reason()); assertThrows(RuntimeException.class,()->f.operator.execute(f.id)); }
            else f.operator.disarm();
            untouched(f); assertFalse(f.operator.status().armed());
            evidence.scenario("expiry_"+offsetMillis,Map.of("ready",report.ready(),"reason",report.reason().name(),"POST",0));
        }
    }

    @ParameterizedTest @ValueSource(strings={"session","stop","tick","health","disconnect","generation","subscriptions","exchange",
            "risk-expiry","version","risk-policy","risk-rejected","AMBIGUOUS","CONFLICT","BROKER_ORDER_MISSING","BROKER_STATE_UNAVAILABLE"})
    void changedEvidenceAfterReadyIsDeniedThroughConfirmedConsole(String fault) throws Exception {
        try(var f=fixture()) {
            f.approve(); var commands=new ArrayList<>(armCommands(f)); appendExecute(commands,f);
            var input=new ArrayDeque<>(commands); var output=new ArrayList<String>();
            new TrustedOperatorConsole(f.operator,Optional.of(f.context.getBean(OrderReconciliationService.class))).run(()->{
                if (input.size()==2) { assertTrue(f.operator.preflight(f.id).ready()); change(f,fault); assertFault(f,fault); }
                return input.poll();
            },output::add);
            f.assertCounts(0); assertEquals(0,f.gatewayCalls.get()); assertFalse(f.operator.status().armed());
            assertEquals(0,count(f,"execution_authorizations"));
            assertTrue(output.contains("DENIED INVALID_COMMAND_OR_EVIDENCE"));
            evidence.scenario("toctou_"+fault,Map.of("gates",f.operator.preflight(f.id).gates(),"POST",0,"armed",false));
        }
    }
    private static void change(Fixture f,String fault) {
        switch(fault) {
            case "session" -> { f.session.install(new KiteAccessToken("replacementSynthetic",NOW,NOW.plusSeconds(3600))); f.session.profileValidated(); }
            case "stop" -> f.stop.set(true);
            case "tick" -> f.now.set(NOW.plusSeconds(5));
            case "generation" -> f.publication.revoke();
            case "health", "disconnect", "subscriptions" -> {
                var h=f.health;
                f.health=new MarketDataHealth(fault.equals("disconnect") ? MarketDataGateway.State.STOPPED : h.connectionState(),
                        fault.equals("health") ? MarketDataHealth.Status.DEGRADED : h.status(),h.reason(),h.connectedAt(),h.lastMessageAt(),h.lastTickAt(),
                        h.desiredSubscriptions(),fault.equals("subscriptions") ? 0 : h.activeSubscriptions(),0,0,0,0,0,0,0);
            }
            case "exchange" -> assertTrue(f.market.update(new Tick(INSTRUMENT.id(),BigDecimal.TEN,NOW,Optional.of(NOW.minusSeconds(5)),Optional.empty(),Optional.empty()),f.publication));
            case "risk-expiry" -> f.jdbc.update("UPDATE trading.risk_decisions SET evaluated_at=? WHERE order_id=?",Timestamp.from(NOW.minusSeconds(60)),f.id.value());
            case "version" -> f.jdbc.update("UPDATE trading.orders SET version=version+1 WHERE order_id=?",f.id.value());
            case "risk-policy" -> f.jdbc.update("UPDATE trading.risk_decisions SET policy_version=? WHERE order_id=?","cash-v1:"+"0".repeat(64),f.id.value());
            case "risk-rejected" -> f.jdbc.update("UPDATE trading.risk_decisions SET outcome='REJECTED',reason='TRADING_HALTED' WHERE order_id=?",f.id.value());
            default -> {
                var outcome=ReconciliationOutcome.valueOf(fault); var order=f.orders.find(f.id).orElseThrow();
                var decision=new ReconciliationDecision(UUID.randomUUID(),f.id,order.state(),Optional.of(order.state()),outcome,
                        ReconciliationReason.READ_FAILED,NOW,order.version());
                assertTrue(f.context.getBean(ReconciliationStore.class).apply(order,order,decision,List.of()));
            }
        }
    }
    private static void assertFault(Fixture f,String fault) {
        var gate=switch(fault) {
            case "session" -> ExecutionReadiness.Gate.SESSION_BOUND;
            case "stop" -> ExecutionReadiness.Gate.EMERGENCY_STOP_CLEAR;
            case "tick","exchange" -> ExecutionReadiness.Gate.MARKET_DATA_FRESH;
            case "health","disconnect","generation","subscriptions" -> ExecutionReadiness.Gate.MARKET_DATA_HEALTHY;
            case "risk-expiry","version","risk-policy","risk-rejected" -> ExecutionReadiness.Gate.RISK_DECISION_CURRENT;
            default -> ExecutionReadiness.Gate.RECONCILIATION_CONFLICT_CLEAR;
        };
        var reason=switch(fault) {
            case "session" -> DISARMED;
            case "stop" -> EMERGENCY_STOP;
            case "tick","exchange" -> MARKET_DATA_STALE;
            case "health","disconnect","generation","subscriptions" -> MARKET_DATA_UNAVAILABLE;
            case "risk-expiry" -> RISK_APPROVAL_EXPIRED;
            case "version" -> ORDER_VERSION_CHANGED;
            case "risk-policy" -> RISK_POLICY_MISMATCH;
            case "risk-rejected" -> RISK_APPROVAL_MISSING;
            default -> RECONCILIATION_CONFLICT;
        };
        assertEquals(reason,f.operator.preflight(f.id).gates().get(gate));
    }

    @ParameterizedTest @ValueSource(strings={"stop","tick","risk-rejected","CONFLICT"})
    void preArmRehearsalStopsOnAnyOtherFailedPrerequisite(String fault) throws Exception {
        try(var f=fixture()) {
            f.approve(); change(f,fault); assertFault(f,fault);
            assertFalse(OperatorExecutionService.canArm(f.operator.preflight(f.id)));
            var output=f.console(armCommands(f).toArray(String[]::new));
            assertTrue(output.contains("DENIED PREFLIGHT_OR_CONFIRMATION_REQUIRED"));
            assertFalse(f.operator.status().armed()); untouched(f);
            evidence.scenario("preArmStop_"+fault,Map.of("gates",f.operator.preflight(f.id).gates(),"POST",0));
        }
    }

    @ParameterizedTest @ValueSource(strings={"ack-store","response-loss","malformed"})
    void ambiguousOutcomeRecoversExplicitlyAfterRestartWithoutAnotherPlacement(String fault) throws Exception {
        try(var f=fixture()) {
            f.approve();
            if (fault.equals("ack-store")) doThrow(new IllegalStateException("private-exception-payload")).when(f.orders).attachBrokerOrderId(any(),any());
            else f.mode=fault.equals("response-loss") ? Mode.RESPONSE_LOST : Mode.MALFORMED;
            var commands=new ArrayList<>(armCommands(f)); appendExecute(commands,f);
            var output=f.console(commands.toArray(String[]::new)); f.assertCounts(1); f.consumed();
            assertEquals(OrderState.SUBMITTING,f.orders.find(f.id).orElseThrow().state());
            assertTrue(f.orders.find(f.id).orElseThrow().brokerOrderId().isEmpty());
            assertTrue(output.stream().noneMatch(s->s.contains("private-exception-payload")));
            f.context.close(); var restarted=f.boot(f.authenticated()); var operator=restarted.getBean(OperatorExecutionService.class);
            assertFalse(operator.status().armed()); assertEquals(RuntimeExecutionArming.PermitState.NONE,operator.status().permitState());
            assertThrows(RuntimeException.class,()->operator.execute(f.id));
            assertEquals(0,count(f,"reconciliation_decisions")); f.brokerObservation();
            var input=new ArrayDeque<>(List.of("reconcile "+f.id.value(),"reconcile "+f.id.value()));
            new TrustedOperatorConsole(operator,Optional.of(restarted.getBean(OrderReconciliationService.class))).run(input::poll,output::add);
            assertEquals(OrderState.OPEN,f.orders.find(f.id).orElseThrow().state());
            assertTrue(f.orders.find(f.id).orElseThrow().brokerOrderId().isPresent());
            assertEquals(2,count(f,"reconciliation_decisions")); f.assertCounts(1); assertFalse(operator.status().armed());
            var observed=snapshot(f); observed.put("restartArmed",false); observed.put("restartPermit","NONE");
            evidence.scenario("recovery_"+fault,observed);
        }
    }

    @ParameterizedTest @ValueSource(strings={"REJECT","AUTH"})
    void brokerRejectionsDoNotRetryOrLeakAndAuthenticationInvalidates(String response) throws Exception {
        try(var f=fixture()) {
            f.approve(); f.mode=Mode.valueOf(response); var commands=new ArrayList<>(armCommands(f)); appendExecute(commands,f);
            var output=f.console(commands.toArray(String[]::new)); f.assertCounts(1); f.consumed();
            assertEquals(OrderState.FAILED,f.orders.find(f.id).orElseThrow().state());
            if (response.equals("AUTH")) assertFalse(f.session.authenticated());
            assertEquals(0,count(f,"reconciliation_decisions"));
            assertTrue(output.stream().noneMatch(s->s.contains("synthetic sensitive response") || s.contains("syntheticToken")));
            evidence.scenario("broker_"+response,snapshot(f));
        }
    }

    @ParameterizedTest @ValueSource(strings={"stop","health","disconnect","generation","subscriptions","exchange",
            "risk-expiry","risk-policy","risk-rejected","AMBIGUOUS","CONFLICT","BROKER_ORDER_MISSING","BROKER_STATE_UNAVAILABLE"})
    void finalDispatchRecheckRejectsChangedEvidenceEvenAfterDurableAdmission(String fault) throws Exception {
        try(var f=fixture()) {
            f.approve(); f.arm(); f.beforeDispatch=()->change(f,fault);
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id));
            f.assertCounts(0); assertEquals(1,f.gatewayCalls.get()); f.consumed();
            assertTrue(count(f,"execution_authorizations")>=2);
            assertEquals(fault.equals("stop") ? OrderState.SUBMITTING : OrderState.FAILED,f.orders.find(f.id).orElseThrow().state());
            evidence.scenario("dispatch_"+fault,snapshot(f));
        }
    }
    @ParameterizedTest @ValueSource(strings={"running","unused","claimed","authorized","admitted","before-http","http-in-flight","after-http","persisted","acknowledged"})
    void abruptChildJvmCrashRehearsal(String checkpoint) throws Exception {
        // Reuse the existing real-process probe, including its durable-state/restart/no-retry assertions.
        new OneOrderOperatorIntegrationTest().actualChildJvmCrashPreservesDurableAdmissionAndNeverRestoresPermission(checkpoint);
        String state=Set.of("running","unused","claimed","authorized").contains(checkpoint) ? "RISK_APPROVED"
                : Set.of("persisted","acknowledged").contains(checkpoint) ? "SUBMITTED" : "SUBMITTING";
        evidence.scenario("crash_"+checkpoint,Map.of("durableState",state,"restartArmed",false,"restartPermit","NONE",
                "POST",Set.of("http-in-flight","after-http","persisted","acknowledged").contains(checkpoint) ? 1 : 0,
                "automaticRetry",false,"requiresExplicitReconciliation",!state.equals("RISK_APPROVED")));
    }

    @Test void independentContextsRaceSameOrderThroughPostgresAdmission() throws Exception {
        new OneOrderOperatorIntegrationTest().twoIndependentContextsCannotSubmitSameOrderTwice();
        evidence.scenario("sameOrderConcurrency",Map.of("instances",2,"POST",1,"bothDisarmed",true));
    }
}
