package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.application.OperatorIdentityProvider;
import com.kitehybrid.platform.trading.application.OperatorIdentityProvider.*;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperatorIdentityProviderTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration MAX_AGE = Duration.ofSeconds(30);
    private static final String PATH = "/api/trading/instruments";

    private static SessionEvidence session(OperatorPrincipal.Provenance provenance,
            Set<OperatorPrincipal.Permission> permissions, Instant expiry, Revocation revocation, Instant checked) {
        var principal = new OperatorPrincipal("fixture-issuer", "fixture-subject", provenance,
                permissions, NOW.minusSeconds(60), NOW.plusSeconds(60));
        return new SessionEvidence(principal, "a".repeat(64), NOW.minusSeconds(60), expiry, revocation, checked);
    }
    private static SessionEvidence current(Revocation revocation, Instant checked) {
        return session(OperatorPrincipal.Provenance.UNVERIFIED_CLAIM,
                Set.of(OperatorPrincipal.Permission.INSTRUMENTS_READ), NOW.plusSeconds(60), revocation, checked);
    }
    private static Assessment assess(SessionEvidence session) {
        OperatorIdentityProvider provider = () -> Optional.of(session);
        return provider.assess(CLOCK, MAX_AGE, "GET", PATH);
    }

    @Test void absentAndFailedProviderRemainUnavailable() {
        assertEquals(Reason.SESSION_UNAVAILABLE,
                OperatorIdentityProvider.unavailable().assess(CLOCK, MAX_AGE, "GET", PATH).reason());
        OperatorIdentityProvider failed = () -> { throw new IllegalStateException("fixture failure"); };
        assertEquals(Reason.SESSION_UNAVAILABLE, failed.assess(CLOCK, MAX_AGE, "GET", PATH).reason());
    }
    @Test void plausibleClaimsNeverEstablishProductionAuthentication() {
        var result = assess(current(Revocation.NOT_REVOKED, NOW));
        assertEquals(Reason.PROVIDER_NOT_ACTIVATED, result.reason());
        assertFalse(result.productionAuthorized());
        assertFalse(current(Revocation.NOT_REVOKED, NOW).principal().authenticatedProductionIdentity());
    }
    @Test void syntheticPrincipalIsRejected() {
        var s = session(OperatorPrincipal.Provenance.SYNTHETIC_FIXTURE,
                Set.of(OperatorPrincipal.Permission.INSTRUMENTS_READ), NOW.plusSeconds(60), Revocation.NOT_REVOKED, NOW);
        assertEquals(Reason.SYNTHETIC_IDENTITY, assess(s).reason());
        assertFalse(assess(s).productionAuthorized());
    }
    @Test void endpointPermissionIsNotInferred() {
        var s = session(OperatorPrincipal.Provenance.UNVERIFIED_CLAIM,
                Set.of(OperatorPrincipal.Permission.RISK_READ), NOW.plusSeconds(60), Revocation.NOT_REVOKED, NOW);
        assertEquals(Reason.MISSING_PERMISSION, assess(s).reason());
    }
    @Test void sessionExpiryIsExclusive() {
        var s = session(OperatorPrincipal.Provenance.UNVERIFIED_CLAIM,
                Set.of(OperatorPrincipal.Permission.INSTRUMENTS_READ), NOW, Revocation.NOT_REVOKED, NOW);
        assertEquals(Reason.INVALID_TIME_WINDOW, assess(s).reason());
    }
    @Test void revokedAndUnknownSessionsFailClosed() {
        assertEquals(Reason.REVOKED, assess(current(Revocation.REVOKED, NOW)).reason());
        assertEquals(Reason.REVOCATION_UNAVAILABLE, assess(current(Revocation.UNKNOWN, NOW)).reason());
    }
    @Test void staleAndFutureRevocationAssertionsAreRejected() {
        assertEquals(Reason.REVOCATION_UNAVAILABLE, assess(current(Revocation.NOT_REVOKED, NOW.minusSeconds(31))).reason());
        assertEquals(Reason.REVOCATION_UNAVAILABLE, assess(current(Revocation.NOT_REVOKED, NOW.plusSeconds(1))).reason());
    }
    @Test void mutationsAndUnknownRoutesNeverGrant() {
        OperatorIdentityProvider provider = () -> Optional.of(current(Revocation.NOT_REVOKED, NOW));
        assertEquals(Reason.METHOD_NOT_ALLOWED, provider.assess(CLOCK, MAX_AGE, "POST", PATH).reason());
        assertEquals(Reason.UNKNOWN_ENDPOINT, provider.assess(CLOCK, MAX_AGE, "GET", "/unapproved").reason());
    }
    @Test void evidenceReadOnceAndIdentityRedacted() {
        var reads = new AtomicInteger();
        OperatorIdentityProvider provider = () -> { reads.incrementAndGet(); return Optional.of(current(Revocation.NOT_REVOKED, NOW)); };
        assertFalse(provider.assess(CLOCK, MAX_AGE, "GET", PATH).productionAuthorized());
        assertEquals(1, reads.get());
        assertFalse(current(Revocation.NOT_REVOKED, NOW).toString().contains("fixture-subject"));
    }
    @Test void malformedEvidenceAndInvalidPolicyAreRejected() {
        var s = current(Revocation.NOT_REVOKED, NOW);
        assertThrows(IllegalArgumentException.class, () -> new SessionEvidence(s.principal(), "invalid",
                s.authenticatedAt(), s.sessionExpiresAt(), s.revocation(), s.revocationCheckedAt()));
        assertThrows(IllegalArgumentException.class,
                () -> OperatorIdentityProvider.unavailable().assess(CLOCK, Duration.ZERO, "GET", PATH));
    }
}
