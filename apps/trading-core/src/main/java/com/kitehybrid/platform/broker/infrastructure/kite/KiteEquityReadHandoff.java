package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Synthetic-only object capability. No credential export, serialization, IPC or real activation. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(
        fieldVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        getterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
final class KiteEquityReadHandoff implements AutoCloseable {
    static final class Owner implements AutoCloseable {
        private final AtomicBoolean alive=new AtomicBoolean(true);
        @Override public void close() { alive.set(false); }
        @Override public String toString() { return "HandoffOwner[REDACTED]"; }
    }
    static final class Recipient {
        @Override public String toString() { return "HandoffRecipient[REDACTED]"; }
    }
    private final UUID opaqueId=UUID.randomUUID(); // Never used as bearer authentication or exposed.
    private final Owner owner;
    private final Recipient recipient;
    private final KiteSession session;
    private final UUID identity;
    private final RuntimeTradingHalt halt;
    private final RuntimeTradingHalt.Epoch epoch;
    private final KiteEquityReadIsolation isolation;
    private final Clock clock;
    private final Instant expires;
    private Instant last;
    private final AtomicBoolean consumed=new AtomicBoolean();
    private boolean revoked;
    private final KiteEquityReadHarness harness;

    KiteEquityReadHandoff(Owner owner, Recipient recipient, KiteSession session,
                         RuntimeTradingHalt halt, KiteEquityReadIsolation isolation,
                         Clock clock, Duration lifetime, KiteEquityReadRequestFactory wire,
                         KiteEquityReadIntegrity integrity) {
        this.owner=Objects.requireNonNull(owner); this.recipient=Objects.requireNonNull(recipient);
        this.session=Objects.requireNonNull(session); this.halt=Objects.requireNonNull(halt);
        this.isolation=Objects.requireNonNull(isolation); this.clock=Objects.requireNonNull(clock);
        if (wire.officialOrigin() || lifetime==null || lifetime.isNegative() || lifetime.isZero()
                || lifetime.compareTo(Duration.ofSeconds(30))>0) throw denied();
        identity=session.executionIdentity().orElseThrow(KiteEquityReadHandoff::denied);
        epoch=halt.epoch(); last=clock.instant(); expires=last.plus(lifetime);
        requireValid();
        wire.bindSyntheticGuard(this::requireValid);
        harness=new KiteEquityReadHarness(wire,session,integrity,halt,clock,
                KiteEquityReadHarness.Mode.SYNTHETIC,this::requireValid);
    }
    synchronized boolean inspect(Owner source, Recipient target) {
        if (source!=owner || target!=recipient || consumed.get()) { revoked=true; return false; }
        try { requireValid(); return true; } catch (RuntimeException unavailable) { return false; }
    }
    KiteEquityReadHarness.Result consume(Owner source, Recipient target, String method, String path) {
        if (!consumed.compareAndSet(false,true)) throw denied();
        synchronized(this) {
            if (source!=owner || target!=recipient || !"GET".equals(method)
                    || !KiteEquityReadRequestFactory.PATH.equals(path)) { revoked=true; throw denied(); }
            requireValid();
        }
        try { return harness.run(); } finally { close(); }
    }
    private synchronized void requireValid() {
        try {
            var now=clock.instant();
            if (revoked || !owner.alive.get() || now.isBefore(last) || !now.isBefore(expires)
                    || !isolation.valid() || !session.authenticated()
                    || !session.executionIdentity().orElseThrow().equals(identity)
                    || !halt.knownHaltedAt(epoch)) throw denied();
            last=now;
        } catch (RuntimeException unavailable) { revoked=true; throw denied(); }
    }
    @Override public synchronized void close() { revoked=true; harness.close(); }
    @Override public String toString() { return "SyntheticEquityHandoff[REDACTED]"; }
    private static BrokerReadException denied() {
        return new BrokerReadException(BrokerReadException.Category.CONFIGURATION);
    }
}
