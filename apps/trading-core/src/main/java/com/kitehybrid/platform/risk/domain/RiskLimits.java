package com.kitehybrid.platform.risk.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** Zero defaults are intentionally unconfigured, never unlimited. */
public record RiskLimits(boolean enabled, long maxOrderQuantity, BigDecimal maxOrderValue,
                         long maxPositionQuantity, BigDecimal maxExposure,
                         Duration marketDataMaxAge, Duration registryMaxAge,
                         BigDecimal priceBuffer, BigDecimal cashReserve) {
    public RiskLimits {
        Objects.requireNonNull(maxOrderValue); Objects.requireNonNull(maxExposure);
        Objects.requireNonNull(marketDataMaxAge); Objects.requireNonNull(registryMaxAge);
        Objects.requireNonNull(priceBuffer); Objects.requireNonNull(cashReserve);
        if (maxOrderQuantity < 0 || maxPositionQuantity < 0 || maxOrderValue.signum() < 0
                || maxExposure.signum() < 0 || marketDataMaxAge.isNegative() || registryMaxAge.isNegative()
                || priceBuffer.compareTo(BigDecimal.ONE) < 0 || priceBuffer.compareTo(new BigDecimal("2")) > 0
                || cashReserve.signum() < 0)
            throw new IllegalArgumentException("Invalid risk configuration");
        for (var amount : new BigDecimal[]{maxOrderValue, maxExposure, priceBuffer, cashReserve})
            if (amount.precision() > 36 || Math.abs((long) amount.scale()) > 18)
                throw new IllegalArgumentException("Risk numeric bounds exceeded");
    }
    public boolean configured() {
        return maxOrderQuantity > 0 && maxPositionQuantity > 0 && maxOrderValue.signum() > 0
                && maxExposure.signum() > 0 && !marketDataMaxAge.isZero() && !registryMaxAge.isZero()
                && priceBuffer.compareTo(BigDecimal.ONE) >= 0 && cashReserve.signum() > 0;
    }
    public String version() {
        try {
            return "cash-v1:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(); }
    }
}
