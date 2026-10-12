package com.kitehybrid.platform.instrument.application;

import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import java.time.*;
import java.util.*;

/** One ledger row per source record, preserving spelling/order. No registry publication. */
public final class OfflineUniverseResolution {
    public enum Status { RESOLVED, AMBIGUOUS, NOT_FOUND, INVALID_SOURCE, STALE_MASTER, MASTER_UNAVAILABLE }
    public record Source(String exchange, String symbol, boolean enabled) { }
    public record Row(int sourceRecord, Source source, Status status, String reason,
                      Optional<Instrument> instrument, List<DatedInstrumentMaster.Candidate> candidates,
                      Optional<DatedInstrumentMaster.Provenance> provenance) {
        public Row { candidates = List.copyOf(candidates); }
    }
    public List<Row> resolve(List<Source> source, Optional<DatedInstrumentMaster> master,
                             Instant asOf, Optional<DatedInstrumentMaster> previous) {
        Objects.requireNonNull(source); Objects.requireNonNull(master); Objects.requireNonNull(asOf);
        Objects.requireNonNull(previous);
        if (previous.isPresent() && (master.isEmpty()
                || previous.get().provenance().observedAt().isAfter(master.get().provenance().observedAt())))
            throw new IllegalArgumentException("INVALID_PREVIOUS_MASTER");
        var result = new ArrayList<Row>(); var seen = new HashSet<String>();
        var old = previous.map(m -> m.candidates().stream().map(c -> researchView(c.instrument())).toList())
                .orElse(List.of());
        var projected = master.map(m -> m.candidates().stream().map(c -> researchView(c.instrument())).toList())
                .orElse(List.of());
        for (int i = 0; i < source.size(); i++) {
            Source s = source.get(i); Status status; String reason; Instrument found = null;
            List<DatedInstrumentMaster.Candidate> candidates = List.of();
            if (s == null || !valid(s.exchange()) || !valid(s.symbol()) || !s.enabled()) {
                status = Status.INVALID_SOURCE; reason = "MALFORMED_OR_DISABLED_SOURCE";
            } else if (!s.exchange().strip().equalsIgnoreCase("NSE")) {
                status = Status.INVALID_SOURCE; reason = "EXCHANGE_MISMATCH";
            } else if (!seen.add(key(s.exchange(), s.symbol()))) {
                status = Status.INVALID_SOURCE; reason = "DUPLICATE_SOURCE";
            } else if (master.isEmpty()) {
                status = Status.MASTER_UNAVAILABLE; reason = "NO_DATED_MASTER_SUPPLIED";
            } else {
                var m = master.get(); var p = m.provenance();
                candidates = m.candidates().stream().filter(c -> key(c.instrument().exchange(), c.instrument().tradingSymbol())
                        .equals(key(s.exchange(), s.symbol()))).toList();
                var date = asOf.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
                if (p.observedAt().isAfter(asOf) || Duration.between(p.observedAt(), asOf).compareTo(Duration.ofDays(1)) > 0
                        || p.publisherAsOf().filter(d -> d.isBefore(date.minusDays(1)) || d.isAfter(date)).isPresent()) {
                    status = Status.STALE_MASTER; reason = "STALE_OR_FUTURE_MASTER";
                } else {
                    var symbol = new HistoricalUniverseResolver.Symbol(s.exchange(), s.symbol());
                    var resolved = new HistoricalUniverseResolver().resolve(List.of(symbol),
                            projected,
                            p.observedAt(), p.sha256(), asOf, old, Set.of()).getFirst();
                    status = switch (resolved.status()) {
                        case RESOLVED -> Status.RESOLVED;
                        case AMBIGUOUS -> Status.AMBIGUOUS;
                        default -> Status.NOT_FOUND;
                    };
                    reason = resolved.reason();
                    found = resolved.instrument().isPresent() ? candidates.getFirst().instrument() : null;
                    if (status == Status.RESOLVED && candidates.getFirst().listing() == DatedInstrumentMaster.Listing.INACTIVE) {
                        status = Status.NOT_FOUND; reason = "EXPLICIT_INACTIVE_LISTING"; found = null;
                    }
                }
            }
            result.add(new Row(i + 1, s, status, reason, Optional.ofNullable(found), candidates,
                    master.map(DatedInstrumentMaster::provenance)));
        }
        return List.copyOf(result);
    }
    private static boolean valid(String text) {
        return text != null && !text.isBlank() && text.length() <= 128 && text.chars().noneMatch(Character::isISOControl);
    }
    /** Phase14 research uses KITE while the existing CSV mapper uses ZERODHA. Translate only
     * this known namespace for the frozen resolver's comparison, then return the original
     * instrument. IDs, token values, classification and provenance are never changed. */
    private static Instrument researchView(Instrument i) {
        if (!i.brokerId().broker().equals("ZERODHA"))
            throw new IllegalArgumentException("UNEXPECTED_MASTER_BROKER_NAMESPACE");
        return new Instrument(i.id(), new BrokerInstrumentId("KITE", i.brokerId().value()), i.tradingSymbol(),
                i.exchange(), i.segment(), i.type(), i.expiry(), i.strike(), i.tickSize(), i.lotSize());
    }
    private static String key(String exchange, String symbol) {
        return exchange.strip().toUpperCase(Locale.ROOT) + "|" + symbol.strip().toUpperCase(Locale.ROOT);
    }
}
