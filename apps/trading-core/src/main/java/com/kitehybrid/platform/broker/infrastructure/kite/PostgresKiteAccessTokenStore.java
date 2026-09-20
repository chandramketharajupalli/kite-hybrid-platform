package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.broker.application.auth.KiteAccessTokenStore;
import com.kitehybrid.platform.broker.application.auth.KiteTokenStoreException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.kitehybrid.platform.broker.application.auth.KiteTokenStoreException.Category.*;

/** AES-256-GCM encrypted credentials in the existing PostgreSQL database. */
public final class PostgresKiteAccessTokenStore implements KiteAccessTokenStore {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SELECT = """
            SELECT token_ciphertext, nonce, issued_at, expires_at
            FROM trading.kite_access_tokens WHERE store_id = ?
            """;
    private static final String UPSERT = """
            INSERT INTO trading.kite_access_tokens (store_id, token_ciphertext, nonce, issued_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (store_id) DO UPDATE SET token_ciphertext = EXCLUDED.token_ciphertext,
                nonce = EXCLUDED.nonce, issued_at = EXCLUDED.issued_at, expires_at = EXCLUDED.expires_at
            """;

    private final JdbcTemplate jdbc;
    private final String storeId;
    private final String base64EncryptionKey;

    public PostgresKiteAccessTokenStore(JdbcTemplate jdbc, String storeId, String base64EncryptionKey) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.storeId = storeId;
        this.base64EncryptionKey = base64EncryptionKey;
    }

    @Override
    public Optional<KiteAccessToken> loadCurrent() {
        SecretKeySpec key = encryptionKey();
        try {
            return jdbc.query(SELECT, (row, index) -> {
                byte[] ciphertext = row.getBytes("token_ciphertext");
                byte[] nonce = row.getBytes("nonce");
                byte[] plaintext = decrypt(key, nonce, ciphertext);
                try {
                    return new KiteAccessToken(new String(plaintext, StandardCharsets.UTF_8),
                            row.getTimestamp("issued_at").toInstant(), row.getTimestamp("expires_at").toInstant());
                } finally {
                    Arrays.fill(plaintext, (byte) 0);
                }
            }, storeId).stream().findFirst();
        } catch (KiteTokenStoreException safeFailure) {
            throw safeFailure;
        } catch (RuntimeException ignored) {
            throw new KiteTokenStoreException(STORAGE);
        }
    }

    @Override
    public void save(KiteAccessToken token) {
        SecretKeySpec key = encryptionKey();
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        byte[] plaintext = token.value().getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce);
            byte[] ciphertext = cipher.doFinal(plaintext);
            jdbc.update(UPSERT, storeId, ciphertext, nonce,
                    Timestamp.from(token.issuedAt()), Timestamp.from(token.expiresAt()));
        } catch (GeneralSecurityException | RuntimeException ignored) {
            throw new KiteTokenStoreException(STORAGE);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    @Override
    public void clear() {
        validateStoreId();
        try {
            jdbc.update("DELETE FROM trading.kite_access_tokens WHERE store_id = ?", storeId);
        } catch (RuntimeException ignored) {
            throw new KiteTokenStoreException(STORAGE);
        }
    }

    private SecretKeySpec encryptionKey() {
        validateStoreId();
        byte[] bytes = null;
        try {
            bytes = Base64.getDecoder().decode(base64EncryptionKey == null ? "" : base64EncryptionKey);
            if (bytes.length != 32) throw new KiteTokenStoreException(CONFIGURATION);
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException ignored) {
            throw new KiteTokenStoreException(CONFIGURATION);
        } finally {
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
    }

    private void validateStoreId() {
        if (storeId == null || !storeId.matches("[0-9a-f]{64}")) {
            throw new KiteTokenStoreException(CONFIGURATION);
        }
    }

    private byte[] decrypt(SecretKeySpec key, byte[] nonce, byte[] ciphertext) {
        if (nonce == null || nonce.length != NONCE_BYTES || ciphertext == null || ciphertext.length < 17) {
            throw new KiteTokenStoreException(DECRYPTION);
        }
        try {
            return cipher(Cipher.DECRYPT_MODE, key, nonce).doFinal(ciphertext);
        } catch (GeneralSecurityException | RuntimeException ignored) {
            throw new KiteTokenStoreException(DECRYPTION);
        }
    }

    private Cipher cipher(int mode, SecretKeySpec key, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(storeId.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
