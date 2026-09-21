package com.kitehybrid.platform.order.application;

public final class OrderExecutionException extends RuntimeException {
    public enum Category { BROKER_REJECTED, AUTHENTICATION, TRANSPORT, MALFORMED_RESPONSE, AMBIGUOUS, DISABLED }
    private final Category category;
    public OrderExecutionException(Category category) {
        super("Order execution failed: " + category.name(), null, false, true);
        this.category = category;
    }
    public Category category() { return category; }
}
