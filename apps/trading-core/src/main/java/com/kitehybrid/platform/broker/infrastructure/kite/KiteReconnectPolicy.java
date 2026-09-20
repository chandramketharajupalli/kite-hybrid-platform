package com.kitehybrid.platform.broker.infrastructure.kite;

import java.time.Duration;
import java.util.function.DoubleSupplier;

/** Equal jitter: half to all of a capped exponential delay; deterministic randomness is injectable. */
final class KiteReconnectPolicy {
    private final KiteMarketDataProperties.Reconnect settings;
    private final DoubleSupplier random;
    KiteReconnectPolicy(KiteMarketDataProperties.Reconnect settings, DoubleSupplier random) {
        this.settings = settings; this.random = random;
    }
    Duration delay(int attempt) {
        long initial = settings.initialDelay().toNanos();
        long max = settings.maxDelay().toNanos();
        long cap = initial;
        for (int i = 1; i < attempt && cap < max; i++) cap = Math.min(max, cap > max / 2 ? max : cap * 2);
        double value = random.getAsDouble();
        if (value < 0 || value >= 1 || !Double.isFinite(value)) throw new IllegalArgumentException("Invalid jitter");
        return Duration.ofNanos(Math.max(1, (long) (cap * (0.5 + value * 0.5))));
    }
}
