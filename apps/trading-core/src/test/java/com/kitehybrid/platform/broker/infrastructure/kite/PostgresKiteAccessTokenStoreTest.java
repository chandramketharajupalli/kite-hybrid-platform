package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.broker.application.auth.KiteTokenStoreException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static com.kitehybrid.platform.broker.application.auth.KiteTokenStoreException.Category.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Real JCE encryption with a tiny JDBC boundary double; no database or network required. */
class PostgresKiteAccessTokenStoreTest {
    private static final String STORE_ID = "a".repeat(64);
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String TOKEN_VALUE = "synthetic-token-never-log";
    private static final Instant ISSUED = Instant.parse("2026-09-20T04:00:00Z");
    private static final KiteAccessToken TOKEN = new KiteAccessToken(TOKEN_VALUE, ISSUED,
            Instant.parse("2026-09-21T00:30:00Z"));

    @Test
    void credentialOnlyReportsRedactedValueAndHasExclusiveExpiry() {
        assertThat(TOKEN.toString()).contains("REDACTED").doesNotContain(TOKEN_VALUE);
        assertThat(TOKEN.isUsableAt(ISSUED.minusNanos(1))).isFalse();
        assertThat(TOKEN.isUsableAt(ISSUED)).isTrue();
        assertThat(TOKEN.isUsableAt(TOKEN.expiresAt().minusNanos(1))).isTrue();
        assertThat(TOKEN.isUsableAt(TOKEN.expiresAt())).isFalse();
        assertThrows(IllegalArgumentException.class, () -> new KiteAccessToken(TOKEN_VALUE, ISSUED, ISSUED));
    }

    @Test
    void missingRowReturnsEmpty() {
        assertThat(store(new MemoryJdbc()).loadCurrent()).isEmpty();
    }

    @Test
    void encryptedRoundTripSurvivesNewStoreInstanceAndPreservesValidity() {
        var jdbc = new MemoryJdbc();
        store(jdbc).save(TOKEN);

        assertThat(new String(jdbc.ciphertext, StandardCharsets.UTF_8)).doesNotContain(TOKEN_VALUE);
        assertThat(jdbc.ciphertext.length).isEqualTo(TOKEN_VALUE.getBytes(StandardCharsets.UTF_8).length + 16);
        KiteAccessToken loaded = store(jdbc).loadCurrent().orElseThrow();
        assertThat(loaded.value()).isEqualTo(TOKEN_VALUE);
        assertThat(loaded.issuedAt()).isEqualTo(TOKEN.issuedAt());
        assertThat(loaded.expiresAt()).isEqualTo(TOKEN.expiresAt());
    }

    @Test
    void overwritingUsesFreshNonceAndCiphertextEvenForSameCredential() {
        var jdbc = new MemoryJdbc();
        var store = store(jdbc);
        store.save(TOKEN);
        byte[] firstCiphertext = jdbc.ciphertext.clone();
        byte[] firstNonce = jdbc.nonce.clone();

        store.save(TOKEN);

        assertThat(jdbc.nonce).hasSize(12).isNotEqualTo(firstNonce);
        assertThat(jdbc.ciphertext).isNotEqualTo(firstCiphertext);
        assertThat(store.loadCurrent().orElseThrow().value()).isEqualTo(TOKEN_VALUE);
    }

    @Test
    void modifiedCiphertextFailsAuthenticationWithoutLeakingData() {
        var jdbc = new MemoryJdbc();
        store(jdbc).save(TOKEN);
        jdbc.ciphertext[0] ^= 1;

        assertSafe(assertThrows(KiteTokenStoreException.class, () -> store(jdbc).loadCurrent()), DECRYPTION);
    }

    @Test
    void wrongKeyFailsAuthenticationWithoutLeakingData() {
        var jdbc = new MemoryJdbc();
        store(jdbc).save(TOKEN);
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        var wrongKeyStore = new PostgresKiteAccessTokenStore(jdbc, STORE_ID,
                Base64.getEncoder().encodeToString(otherKey));

        assertSafe(assertThrows(KiteTokenStoreException.class, wrongKeyStore::loadCurrent), DECRYPTION);
    }

