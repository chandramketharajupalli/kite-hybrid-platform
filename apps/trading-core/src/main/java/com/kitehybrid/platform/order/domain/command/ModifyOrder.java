package com.kitehybrid.platform.order.domain.command;

import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public record ModifyOrder(String idempotencyKey, OrderId orderId, OrderType orderType, long quantity,
                          Optional<BigDecimal> limitPrice, Optional<BigDecimal> triggerPrice,
                          long disclosedQuantity, OrderValidity validity) implements OrderCommand {
    public ModifyOrder {
        idempotencyKey = CommandText.key(idempotencyKey); Objects.requireNonNull(orderId);
        Objects.requireNonNull(orderType); Objects.requireNonNull(limitPrice);
        Objects.requireNonNull(triggerPrice); Objects.requireNonNull(validity);
        if (quantity <= 0 || disclosedQuantity < 0) throw new IllegalArgumentException("Invalid order quantity");
        limitPrice = limitPrice.map(CommandText::nonnegativePrice);
        triggerPrice = triggerPrice.map(CommandText::nonnegativePrice);
    }
}
