package com.kitehybrid.platform.broker.application.auth;

import java.time.Instant;
import java.util.Objects;

/** Authentication credential: deliberately not a record, to keep its value out of diagnostics. */
public final class KiteAccessToken {
    private final String value;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public KiteAccessToken(String value, Instant issuedAt, Instant expiresAt) {
        if (value == null || value.isBlank() || value.length() > 4096
                || value.chars().anyMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character))) {
            throw new IllegalArgumentException("Invalid Kite access token");
        }
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("Invalid Kite access token validity period");
        }
        this.value = value;
    }

    public String value() { return value; }
    public Instant issuedAt() { return issuedAt; }
    public Instant expiresAt() { return expiresAt; }

    public boolean isUsableAt(Instant instant) {
        return !instant.isBefore(issuedAt) && instant.isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return "KiteAccessToken[value=REDACTED, issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + "]";
    }
}
