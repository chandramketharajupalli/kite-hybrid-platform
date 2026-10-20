package com.kitehybrid.platform.trading.infrastructure;

import com.kitehybrid.platform.trading.application.OperatorSessionLifecycle;
import com.kitehybrid.platform.trading.application.OperatorSessionRegistryPort;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Inactive adapter. Decoder keys and token client are trusted server dependencies, never request data.
 * The supplied Nimbus decoder must use independently approved issuer keys. This class does not
 * discover/register a tenant or certify its Conditional Access policy. No Authentication input is accepted. */
public final class EntraOperatorAuthenticationAdapter {
    public enum Status { DIAGNOSTIC_SESSION_ESTABLISHED, DENIED }
    public static final class Result {
        private final Status status;
        private final String handle;
        private Result(Status status, String handle) { this.status = status; this.handle = handle; }
        public Status status() { return status; }
        /** Server-side handoff only; never log or expose as an authentication grant. */
        public String sessionHandle() { return handle; }
        public boolean productionAuthorized() { return false; }
        @Override public String toString() { return "Result[" + status + ", REDACTED]"; }
    }
    private final EntraOperatorAuthenticationPolicy policy;
    private final OperatorOidcAuthorizationRequestRepository requests;
    private final ClientRegistration registration;
    private final OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokens;
    private final NimbusJwtDecoder decoder;
    private final OperatorSessionLifecycle lifecycle;
    private final Clock clock;
    private final SecureRandom random;
    public EntraOperatorAuthenticationAdapter(EntraOperatorAuthenticationPolicy policy,
            OperatorOidcAuthorizationRequestRepository requests, ClientRegistration registration,
            OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokens,
            NimbusJwtDecoder decoder, OperatorSessionRegistryPort registry, Clock clock, SecureRandom random) {
        this.policy = Objects.requireNonNull(policy); this.requests = Objects.requireNonNull(requests);
        this.registration = Objects.requireNonNull(registration); this.tokens = Objects.requireNonNull(tokens);
        this.decoder = Objects.requireNonNull(decoder); this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
        if (!registration.getClientId().equals(policy.clientId())
                || !registration.getRedirectUri().equals(policy.callback().toString())
                || !registration.getProviderDetails().getAuthorizationUri().equals(policy.authorizationUri())
                || !registration.getProviderDetails().getTokenUri().equals(policy.tokenUri())
                || !policy.issuer().toString().equals(registration.getProviderDetails().getIssuerUri())
                || !registration.getScopes().equals(Set.of("openid")))
            throw new IllegalArgumentException("Registration policy mismatch");
        // Nimbus still verifies signatures; this validator adds deterministic injected-clock policy.
        decoder.setJwtValidator(jwt -> validClaims(jwt) ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error("invalid_token")));
        lifecycle = new OperatorSessionLifecycle(Objects.requireNonNull(registry), clock, policy.localSession());
    }
    public Result authenticate(HttpServletRequest callback) {
        try {
            String[] codes = callback.getParameterValues("code");
            String code = codes != null && codes.length == 1 ? codes[0] : null;
            var request = requests.consume(callback, code);
            if (request == null || callback.getParameter("error") != null
                    || !policy.clientId().equals(request.getClientId())
                    || !policy.callback().toString().equals(request.getRedirectUri())
                    || !policy.authorizationUri().equals(request.getAuthorizationUri())) return denied();
            var response = OAuth2AuthorizationResponse.success(code).state(request.getState())
                    .redirectUri(policy.callback().toString()).build();
            var token = tokens.getTokenResponse(new OAuth2AuthorizationCodeGrantRequest(registration,
                    new OAuth2AuthorizationExchange(request, response)));
            Object raw = token.getAdditionalParameters().get("id_token");
            if (!(raw instanceof String encoded) || encoded.length() > 32768) return denied();
            Jwt verified = decoder.decode(encoded);
            Object expectedNonce = request.getAttributes().get("nonce");
            String actualNonce = verified.getClaimAsString("nonce");
            if (!(expectedNonce instanceof String nonce) || actualNonce == null
                    || !MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8), actualNonce.getBytes(StandardCharsets.UTF_8))
                    || !validClaims(verified)) return denied();
            byte[] entropy = new byte[32]; random.nextBytes(entropy);
            String handle = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
            // Rotation precedes durable establishment. No SecurityContext or protected-route grant is installed.
            callback.changeSessionId();
            var event = new OperatorSessionRegistryPort.Event(policy.issuer().toString(), verified.getSubject(),
                    policy.clientId(), UUID.randomUUID(), verified.getClaimAsInstant("auth_time"),
                    OperatorSessionRegistryPort.Trust.VERIFIED_OPERATOR_ASSERTION);
            var result = lifecycle.establish(handle, event);
            return result.diagnosticReady() ? new Result(Status.DIAGNOSTIC_SESSION_ESTABLISHED, handle) : denied();
        } catch (RuntimeException failure) { return denied(); }
    }
    private boolean validClaims(Jwt token) {
        try {
            var now = clock.instant(); var skew = policy.clockSkew();
            var auth = token.getClaimAsInstant("auth_time"); var issued = token.getIssuedAt(); var expiry = token.getExpiresAt();
            var notBefore = token.getNotBefore();
            String azp = token.getClaimAsString("azp");
            var contexts = token.getClaimAsStringList("acrs");
            return policy.signingAlgorithms().contains(token.getHeaders().get("alg"))
                    && policy.issuer().toString().equals(token.getClaimAsString("iss"))
                    && policy.subjects().contains(token.getSubject())
                    && token.getAudience().equals(java.util.List.of(policy.clientId()))
                    && (azp == null || azp.equals(policy.clientId()))
                    && contexts != null && contexts.contains(policy.authenticationContext())
                    && auth != null && issued != null && expiry != null
                    && !auth.isAfter(now) && !issued.isAfter(now.plus(skew))
                    && !auth.isAfter(issued.plus(skew)) && issued.isBefore(expiry)
                    && now.isBefore(expiry.plus(skew)) && now.isBefore(auth.plus(policy.authenticationAge()))
                    && (notBefore == null || !notBefore.isAfter(now.plus(skew)));
        } catch (RuntimeException malformed) { return false; }
    }
    private static Result denied() { return new Result(Status.DENIED, null); }
    @Override public String toString() { return "EntraOperatorAuthenticationAdapter[REDACTED]"; }
}
