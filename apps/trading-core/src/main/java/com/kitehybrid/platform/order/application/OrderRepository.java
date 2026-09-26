package com.kitehybrid.platform.order.application;

import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.OrderState;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.util.Optional;

/** PostgreSQL-backed order and idempotency authority. */
public interface OrderRepository {
    enum IdempotencyClaim { CREATED, EXISTING, CONFLICT }
    IdempotencyClaim claimIdempotency(String key, String fingerprint, OrderId orderId);
    IdempotencyClaim createIfAbsent(OrderRecord record, String fingerprint);
    void create(OrderRecord record);
    Optional<OrderRecord> find(OrderId id);
    Optional<OrderRecord> findByIdempotencyKey(String key);
    boolean compareAndSet(OrderRecord expected, OrderRecord next);
    boolean attachBrokerOrderId(OrderRecord expected, OrderRecord next);
    default boolean hasDangerousUnresolvedOrders() { return false; }
}
