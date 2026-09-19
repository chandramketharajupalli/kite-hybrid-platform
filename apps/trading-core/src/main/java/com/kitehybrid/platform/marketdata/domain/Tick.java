package com.kitehybrid.platform.marketdata.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Internal model, independent of wire encoding and broker SDK. */
public record Tick(InstrumentId instrumentId, BigDecimal lastPrice, Instant observedAt) {
    public Tick {
        Objects.requireNonNull(instrumentId);
        Objects.requireNonNull(lastPrice);
        Objects.requireNonNull(observedAt);
        if (lastPrice.signum() < 0) throw new IllegalArgumentException("Negative price");
    }
}
