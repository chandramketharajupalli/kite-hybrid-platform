package com.kitehybrid.platform.order.domain.command;

import java.math.BigDecimal;

final class CommandText {
    private CommandText() {}
    static String key(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))
            throw new IllegalArgumentException("Invalid idempotency key");
        return value;
    }
    static BigDecimal nonnegativePrice(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.precision() > 36 || value.scale() > 18)
            throw new IllegalArgumentException("Invalid order price");
        return value.stripTrailingZeros();
    }
}
