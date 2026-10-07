package com.kitehybrid.platform;

import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.historical.application.*;
import com.kitehybrid.platform.historical.infrastructure.PostgresHistoricalBarRepository;
import com.kitehybrid.platform.historical.infrastructure.HistoricalResearchExporter;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers @Isolated
class PostgresHistoricalBarRepositoryTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres","-c","fsync=off","-c","timezone=UTC");
    static TimeZone original;
    static final Instant START=Instant.parse("2026-10-01T03:45:00Z"),NOW=START.plus(Duration.ofDays(5));
    static final Instrument SBIN=Instrument.create(new BrokerInstrumentId("KITE","123"),"SBIN","NSE","CASH",
            InstrumentType.CASH,Optional.empty(),Optional.empty(),new BigDecimal("0.05"),1);
    JdbcTemplate jdbc; PostgresHistoricalBarRepository repo;
    @BeforeAll static void utc() { original=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC")); }
    @AfterAll static void restore() { TimeZone.setDefault(original); }
    @BeforeEach void setup() {
        assertNotEquals("jdbc:postgresql://localhost:5432/trading",postgres.getJdbcUrl());
        var source=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration","classpath:db/historical").load().migrate();
        jdbc=new JdbcTemplate(source);jdbc.execute("TRUNCATE trading.historical_bars,trading.historical_ingestion_chunks");
        repo=new PostgresHistoricalBarRepository(jdbc);
    }
    HistoricalBar bar(Instant start,String close) {
        return new HistoricalBar(SBIN.id(),start,BarInterval.MINUTE,new BigDecimal("100"),new BigDecimal("101"),new BigDecimal("99"),
                new BigDecimal(close),Long.MAX_VALUE,Optional.of(BigDecimal.ZERO));
    }
    HistoricalWindow window() { return new HistoricalWindow(SBIN.id(),BarInterval.MINUTE,START,START.plusSeconds(180)); }
    HistoricalBarRepository.Chunk chunk(List<HistoricalBar> bars,Instant time) {
        return new HistoricalBarRepository.Chunk(UUID.randomUUID(),window(),window(),bars,"FAKE","v1",time,time,time,"a".repeat(64),"b".repeat(64));
    }
    HistoricalBarRepository.Dataset query(Instant decision,Instant dataset) {
        return repo.query(new HistoricalBarRepository.Query(window(),decision,dataset));
    }
    @Test void corpusRestartReusesCommittedSessionAndNeverRetriesFailedFetch() {
        var day=new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,List.of(
            new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(9,18))));
        var first=START.atZone(TradingCalendar.NSE_ZONE).toLocalDate();
        var calendar=new TradingCalendar("corpus-test","synthetic",Map.of(first,day,first.plusDays(1),day));
        var plan=new HistoricalCorpusPlan(SBIN.id(),first,first.plusDays(2),calendar,2);
        var registry=mock(InstrumentRegistry.class);
        when(registry.snapshot()).thenReturn(InstrumentSnapshot.validated(List.of(SBIN),1,NOW));
        when(registry.findById(SBIN.id())).thenReturn(Optional.of(SBIN));
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        HistoricalMarketDataProvider provider=w->{
            if(calls.incrementAndGet()==2) throw new HistoricalDataException(HistoricalDataException.Reason.RATE_LIMITED);
            return new HistoricalMarketDataProvider.Batch(List.of(bar(w.from(),"100"),bar(w.from().plusSeconds(60),"100"),
                bar(w.from().plusSeconds(120),"100")),"FAKE","v1",NOW);
        };
        var clock=Clock.fixed(NOW,ZoneOffset.UTC);
        var acquisition=new HistoricalCorpusAcquisition(new HistoricalDataIngestionService(registry,provider,repo,clock),repo,clock);
        assertThrows(HistoricalDataException.class,()->acquisition.acquire(plan));
        assertEquals(2,calls.get()); assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM trading.historical_bars",Integer.class));
        var resumed=acquisition.acquire(plan);
        assertEquals(3,calls.get());assertEquals(1,resumed.providerCalls()); assertEquals(3,resumed.inserted());
        assertTrue(resumed.sessions().getFirst().reused());
        var before=jdbc.queryForList("SELECT * FROM trading.historical_bars ORDER BY start_time");
        var replay=acquisition.acquire(plan);
        assertEquals(3,calls.get());assertEquals(0,replay.providerCalls());assertEquals(0,replay.inserted());
        assertEquals(resumed.contentFingerprint(),replay.contentFingerprint());
        assertEquals(before,jdbc.queryForList("SELECT * FROM trading.historical_bars ORDER BY start_time"));
    }
    @Test void multiInstrumentRestartAndReplayPreserveEachCommittedCorpus() {
        var other=Instrument.create(new BrokerInstrumentId("ZERODHA","456"),"HDFCBANK","NSE","CASH",
                InstrumentType.CASH,Optional.empty(),Optional.empty(),new BigDecimal("0.05"),1);
        var first=LocalDate.of(2026,2,2);
        var day=new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,List.of(
                new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(9,18))));
        var calendar=new TradingCalendar("multi-synthetic","synthetic",Map.of(first,day));
        var plan=new MultiInstrumentCorpusPlan("a".repeat(64),List.of(
                new HistoricalCorpusPlan(SBIN.id(),first,first.plusDays(1),calendar,1),
                new HistoricalCorpusPlan(other.id(),first,first.plusDays(1),calendar,1)),2);
        var registry=mock(InstrumentRegistry.class);
        when(registry.snapshot()).thenReturn(InstrumentSnapshot.validated(List.of(SBIN,other),1,NOW));
        when(registry.findById(SBIN.id())).thenReturn(Optional.of(SBIN));
        when(registry.findById(other.id())).thenReturn(Optional.of(other));
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        HistoricalMarketDataProvider provider=w->{
            if(calls.incrementAndGet()==2) throw new HistoricalDataException(HistoricalDataException.Reason.RATE_LIMITED);
            var bars=new ArrayList<HistoricalBar>();
            for(int n=0;n<3;n++) bars.add(new HistoricalBar(w.instrumentId(),w.from().plusSeconds(n*60L),
                    BarInterval.MINUTE,new BigDecimal("100"),new BigDecimal("101"),new BigDecimal("99"),
                    new BigDecimal("100"),100,Optional.empty()));
            return new HistoricalMarketDataProvider.Batch(bars,"FAKE","v1",NOW);
        };
        var clock=Clock.fixed(NOW,ZoneOffset.UTC);
        var single=new HistoricalCorpusAcquisition(
                new HistoricalDataIngestionService(registry,provider,repo,clock),repo,clock);
        var multi=new MultiInstrumentCorpusAcquisition(single);
        assertThrows(HistoricalDataException.class,()->multi.acquire(plan));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM trading.historical_bars",Integer.class));
        var resumed=multi.acquire(plan);
        assertEquals(1,resumed.providerCalls());assertEquals(3,resumed.inserted());
        var before=jdbc.queryForList("SELECT * FROM trading.historical_bars ORDER BY instrument_id,start_time");
        var replay=multi.acquire(plan);
        assertEquals(3,calls.get());assertEquals(0,replay.providerCalls());assertEquals(0,replay.inserted());
        assertEquals(resumed.aggregateFingerprint(),replay.aggregateFingerprint());
        assertEquals(resumed.members().stream().map(HistoricalCorpusAcquisition.Result::contentFingerprint).toList(),
                replay.members().stream().map(HistoricalCorpusAcquisition.Result::contentFingerprint).toList());
        assertEquals(before,jdbc.queryForList("SELECT * FROM trading.historical_bars ORDER BY instrument_id,start_time"));
    }
    @Test void migrationIsExplicitRepeatableAndDoesNotChangeTradingReadinessAtDefaultLocation() {
        jdbc.execute("CREATE DATABASE historical_default_scope");
        var defaultSource=new DriverManagerDataSource(postgres.getJdbcUrl().replace("/"+postgres.getDatabaseName(),"/historical_default_scope"),
                postgres.getUsername(),postgres.getPassword());
        var defaults=Flyway.configure().dataSource(defaultSource).locations("classpath:db/migration").load();
        defaults.migrate();defaults.validate();
        assertEquals("10",defaults.info().current().getVersion().toString());
        assertNull(new JdbcTemplate(defaultSource).queryForObject("SELECT to_regclass('trading.historical_bars')",String.class));
        var f=Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration","classpath:db/historical").load();
        f.validate();assertEquals(0,f.migrate().migrationsExecuted);assertEquals("11",f.info().current().getVersion().toString());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM trading.orders",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM trading.kite_access_tokens",Integer.class));
        assertTrue(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_indexes WHERE schemaname='trading' AND tablename='historical_bars' AND indexdef LIKE '%UNIQUE%instrument_id, bar_interval, start_time%')",Boolean.class));
    }
    @Test void replayPreservesValuesAndProvenanceWhileQueriesEnforceBothCutoffs() {
        var bars=List.of(bar(START,"100.5"),bar(START.plusSeconds(60),"100.6"),bar(START.plusSeconds(120),"100.7"));
        var first=repo.append(chunk(bars,NOW));assertEquals(3,first.inserted());
        var before=jdbc.queryForList("SELECT * FROM trading.historical_bars ORDER BY start_time");
        var replay=new PostgresHistoricalBarRepository(jdbc).append(chunk(bars,NOW.plusSeconds(60)));
        assertEquals(0,replay.inserted());assertEquals(3,replay.existing());assertEquals(first.contentHash(),replay.contentHash());
        assertEquals(before,jdbc.queryForList("SELECT * FROM trading.historical_bars ORDER BY start_time"));
        assertEquals(List.of(),query(NOW,NOW.minusSeconds(1)).bars());
        assertEquals(bars.subList(0,2),query(START.plusSeconds(120),NOW).bars());
        assertEquals(bars.subList(0,1),query(START.plusSeconds(119),NOW).bars());
        assertEquals(List.of(first.chunkId()),query(NOW,NOW).provenanceChunks());
        assertEquals(first.contentHash(),query(NOW,NOW).contentHash());
        assertEquals("FAKE",repo.evidence(first.chunkId()).orElseThrow().source());
        assertEquals(window(),repo.evidence(first.chunkId()).orElseThrow().request());
        assertEquals(query(NOW,NOW),repo.replay(query(NOW,NOW)));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM trading.historical_ingestion_chunks",Integer.class));
    }
    @Test void researchExportReplaysPinnedPostgresDataInReadOnlyTransaction() throws Exception {
        var calendar=new TradingCalendar("synthetic-v1","Synthetic export fixture",
                Map.of(LocalDate.of(2026,10,1),new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,
                        List.of(new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(9,18))))));
        var bars=List.of(bar(START,"100.5"),bar(START.plusSeconds(60),"100.6"),bar(START.plusSeconds(120),"100.7"));
        repo.append(new HistoricalBarRepository.Chunk(UUID.randomUUID(),window(),window(),bars,"SYNTHETIC","fixture-v1",
                NOW,NOW,NOW,"a".repeat(64),calendar.fingerprint()));
        var pinned=query(NOW,NOW);
        var before=jdbc.queryForList("SELECT * FROM trading.historical_bars ORDER BY start_time");
        var evidenceBefore=jdbc.queryForList("SELECT * FROM trading.historical_ingestion_chunks");
        var transaction=new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.setReadOnly(true);
        var exported=transaction.execute(status->{
            assertEquals("on",jdbc.queryForObject("SHOW transaction_read_only",String.class));
            return new HistoricalResearchExporter(repo).export(pinned,calendar,SBIN);
        });
        assertEquals(exported,new HistoricalResearchExporter(new PostgresHistoricalBarRepository(jdbc)).export(pinned,calendar,SBIN));
        var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(exported);
        assertEquals(pinned.contentHash(),json.get("content_fingerprint").asText());
        assertEquals("100.5",json.get("bars").get(0).get("close").asText());
        assertEquals(3,json.get("bars").size());
        assertEquals(before,jdbc.queryForList("SELECT * FROM trading.historical_bars ORDER BY start_time"));
        assertEquals(evidenceBefore,jdbc.queryForList("SELECT * FROM trading.historical_ingestion_chunks"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM trading.orders",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM trading.kite_access_tokens",Integer.class));
    }
    @Test void pinnedManifestDetectsLateBackfillEvenWithTheSameObservationTimestamp() {
        repo.append(chunk(List.of(bar(START,"100.5")),NOW));
        var manifest=query(NOW,NOW);
        repo.append(chunk(List.of(bar(START.plusSeconds(60),"100.5")),NOW));
        assertEquals(HistoricalDataException.Reason.CONFLICT,
                assertThrows(HistoricalDataException.class,()->repo.replay(manifest)).reason());
    }
    @Test void changedCloseRollsBackWholeChunkIncludingEarlierInsertAndEvidence() {
        repo.append(chunk(List.of(bar(START.plusSeconds(60),"100.5")),NOW));
        var before=jdbc.queryForList("SELECT * FROM trading.historical_bars");
        var failure=assertThrows(HistoricalDataException.class,()->repo.append(chunk(List.of(bar(START,"100"),bar(START.plusSeconds(60),"100.7")),NOW)));
        assertEquals(HistoricalDataException.Reason.CONFLICT,failure.reason());
        assertEquals(before,jdbc.queryForList("SELECT * FROM trading.historical_bars"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM trading.historical_ingestion_chunks",Integer.class));
    }
    @Test void concurrentIdenticalWorkersHaveOneRowAndConflictingWorkersHaveOneWinner() throws Exception {
        race(false);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM trading.historical_bars",Integer.class));
        jdbc.execute("TRUNCATE trading.historical_bars,trading.historical_ingestion_chunks");race(true);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM trading.historical_bars",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM trading.historical_ingestion_chunks",Integer.class));
    }
    void race(boolean differing) throws Exception {
        var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->{gate.await();return attempt("100.5");});
            var b=executor.submit(()->{gate.await();return attempt(differing?"100.7":"100.5");});gate.countDown();
            var results=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertEquals(differing?1:0,results.stream().filter("CONFLICT"::equals).count());
        }
    }
    String attempt(String close) {
        try {new PostgresHistoricalBarRepository(jdbc).append(chunk(List.of(bar(START,close)),NOW));return "OK";}
        catch(HistoricalDataException e) {return e.reason().name();}
    }
    @Test void restartAfterSecondChunkFailureHasNoDuplicateAndPreservesFirstChunk() {
        var request=new HistoricalWindow(SBIN.id(),BarInterval.MINUTE,START,START.plus(Duration.ofDays(2)));
        var registry=mock(InstrumentRegistry.class);when(registry.snapshot()).thenReturn(InstrumentSnapshot.validated(List.of(SBIN),1,NOW));
        when(registry.findById(SBIN.id())).thenReturn(Optional.of(SBIN));
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        HistoricalMarketDataProvider failing=w->{if(calls.incrementAndGet()==2)throw new HistoricalDataException(HistoricalDataException.Reason.TRANSIENT_PROVIDER);
            return new HistoricalMarketDataProvider.Batch(List.of(bar(w.from(),"100.5")),"FAKE","v1",NOW);};
        var calendar=new TradingCalendar("unknown","synthetic",Map.of());var clock=Clock.fixed(NOW,ZoneOffset.UTC);
        assertThrows(HistoricalDataException.class,()->new HistoricalDataIngestionService(registry,failing,repo,clock).ingest(request,NOW,calendar));
        var before=jdbc.queryForList("SELECT * FROM trading.historical_bars");assertEquals(1,before.size());
        HistoricalMarketDataProvider success=w->new HistoricalMarketDataProvider.Batch(List.of(bar(w.from(),"100.5")),"FAKE","v1",NOW);
        var result=new HistoricalDataIngestionService(registry,success,new PostgresHistoricalBarRepository(jdbc),clock).ingest(request,NOW,calendar);
        assertEquals(2,result.providerCalls());assertEquals(1,result.chunks().getFirst().existing());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM trading.historical_bars",Integer.class));
        assertEquals(before.getFirst(),jdbc.queryForList("SELECT * FROM trading.historical_bars ORDER BY start_time").getFirst());
    }
    @Test void instrumentAndRangeIsolationAndNoRounding() {
        repo.append(chunk(List.of(bar(START,"100.123456789")),NOW));
        assertEquals(new BigDecimal("100.123456789"),query(NOW,NOW).bars().getFirst().close());
        var other=new HistoricalWindow(new com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId(UUID.randomUUID()),BarInterval.MINUTE,START,START.plusSeconds(180));
        assertTrue(repo.query(new HistoricalBarRepository.Query(other,NOW,NOW)).bars().isEmpty());
        var excluded=new HistoricalWindow(SBIN.id(),BarInterval.MINUTE,START.plusSeconds(60),START.plusSeconds(180));
        assertTrue(repo.query(new HistoricalBarRepository.Query(excluded,NOW,NOW)).bars().isEmpty());
    }
}
