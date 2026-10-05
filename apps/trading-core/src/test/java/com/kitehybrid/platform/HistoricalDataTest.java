package com.kitehybrid.platform;

import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.historical.application.*;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoricalDataTest {
    static final Instant START=Instant.parse("2026-10-05T03:45:00Z"), NOW=START.plusSeconds(600);
    static final Instrument SBIN=Instrument.create(new BrokerInstrumentId("KITE","779521"),"SBIN","NSE","CASH",
            InstrumentType.CASH,Optional.empty(),Optional.empty(),new BigDecimal("0.05"),1);
    static HistoricalBar bar(int minute,String close) {
        return new HistoricalBar(SBIN.id(),START.plusSeconds(minute*60L),BarInterval.MINUTE,
                new BigDecimal("100.00"),new BigDecimal("101"),new BigDecimal("99"),new BigDecimal(close),5,Optional.empty());
    }
    static HistoricalWindow window(int minutes) { return new HistoricalWindow(SBIN.id(),BarInterval.MINUTE,START,START.plusSeconds(minutes*60L)); }
    static TradingCalendar calendar() {
        return new TradingCalendar("synthetic-v1","synthetic",Map.of(LocalDate.of(2026,10,5),
                new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,List.of(
                        new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(15,30))))));
    }
    @Test void decimalsCanonicalizeAndBarMeansStartInclusiveEndExclusive() {
        assertEquals(bar(0,"100.5"),bar(0,"100.5000"));
        assertEquals(START.plusSeconds(60),bar(0,"100.5").endTime());
        assertEquals(LocalTime.of(9,15),START.atZone(TradingCalendar.NSE_ZONE).toLocalTime());
        assertEquals(HistoricalFingerprint.bars(List.of(bar(0,"100.5"))),HistoricalFingerprint.bars(List.of(bar(0,"100.500"))));
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","102","98","1e100","100.00000000001"})
    void rejectsImpossibleOrUnrepresentablePrices(String close) {
        assertThrows(HistoricalDataException.class,()->bar(0,close));
    }
    @Test void rejectsNegativeVolumeOiAndMisalignedTimestamp() {
        var b=bar(0,"100");
        assertThrows(HistoricalDataException.class,()->new HistoricalBar(b.instrumentId(),b.startTime(),b.interval(),b.open(),b.high(),b.low(),b.close(),-1,b.openInterest()));
        assertThrows(HistoricalDataException.class,()->new HistoricalBar(b.instrumentId(),b.startTime(),b.interval(),b.open(),b.high(),b.low(),b.close(),1,Optional.of(BigDecimal.ONE.negate())));
        assertThrows(HistoricalDataException.class,()->new HistoricalBar(b.instrumentId(),b.startTime().plusSeconds(1),b.interval(),b.open(),b.high(),b.low(),b.close(),1,b.openInterest()));
    }
    @Test void gapsUseOnlyConfirmedSessionsAndClosedRequestedMinutes() {
        var q=HistoricalQuality.analyze(window(5),START.plusSeconds(240),List.of(bar(0,"100"),bar(1,"100"),bar(3,"100")),calendar());
        assertEquals(4,q.expectedBars()); assertEquals(List.of(START.plusSeconds(120)),q.missingSamples());
        assertFalse(q.complete()); assertEquals(0,q.unexpectedBars());
        var partial=new HistoricalWindow(SBIN.id(),BarInterval.MINUTE,START.plusSeconds(60),START.plusSeconds(120));
        assertTrue(HistoricalQuality.analyze(partial,NOW,List.of(bar(1,"100")),calendar()).complete());
    }
    @Test void unknownDatesNeverBecomeConfirmedGapsAndNonTradingDatesHaveNoExpectedBars() {
        var unknown=new TradingCalendar("v1","synthetic",Map.of());
        var quality=HistoricalQuality.analyze(window(5),NOW,List.of(),unknown);
        assertEquals(0,quality.missingBars()); assertEquals(List.of(LocalDate.of(2026,10,5)),quality.unknownDates());
        var holiday=new TradingCalendar("v2","synthetic",Map.of(LocalDate.of(2026,10,5),
                new TradingCalendar.Day(TradingCalendar.Status.NON_TRADING_DAY,List.of())));
        assertTrue(HistoricalQuality.analyze(window(5),NOW,List.of(),holiday).complete());
        assertEquals(1,HistoricalQuality.analyze(window(5),NOW,List.of(bar(0,"100")),holiday).unexpectedBars());
    }
    @Test void specialSessionAndCalendarContentFingerprintAreExplicit() {
        var special=new TradingCalendar("special","synthetic",Map.of(LocalDate.of(2026,10,5),
                new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,List.of(
                        new TradingCalendar.Session(LocalTime.of(9,17),LocalTime.of(9,19))))));
        var q=HistoricalQuality.analyze(window(10),NOW,List.of(bar(2,"100"),bar(3,"100")),special);
        assertTrue(q.complete()); assertEquals(2,q.expectedBars());
        assertNotEquals(calendar().fingerprint(),special.fingerprint());
    }
    @Test void chunkWindowsHaveNoGapOrOverlapAndRejectUnboundedRequests() {
        var request=new HistoricalWindow(SBIN.id(),BarInterval.MINUTE,START,START.plus(Duration.ofDays(3)).plusSeconds(60));
        assertEquals(4,request.chunks().size());
        for(int n=1;n<4;n++) assertEquals(request.chunks().get(n-1).to(),request.chunks().get(n).from());
        assertThrows(HistoricalDataException.class,()->new HistoricalWindow(SBIN.id(),BarInterval.MINUTE,START,START));
        assertThrows(HistoricalDataException.class,()->new HistoricalWindow(SBIN.id(),BarInterval.MINUTE,START,START.plus(Duration.ofDays(32))));
    }
    @Test void ingestionSortsDeduplicatesAndExcludesIncompleteWithFixedClock() {
        var registry=mock(InstrumentRegistry.class); when(registry.snapshot()).thenReturn(InstrumentSnapshot.validated(List.of(SBIN),1,NOW));
        when(registry.findById(SBIN.id())).thenReturn(Optional.of(SBIN));
        var repo=mock(HistoricalBarRepository.class);
        when(repo.append(any())).thenAnswer(inv->{var c=(HistoricalBarRepository.Chunk)inv.getArgument(0);return new HistoricalBarRepository.Saved(UUID.randomUUID(),c.bars().size(),0,HistoricalFingerprint.bars(c.bars()));});
        HistoricalMarketDataProvider provider=w->new HistoricalMarketDataProvider.Batch(List.of(bar(2,"100"),bar(0,"100"),bar(1,"100"),bar(0,"100.0")),"FAKE","v1",NOW);
        var result=new HistoricalDataIngestionService(registry,provider,repo,Clock.fixed(NOW,ZoneOffset.UTC))
                .ingest(window(3),START.plusSeconds(120),calendar());
        assertEquals(2,result.accepted()); assertEquals(1,result.duplicates()); assertEquals(1,result.incompleteExcluded());
        assertTrue(result.quality().complete());
        var captor=org.mockito.ArgumentCaptor.forClass(HistoricalBarRepository.Chunk.class); verify(repo).append(captor.capture());
        assertEquals(List.of(bar(0,"100"),bar(1,"100")),captor.getValue().bars());
    }
    @Test void differingDuplicateFailsBeforePersistence() {
        var registry=mock(InstrumentRegistry.class);when(registry.snapshot()).thenReturn(InstrumentSnapshot.validated(List.of(SBIN),1,NOW));
        when(registry.findById(SBIN.id())).thenReturn(Optional.of(SBIN)); var repo=mock(HistoricalBarRepository.class);
        HistoricalMarketDataProvider provider=w->new HistoricalMarketDataProvider.Batch(List.of(bar(0,"100.5"),bar(0,"100.7")),"FAKE","v1",NOW);
        var service=new HistoricalDataIngestionService(registry,provider,repo,Clock.fixed(NOW,ZoneOffset.UTC));
        assertEquals(HistoricalDataException.Reason.CONFLICT,assertThrows(HistoricalDataException.class,()->service.ingest(window(3),NOW,calendar())).reason());
        verifyNoInteractions(repo);
    }
}
