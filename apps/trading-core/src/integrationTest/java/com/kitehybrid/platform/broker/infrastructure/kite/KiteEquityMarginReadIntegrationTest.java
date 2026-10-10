package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** No Spring startup: SELECT-only credential loading, intercepted HTTP and a disposable database. */
@Testcontainers
@Isolated("PostgreSQL JDBC UTC startup")
class KiteEquityMarginReadIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    static final Instant NOW = Instant.parse("2026-10-10T05:00:00Z");
    static final String STORE = "e".repeat(64);
    static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    static final String BODY = """
            {"status":"success","data":{"enabled":true,"net":9000.00000000001,
            "available":{"adhoc_margin":0,"cash":0.01,"opening_balance":0.01,"live_balance":0.01,
            "collateral":9000,"intraday_payin":0},"utilised":{"debits":0,"exposure":0,
            "m2m_realised":0,"m2m_unrealised":0,"option_premium":0,"payout":0,"span":0,
            "holding_sales":0,"turnover":0,"liquid_collateral":0,"stock_collateral":9000,"delivery":0}}}
            """;
    static TimeZone original;
    static JdbcTemplate admin;
    static JdbcTemplate reader;

    @BeforeAll static void fixture() {
        original = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        admin = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
        // Fixture writes finish before the read-only observation window begins.
        new PostgresKiteAccessTokenStore(admin,STORE,KEY).save(
                new KiteAccessToken("syntheticEquityReadCredential",NOW.minusSeconds(1),NOW.plusSeconds(600)));
        admin.execute("CREATE ROLE equity_observer LOGIN PASSWORD 'syntheticDisposableOnly'");
        admin.execute("GRANT USAGE ON SCHEMA trading TO equity_observer");
        admin.execute("GRANT SELECT ON ALL TABLES IN SCHEMA trading TO equity_observer");
        admin.execute("ALTER ROLE equity_observer SET default_transaction_read_only = on");
        reader = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),"equity_observer","syntheticDisposableOnly"));
    }
    @AfterAll static void timezone() { if (original != null) TimeZone.setDefault(original); }

    @ParameterizedTest @ValueSource(strings={"success","401","403","429","500","503","malformed","oversized","timeout","connectivity","token-error"})
    void passiveReadPreservesEveryTradingTableAndEncryptedToken(String response) {
        var before = integrity();
        byte[] tokenBefore = reader.queryForObject("SELECT token_ciphertext FROM trading.kite_access_tokens",byte[].class);
        assertThat(reader.queryForObject("SHOW transaction_read_only",String.class)).isEqualTo("on");
        assertThat(reader.queryForObject("SELECT has_table_privilege(current_user,'trading.kite_access_tokens','INSERT')",Boolean.class)).isFalse();
        assertThat(reader.queryForObject("SELECT has_table_privilege(current_user,'trading.orders','UPDATE')",Boolean.class)).isFalse();
        var clock = Clock.fixed(NOW,ZoneOffset.UTC);
        var session = new KiteSession(new KiteProperties("syntheticEquityReadKey","","",true),clock);
        session.install(new PostgresKiteAccessTokenStore(reader,STORE,KEY).loadCurrent().orElseThrow());
        session.profileValidated(); // Synthetic attestation; no profile GET or token exchange.
        var halt = new RuntimeTradingHalt(() -> true); var epoch = halt.epoch();
        var builder = RestClient.builder().baseUrl("http://127.0.0.1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var calls = new AtomicInteger();
        var expected = server.expect(requestTo("http://127.0.0.1/user/margins/equity"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("X-Kite-Version","3"))
                .andExpect(r -> { assertTrue(halt.getAsBoolean()); calls.incrementAndGet(); });
        switch (response) {
            case "success" -> expected.andRespond(withSuccess(BODY,MediaType.APPLICATION_JSON));
            case "malformed" -> expected.andRespond(withSuccess(BODY+"{}",MediaType.APPLICATION_JSON));
            case "oversized" -> expected.andRespond(withSuccess(" ".repeat(65537),MediaType.APPLICATION_JSON));
            case "timeout" -> expected.andRespond(withException(new java.net.SocketTimeoutException("syntheticPrivateDetail")));
            case "connectivity" -> expected.andRespond(withException(new java.net.ConnectException("syntheticPrivateDetail")));
            case "token-error" -> expected.andRespond(withSuccess("{\"status\":\"error\",\"error_type\":\"TokenException\"}",MediaType.APPLICATION_JSON));
            default -> expected.andRespond(withStatus(HttpStatus.valueOf(Integer.parseInt(response))).body("syntheticPrivateDetail"));
        }
        var adapter = new KiteEquityMarginReadAdapter(new KiteRestTransport(builder.build(),session),session,clock,true);
        if (response.equals("success")) assertThat(adapter.read().receivedAt()).isEqualTo(NOW);
        else {
            var failure = assertThrows(BrokerReadException.class,adapter::read);
            assertThat(failure.toString()).doesNotContain("syntheticPrivateDetail", "syntheticEquityReadCredential");
            assertThat(failure.getCause()).isNull();
        }
        assertThat(session.authenticated()).isEqualTo(!Set.of("401","403","token-error").contains(response));
        assertThat(calls).hasValue(1); server.verify();
        assertSame(epoch,halt.epoch()); assertTrue(halt.getAsBoolean());
        // Assertions intentionally avoid printing token bytes or row fingerprints on failure.
        assertTrue(Arrays.equals(tokenBefore,reader.queryForObject("SELECT token_ciphertext FROM trading.kite_access_tokens",byte[].class)),
                "Encrypted token changed during passive read");
        assertTrue(before.equals(integrity()),"Trading table counts/content changed during passive read");
        assertThat(reader.queryForObject("SELECT count(*) FROM trading.orders",Integer.class)).isZero();
        assertThat(reader.queryForObject("SELECT count(*) FROM trading.execution_authorizations",Integer.class)).isZero();
    }

    private static Map<String,String> integrity() {
        var result = new TreeMap<String,String>();
        for (var table : reader.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema='trading' AND table_type='BASE TABLE' ORDER BY table_name",String.class)) {
            assertTrue(table.matches("[a-z_]+"),"Unexpected table identifier");
            result.put(table,reader.queryForObject("SELECT count(*)::text || ':' || md5(coalesce(string_agg(row_to_json(t)::text, '' ORDER BY row_to_json(t)::text),'')) FROM trading.\""+table+"\" t",String.class));
        }
        assertFalse(result.isEmpty());
        return result;
    }
}
