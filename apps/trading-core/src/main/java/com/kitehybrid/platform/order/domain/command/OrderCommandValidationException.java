package com.kitehybrid.platform.order.domain.command;

public final class OrderCommandValidationException extends RuntimeException {
    public OrderCommandValidationException(String reason) { super(reason, null, false, true); }
}
