package com.kitehybrid.platform.historical.application;

import com.kitehybrid.platform.historical.domain.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Explicit synchronous research acquisition. Each complete session commits separately; no retry. */
public final class HistoricalCorpusAcquisition {
    private final HistoricalDataIngestionService ingestion;
    private final HistoricalBarRepository repository;
    private final Clock clock;
    public HistoricalCorpusAcquisition(HistoricalDataIngestionService ingestion,
                                       HistoricalBarRepository repository, Clock clock) {
        this.ingestion=Objects.requireNonNull(ingestion);
        this.repository=Objects.requireNonNull(repository);this.clock=Objects.requireNonNull(clock);
    }
    public record Session(HistoricalCorpusPlan.Chunk chunk, HistoricalBarRepository.Dataset dataset,
                          boolean reused) { }
    public record Result(String planFingerprint, List<Session> sessions, int providerCalls,
                         int inserted, String contentFingerprint) {
        public Result { sessions=List.copyOf(sessions); }
    }
    public Result acquire(HistoricalCorpusPlan plan) {
        var chunks=plan.chunks(); // Complete, validated plan before any fetch.
        if(chunks.getLast().window().to().isAfter(clock.instant()))
            throw new IllegalArgumentException("CORPUS_NOT_COMPLETED");
        var sessions=new ArrayList<Session>(); var bars=new ArrayList<HistoricalBar>();
        int calls=0,inserted=0;
        for(var chunk:chunks) {
            var query=new HistoricalBarRepository.Query(chunk.window(),chunk.window().to(),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
            var stored=repository.query(query); boolean reused=!stored.bars().isEmpty();
            if(!reused) {
                if(calls>=plan.maximumRequests()) throw new IllegalStateException("CORPUS_REQUEST_BUDGET_EXCEEDED");
                var result=ingestion.ingest(chunk.window(),chunk.window().to(),chunk.calendar());
                calls+=result.providerCalls();
                inserted+=result.chunks().stream().mapToInt(HistoricalBarRepository.Saved::inserted).sum();
                query=new HistoricalBarRepository.Query(chunk.window(),chunk.window().to(),
                    clock.instant().truncatedTo(ChronoUnit.MICROS));
                stored=repository.query(query);
            }
            // Partial persisted evidence is never silently skipped/repaired or certified.
            var quality=HistoricalQuality.analyze(chunk.window(),chunk.window().to(),stored.bars(),chunk.calendar());
            if(!quality.complete() || stored.bars().size()!=chunk.expectedBars() || stored.provenanceChunks().isEmpty()
                    || !stored.contentHash().equals(HistoricalFingerprint.bars(stored.bars())))
                throw new IllegalStateException("CORPUS_SESSION_NOT_CERTIFIED");
            for(var id:stored.provenanceChunks()) {
                var evidence=repository.evidence(id).orElseThrow();
                if(!evidence.calendarFingerprint().equals(chunk.calendar().fingerprint()))
                    throw new IllegalStateException("CORPUS_CALENDAR_CONFLICT");
                if(!evidence.window().equals(chunk.window()) || !evidence.contentHash().equals(stored.contentHash()))
                    throw new HistoricalDataException(HistoricalDataException.Reason.CONFLICT);
            }
            repository.replay(stored);
            sessions.add(new Session(chunk,stored,reused));bars.addAll(stored.bars());
        }
        return new Result(plan.fingerprint(),sessions,calls,inserted,HistoricalFingerprint.bars(bars));
    }
}
