package com.kitehybrid.platform.order.domain;

import com.kitehybrid.platform.order.domain.command.OrderType;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Exact pre-trade observation, never a guarantee about a subsequent MARKET fill. */
public final class ConservativeOrderValuation {
    private final BigDecimal referencePrice;
    private final BigDecimal conservativeUnitPrice;
    private final BigDecimal conservativeNotional;

    private ConservativeOrderValuation(BigDecimal reference, BigDecimal buffer, long quantity) {
        referencePrice = reference;
        conservativeUnitPrice = reference.multiply(buffer);
        conservativeNotional = conservativeUnitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** No rounding or MathContext: bounded decimal inputs and integral units remain exact. */
    public static ConservativeOrderValuation evaluate(OrderType type, long quantity, BigDecimal marketPrice,
            Optional<BigDecimal> limitPrice, BigDecimal priceBuffer) {
        Objects.requireNonNull(type); Objects.requireNonNull(limitPrice);
        positivePrice(marketPrice);
        validatePriceBuffer(priceBuffer);
        if (quantity <= 0) throw new IllegalArgumentException("Invalid valuation quantity");
        BigDecimal reference = switch (type) {
            case MARKET -> {
                if (limitPrice.isPresent()) throw new IllegalArgumentException("MARKET price not allowed");
                yield marketPrice;
            }
            case LIMIT -> {
                var limit = limitPrice.orElseThrow(() -> new IllegalArgumentException("LIMIT price required"));
                positivePrice(limit);
                yield marketPrice.max(limit);
            }
            default -> throw new IllegalArgumentException("Unsupported valuation order type");
        };
        return new ConservativeOrderValuation(reference, priceBuffer, quantity);
    }

    /** The existing risk.price-buffer contract is shared by configuration and every valuation. */
    public static void validatePriceBuffer(BigDecimal buffer) {
        if (buffer == null || buffer.compareTo(BigDecimal.ONE) < 0
                || buffer.compareTo(BigDecimal.valueOf(2)) > 0 || !bounded(buffer))
            throw new IllegalArgumentException("Invalid conservative price buffer");
    }
    private static void positivePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0 || !bounded(price))
            throw new IllegalArgumentException("Invalid valuation price");
    }
    private static boolean bounded(BigDecimal value) {
        return value.precision() <= 36 && Math.abs((long) value.scale()) <= 18;
    }
    public BigDecimal referencePrice() { return referencePrice; }
    public BigDecimal conservativeUnitPrice() { return conservativeUnitPrice; }
    public BigDecimal conservativeNotional() { return conservativeNotional; }
    public boolean within(BigDecimal ceiling) {
        return ceiling != null && ceiling.signum() > 0 && conservativeNotional.compareTo(ceiling) <= 0;
    }
}
