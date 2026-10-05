package com.kitehybrid.platform.order.domain;

import com.kitehybrid.platform.order.domain.command.OrderType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/** Pure MARKET sizing from explicit evidence. Freshness and account headroom belong to the caller. */
public final class ConservativeOrderQuantitySizer {
    private ConservativeOrderQuantitySizer() {}

    public record Size(long quantity, BigDecimal conservativeUnitPrice, BigDecimal conservativeNotional) {}

    public static Size size(BigDecimal freshPrice, BigDecimal buffer, BigDecimal ceiling,
                            int lotSize, List<Long> quantityCaps) {
        if (ceiling == null || ceiling.signum() < 0 || ceiling.precision() > 36
                || Math.abs((long) ceiling.scale()) > 18 || lotSize <= 0)
            throw new IllegalArgumentException("Invalid sizing budget or lot");
        var caps = List.copyOf(quantityCaps);
        if (caps.isEmpty() || caps.stream().anyMatch(q -> q < 0))
            throw new IllegalArgumentException("Explicit nonnegative quantity caps required");
        var unit = ConservativeOrderValuation.evaluate(OrderType.MARKET, 1, freshPrice,
                Optional.empty(), buffer).conservativeUnitPrice();
        // Clamp in decimal space BEFORE long conversion; huge budgets cannot wrap quantity.
        long cap = caps.stream().mapToLong(Long::longValue).min().orElseThrow();
        long units = ceiling.divide(unit, 0, RoundingMode.DOWN)
                .min(BigDecimal.valueOf(cap)).longValueExact();
        long quantity = Math.multiplyExact(units / lotSize, (long) lotSize);
        if (quantity == 0) return new Size(0, unit, BigDecimal.ZERO);
        var valuation = ConservativeOrderValuation.evaluate(OrderType.MARKET, quantity, freshPrice,
                Optional.empty(), buffer);
        if (!valuation.within(ceiling) || quantity > cap || quantity % lotSize != 0)
            throw new IllegalArgumentException("Sizing verification failed");
        return new Size(quantity, unit, valuation.conservativeNotional());
    }
}
