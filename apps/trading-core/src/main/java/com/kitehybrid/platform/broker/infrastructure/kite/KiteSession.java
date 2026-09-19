package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import java.util.concurrent.atomic.AtomicReference;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.AUTHENTICATION;

/** Externally supplied access-token session; no login, refresh or token exchange. */
public final class KiteSession {
    public enum State { DISABLED, NOT_CONFIGURED, UNVERIFIED, VALIDATED, INVALIDATED }
    private final KiteProperties properties;
    private final AtomicReference<State> state;

    public KiteSession(KiteProperties properties) {
        this.properties = properties;
        this.state = new AtomicReference<>(!properties.restEnabled() ? State.DISABLED
                : properties.credentialsValid() ? State.UNVERIFIED : State.NOT_CONFIGURED);
    }
    String authorization() {
        if (state.get() == State.INVALIDATED) throw new BrokerReadException(AUTHENTICATION);
        return properties.authorization();
    }
    void profileValidated() { state.compareAndSet(State.UNVERIFIED, State.VALIDATED); }
    void invalidate() { state.set(State.INVALIDATED); }
    public State state() { return state.get(); }
    @Override public String toString() { return "KiteSession[state=" + state() + ", credentials=REDACTED]"; }
}
