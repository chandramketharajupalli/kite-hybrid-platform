package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.historical.application.*;
import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.historical.infrastructure.PostgresHistoricalBarRepository;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Entire provider-to-replay path; synthetic candles/credentials and disposable PostgreSQL only. */
@Testcontainers @Isolated
class KiteHistoricalPipelineTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres","-c","timezone=UTC");
    static TimeZone original;
    @BeforeAll static void utc() { original=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC")); }
    @AfterAll static void restore() { TimeZone.setDefault(original); }

    @Test void mappedInstrumentThroughHistoricalHttpToPostgresHasStableReplayAndClosedBarCutoff() {
        var from=Instant.parse("2026-07-30T03:45:00Z");var to=Instant.parse("2026-07-30T10:00:00Z");
        var now=Instant.parse("2026-10-05T16:00:00Z");var clock=Clock.fixed(now,ZoneOffset.UTC);
        var instrument=new KiteInstrumentCsvMapper().map("""
                instrument_token,tradingsymbol,exchange,segment,instrument_type,expiry,strike,tick_size,lot_size
                123,SBIN,NSE,NSE,EQ,,0,0.05,1
                """).getFirst();
        var registry=new InMemoryInstrumentRegistry();registry.replace(List.of(instrument),now);
        var session=new KiteSession(new KiteProperties("syntheticHistoricalKey","","",true),clock);
        session.install(new KiteAccessToken("syntheticHistoricalToken",now,now.plusSeconds(3600)));session.profileValidated();
        var identity=session.executionIdentity();var halt=new RuntimeTradingHalt(()->true);var epoch=halt.epoch();
        var requests=new ArrayList<String>();
        var builder=RestClient.builder().baseUrl("http://127.0.0.1").requestInterceptor((r,b,next)->{
            assertEquals("127.0.0.1",r.getURI().getHost());assertEquals(HttpMethod.GET,r.getMethod());
            assertEquals("/instruments/historical/123/minute",r.getURI().getPath());
            requests.add(r.getURI().getPath());return next.execute(r,b);
        });
        var server=MockRestServiceServer.bindTo(builder).build();
        var rows=new ArrayList<String>();
        var format=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXX",Locale.ROOT)
                .withZone(TradingCalendar.NSE_ZONE);
        for(var at=from;!at.isAfter(to);at=at.plusSeconds(60))
            rows.add("[\""+format.format(at)+"\",100.123456789,101,99,100.5,9]");
        var body="{\"status\":\"success\",\"data\":{\"candles\":["+String.join(",",rows)+"]}}";
        for(int n=0;n<2;n++) server.expect(anything()).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        var adapter=new KiteHistoricalAdapter(new KiteRestTransport(builder.build(),session),registry,clock,true,()->{});
        var calendar=new TradingCalendar("synthetic-full-session","synthetic",Map.of(LocalDate.of(2026,7,30),
                new TradingCalendar.Day(TradingCalendar.Status.EXPECTED_SESSION,
                        List.of(new TradingCalendar.Session(LocalTime.of(9,15),LocalTime.of(15,30))))));
        var window=new HistoricalWindow(instrument.id(),BarInterval.MINUTE,from,to);
        HistoricalMarketDataProvider checked=w->{
            var batch=adapter.fetch(w);
            assertTrue(HistoricalQuality.analyze(w,to,batch.bars(),calendar).complete());
            return batch;
        };
        assertNotEquals("jdbc:postgresql://localhost:5432/trading",postgres.getJdbcUrl());
        var ds=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration","classpath:db/historical").load().migrate();
        var jdbc=new JdbcTemplate(ds);var repo=new PostgresHistoricalBarRepository(jdbc);
        var service=new HistoricalDataIngestionService(registry,checked,repo,clock);
        var first=service.ingest(window,to,calendar);
        long expected=Duration.between(from,to).dividedBy(BarInterval.MINUTE.duration());
        assertEquals(expected,first.accepted());assertEquals(expected,first.quality().expectedBars());
        assertEquals(0,first.duplicates());assertTrue(first.quality().complete());
        var originalRows=jdbc.queryForList("SELECT * FROM trading.historical_bars ORDER BY start_time");
        var manifest=repo.query(new HistoricalBarRepository.Query(window,to,now));
        assertEquals(first.contentHash(),manifest.contentHash());
        assertEquals(from,manifest.bars().getFirst().startTime());
        assertEquals(to.minusSeconds(60),manifest.bars().getLast().startTime());
        assertEquals(new java.math.BigDecimal("100.123456789"),manifest.bars().getFirst().open());
        var replay=service.ingest(window,to,calendar);
        assertEquals(0,replay.chunks().getFirst().inserted());assertEquals(expected,replay.chunks().getFirst().existing());
        assertEquals(originalRows,jdbc.queryForList("SELECT * FROM trading.historical_bars ORDER BY start_time"));
        assertEquals(manifest,repo.replay(manifest));assertEquals(first.contentHash(),replay.contentHash());
        var evidence=repo.evidence(first.chunks().getFirst().chunkId()).orElseThrow();
        assertEquals(KiteHistoricalAdapter.SOURCE_VERSION,evidence.sourceVersion());
        assertEquals(calendar.fingerprint(),evidence.calendarFingerprint());assertEquals(window,evidence.request());
        var closed=repo.query(new HistoricalBarRepository.Query(window,from.plusSeconds(120),now));
        assertEquals(2,closed.bars().size());assertEquals(from.plusSeconds(60),closed.bars().getLast().startTime());
        for(var table:List.of("orders","risk_decisions","execution_authorizations","kite_access_tokens","reconciliation_decisions"))
            assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM trading."+table,Long.class));
        assertEquals(2,requests.size());server.verify();assertEquals(identity,session.executionIdentity());
        assertTrue(halt.getAsBoolean());assertSame(epoch,halt.epoch());
    }
}
