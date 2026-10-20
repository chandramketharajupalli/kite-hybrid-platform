package com.kitehybrid.platform.trading.infrastructure;

import com.kitehybrid.platform.trading.application.*;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import static com.kitehybrid.platform.trading.application.OperatorSessionVerificationPort.*;

/** Request-bound, manually constructed diagnostic bridge. Never a production identity provider bean. */
public final class RegistryOperatorIdentityAdapter implements OperatorIdentityProvider, OperatorSessionVerificationPort {
    private final OperatorSessionRegistryPort registry;
    private final Clock clock;
    private final OperatorSessionRegistryPort.Policy policy;
    private final SessionBinding binding;
    public RegistryOperatorIdentityAdapter(OperatorSessionRegistryPort registry, Clock clock,
            OperatorSessionRegistryPort.Policy policy, SessionBinding binding) {
        this.registry = Objects.requireNonNull(registry); this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(policy); this.binding = Objects.requireNonNull(binding);
    }
    private Optional<OperatorSessionRegistryPort.Observation> read() {
        var found = registry.find(OperatorSessionLifecycle.hashHandle(binding.opaqueHandle()));
        if (found == null || found.isEmpty()) return Optional.empty();
        var observation = found.get(); var s = observation.session(); var now = clock.instant();
        if (!s.digest().equals(OperatorSessionLifecycle.hashHandle(binding.opaqueHandle())) || !s.activeAt(now)
                || !s.event().authenticationEvent().toString().equals(binding.authenticationEvent())
                || !s.event().issuer().equals(policy.issuer()) || !s.event().subject().equals(policy.subject()) || !s.event().audience().equals(policy.audience())
                || !s.policyVersion().equals(policy.version()) || !s.inactivity().equals(policy.inactivity())
                || s.absoluteExpiresAt().isAfter(s.event().authenticatedAt().plus(policy.absoluteLifetime()))
                || observation.observedAt().isAfter(now) || observation.observedAt().isBefore(s.event().authenticatedAt())
                || Duration.between(observation.observedAt(), now).compareTo(policy.freshness()) > 0)
            return Optional.empty();
        return found;
    }
    @Override public Optional<SessionEvidence> currentSession() {
        try { return read().map(o -> {
            var s = o.session();
            var principal = new OperatorPrincipal(s.event().issuer(), s.event().subject(),
                    OperatorPrincipal.Provenance.UNVERIFIED_CLAIM, s.permissions(), s.event().authenticatedAt(), s.absoluteExpiresAt());
            return new SessionEvidence(principal, s.digest(), s.event().authenticatedAt(), s.absoluteExpiresAt(),
                    Revocation.NOT_REVOKED, o.observedAt());
        }); } catch (RuntimeException unavailable) { return Optional.empty(); }
    }
    @Override public Optional<Verification> verificationFor(SessionBinding requested) {
        if (!binding.equals(requested)) return Optional.empty();
        try { return read().map(o -> {
            var s = o.session(); var e = s.event();
            return new Verification(binding, e.issuer(), e.subject(), e.audience(), s.digest(),
                    Trust.VERIFIED_ASSERTION, CredentialKind.OPERATOR, e.authenticatedAt(), s.absoluteExpiresAt(),
                    s.lastActivityAt(), s.lastActivityAt().plus(s.inactivity()), o.observedAt(),
                    Revocation.NOT_REVOKED, policy.revocationAuthority(), o.observedAt(), s.permissions(), s.accounts(),
                    new OriginalGrant(e.authenticationEvent().toString(), e.authenticatedAt(), s.absoluteExpiresAt(),
                            s.permissions(), s.accounts()));
        }); } catch (RuntimeException unavailable) { return Optional.empty(); }
    }
}
