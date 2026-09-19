package com.kitehybrid.platform.instrument.domain;

import java.util.Locale;

/** Broker-scoped mapping, never a platform instrument identity. */
public record BrokerInstrumentId(String broker, String value) {
    public BrokerInstrumentId {
        if (broker == null || value == null)
            throw new IllegalArgumentException("Broker and broker instrument ID required");
        if (broker.chars().anyMatch(Character::isISOControl)
                || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Broker instrument mapping contains control characters");
        broker = broker.trim().toUpperCase(Locale.ROOT);
        value = value.trim();
        if (!broker.matches("[A-Z][A-Z0-9_-]{0,31}")
                || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))
            throw new IllegalArgumentException("Invalid broker instrument mapping");
    }
}
