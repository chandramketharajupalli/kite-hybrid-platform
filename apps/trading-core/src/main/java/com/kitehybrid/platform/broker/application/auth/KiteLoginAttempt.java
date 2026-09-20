package com.kitehybrid.platform.broker.application.auth;

import java.time.Duration;
import java.time.Instant;

/** Durable, short-lived correlation data. Only a digest of the browser nonce is retained. */
public record KiteLoginAttempt(String nonceDigest, Instant createdAt, Instant expiresAt) {
    public KiteLoginAttempt {
        if (nonceDigest == null || !nonceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid Kite login attempt digest");
        }
        if (createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)
                || Duration.between(createdAt, expiresAt).compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("Invalid Kite login attempt validity period");
        }
    }

    @Override
    public String toString() {
        return "KiteLoginAttempt[nonceDigest=REDACTED, createdAt=" + createdAt
                + ", expiresAt=" + expiresAt + "]";
    }
}
