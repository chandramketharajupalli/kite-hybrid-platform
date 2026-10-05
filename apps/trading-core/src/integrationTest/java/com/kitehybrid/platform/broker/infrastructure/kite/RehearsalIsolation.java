package com.kitehybrid.platform.broker.infrastructure.kite;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Test-only: no DNS names, redirects, ambient deployment properties or external broker targets. */
final class RehearsalIsolation {
    private RehearsalIsolation() {}
    static com.kitehybrid.platform.order.application.AccountExecutionChecks accounts(
            com.kitehybrid.platform.order.application.OrderExecutionProperties properties,
            com.kitehybrid.platform.instrument.application.InstrumentRegistry registry,
            com.kitehybrid.platform.marketdata.application.LatestMarketDataStore market, java.time.Clock clock) {
        return new com.kitehybrid.platform.order.application.CurrentAccountExecutionChecks(
                () -> new com.kitehybrid.platform.broker.domain.read.BrokerPositions(java.util.List.of(),java.util.List.of()),
                java.util.List::of, OperatorPreflightDryRunTest::margins, java.util.List::of,
                registry,market,properties.riskLimits(),clock);
    }
    static StandardEnvironment environment() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        return environment;
    }
    static void requireLoopback(URI uri) {
        if (uri == null || !"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
                || uri.getPort() < 1 || uri.getPort() > 65535 || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getQuery() != null)
            throw new IllegalArgumentException("Disposable loopback broker required");
    }
    static RestClient client(String base) {
        return client(base,250);
    }
    static RestClient client(String base,int readTimeout) {
        requireLoopback(URI.create(base));
        var factory = new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                requireLoopback(URI.create(connection.getURL().toString()));
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(1000); factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }
}
