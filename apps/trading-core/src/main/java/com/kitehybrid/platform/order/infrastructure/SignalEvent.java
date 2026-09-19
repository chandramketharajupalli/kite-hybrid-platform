package com.kitehybrid.platform.order.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kitehybrid.platform.order.domain.Signal;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire DTO. A future ingress adapter MUST validate JSON Schema before binding.
 * Serialization types belong here, not in the domain. No ingress exists in Phase 1.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignalEvent(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("event_timestamp") Instant eventTimestamp,
        @JsonProperty("correlation_id") String correlationId,
        Payload payload) {
    public record Payload(
            @JsonProperty("signal_id") String signalId,
            @JsonProperty("strategy_id") String strategyId,
            @JsonProperty("instrument_id") String instrumentId,
            Signal.Side side,
            int quantity,
            @JsonProperty("reference_price") String referencePrice) {}

    public Signal toDomain() {
        return new Signal(new SignalId(UUID.fromString(payload.signalId())),
                new StrategyId(payload.strategyId()),
                new InstrumentId(UUID.fromString(payload.instrumentId())),
                payload.side(), payload.quantity(), new BigDecimal(payload.referencePrice()), eventTimestamp);
    }
}
