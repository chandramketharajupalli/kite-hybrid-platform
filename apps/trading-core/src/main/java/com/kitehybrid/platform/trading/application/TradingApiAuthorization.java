package com.kitehybrid.platform.trading.application;

import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Deny-only production boundary plus a distinct synthetic permission model.
 * No HTTP integration, public-status routing, session validation or endpoint activation.
 */
public final class TradingApiAuthorization {
    private static final Map<String, Permission> READS = Map.of(
            "/api/trading/instruments", Permission.INSTRUMENTS_READ,
            "/api/trading/strategies", Permission.STRATEGIES_READ,
            "/api/trading/qualifications", Permission.QUALIFICATIONS_READ,
            "/api/trading/signals", Permission.SIGNALS_READ,
            "/api/trading/positions", Permission.POSITIONS_READ,
            "/api/trading/orders", Permission.ORDERS_READ,
            "/api/trading/risk/status", Permission.RISK_READ);

    public enum Reason {
        ABSENT_IDENTITY, IDENTITY_UNAVAILABLE, IDENTITY_NOT_ESTABLISHED,
        INVALID_TIME_WINDOW, METHOD_NOT_ALLOWED, UNKNOWN_ENDPOINT, MISSING_PERMISSION,
        NOT_SYNTHETIC, SYNTHETIC_PERMISSION_MATCH
    }

    /** Cannot represent an authorization grant in this milestone. */
    public record Decision(Reason reason) {
        public Decision { Objects.requireNonNull(reason, "Reason required"); }
        public boolean authorized() { return false; }
    }

    /** Fixture outcome is intentionally a different type from a production decision. */
    public record SyntheticDecision(Reason reason) {
        public SyntheticDecision { Objects.requireNonNull(reason, "Reason required"); }
        public boolean fixturePermitted() { return reason == Reason.SYNTHETIC_PERMISSION_MATCH; }
        public boolean productionAuthorized() { return false; }
    }

    private final Clock clock;

    public TradingApiAuthorization(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Injected clock required");
    }

    public static Optional<Permission> requiredPermission(String method, String path) {
        if (!"GET".equals(method) || path == null) return Optional.empty();
        return Optional.ofNullable(READS.get(path));
    }

    public Decision authorize(OperatorPrincipalAdapter adapter, String method, String path) {
        try {
            if (adapter == null) return new Decision(Reason.ABSENT_IDENTITY);
            Optional<OperatorPrincipal> identity = adapter.currentPrincipal();
            if (identity == null) return new Decision(Reason.IDENTITY_UNAVAILABLE);
            if (identity.isEmpty()) return new Decision(Reason.ABSENT_IDENTITY);
            Reason rejection = rejection(identity.get(), method, path);
            if (rejection != null) return new Decision(rejection);
            // There is no verified production identity provenance or trusted adapter yet.
            return new Decision(Reason.IDENTITY_NOT_ESTABLISHED);
        } catch (RuntimeException unavailable) {
            // Do not include adapter failures, identity claims or credentials in a decision.
            return new Decision(Reason.IDENTITY_UNAVAILABLE);
        }
    }

    public SyntheticDecision evaluateSynthetic(OperatorPrincipal principal, String method, String path) {
        try {
            if (principal == null) return new SyntheticDecision(Reason.ABSENT_IDENTITY);
            if (principal.provenance() != OperatorPrincipal.Provenance.SYNTHETIC_FIXTURE) {
                return new SyntheticDecision(Reason.NOT_SYNTHETIC);
            }
            Reason rejection = rejection(principal, method, path);
            return new SyntheticDecision(rejection == null ? Reason.SYNTHETIC_PERMISSION_MATCH : rejection);
        } catch (RuntimeException unavailable) {
            return new SyntheticDecision(Reason.IDENTITY_UNAVAILABLE);
        }
    }

    private Reason rejection(OperatorPrincipal principal, String method, String path) {
        if (!"GET".equals(method)) return Reason.METHOD_NOT_ALLOWED;
        Permission permission = path == null ? null : READS.get(path);
        if (permission == null) return Reason.UNKNOWN_ENDPOINT;
        if (!principal.validAt(clock.instant())) return Reason.INVALID_TIME_WINDOW;
        if (!principal.permissions().contains(permission)) return Reason.MISSING_PERMISSION;
        return null;
    }
}
