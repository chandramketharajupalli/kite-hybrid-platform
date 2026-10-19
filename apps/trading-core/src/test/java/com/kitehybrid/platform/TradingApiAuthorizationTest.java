package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.application.OperatorPrincipalAdapter;
import com.kitehybrid.platform.trading.application.TradingApiAuthorization;
import com.kitehybrid.platform.trading.application.TradingApiAuthorization.Reason;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal.Provenance;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradingApiAuthorizationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:30Z");
    private final TradingApiAuthorization authorization =
            new TradingApiAuthorization(Clock.fixed(NOW, ZoneOffset.UTC));

    private OperatorPrincipal fixture(Set<Permission> permissions) {
        return new OperatorPrincipal("fixture", "operator", Provenance.SYNTHETIC_FIXTURE,
                permissions, NOW.minusSeconds(30), NOW.plusSeconds(30));
    }

    @Test void absentIdentityIsDenied() {
        var decision = authorization.authorize(OperatorPrincipalAdapter.unavailable(),
                "GET", "/api/trading/instruments");
        assertEquals(Reason.ABSENT_IDENTITY, decision.reason());
        assertFalse(decision.authorized());
        assertFalse(authorization.authorize(null, "GET", "/api/trading/instruments").authorized());
    }

    @Test void adapterFailureAndBrokenOptionalFailClosedWithoutLeakingDetails() {
        var failed = authorization.authorize(() -> { throw new IllegalStateException("fixture-sensitive-marker"); },
                "GET", "/api/trading/instruments");
        assertEquals(Reason.IDENTITY_UNAVAILABLE, failed.reason());
        assertFalse(failed.authorized());
        assertFalse(failed.toString().contains("fixture-sensitive-marker"));
        assertEquals(Reason.IDENTITY_UNAVAILABLE,
                authorization.authorize(() -> null, "GET", "/api/trading/instruments").reason());
    }

    @Test void matchingSyntheticPermissionStillCannotAuthorizeProduction() {
        var principal = fixture(Set.of(Permission.INSTRUMENTS_READ));
        var synthetic = authorization.evaluateSynthetic(principal, "GET", "/api/trading/instruments");
        assertTrue(synthetic.fixturePermitted());
        assertFalse(synthetic.productionAuthorized());
        var real = authorization.authorize(OperatorPrincipalAdapter.synthetic(principal),
                "GET", "/api/trading/instruments");
        assertEquals(Reason.IDENTITY_NOT_ESTABLISHED, real.reason());
        assertFalse(real.authorized());
    }

    @Test void unverifiedClaimCannotBeUsedAsEitherProductionOrSyntheticIdentity() {
        var principal = new OperatorPrincipal("claimed-issuer", "claimed-user", Provenance.UNVERIFIED_CLAIM,
                Set.of(Permission.INSTRUMENTS_READ), NOW.minusSeconds(1), NOW.plusSeconds(1));
        assertFalse(authorization.authorize(() -> Optional.of(principal),
                "GET", "/api/trading/instruments").authorized());
        assertEquals(Reason.NOT_SYNTHETIC,
                authorization.evaluateSynthetic(principal, "GET", "/api/trading/instruments").reason());
    }

    @Test void missingPermissionAndCrossEndpointPermissionAreDenied() {
        assertEquals(Reason.MISSING_PERMISSION, authorization.evaluateSynthetic(
                fixture(Set.of()), "GET", "/api/trading/instruments").reason());
        var principal = fixture(Set.of(Permission.INSTRUMENTS_READ));
        assertEquals(Reason.MISSING_PERMISSION,
                authorization.evaluateSynthetic(principal, "GET", "/api/trading/risk/status").reason());
        assertFalse(authorization.authorize(OperatorPrincipalAdapter.synthetic(principal),
                "GET", "/api/trading/risk/status").authorized());
    }

    @Test void eachProtectedReadHasItsOwnExactPermission() {
        var routes = Map.of(
                "/api/trading/instruments", Permission.INSTRUMENTS_READ,
                "/api/trading/strategies", Permission.STRATEGIES_READ,
                "/api/trading/qualifications", Permission.QUALIFICATIONS_READ,
                "/api/trading/signals", Permission.SIGNALS_READ,
                "/api/trading/positions", Permission.POSITIONS_READ,
                "/api/trading/orders", Permission.ORDERS_READ,
                "/api/trading/risk/status", Permission.RISK_READ);
        routes.forEach((path, permission) -> {
            assertEquals(Optional.of(permission), TradingApiAuthorization.requiredPermission("GET", path));
            assertTrue(authorization.evaluateSynthetic(fixture(Set.of(permission)), "GET", path).fixturePermitted());
            assertFalse(authorization.authorize(OperatorPrincipalAdapter.synthetic(fixture(Set.of(permission))),
                    "GET", path).authorized());
        });
    }

    @Test void mutationsUnknownRoutesAndNoncanonicalPathsDeny() {
        var principal = fixture(Set.of(Permission.values()));
        for (String method : new String[] {"POST", "PUT", "DELETE", "PATCH", "HEAD", "get"}) {
            assertEquals(Reason.METHOD_NOT_ALLOWED,
                    authorization.evaluateSynthetic(principal, method, "/api/trading/orders").reason());
        }
        for (String path : new String[] {"/api/trading/unknown", "/api/trading/instruments/",
                "/api/trading/%69nstruments", "/api/trading/instruments?all=true",
                "/api/trading/instruments;extra", "/api/trading/../orders", "/api/trading/status"}) {
            assertEquals(Reason.UNKNOWN_ENDPOINT, authorization.evaluateSynthetic(principal, "GET", path).reason());
        }
        assertTrue(TradingApiAuthorization.requiredPermission(null, null).isEmpty());
        assertFalse(authorization.evaluateSynthetic(principal, "GET", null).fixturePermitted());
    }

    @Test void notYetValidAndExpiredIdentityDenyAtBoundary() {
        var expired = new OperatorPrincipal("fixture", "operator", Provenance.SYNTHETIC_FIXTURE,
                Set.of(Permission.INSTRUMENTS_READ), NOW.minusSeconds(1), NOW);
        var future = new OperatorPrincipal("fixture", "operator", Provenance.SYNTHETIC_FIXTURE,
                Set.of(Permission.INSTRUMENTS_READ), NOW.plusSeconds(1), NOW.plusSeconds(2));
        for (var principal : new OperatorPrincipal[] {expired, future}) {
            assertEquals(Reason.INVALID_TIME_WINDOW,
                    authorization.evaluateSynthetic(principal, "GET", "/api/trading/instruments").reason());
            assertFalse(authorization.authorize(OperatorPrincipalAdapter.synthetic(principal),
                    "GET", "/api/trading/instruments").authorized());
        }
    }
}
