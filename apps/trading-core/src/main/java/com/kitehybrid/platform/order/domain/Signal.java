package com.kitehybrid.platform.order.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Signal(SignalId id, StrategyId strategyId, InstrumentId instrumentId,
                     Side side, int quantity, BigDecimal referencePrice, Instant timestamp,
                     String strategyVersion, Reason reason) {
    public enum Side { BUY, SELL, HOLD }
    public enum Reason { THRESHOLD_CROSSED, BELOW_THRESHOLD, ABOVE_THRESHOLD, HOLD_THRESHOLD,
        MARKET_DATA_UNAVAILABLE, EMERGENCY_STOP }
    public Signal {
        Objects.requireNonNull(id);
        Objects.requireNonNull(strategyId);
        Objects.requireNonNull(instrumentId);
        Objects.requireNonNull(side);
        Objects.requireNonNull(referencePrice);
        Objects.requireNonNull(timestamp);
        if (quantity < 0 || (side != Side.HOLD && quantity == 0) || (side == Side.HOLD && quantity != 0))
            throw new IllegalArgumentException("Invalid signal quantity");
        if (referencePrice.signum() < 0) throw new IllegalArgumentException("Negative reference price");
        if (strategyVersion == null || !strategyVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,31}"))
            throw new IllegalArgumentException("Invalid strategy version");
        Objects.requireNonNull(reason);
    }
    public Signal(SignalId id, StrategyId strategyId, InstrumentId instrumentId, Side side,
                  int quantity, BigDecimal referencePrice, Instant timestamp) {
        this(id, strategyId, instrumentId, side, quantity, referencePrice, timestamp, "legacy-v1", Reason.THRESHOLD_CROSSED);
    }
}
