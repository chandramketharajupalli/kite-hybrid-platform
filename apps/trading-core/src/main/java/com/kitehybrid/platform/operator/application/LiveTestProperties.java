package com.kitehybrid.platform.operator.application;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

/** Independent, additive restrictions. Empty/zero defaults deny; no instrument is selected. */
public record LiveTestProperties(boolean enabled, Set<InstrumentId> allowedInstruments,
        long maxQuantity, BigDecimal maxNotional, Duration armMaxDuration) {
    public LiveTestProperties {
        allowedInstruments = Set.copyOf(allowedInstruments == null ? Set.of() : allowedInstruments);
        if (allowedInstruments.size() > 5 || maxQuantity < 0 || maxNotional == null || maxNotional.signum() < 0
                || maxNotional.precision() > 36 || Math.abs((long) maxNotional.scale()) > 18
                || armMaxDuration == null || armMaxDuration.isNegative() || armMaxDuration.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Invalid live test safety configuration");
    }
    public boolean configured() {
        return enabled && !allowedInstruments.isEmpty() && maxQuantity > 0 && maxNotional.signum() > 0 && !armMaxDuration.isZero();
    }
}
