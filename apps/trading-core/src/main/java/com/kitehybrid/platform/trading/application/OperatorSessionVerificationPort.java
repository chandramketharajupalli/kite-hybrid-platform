package com.kitehybrid.platform.trading.application;

import com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Read already captured independent verification evidence. No credential verification,
 * networking, session writes or provider registration is implemented here.
 * VERIFIED_ASSERTION describes the boundary's assertion, not proof supplied by this record.
 * A future implementation must establish trust independently of cookies, claims and Spring
 * Authentication flags. Neither possession of this evidence nor a diagnostic match grants access.
 */
@FunctionalInterface
public interface OperatorSessionVerificationPort {
    Optional<Verification> verificationFor(SessionBinding binding);

    enum Trust { VERIFIED_ASSERTION, UNVERIFIED, REJECTED, UNAVAILABLE }
    enum CredentialKind { OPERATOR, BROKER, UNKNOWN }

    /** Server-selected opaque handle plus authentication event; never accept request claims as proof. */
    record SessionBinding(String opaqueHandle, String authenticationEvent) {
        public SessionBinding {
            opaqueHandle = text(opaqueHandle);
            authenticationEvent = text(authenticationEvent);
        }
        @Override public String toString() { return "SessionBinding[REDACTED]"; }
    }

    /** Captured initial grant, not a renewal's self-selected ceiling. No fresh event is inferred. */
    record OriginalGrant(String authenticationEvent, Instant authenticatedAt, Instant absoluteExpiresAt,
                         Set<Permission> permissions, Set<String> accounts) {
        public OriginalGrant {
            authenticationEvent = text(authenticationEvent);
            Objects.requireNonNull(authenticatedAt);
            Objects.requireNonNull(absoluteExpiresAt);
            if (!authenticatedAt.isBefore(absoluteExpiresAt)) throw new IllegalArgumentException("Invalid grant interval");
            permissions = Set.copyOf(permissions);
            accounts = OperatorSessionVerificationPort.accounts(accounts);
        }
        @Override public String toString() { return "OriginalGrant[REDACTED]"; }
    }

    record Verification(SessionBinding binding, String issuer, String subject, String audience,
                        String sessionEvidenceSha256, Trust trust, CredentialKind credentialKind,
                        Instant authenticatedAt, Instant absoluteExpiresAt, Instant lastActivityAt,
                        Instant inactivityExpiresAt, Instant verifiedAt,
                        OperatorIdentityProvider.Revocation revocation, String revocationAuthority,
                        Instant revocationCheckedAt, Set<Permission> permissions, Set<String> accounts,
                        OriginalGrant originalGrant) {
        public Verification {
            Objects.requireNonNull(binding);
            issuer = text(issuer); subject = text(subject); audience = text(audience);
            if (sessionEvidenceSha256 == null || !sessionEvidenceSha256.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Session evidence digest required");
            Objects.requireNonNull(trust); Objects.requireNonNull(credentialKind);
            Objects.requireNonNull(authenticatedAt); Objects.requireNonNull(absoluteExpiresAt);
            Objects.requireNonNull(lastActivityAt); Objects.requireNonNull(inactivityExpiresAt);
            Objects.requireNonNull(verifiedAt); Objects.requireNonNull(revocation);
            revocationAuthority = text(revocationAuthority); Objects.requireNonNull(revocationCheckedAt);
            permissions = Set.copyOf(permissions); accounts = OperatorSessionVerificationPort.accounts(accounts);
            Objects.requireNonNull(originalGrant);
        }
        @Override public String toString() {
            return "Verification[identity=REDACTED, trust=" + trust + ", revocation=" + revocation + "]";
        }
    }

    static OperatorSessionVerificationPort unavailable() { return binding -> Optional.empty(); }

    private static Set<String> accounts(Set<String> values) {
        Set<String> copy = Set.copyOf(values);
        copy.forEach(OperatorSessionVerificationPort::text);
        return copy;
    }

    private static String text(String value) {
        Objects.requireNonNull(value, "Binding required");
        if (value.isBlank() || value.length() > 256 || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid binding");
        return value;
    }
}
