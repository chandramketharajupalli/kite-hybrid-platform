package com.kitehybrid.platform.historical.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.kitehybrid.platform.historical.domain.HistoricalDataException.Reason.INVALID_BAR;

/** Interval start in UTC. Exact, scale-independent equality after decimal canonicalization. */
public record HistoricalBar(InstrumentId instrumentId, Instant startTime, BarInterval interval,
                            BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                            long volume, Optional<BigDecimal> openInterest) {
    public HistoricalBar {
        if (instrumentId == null || interval == null || startTime == null || openInterest == null
                || startTime.getNano() != 0 || Math.floorMod(startTime.getEpochSecond(),60) != 0
                || startTime.isBefore(Instant.parse("1990-01-01T00:00:00Z"))
                || startTime.isAfter(Instant.parse("2100-01-01T00:00:00Z")) || volume < 0)
            throw new HistoricalDataException(INVALID_BAR);
        open = decimal(open, true); high = decimal(high, true);
        low = decimal(low, true); close = decimal(close, true);
        openInterest = openInterest.map(value -> decimal(value, false));
        if (high.compareTo(open) < 0 || high.compareTo(close) < 0 || high.compareTo(low) < 0
                || low.compareTo(open) > 0 || low.compareTo(close) > 0)
            throw new HistoricalDataException(INVALID_BAR);
    }
    private static BigDecimal decimal(BigDecimal value, boolean positive) {
        if (value == null || value.signum() < 0 || (positive && value.signum() == 0)
                || value.precision() > 100 || Math.abs((long)value.scale()) > 100)
            throw new HistoricalDataException(INVALID_BAR);
        value = value.stripTrailingZeros();
        if (value.scale() > 10 || value.precision() - value.scale() > 18)
            throw new HistoricalDataException(INVALID_BAR);
        return value;
    }
    public Instant endTime() { return startTime.plus(interval.duration()); }
}
