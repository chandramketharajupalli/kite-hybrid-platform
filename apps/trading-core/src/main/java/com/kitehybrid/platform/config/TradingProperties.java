package com.kitehybrid.platform.config;

import com.kitehybrid.platform.shared.domain.TradingMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("trading")
public record TradingProperties(
        @DefaultValue("PAPER") TradingMode mode,
        @DefaultValue("false") boolean enableLiveTrading,
        @DefaultValue("true") boolean emergencyStop) {
    public TradingProperties {
        if (mode == null) throw new IllegalArgumentException("Trading mode required");
        if (enableLiveTrading || mode == TradingMode.LIVE) {
            throw new IllegalArgumentException("Live trading is unavailable in Phase 1");
        }
    }
}
