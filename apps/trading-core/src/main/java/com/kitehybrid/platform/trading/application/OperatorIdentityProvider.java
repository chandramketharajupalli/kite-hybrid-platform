package com.kitehybrid.platform.trading.application;

import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Inactive operator identity integration boundary, separate from Kite authentication.
 * No Spring bean, provider registration, credential loading or HTTP session is supplied here.
 * A future implementation must validate issuer, audience, signatures and claim mappings using
 * an approved provider before returning evidence. Supplied evidence is not proof of that work.
 * Map only explicitly approved endpoint permissions; never map broker tokens to an operator.
 *
 * Future session integration must bind an opaque session to the verified subject, enforce both
 * principal/session expiry, check authoritative revocation fail-closed, and invalidate it on
 * logout. Logout must be CSRF-protected and must not reset the independent Kite broker session.
 * Preserve CSRF protections on state-changing requests and secure, HttpOnly session cookies.
 * No such session/HTTP integration is activated by this interface or its assessment result.
 */
@FunctionalInterface
public interface OperatorIdentityProvider {
    /** Read already captured evidence only: no login, refresh, network or persistence writes. */
    Optional<SessionEvidence> currentSession();

    enum Revocation { NOT_REVOKED, REVOKED, UNKNOWN }
    enum Reason {
        SESSION_UNAVAILABLE, INVALID_TIME_WINDOW, SYNTHETIC_IDENTITY,
        REVOKED, REVOCATION_UNAVAILABLE, METHOD_NOT_ALLOWED, UNKNOWN_ENDPOINT,
        MISSING_PERMISSION, PROVIDER_NOT_ACTIVATED
    }

    /** A fingerprint binds supplied evidence, not independently authenticated provenance. */
    record SessionEvidence(OperatorPrincipal principal, String evidenceSha256,
                           Instant authenticatedAt, Instant sessionExpiresAt,
                           Revocation revocation, Instant revocationCheckedAt) {
        public SessionEvidence {
            Objects.requireNonNull(principal);
            if (evidenceSha256 == null || !evidenceSha256.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("Evidence SHA-256 required");
            }
            Objects.requireNonNull(authenticatedAt);
            Objects.requireNonNull(sessionExpiresAt);
            Objects.requireNonNull(revocation);
            Objects.requireNonNull(revocationCheckedAt);
            if (!authenticatedAt.isBefore(sessionExpiresAt)) {
                throw new IllegalArgumentException("Nonempty session interval required");
            }
        }
        @Override public String toString() {
            return "SessionEvidence[identity=REDACTED, revocation=" + revocation + "]";
        }
    }

    /** Diagnostic readiness only; cannot grant source, HTTP, broker or trading authority. */
    record Assessment(Reason reason) {
        public Assessment { Objects.requireNonNull(reason); }
        public boolean productionAuthorized() { return false; }
    }

    /** Caller supplies the reviewed revocation freshness bound; no default approval is inferred. */
    default Assessment assess(Clock clock, Duration maximumRevocationAge, String method, String path) {
        Objects.requireNonNull(clock);
        Objects.requireNonNull(maximumRevocationAge);
        if (maximumRevocationAge.isNegative() || maximumRevocationAge.isZero()) {
            throw new IllegalArgumentException("Positive revocation freshness bound required");
        }
        try {
            Optional<SessionEvidence> supplied = currentSession();
            if (supplied == null || supplied.isEmpty()) return new Assessment(Reason.SESSION_UNAVAILABLE);
            SessionEvidence session = supplied.get();
            OperatorPrincipal principal = session.principal();
            Instant now = clock.instant();
            if (principal.provenance() == OperatorPrincipal.Provenance.SYNTHETIC_FIXTURE) {
                return new Assessment(Reason.SYNTHETIC_IDENTITY);
            }
            if (!principal.validAt(now) || now.isBefore(session.authenticatedAt())
                    || !now.isBefore(session.sessionExpiresAt())) {
                return new Assessment(Reason.INVALID_TIME_WINDOW);
            }
            if (session.revocation() == Revocation.REVOKED) return new Assessment(Reason.REVOKED);
            if (session.revocation() != Revocation.NOT_REVOKED
                    || session.revocationCheckedAt().isBefore(session.authenticatedAt())
                    || session.revocationCheckedAt().isAfter(now)
                    || Duration.between(session.revocationCheckedAt(), now).compareTo(maximumRevocationAge) > 0) {
                return new Assessment(Reason.REVOCATION_UNAVAILABLE);
            }
            if (!"GET".equals(method)) return new Assessment(Reason.METHOD_NOT_ALLOWED);
            var permission = TradingApiAuthorization.requiredPermission(method, path);
            if (permission.isEmpty()) return new Assessment(Reason.UNKNOWN_ENDPOINT);
            if (!principal.permissions().contains(permission.get())) return new Assessment(Reason.MISSING_PERMISSION);
            // Even plausible claims and a current revocation assertion do not authenticate a provider.
            return new Assessment(Reason.PROVIDER_NOT_ACTIVATED);
        } catch (RuntimeException unavailable) {
            return new Assessment(Reason.SESSION_UNAVAILABLE);
        }
    }

    static OperatorIdentityProvider unavailable() { return Optional::empty; }
}