    @Test
    void ciphertextCannotBeMovedToAnotherApiKeyNamespace() {
        var jdbc = new MemoryJdbc();
        store(jdbc).save(TOKEN);
        // This JDBC double deliberately returns the original row for a different store ID.
        var otherStore = new PostgresKiteAccessTokenStore(jdbc, "b".repeat(64), ENCRYPTION_KEY);

        assertSafe(assertThrows(KiteTokenStoreException.class, otherStore::loadCurrent), DECRYPTION);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid-base64", "AA=="})
    void absentOrInvalidEncryptionKeyFailsLazilyBeforeDatabaseWork(String key) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new PostgresKiteAccessTokenStore(jdbc, STORE_ID, key);

        assertSafe(assertThrows(KiteTokenStoreException.class, store::loadCurrent), CONFIGURATION);
        assertSafe(assertThrows(KiteTokenStoreException.class, () -> store.save(TOKEN)), CONFIGURATION);
        verifyNoInteractions(jdbc);
    }

    @Test
    void clearRemovesOnlyStoredCredentialAndAllowsRecoveryWithoutKey() {
        var jdbc = new MemoryJdbc();
        store(jdbc).save(TOKEN);

        new PostgresKiteAccessTokenStore(jdbc, STORE_ID, "").clear();

        assertThat(store(jdbc).loadCurrent()).isEmpty();
        assertThat(jdbc.lastId).isEqualTo(STORE_ID);
    }

    @Test
    void databaseFailureHasNoRawMessageCauseOrSql() {
        var jdbc = new MemoryJdbc();
        jdbc.fail = true;

        assertSafe(assertThrows(KiteTokenStoreException.class, () -> store(jdbc).loadCurrent()), STORAGE);
        assertSafe(assertThrows(KiteTokenStoreException.class, () -> store(jdbc).save(TOKEN)), STORAGE);
        assertSafe(assertThrows(KiteTokenStoreException.class, () -> store(jdbc).clear()), STORAGE);
    }

    private static PostgresKiteAccessTokenStore store(JdbcTemplate jdbc) {
        return new PostgresKiteAccessTokenStore(jdbc, STORE_ID, ENCRYPTION_KEY);
    }

    private static void assertSafe(KiteTokenStoreException error, KiteTokenStoreException.Category category) {
        assertThat(error.category()).isEqualTo(category);
        assertThat(error.getCause()).isNull();
        assertThat(error.getSuppressed()).isEmpty();
        var stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        assertThat(stack.toString()).doesNotContain(TOKEN_VALUE, ENCRYPTION_KEY, "sensitive-database-detail");
    }

    private static final class MemoryJdbc extends JdbcTemplate {
        private byte[] ciphertext;
        private byte[] nonce;
        private Timestamp issuedAt;
        private Timestamp expiresAt;
        private String lastId;
        private boolean fail;

        @Override
        public int update(String sql, Object... arguments) {
            if (fail) throw databaseFailure();
            lastId = (String) arguments[0];
            if (sql.startsWith("DELETE")) {
                ciphertext = null;
            } else {
                assertThat(sql).contains("ON CONFLICT (store_id) DO UPDATE");
                ciphertext = ((byte[]) arguments[1]).clone();
                nonce = ((byte[]) arguments[2]).clone();
                issuedAt = (Timestamp) arguments[3];
                expiresAt = (Timestamp) arguments[4];
            }
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... arguments) {
            if (fail) throw databaseFailure();
            if (ciphertext == null) return List.of();
            try {
                ResultSet result = mock(ResultSet.class);
                when(result.getBytes("token_ciphertext")).thenReturn(ciphertext.clone());
                when(result.getBytes("nonce")).thenReturn(nonce.clone());
                when(result.getTimestamp("issued_at")).thenReturn(issuedAt);
                when(result.getTimestamp("expires_at")).thenReturn(expiresAt);
                return List.of(mapper.mapRow(result, 0));
            } catch (SQLException error) {
                throw new IllegalStateException(error);
            }
        }

        private static RuntimeException databaseFailure() {
            return new DataAccessResourceFailureException("sensitive-database-detail: " + TOKEN_VALUE);
        }
    }
}
