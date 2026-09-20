package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationSession;
import java.time.Clock;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;

/** The single active broker session. Reads and credential changes share this monitor. */
public final class KiteSession implements KiteAuthenticationSession {
    public enum State { DISABLED, NOT_CONFIGURED, AUTH_REQUIRED, UNVERIFIED, AUTHENTICATED, INVALIDATED }
    private final KiteProperties properties;
    private final Clock clock;
    private State state;
    private KiteAccessToken token;

    public KiteSession(KiteProperties properties) {
        this(properties, Clock.systemUTC());
        if (properties.restEnabled() && properties.credentialsValid()) {
            install(new KiteAccessToken(properties.initialAccessToken(), clock.instant(),
                    KiteAuthenticationAdapter.expiresAt(clock.instant())));
        }
    }
    /** Application credentials are restored from the store; legacy diagnostics use the other constructor. */
    public KiteSession(KiteProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        clear();
    }
    synchronized String authorization() {
        expire();
        if (state == State.DISABLED || state == State.NOT_CONFIGURED)
            throw new BrokerReadException(CONFIGURATION);
        if (token == null) throw new BrokerReadException(AUTHENTICATION);
        return "token " + properties.apiKey() + ":" + token.value();
    }
    synchronized void profileValidated() {
        expire();
        if (state == State.UNVERIFIED) state = State.AUTHENTICATED;
    }
    synchronized void invalidate() { token = null; state = State.INVALIDATED; }
    @Override public boolean enabled() { return properties.restEnabled(); }
    @Override public synchronized boolean authenticated() { return state() == State.AUTHENTICATED; }
    @Override public synchronized boolean tokenAvailable() { expire(); return token != null; }
    @Override public synchronized void install(KiteAccessToken candidate) {
        if (!enabled() || !properties.apiKeyValid()) throw new BrokerReadException(CONFIGURATION);
        if (!candidate.isUsableAt(clock.instant())) throw new BrokerReadException(AUTHENTICATION);
        token = candidate;
        state = State.UNVERIFIED;
    }
    @Override public synchronized void clear() {
        token = null;
        state = !properties.restEnabled() ? State.DISABLED
                : !properties.apiKeyValid() ? State.NOT_CONFIGURED : State.AUTH_REQUIRED;
    }
    public synchronized State state() { expire(); return state; }
    private void expire() {
        if (token != null && !token.isUsableAt(clock.instant())) {
            token = null;
            state = State.AUTH_REQUIRED;
        }
    }
    @Override public String toString() { return "KiteSession[state=" + state() + ", credentials=REDACTED]"; }
}
