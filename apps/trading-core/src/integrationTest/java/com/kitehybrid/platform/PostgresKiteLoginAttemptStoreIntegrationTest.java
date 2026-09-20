package com.kitehybrid.platform;

import com.kitehybrid.platform.broker.application.auth.KiteLoginAttempt;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptStoreException;
import com.kitehybrid.platform.broker.infrastructure.kite.PostgresKiteLoginAttemptStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@Isolated("Temporarily sets the JVM default timezone for PostgreSQL JDBC startup")
class PostgresKiteLoginAttemptStoreIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");
    private static final String STORE_ID = "a".repeat(64);
    private static final String OTHER_STORE_ID = "b".repeat(64);
    private static final String DIGEST = "c".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-20T04:00:00Z");
    private static final KiteLoginAttempt ATTEMPT = new KiteLoginAttempt(DIGEST, NOW, NOW.plusSeconds(600));
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
    void clearAttempts() {
        jdbc.update("DELETE FROM trading.kite_login_attempts");
    }

    @Test
    void persistedAttemptSurvivesNewAdapterAndConnectionAndContainsOnlyCorrelationDigestAndTimes() {
        store(jdbc).create(ATTEMPT);

        var row = jdbc.queryForMap("SELECT * FROM trading.kite_login_attempts WHERE store_id = ?", STORE_ID);
        assertThat(row).containsOnlyKeys("store_id", "nonce_digest", "created_at", "expires_at");
        assertThat(row.get("nonce_digest")).isEqualTo(DIGEST);
        assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(NOW);
        assertThat(((Timestamp) row.get("expires_at")).toInstant()).isEqualTo(ATTEMPT.expiresAt());

        assertThat(store(newJdbcTemplate()).consume(DIGEST, NOW.plusSeconds(1))).isTrue();
        assertThat(store(newJdbcTemplate()).consume(DIGEST, NOW.plusSeconds(2))).isFalse();
        assertThat(count(STORE_ID)).isZero();
    }

    @Test
    void concurrentCallbacksAcrossIndependentAdaptersHaveExactlyOneWinner() throws Exception {
        store(jdbc).create(ATTEMPT);
        int callbacks = 12;
        var ready = new CountDownLatch(callbacks);
        var start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(callbacks)) {
            for (int index = 0; index < callbacks; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test start timed out");
                    return store(newJdbcTemplate()).consume(DIGEST, NOW.plusSeconds(30));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successful = 0;
            for (Future<Boolean> result : results) {
                if (result.get(15, TimeUnit.SECONDS)) successful++;
            }
            assertThat(successful).isEqualTo(1);
        } finally {
            start.countDown();
        }
        assertThat(store(jdbc).consume(DIGEST, NOW.plusSeconds(31))).isFalse();
        assertThat(count(STORE_ID)).isZero();
    }

    @Test
    void missingMismatchFutureCreationAndExpiryFailClosedWithoutConsumingOtherAttempts() {
        var store = store(jdbc);
        assertThat(store.consume(DIGEST, NOW)).isFalse();
        store.create(ATTEMPT);

        assertThat(store.consume("d".repeat(64), NOW)).isFalse();
        assertThat(store.consume(DIGEST, NOW.minusSeconds(1))).isFalse();
        assertThat(count(STORE_ID)).isEqualTo(1);
        assertThat(store.consume(DIGEST, ATTEMPT.expiresAt())).isFalse();
        assertThat(store.consume(DIGEST, ATTEMPT.expiresAt().plusSeconds(1))).isFalse();
        assertThat(count(STORE_ID)).isEqualTo(1);
        assertThat(store.deleteExpired(ATTEMPT.expiresAt(), 10)).isEqualTo(1);
    }

    @Test
    void creationBoundaryIsInclusiveAndExpiryBoundaryIsExclusive() {
        var store = store(jdbc);
        store.create(ATTEMPT);
        assertThat(store.consume(DIGEST, NOW)).isTrue();
        store.create(new KiteLoginAttempt("d".repeat(64), NOW, ATTEMPT.expiresAt()));
        assertThat(store.consume("d".repeat(64), ATTEMPT.expiresAt().minusNanos(1000))).isTrue();
    }

    @Test
    void attemptsAreScopedToApiKeyNamespaceEvenWithTheSameNonceDigest() {
        var first = store(jdbc);
        var other = new PostgresKiteLoginAttemptStore(newJdbcTemplate(), OTHER_STORE_ID);
        first.create(ATTEMPT);
        assertThat(other.consume(DIGEST, NOW)).isFalse();
        other.create(ATTEMPT);

        assertThat(first.consume(DIGEST, NOW)).isTrue();
        assertThat(count(STORE_ID)).isZero();
        assertThat(count(OTHER_STORE_ID)).isEqualTo(1);
        assertThat(other.consume(DIGEST, NOW)).isTrue();
    }

    @Test
    void cleanupIsBoundedKeepsUnexpiredRowsAndCannotDeleteAnotherNamespacesRows() {
        var first = store(jdbc);
        var other = new PostgresKiteLoginAttemptStore(newJdbcTemplate(), OTHER_STORE_ID);
        for (int index = 1; index <= 7; index++) {
            first.create(new KiteLoginAttempt(digest(index), NOW.minusSeconds(600), NOW));
        }
        for (int index = 8; index <= 9; index++) {
            first.create(new KiteLoginAttempt(digest(index), NOW, NOW.plusSeconds(600)));
        }
        other.create(new KiteLoginAttempt(DIGEST, NOW.minusSeconds(600), NOW));

        assertThat(first.deleteExpired(NOW, 3)).isEqualTo(3);
        assertThat(count(STORE_ID)).isEqualTo(6);
        assertThat(count(OTHER_STORE_ID)).isEqualTo(1);
        assertThat(first.deleteExpired(NOW, 1000)).isEqualTo(4);
        assertThat(first.deleteExpired(NOW, 1000)).isZero();
        assertThat(count(STORE_ID)).isEqualTo(2);
        assertThat(first.consume(digest(8), NOW)).isTrue();
        assertThat(first.consume(digest(9), NOW)).isTrue();
        assertThat(count(OTHER_STORE_ID)).isEqualTo(1);
    }

    @Test
    void duplicateCreateCannotExtendExistingAttemptsExpiryAndExposesNoDatabaseDetails() {
        var store = store(jdbc);
        store.create(ATTEMPT);

        var failure = assertThrows(KiteLoginAttemptStoreException.class,
                () -> store.create(new KiteLoginAttempt(DIGEST, NOW.plusSeconds(60), NOW.plusSeconds(660))));

        assertThat(failure).hasMessage("Kite login attempt storage failed").hasNoCause();
        assertThat(store.consume(DIGEST, NOW.plusSeconds(600))).isFalse();
    }

    @Test
    void databaseConstraintsRejectMalformedDigestsAndAttemptsLastingOverTenMinutes() {
        String insert = "INSERT INTO trading.kite_login_attempts VALUES (?, ?, ?, ?)";
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(insert, STORE_ID, "synthetic-raw-nonce", Timestamp.from(NOW),
                        Timestamp.from(NOW.plusSeconds(600))));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(insert, STORE_ID, DIGEST, Timestamp.from(NOW),
                        Timestamp.from(NOW.plusSeconds(601))));
        assertThat(count(STORE_ID)).isZero();
    }

    private static int count(String storeId) {
        return jdbc.queryForObject("SELECT count(*) FROM trading.kite_login_attempts WHERE store_id = ?",
                Integer.class, storeId);
    }

    private static String digest(int number) {
        return String.format("%064x", number);
    }

    private static JdbcTemplate newJdbcTemplate() {
        return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword()));
    }

    private static PostgresKiteLoginAttemptStore store(JdbcTemplate template) {
        return new PostgresKiteLoginAttemptStore(template, STORE_ID);
    }
}
