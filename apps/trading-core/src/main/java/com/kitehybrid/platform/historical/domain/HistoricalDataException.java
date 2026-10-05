package com.kitehybrid.platform.historical.domain;

/** Bounded categories only; upstream text, URLs and causes are deliberately excluded. */
public final class HistoricalDataException extends RuntimeException {
    public enum Reason { AUTHENTICATION, RATE_LIMITED, TRANSIENT_PROVIDER, INVALID_RESPONSE,
        INVALID_BAR, CONFLICT, REFERENCE_UNAVAILABLE, INVALID_REQUEST, STORAGE_UNAVAILABLE,
        TIMESTAMP_SEMANTICS_UNVERIFIED }
    private final Reason reason;
    public HistoricalDataException(Reason reason) {
        super(reason.name(), null, false, true); this.reason = reason;
    }
    public Reason reason() { return reason; }
}
