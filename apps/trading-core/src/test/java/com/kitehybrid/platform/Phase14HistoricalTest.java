package com.kitehybrid.platform;

import com.kitehybrid.platform.historical.application.*;
import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.instrument.application.HistoricalUniverseResolver;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase14HistoricalTest {
    final Instant now=Instant.parse("2026-10-12T12:00:00Z");
    Instrument instrument(String token,String symbol) {
        return Instrument.create(new BrokerInstrumentId("KITE",token),symbol,"NSE","CASH",InstrumentType.CASH,
            Optional.empty(),Optional.empty(),new BigDecimal("0.05"),1);
    }
    @Test void resolutionPreservesAmpersandDeduplicatesAndRejectsAmbiguityStalenessAndTokenReuse() {
        var resolver=new HistoricalUniverseResolver();var stock=instrument("123","M&M");
        var symbol=new HistoricalUniverseResolver.Symbol("NSE","M&M");
        var universe=List.of(symbol,new HistoricalUniverseResolver.Symbol(" nse "," m&m "));
        var ok=resolver.resolve(universe,List.of(stock),now,"a".repeat(64),now,List.of(),Set.of());
        assertEquals(1,ok.size());assertEquals(HistoricalUniverseResolver.Status.RESOLVED,ok.getFirst().status());
        assertEquals("M&M",ok.getFirst().requested().symbol());
        assertEquals(HistoricalUniverseResolver.Status.AMBIGUOUS,resolver.resolve(universe,List.of(stock,stock),now,
            "a".repeat(64),now,List.of(),Set.of()).getFirst().status());
        assertEquals("STALE_OR_FUTURE_MASTER",resolver.resolve(universe,List.of(stock),now.minusSeconds(86401),
            "a".repeat(64),now,List.of(),Set.of()).getFirst().reason());
        assertEquals("TOKEN_REUSE_OR_MAPPING_CHANGE",resolver.resolve(universe,List.of(stock),now,"a".repeat(64),now,
            List.of(instrument("123","OTHER")),Set.of()).getFirst().reason());
        assertEquals(HistoricalUniverseResolver.Status.INACTIVE,resolver.resolve(universe,List.of(stock),now,
            "a".repeat(64),now,List.of(),Set.of(symbol)).getFirst().status());
        assertEquals(HistoricalUniverseResolver.Status.UNRESOLVED,resolver.resolve(universe,List.of(),now,
            "a".repeat(64),now,List.of(),Set.of()).getFirst().status());
        var index=Instrument.create(new BrokerInstrumentId("KITE","123"),"M&M","NSE","INDICES",InstrumentType.INDEX,
            Optional.empty(),Optional.empty(),BigDecimal.ZERO,0);
        assertEquals(HistoricalUniverseResolver.Status.NON_EQUITY,resolver.resolve(universe,List.of(index),now,
            "a".repeat(64),now,List.of(),Set.of()).getFirst().status());
        assertEquals("INVALID_KITE_TOKEN_EVIDENCE",resolver.resolve(universe,List.of(instrument("0","M&M")),now,
            "a".repeat(64),now,List.of(),Set.of()).getFirst().reason());
    }
    @Test void annualPlanSplitsBySessionDeduplicatesAndHasZeroRetries() {
        var first=LocalDate.of(2025,10,12);var days=new HashMap<LocalDate,TradingCalendar.Day>();
        for(var day=first;day.isBefore(first.plusYears(1));day=day.plusDays(1))
            days.put(day,new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,List.of(
                new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(15,30)))));
        var calendar=new TradingCalendar("synthetic","FIXTURE_NOT_EXCHANGE_CALENDAR",days);
        var id=instrument("123","M&M").id();
        var plan=FiveMinuteAcquisitionPlan.create(List.of(id,id),first,first.plusYears(1),calendar);
        assertEquals(365,plan.requests());assertEquals(0,plan.retryBudget());
        assertEquals(Duration.ofSeconds(364),plan.minimumPacedTime());
        assertTrue(plan.members().getFirst().chunks().stream().allMatch(c->c.expectedBars()==75));
        assertEquals(Instant.parse("2025-10-12T03:45:00Z"),plan.members().getFirst().chunks().getFirst().window().from());
        days.remove(first);var unknown=new TradingCalendar("synthetic","fixture",days);
        assertThrows(IllegalArgumentException.class,()->FiveMinuteAcquisitionPlan.create(List.of(id),first,first.plusYears(1),unknown));
    }
    @Test void fiveMinuteAlignmentAndCalendarAnomaliesAreExplicit() {
        var id=instrument("123","M&M").id();var start=Instant.parse("2026-10-12T03:45:00Z");
        assertThrows(HistoricalDataException.class,()->new HistoricalBar(id,start.plusSeconds(60),BarInterval.FIVE_MINUTE,
            BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,0,Optional.empty()));
        var window=new HistoricalWindow(id,BarInterval.FIVE_MINUTE,start,start.plusSeconds(600));
        var calendar=new TradingCalendar("synthetic","fixture",Map.of(LocalDate.of(2026,10,12),
            new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,List.of(
                new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(9,25))))));
        var bar=new HistoricalBar(id,start,BarInterval.FIVE_MINUTE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,
            BigDecimal.ONE,0,Optional.empty());
        assertEquals(1,HistoricalQuality.analyze(window,window.to(),List.of(bar),calendar).missingBars());
        assertEquals(start.plusSeconds(300),bar.endTime());
    }
    @Test void fiveMinuteRejectsDuplicatesOutOfOrderAndMissingBeforePersistence() {
        var stock=instrument("123","M&M");var registry=new InMemoryInstrumentRegistry();
        registry.replace(List.of(stock),now);
        var start=Instant.parse("2026-10-12T03:45:00Z");
        var first=new HistoricalBar(stock.id(),start,BarInterval.FIVE_MINUTE,BigDecimal.ONE,BigDecimal.ONE,
            BigDecimal.ONE,BigDecimal.ONE,1,Optional.empty());
        var second=new HistoricalBar(stock.id(),start.plusSeconds(300),BarInterval.FIVE_MINUTE,BigDecimal.ONE,
            BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,1,Optional.empty());
        var window=new HistoricalWindow(stock.id(),BarInterval.FIVE_MINUTE,start,start.plusSeconds(600));
        var calendar=new TradingCalendar("fixture","synthetic",Map.of(LocalDate.of(2026,10,12),
            new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,List.of(
                new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(9,25))))));
        for(var bars:List.of(List.of(first,first,second),List.of(second,first),List.of(first))) {
            var repo=mock(HistoricalBarRepository.class);
            HistoricalMarketDataProvider provider=w->new HistoricalMarketDataProvider.Batch(bars,"FAKE","v1",now);
            var service=new HistoricalDataIngestionService(registry,provider,repo,Clock.fixed(now,ZoneOffset.UTC));
            assertThrows(HistoricalDataException.class,()->service.ingest(window,window.to(),calendar));
            verifyNoInteractions(repo);
        }
    }
}
