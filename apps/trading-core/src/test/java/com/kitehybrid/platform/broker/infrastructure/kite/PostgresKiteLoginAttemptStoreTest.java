package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.KiteLoginAttempt;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptStoreException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostgresKiteLoginAttemptStoreTest {
    private static final String STORE_ID = "a".repeat(64);
    private static final String DIGEST = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-20T04:00:00Z");
    private static final KiteLoginAttempt ATTEMPT = new KiteLoginAttempt(DIGEST, NOW, NOW.plusSeconds(600));

    @Test
    void attemptRetainsOnlyDigestAndValidityAndRedactsItsRepresentation() {
        assertThat(ATTEMPT.nonceDigest()).isEqualTo(DIGEST);
        assertThat(ATTEMPT.createdAt()).isEqualTo(NOW);
        assertThat(ATTEMPT.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(ATTEMPT.toString()).contains("nonceDigest=REDACTED").doesNotContain(DIGEST);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"synthetic-raw-nonce", "ABCDEF", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb "})
    void invalidDigestIsRejectedBeforePersistenceWithoutEchoingIt(String digest) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new PostgresKiteLoginAttemptStore(jdbc, STORE_ID);

        assertThat(assertThrows(IllegalArgumentException.class,
                () -> new KiteLoginAttempt(digest, NOW, NOW.plusSeconds(600))))
                .hasMessage("Invalid Kite login attempt digest");
        assertThat(assertThrows(IllegalArgumentException.class, () -> store.consume(digest, NOW)))
                .hasMessage("Invalid Kite login attempt digest");
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 601})
    void recordRejectsInvalidOrExcessiveLifetime(long seconds) {
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> new KiteLoginAttempt(DIGEST, NOW, NOW.plusSeconds(seconds))))
                .hasMessage("Invalid Kite login attempt validity period");
    }

    @Test
    void nullTimestampsAndEvenOneNanosecondOverTenMinutesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new KiteLoginAttempt(DIGEST, null, NOW));
        assertThrows(IllegalArgumentException.class, () -> new KiteLoginAttempt(DIGEST, NOW, null));
        assertThrows(IllegalArgumentException.class,
                () -> new KiteLoginAttempt(DIGEST, NOW, NOW.plusSeconds(600).plusNanos(1)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"synthetic-api-key", "Aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rawOrMalformedStoreIdentifierCannotReachDatabase(String storeId) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        assertThat(assertThrows(IllegalArgumentException.class,
                () -> new PostgresKiteLoginAttemptStore(jdbc, storeId)))
                .hasMessage("Invalid Kite login attempt store identifier");

        verifyNoInteractions(jdbc);
    }

    @Test
    void createPassesOnlyStoreDigestNonceDigestAndValidityToJdbc() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        new PostgresKiteLoginAttemptStore(jdbc, STORE_ID).create(ATTEMPT);

        verify(jdbc).update(anyString(), eq(STORE_ID), eq(DIGEST),
                eq(Timestamp.from(NOW)), eq(Timestamp.from(ATTEMPT.expiresAt())));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void createDoesNotReportSuccessWhenNoAttemptWasStored() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        assertThrows(KiteLoginAttemptStoreException.class,
                () -> new PostgresKiteLoginAttemptStore(jdbc, STORE_ID).create(ATTEMPT));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void consumeReportsWhetherAnAttemptWasAtomicallyDeleted(int affectedRows) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(affectedRows);

        boolean consumed = new PostgresKiteLoginAttemptStore(jdbc, STORE_ID).consume(DIGEST, NOW);

        assertThat(consumed).isEqualTo(affectedRows == 1);
        verify(jdbc).update(anyString(), eq(STORE_ID), eq(DIGEST),
                eq(Timestamp.from(NOW)), eq(Timestamp.from(NOW)));
    }

    @Test
    void cleanupPassesItsBoundToDatabaseAndReturnsRemovedCount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(3);

        assertThat(new PostgresKiteLoginAttemptStore(jdbc, STORE_ID).deleteExpired(NOW, 5)).isEqualTo(3);

        verify(jdbc).update(anyString(), eq(STORE_ID), eq(Timestamp.from(NOW)), eq(5));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1001, Integer.MAX_VALUE})
    void invalidCleanupLimitIsRejectedBeforeDatabaseWork(int limit) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        assertThat(assertThrows(IllegalArgumentException.class,
                () -> new PostgresKiteLoginAttemptStore(jdbc, STORE_ID).deleteExpired(NOW, limit)))
                .hasMessage("Invalid Kite login attempt cleanup limit");

        verifyNoInteractions(jdbc);
    }

    @Test
    void missingAttemptOrOperationTimeIsRejectedBeforeDatabaseWork() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new PostgresKiteLoginAttemptStore(jdbc, STORE_ID);

        assertThrows(IllegalArgumentException.class, () -> store.create(null));
        assertThrows(IllegalArgumentException.class, () -> store.consume(DIGEST, null));
        assertThrows(IllegalArgumentException.class, () -> store.deleteExpired(null, 10));

        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void databaseFailureCannotLeakOriginalSqlParametersMessageOrCause(Operation operation) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DataAccessResourceFailureException(
                "sensitive-database-detail " + STORE_ID + " " + DIGEST + " synthetic-request-token"));
        var store = new PostgresKiteLoginAttemptStore(jdbc, STORE_ID);

        var failure = assertThrows(KiteLoginAttemptStoreException.class, () -> {
            switch (operation) {
                case CREATE -> store.create(ATTEMPT);
                case CONSUME -> store.consume(DIGEST, NOW);
                case DELETE_EXPIRED -> store.deleteExpired(NOW, 10);
            }
        });

        assertThat(failure).hasMessage("Kite login attempt storage failed").hasNoCause();
        assertThat(failure.getSuppressed()).isEmpty();
        var stack = new StringWriter();
        failure.printStackTrace(new PrintWriter(stack));
        assertThat(stack.toString()).doesNotContain("sensitive-database-detail", STORE_ID, DIGEST,
                "synthetic-request-token", "DataAccessResourceFailureException");
    }

    private enum Operation { CREATE, CONSUME, DELETE_EXPIRED }
}
