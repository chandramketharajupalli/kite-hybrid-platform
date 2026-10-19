package com.kitehybrid.platform.trading.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable identity claims, not proof of authentication. No production identity issuer is
 * integrated in this source-only milestone. Never populate this contract from a broker token.
 */
public record OperatorPrincipal(
        String issuer,
        String subject,
        Provenance provenance,
        Set<Permission> permissions,
        Instant validFrom,
        Instant expiresAt) {

    public enum Provenance { UNVERIFIED_CLAIM, SYNTHETIC_FIXTURE }

    public enum Permission {
        INSTRUMENTS_READ("trading:instruments:read"),
        STRATEGIES_READ("trading:strategies:read"),
        QUALIFICATIONS_READ("trading:qualifications:read"),
        SIGNALS_READ("trading:signals:read"),
        POSITIONS_READ("trading:positions:read"),
        ORDERS_READ("trading:orders:read"),
        RISK_READ("trading:risk:read");

        private final String value;

        Permission(String value) { this.value = value; }

        public String value() { return value; }
    }

    public OperatorPrincipal {
        issuer = identityPart(issuer);
        subject = identityPart(subject);
        Objects.requireNonNull(provenance, "Identity provenance required");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "Permissions required"));
        Objects.requireNonNull(validFrom, "Validity start required");
        Objects.requireNonNull(expiresAt, "Expiry required");
        if (!validFrom.isBefore(expiresAt)) {
            throw new IllegalArgumentException("Validity interval must be nonempty");
        }
    }

    public boolean validAt(Instant now) {
        Objects.requireNonNull(now, "Evaluation time required");
        return !now.isBefore(validFrom) && now.isBefore(expiresAt);
    }

    /** Claims, validity and permissions cannot establish production authentication. */
    public boolean authenticatedProductionIdentity() { return false; }

    @Override
    public String toString() {
        return "OperatorPrincipal[identity=REDACTED, provenance=" + provenance + "]";
    }

    private static String identityPart(String value) {
        Objects.requireNonNull(value, "Identity component required");
        if (value.isBlank() || value.length() > 256
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid identity component");
        }
        return value;
    }
}
