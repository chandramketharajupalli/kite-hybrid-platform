package com.kitehybrid.platform.order.domain.command;

import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.util.Objects;

public record CancelOrder(String idempotencyKey, OrderId orderId) implements OrderCommand {
    public CancelOrder {
        idempotencyKey = CommandText.key(idempotencyKey); Objects.requireNonNull(orderId);
    }
}
