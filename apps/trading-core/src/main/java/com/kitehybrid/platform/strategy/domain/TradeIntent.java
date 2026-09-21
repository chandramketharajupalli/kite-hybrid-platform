package com.kitehybrid.platform.strategy.domain;

import com.kitehybrid.platform.order.domain.Signal;
import com.kitehybrid.platform.order.domain.command.OrderType;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Broker-independent proposal. It is not an order and carries no broker identity. */
public record TradeIntent(OrderIntentId id, StrategyId strategyId, String strategyVersion,
                          InstrumentId instrumentId, Signal.Side side, int quantity, OrderType orderType,
                          Optional<BigDecimal> limitPrice, Instant generatedAt, SignalId sourceSignalId) {
    public TradeIntent {
        Objects.requireNonNull(id); Objects.requireNonNull(strategyId); Objects.requireNonNull(instrumentId);
        Objects.requireNonNull(side); Objects.requireNonNull(orderType); Objects.requireNonNull(limitPrice);
        Objects.requireNonNull(generatedAt); Objects.requireNonNull(sourceSignalId);
        if (strategyVersion == null || !strategyVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,31}")) throw new IllegalArgumentException("Invalid strategy version");
        if (side == Signal.Side.HOLD || quantity <= 0) throw new IllegalArgumentException("Non-actionable trade intent");
        if (orderType == OrderType.LIMIT && limitPrice.isEmpty()) throw new IllegalArgumentException("Limit price required");
        if (orderType != OrderType.LIMIT && limitPrice.isPresent()) throw new IllegalArgumentException("Only limit orders have a limit price");
    }
}
