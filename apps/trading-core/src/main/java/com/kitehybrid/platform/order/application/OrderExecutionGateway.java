package com.kitehybrid.platform.order.application;

import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.command.CancelOrder;
import com.kitehybrid.platform.order.domain.command.ModifyOrder;

/** Broker-independent execution boundary. Implementations must never retry an ambiguous request. */
public interface OrderExecutionGateway {
    default String place(OrderRecord order) { throw new OrderExecutionException(OrderExecutionException.Category.DISABLED); }
    default String place(OrderRecord order, Runnable dispatchValidation) { throw new OrderExecutionException(OrderExecutionException.Category.DISABLED); }
    void modify(OrderRecord order, ModifyOrder command);
    void cancel(OrderRecord order, CancelOrder command);
}
