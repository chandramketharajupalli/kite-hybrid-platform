package com.kitehybrid.platform.broker.infrastructure.kite;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("kite.market-data")
public record KiteMarketDataProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("false") boolean diagnosticEnabled,
        @DefaultValue("10s") Duration connectTimeout, @DefaultValue("30s") Duration staleAfter,
        @DefaultValue("30s") Duration idleTimeout, @DefaultValue("4096") int queueCapacity,
        @DefaultValue("3000") int maxSubscriptions, @DefaultValue Reconnect reconnect) {
    public KiteMarketDataProperties {
        positive(connectTimeout); positive(staleAfter); positive(idleTimeout);
        if (queueCapacity < 1 || queueCapacity > 1_000_000 || maxSubscriptions < 1 || maxSubscriptions > 3000)
            throw new IllegalArgumentException("Invalid market-data capacity");
        if (reconnect == null) throw new IllegalArgumentException("Reconnect policy required");
    }
    public record Reconnect(@DefaultValue("1s") Duration initialDelay,
            @DefaultValue("30s") Duration maxDelay, @DefaultValue("8") int maxAttempts) {
        public Reconnect {
            positive(initialDelay); positive(maxDelay);
            if (maxDelay.compareTo(initialDelay) < 0 || maxAttempts < 0 || maxAttempts > 100)
                throw new IllegalArgumentException("Invalid reconnect policy");
        }
    }
    private static void positive(Duration value) {
        if (value == null || value.isNegative() || value.isZero() || value.compareTo(Duration.ofDays(1)) > 0)
            throw new IllegalArgumentException("Market-data duration must be positive and at most one day");
    }
}
