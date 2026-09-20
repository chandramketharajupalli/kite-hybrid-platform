package com.kitehybrid.platform.marketdata.application;

import java.time.Instant;
import java.util.Optional;

/** Passive operational state, never trading authorization. Fresh means every desired instrument is fresh. */
public record MarketDataHealth(MarketDataGateway.State connectionState, Status status, Reason reason,
        Optional<Instant> connectedAt, Optional<Instant> lastMessageAt, Optional<Instant> lastTickAt,
        int desiredSubscriptions, int activeSubscriptions, int reconnectAttempts,
        long framesReceived, long ticksReceived, long decodeFailures, long eventsDropped,
        long ignoredTextMessages, int queuedEvents) {
    public enum Status { STOPPED, STARTING, NO_DATA, FRESH, STALE, RECONNECTING, DEGRADED, STOPPING }
    public enum Reason {
        NONE, DISABLED, AUTH_REQUIRED, SESSION_CHANGED, CONNECTION_FAILED, RECONNECT_EXHAUSTED,
        BACKPRESSURE, MALFORMED_DATA, BROKER_ERROR, PROCESSING_FAILURE, INSTRUMENT_REGISTRY_CHANGED,
        UNRESOLVED_INSTRUMENT, INVALID_BROKER_MAPPING
    }
}
