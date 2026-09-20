package com.kitehybrid.platform.broker.domain.read;

import java.math.BigDecimal;
import java.util.Objects;

final class ReadModelValidation {
    private static final BigDecimal LIMIT = new BigDecimal("1e18");
    private ReadModelValidation() {}
    static String identifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))
            throw new IllegalArgumentException("Invalid broker observation identifier");
        return value;
    }
    static BigDecimal amount(BigDecimal value) {
        Objects.requireNonNull(value);
        if (value.precision() > 36 || value.scale() > 18 || value.scale() < -18
                || value.abs().compareTo(LIMIT) >= 0)
            throw new IllegalArgumentException("Amount outside supported bounds");
        return value;
    }
    static BigDecimal price(BigDecimal value) {
        amount(value);
        if (value.signum() < 0) throw new IllegalArgumentException("Negative price");
        return value;
    }
    static long nonnegative(long value) {
        if (value < 0) throw new IllegalArgumentException("Negative quantity");
        return value;
    }
    static long positive(long value) {
        if (value <= 0) throw new IllegalArgumentException("Nonpositive quantity");
        return value;
    }
}
