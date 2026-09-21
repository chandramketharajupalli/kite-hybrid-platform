package com.kitehybrid.platform.risk;

import com.kitehybrid.platform.risk.domain.RiskLimits;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiskConfigurationTest {
    @Test void zeroDefaultsAreUnconfiguredAndCannotApprove() {
        var limits = new RiskLimits(false, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO,
                Duration.ZERO, Duration.ZERO, BigDecimal.ONE, BigDecimal.ZERO);
        assertFalse(limits.enabled());
        assertFalse(limits.configured());
    }

    @Test void negativeAndExtremeValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RiskLimits(true, -1, BigDecimal.ONE,
                1, BigDecimal.ONE, Duration.ofSeconds(1), Duration.ofSeconds(1), BigDecimal.ONE,
                BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new RiskLimits(true, 1,
                new BigDecimal("1e100"), 1, BigDecimal.ONE, Duration.ofSeconds(1), Duration.ofSeconds(1),
                BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test void explicitConfigurationIsStillRequiredBeforeApproval() {
        var limits = new RiskLimits(true, 1, BigDecimal.TEN, 1, BigDecimal.TEN,
                Duration.ofSeconds(1), Duration.ofSeconds(1), BigDecimal.ONE, BigDecimal.ONE);
        assertTrue(limits.configured());
    }
}
