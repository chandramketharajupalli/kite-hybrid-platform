package com.kitehybrid.platform.historical.domain;

import java.time.Duration;

/** Only provider minute bars are supported initially; no implicit resampling. */
public enum BarInterval {
    MINUTE(Duration.ofMinutes(1));
    private final Duration duration;
    BarInterval(Duration duration) { this.duration = duration; }
    public Duration duration() { return duration; }
}
