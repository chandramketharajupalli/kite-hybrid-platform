package com.kitehybrid.platform.historical.domain;

import java.time.Duration;

/** Native provider intervals only; no implicit resampling. */
public enum BarInterval {
    MINUTE(Duration.ofMinutes(1)), FIVE_MINUTE(Duration.ofMinutes(5));
    private final Duration duration;
    BarInterval(Duration duration) { this.duration = duration; }
    public Duration duration() { return duration; }
}
