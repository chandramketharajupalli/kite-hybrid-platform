package com.kitehybrid.platform.trading.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/** Lazy network clients. Construction performs no discovery, credential exchange or JWKS fetch. */
public final class EntraDevelopmentOidcTransport {
    private final EntraOperatorAuthenticationPolicy policy;
    private final Clock clock;
    public EntraDevelopmentOidcTransport(EntraOperatorAuthenticationPolicy policy, Clock clock) {
        this.policy = java.util.Objects.requireNonNull(policy);
        this.clock = java.util.Objects.requireNonNull(clock);
    }
    public ClientRegistration registration(String credential) {
        return ClientRegistration.withRegistrationId("entra-development")
                .clientId(policy.clientId()).clientSecret(credential)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(policy.callback().toString()).scope("openid")
                .authorizationUri(policy.authorizationUri()).tokenUri(policy.tokenUri())
                .issuerUri(policy.issuer().toString()).jwkSetUri(jwks().toString()).build();
    }
    public URI jwks() {
        return URI.create(policy.issuer().toString().replace("/v2.0", "/discovery/v2.0/keys"));
    }
    private static JdkClientHttpRequestFactory factory() {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
    public RestClientAuthorizationCodeTokenResponseClient tokenClient() {
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(RestClient.builder().requestFactory(factory())
                .requestInterceptor(new Guard(URI.create(policy.tokenUri()), HttpMethod.POST, clock, Duration.ZERO, 65536))
                .messageConverters(c -> { c.clear(); c.add(new FormHttpMessageConverter());
                    c.add(new OAuth2AccessTokenResponseHttpMessageConverter()); })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build());
        return client;
    }
    public NimbusJwtDecoder decoder() {
        var operations = new RestTemplate(factory());
        operations.setInterceptors(List.of(new Guard(jwks(), HttpMethod.GET, clock, Duration.ofSeconds(30), 262144)));
        // Nimbus maintains its JWK cache. The guard bounds network refresh even for unknown key IDs.
        return NimbusJwtDecoder.withJwkSetUri(jwks().toString()).restOperations(operations).build();
    }
    /** Public for synthetic interceptor tests; callers cannot alter a constructed adapter's authority. */
    public static final class Guard implements ClientHttpRequestInterceptor {
        private final URI endpoint;
        private final HttpMethod method;
        private final Clock clock;
        private final Duration interval;
        private final int cap;
        private Instant next = Instant.MIN;
        public Guard(URI endpoint, HttpMethod method, Clock clock, Duration interval, int cap) {
            if (!"https".equals(endpoint.getScheme()) || !"login.microsoftonline.com".equals(endpoint.getHost())
                    || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                    || endpoint.getQuery() != null || endpoint.getPort() != -1 || cap < 1
                    || cap > 262144 || interval.isNegative()) throw new IllegalArgumentException("Invalid transport bounds");
            this.endpoint = endpoint; this.method = method; this.clock = clock; this.interval = interval; this.cap = cap;
        }
        @Override public ClientHttpResponse intercept(org.springframework.http.HttpRequest request, byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) throws IOException {
            synchronized (this) {
                if (!endpoint.equals(request.getURI()) || method != request.getMethod()
                        || body.length > 32768 || clock.instant().isBefore(next)) throw unavailable();
                next = clock.instant().plus(interval);
            }
            try {
                var response = execution.execute(request, body);
                try {
                    if (response.getStatusCode().value() != 200) throw unavailable();
                    byte[] bytes = response.getBody().readNBytes(cap + 1);
                    if (bytes.length > cap) throw unavailable();
                    HttpHeaders headers = new HttpHeaders(); headers.putAll(response.getHeaders());
                    return new ClientHttpResponse() {
                        public HttpStatusCode getStatusCode() { return HttpStatusCode.valueOf(200); }
                        public String getStatusText() { return "OK"; }
                        public HttpHeaders getHeaders() { return headers; }
                        public InputStream getBody() { return new java.io.ByteArrayInputStream(bytes); }
                        public void close() { }
                    };
                } finally { response.close(); }
            } catch (IOException | RuntimeException failure) { throw unavailable(); }
        }
        private static IOException unavailable() { return new IOException("Identity provider unavailable"); }
    }
    @Override public String toString() { return "EntraDevelopmentOidcTransport[REDACTED]"; }
}
