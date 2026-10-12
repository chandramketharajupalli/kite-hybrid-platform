package com.kitehybrid.platform.historical.application;

import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Offline, complete request plan. No weekday assumptions, network, persistence or execution. */
public record HistoricalCorpusPlan(InstrumentId instrumentId, LocalDate first, LocalDate endExclusive,
                                   TradingCalendar calendar, int maximumRequests, BarInterval interval) {
    public HistoricalCorpusPlan(InstrumentId instrumentId, LocalDate first, LocalDate endExclusive,
                                TradingCalendar calendar, int maximumRequests) {
        this(instrumentId,first,endExclusive,calendar,maximumRequests,BarInterval.MINUTE);
    }
    public HistoricalCorpusPlan {
        Objects.requireNonNull(interval); Objects.requireNonNull(instrumentId); Objects.requireNonNull(first);
        Objects.requireNonNull(endExclusive); Objects.requireNonNull(calendar);
        long days=ChronoUnit.DAYS.between(first,endExclusive);
        if(days<1 || days>366 || maximumRequests<1 || maximumRequests>366)
            throw new IllegalArgumentException("CORPUS_BOUNDS_INVALID");
        int requests=0;
        for(var date=first;date.isBefore(endExclusive);date=date.plusDays(1)) {
            var day=calendar.day(date);
            if(day.status()==TradingCalendar.Status.UNKNOWN_SESSION)
                throw new IllegalArgumentException("CORPUS_CALENDAR_UNKNOWN");
            if(day.sessions().size()>1) throw new IllegalArgumentException("CORPUS_SPLIT_SESSION_UNSUPPORTED");
            requests+=day.sessions().size();
        }
        if(requests==0 || requests>maximumRequests)
            throw new IllegalArgumentException("CORPUS_REQUEST_BUDGET_EXCEEDED");
    }
    public record Chunk(int sequence, LocalDate date, HistoricalWindow window, TradingCalendar calendar,
                        int expectedBars) { }
    public List<Chunk> chunks() {
        var result=new ArrayList<Chunk>();
        for(var date=first;date.isBefore(endExclusive);date=date.plusDays(1)) {
            var day=calendar.day(date);
            if(day.sessions().isEmpty()) continue;
            var session=day.sessions().getFirst();
            var window=new HistoricalWindow(instrumentId,interval,
                date.atTime(session.open()).atZone(TradingCalendar.NSE_ZONE).toInstant(),
                date.atTime(session.close()).atZone(TradingCalendar.NSE_ZONE).toInstant());
            var bounded=new TradingCalendar(calendar.version(),calendar.source(),Map.of(date,day));
            result.add(new Chunk(result.size()+1,date,window,bounded,
                Math.toIntExact(Duration.between(window.from(),window.to()).dividedBy(interval.duration()))));
        }
        return List.copyOf(result);
    }
    public String fingerprint() {
        return HistoricalFingerprint.sha256((interval==BarInterval.MINUTE?"":"FIVE_MINUTE\n")+"historical-corpus-plan-v1\n"+instrumentId.value()+"\n"
            +first+"\n"+endExclusive+"\n"+calendar.fingerprint()+"\n"+maximumRequests+"\n");
    }
}
