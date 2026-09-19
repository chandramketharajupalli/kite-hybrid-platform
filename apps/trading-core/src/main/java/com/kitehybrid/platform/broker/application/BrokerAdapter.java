package com.kitehybrid.platform.broker.application;

import com.kitehybrid.platform.order.domain.OrderIntent;

/**
 * Java-only execution boundary. No implementation or runtime wiring exists in Phase 1.
 * Future adapters must map ambiguous outcomes to reconciliation, never blind retry.
 */
public interface BrokerAdapter {
    void submit(OrderIntent intent);
}
