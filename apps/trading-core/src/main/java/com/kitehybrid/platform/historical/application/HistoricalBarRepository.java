package com.kitehybrid.platform.historical.application;

import com.kitehybrid.platform.historical.domain.*;
import java.time.Instant;
import java.util.*;

public interface HistoricalBarRepository {
    /** Atomic chunk; identical values are replay-safe; differing values roll back the entire chunk. */
    Saved append(Chunk chunk);
    Dataset query(Query query);
    /** Pin the returned manifest in research artifacts; later backfill must not silently change a replay. */
    default Dataset replay(Dataset expected) {
        var actual=query(expected.query());
        if(!actual.contentHash().equals(expected.contentHash()))
            throw new HistoricalDataException(HistoricalDataException.Reason.CONFLICT);
        return actual;
    }
    Optional<Evidence> evidence(UUID chunkId);
    record Evidence(UUID chunkId, UUID runId, HistoricalWindow request, HistoricalWindow window,
                    String source, String sourceVersion, Instant observedAt, Instant ingestedAt,
                    Instant cutoff, String referenceFingerprint, String calendarFingerprint,
                    String contentHash, int accepted, int inserted) { }
    record Chunk(UUID runId, HistoricalWindow request, HistoricalWindow window, List<HistoricalBar> bars,
                 String source, String sourceVersion, Instant observedAt, Instant ingestedAt,
                 Instant closedBarCutoff, String referenceFingerprint, String calendarFingerprint) {
        public Chunk {
            Objects.requireNonNull(runId); Objects.requireNonNull(request); Objects.requireNonNull(window);
            bars=List.copyOf(bars);
            if(!request.instrumentId().equals(window.instrumentId()) || request.interval()!=window.interval()
                    || window.from().isBefore(request.from()) || window.to().isAfter(request.to())
                    || java.time.Duration.between(window.from(),window.to()).compareTo(java.time.Duration.ofDays(1))>0
                    || observedAt==null || ingestedAt==null || closedBarCutoff==null
                    || observedAt.isAfter(ingestedAt) || closedBarCutoff.isAfter(observedAt)
                    || ingestedAt.getNano()%1000!=0 || observedAt.getNano()%1000!=0 || closedBarCutoff.getNano()%1000!=0
                    || referenceFingerprint==null || !referenceFingerprint.matches("[a-f0-9]{64}")
                    || calendarFingerprint==null || !calendarFingerprint.matches("[a-f0-9]{64}"))
                throw new HistoricalDataException(HistoricalDataException.Reason.INVALID_REQUEST);
            new HistoricalMarketDataProvider.Batch(bars,source,sourceVersion,observedAt);
            Instant previous=null;
            for(var b:bars) {
                if(!window.contains(b) || b.endTime().isAfter(closedBarCutoff)
                        || (previous!=null && !b.startTime().isAfter(previous)))
                    throw new HistoricalDataException(HistoricalDataException.Reason.INVALID_BAR);
                previous=b.startTime();
            }
        }
    }
    record Saved(UUID chunkId, int inserted, int existing, String contentHash) { }
    record Query(HistoricalWindow window, Instant decisionCutoff, Instant datasetCutoff) {
        public Query {
            Objects.requireNonNull(window);
            if(decisionCutoff==null || datasetCutoff==null || datasetCutoff.getNano()%1000!=0)
                throw new HistoricalDataException(HistoricalDataException.Reason.INVALID_REQUEST);
        }
    }
    record Dataset(Query query, List<HistoricalBar> bars, List<UUID> provenanceChunks, String contentHash) {
        public Dataset { bars=List.copyOf(bars); provenanceChunks=List.copyOf(provenanceChunks); }
    }
}
