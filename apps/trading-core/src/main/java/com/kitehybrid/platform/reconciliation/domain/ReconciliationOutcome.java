package com.kitehybrid.platform.reconciliation.domain;

public enum ReconciliationOutcome {
    IN_SYNC, ADVANCED, BROKER_REJECTED, BROKER_CANCELLED, PARTIALLY_FILLED,
    FILLED, AMBIGUOUS, CONFLICT, BROKER_ORDER_MISSING, BROKER_STATE_UNAVAILABLE
}
