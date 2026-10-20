package com.kitehybrid.platform.trading.infrastructure;

import com.kitehybrid.platform.trading.application.OperatorSessionRegistryPort;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** Server-selected policy only. Construction is not tenant approval or authentication. */
public record EntraOperatorAuthenticationPolicy(
        URI issuer, String clientId, URI callback, Set<String> subjects,
        Set<String> signingAlgorithms, Duration authenticationAge, Duration clockSkew,
        Duration loginLifetime, int capacity, String authenticationContext,
        String approvedTenantPolicyBinding, OperatorSessionRegistryPort.Policy localSession) {
    public EntraOperatorAuthenticationPolicy {
        Objects.requireNonNull(issuer); Objects.requireNonNull(callback); Objects.requireNonNull(localSession);
        clientId = OperatorSessionRegistryPort.text(clientId);
        subjects = Set.copyOf(subjects); signingAlgorithms = Set.copyOf(signingAlgorithms);
        subjects.forEach(OperatorSessionRegistryPort::text);
        authenticationContext = OperatorSessionRegistryPort.text(authenticationContext);
        if (approvedTenantPolicyBinding == null || !approvedTenantPolicyBinding.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Independent tenant-policy approval binding required");
        if (!issuer.toString().matches("https://login\\.microsoftonline\\.com/[a-fA-F0-9-]{36}/v2\\.0")
                || !"https".equals(callback.getScheme()) || callback.getHost() == null
                || callback.getUserInfo() != null || callback.getQuery() != null || callback.getFragment() != null
                || subjects.size() != 1 || !subjects.contains(localSession.subject())
                || !issuer.toString().equals(localSession.issuer()) || !clientId.equals(localSession.audience())
                || !signingAlgorithms.equals(Set.of("RS256")))
            throw new IllegalArgumentException("Invalid exact operator policy");
        positive(authenticationAge, 3600); positive(loginLifetime, 300);
        Objects.requireNonNull(clockSkew);
        if (clockSkew.isNegative() || clockSkew.compareTo(Duration.ofSeconds(60)) > 0
                || capacity < 1 || capacity > 1024)
            throw new IllegalArgumentException("Invalid bounds");
    }
    private static void positive(Duration d, long maximum) {
        Objects.requireNonNull(d);
        if (d.isNegative() || d.isZero() || d.compareTo(Duration.ofSeconds(maximum)) > 0)
            throw new IllegalArgumentException("Invalid lifetime");
    }
    public String authorizationUri() { return issuer.toString().replace("/v2.0", "/oauth2/v2.0/authorize"); }
    public String tokenUri() { return issuer.toString().replace("/v2.0", "/oauth2/v2.0/token"); }
    @Override public String toString() { return "EntraOperatorAuthenticationPolicy[REDACTED]"; }
}
