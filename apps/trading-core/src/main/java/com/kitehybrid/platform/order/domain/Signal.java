package com.kitehybrid.platform.order.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Signal(SignalId id, StrategyId strategyId, InstrumentId instrumentId,
                     Side side, int quantity, BigDecimal referencePrice, Instant timestamp) {
    public enum Side { BUY, SELL }
    public Signal {
        Objects.requireNonNull(id);
        Objects.requireNonNull(strategyId);
        Objects.requireNonNull(instrumentId);
        Objects.requireNonNull(side);
        Objects.requireNonNull(referencePrice);
        Objects.requireNonNull(timestamp);
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be whole positive units");
        if (referencePrice.signum() < 0) throw new IllegalArgumentException("Negative reference price");
    }
}
