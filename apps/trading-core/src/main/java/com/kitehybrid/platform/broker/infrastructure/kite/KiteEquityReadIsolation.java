package com.kitehybrid.platform.broker.infrastructure.kite;

import java.time.*;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Synthetic witness lease. Does not attest host-wide or live broker isolation. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(
        fieldVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        getterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        isGetterVisibility=com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
final class KiteEquityReadIsolation implements AutoCloseable {
    private final Clock clock;
    private final Instant expires;
    private final BooleanSupplier witness;
    private Instant last;
    private boolean closed;

    KiteEquityReadIsolation(Clock clock, Duration lifetime, BooleanSupplier witness) {
        this.clock=Objects.requireNonNull(clock); this.witness=Objects.requireNonNull(witness);
        if (lifetime==null || lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofSeconds(30))>0)
            throw new IllegalArgumentException("ISOLATION_DENIED");
        last=clock.instant(); expires=last.plus(lifetime);
    }
    synchronized boolean valid() {
        try {
            var now=clock.instant();
            if (closed || now.isBefore(last) || !now.isBefore(expires) || !witness.getAsBoolean()) {
                closed=true; return false;
            }
            last=now; return true;
        } catch (RuntimeException unavailable) { closed=true; return false; }
    }
    @Override public synchronized void close() { closed=true; }
    @Override public String toString() { return "SyntheticIsolation[REDACTED]"; }
}
