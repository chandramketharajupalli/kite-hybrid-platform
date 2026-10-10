package com.kitehybrid.platform.broker.infrastructure.kite;

import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KiteEquityReadHandoffTest {
    @ParameterizedTest @ValueSource(strings={"issue","admission","response"})
    void unknownHaltEvidenceNeverProducesAnObservation(String stage)throws Exception{
        try(var f=new KiteEquityReadHarnessTest.Fixture();var owner=new KiteEquityReadHandoff.Owner();
            var isolation=new KiteEquityReadIsolation(f.clock,Duration.ofSeconds(10),()->true)){
            var known=new AtomicBoolean(!stage.equals("issue"));
            var halt=new com.kitehybrid.platform.shared.application.RuntimeTradingHalt(()->{
                if(!known.get())throw new IllegalStateException("privateHaltMarker");return true;
            });
            var recipient=new KiteEquityReadHandoff.Recipient();
            if(stage.equals("issue")){
                assertThatThrownBy(()->new KiteEquityReadHandoff(owner,recipient,f.session,halt,isolation,f.clock,
                        Duration.ofSeconds(5),f.wire,f.integrity)).hasMessageNotContaining("privateHaltMarker");
                var direct=new KiteEquityReadHarness(f.wire,f.session,f.integrity,halt,f.clock,KiteEquityReadHarness.Mode.SYNTHETIC);
                assertThat(direct.run().outcome()).isEqualTo(KiteEquityReadHarness.Outcome.PRECONDITION_DENIED);
                assertThat(f.calls).hasValue(0);return;
            }
            try(var handoff=new KiteEquityReadHandoff(owner,recipient,f.session,halt,isolation,f.clock,
                    Duration.ofSeconds(5),f.wire,f.integrity)){
                if(stage.equals("admission")){
                    known.set(false);
                    assertThatThrownBy(()->handoff.consume(owner,recipient,"GET",PATH)).isInstanceOf(RuntimeException.class);
                    assertThat(f.calls).hasValue(0);
                }else{
                    f.onResponse=()->known.set(false);
                    var result=handoff.consume(owner,recipient,"GET",PATH);
                    assertThat(result.outcome()).isEqualTo(KiteEquityReadHarness.Outcome.STATE_CHANGED);
                    assertThat(result.observation()).isNull();verify(f.integrity,times(2)).capture();
                    assertThat(f.calls).hasValue(1);
                }
                assertThat(halt.getAsBoolean()).isTrue();
            }
        }
    }
    @ParameterizedTest @ValueSource(strings={"owner","epoch","session","resume"})
    void concurrentInvalidationDuringResponseDiscardsReceiptAndStillChecksIntegrity(String reason)throws Exception{
        try(var f=new Fixture();var pool=Executors.newSingleThreadExecutor()){
            f.base.onResponse=()->{
                try{pool.submit(()->{
                    switch(reason){case "owner"->f.owner.close();case "epoch"->f.base.halt.halt();
                        case "session"->f.base.session.rejectMarketData(f.base.session.marketDataStatus().generation());
                        case "resume"->{f.base.startup.set(false);assertThat(f.base.halt.resume(f.base.halt.epoch())).isTrue();}}
                }).get(5,TimeUnit.SECONDS);}catch(Exception failure){throw new IllegalStateException(failure);}
            };
            var result=f.consume();assertThat(result.observation()).isNull();
            assertThat(result.outcome()).isEqualTo(KiteEquityReadHarness.Outcome.STATE_CHANGED);
            verify(f.base.integrity,times(2)).capture();assertThat(f.base.calls).hasValue(1);
            assertThatThrownBy(f::consume).isInstanceOf(RuntimeException.class);
        }
    }
    @Test void historicalReceiptDoesNotPreserveCapabilityAfterSessionInvalidation()throws Exception{
        try(var f=new Fixture()){
            var receipt=f.consume();f.base.session.clear();
            assertThat(receipt.observation().provenance()).isEqualTo(KiteEquityMarginReadAdapter.Provenance.SYNTHETIC_TRANSPORT);
            assertThat(f.handoff.inspect(f.owner,f.recipient)).isFalse();
            assertThatThrownBy(f::consume).isInstanceOf(RuntimeException.class);
            assertThat(f.base.calls).hasValue(1);
            // The detached timestamp/provenance receipt is historical, not a live capability or funding proof.
        }
    }
    @Test void locallyAuthenticatedKiteSessionIsSyntheticScopeNotIndependentBrokerProof()throws Exception{
        try(var f=new Fixture()){
            assertThat(f.base.session.authenticated()).isTrue();
            var privateIdentity=f.base.session.executionIdentity().orElseThrow().toString();
            var result=f.consume();
            assertThat(result.observation().provenance()).isEqualTo(KiteEquityMarginReadAdapter.Provenance.SYNTHETIC_TRANSPORT);
            var contract=com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.equityObservation(
                    KiteEquityMarginReadAdapterTest.quote(),KiteEquityMarginReadAdapterTest.REQUEST,
                    KiteEquityMarginReadAdapterTest.REF,KiteEquityMarginReadAdapterTest.LIMITS,
                    result.observation().receivedAt(),f.base.clock.instant(),f.base.clock.instant(),true,
                    com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.Origin.SYNTHETIC);
            assertThat(contract.questions().values()).allMatch(term->term.status()==
                    com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.Proof.UNKNOWN);
            assertThat(java.util.Arrays.stream(KiteEquityMarginReadAdapter.Observation.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName)).containsExactly("provenance","receivedAt");
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                    .setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.ALL,com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
            assertThat(mapper.writeValueAsString(result)).doesNotContain(privateIdentity,"syntheticToken","syntheticKey","245431.6");
            assertThat(mapper.writeValueAsString(f.handoff)).isEqualTo("{}");
            assertThat(f.base.calls).hasValue(1);
        }
    }
    static final String PATH="/user/margins/equity";
    static final class Fixture implements AutoCloseable {
        final KiteEquityReadHarnessTest.Fixture base=new KiteEquityReadHarnessTest.Fixture();
        final KiteEquityReadHandoff.Owner owner=new KiteEquityReadHandoff.Owner();
        final KiteEquityReadHandoff.Recipient recipient=new KiteEquityReadHandoff.Recipient();
        final AtomicBoolean witness=new AtomicBoolean(true);
        final KiteEquityReadIsolation isolation=new KiteEquityReadIsolation(base.clock,Duration.ofSeconds(10),witness::get);
        final KiteEquityReadHandoff handoff=new KiteEquityReadHandoff(owner,recipient,base.session,base.halt,isolation,
                base.clock,Duration.ofSeconds(5),base.wire,base.integrity);
        Fixture()throws Exception{}
        KiteEquityReadHarness.Result consume(){return handoff.consume(owner,recipient,"GET",PATH);}
        @Override public void close(){handoff.close();owner.close();isolation.close();base.close();}
    }
    @Test void inspectThenConsumeDoesNotExportSessionAndReplayIsDenied()throws Exception{
        try(var f=new Fixture()){
            assertThat(f.handoff.inspect(f.owner,f.recipient)).isTrue();
            var result=f.consume();assertThat(result.outcome()).isEqualTo(KiteEquityReadHarness.Outcome.OBSERVED);
            assertThat(result.observation().provenance()).isEqualTo(KiteEquityMarginReadAdapter.Provenance.SYNTHETIC_TRANSPORT);
            assertThatThrownBy(f::consume).hasMessageNotContaining("synthetic");
            assertThat(f.base.calls).hasValue(1);
        }
    }
    @ParameterizedTest @ValueSource(strings={"owner","recipient","method","path","expired","rollback","logout","session","epoch","halt","owner-death","isolation","revoked","restart","visibility"})
    void everyFailedCheckBurnsCapabilityBeforeHttp(String fault)throws Exception{
        try(var f=new Fixture()){
            var owner=f.owner;var recipient=f.recipient;String method="GET",path=PATH;
            switch(fault){
                case "owner","restart" -> owner=new KiteEquityReadHandoff.Owner();
                case "recipient" -> recipient=new KiteEquityReadHandoff.Recipient();
                case "method" -> method="POST";
                case "path" -> path="/orders";
                case "expired" -> f.base.clock.now=f.base.clock.now.plusSeconds(5);
                case "rollback" -> f.base.clock.now=f.base.clock.now.minusNanos(1);
                case "logout" -> f.base.session.clear();
                case "session" -> {f.base.session.install(new com.kitehybrid.platform.broker.application.auth.KiteAccessToken("syntheticReplacement",f.base.clock.now,f.base.clock.now.plusSeconds(60)));f.base.session.profileValidated();}
                case "epoch" -> f.base.halt.halt();
                case "halt" -> f.base.startup.set(false);
                case "owner-death" -> f.owner.close();
                case "isolation","visibility" -> f.witness.set(false);
                case "revoked" -> f.handoff.close();
            }
            final var o=owner;final var r=recipient;final var m=method;final var p=path;
            assertThatThrownBy(()->f.handoff.consume(o,r,m,p)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(f::consume).isInstanceOf(RuntimeException.class);
            assertThat(f.base.calls).hasValue(0);assertThat(f.base.wire.attempts()).isZero();
        }
    }
    @Test void concurrentConsumeHasExactlyOneWinner()throws Exception{
        try(var f=new Fixture();var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);Callable<Boolean> task=()->{start.await();try{return f.consume().outcome()==KiteEquityReadHarness.Outcome.OBSERVED;}catch(RuntimeException denied){return false;}};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertThat(java.util.List.of(a.get(),b.get())).containsExactlyInAnyOrder(true,false);
            assertThat(f.base.calls).hasValue(1);
        }
    }
    @ParameterizedTest @ValueSource(strings={"before","during"})
    void isolationLossNeverProducesUsableObservationOrAnotherRequest(String when)throws Exception{
        try(var f=new Fixture()){
            if(when.equals("before"))when(f.base.integrity.capture()).thenAnswer(i->{f.witness.set(false);return f.base.fingerprint;});
            else f.base.onResponse=()->f.witness.set(false);
            assertThat(f.consume().observation()).isNull();
            assertThat(f.base.calls).hasValue(when.equals("before")?0:1);
            assertThatThrownBy(f::consume).isInstanceOf(RuntimeException.class);
        }
    }
    @Test void serializationFailsWithoutExposingCapabilityOrCredentials()throws Exception{
        try(var f=new Fixture()){
            assertThat(f.handoff.toString()).isEqualTo("SyntheticEquityHandoff[REDACTED]");
            var bytes=new java.io.ByteArrayOutputStream();
            assertThatThrownBy(()->new java.io.ObjectOutputStream(bytes).writeObject(f.handoff)).isInstanceOf(java.io.NotSerializableException.class);
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(f.handoff)).isEqualTo("{}");
            var reflective=new com.fasterxml.jackson.databind.ObjectMapper()
                    .setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.ALL,com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS);
            assertThat(reflective.writeValueAsString(f.handoff)).isEqualTo("{}");
            assertThat(reflective.writeValueAsString(f.isolation)).isEqualTo("{}");
            assertThat(bytes.toString(java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("syntheticToken");
        }
    }
    @ParameterizedTest @ValueSource(strings={"expiry","rollback","missing","closed","exception"})
    void witnessLeaseFailClosedAndCannotRecover(String failure){
        var clock=new KiteEquityReadHarnessTest.MutableClock();var available=new AtomicBoolean(true);
        var lease=new KiteEquityReadIsolation(clock,Duration.ofSeconds(1),()->{if(failure.equals("exception"))throw new IllegalStateException("private");return available.get();});
        switch(failure){case "expiry"->clock.now=clock.now.plusSeconds(1);case "rollback"->clock.now=clock.now.minusNanos(1);case "missing"->available.set(false);case "closed"->lease.close();}
        assertThat(lease.valid()).isFalse();clock.now=KiteEquityReadHarnessTest.NOW;available.set(true);assertThat(lease.valid()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"org.apache.hc.client5.http.wire","org.apache.hc.client5.http.headers"})
    void verboseLoggerAbortsBeforeAnySensitiveRequest(String name)throws Exception{
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(name);var old=logger.getLevel();
        try(var f=new Fixture()){
            logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            assertThat(f.consume().outcome()).isEqualTo(KiteEquityReadHarness.Outcome.PRECONDITION_DENIED);
            assertThat(f.base.calls).hasValue(0);
        }finally{logger.setLevel(old);}
    }
    @Test void capturedLogsDoNotContainSyntheticCredentialsOrBalances()throws Exception{
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var capture=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();capture.start();logger.addAppender(capture);
        try(var f=new Fixture()){
            f.base.body="{syntheticPrivateBody";f.consume();
            String output=capture.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining());
            assertThat(output).doesNotContain("syntheticToken","syntheticKey","syntheticPrivateBody","Authorization","245431.6");
        }finally{logger.detachAppender(capture);capture.stop();}
    }
    @Test void officialWireCannotReceiveSyntheticHandoff()throws Exception{
        try(var f=new Fixture();var real=KiteEquityReadRequestFactory.official()){
            assertThatThrownBy(()->new KiteEquityReadHandoff(f.owner,f.recipient,f.base.session,f.base.halt,f.isolation,
                    f.base.clock,Duration.ofSeconds(5),real,f.base.integrity)).isInstanceOf(RuntimeException.class);
            assertThat(real.attempts()).isZero();
        }
    }

    @ParameterizedTest @ValueSource(strings={"401","403","isolation"})
    void postReadIntegrityIsAttemptedEvenWhenCapabilityIsRevoked(String reason)throws Exception{
        try(var f=new Fixture()){
            if(reason.equals("isolation"))f.base.onResponse=()->f.witness.set(false);else f.base.status=Integer.parseInt(reason);
            assertThat(f.consume().observation()).isNull();
            verify(f.base.integrity,times(2)).capture();
            assertThat(f.base.calls).hasValue(1);
        }
    }

    @ParameterizedTest @ValueSource(strings={"auth","owner","zero","negative","oversized","witness","unknown"})
    void issuanceRequiresValidAuthenticatedContextAndBoundedLifetime(String fault)throws Exception{
        try(var f=new KiteEquityReadHarnessTest.Fixture();var owner=new KiteEquityReadHandoff.Owner()){
            if(fault.equals("auth"))f.session.clear();
            if(fault.equals("owner"))owner.close();
            Duration lifetime=switch(fault){case "zero"->Duration.ZERO;case "negative"->Duration.ofSeconds(-1);case "oversized"->Duration.ofSeconds(31);default->Duration.ofSeconds(5);};
            try(var isolation=new KiteEquityReadIsolation(f.clock,Duration.ofSeconds(10),()->{if(fault.equals("unknown"))throw new IllegalStateException("privateFixture");return !fault.equals("witness");})){
                assertThatThrownBy(()->new KiteEquityReadHandoff(owner,new KiteEquityReadHandoff.Recipient(),f.session,f.halt,isolation,
                        f.clock,lifetime,f.wire,f.integrity)).isInstanceOf(RuntimeException.class).hasMessageNotContaining("privateFixture");
                assertThat(f.calls).hasValue(0);assertThat(f.wire.attempts()).isZero();
            }
        }
    }

}
