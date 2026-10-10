package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.kitehybrid.platform.broker.infrastructure.kite.KiteEquityReadHarness.*;

@Testcontainers @Isolated("UTC PostgreSQL startup; disposable roles and rows only")
class KiteEquityReadHarnessIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres","-c","fsync=off","-c","timezone=UTC");
    static final Instant NOW=Instant.parse("2026-10-10T05:00:00Z");
    static final String KEY=Base64.getEncoder().encodeToString(new byte[32]);
    static JdbcTemplate admin;static TimeZone zone;
    @BeforeAll static void database(){
        zone=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        admin=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
        admin.execute("CREATE ROLE controlled_observer LOGIN PASSWORD 'syntheticDisposableOnly'");
        admin.execute("GRANT pg_read_all_stats TO controlled_observer");
        admin.execute("GRANT USAGE ON SCHEMA trading TO controlled_observer");
        admin.execute("GRANT SELECT ON ALL TABLES IN SCHEMA trading TO controlled_observer");
        admin.execute("ALTER ROLE controlled_observer SET default_transaction_read_only=on");
    }
    @BeforeEach void seed(){
        new PostgresKiteAccessTokenStore(admin,"f".repeat(64),KEY).save(
                new KiteAccessToken("syntheticHarnessToken",NOW.minusSeconds(1),NOW.plusSeconds(600)));
    }
    @AfterAll static void timezone(){if(zone!=null)TimeZone.setDefault(zone);}
    static Connection observer()throws Exception{return DriverManager.getConnection(postgres.getJdbcUrl(),"controlled_observer","syntheticDisposableOnly");}
    static KiteSession session(){
        var session=new KiteSession(new KiteProperties("syntheticHarnessKey","","",true),Clock.fixed(NOW,ZoneOffset.UTC));
        session.install(new KiteAccessToken("syntheticHarnessToken",NOW.minusSeconds(1),NOW.plusSeconds(600)));
        session.profileValidated();return session; // Test-only prior authentication; no broker profile call.
    }

    @ParameterizedTest @ValueSource(strings={"success","401","403","429","500","503","timeout","malformed","duplicate","trailing","oversized","session"})
    void isolatedReadPreservesTokensEveryTableAndHaltOnAllBrokerOutcomes(String response)throws Exception{
        exercise(response,false);
    }
    @ParameterizedTest @ValueSource(strings={"external-token-write","halt-epoch"})
    void externalStateChangeIsDetectedAndNeverReportedAsVerified(String response)throws Exception{
        exercise(response,true);
    }
    void exercise(String response,boolean changed)throws Exception{
        var calls=new AtomicInteger();var session=session();var halt=new RuntimeTradingHalt(()->true);var epoch=halt.epoch();
        var peer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        peer.createContext("/",exchange->{
            calls.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().toString()).isEqualTo("/user/margins/equity");
            if(response.equals("session"))session.rejectMarketData(session.marketDataStatus().generation());
            if(response.equals("external-token-write"))admin.update("UPDATE trading.kite_access_tokens SET expires_at=expires_at+interval '1 second'");
            if(response.equals("halt-epoch"))halt.halt();
            if(response.equals("timeout"))try{Thread.sleep(600);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            var body=KiteEquityMarginReadIntegrationTest.BODY;
            if(response.equals("malformed"))body="{syntheticPrivateText";
            if(response.equals("duplicate"))body=body.replace("\"enabled\":true","\"enabled\":true,\"enabled\":false");
            if(response.equals("trailing"))body+="{}";
            if(response.equals("oversized"))body=" ".repeat(65537);
            int status=response.matches("[0-9]{3}")?Integer.parseInt(response):200;
            byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try{exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);}finally{exchange.close();}
        });peer.start();
        try(var connection=observer();var wire=KiteEquityReadRequestFactory.loopback(
                URI.create("http://127.0.0.1:"+peer.getAddress().getPort()),Duration.ofMillis(300))){
            var guard=new KiteEquityReadIntegrity(connection);var before=guard.capture();
            try(var harness=new KiteEquityReadHarness(wire,session,guard,halt,Clock.fixed(NOW,ZoneOffset.UTC),Mode.SYNTHETIC)){
                var result=harness.run();
                if(changed)assertThat(result.outcome()).isEqualTo(Outcome.STATE_CHANGED);
                else{
                    assertThat(result.statePreserved()).isTrue();assertTrue(before.matches(guard.capture()),"Private integrity fingerprint changed");
                    assertThat(halt.epoch()).isSameAs(epoch);
                    assertThat(result.outcome()==Outcome.OBSERVED).isEqualTo(response.equals("success"));
                }
                assertThat(result.requestAttempts()).isEqualTo(1);assertThat(calls).hasValue(1);
                assertThat(result.toString()).doesNotContain("9000","syntheticPrivateText","syntheticHarnessToken");
                assertThat(halt.getAsBoolean()).isTrue();
                assertThat(harness.run().outcome()).isEqualTo(Outcome.ALREADY_USED);
            }
        }finally{peer.stop(0);}
    }
    @ParameterizedTest @ValueSource(strings={"superuser","other-connection","repeatable-read","transaction","missing-token","write-role","stats-hidden"})
    void unverifiableIsolationAbortsWithoutHttp(String fault)throws Exception{
        if(fault.equals("stats-hidden"))admin.execute("REVOKE pg_read_all_stats FROM controlled_observer");
        if(fault.equals("missing-token"))admin.update("DELETE FROM trading.kite_access_tokens");
        if(fault.equals("write-role"))admin.execute("GRANT UPDATE ON trading.kite_access_tokens TO controlled_observer");
        var extra=fault.equals("other-connection")?observer():null;
        try(var connection=fault.equals("superuser")?DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()):observer();
            var wire=KiteEquityReadRequestFactory.loopback(URI.create("http://127.0.0.1:1"),Duration.ofMillis(100))){
            if(fault.equals("repeatable-read"))connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            if(fault.equals("transaction"))connection.setAutoCommit(false);
            var harness=new KiteEquityReadHarness(wire,session(),new KiteEquityReadIntegrity(connection),
                    new RuntimeTradingHalt(()->true),Clock.fixed(NOW,ZoneOffset.UTC),Mode.SYNTHETIC);
            assertThat(harness.run().outcome()).isEqualTo(Outcome.PRECONDITION_DENIED);assertThat(wire.attempts()).isZero();
        }finally{
            if(extra!=null)extra.close();
            if(fault.equals("stats-hidden"))admin.execute("GRANT pg_read_all_stats TO controlled_observer");
            if(fault.equals("write-role"))admin.execute("REVOKE UPDATE ON trading.kite_access_tokens FROM controlled_observer");
        }
    }
}
