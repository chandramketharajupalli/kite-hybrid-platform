package com.kitehybrid.platform.trading.application;

import com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Inactive server-side registry boundary. No cookie, assertion or record grants access. */
public interface OperatorSessionRegistryPort {
    enum Trust { VERIFIED_OPERATOR_ASSERTION, UNVERIFIED, SYNTHETIC, BROKER }
    record Event(String issuer, String subject, String audience, UUID authenticationEvent,
                 Instant authenticatedAt, Trust trust) {
        public Event {
            issuer = text(issuer); subject = text(subject); audience = text(audience);
            Objects.requireNonNull(authenticationEvent); Objects.requireNonNull(authenticatedAt);
            Objects.requireNonNull(trust);
        }
        @Override public String toString() { return "Event[REDACTED]"; }
    }
    record Policy(String issuer, String subject, String audience, String version, String revocationAuthority,
                  Duration absoluteLifetime, Duration inactivity, Duration freshness,
                  Set<Permission> permissions, Set<String> accounts) {
        public Policy {
            issuer = text(issuer); subject = text(subject); audience = text(audience); version = text(version);
            revocationAuthority = text(revocationAuthority);
            positive(absoluteLifetime); positive(inactivity); positive(freshness);
            if (inactivity.compareTo(absoluteLifetime) > 0) throw new IllegalArgumentException("Invalid timeout");
            permissions = Set.copyOf(permissions); accounts = copyAccounts(accounts);
            if (permissions.isEmpty() || accounts.isEmpty()) throw new IllegalArgumentException("Empty grant");
        }
        @Override public String toString() { return "Policy[REDACTED]"; }
    }
    record Session(String digest, Event event, Instant absoluteExpiresAt, Instant lastActivityAt,
                   Duration inactivity, Set<Permission> permissions, Set<String> accounts,
                   String policyVersion, boolean revoked, long revision) {
        public Session {
            digest = OperatorSessionRegistryPort.digest(digest); Objects.requireNonNull(event); Objects.requireNonNull(absoluteExpiresAt);
            Objects.requireNonNull(lastActivityAt); positive(inactivity);
            permissions = Set.copyOf(permissions); accounts = copyAccounts(accounts); policyVersion = text(policyVersion);
            if (event.trust() != Trust.VERIFIED_OPERATOR_ASSERTION || revision < 1 || permissions.isEmpty()
                    || accounts.isEmpty() || !event.authenticatedAt().isBefore(absoluteExpiresAt)
                    || lastActivityAt.isBefore(event.authenticatedAt()) || !lastActivityAt.isBefore(absoluteExpiresAt)
                    || inactivity.compareTo(Duration.between(event.authenticatedAt(), absoluteExpiresAt)) > 0)
                throw new IllegalArgumentException("Invalid registry record");
        }
        public boolean activeAt(Instant now) {
            return !revoked && !now.isBefore(event.authenticatedAt()) && !now.isBefore(lastActivityAt)
                    && now.isBefore(absoluteExpiresAt) && now.isBefore(lastActivityAt.plus(inactivity));
        }
        public Session changed(String nextDigest, Instant activity, boolean nextRevoked) {
            return new Session(nextDigest, event, absoluteExpiresAt, activity, inactivity, permissions,
                    accounts, policyVersion, nextRevoked, Math.addExact(revision, 1));
        }
        @Override public String toString() { return "Session[REDACTED, revoked=" + revoked + "]"; }
    }
    record Observation(Session session, Instant observedAt) {
        public Observation { Objects.requireNonNull(session); Objects.requireNonNull(observedAt); }
        @Override public String toString() { return "Observation[REDACTED]"; }
    }
    Optional<Observation> find(String digest);
    /** Atomically revoke previous sessions for this exact issuer/subject and insert the original grant. */
    boolean establish(Session session);
    /** Compare-and-set rotation; old binding is retained revoked, never reusable. */
    boolean rotate(String digest, long revision, String nextDigest, Instant now);
    boolean touch(String digest, long revision, Instant now);
    boolean revoke(String digest, Instant now);

    static String text(String value) {
        if (value == null || value.isBlank() || value.length() > 256
                || value.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid field");
        return value;
    }
    static String digest(String value) {
        if (value == null || !value.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid digest");
        return value;
    }
    private static Set<String> copyAccounts(Set<String> values) {
        Set<String> copy = Set.copyOf(values); copy.forEach(OperatorSessionRegistryPort::text); return copy;
    }
    private static void positive(Duration value) {
        Objects.requireNonNull(value);
        if (value.isNegative() || value.isZero() || value.getNano() != 0 || value.getSeconds() > 86400)
            throw new IllegalArgumentException("Invalid duration");
    }
}
