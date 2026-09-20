package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationException;
import java.net.URI;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import static com.kitehybrid.platform.broker.application.auth.KiteAuthenticationException.Code.CONFIGURATION;

@ConfigurationProperties("kite.auth")
public final class KiteAuthenticationProperties {
    private final String redirectUrl;
    private final String encryptionKey;

    public KiteAuthenticationProperties(
            @DefaultValue("http://localhost:8080/api/broker/kite/auth/callback") String redirectUrl,
            @DefaultValue("") String encryptionKey) {
        this.redirectUrl = redirectUrl == null ? "" : redirectUrl;
        this.encryptionKey = encryptionKey == null ? "" : encryptionKey;
    }

    String encryptionKey() { return encryptionKey; }
    URI redirectUri() {
        try {
            URI uri = URI.create(redirectUrl);
            boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
                    || "[::1]".equals(uri.getHost());
            if ((!"https".equals(uri.getScheme()) && !(local && "http".equals(uri.getScheme())))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !"/api/broker/kite/auth/callback".equals(uri.getPath()))
                throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException invalid) { throw new KiteAuthenticationException(CONFIGURATION); }
    }
    void requireConfigured() {
        redirectUri();
        try {
            if (Base64.getDecoder().decode(encryptionKey).length != 32) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw new KiteAuthenticationException(CONFIGURATION); }
    }
    @Override public String toString() { return "KiteAuthenticationProperties[credentials=REDACTED]"; }
}
