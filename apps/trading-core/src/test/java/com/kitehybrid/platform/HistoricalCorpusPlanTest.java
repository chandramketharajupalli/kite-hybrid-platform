package com.kitehybrid.platform;

import com.kitehybrid.platform.historical.application.*;
import com.kitehybrid.platform.historical.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoricalCorpusPlanTest {
    static final LocalDate FIRST=LocalDate.of(2026,7,30);
    static final TradingCalendar.Day NORMAL=new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,
        List.of(new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(15,30))));
    static TradingCalendar calendar(Map<LocalDate,TradingCalendar.Day> days) {
        return new TradingCalendar("synthetic-v1","synthetic-only",days);
    }
    @Test void planIsDeterministicExplicitAndDerivesCountsIncludingShortSession() {
        var days=Map.of(FIRST,NORMAL,FIRST.plusDays(1),new TradingCalendar.Day(
            TradingCalendar.Status.EXPECTED_SESSION,List.of(new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(12,0)))),
            FIRST.plusDays(2),new TradingCalendar.Day(TradingCalendar.Status.NON_TRADING_DAY,List.of()));
        var plan=new HistoricalCorpusPlan(HistoricalDataTest.SBIN.id(),FIRST,FIRST.plusDays(3),calendar(days),2);
        assertEquals(2,plan.chunks().size()); assertEquals(375,plan.chunks().getFirst().expectedBars());
        assertEquals(165,plan.chunks().getLast().expectedBars());
        assertEquals(Instant.parse("2026-07-30T03:45:00Z"),plan.chunks().getFirst().window().from());
        assertEquals(plan.fingerprint(),new HistoricalCorpusPlan(plan.instrumentId(),FIRST,FIRST.plusDays(3),calendar(days),2).fingerprint());
        assertThrows(UnsupportedOperationException.class,()->plan.chunks().clear());
    }
    @Test void unknownWeekendBudgetAndOversizedRangeFailBeforeNetwork() {
        var known=calendar(Map.of(FIRST,NORMAL));
        assertThrows(IllegalArgumentException.class,()->new HistoricalCorpusPlan(HistoricalDataTest.SBIN.id(),FIRST,FIRST.plusDays(3),known,3));
        assertThrows(IllegalArgumentException.class,()->new HistoricalCorpusPlan(HistoricalDataTest.SBIN.id(),FIRST,FIRST.plusDays(367),known,366));
        var two=calendar(Map.of(FIRST,NORMAL,FIRST.plusDays(1),NORMAL));
        assertThrows(IllegalArgumentException.class,()->new HistoricalCorpusPlan(HistoricalDataTest.SBIN.id(),FIRST,FIRST.plusDays(2),two,1));
    }
    @Test void missingProvenanceOrChangedHistoricalEvidenceCannotBeReused() {
        var date=HistoricalDataTest.START.atZone(TradingCalendar.NSE_ZONE).toLocalDate();
        var day=new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,List.of(
            new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(9,18))));
        var plan=new HistoricalCorpusPlan(HistoricalDataTest.SBIN.id(),date,date.plusDays(1),calendar(Map.of(date,day)),1);
        var bars=List.of(HistoricalDataTest.bar(0,"100"),HistoricalDataTest.bar(1,"100"),HistoricalDataTest.bar(2,"100"));
        var repo=mock(HistoricalBarRepository.class);var ingestion=mock(HistoricalDataIngestionService.class);
        var clock=Clock.fixed(HistoricalDataTest.NOW,ZoneOffset.UTC);
        when(repo.query(any())).thenAnswer(inv->new HistoricalBarRepository.Dataset(inv.getArgument(0),bars,List.of(),HistoricalFingerprint.bars(bars)));
        var acquisition=new HistoricalCorpusAcquisition(ingestion,repo,clock);
        assertThrows(IllegalStateException.class,()->acquisition.acquire(plan));
        var id=new UUID(0,1);var chunk=plan.chunks().getFirst();
        when(repo.query(any())).thenAnswer(inv->new HistoricalBarRepository.Dataset(inv.getArgument(0),bars,List.of(id),HistoricalFingerprint.bars(bars)));
        when(repo.evidence(id)).thenReturn(Optional.of(new HistoricalBarRepository.Evidence(id,id,chunk.window(),chunk.window(),
            "FAKE","v1",clock.instant(),clock.instant(),chunk.window().to(),"a".repeat(64),chunk.calendar().fingerprint(),"b".repeat(64),3,3)));
        assertEquals(HistoricalDataException.Reason.CONFLICT,
            assertThrows(HistoricalDataException.class,()->acquisition.acquire(plan)).reason());
        verifyNoInteractions(ingestion);
    }
}
