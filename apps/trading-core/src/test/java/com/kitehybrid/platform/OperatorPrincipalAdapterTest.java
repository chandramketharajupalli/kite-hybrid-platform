package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.application.OperatorPrincipalAdapter;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal.Provenance;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperatorPrincipalAdapterTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = START.plusSeconds(60);

    private OperatorPrincipal principal(Provenance provenance, Set<Permission> permissions) {
        return new OperatorPrincipal("fixture-issuer", "fixture-subject", provenance, permissions, START, END);
    }

    @Test void unavailableAdapterDoesNotInventIdentity() {
        assertTrue(OperatorPrincipalAdapter.unavailable().currentPrincipal().isEmpty());
    }

    @Test void syntheticIdentityRetainsProvenanceAndCannotAuthenticateProduction() {
        var principal = principal(Provenance.SYNTHETIC_FIXTURE, Set.of(Permission.INSTRUMENTS_READ));
        var result = OperatorPrincipalAdapter.synthetic(principal).currentPrincipal().orElseThrow();
        assertEquals(principal, result);
        assertEquals(Provenance.SYNTHETIC_FIXTURE, result.provenance());
        assertFalse(result.authenticatedProductionIdentity());
    }

    @Test void fixtureFactoryRejectsUnverifiedClaimsAndNull() {
        assertThrows(IllegalArgumentException.class,
                () -> OperatorPrincipalAdapter.synthetic(principal(Provenance.UNVERIFIED_CLAIM, Set.of())));
        assertThrows(NullPointerException.class, () -> OperatorPrincipalAdapter.synthetic(null));
    }

    @Test void permissionsAreDefensivelyCopiedAndImmutable() {
        var permissions = new HashSet<Permission>();
        permissions.add(Permission.INSTRUMENTS_READ);
        var principal = principal(Provenance.SYNTHETIC_FIXTURE, permissions);
        permissions.clear();
        assertEquals(Set.of(Permission.INSTRUMENTS_READ), principal.permissions());
        assertThrows(UnsupportedOperationException.class,
                () -> principal.permissions().add(Permission.RISK_READ));
    }

    @Test void validityIsStartInclusiveAndExpiryExclusive() {
        var principal = principal(Provenance.SYNTHETIC_FIXTURE, Set.of());
        assertFalse(principal.validAt(START.minusNanos(1)));
        assertTrue(principal.validAt(START));
        assertTrue(principal.validAt(END.minusNanos(1)));
        assertFalse(principal.validAt(END));
    }

    @Test void malformedIdentityAndValidityReject() {
        assertThrows(IllegalArgumentException.class, () -> new OperatorPrincipal(
                " ", "subject", Provenance.UNVERIFIED_CLAIM, Set.of(), START, END));
        assertThrows(IllegalArgumentException.class, () -> new OperatorPrincipal(
                "issuer", "subject\n", Provenance.UNVERIFIED_CLAIM, Set.of(), START, END));
        assertThrows(IllegalArgumentException.class, () -> new OperatorPrincipal(
                "issuer", "subject", Provenance.UNVERIFIED_CLAIM, Set.of(), START, START));
        assertThrows(NullPointerException.class, () -> principal(Provenance.SYNTHETIC_FIXTURE, null));
        assertThrows(NullPointerException.class, () -> principal(null, Set.of()));
    }

    @Test void diagnosticStringDoesNotExposeIdentity() {
        var principal = principal(Provenance.SYNTHETIC_FIXTURE, Set.of());
        assertFalse(principal.toString().contains("fixture-issuer"));
        assertFalse(principal.toString().contains("fixture-subject"));
        assertTrue(principal.toString().contains("SYNTHETIC_FIXTURE"));
    }
}
