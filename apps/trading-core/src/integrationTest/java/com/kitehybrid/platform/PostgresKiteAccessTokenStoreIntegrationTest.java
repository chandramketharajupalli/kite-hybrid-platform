package com.kitehybrid.platform;

import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.broker.application.auth.KiteTokenStoreException;
import com.kitehybrid.platform.broker.infrastructure.kite.PostgresKiteAccessTokenStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.TimeZone;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@Isolated("Temporarily sets the JVM default timezone for PostgreSQL JDBC startup")
class PostgresKiteAccessTokenStoreIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    private static final String STORE_ID = "c".repeat(64);
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final KiteAccessToken TOKEN = new KiteAccessToken("synthetic-durable-credential",
            Instant.parse("2026-09-20T04:00:00Z"), Instant.parse("2026-09-21T00:30:00Z"));
    private static TimeZone originalTimeZone;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        jdbc = newJdbcTemplate();
    }

    @AfterAll
    static void restoreTimezone() {
        if (originalTimeZone != null) TimeZone.setDefault(originalTimeZone);
    }

    @BeforeEach
    void clearDatabase() {
        jdbc.update("DELETE FROM trading.kite_access_tokens");
    }

    @Test
    void persistedTokenSurvivesNewConnectionAndAdapterAndDatabaseContainsOnlyCiphertext() {
        store(jdbc).save(TOKEN);

        byte[] ciphertext = jdbc.queryForObject(
                "SELECT token_ciphertext FROM trading.kite_access_tokens WHERE store_id = ?", byte[].class, STORE_ID);
        assertThat(new String(ciphertext, StandardCharsets.UTF_8)).doesNotContain(TOKEN.value());
        assertThat(jdbc.queryForObject("SELECT octet_length(nonce) FROM trading.kite_access_tokens", Integer.class))
                .isEqualTo(12);
        var loaded = store(newJdbcTemplate()).loadCurrent().orElseThrow();
        assertThat(loaded.value()).isEqualTo(TOKEN.value());
        assertThat(loaded.issuedAt()).isEqualTo(TOKEN.issuedAt());
        assertThat(loaded.expiresAt()).isEqualTo(TOKEN.expiresAt());
    }

    @Test
    void saveAtomicallyReplacesTheSingleCredentialAndClearPersists() {
        var store = store(jdbc);
        store.save(TOKEN);
        var replacement = new KiteAccessToken("synthetic-replacement", TOKEN.issuedAt(), TOKEN.expiresAt());

        store.save(replacement);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM trading.kite_access_tokens", Integer.class)).isEqualTo(1);
        assertThat(store(newJdbcTemplate()).loadCurrent().orElseThrow().value()).isEqualTo(replacement.value());
        store.clear();
        assertThat(store(newJdbcTemplate()).loadCurrent()).isEmpty();
    }

    @Test
    void differentApiKeysRemainIsolatedAndClearingOneKeepsTheOther() {
        var first = store(jdbc);
        var other = new PostgresKiteAccessTokenStore(jdbc, "d".repeat(64), KEY);
        first.save(TOKEN);
        assertThat(other.loadCurrent()).isEmpty();
        other.save(new KiteAccessToken("synthetic-other-account", TOKEN.issuedAt(), TOKEN.expiresAt()));

        first.clear();

        assertThat(first.loadCurrent()).isEmpty();
        assertThat(other.loadCurrent().orElseThrow().value()).isEqualTo("synthetic-other-account");
    }

    @Test
    void wrongKeyDoesNotExposeCredentialAndCanBeRecoveredByLocalClear() {
        store(jdbc).save(TOKEN);
        byte[] keyBytes = new byte[32];
        keyBytes[0] = 1;
        var wrongKey = new PostgresKiteAccessTokenStore(jdbc, STORE_ID,
                Base64.getEncoder().encodeToString(keyBytes));

        var failure = assertThrows(KiteTokenStoreException.class, wrongKey::loadCurrent);

        assertThat(failure.category()).isEqualTo(KiteTokenStoreException.Category.DECRYPTION);
        assertThat(failure.getCause()).isNull();
        assertThat(failure.getMessage()).doesNotContain(TOKEN.value(), KEY);
        new PostgresKiteAccessTokenStore(jdbc, STORE_ID, "").clear();
        assertThat(store(jdbc).loadCurrent()).isEmpty();
    }

    private static JdbcTemplate newJdbcTemplate() {
        return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword()));
    }

    private static PostgresKiteAccessTokenStore store(JdbcTemplate template) {
        return new PostgresKiteAccessTokenStore(template, STORE_ID, KEY);
    }
}
