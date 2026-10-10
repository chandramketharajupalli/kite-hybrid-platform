package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.kitehybrid.platform.broker.infrastructure.kite.KiteEquityReadHarness.*;

class KiteEquityReadHarnessTest {
    static final Instant NOW=KiteEquityReadRequestFactoryTest.NOW;
    static class MutableClock extends Clock {
        Instant now=NOW;
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
    static final class Fixture implements AutoCloseable {
        final MutableClock clock=new MutableClock();
        final KiteSession session=KiteEquityReadRequestFactoryTest.session(clock);
        final AtomicBoolean startup=new AtomicBoolean(true);
        final RuntimeTradingHalt halt=new RuntimeTradingHalt(startup::get);
        final KiteEquityReadIntegrity integrity=mock(KiteEquityReadIntegrity.class);
        final KiteEquityReadIntegrity.Fingerprint fingerprint=mock(KiteEquityReadIntegrity.Fingerprint.class);
        final HttpServer peer;
        final KiteEquityReadRequestFactory wire;
        final AtomicInteger calls=new AtomicInteger();
        String body=KiteTradingReadFixtures.envelope(KiteTradingReadFixtures.SEGMENT);
        int status=200;
        Runnable onResponse=()->{};
        Fixture() throws Exception {
            when(integrity.capture()).thenReturn(fingerprint);when(fingerprint.matches(fingerprint)).thenReturn(true);
            peer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            peer.createContext("/",exchange->{calls.incrementAndGet();onResponse.run();
                var bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if(status==302)exchange.getResponseHeaders().set("Location","/orders");
                exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
            peer.start();wire=KiteEquityReadRequestFactory.loopback(URI.create("http://127.0.0.1:"+peer.getAddress().getPort()),Duration.ofMillis(250));
        }
        KiteEquityReadHarness harness(Mode mode){return new KiteEquityReadHarness(wire,session,integrity,halt,clock,mode);}
        @Override public void close(){wire.close();peer.stop(0);}
    }
    @ParameterizedTest @ValueSource(strings={"disabled","missing-auth","unverified","halt","database","mode"})
    void preconditionsDenyBeforeHttp(String fault)throws Exception {
        try(var f=new Fixture()) {
            if(fault.equals("missing-auth"))f.session.clear();
            if(fault.equals("unverified"))f.session.install(new KiteAccessToken("syntheticUnverified",NOW,NOW.plusSeconds(60)));
            if(fault.equals("halt"))f.startup.set(false);
            if(fault.equals("database"))when(f.integrity.capture()).thenThrow(new IllegalStateException("syntheticPrivateDbText"));
            var harness=fault.equals("disabled")?new KiteEquityReadHarness(f.wire,f.session,f.integrity,f.halt,f.clock)
                    :f.harness(fault.equals("mode")?Mode.SEPARATELY_APPROVED_REAL:Mode.SYNTHETIC);
            var result=harness.run();
            assertThat(result.outcome()).isNotEqualTo(Outcome.OBSERVED);assertThat(result.requestAttempts()).isZero();
            assertThat(result.toString()).doesNotContain("syntheticPrivateDbText","syntheticToken");
            assertThat(f.calls).hasValue(0);
        }
    }
    @ParameterizedTest @ValueSource(strings={"success","401","403","429","500","503","redirect","malformed","duplicate","trailing",
            "oversized","missing","null","segments","commodity","negative","zero","high-net","utilised","conflicting-cash","unknown","timeout"})
    void allOutcomesAreSingleUseRedactedAndPreserveState(String fault)throws Exception {
        try(var f=new Fixture()) {
            switch(fault){
                case "401","403","429","500","503" -> f.status=Integer.parseInt(fault);
                case "redirect" -> f.status=302;
                case "malformed" -> f.body="{syntheticPrivateBody";
                case "duplicate" -> f.body=f.body.replace("\"enabled\":true","\"enabled\":true,\"enabled\":false");
                case "trailing" -> f.body+="{}";
                case "oversized" -> f.body=" ".repeat(65537);
                case "missing" -> f.body=f.body.replace("\"cash\":245431.6,","");
                case "null" -> f.body=f.body.replace("245431.6","null");
                case "segments","commodity" -> f.body="{\"status\":\"success\",\"data\":{\""+fault+"\":{}}}";
                case "negative" -> f.body=f.body.replace("245431.6","-0.01");
                case "zero" -> f.body=f.body.replace("245431.6","0");
                case "high-net" -> f.body=f.body.replace("245431.6","0.01");
                case "utilised" -> f.body=f.body.replace("\"collateral\":0","\"collateral\":1000").replace("\"stock_collateral\":0","\"stock_collateral\":1000");
                case "conflicting-cash" -> f.body=f.body.replace("\"cash\":245431.6","\"cash\":-1");
                case "unknown" -> f.body=f.body.replace("\"enabled\"","\"eligible\":true,\"enabled\"");
                case "timeout" -> f.onResponse=()->{try{Thread.sleep(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}};
            }
            var epoch=f.halt.epoch();var harness=f.harness(Mode.SYNTHETIC);var result=harness.run();
            boolean valid=java.util.Set.of("success","negative","zero","high-net","utilised","conflicting-cash","unknown").contains(fault);
            assertThat(result.outcome()==Outcome.OBSERVED).isEqualTo(valid);
            if (valid) {
                var contract=com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.equityObservation(
                        KiteEquityMarginReadAdapterTest.quote(),KiteEquityMarginReadAdapterTest.REQUEST,
                        KiteEquityMarginReadAdapterTest.REF,KiteEquityMarginReadAdapterTest.LIMITS,
                        result.observation().receivedAt(),NOW,NOW,true,
                        com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.Origin.SYNTHETIC);
                assertThat(contract.questions().values()).allMatch(term -> term.status()==
                        com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.Proof.UNKNOWN);
                assertThat(contract.cashOnlyFunding()).isEqualTo(com.kitehybrid.platform.risk.domain.RiskReason.MIS_MARGIN_UNAVAILABLE);
                assertThat(contract.scope().requestFingerprint()).hasSize(64);
                assertThat(contract.scope().instrumentMappingFingerprint()).hasSize(64);
            }
            assertThat(result.statePreserved()).isTrue();assertThat(result.requestAttempts()).isEqualTo(1);
            assertThat(result.toString()).doesNotContain("245431","99725","syntheticPrivateBody","syntheticToken");
            assertThat(harness.run().outcome()).isEqualTo(Outcome.ALREADY_USED);
            assertThat(f.calls).hasValue(1);assertThat(f.halt.epoch()).isSameAs(epoch);assertThat(f.halt.getAsBoolean()).isTrue();
            verify(f.integrity,times(2)).capture();
        }
    }
    @ParameterizedTest @ValueSource(strings={"hash","database-unavailable","halt-epoch","session","stale","future"})
    void finalVerificationDiscardsObservationOnDeterioration(String fault)throws Exception {
        try(var f=new Fixture()) {
            var count=new AtomicInteger();
            when(f.integrity.capture()).thenAnswer(invocation->{
                if(count.incrementAndGet()==2)switch(fault){
                    case "hash" -> {when(f.fingerprint.matches(f.fingerprint)).thenReturn(false);}
                    case "database-unavailable" -> throw new IllegalStateException("syntheticPrivateFailure");
                    case "halt-epoch" -> f.halt.halt();
                    case "session" -> f.session.install(new KiteAccessToken("syntheticChanged",NOW,NOW.plusSeconds(60)));
                    case "stale" -> f.clock.now=NOW.plusSeconds(5);
                    case "future" -> f.clock.now=NOW.minusNanos(1);
                }
                return f.fingerprint;
            });
            var result=f.harness(Mode.SYNTHETIC).run();assertThat(result.outcome()).isNotEqualTo(Outcome.OBSERVED);
            assertThat(result.observation()).isNull();assertThat(f.calls).hasValue(1);
        }
    }
    @Test void competingCallsCannotSpendBudgetTwice()throws Exception {
        try(var f=new Fixture();var workers=Executors.newFixedThreadPool(2)){
            var harness=f.harness(Mode.SYNTHETIC);var a=workers.submit(harness::run);var b=workers.submit(harness::run);
            assertThat(java.util.List.of(a.get().outcome(),b.get().outcome())).containsExactlyInAnyOrder(Outcome.OBSERVED,Outcome.ALREADY_USED);
            assertThat(f.calls).hasValue(1);
        }
    }
}
