package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.historical.application.HistoricalMarketDataProvider;
import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.kitehybrid.platform.historical.domain.HistoricalDataException.Reason.*;

/** Explicit read-only adapter, never auto-wired. Real normalization awaits authoritative timestamp semantics. */
public final class KiteHistoricalAdapter implements HistoricalMarketDataProvider {
    private final KiteRestTransport transport;
    private final InstrumentRegistry registry;
    private final Clock clock;
    private final boolean confirmedStartConvention;
    private final Runnable pacing;
    private final ObjectMapper json=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public static KiteHistoricalAdapter production(KiteSession session, InstrumentRegistry registry, Clock clock) {
        return new KiteHistoricalAdapter(KiteRestTransport.production(session),registry,clock,false,HistoricalPacing::await);
    }
    /** Package-private synthetic seam: not an operational switch for unverified provider semantics. */
    KiteHistoricalAdapter(KiteRestTransport transport, InstrumentRegistry registry, Clock clock,
                          boolean confirmedStartConvention, Runnable pacing) {
        this.transport=Objects.requireNonNull(transport); this.registry=Objects.requireNonNull(registry);
        this.clock=Objects.requireNonNull(clock); this.confirmedStartConvention=confirmedStartConvention;
        this.pacing=Objects.requireNonNull(pacing);
    }
    @Override public Batch fetch(HistoricalWindow window) {
        if(!confirmedStartConvention) throw new HistoricalDataException(TIMESTAMP_SEMANTICS_UNVERIFIED);
        if(Duration.between(window.from(),window.to()).compareTo(Duration.ofDays(1))>0)
            throw new HistoricalDataException(INVALID_REQUEST);
        var snapshot=registry.snapshot(); var instrument=snapshot.byId().get(window.instrumentId());
        if(instrument==null || instrument.type()!=InstrumentType.CASH || !instrument.exchange().equals("NSE")
                || !instrument.brokerId().broker().equals("KITE") || snapshot.refreshedAt().isAfter(clock.instant())
                || Duration.between(snapshot.refreshedAt(),clock.instant()).compareTo(Duration.ofDays(1))>0)
            throw new HistoricalDataException(REFERENCE_UNAVAILABLE);
        try {
            pacing.run();
            String body=transport.historicalMinute(instrument.brokerId().value(),window.from(),window.to());
            if(!registry.findById(instrument.id()).filter(instrument::equals).isPresent())
                throw new HistoricalDataException(REFERENCE_UNAVAILABLE);
            var root=json.readTree(body); var candles=root.path("data").path("candles");
            if(!root.path("status").asText().equals("success") || !candles.isArray() || candles.size()>3000)
                throw new HistoricalDataException(INVALID_RESPONSE);
            var bars=new ArrayList<HistoricalBar>();
            for(var row:candles) {
                if(!row.isArray() || row.size()!=6 || !row.get(0).isTextual())
                    throw new HistoricalDataException(INVALID_RESPONSE);
                var offset=OffsetDateTime.parse(row.get(0).textValue(),DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXX",Locale.ROOT)
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT));
                if(!offset.getOffset().equals(ZoneOffset.ofHoursMinutes(5,30))) throw new HistoricalDataException(INVALID_RESPONSE);
                for(int n=1;n<=4;n++) if(!row.get(n).isNumber()) throw new HistoricalDataException(INVALID_RESPONSE);
                if(!row.get(5).isIntegralNumber() || !row.get(5).canConvertToLong()) throw new HistoricalDataException(INVALID_RESPONSE);
                var bar=new HistoricalBar(window.instrumentId(),offset.toInstant(),window.interval(),
                        row.get(1).decimalValue(),row.get(2).decimalValue(),row.get(3).decimalValue(),row.get(4).decimalValue(),
                        row.get(5).longValue(),Optional.empty());
                // The documented example includes to; only that exact endpoint overlap is discarded.
                if(bar.startTime().equals(window.to())) continue;
                if(!window.contains(bar)) throw new HistoricalDataException(INVALID_RESPONSE);
                bars.add(bar);
            }
            return new Batch(bars,"KITE","v3-minute-unadjusted-by-platform",clock.instant().truncatedTo(ChronoUnit.MICROS));
        } catch(HistoricalDataException safe) { throw safe; }
        catch(BrokerReadException safe) {
            throw new HistoricalDataException(safe.category()==BrokerReadException.Category.AUTHENTICATION?AUTHENTICATION:
                    safe.httpStatus()==429?RATE_LIMITED:safe.category()==BrokerReadException.Category.TRANSPORT
                    || safe.httpStatus()>=500?TRANSIENT_PROVIDER:INVALID_RESPONSE);
        } catch(Exception malformed) { throw new HistoricalDataException(INVALID_RESPONSE); }
    }
    /** Process-wide, one request start/second; caller owns cross-process credential coordination. */
    static final class HistoricalPacing {
        private static long last; private static boolean started;
        static synchronized void await() {
            long remaining=started?1_000_000_000L-(System.nanoTime()-last):0;
            try { if(remaining>0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(remaining); }
            catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new HistoricalDataException(TRANSIENT_PROVIDER); }
            last=System.nanoTime(); started=true;
        }
    }
}
