package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;
import java.time.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Internal single-use composition. No startup/auth lifecycle, order service or credential input. */
final class KiteEquityReadHarness implements AutoCloseable {
    enum Mode { DISABLED, SYNTHETIC, SEPARATELY_APPROVED_REAL }
    enum Outcome { OBSERVED, DISABLED, ALREADY_USED, PRECONDITION_DENIED, AUTHENTICATION,
        BROKER_API, TRANSPORT, INVALID_RESPONSE, STATE_CHANGED }
    record Result(Outcome outcome, int requestAttempts, boolean statePreserved,
                  KiteEquityMarginReadAdapter.Observation observation) {}
    private final KiteEquityReadRequestFactory wire;
    private final KiteEquityMarginReadAdapter adapter;
    private final KiteSession session;
    private final KiteEquityReadIntegrity integrity;
    private final RuntimeTradingHalt halt;
    private final Clock clock;
    private final Mode mode;
    private final Runnable guard;
    private final AtomicBoolean used = new AtomicBoolean();

    KiteEquityReadHarness(KiteEquityReadRequestFactory wire, KiteSession session, KiteEquityReadIntegrity integrity,
                         RuntimeTradingHalt halt, Clock clock) {
        this(wire,session,integrity,halt,clock,Mode.DISABLED);
    }
    KiteEquityReadHarness(KiteEquityReadRequestFactory wire, KiteSession session, KiteEquityReadIntegrity integrity,
                         RuntimeTradingHalt halt, Clock clock, Mode mode) {
        this(wire,session,integrity,halt,clock,mode,()->{});
    }
    KiteEquityReadHarness(KiteEquityReadRequestFactory wire, KiteSession session, KiteEquityReadIntegrity integrity,
                         RuntimeTradingHalt halt, Clock clock, Mode mode, Runnable guard) {
        this.wire=Objects.requireNonNull(wire); this.session=Objects.requireNonNull(session);
        this.integrity=Objects.requireNonNull(integrity); this.halt=Objects.requireNonNull(halt);
        this.clock=Objects.requireNonNull(clock); this.mode=Objects.requireNonNull(mode);
        this.guard=Objects.requireNonNull(guard);
        adapter=new KiteEquityMarginReadAdapter(KiteRestTransport.controlledEquity(session,wire),session,clock,true);
    }
    Result run() {
        if (mode==Mode.DISABLED) return result(Outcome.DISABLED,false,null);
        if (!used.compareAndSet(false,true)) return result(Outcome.ALREADY_USED,false,null);
        if (wire.officialOrigin() != (mode==Mode.SEPARATELY_APPROVED_REAL)) return result(Outcome.PRECONDITION_DENIED,false,null);
        var epoch=halt.epoch();
        KiteEquityReadIntegrity.Fingerprint before;
        java.util.UUID identity;
        try {
            guard.run();
            requireHalt(epoch);
            if (org.slf4j.LoggerFactory.getLogger("org.apache.hc.client5.http.wire").isDebugEnabled()
                    || org.slf4j.LoggerFactory.getLogger("org.apache.hc.client5.http.headers").isDebugEnabled())
                return result(Outcome.PRECONDITION_DENIED,false,null);
            if (!session.authenticated()) return result(Outcome.AUTHENTICATION,false,null);
            identity=session.executionIdentity().orElseThrow();
            before=integrity.capture();
            guard.run();
            requireHalt(epoch);
            if (!session.authenticated() || !session.executionIdentity().orElseThrow().equals(identity))
                return result(Outcome.AUTHENTICATION,false,null);
        } catch (RuntimeException unavailable) { return result(Outcome.PRECONDITION_DENIED,false,null); }
        Outcome outcome=Outcome.OBSERVED;
        KiteEquityMarginReadAdapter.Observation observation=null;
        try {
            var snapshot=adapter.read(); observation=adapter.observation(snapshot);
            var now=clock.instant();
            if (observation.receivedAt().isAfter(now) || Duration.between(observation.receivedAt(),now).compareTo(Duration.ofSeconds(5))>=0)
                outcome=Outcome.INVALID_RESPONSE;
            if (!session.executionIdentity().orElseThrow().equals(identity)) outcome=Outcome.AUTHENTICATION;
        } catch (BrokerReadException failure) {
            outcome=switch(failure.category()) {
                case AUTHENTICATION -> Outcome.AUTHENTICATION;
                case BROKER_API -> Outcome.BROKER_API;
                case TRANSPORT -> Outcome.TRANSPORT;
                default -> Outcome.INVALID_RESPONSE;
            };
        } catch (RuntimeException failure) { outcome=Outcome.INVALID_RESPONSE; }
        try {
            if (!before.matches(integrity.capture())) return result(Outcome.STATE_CHANGED,false,null);
            guard.run();
            requireHalt(epoch);
            if (session.executionIdentity().isPresent() && !session.executionIdentity().orElseThrow().equals(identity))
                outcome=Outcome.AUTHENTICATION;
            // Authentication rejection may invalidate memory; only successful observations require live binding.
            if (outcome==Outcome.OBSERVED && (!session.authenticated() || !session.executionIdentity().orElseThrow().equals(identity)))
                outcome=Outcome.AUTHENTICATION;
            if (outcome==Outcome.OBSERVED && (observation.receivedAt().isAfter(clock.instant())
                    || Duration.between(observation.receivedAt(),clock.instant()).compareTo(Duration.ofSeconds(5))>=0))
                outcome=Outcome.INVALID_RESPONSE;
            return result(outcome,true,outcome==Outcome.OBSERVED?observation:null);
        } catch (RuntimeException changed) { return result(Outcome.STATE_CHANGED,false,null); }
    }
    private void requireHalt(RuntimeTradingHalt.Epoch epoch) {
        if (!halt.knownHaltedAt(epoch)) throw new IllegalStateException();
    }
    private Result result(Outcome outcome,boolean preserved,KiteEquityMarginReadAdapter.Observation observation) {
        return new Result(outcome,wire.attempts(),preserved,observation);
    }
    @Override public void close() { used.set(true); wire.close(); }
}
