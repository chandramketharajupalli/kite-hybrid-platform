package com.kitehybrid.platform.historical.application;

import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.kitehybrid.platform.historical.domain.HistoricalDataException.Reason.*;

/** Explicit synchronous research operation. Provider I/O always precedes the repository transaction. */
public final class HistoricalDataIngestionService {
    private final InstrumentRegistry registry;
    private final HistoricalMarketDataProvider provider;
    private final HistoricalBarRepository repository;
    private final Clock clock;
    public HistoricalDataIngestionService(InstrumentRegistry registry, HistoricalMarketDataProvider provider,
                                          HistoricalBarRepository repository, Clock clock) {
        this.registry=Objects.requireNonNull(registry); this.provider=Objects.requireNonNull(provider);
        this.repository=Objects.requireNonNull(repository); this.clock=Objects.requireNonNull(clock);
    }
    public record Result(UUID runId, HistoricalWindow request, Instant cutoff, int providerCalls,
                         int received, int accepted, int duplicates, int incompleteExcluded,
                         List<HistoricalBarRepository.Saved> chunks, HistoricalQuality quality,
                         Optional<Instant> first, Optional<Instant> last, String contentHash) {
        public Result { chunks=List.copyOf(chunks); }
    }
    public Result ingest(HistoricalWindow request, Instant requestedCutoff, TradingCalendar calendar) {
        Objects.requireNonNull(request); Objects.requireNonNull(calendar);
        var began=clock.instant().truncatedTo(ChronoUnit.MICROS);
        if(requestedCutoff==null || requestedCutoff.isAfter(began) || requestedCutoff.getNano()%1000!=0)
            throw new HistoricalDataException(INVALID_REQUEST);
        var snapshot=registry.snapshot(); var instrument=snapshot.byId().get(request.instrumentId());
        if(instrument==null || instrument.type()!=InstrumentType.CASH || !instrument.exchange().equals("NSE")
                || snapshot.refreshedAt().isAfter(began) || Duration.between(snapshot.refreshedAt(),began).compareTo(Duration.ofDays(1))>0)
            throw new HistoricalDataException(REFERENCE_UNAVAILABLE);
        // Hash captures the mapping used without persisting or printing the broker token.
        var reference=HistoricalFingerprint.sha256(instrument.toString()+"|"+snapshot.refreshedAt());
        var run=UUID.randomUUID(); var saved=new ArrayList<HistoricalBarRepository.Saved>();
        var all=new ArrayList<HistoricalBar>(); int received=0,duplicates=0,incomplete=0,calls=0;
        for(var chunk:request.chunks()) {
            HistoricalMarketDataProvider.Batch batch;
            try { batch=Objects.requireNonNull(provider.fetch(chunk)); }
            catch(HistoricalDataException safe) { throw safe; }
            catch(RuntimeException unavailable) { throw new HistoricalDataException(TRANSIENT_PROVIDER); }
            calls++;
            var now=clock.instant().truncatedTo(ChronoUnit.MICROS);
            if(batch.observedAt().isBefore(began) || batch.observedAt().isAfter(now)
                    || !registry.findById(instrument.id()).filter(instrument::equals).isPresent())
                throw new HistoricalDataException(REFERENCE_UNAVAILABLE);
            var sorted=new TreeMap<Instant,HistoricalBar>(); received+=batch.bars().size();
            for(var bar:batch.bars()) {
                if(!chunk.contains(bar)) throw new HistoricalDataException(INVALID_RESPONSE);
                var old=sorted.putIfAbsent(bar.startTime(),bar);
                if(old!=null) { if(!old.equals(bar)) throw new HistoricalDataException(CONFLICT); duplicates++; }
            }
            var closed=new ArrayList<HistoricalBar>();
            for(var b:sorted.values()) { if(b.endTime().isAfter(requestedCutoff)) incomplete++; else closed.add(b); }
            var evidence=new HistoricalBarRepository.Chunk(run,request,chunk,closed,batch.source(),batch.sourceVersion(),
                    batch.observedAt(),now,requestedCutoff,reference,calendar.fingerprint());
            saved.add(repository.append(evidence)); all.addAll(closed);
        }
        return new Result(run,request,requestedCutoff,calls,received,all.size(),duplicates,incomplete,saved,
                HistoricalQuality.analyze(request,requestedCutoff,all,calendar),
                all.isEmpty()?Optional.empty():Optional.of(all.getFirst().startTime()),
                all.isEmpty()?Optional.empty():Optional.of(all.getLast().startTime()),HistoricalFingerprint.bars(all));
    }
}
