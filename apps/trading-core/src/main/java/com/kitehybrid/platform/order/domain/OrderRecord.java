package com.kitehybrid.platform.order.domain;

import com.kitehybrid.platform.order.domain.command.PlaceOrder;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Durable platform identity and lifecycle record. The broker order ID is only an attached reference. */
public record OrderRecord(OrderId id, PlaceOrder command, OrderState state, Optional<String> brokerOrderId,
                          Optional<String> failureCategory, Instant createdAt, Instant updatedAt, long version) {
    public OrderRecord {
        Objects.requireNonNull(id); Objects.requireNonNull(command); Objects.requireNonNull(state);
        Objects.requireNonNull(brokerOrderId); Objects.requireNonNull(failureCategory);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        if (version < 0 || updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("Invalid order record");
    }
    public OrderRecord transitionTo(OrderState next, Instant now) {
        state.transitionTo(next);
        return new OrderRecord(id, command, next, brokerOrderId, failureCategory, createdAt, now, version + 1);
    }
    public OrderRecord withBrokerOrderId(String brokerId, Instant now) {
        if (brokerId == null || !brokerId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))
            throw new IllegalArgumentException("Invalid broker order reference");
        return new OrderRecord(id, command, state, Optional.of(brokerId), failureCategory, createdAt, now, version + 1);
    }
    public OrderRecord failed(String category, Instant now) {
        state.transitionTo(OrderState.FAILED);
        return new OrderRecord(id, command, OrderState.FAILED, brokerOrderId,
                Optional.of(Objects.requireNonNull(category)), createdAt, now, version + 1);
    }
}
