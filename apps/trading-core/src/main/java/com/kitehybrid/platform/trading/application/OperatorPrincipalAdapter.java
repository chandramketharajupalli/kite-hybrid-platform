package com.kitehybrid.platform.trading.application;

import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only identity port. Implementations must not load credentials, contact a provider or
 * infer identity from a broker session. Returning claims does not authenticate them.
 */
@FunctionalInterface
public interface OperatorPrincipalAdapter {
    Optional<OperatorPrincipal> currentPrincipal();

    static OperatorPrincipalAdapter unavailable() {
        return Optional::empty;
    }

    /** Explicit fixture adapter only; there is deliberately no production factory. */
    static OperatorPrincipalAdapter synthetic(OperatorPrincipal principal) {
        Objects.requireNonNull(principal, "Fixture principal required");
        if (principal.provenance() != OperatorPrincipal.Provenance.SYNTHETIC_FIXTURE) {
            throw new IllegalArgumentException("Synthetic provenance required");
        }
        return () -> Optional.of(principal);
    }
}
