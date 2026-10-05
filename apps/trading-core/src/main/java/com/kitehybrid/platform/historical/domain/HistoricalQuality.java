package com.kitehybrid.platform.historical.domain;

import java.time.*;
import java.util.*;

/** Bounded evidence: complete counts, at most 100 gap/unexpected samples, at most 32 dates. */
public record HistoricalQuality(int expectedBars, int missingBars, int unexpectedBars,
                                List<Instant> missingSamples, List<Instant> unexpectedSamples,
                                List<LocalDate> unknownDates, String calendarFingerprint) {
    public HistoricalQuality {
        missingSamples=List.copyOf(missingSamples); unexpectedSamples=List.copyOf(unexpectedSamples);
        unknownDates=List.copyOf(unknownDates);
    }
    public boolean complete() { return missingBars==0 && unexpectedBars==0 && unknownDates.isEmpty(); }
    public static HistoricalQuality analyze(HistoricalWindow window, Instant cutoff,
                                             List<HistoricalBar> bars, TradingCalendar calendar) {
        var actual=new TreeSet<Instant>();
        for(var b:bars) {
            if(!window.contains(b)) throw new HistoricalDataException(HistoricalDataException.Reason.INVALID_BAR);
            if(!b.endTime().isAfter(cutoff)) actual.add(b.startTime());
        }
        var expected=new TreeSet<Instant>(); var unknown=new ArrayList<LocalDate>();
        var unknownStarts=new HashSet<Instant>();
        var first=window.from().atZone(TradingCalendar.NSE_ZONE).toLocalDate();
        var last=window.to().minusNanos(1).atZone(TradingCalendar.NSE_ZONE).toLocalDate();
        for(var date=first; !date.isAfter(last); date=date.plusDays(1)) {
            if(!date.atStartOfDay(TradingCalendar.NSE_ZONE).toInstant().isBefore(cutoff)) continue;
            var day=calendar.day(date);
            if(day.status()==TradingCalendar.Status.UNKNOWN_SESSION) {
                unknown.add(date);
                for(var start:actual) if(start.atZone(TradingCalendar.NSE_ZONE).toLocalDate().equals(date)) unknownStarts.add(start);
            }
            for(var session:day.sessions()) {
                var end=date.atTime(session.close()).atZone(TradingCalendar.NSE_ZONE).toInstant();
                for(var start=date.atTime(session.open()).atZone(TradingCalendar.NSE_ZONE).toInstant();
                    start.plus(window.interval().duration()).compareTo(end)<=0; start=start.plus(window.interval().duration())) {
                    if(!start.isBefore(window.from()) && start.isBefore(window.to())
                            && !start.plus(window.interval().duration()).isAfter(cutoff)) expected.add(start);
                }
            }
        }
        var missing=new TreeSet<>(expected); missing.removeAll(actual);
        var unexpected=new TreeSet<>(actual); unexpected.removeAll(expected); unexpected.removeAll(unknownStarts);
        return new HistoricalQuality(expected.size(),missing.size(),unexpected.size(),missing.stream().limit(100).toList(),
                unexpected.stream().limit(100).toList(),unknown,calendar.fingerprint());
    }
}
