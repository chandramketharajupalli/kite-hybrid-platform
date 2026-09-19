package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.order.domain.Signal;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class EmergencyStopRiskRule implements RiskRule {
    private final BooleanSupplier halted;
    public EmergencyStopRiskRule(BooleanSupplier halted) { this.halted = Objects.requireNonNull(halted); }
    @Override public Optional<String> rejection(Signal signal) {
        return halted.getAsBoolean() ? Optional.of("EMERGENCY_STOP") : Optional.empty();
    }
}
