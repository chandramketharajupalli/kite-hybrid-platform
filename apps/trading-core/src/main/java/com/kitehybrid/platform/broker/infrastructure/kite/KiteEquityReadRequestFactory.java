package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.*;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.CONFIGURATION;

/** Only the controlled equity run uses this wire factory. No arbitrary real URL, redirects or retries. */
final class KiteEquityReadRequestFactory implements ClientHttpRequestFactory, AutoCloseable {
    static final URI OFFICIAL = URI.create("https://api.kite.trade");
    static final String PATH = "/user/margins/equity";
    private static final AtomicBoolean REAL_PROCESS_ATTEMPT = new AtomicBoolean();
    private final URI origin;
    private final boolean official;
    private final AtomicBoolean attempted = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CloseableHttpClient client;
    private final HttpComponentsClientHttpRequestFactory delegate;
    private Runnable syntheticGuard=()->{};
    private boolean guardBound;

    synchronized void bindSyntheticGuard(Runnable guard) {
        if (official || guardBound || attempted.get() || closed.get() || guard==null) throw denied();
        guardBound=true; syntheticGuard=guard;
    }

    static KiteEquityReadRequestFactory official() {
        return new KiteEquityReadRequestFactory(OFFICIAL, true, Duration.ofSeconds(30));
    }
    static KiteEquityReadRequestFactory loopback(URI origin, Duration timeout) {
        if (origin == null || !"http".equals(origin.getScheme()) || !"127.0.0.1".equals(origin.getHost())
                || origin.getPort() < 1 || origin.getRawUserInfo() != null || origin.getRawQuery() != null
                || origin.getRawFragment() != null || !origin.getRawPath().isEmpty()) throw denied();
        return new KiteEquityReadRequestFactory(origin, false, timeout);
    }
    private KiteEquityReadRequestFactory(URI origin, boolean official, Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0)
            throw denied();
        this.origin = origin; this.official = official;
        var config = RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(10))
                .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                .setAuthenticationEnabled(false).build();
        client = HttpClients.custom().disableAutomaticRetries().disableRedirectHandling()
                .disableCookieManagement().disableAuthCaching().disableContentCompression()
                .setDefaultRequestConfig(config).build();
        delegate = new HttpComponentsClientHttpRequestFactory(client);
    }
    @Override public ClientHttpRequest createRequest(URI uri, HttpMethod method) throws IOException {
        requireSafeLogging();
        syntheticGuard.run();
        if (closed.get() || method != HttpMethod.GET || !origin.resolve(PATH).equals(uri)) {
            close(); throw denied();
        }
        if (!attempted.compareAndSet(false, true)
                || (official && !REAL_PROCESS_ATTEMPT.compareAndSet(false, true))) throw denied();
        var request=delegate.createRequest(uri, method);
        return new ClientHttpRequest() {
            private final AtomicBoolean executed=new AtomicBoolean();
            @Override public HttpMethod getMethod() { return request.getMethod(); }
            @Override public URI getURI() { return request.getURI(); }
            @Override public java.util.Map<String,Object> getAttributes() { return request.getAttributes(); }
            @Override public org.springframework.http.HttpHeaders getHeaders() { return request.getHeaders(); }
            @Override public java.io.OutputStream getBody() throws IOException { return request.getBody(); }
            @Override public ClientHttpResponse execute() throws IOException {
                if (closed.get() || !executed.compareAndSet(false,true)) throw denied();
                requireSafeLogging();
                syntheticGuard.run();
                return request.execute();
            }
        };
    }
    URI origin() { return origin; }
    boolean officialOrigin() { return official; }
    int attempts() { return attempted.get() ? 1 : 0; }
    private void requireSafeLogging() {
        if (org.slf4j.LoggerFactory.getLogger("org.apache.hc.client5.http.wire").isDebugEnabled()
                || org.slf4j.LoggerFactory.getLogger("org.apache.hc.client5.http.headers").isDebugEnabled()) {
            close(); throw denied();
        }
    }
    @Override public void close() {
        closed.set(true);
        try { client.close(); } catch (IOException ignored) { /* Never expose a wire exception. */ }
    }
    private static BrokerReadException denied() { return new BrokerReadException(CONFIGURATION); }
}
