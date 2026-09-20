package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationSession;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;

/** Single broker session. REST/auth share this monitor; WebSocket reads use its immutable published view. */
public final class KiteSession implements KiteAuthenticationSession {
    public enum State { DISABLED, NOT_CONFIGURED, AUTH_REQUIRED, UNVERIFIED, AUTHENTICATED, INVALIDATED }
    private final KiteProperties properties;
    private final Clock clock;
    private State state;
    private KiteAccessToken token;
    private long marketDataGeneration;
    private final AtomicLong rejectedMarketDataGeneration = new AtomicLong(Long.MIN_VALUE);
    private volatile MarketDataView marketDataView;

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
        if (state == State.UNVERIFIED) {
            state = State.AUTHENTICATED;
            publishMarketDataView();
        }
    }
    synchronized void invalidate() {
        token = null; state = State.INVALIDATED; marketDataGeneration++;
        publishMarketDataView();
    }
    /** Credentials stay inside the broker infrastructure boundary and require profile validation. */
    MarketDataCredentials marketDataCredentials() {
        var view = marketDataView;
        if (!usable(view.status()) || view.credentials() == null) throw new BrokerReadException(AUTHENTICATION);
        return view.credentials();
    }
    synchronized long marketDataGeneration() { expire(); return marketDataGeneration; }
    /** Non-secret hot-path view never waits behind an existing REST request's session monitor. */
    MarketDataStatus marketDataStatus() {
        var status = marketDataView.status();
        return usable(status) ? status : new MarketDataStatus(false, status.generation(), status.expiresAt());
    }
    private boolean usable(MarketDataStatus status) {
        return status.authenticated() && clock.instant().isBefore(status.expiresAt())
                && rejectedMarketDataGeneration.get() != status.generation();
    }
    /** Fail closed immediately on I/O threads; synchronized session entry points apply it lazily. */
    boolean rejectMarketData(long expectedGeneration) {
        if (marketDataView.status().generation() != expectedGeneration) return false;
        rejectedMarketDataGeneration.accumulateAndGet(expectedGeneration, Math::max);
        return true;
    }
    /** A late rejection of an old WebSocket must never invalidate a replacement login. */
    synchronized boolean invalidateMarketData(long expectedGeneration) {
        expire();
        if (expectedGeneration != marketDataGeneration || token == null) return false;
        invalidate();
        return true;
    }
    @Override public boolean enabled() { return properties.restEnabled(); }
    @Override public synchronized boolean authenticated() { return state() == State.AUTHENTICATED; }
    @Override public synchronized boolean tokenAvailable() { expire(); return token != null; }
    @Override public synchronized void install(KiteAccessToken candidate) {
        if (!enabled() || !properties.apiKeyValid()) throw new BrokerReadException(CONFIGURATION);
        if (!candidate.isUsableAt(clock.instant())) throw new BrokerReadException(AUTHENTICATION);
        token = candidate;
        state = State.UNVERIFIED;
        marketDataGeneration++;
        publishMarketDataView();
    }
    @Override public synchronized void clear() {
        token = null;
        marketDataGeneration++;
        state = !properties.restEnabled() ? State.DISABLED
                : !properties.apiKeyValid() ? State.NOT_CONFIGURED : State.AUTH_REQUIRED;
        publishMarketDataView();
    }
    public synchronized State state() { expire(); return state; }
    private void expire() {
        if (token != null && rejectedMarketDataGeneration.get() == marketDataGeneration) {
            invalidate();
        } else if (token != null && !token.isUsableAt(clock.instant())) {
            token = null;
            state = State.AUTH_REQUIRED;
            marketDataGeneration++;
            publishMarketDataView();
        }
    }
    /** Existing synchronized mutations publish one coherent, immutable market-data view. */
    private void publishMarketDataView() {
        var status = new MarketDataStatus(state == State.AUTHENTICATED, marketDataGeneration,
                token == null ? Instant.EPOCH : token.expiresAt());
        var credentials = status.authenticated()
                ? new MarketDataCredentials(properties.apiKey(), token.value(), marketDataGeneration) : null;
        marketDataView = new MarketDataView(status, credentials);
    }
    @Override public String toString() { return "KiteSession[state=" + state() + ", credentials=REDACTED]"; }
    record MarketDataStatus(boolean authenticated, long generation, Instant expiresAt) {}
    private record MarketDataView(MarketDataStatus status, MarketDataCredentials credentials) {}
    static final class MarketDataCredentials {
        private final String apiKey;
        private final String accessToken;
        private final long generation;
        private MarketDataCredentials(String apiKey, String accessToken, long generation) {
            this.apiKey = apiKey;
            this.accessToken = accessToken;
            this.generation = generation;
        }
        String apiKey() { return apiKey; }
        String accessToken() { return accessToken; }
        long generation() { return generation; }
        @Override public String toString() { return "MarketDataCredentials[credentials=REDACTED]"; }
    }
}
