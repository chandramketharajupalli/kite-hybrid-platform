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
    @ParameterizedTest @ValueSource(strings={"trigger","inherited-update","inherited-trigger"})
    void effectiveInheritedAndTriggerPrivilegesDenyBeforeHttp(String fault)throws Exception{
        boolean inherited=fault.startsWith("inherited");String privilege=fault.endsWith("update")?"UPDATE":"TRIGGER";
        if(inherited){
            admin.execute("CREATE ROLE fixture_parent INHERIT");admin.execute("CREATE ROLE fixture_child INHERIT");
            admin.execute("GRANT fixture_parent TO fixture_child");admin.execute("GRANT fixture_child TO controlled_observer");
        }
        String grantee=inherited?"fixture_parent":"controlled_observer";
        admin.execute("GRANT "+privilege+" ON trading.kite_access_tokens TO "+grantee);
        try(var connection=observer();var wire=KiteEquityReadRequestFactory.loopback(URI.create("http://127.0.0.1:1"),Duration.ofMillis(100));
            var statement=connection.createStatement()){
            try(var rows=statement.executeQuery("SELECT has_table_privilege(current_user,'trading.kite_access_tokens','"+privilege+"')")){
                assertThat(rows.next()).isTrue();assertThat(rows.getBoolean(1)).isTrue();
            }
            var harness=new KiteEquityReadHarness(wire,session(),new KiteEquityReadIntegrity(connection),
                    new RuntimeTradingHalt(()->true),Clock.fixed(NOW,ZoneOffset.UTC),Mode.SYNTHETIC);
            assertThat(harness.run().outcome()).isEqualTo(Outcome.PRECONDITION_DENIED);
            assertThat(wire.attempts()).isZero();
        }finally{
            admin.execute("REVOKE "+privilege+" ON trading.kite_access_tokens FROM "+grantee);
            if(inherited){
                admin.execute("REVOKE fixture_child FROM controlled_observer");admin.execute("REVOKE fixture_parent FROM fixture_child");
                admin.execute("DROP ROLE fixture_child");admin.execute("DROP ROLE fixture_parent");
            }
        }
    }

    @Test void disablingFutureLoginsDoesNotExcludeAlreadyConnectedWriter()throws Exception{
        admin.execute("CREATE ROLE fixture_writer LOGIN PASSWORD 'syntheticWriterOnly'");
        admin.execute("GRANT USAGE ON SCHEMA trading TO fixture_writer");
        admin.execute("GRANT SELECT,UPDATE ON trading.kite_access_tokens TO fixture_writer");
        try(var connection=observer()){
            var guard=new KiteEquityReadIntegrity(connection);var before=guard.capture();
            try(var writer=DriverManager.getConnection(postgres.getJdbcUrl(),"fixture_writer","syntheticWriterOnly")){
                admin.execute("ALTER ROLE fixture_writer NOLOGIN");
                var denied=org.junit.jupiter.api.Assertions.assertThrows(SQLException.class,()->{
                    try(var unexpected=DriverManager.getConnection(postgres.getJdbcUrl(),"fixture_writer","syntheticWriterOnly")){
                        throw new AssertionError("Fixture writer unexpectedly connected");
                    }
                });
                assertThat(denied.getSQLState()).isEqualTo("28000");
                try(var statement=writer.createStatement()){
                    assertThat(statement.executeUpdate("UPDATE trading.kite_access_tokens SET expires_at=expires_at+interval '1 second'")).isEqualTo(1);
                }
                assertThatThrownBy(guard::capture).isInstanceOf(RuntimeException.class);
            }
            org.junit.jupiter.api.Assertions.assertFalse(before.matches(guard.capture()),"Existing writer changed private fixture evidence");
        }finally{
            admin.execute("REVOKE SELECT,UPDATE ON trading.kite_access_tokens FROM fixture_writer");
            admin.execute("REVOKE USAGE ON SCHEMA trading FROM fixture_writer");
            admin.execute("DROP ROLE fixture_writer");
        }
    }
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
        admin.execute("REVOKE TEMP ON DATABASE "+postgres.getDatabaseName()+" FROM PUBLIC");
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

    @ParameterizedTest @ValueSource(strings={"success","301","302","307","308","401","403","429","500","503","timeout","malformed","duplicate","trailing","oversized","session","isolation-loss","response-loss"})
    void isolatedReadPreservesTokensEveryTableAndHaltOnAllBrokerOutcomes(String response)throws Exception{
        exercise(response,false);
    }
    @ParameterizedTest @ValueSource(strings={"external-token-write","halt-epoch"})
    void externalStateChangeIsDetectedAndNeverReportedAsVerified(String response)throws Exception{
        exercise(response,true);
    }
    void exercise(String response,boolean changed)throws Exception{
        var isolated=new java.util.concurrent.atomic.AtomicBoolean(true);
        var calls=new AtomicInteger();var session=session();var halt=new RuntimeTradingHalt(()->true);var epoch=halt.epoch();
        var peer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        peer.createContext("/",exchange->{
            calls.incrementAndGet();
            if(response.equals("isolation-loss"))isolated.set(false);
            if(response.equals("response-loss")){exchange.close();return;}
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
            if(status>=300 && status<400)exchange.getResponseHeaders().set("Location","/orders");
            byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try{exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);}finally{exchange.close();}
        });peer.start();
        try(var connection=observer();var wire=KiteEquityReadRequestFactory.loopback(
                URI.create("http://127.0.0.1:"+peer.getAddress().getPort()),Duration.ofMillis(300))){
            var guard=new KiteEquityReadIntegrity(connection);var before=guard.capture();
            try(var owner=new KiteEquityReadHandoff.Owner();
                var isolation=new KiteEquityReadIsolation(Clock.fixed(NOW,ZoneOffset.UTC),Duration.ofSeconds(10),isolated::get)){
                var recipient=new KiteEquityReadHandoff.Recipient();
                var handoff=new KiteEquityReadHandoff(owner,recipient,session,halt,isolation,Clock.fixed(NOW,ZoneOffset.UTC),Duration.ofSeconds(5),wire,guard);
                var result=handoff.consume(owner,recipient,"GET","/user/margins/equity");
                if(changed || java.util.Set.of("isolation-loss","session","401","403").contains(response))assertThat(result.outcome()).isEqualTo(Outcome.STATE_CHANGED);
                else{
                    assertThat(result.statePreserved()).isTrue();assertTrue(before.matches(guard.capture()),"Private integrity fingerprint changed");
                    assertThat(halt.epoch()).isSameAs(epoch);
                    assertThat(result.outcome()==Outcome.OBSERVED).isEqualTo(response.equals("success"));
                }
                if(!changed)assertTrue(before.matches(guard.capture()),"Private integrity fingerprint changed");
                assertThat(result.requestAttempts()).isEqualTo(1);assertThat(calls).hasValue(1);
                assertThat(result.toString()).doesNotContain("9000","syntheticPrivateText","syntheticHarnessToken");
                assertThat(halt.getAsBoolean()).isTrue();
                assertThatThrownBy(()->handoff.consume(owner,recipient,"GET","/user/margins/equity")).isInstanceOf(RuntimeException.class);
            }
        }finally{peer.stop(0);}
    }
    @ParameterizedTest @ValueSource(strings={"superuser","other-connection","repeatable-read","transaction","missing-token","write-role","stats-hidden","function","role-member","schema-create","temp","missing-table","select-denied","read-write"})
    void unverifiableIsolationAbortsWithoutHttp(String fault)throws Exception{
        if(fault.equals("function"))admin.execute("CREATE FUNCTION public.fixture_mutator() RETURNS integer LANGUAGE sql SECURITY DEFINER AS 'SELECT 1'");
        if(fault.equals("role-member")){admin.execute("CREATE ROLE fixture_escalation");admin.execute("GRANT fixture_escalation TO controlled_observer");}
        if(fault.equals("schema-create"))admin.execute("GRANT CREATE ON SCHEMA public TO controlled_observer");
        if(fault.equals("temp"))admin.execute("GRANT TEMP ON DATABASE "+postgres.getDatabaseName()+" TO controlled_observer");
        if(fault.equals("missing-table"))admin.execute("ALTER TABLE trading.reconciliation_trades RENAME TO fixture_hidden");
        if(fault.equals("select-denied"))admin.execute("REVOKE SELECT ON trading.orders FROM controlled_observer");
        if(fault.equals("stats-hidden"))admin.execute("REVOKE pg_read_all_stats FROM controlled_observer");
        if(fault.equals("missing-token"))admin.update("DELETE FROM trading.kite_access_tokens");
        if(fault.equals("write-role"))admin.execute("GRANT UPDATE ON trading.kite_access_tokens TO controlled_observer");
        var extra=fault.equals("other-connection")?observer():null;
        try(var connection=fault.equals("superuser")?DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()):observer();
            var wire=KiteEquityReadRequestFactory.loopback(URI.create("http://127.0.0.1:1"),Duration.ofMillis(100))){
            if(fault.equals("read-write"))connection.setReadOnly(false);
            if(fault.equals("read-write"))connection.createStatement().execute("SET default_transaction_read_only=off");
            if(fault.equals("repeatable-read"))connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            if(fault.equals("transaction"))connection.setAutoCommit(false);
            var harness=new KiteEquityReadHarness(wire,session(),new KiteEquityReadIntegrity(connection),
                    new RuntimeTradingHalt(()->true),Clock.fixed(NOW,ZoneOffset.UTC),Mode.SYNTHETIC);
            assertThat(harness.run().outcome()).isEqualTo(Outcome.PRECONDITION_DENIED);assertThat(wire.attempts()).isZero();
        }finally{
            if(extra!=null)extra.close();
            if(fault.equals("function"))admin.execute("DROP FUNCTION public.fixture_mutator()");
            if(fault.equals("role-member")){admin.execute("REVOKE fixture_escalation FROM controlled_observer");admin.execute("DROP ROLE fixture_escalation");}
            if(fault.equals("schema-create"))admin.execute("REVOKE CREATE ON SCHEMA public FROM controlled_observer");
            if(fault.equals("temp"))admin.execute("REVOKE TEMP ON DATABASE "+postgres.getDatabaseName()+" FROM controlled_observer");
            if(fault.equals("missing-table"))admin.execute("ALTER TABLE trading.fixture_hidden RENAME TO reconciliation_trades");
            if(fault.equals("select-denied"))admin.execute("GRANT SELECT ON trading.orders TO controlled_observer");
            if(fault.equals("stats-hidden"))admin.execute("GRANT pg_read_all_stats TO controlled_observer");
            if(fault.equals("write-role"))admin.execute("REVOKE UPDATE ON trading.kite_access_tokens FROM controlled_observer");
        }
    }
    @ParameterizedTest @ValueSource(strings={"same-count-write","empty-schema-change","rollback"})
    void canonicalFingerprintDetectsWritesAndSchemaButNotRolledBackChanges(String fault)throws Exception{
        try(var connection=observer()){
            var guard=new KiteEquityReadIntegrity(connection);var before=guard.capture();
            try(var writer=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())){
                writer.setAutoCommit(false);
                try(var statement=writer.createStatement()){
                    statement.execute(fault.equals("empty-schema-change")?"ALTER TABLE trading.orders ADD COLUMN fixture_marker integer":"UPDATE trading.kite_access_tokens SET expires_at=expires_at+interval '1 second'");
                }
                assertThatThrownBy(guard::capture).isInstanceOf(RuntimeException.class);
                if(fault.equals("rollback"))writer.rollback();else writer.commit();
            }
            org.junit.jupiter.api.Assertions.assertEquals(fault.equals("rollback"),before.matches(guard.capture()),"Private fingerprint comparison");
        }finally{if(fault.equals("empty-schema-change"))admin.execute("ALTER TABLE trading.orders DROP COLUMN fixture_marker");}
    }
    @Test void observerCannotWriteEvenIfItAttemptsDirectSql()throws Exception{
        try(var connection=observer();var statement=connection.createStatement()){
            assertThatThrownBy(()->statement.executeUpdate("UPDATE trading.kite_access_tokens SET expires_at=expires_at")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->statement.execute("CREATE TABLE trading.fixture_forbidden(x integer)")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->statement.execute("SET ROLE "+postgres.getUsername())).isInstanceOf(SQLException.class);
            assertThat(new KiteEquityReadIntegrity(connection).capture().toString()).isEqualTo("EquityReadIntegrity[REDACTED]");
        }
    }

    @Test void committedWriteAndRestorationBetweenSnapshotsCannotCertifyWriterExclusion()throws Exception{
        try(var connection=observer()){
            var guard=new KiteEquityReadIntegrity(connection);var before=guard.capture();
            // Two committed transactions, completely between observations; no cooperative lock is consulted.
            try(var writer=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
                var statement=writer.createStatement()){
                assertThat(statement.executeUpdate("UPDATE trading.kite_access_tokens SET expires_at=expires_at+interval '1 second'")).isEqualTo(1);
                assertThat(statement.executeUpdate("UPDATE trading.kite_access_tokens SET expires_at=expires_at-interval '1 second'")).isEqualTo(1);
            }
            assertTrue(before.matches(guard.capture()),"Equal snapshots do not establish absence of intervening writes");
        }
    }

    @ParameterizedTest @ValueSource(strings={"insert","update","delete","truncate","ddl","sequence","role","function"})
    void observerPrivilegesDenyMutationEvenWithoutTransactionReadOnlyFallback(String operation)throws Exception{
        admin.execute("CREATE SEQUENCE trading.fixture_sequence");
        admin.execute("CREATE FUNCTION public.fixture_write() RETURNS void LANGUAGE sql SECURITY DEFINER AS 'UPDATE trading.kite_access_tokens SET expires_at=expires_at+interval ''1 second''' ");
        admin.execute("REVOKE ALL ON FUNCTION public.fixture_write() FROM PUBLIC");
        try(var connection=observer();var statement=connection.createStatement()){
            var guard=new KiteEquityReadIntegrity(connection);var before=guard.capture();
            // Fixture only: prove ACL denial independently of transaction_read_only.
            statement.execute("SET default_transaction_read_only=off");
            String sql=switch(operation){
                case "insert"->"INSERT INTO trading.kite_access_tokens SELECT * FROM trading.kite_access_tokens WHERE false";
                case "update"->"UPDATE trading.kite_access_tokens SET expires_at=expires_at";
                case "delete"->"DELETE FROM trading.kite_access_tokens";
                case "truncate"->"TRUNCATE trading.kite_access_tokens";
                case "ddl"->"CREATE TABLE trading.fixture_forbidden(x integer)";
                case "sequence"->"SELECT nextval('trading.fixture_sequence')";
                case "role"->"SET ROLE "+postgres.getUsername();
                case "function"->"SELECT public.fixture_write()";
                default->throw new IllegalArgumentException();
            };
            var denied=org.junit.jupiter.api.Assertions.assertThrows(SQLException.class,()->statement.execute(sql));
            assertThat(denied.getSQLState()).isEqualTo("42501");
            statement.execute("SET default_transaction_read_only=on");
            assertTrue(before.matches(guard.capture()),"Mutation attempt changed private evidence");
        }finally{
            admin.execute("DROP FUNCTION public.fixture_write()");
            admin.execute("DROP SEQUENCE trading.fixture_sequence");
        }
    }

    @ParameterizedTest @ValueSource(strings={"sequence","write-function"})
    void newlyGrantedMutatingPrivilegeAbortsBeforeHttp(String privilege)throws Exception{
        if(privilege.equals("sequence")){
            admin.execute("CREATE SEQUENCE trading.fixture_sequence");
            admin.execute("GRANT USAGE ON SEQUENCE trading.fixture_sequence TO controlled_observer");
        }else{
            admin.execute("CREATE FUNCTION public.fixture_write() RETURNS void LANGUAGE sql SECURITY DEFINER AS 'UPDATE trading.kite_access_tokens SET expires_at=expires_at+interval ''1 second''' ");
        }
        try(var connection=observer();var wire=KiteEquityReadRequestFactory.loopback(URI.create("http://127.0.0.1:1"),Duration.ofMillis(100))){
            var harness=new KiteEquityReadHarness(wire,session(),new KiteEquityReadIntegrity(connection),
                    new RuntimeTradingHalt(()->true),Clock.fixed(NOW,ZoneOffset.UTC),Mode.SYNTHETIC);
            assertThat(harness.run().outcome()).isEqualTo(Outcome.PRECONDITION_DENIED);
            assertThat(wire.attempts()).isZero();
        }finally{
            if(privilege.equals("sequence"))admin.execute("DROP SEQUENCE trading.fixture_sequence");
            else admin.execute("DROP FUNCTION public.fixture_write()");
        }
    }

}
