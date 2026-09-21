package com.kitehybrid.platform.order.infrastructure;

import com.kitehybrid.platform.order.application.OrderExecutionException;
import com.kitehybrid.platform.order.application.OrderExecutionGateway;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.command.CancelOrder;
import com.kitehybrid.platform.order.domain.command.ModifyOrder;

/** Default safety gate. It contains no HTTP client and cannot reach a broker. */
public final class DisabledOrderExecutionGateway implements OrderExecutionGateway {
    private static OrderExecutionException disabled() {
        return new OrderExecutionException(OrderExecutionException.Category.DISABLED);
    }
    @Override public String place(OrderRecord order) { throw disabled(); }
    @Override public void modify(OrderRecord order, ModifyOrder command) { throw disabled(); }
    @Override public void cancel(OrderRecord order, CancelOrder command) { throw disabled(); }
}
