package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.KiteLoginAttempt;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptStore;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptStoreException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL owns login-attempt lifetime and atomic single-use consumption across app instances. */
public final class PostgresKiteLoginAttemptStore implements KiteLoginAttemptStore {
    private static final String INSERT = """
            INSERT INTO trading.kite_login_attempts (store_id, nonce_digest, created_at, expires_at)
            VALUES (?, ?, ?, ?)
            """;
    private static final String CONSUME = """
            DELETE FROM trading.kite_login_attempts
            WHERE store_id = ? AND nonce_digest = ? AND created_at <= ? AND expires_at > ?
            """;
    private static final String DELETE_EXPIRED = """
            WITH expired AS (
                SELECT store_id, nonce_digest FROM trading.kite_login_attempts
                WHERE store_id = ? AND expires_at <= ?
                ORDER BY expires_at, nonce_digest
                LIMIT ? FOR UPDATE SKIP LOCKED
            )
            DELETE FROM trading.kite_login_attempts AS attempts USING expired
            WHERE attempts.store_id = expired.store_id AND attempts.nonce_digest = expired.nonce_digest
            """;

    private final JdbcTemplate jdbc;
    private final String storeId;

    public PostgresKiteLoginAttemptStore(JdbcTemplate jdbc, String storeId) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        if (storeId == null || !storeId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid Kite login attempt store identifier");
        }
        this.storeId = storeId;
    }

    @Override
    public void create(KiteLoginAttempt attempt) {
        if (attempt == null) throw new IllegalArgumentException("Invalid Kite login attempt");
        try {
            if (jdbc.update(INSERT, storeId, attempt.nonceDigest(), Timestamp.from(attempt.createdAt()),
                    Timestamp.from(attempt.expiresAt())) != 1) {
                throw new KiteLoginAttemptStoreException();
            }
        } catch (RuntimeException ignored) {
            throw new KiteLoginAttemptStoreException();
        }
    }

    @Override
    public boolean consume(String nonceDigest, Instant now) {
        if (nonceDigest == null || !nonceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid Kite login attempt digest");
        }
        validateTime(now);
        try {
            Timestamp timestamp = Timestamp.from(now);
            return jdbc.update(CONSUME, storeId, nonceDigest, timestamp, timestamp) == 1;
        } catch (RuntimeException ignored) {
            throw new KiteLoginAttemptStoreException();
        }
    }

    @Override
    public int deleteExpired(Instant now, int limit) {
        validateTime(now);
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Invalid Kite login attempt cleanup limit");
        }
        try {
            return jdbc.update(DELETE_EXPIRED, storeId, Timestamp.from(now), limit);
        } catch (RuntimeException ignored) {
            throw new KiteLoginAttemptStoreException();
        }
    }

    private static void validateTime(Instant now) {
        if (now == null) throw new IllegalArgumentException("Invalid Kite login attempt time");
    }
}
