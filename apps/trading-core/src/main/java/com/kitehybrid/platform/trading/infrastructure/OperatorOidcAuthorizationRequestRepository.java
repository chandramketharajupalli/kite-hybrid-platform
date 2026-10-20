package com.kitehybrid.platform.trading.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** Bounded process-local login transactions. A restart loses pending logins and fails closed.
 * No routes or Spring registration. A servlet session alone is never identity evidence. */
public final class OperatorOidcAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private static final String BINDING = OperatorOidcAuthorizationRequestRepository.class.getName() + ".binding";
    private record Pending(OAuth2AuthorizationRequest request, String binding, Instant expires) {
        @Override public String toString() { return "Pending[REDACTED]"; }
    }
    private final EntraOperatorAuthenticationPolicy policy;
    private final Clock clock;
    private final SecureRandom random;
    private final Map<String, Pending> pending = new HashMap<>();
    private final Map<String, Instant> consumedCodes = new HashMap<>();
    public OperatorOidcAuthorizationRequestRepository(EntraOperatorAuthenticationPolicy policy, Clock clock, SecureRandom random) {
        this.policy = Objects.requireNonNull(policy); this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
    }
    public synchronized OAuth2AuthorizationRequest begin(HttpServletRequest request) {
        purge();
        if (pending.size() >= policy.capacity() || consumedCodes.size() >= policy.capacity())
            throw new IllegalStateException("Login capacity unavailable");
        var session = request.getSession(true);
        String binding = (String) session.getAttribute(BINDING);
        if (binding == null) { binding = secret(); session.setAttribute(BINDING, binding); }
        String state = secret(), nonce = secret(), verifier = secret();
        var authorization = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(policy.authorizationUri()).clientId(policy.clientId())
                .redirectUri(policy.callback().toString()).scopes(Set.of("openid"))
                .state(state).attributes(Map.of("nonce", nonce, "code_verifier", verifier))
                .additionalParameters(Map.of("nonce", nonce, "code_challenge", digest(verifier),
                        "code_challenge_method", "S256", "max_age", policy.authenticationAge().toSeconds(),
                        "claims", "{\"id_token\":{\"acrs\":{\"essential\":true,\"value\":\""
                                + safeContext() + "\"}}}"))
                .build();
        pending.put(state, new Pending(authorization, binding, clock.instant().plus(policy.loginLifetime())));
        return authorization;
    }
    private String safeContext() {
        if (!policy.authenticationContext().matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Invalid authentication context");
        return policy.authenticationContext();
    }
    /** Atomic state/code consumption before token exchange, including failed callbacks. */
    public synchronized OAuth2AuthorizationRequest consume(HttpServletRequest request, String code) {
        purge();
        String state = state(request);
        Pending found = state == null ? null : pending.remove(state);
        if (!bound(found, request) || code == null || code.isBlank() || code.length() > 8192
                || code.codePoints().anyMatch(Character::isISOControl)
                || consumedCodes.size() >= policy.capacity()) return null;
        String codeDigest = digest(code);
        if (consumedCodes.putIfAbsent(codeDigest, clock.instant().plus(policy.loginLifetime())) != null) return null;
        return found.request();
    }
    @Override public synchronized OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        purge(); Pending found = pending.get(state(request));
        return bound(found, request) ? found.request() : null;
    }
    @Override public synchronized void saveAuthorizationRequest(OAuth2AuthorizationRequest value,
            HttpServletRequest request, HttpServletResponse response) {
        // Only repository-issued requests can be saved; caller-created claims cannot establish a transaction.
        if (value == null) { String state = state(request); if (state != null) pending.remove(state); return; }
        Pending found = pending.get(value.getState());
        if (found == null || found.request() != value || !sameSession(found, request))
            throw new IllegalArgumentException("Unissued login transaction");
    }
    @Override public synchronized OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        // A generic framework removal cannot mint verified adapter evidence.
        Pending found = pending.remove(state(request));
        return bound(found, request) ? found.request() : null;
    }
    private boolean bound(Pending found, HttpServletRequest request) {
        return found != null && clock.instant().isBefore(found.expires()) && sameSession(found, request)
                && policy.callback().toString().equals(request.getRequestURL().toString());
    }
    private boolean sameSession(Pending found, HttpServletRequest request) {
        var session = request.getSession(false);
        Object actual = session == null ? null : session.getAttribute(BINDING);
        return actual instanceof String text && MessageDigest.isEqual(
                found.binding().getBytes(StandardCharsets.UTF_8), text.getBytes(StandardCharsets.UTF_8));
    }
    private static String state(HttpServletRequest request) {
        String[] values = request.getParameterValues("state");
        return values != null && values.length == 1 && values[0].matches("[A-Za-z0-9_-]{43}") ? values[0] : null;
    }
    private void purge() {
        Instant now = clock.instant();
        pending.values().removeIf(p -> !now.isBefore(p.expires()));
        consumedCodes.values().removeIf(expiry -> !now.isBefore(expiry));
    }
    private String secret() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String digest(String value) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("Digest unavailable"); }
    }
    @Override public String toString() { return "OperatorOidcAuthorizationRequestRepository[REDACTED]"; }
}
