package com.kitehybrid.platform.risk;

import com.kitehybrid.platform.risk.domain.RiskLimits;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiskConfigurationTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"0.99", "2.01", "NaN", "1E100", "1.0000000000000000001"})
    void malformedBufferFailsStartup(String value) {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(com.kitehybrid.platform.risk.infrastructure.RiskConfiguration.class)
                .withPropertyValues("spring.profiles.active=test", "risk.price-buffer=" + value)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
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
