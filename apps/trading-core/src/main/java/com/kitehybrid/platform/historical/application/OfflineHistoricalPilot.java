package com.kitehybrid.platform.historical.application;

import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.instrument.application.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** A proposal, never an authorization or dispatcher. No broker or credential dependency. */
public final class OfflineHistoricalPilot {
    public record Request(String symbol, String instrumentToken, String route, HistoricalWindow window) { }
    public record Proposal(List<Request> requests, String masterHash, String calendarHash,
                           Duration minimumPacedTime, Duration connectTimeout, Duration readTimeout,
                           long maximumResponseBytes, int concurrency, int retryBudget, boolean redirects,
                           String authorizationStatus) {
        public Proposal { requests = List.copyOf(requests); }
    }
    public Proposal plan(List<OfflineUniverseResolution.Source> selected, DatedInstrumentMaster master,
                         Optional<DatedInstrumentMaster> previous, Instant asOf, LocalDate from,
                         LocalDate endExclusive, TradingCalendar calendar) {
        long days = ChronoUnit.DAYS.between(from, endExclusive);
        if (selected.size() < 2 || selected.size() > 5 || days < 1 || days > 5)
            throw new IllegalArgumentException("PILOT_BOUNDS_INVALID");
        var rows = new OfflineUniverseResolution().resolve(selected, Optional.of(master), asOf, previous);
        if (rows.stream().anyMatch(r -> r.status() != OfflineUniverseResolution.Status.RESOLVED))
            throw new IllegalArgumentException("PILOT_MAPPING_UNRESOLVED");
        var instruments = rows.stream().map(r -> r.instrument().orElseThrow()).toList();
        var plan = FiveMinuteAcquisitionPlan.create(instruments.stream().map(i -> i.id()).toList(), from, endExclusive, calendar);
        var requests = new ArrayList<Request>();
        for (var member : plan.members()) {
            var instrument = instruments.stream().filter(i -> i.id().equals(member.instrumentId())).findFirst().orElseThrow();
            for (var chunk : member.chunks()) {
                if (chunk.window().to().isAfter(asOf)) throw new IllegalArgumentException("INCOMPLETE_PILOT_SESSION");
                requests.add(new Request(instrument.tradingSymbol(), instrument.brokerId().value(),
                        "/instruments/historical/" + instrument.brokerId().value() + "/5minute", chunk.window()));
            }
        }
        if (requests.size() > 25) throw new IllegalArgumentException("PILOT_REQUEST_BUDGET_EXCEEDED");
        return new Proposal(requests, master.provenance().sha256(), calendar.fingerprint(), plan.minimumPacedTime(),
                Duration.ofSeconds(10), Duration.ofSeconds(30), requests.size() * 1024L * 1024,
                1, 0, false, "UNAPPROVED_OFFLINE_ONLY");
    }
    /** No approval text/boolean can enable real traffic in this phase. No callable I/O is accepted. */
    public void requireRealDispatch() {
        throw new IllegalStateException("REAL_HISTORICAL_DISPATCH_UNAVAILABLE");
    }
    public HistoricalQuality validate(HistoricalWindow window, Instant cutoff, List<HistoricalBar> bars,
                                      TradingCalendar calendar, String expectedContentHash) {
        if (window.interval() != BarInterval.FIVE_MINUTE || cutoff.isBefore(window.to())
                || !HistoricalFingerprint.bars(bars).equals(expectedContentHash))
            throw new IllegalArgumentException("PILOT_CANDLE_PROVENANCE_INVALID");
        Instant previous = null;
        for (var bar : bars) {
            if (bar.endTime().isAfter(cutoff) || previous != null && !bar.startTime().isAfter(previous))
                throw new IllegalArgumentException("INCOMPLETE_DUPLICATE_OR_UNORDERED_BAR");
            previous = bar.startTime();
        }
        var quality = HistoricalQuality.analyze(window, cutoff, bars, calendar);
        if (!quality.complete()) throw new IllegalArgumentException("PILOT_CANDLE_QUALITY_REJECTED");
        return quality;
    }
}
