package com.kitehybrid.platform.broker.application.auth;

import java.util.Optional;

/** Durable authentication state; validity is decided by the authentication use case. */
public interface KiteAccessTokenStore {
    Optional<KiteAccessToken> loadCurrent();
    void save(KiteAccessToken token);
    void clear();
}
