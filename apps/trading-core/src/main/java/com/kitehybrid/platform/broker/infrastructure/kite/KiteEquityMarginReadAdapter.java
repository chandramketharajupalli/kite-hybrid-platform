package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.domain.read.BrokerMargins;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;

/** Unwired, opt-in, one GET per invocation. No credential lifecycle, repository or execution capability. */
final class KiteEquityMarginReadAdapter {
    enum Provenance { SYNTHETIC_TRANSPORT, BROKER_AUTHENTICATED_OBSERVATION }
    record Observation(Provenance provenance, Instant receivedAt) {}

    /** Internal normalized values only. No public serializer accessors or raw-value toString. */
    static final class Snapshot {
        private final BrokerMargins.SegmentMargin equity;
        private final Instant receivedAt;
        private final UUID sessionIdentity;
        private final Object owner;
        private final Provenance provenance;
        private Snapshot(BrokerMargins.SegmentMargin equity, Instant receivedAt, UUID sessionIdentity,
                         Object owner, Provenance provenance) {
            this.equity = equity; this.receivedAt = receivedAt; this.sessionIdentity = sessionIdentity;
            this.owner = owner; this.provenance = provenance;
        }
        BrokerMargins.SegmentMargin equity() { return equity; }
        Instant receivedAt() { return receivedAt; }
        @Override public String toString() { return "EquityMarginSnapshot[REDACTED]"; }
    }

    private final KiteRestTransport transport;
    private final KiteSession session;
    private final Clock clock;
    private final boolean enabled;
    private final Object owner = new Object();

    KiteEquityMarginReadAdapter(KiteRestTransport transport, KiteSession session, Clock clock) {
        this(transport, session, clock, false);
    }
    KiteEquityMarginReadAdapter(KiteRestTransport transport, KiteSession session, Clock clock, boolean enabled) {
        this.transport = Objects.requireNonNull(transport);
        this.session = Objects.requireNonNull(session);
        this.clock = Objects.requireNonNull(clock);
        this.enabled = enabled;
        if (!transport.usesSession(session)) throw new BrokerReadException(CONFIGURATION);
    }

    Snapshot read() {
        requireEnabled();
        synchronized (session) {
            var identity = identity();
            var started = clock.instant();
            var equity = KiteTradingReadMapper.equityMargin(transport.get(KiteRestTransport.Endpoint.EQUITY_MARGINS));
            if (!identity.equals(identity())) throw new BrokerReadException(AUTHENTICATION);
            var received = clock.instant();
            if (received.isBefore(started)) throw new BrokerReadException(INVALID_RESPONSE);
            return new Snapshot(equity, received, identity, owner, transport.officialOrigin()
                    ? Provenance.BROKER_AUTHENTICATED_OBSERVATION : Provenance.SYNTHETIC_TRANSPORT);
        }
    }

    /** Redacted receipt after owner/session validation; no funding/risk dependency. */
    Observation observation(Snapshot snapshot) {
        requireEnabled();
        synchronized (session) {
            var identity = identity();
            if (snapshot == null || snapshot.owner != owner || !snapshot.sessionIdentity.equals(identity))
                throw new BrokerReadException(AUTHENTICATION);
            return new Observation(snapshot.provenance, snapshot.receivedAt);
        }
    }

    private UUID identity() {
        if (!session.authenticated()) throw new BrokerReadException(AUTHENTICATION);
        return session.executionIdentity().orElseThrow(() -> new BrokerReadException(AUTHENTICATION));
    }
    private void requireEnabled() { if (!enabled) throw new BrokerReadException(CONFIGURATION); }
}
