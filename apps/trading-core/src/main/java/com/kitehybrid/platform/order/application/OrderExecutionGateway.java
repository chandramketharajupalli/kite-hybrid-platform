package com.kitehybrid.platform.order.application;

import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.command.CancelOrder;
import com.kitehybrid.platform.order.domain.command.ModifyOrder;

/** Broker-independent execution boundary. Implementations must never retry an ambiguous request. */
public interface OrderExecutionGateway {
    String place(OrderRecord order);
    void modify(OrderRecord order, ModifyOrder command);
    void cancel(OrderRecord order, CancelOrder command);
}
