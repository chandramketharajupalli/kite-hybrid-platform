package com.kitehybrid.platform.strategy.domain;

import com.kitehybrid.platform.marketdata.application.MarketDataHealth;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record StrategyInput(InstrumentId instrumentId, Optional<Tick> latestTick,
                            MarketDataHealth health, Instant evaluatedAt) {
    public StrategyInput {
        Objects.requireNonNull(instrumentId); Objects.requireNonNull(latestTick);
        Objects.requireNonNull(health); Objects.requireNonNull(evaluatedAt);
    }
    public boolean fresh() {
        return health.status() == MarketDataHealth.Status.FRESH && health.reason() == MarketDataHealth.Reason.NONE
                && latestTick.isPresent();
    }
}
