package com.kitehybrid.platform.historical.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.time.*;
import java.util.*;
import static com.kitehybrid.platform.historical.domain.HistoricalDataException.Reason.INVALID_REQUEST;

/** Bounded minute-aligned half-open range. All bounds are UTC instants. */
public record HistoricalWindow(InstrumentId instrumentId, BarInterval interval, Instant from, Instant to) {
    public HistoricalWindow {
        if (instrumentId == null || interval == null || from == null || to == null || !from.isBefore(to)
                || Duration.between(from,to).compareTo(Duration.ofDays(31)) > 0
                || from.getNano()!=0 || to.getNano()!=0 || Math.floorMod(from.getEpochSecond(),60)!=0
                || Math.floorMod(to.getEpochSecond(),60)!=0
                || from.isBefore(Instant.parse("1990-01-01T00:00:00Z"))
                || to.isAfter(Instant.parse("2100-01-01T00:00:00Z")))
            throw new HistoricalDataException(INVALID_REQUEST);
    }
    public boolean contains(HistoricalBar bar) {
        return instrumentId.equals(bar.instrumentId()) && interval == bar.interval()
                && !bar.startTime().isBefore(from) && bar.startTime().isBefore(to);
    }
    public List<HistoricalWindow> chunks() {
        var chunks = new ArrayList<HistoricalWindow>();
        for (var cursor=from; cursor.isBefore(to);) {
            var end=cursor.plus(Duration.ofDays(1)); if(end.isAfter(to)) end=to;
            chunks.add(new HistoricalWindow(instrumentId,interval,cursor,end)); cursor=end;
        }
        return List.copyOf(chunks);
    }
}
