package com.kitehybrid.platform.order.application;

import com.kitehybrid.platform.order.domain.OrderIntent;
import com.kitehybrid.platform.order.domain.OrderState;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;

/** Future PostgreSQL adapter: CAS state + event + dedup identity must commit atomically. */
public interface OrderRepository {
    boolean createIfAbsent(OrderId id, OrderIntent intent);
    boolean compareAndSetState(OrderId id, OrderState expected, OrderState next, long expectedVersion);
}
