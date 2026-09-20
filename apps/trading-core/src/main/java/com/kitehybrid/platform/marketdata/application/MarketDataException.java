package com.kitehybrid.platform.marketdata.application;

/** Bounded, credential-free application errors. Subscription changes are atomic on validation failure. */
public final class MarketDataException extends IllegalArgumentException {
    public enum Reason { UNRESOLVED_INSTRUMENT, INVALID_BROKER_MAPPING, SUBSCRIPTION_LIMIT, CLOSED }
    private final Reason reason;
    public MarketDataException(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason reason() { return reason; }
}
