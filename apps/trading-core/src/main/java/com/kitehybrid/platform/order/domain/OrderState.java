package com.kitehybrid.platform.order.domain;

public enum OrderState {
    CREATED, VALIDATED, RISK_APPROVED, SUBMITTING, SUBMITTED, ACKNOWLEDGED,
    OPEN, PARTIALLY_FILLED, FILLED, CANCEL_PENDING, CANCELLED, REJECTED, FAILED;

    public boolean terminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED || this == FAILED;
    }

    /** Same-state notifications are no-ops. Fill deduplication needs fill/event identity later. */
    public OrderState transitionTo(OrderState target) {
        if (target == null) throw new IllegalArgumentException("Target state required");
        if (this == target) return this;
        boolean allowed = switch (this) {
            case CREATED -> target == VALIDATED || target == REJECTED;
            case VALIDATED -> target == RISK_APPROVED || target == REJECTED;
            case RISK_APPROVED -> target == SUBMITTING;
            case SUBMITTING -> target == SUBMITTED || target == REJECTED || target == FAILED;
            case SUBMITTED -> target == ACKNOWLEDGED || target == OPEN || target == PARTIALLY_FILLED
                    || target == FILLED || target == CANCEL_PENDING || target == REJECTED;
            case ACKNOWLEDGED -> target == OPEN || target == PARTIALLY_FILLED
                    || target == FILLED || target == CANCEL_PENDING || target == REJECTED;
            case OPEN -> target == PARTIALLY_FILLED || target == FILLED
                    || target == CANCEL_PENDING || target == CANCELLED || target == REJECTED;
            case PARTIALLY_FILLED -> target == FILLED || target == CANCEL_PENDING || target == CANCELLED;
            // A fill may race with cancellation. Cancellation rejection returns to an active state.
            case CANCEL_PENDING -> target == CANCELLED || target == PARTIALLY_FILLED
                    || target == FILLED || target == OPEN;
            case FILLED, CANCELLED, REJECTED, FAILED -> false;
        };
        if (!allowed) throw new IllegalStateException("Forbidden transition: " + this + " -> " + target);
        return target;
    }
}
