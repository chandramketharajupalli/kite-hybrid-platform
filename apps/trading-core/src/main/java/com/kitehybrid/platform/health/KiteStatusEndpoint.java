package com.kitehybrid.platform.health;

import com.kitehybrid.platform.broker.infrastructure.kite.KiteSession;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/** Passive local state only; not a health contributor, active probe or trading authorization. */
@Component
@Endpoint(id = "kitestatus")
public final class KiteStatusEndpoint {
    private final KiteSession session;
    private final InstrumentRegistry registry;
    public KiteStatusEndpoint(KiteSession session, InstrumentRegistry registry) {
        this.session = session;
        this.registry = registry;
    }
    @ReadOperation public Map<String, Object> status() {
        var snapshot = registry.snapshot();
        return Map.of("sessionState", session.state().name(), "instrumentCount", snapshot.size(),
                "snapshotVersion", snapshot.version(), "snapshotTimestamp", snapshot.refreshedAt().toString(),
                "tradingReady", false);
    }
}
