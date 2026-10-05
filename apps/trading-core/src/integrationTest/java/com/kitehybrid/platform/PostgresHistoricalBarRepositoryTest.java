package com.kitehybrid.platform;

import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.historical.application.*;
import com.kitehybrid.platform.historical.infrastructure.PostgresHistoricalBarRepository;
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
