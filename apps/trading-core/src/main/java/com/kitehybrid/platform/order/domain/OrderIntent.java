package com.kitehybrid.platform.order.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.OrderIntentId;
import java.time.Instant;
import java.util.Objects;

/** An intent is not an execution authorization; only Java's future OMS may act on it. */
public record OrderIntent(OrderIntentId id, Signal signal, Instant createdAt) {
    public OrderIntent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(signal);
        Objects.requireNonNull(createdAt);
    }
}
