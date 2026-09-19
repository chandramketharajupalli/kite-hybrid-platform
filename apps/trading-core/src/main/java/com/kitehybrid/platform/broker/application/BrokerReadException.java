package com.kitehybrid.platform.broker.application;

/** Safe boundary failure: never carries upstream text, payloads, headers or causes. */
public final class BrokerReadException extends RuntimeException {
    public enum Category { CONFIGURATION, AUTHENTICATION, BROKER_API, TRANSPORT, INVALID_RESPONSE }
    private final Category category;
    private final int httpStatus;
    public BrokerReadException(Category category) { this(category, 0); }
    public BrokerReadException(Category category, int httpStatus) {
        super("Broker read failed: " + category.name(), null, false, true);
        this.category = category;
        this.httpStatus = httpStatus;
    }
    public Category category() { return category; }
    public int httpStatus() { return httpStatus; }
}
