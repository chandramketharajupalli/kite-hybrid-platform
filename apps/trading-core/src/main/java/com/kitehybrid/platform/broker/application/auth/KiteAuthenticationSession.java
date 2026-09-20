package com.kitehybrid.platform.broker.application.auth;

/** The existing broker session is the sole owner of active API credentials. */
public interface KiteAuthenticationSession {
    boolean enabled();
    boolean authenticated();
    boolean tokenAvailable();
    void install(KiteAccessToken token);
    void clear();
}
