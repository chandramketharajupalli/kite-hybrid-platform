package com.kitehybrid.platform.order.domain.command;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public record PlaceOrder(String idempotencyKey, InstrumentId instrumentId, OrderSide side, long quantity,
                         OrderType orderType, OrderProduct product, OrderValidity validity,
                         Optional<BigDecimal> limitPrice, Optional<BigDecimal> triggerPrice,
                         long disclosedQuantity, OrderVariety variety) implements OrderCommand {
    public PlaceOrder {
        idempotencyKey = CommandText.key(idempotencyKey);
        Objects.requireNonNull(instrumentId); Objects.requireNonNull(side);
        Objects.requireNonNull(orderType); Objects.requireNonNull(product);
        Objects.requireNonNull(validity); Objects.requireNonNull(limitPrice);
        Objects.requireNonNull(triggerPrice); Objects.requireNonNull(variety);
        if (quantity <= 0 || disclosedQuantity < 0) throw new IllegalArgumentException("Invalid order quantity");
        limitPrice = limitPrice.map(CommandText::nonnegativePrice);
        triggerPrice = triggerPrice.map(CommandText::nonnegativePrice);
    }
}
