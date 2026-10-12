package com.kitehybrid.platform.historical.application;

import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.instrument.application.*;
import com.kitehybrid.platform.instrument.domain.Instrument;
import java.time.*;
import java.util.*;

/** Reporting around existing offline validators; no acquisition, authorization or persistence. */
public final class OfflineDataVerification {
    public enum ResolutionStatus { RESOLVED, AMBIGUOUS, MISSING, INELIGIBLE, MASTER_UNAVAILABLE }
    public record Resolution(int sourceRecord, OfflineUniverseResolution.Source source,
                             ResolutionStatus status, String reason, Optional<Instrument> instrument,
                             List<DatedInstrumentMaster.Candidate> candidates,
                             Optional<DatedInstrumentMaster.Provenance> provenance) {
        public Resolution { candidates = List.copyOf(candidates); }
    }
    public List<Resolution> resolve(List<OfflineUniverseResolution.Source> source,
                                    Optional<DatedInstrumentMaster> master, Instant asOf,
                                    Optional<DatedInstrumentMaster> previous) {
        return new OfflineUniverseResolution().resolve(source, master, asOf, previous).stream().map(row -> {
            var status = switch (row.status()) {
                case RESOLVED -> ResolutionStatus.RESOLVED;
                case AMBIGUOUS -> ResolutionStatus.AMBIGUOUS;
                case NOT_FOUND -> row.reason().equals("ABSENT_NO_RENAME_INFERENCE")
                        ? ResolutionStatus.MISSING : ResolutionStatus.INELIGIBLE;
                case INVALID_SOURCE -> ResolutionStatus.INELIGIBLE;
                case STALE_MASTER, MASTER_UNAVAILABLE -> ResolutionStatus.MASTER_UNAVAILABLE;
            };
            return new Resolution(row.sourceRecord(), row.source(), status, row.reason(), row.instrument(),
                    row.candidates(), row.provenance());
        }).toList();
    }
    public record Input(HistoricalWindow window, Instant cutoff, Instant asOf, List<HistoricalBar> bars,
                        TradingCalendar calendar, String calendarHash, String contentHash,
                        String rawObservationHash, String masterHash, String corporateActionEvidenceHash,
                        ZoneId sourceZone, boolean synthetic) {
        public Input {
            Objects.requireNonNull(window); Objects.requireNonNull(cutoff); Objects.requireNonNull(asOf);
            bars = List.copyOf(bars); Objects.requireNonNull(calendar);
        }
    }
    public enum QualityStatus { NOT_EVALUATED, QUARANTINED, OFFLINE_CHECKS_PASSED }
    public record QualityReport(QualityStatus status, boolean synthetic, int received, int accepted,
                                List<String> flags, Optional<String> contentHash) {
        public QualityReport { flags = List.copyOf(flags); }
    }
    /** Hashes bind supplied evidence, not its authenticity. A pass never permits real acquisition. */
    public QualityReport review(Optional<Input> supplied) {
        if (supplied.isEmpty()) return new QualityReport(QualityStatus.NOT_EVALUATED, false, 0, 0,
                List.of("NO_CANDLES_SUPPLIED"), Optional.empty());
        var in = supplied.get(); var flags = new LinkedHashSet<String>();
        if (!TradingCalendar.NSE_ZONE.equals(in.sourceZone())) flags.add("SOURCE_TIMEZONE_UNVERIFIED");
        if (!hash(in.rawObservationHash()) || !hash(in.masterHash()) || !hash(in.corporateActionEvidenceHash()))
            flags.add("SOURCE_MASTER_OR_CORPORATE_ACTION_EVIDENCE_MISSING");
        if (!in.calendar().fingerprint().equals(in.calendarHash())) flags.add("CALENDAR_FINGERPRINT_MISMATCH");
        if (in.cutoff().isAfter(in.asOf())) flags.add("FUTURE_CUTOFF");
        if (in.bars().isEmpty()) flags.add("EMPTY_SESSION");
        try {
            var quality = HistoricalQuality.analyze(in.window(), in.cutoff(), in.bars(), in.calendar());
            if (quality.missingBars() > 0) flags.add("MISSING_CANDLES");
            if (quality.unexpectedBars() > 0) flags.add("OUT_OF_SESSION_CANDLES");
            if (!quality.unknownDates().isEmpty()) flags.add("CALENDAR_DATES_UNKNOWN");
            new OfflineHistoricalPilot().validate(in.window(), in.cutoff(), in.bars(), in.calendar(), in.contentHash());
        } catch (HistoricalDataException | IllegalArgumentException invalid) {
            flags.add("CANDLE_VALIDATION_REJECTED");
        }
        boolean passed = flags.isEmpty();
        return new QualityReport(passed ? QualityStatus.OFFLINE_CHECKS_PASSED : QualityStatus.QUARANTINED,
                in.synthetic(), in.bars().size(), passed ? in.bars().size() : 0,
                List.copyOf(flags), Optional.of(HistoricalFingerprint.bars(in.bars())));
    }
    private static boolean hash(String text) { return text != null && text.matches("[a-f0-9]{64}"); }
}
