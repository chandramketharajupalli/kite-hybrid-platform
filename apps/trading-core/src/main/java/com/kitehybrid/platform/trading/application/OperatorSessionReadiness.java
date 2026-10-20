package com.kitehybrid.platform.trading.application;

import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import static com.kitehybrid.platform.trading.application.OperatorSessionVerificationPort.*;

/**
 * Deterministic, inactive readiness assessment. No authentication, HTTP access, data query,
 * session renewal or production registration. Even a diagnostic match is not a trust grant.
 */
public final class OperatorSessionReadiness {
    public enum Reason {
        SESSION_UNAVAILABLE, VERIFICATION_UNAVAILABLE, VERIFICATION_REJECTED, UNVERIFIED,
        SYNTHETIC_IDENTITY, NON_OPERATOR_CREDENTIAL, BINDING_MISMATCH, INVALID_TIME_WINDOW,
        INACTIVITY_EXPIRED, REVOKED, REVOCATION_UNAVAILABLE, RENEWAL_ESCALATION,
        METHOD_NOT_ALLOWED, UNKNOWN_ENDPOINT, MISSING_PERMISSION, ACCOUNT_DENIED,
        DIAGNOSTIC_MATCH_NOT_AUTHORIZATION
    }

    public record Result(Reason reason) {
        public Result { Objects.requireNonNull(reason); }
        public boolean diagnosticReady() { return reason == Reason.DIAGNOSTIC_MATCH_NOT_AUTHORIZATION; }
        public boolean productionAuthorized() { return false; }
    }

    /** Independent server policy, never assembled from caller-selected entitlements. */
    public record Policy(String issuer, String audience, String revocationAuthority,
                         String method, String endpoint, String selectedAccount,
                         Set<String> accounts, Set<Permission> permissions,
                         Duration maximumRevocationAge, Duration maximumVerificationAge,
                         Duration maximumInactivity) {
        public Policy {
            issuer = text(issuer); audience = text(audience); revocationAuthority = text(revocationAuthority);
            method = text(method); endpoint = text(endpoint); selectedAccount = text(selectedAccount);
            accounts = Set.copyOf(accounts); accounts.forEach(OperatorSessionReadiness::text);
            permissions = Set.copyOf(permissions);
            positive(maximumRevocationAge); positive(maximumVerificationAge); positive(maximumInactivity);
        }
        @Override public String toString() { return "Policy[scope=REDACTED]"; }
    }

    private final OperatorIdentityProvider identity;
    private final OperatorSessionVerificationPort verification;
    private final Clock clock;
    private final Policy policy;
    private final SessionBinding binding;

    public OperatorSessionReadiness(OperatorIdentityProvider identity, OperatorSessionVerificationPort verification,
                                   Clock clock, Policy policy, SessionBinding binding) {
        this.identity = Objects.requireNonNull(identity); this.verification = Objects.requireNonNull(verification);
        this.clock = Objects.requireNonNull(clock); this.policy = Objects.requireNonNull(policy);
        this.binding = Objects.requireNonNull(binding);
    }

