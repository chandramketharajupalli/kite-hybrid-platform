package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.marketdata.infrastructure.InMemoryLatestMarketDataStore;
import com.kitehybrid.platform.operator.application.*;
import com.kitehybrid.platform.operator.infrastructure.PostgresOperationalReadiness;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.order.infrastructure.*;
import com.kitehybrid.platform.risk.infrastructure.PostgresRiskDecisionStore;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.RestClient;

/** Child JVM crash probe, compiled only by the integration profile. Accepts only disposable/loopback targets. */
public final class OneOrderCrashProbe {
    private OneOrderCrashProbe() {}
    public static void main(String[] args) {
        if (args.length != 6 || !args[0].matches("jdbc:postgresql://localhost:[0-9]+/one_order_[a-f0-9]+\\?loggerLevel=OFF")
                || !args[3].matches("http://127\\.0\\.0\\.1:[0-9]+")
                || !Set.of("running","unused","claimed","authorized","admitted","before-http","after-http","persisted","http-in-flight","acknowledged").contains(args[5]))
            throw new IllegalArgumentException("Disposable crash fixture required");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        var now=OneOrderOperatorIntegrationTest.NOW;
        var clock=Clock.fixed(now,ZoneOffset.UTC);
        var instrument=OneOrderOperatorIntegrationTest.INSTRUMENT;
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(args[0],args[1],args[2]));
        var repository=new PostgresOrderRepository(jdbc);
        var orders=(OrderRepository)java.lang.reflect.Proxy.newProxyInstance(OrderRepository.class.getClassLoader(),new Class<?>[]{OrderRepository.class},
                (proxy,method,arguments)->{
                    Object result=method.invoke(repository,arguments);
                    if (method.getName().equals("attachBrokerOrderId") && Boolean.TRUE.equals(result)) halt(args[5],"persisted");
                    return result;
                });
        var risks=new PostgresRiskDecisionStore(jdbc,orders);
        var id=new OrderId(UUID.fromString(args[4]));
        var registry=new InMemoryInstrumentRegistry(); registry.replace(List.of(instrument),now);
        var market=new InMemoryLatestMarketDataStore(); market.update(new Tick(instrument.id(),BigDecimal.TEN,now));
        var session=new KiteSession(new KiteProperties("syntheticKey","syntheticSecret","",true),clock);
        session.install(new KiteAccessToken("syntheticToken",now.minusSeconds(1),now.plusSeconds(3600))); session.profileValidated();
        var metrics=new SimpleMeterRegistry();
        var runtimeHalt=new com.kitehybrid.platform.shared.application.RuntimeTradingHalt(() -> false);
        var arm=new RuntimeExecutionArming(metrics,session::executionIdentity,runtimeHalt);
        var properties=new OrderExecutionProperties(true,Set.of(instrument.id()),1,new BigDecimal("20"),Duration.ofSeconds(60),Duration.ofSeconds(5),
                risks.find(id).orElseThrow().policyVersion(),"crash-probe");
        var live=new LiveTestProperties(true,Set.of(instrument.id()),1,new BigDecimal("20"),Duration.ofSeconds(30));
        var checks=new LiveTestExecutionChecks(true,live,arm,new PostgresOperationalReadiness(jdbc,()->true,"public","flyway_schema_history"),clock);
        var audit=new PostgresExecutionAuthorizationAuditStore(jdbc);
        var policy=new ExecutionSafetyPolicy(properties,arm,runtimeHalt,session,risks,registry,market,OperatorPreflightDryRunTest::healthy,orders,clock,metrics,
                decision->{ audit.record(decision); halt(args[5],"authorized"); },checks);
        var adapter=new KiteOrderAdapter(new KiteRestTransport(RehearsalIsolation.client(args[3],args[5].equals("http-in-flight") ? 30000 : 1000),session),registry,properties);
        var gateway=new OrderExecutionGateway() {
            @Override public String place(OrderRecord order,Runnable validation) {
                halt(args[5],"admitted");
                String result=adapter.place(order,()->{ validation.run(); halt(args[5],"before-http"); });
                halt(args[5],"after-http"); return result;
            }
            @Override public void modify(OrderRecord order,ModifyOrder command) { throw new AssertionError(); }
            @Override public void cancel(OrderRecord order,CancelOrder command) { throw new AssertionError(); }
        };
        var application=new OrderApplicationService(orders,new OrderCommandValidator(registry),gateway,properties,clock,metrics,policy);
        var operator=new OperatorExecutionService(true,properties,live,arm,session,runtimeHalt,orders,policy,application,clock);
        if (!operator.resume(operator.prepareResume()).startsWith("RESUME_SUCCESS")) throw new IllegalStateException("Crash probe could not resume");
        halt(args[5],"running");
        if (!operator.arm(id,Duration.ofSeconds(30)).armed()) throw new IllegalStateException("Crash probe could not arm");
        halt(args[5],"unused");
        if (args[5].equals("claimed")) { arm.claim(id,now); halt(args[5],"claimed"); }
        operator.execute(id); halt(args[5],"acknowledged");
        throw new AssertionError("Crash checkpoint not reached");
    }
    private static void halt(String selected,String checkpoint) {
        if (selected.equals(checkpoint)) Runtime.getRuntime().halt(73); // Deliberately bypass finally/shutdown hooks in THIS CHILD ONLY.
    }
}
