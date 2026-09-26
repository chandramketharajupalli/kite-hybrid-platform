package com.kitehybrid.platform.order.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@ConfigurationProperties("kite.order-execution")
public class OrderExecutionConfigurationProperties {
    private boolean enabled;
    private Set<UUID> allowedInstruments = Set.of();
    private long maxQuantity;
    private BigDecimal maxNotional = BigDecimal.ZERO;
    private Duration riskDecisionMaxAge = Duration.ZERO;
    private Duration marketDataMaxAge = Duration.ZERO;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Set<UUID> getAllowedInstruments() { return allowedInstruments; }
    public void setAllowedInstruments(Set<UUID> value) { allowedInstruments = value == null ? Set.of() : Set.copyOf(value); }
    public long getMaxQuantity() { return maxQuantity; }
    public void setMaxQuantity(long value) { maxQuantity = value; }
    public BigDecimal getMaxNotional() { return maxNotional; }
    public void setMaxNotional(BigDecimal value) { maxNotional = value == null ? BigDecimal.ZERO : value; }
    public Duration getRiskDecisionMaxAge() { return riskDecisionMaxAge; }
    public void setRiskDecisionMaxAge(Duration value) { riskDecisionMaxAge = value == null ? Duration.ZERO : value; }
    public Duration getMarketDataMaxAge() { return marketDataMaxAge; }
    public void setMarketDataMaxAge(Duration value) { marketDataMaxAge = value == null ? Duration.ZERO : value; }
}