    public Result assess() {
        try {
            if (!"GET".equals(policy.method())) return result(Reason.METHOD_NOT_ALLOWED);
            var required = TradingApiAuthorization.requiredPermission(policy.method(), policy.endpoint());
            if (required.isEmpty()) return result(Reason.UNKNOWN_ENDPOINT);
            var captured = identity.currentSession();
            if (captured == null || captured.isEmpty()) return result(Reason.SESSION_UNAVAILABLE);
            var session = captured.get();
            var principal = session.principal();
            if (principal.provenance() == OperatorPrincipal.Provenance.SYNTHETIC_FIXTURE)
                return result(Reason.SYNTHETIC_IDENTITY);
            var independentlyCaptured = verification.verificationFor(binding);
            if (independentlyCaptured == null || independentlyCaptured.isEmpty())
                return result(Reason.VERIFICATION_UNAVAILABLE);
            var evidence = independentlyCaptured.get();
            if (evidence.trust() == Trust.UNAVAILABLE) return result(Reason.VERIFICATION_UNAVAILABLE);
            if (evidence.trust() == Trust.REJECTED) return result(Reason.VERIFICATION_REJECTED);
            if (evidence.trust() != Trust.VERIFIED_ASSERTION) return result(Reason.UNVERIFIED);
            if (evidence.credentialKind() != CredentialKind.OPERATOR) return result(Reason.NON_OPERATOR_CREDENTIAL);
            var original = evidence.originalGrant();
            if (!binding.equals(evidence.binding()) || !policy.issuer().equals(evidence.issuer())
                    || !policy.audience().equals(evidence.audience()) || !principal.issuer().equals(evidence.issuer())
                    || !principal.subject().equals(evidence.subject())
                    || !session.evidenceSha256().equals(evidence.sessionEvidenceSha256())
                    || !session.authenticatedAt().equals(evidence.authenticatedAt())
                    || !original.authenticationEvent().equals(binding.authenticationEvent())
                    || !original.authenticatedAt().equals(evidence.authenticatedAt()))
                return result(Reason.BINDING_MISMATCH);
            Instant now = clock.instant();
            if (!principal.validAt(now) || evidence.authenticatedAt().isBefore(principal.validFrom())
                    || evidence.authenticatedAt().isAfter(now) || !now.isBefore(session.sessionExpiresAt())
                    || !now.isBefore(evidence.absoluteExpiresAt()) || !now.isBefore(original.absoluteExpiresAt())
                    || evidence.lastActivityAt().isBefore(evidence.authenticatedAt())
                    || evidence.lastActivityAt().isAfter(now)
                    || !fresh(evidence.verifiedAt(), evidence.authenticatedAt(), now, policy.maximumVerificationAge()))
                return result(Reason.INVALID_TIME_WINDOW);
            if (!now.isBefore(evidence.inactivityExpiresAt())
                    || !now.isBefore(evidence.lastActivityAt().plus(policy.maximumInactivity()))
                    || evidence.inactivityExpiresAt().isAfter(evidence.lastActivityAt().plus(policy.maximumInactivity())))
                return result(Reason.INACTIVITY_EXPIRED);
            if (session.revocation() == OperatorIdentityProvider.Revocation.REVOKED
                    || evidence.revocation() == OperatorIdentityProvider.Revocation.REVOKED)
                return result(Reason.REVOKED);
            if (session.revocation() != OperatorIdentityProvider.Revocation.NOT_REVOKED
                    || evidence.revocation() != OperatorIdentityProvider.Revocation.NOT_REVOKED
                    || !policy.revocationAuthority().equals(evidence.revocationAuthority())
                    || !fresh(session.revocationCheckedAt(), session.authenticatedAt(), now, policy.maximumRevocationAge())
                    || !fresh(evidence.revocationCheckedAt(), evidence.authenticatedAt(), now, policy.maximumRevocationAge()))
                return result(Reason.REVOCATION_UNAVAILABLE);
            if (!original.permissions().containsAll(evidence.permissions())
                    || !original.permissions().containsAll(principal.permissions())
                    || !original.accounts().containsAll(evidence.accounts())
                    || evidence.absoluteExpiresAt().isAfter(original.absoluteExpiresAt())
                    || session.sessionExpiresAt().isAfter(original.absoluteExpiresAt()))
                return result(Reason.RENEWAL_ESCALATION);
            if (!principal.permissions().contains(required.get()) || !evidence.permissions().contains(required.get())
                    || !policy.permissions().contains(required.get())) return result(Reason.MISSING_PERMISSION);
            if (!policy.accounts().contains(policy.selectedAccount())
                    || !evidence.accounts().contains(policy.selectedAccount())) return result(Reason.ACCOUNT_DENIED);
            return result(Reason.DIAGNOSTIC_MATCH_NOT_AUTHORIZATION);
        } catch (RuntimeException unavailable) {
            return result(Reason.VERIFICATION_UNAVAILABLE);
        }
    }

    private static boolean fresh(Instant observed, Instant authenticated, Instant now, Duration maximumAge) {
        return !observed.isBefore(authenticated) && !observed.isAfter(now)
                && Duration.between(observed, now).compareTo(maximumAge) <= 0;
    }
    private static Result result(Reason reason) { return new Result(reason); }
    private static void positive(Duration value) {
        Objects.requireNonNull(value);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException("Positive bound required");
    }
    private static String text(String value) {
        Objects.requireNonNull(value);
        if (value.isBlank() || value.length() > 256 || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid policy");
        return value;
    }
}
