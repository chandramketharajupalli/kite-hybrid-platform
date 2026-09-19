package com.kitehybrid.platform.instrument.domain;

/** Broker-scoped mapping, never a platform instrument identity. */
public record BrokerInstrumentId(String broker, String value) {
    public BrokerInstrumentId {
        if (broker == null || broker.isBlank() || value == null || value.isBlank())
            throw new IllegalArgumentException("Broker and broker instrument ID required");
    }
}
