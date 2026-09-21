package com.kitehybrid.platform.risk.infrastructure;

import com.kitehybrid.platform.risk.domain.RiskLimits;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** All pre-trade limits are centralized here. Zero limits are deliberately unconfigured. */
@ConfigurationProperties("risk")
public record RiskConfigurationProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("0") long maxOrderQuantity,
        @DefaultValue("0") BigDecimal maxOrderValue,
        @DefaultValue("0") long maxPositionQuantity,
        @DefaultValue("0") BigDecimal maxExposure,
        @DefaultValue("0s") Duration marketDataMaxAge,
        @DefaultValue("0s") Duration registryMaxAge,
        @DefaultValue("1.0") BigDecimal priceBuffer,
        @DefaultValue("0") BigDecimal cashReserve) {
    public RiskLimits limits() {
        return new RiskLimits(enabled, maxOrderQuantity, maxOrderValue, maxPositionQuantity,
                maxExposure, marketDataMaxAge, registryMaxAge, priceBuffer, cashReserve);
    }
}
