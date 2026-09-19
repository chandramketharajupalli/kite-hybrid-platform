package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.CONFIGURATION;

/**
 * Secret strings deliberately have no public getters or value-bearing toString.
 * Validate on explicit use, not binding: absent credentials must not break startup,
 * and binding failure reports must never contain rejected credential values.
 */
@ConfigurationProperties("kite")
public final class KiteProperties {
    private final String apiKey;
    private final String apiSecret;
    private final String accessToken;
    private final boolean restEnabled;

    public KiteProperties(@DefaultValue("") String apiKey, @DefaultValue("") String apiSecret,
                          @DefaultValue("") String accessToken, @DefaultValue("false") boolean restEnabled) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiSecret = apiSecret == null ? "" : apiSecret;
        this.accessToken = accessToken == null ? "" : accessToken;
        this.restEnabled = restEnabled;
    }

    public boolean restEnabled() { return restEnabled; }
    boolean credentialsValid() {
        return apiKey.matches("[A-Za-z0-9]{1,128}")
                && accessToken.matches("[A-Za-z0-9_-]{1,256}");
    }
    void requireConfigured() {
        if (!restEnabled || !credentialsValid()) throw new BrokerReadException(CONFIGURATION);
    }
    String authorization() {
        requireConfigured();
        return "token " + apiKey + ":" + accessToken;
    }
    // apiSecret is reserved for future request-token exchange; never used for these GETs.
    public static KiteProperties fromEnvironment(Map<String, String> environment) {
        String enabled = environment.getOrDefault("KITE_REST_ENABLED", "false");
        if (!enabled.equalsIgnoreCase("true") && !enabled.equalsIgnoreCase("false"))
            throw new BrokerReadException(CONFIGURATION);
        return new KiteProperties(environment.get("KITE_API_KEY"), environment.get("KITE_API_SECRET"),
                environment.get("KITE_ACCESS_TOKEN"), Boolean.parseBoolean(enabled));
    }
    @Override public String toString() { return "KiteProperties[credentials=REDACTED]"; }
}
