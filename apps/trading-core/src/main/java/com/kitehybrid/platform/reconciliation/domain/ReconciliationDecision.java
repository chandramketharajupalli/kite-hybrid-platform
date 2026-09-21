package com.kitehybrid.platform.reconciliation.domain;

import com.kitehybrid.platform.order.domain.OrderState;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ReconciliationDecision(UUID reconciliationId, OrderId orderId, OrderState stateBefore,
                                     Optional<OrderState> stateAfter, ReconciliationOutcome outcome,
                                     ReconciliationReason reason, Instant observedAt, long localVersion) {
    public ReconciliationDecision {
        Objects.requireNonNull(reconciliationId); Objects.requireNonNull(orderId);
        Objects.requireNonNull(stateBefore); Objects.requireNonNull(stateAfter);
        Objects.requireNonNull(outcome); Objects.requireNonNull(reason); Objects.requireNonNull(observedAt);
        if (localVersion < 0) throw new IllegalArgumentException("Invalid local version");
    }
}
