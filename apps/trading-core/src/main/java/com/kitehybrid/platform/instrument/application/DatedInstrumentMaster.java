package com.kitehybrid.platform.instrument.application;

import com.kitehybrid.platform.instrument.domain.Instrument;
import java.time.*;
import java.util.*;

/** Offline, immutable observation. A content hash does not attest publisher authenticity. */
public record DatedInstrumentMaster(Provenance provenance, List<Candidate> candidates) {
    public enum Listing { UNSPECIFIED, ACTIVE, INACTIVE }
    public record Candidate(int sourceRecord, Instrument instrument, Listing listing) {
        public Candidate {
            if (sourceRecord < 1) throw new IllegalArgumentException("INVALID_SOURCE_RECORD");
            Objects.requireNonNull(instrument); Objects.requireNonNull(listing);
        }
    }
    public record Provenance(String source, String evidenceLocation, Instant observedAt,
                             Optional<LocalDate> publisherAsOf, String sha256, long bytes,
                             String schemaVersion, boolean synthetic) {
        public Provenance {
            // Evidence references are identifiers, not arbitrary URLs with query strings or credentials.
            if (source == null || !source.matches("[A-Za-z0-9_.:/-]{1,256}")
                    || evidenceLocation == null || !evidenceLocation.matches("[A-Za-z0-9_./-]{1,256}")
                    || sha256 == null || !sha256.matches("[a-f0-9]{64}")
                    || bytes < 1 || bytes > 32L * 1024 * 1024
                    || !"kite-instruments-csv-v3".equals(schemaVersion))
                throw new IllegalArgumentException("INVALID_MASTER_PROVENANCE");
            Objects.requireNonNull(observedAt); Objects.requireNonNull(publisherAsOf);
            if (publisherAsOf.filter(d -> d.isAfter(observedAt.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate())).isPresent())
                throw new IllegalArgumentException("FUTURE_PUBLISHER_DATE");
        }
    }
    public DatedInstrumentMaster {
        Objects.requireNonNull(provenance); candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) throw new IllegalArgumentException("EMPTY_MASTER");
    }
}
