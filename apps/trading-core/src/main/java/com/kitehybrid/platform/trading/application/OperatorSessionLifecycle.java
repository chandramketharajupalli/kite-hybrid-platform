package com.kitehybrid.platform.trading.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import static com.kitehybrid.platform.trading.application.OperatorSessionRegistryPort.*;

/** Manually constructed lifecycle. A VERIFIED_OPERATOR_ASSERTION is a port assertion, not credential proof. */
public final class OperatorSessionLifecycle {
    public enum Reason { DIAGNOSTIC_READY, DENIED, UNAVAILABLE }
    public record Result(Reason reason) {
        public boolean diagnosticReady() { return reason == Reason.DIAGNOSTIC_READY; }
        public boolean productionAuthorized() { return false; }
    }
    private final OperatorSessionRegistryPort registry;
    private final Clock clock;
    private final Policy policy;
    public OperatorSessionLifecycle(OperatorSessionRegistryPort registry, Clock clock, Policy policy) {
        this.registry = Objects.requireNonNull(registry); this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(policy);
    }
    public Result establish(String handle, Event event) {
        try {
            var now = clock.instant();
            if (event.trust() != Trust.VERIFIED_OPERATOR_ASSERTION || !event.issuer().equals(policy.issuer()) || !event.subject().equals(policy.subject())
                    || !event.audience().equals(policy.audience()) || event.authenticatedAt().isAfter(now)
                    || !now.isBefore(event.authenticatedAt().plus(policy.absoluteLifetime()))) return denied();
            var session = new Session(hashHandle(handle), event, event.authenticatedAt().plus(policy.absoluteLifetime()),
                    now, policy.inactivity(), policy.permissions(), policy.accounts(), policy.version(), false, 1);
            return outcome(registry.establish(session));
        } catch (RuntimeException unavailable) { return unavailable(); }
    }
    public Result assess(String handle, String method, String endpoint, String account) {
        try {
            var required = TradingApiAuthorization.requiredPermission(method, endpoint);
            var current = eligible(handle);
            return outcome(required.isPresent() && current.isPresent()
                    && policy.permissions().contains(required.get()) && current.get().permissions().contains(required.get())
                    && policy.accounts().contains(account) && current.get().accounts().contains(account));
        } catch (RuntimeException unavailable) { return unavailable(); }
    }
    public Result renew(String handle) {
        try { var s = eligible(handle); return outcome(s.isPresent()
                && registry.touch(s.get().digest(), s.get().revision(), clock.instant())); }
        catch (RuntimeException unavailable) { return unavailable(); }
    }
    public Result rotate(String handle, String replacement) {
        try { var s = eligible(handle); return outcome(s.isPresent()
                && registry.rotate(s.get().digest(), s.get().revision(), hashHandle(replacement), clock.instant())); }
        catch (RuntimeException unavailable) { return unavailable(); }
    }
    public Result logout(String handle) {
        try { return outcome(registry.revoke(hashHandle(handle), clock.instant())); }
        catch (RuntimeException unavailable) { return unavailable(); }
    }
    private Optional<Session> eligible(String handle) {
        var found = registry.find(hashHandle(handle));
        if (found == null || found.isEmpty()) return Optional.empty();
        var observed = found.get(); var s = observed.session(); var now = clock.instant();
        if (!s.digest().equals(hashHandle(handle)) || !s.activeAt(now)
                || observed.observedAt().isBefore(s.event().authenticatedAt()) || observed.observedAt().isAfter(now)
                || Duration.between(observed.observedAt(), now).compareTo(policy.freshness()) > 0
                || !s.event().issuer().equals(policy.issuer()) || !s.event().subject().equals(policy.subject()) || !s.event().audience().equals(policy.audience())
                || !s.policyVersion().equals(policy.version()) || !s.inactivity().equals(policy.inactivity())
                || s.absoluteExpiresAt().isAfter(s.event().authenticatedAt().plus(policy.absoluteLifetime())))
            return Optional.empty();
        return Optional.of(s);
    }
    public static String hashHandle(String handle) {
        // High-entropy server-generated handles only. This syntax check is not authentication.
        if (handle == null || !handle.matches("[A-Za-z0-9_-]{32,128}")) throw new IllegalArgumentException("Invalid binding");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(handle.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("Digest unavailable"); }
    }
    private static Result outcome(boolean ok) { return ok ? new Result(Reason.DIAGNOSTIC_READY) : denied(); }
    private static Result denied() { return new Result(Reason.DENIED); }
    private static Result unavailable() { return new Result(Reason.UNAVAILABLE); }
}
