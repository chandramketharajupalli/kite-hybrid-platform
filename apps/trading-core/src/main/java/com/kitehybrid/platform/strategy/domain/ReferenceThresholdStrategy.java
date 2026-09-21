package com.kitehybrid.platform.strategy.domain;

import com.kitehybrid.platform.order.domain.Signal;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import com.kitehybrid.platform.strategy.application.Strategy;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** REFERENCE/TEST STRATEGY ONLY. It is not investment logic or production strategy code. */
public final class ReferenceThresholdStrategy implements Strategy {
    private final StrategyDefinition definition;
    private final InstrumentId instrument;
    private final BigDecimal threshold;
    private final int quantity;
    public ReferenceThresholdStrategy(StrategyDefinition definition, InstrumentId instrument,
                                      BigDecimal threshold, int quantity) {
        this.definition = Objects.requireNonNull(definition); this.instrument = Objects.requireNonNull(instrument);
        this.threshold = Objects.requireNonNull(threshold); this.quantity = quantity;
        if (threshold.signum() <= 0 || quantity <= 0) throw new IllegalArgumentException("Invalid reference strategy parameters");
    }
    @Override public StrategyDefinition definition() { return definition; }
    @Override public Signal evaluate(StrategyInput input) {
        if (!instrument.equals(input.instrumentId())) throw new IllegalArgumentException("Unexpected strategy instrument");
        var tick = input.latestTick();
        if (!input.fresh()) return signal(Signal.Side.HOLD, 0, tick.map(t -> t.lastPrice()).orElse(BigDecimal.ZERO), input, Signal.Reason.MARKET_DATA_UNAVAILABLE);
        var price = tick.orElseThrow().lastPrice();
        int compare = price.compareTo(threshold);
        if (compare < 0) return signal(Signal.Side.BUY, quantity, price, input, Signal.Reason.BELOW_THRESHOLD);
        if (compare > 0) return signal(Signal.Side.SELL, quantity, price, input, Signal.Reason.ABOVE_THRESHOLD);
        return signal(Signal.Side.HOLD, 0, price, input, Signal.Reason.HOLD_THRESHOLD);
    }
    private Signal signal(Signal.Side side, int units, BigDecimal price, StrategyInput input, Signal.Reason reason) {
        return new Signal(new SignalId(UUID.nameUUIDFromBytes((definition.id().value() + ":" + definition.version() + ":" + input.evaluatedAt() + ":" + instrument.value()).getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                definition.id(), instrument, side, units, price, input.evaluatedAt(), definition.version(), reason);
    }
}
