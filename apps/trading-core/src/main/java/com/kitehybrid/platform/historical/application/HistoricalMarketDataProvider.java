package com.kitehybrid.platform.historical.application;

import com.kitehybrid.platform.historical.domain.*;
import java.time.Instant;
import java.util.List;

/** Read-only acquisition. No broker identity, session, order or execution capability. */
public interface HistoricalMarketDataProvider {
    Batch fetch(HistoricalWindow chunk);
    record Batch(List<HistoricalBar> bars, String source, String sourceVersion, Instant observedAt) {
        public Batch {
            bars=List.copyOf(bars);
            if(bars.size()>3000 || source==null || !source.matches("[A-Za-z0-9_.-]{1,80}")
                    || sourceVersion==null || !sourceVersion.matches("[A-Za-z0-9_.-]{1,80}") || observedAt==null)
                throw new HistoricalDataException(HistoricalDataException.Reason.INVALID_RESPONSE);
        }
    }
}
