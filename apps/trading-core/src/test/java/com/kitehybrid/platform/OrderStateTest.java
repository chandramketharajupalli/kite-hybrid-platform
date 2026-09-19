package com.kitehybrid.platform;

import com.kitehybrid.platform.order.domain.OrderState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.kitehybrid.platform.order.domain.OrderState.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderStateTest {
    @Test void followsLifecycleAndHandlesFillDuringCancellation() {
        var state = CREATED.transitionTo(VALIDATED).transitionTo(RISK_APPROVED)
                .transitionTo(SUBMITTING).transitionTo(SUBMITTED).transitionTo(ACKNOWLEDGED)
                .transitionTo(OPEN).transitionTo(PARTIALLY_FILLED).transitionTo(CANCEL_PENDING)
                .transitionTo(FILLED);
        assertEquals(FILLED, state);
    }
    @Test void cancellationAndRejectionAreExplicit() {
        assertEquals(CANCELLED, OPEN.transitionTo(CANCEL_PENDING).transitionTo(CANCELLED));
        assertEquals(REJECTED, VALIDATED.transitionTo(REJECTED));
        assertEquals(FAILED, SUBMITTING.transitionTo(FAILED));
        assertEquals(OPEN, CANCEL_PENDING.transitionTo(OPEN));
    }
    @Test void cannotSkipRiskOrSubmissionAndCannotRewind() {
        assertThrows(IllegalStateException.class, () -> CREATED.transitionTo(SUBMITTING));
        assertThrows(IllegalStateException.class, () -> VALIDATED.transitionTo(SUBMITTED));
        assertThrows(IllegalStateException.class, () -> OPEN.transitionTo(CREATED));
        assertThrows(IllegalArgumentException.class, () -> CREATED.transitionTo(null));
    }
    @ParameterizedTest @EnumSource(OrderState.class)
    void duplicateStateNotificationsAreIdempotent(OrderState state) {
        assertSame(state, state.transitionTo(state));
    }
    @ParameterizedTest @EnumSource(value = OrderState.class, names = {"FILLED", "CANCELLED", "REJECTED", "FAILED"})
    void terminalsCannotTransitionToAnyOtherState(OrderState terminal) {
        assertTrue(terminal.terminal());
        for (var other : OrderState.values()) {
            if (other != terminal) assertThrows(IllegalStateException.class, () -> terminal.transitionTo(other));
        }
    }
}
