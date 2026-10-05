package com.kitehybrid.platform.broker.application.auth;

import com.kitehybrid.platform.account.application.ValidateBrokerProfileUseCase;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.instrument.application.RefreshInstrumentRegistryUseCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static com.kitehybrid.platform.broker.application.auth.KiteAuthenticationException.Code.*;

/**
 * Serializes restore, callback and reset for one broker account. Interactive login remains with
 * Zerodha; no broker credentials or token values are returned to the delivery layer.
 */
public final class KiteAuthenticationUseCase implements com.kitehybrid.platform.shared.application.ExecutionInitialization {
    public static final String LOGIN_ENDPOINT = "/api/broker/kite/auth/login";
    private static final int MAX_REMEMBERED_REQUESTS = 256;
    private final KiteAuthenticationGateway gateway;
    private final KiteAuthenticationSession session;
    private final KiteAccessTokenStore store;
    private final ValidateBrokerProfileUseCase profiles;
    private final RefreshInstrumentRegistryUseCase instruments;
    private final Clock clock;
    private final Map<String, Attempt> attempts = new LinkedHashMap<>();
    private KiteAccessToken activeToken;
    private long generation;
    private volatile java.util.Optional<java.util.UUID> initializedIdentity = java.util.Optional.empty();
    private String unavailableCode;

    public KiteAuthenticationUseCase(KiteAuthenticationGateway gateway,
                                     KiteAuthenticationSession session,
                                     KiteAccessTokenStore store,
                                     ValidateBrokerProfileUseCase profiles,
                                     RefreshInstrumentRegistryUseCase instruments,
                                     Clock clock) {
        this.gateway = Objects.requireNonNull(gateway);
        this.session = Objects.requireNonNull(session);
        this.store = Objects.requireNonNull(store);
        this.profiles = Objects.requireNonNull(profiles);
        this.instruments = Objects.requireNonNull(instruments);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Missing credentials, an expired token or temporary broker failure never stop startup. */
    public synchronized Status restore() {
        if (!session.enabled()) return currentStatus();
        if (activeToken != null) {
            reconcileSession();
            if (activeToken != null && session.authenticated()) {
                initializeInstruments();
                return currentStatus();
            }
        }
        try {
            var stored = store.loadCurrent();
            if (stored.isEmpty()) {
                clearSession();
                unavailableCode = null;
                return currentStatus();
            }
            var token = stored.orElseThrow();
            if (!token.isUsableAt(clock.instant())) {
                clearStoredSession();
                return currentStatus();
            }
            installAndValidate(token);
            unavailableCode = null;
            initializeInstruments();
        } catch (BrokerReadException failure) {
            clearSession();
            if (failure.category() == BrokerReadException.Category.AUTHENTICATION) {
                try { clearStoredSession(); }
                catch (KiteAuthenticationException unavailable) { unavailableCode = "KITE_AUTH_UNAVAILABLE"; }
            } else {
                // Keep the durable credential: timeouts/rate limits do not prove it is invalid.
                unavailableCode = "KITE_AUTH_UNAVAILABLE";
            }
        } catch (RuntimeException failure) {
            clearSession();
            unavailableCode = "KITE_AUTH_UNAVAILABLE";
        }
        return currentStatus();
    }

    public synchronized Status status() {
        if (session.enabled()) reconcileSession();
        return currentStatus();
    }

    public synchronized String loginUrl(String state) {
        requireEnabled();
        try { return gateway.loginUrl(state); }
        catch (KiteAuthenticationException safe) { throw safe; }
        catch (RuntimeException failure) { throw new KiteAuthenticationException(CONFIGURATION); }
    }

    public synchronized Status complete(String requestToken, String status, String action, String type) {
        requireEnabled();
        validateCallback(requestToken, status, action);
        reconcileSession();
        String fingerprint = fingerprint(requestToken);
        Attempt previous = attempts.get(fingerprint);
        if (previous != null) {
            if (previous.succeeded() && previous.generation() == generation
                    && activeToken != null && session.authenticated()) {
                initializeInstruments();
                return currentStatus();
            }
            throw new KiteAuthenticationException(REQUEST_TOKEN_ALREADY_USED);
        }

        // Record BEFORE spending a short-lived, single-use token; a timeout is ambiguous.
        remember(fingerprint, new Attempt(false, generation));
        KiteAccessToken candidate;
        try {
            candidate = Objects.requireNonNull(gateway.exchange(requestToken));
            if (!candidate.isUsableAt(clock.instant())) throw new KiteAuthenticationException(AUTHENTICATION_FAILED);
        } catch (KiteAuthenticationException safe) { throw safe; }
        catch (RuntimeException failure) { throw new KiteAuthenticationException(EXCHANGE_FAILED); }

        try {
            installAndValidate(candidate);
        } catch (BrokerReadException failure) {
            if (failure.category() == BrokerReadException.Category.AUTHENTICATION) {
                clearStoredSession();
                throw new KiteAuthenticationException(AUTHENTICATION_FAILED);
            }
            clearSession();
            unavailableCode = "KITE_AUTH_UNAVAILABLE";
            throw new KiteAuthenticationException(BROKER_UNAVAILABLE);
        } catch (RuntimeException failure) {
            clearSession();
            unavailableCode = "KITE_AUTH_UNAVAILABLE";
            throw new KiteAuthenticationException(BROKER_UNAVAILABLE);
        }
        try {
            store.save(candidate);
        } catch (RuntimeException failure) {
            clearSession();
            unavailableCode = "KITE_AUTH_UNAVAILABLE";
            throw new KiteAuthenticationException(STORAGE_UNAVAILABLE);
        }
        unavailableCode = null;
        remember(fingerprint, new Attempt(true, generation));
        initializeInstruments();
        return currentStatus();
    }

    /** Clears this application's credential only; it does not log out the Zerodha account. */
    public synchronized Status reset() {
        clearStoredSession();
        return currentStatus();
    }

    private void installAndValidate(KiteAccessToken token) {
        clearSession();
        session.install(token);
        profiles.validate();
        if (!session.authenticated()) throw new BrokerReadException(BrokerReadException.Category.AUTHENTICATION);
        activeToken = token;
    }

    private void initializeInstruments() {
        if (initializationReady() || activeToken == null || !session.authenticated()) return;
        try {
            var identity = session.executionIdentity();
            instruments.refresh();
            if (identity.isPresent() && identity.equals(session.executionIdentity())) initializedIdentity = identity;
        } catch (BrokerReadException failure) {
            if (failure.category() == BrokerReadException.Category.AUTHENTICATION) {
                try { clearStoredSession(); }
                catch (KiteAuthenticationException unavailable) { unavailableCode = "KITE_AUTH_UNAVAILABLE"; }
            }
            // Authentication survives a transient reference-data failure. A later restore retries.
        } catch (RuntimeException failure) {
            // The registry retains its previous atomic snapshot; report that initialization is pending.
        }
    }

    private void reconcileSession() {
        if (activeToken != null && (!activeToken.isUsableAt(clock.instant()) || !session.authenticated())) {
            try { clearStoredSession(); }
            catch (KiteAuthenticationException unavailable) { unavailableCode = "KITE_AUTH_UNAVAILABLE"; }
        }
    }

    private void clearStoredSession() {
        clearSession();
        try {
            store.clear();
            unavailableCode = null;
        } catch (RuntimeException failure) {
            unavailableCode = "KITE_AUTH_UNAVAILABLE";
            throw new KiteAuthenticationException(STORAGE_UNAVAILABLE);
        }
    }

    private void clearSession() {
        initializedIdentity = java.util.Optional.empty();
        session.clear();
        activeToken = null;
        generation++;
    }

    /** The status endpoint and execution share this state. Never acquire the authentication monitor
     * from transport's session monitor, reconcile credentials, refresh instruments or touch storage here. */
    @Override public boolean initializationReady() {
        var initialized = initializedIdentity;
        return initialized.isPresent() && initialized.equals(session.executionIdentity());
    }

    private Status currentStatus() {
        if (!session.enabled()) return new Status(false, "KITE", false, "KITE_AUTH_DISABLED", null, false);
        boolean authenticated = activeToken != null && session.authenticated();
        boolean initialized = initializationReady();
        String code = authenticated ? initialized ? "KITE_AUTHENTICATED" : "KITE_INITIALIZATION_PENDING"
                : unavailableCode != null ? unavailableCode : "KITE_AUTH_REQUIRED";
        return new Status(authenticated, "KITE", session.tokenAvailable(), code,
                authenticated ? null : LOGIN_ENDPOINT, authenticated && initialized);
    }

    private void requireEnabled() {
        if (!session.enabled()) throw new KiteAuthenticationException(DISABLED);
    }

    private static void validateCallback(String requestToken, String status, String action) {
        if (requestToken == null || !requestToken.matches("[A-Za-z0-9_-]{1,256}"))
            throw new KiteAuthenticationException(INVALID_CALLBACK);
        if (status != null && !"success".equalsIgnoreCase(status))
            throw new KiteAuthenticationException(LOGIN_REJECTED);
        if (action != null && !"login".equalsIgnoreCase(action))
            throw new KiteAuthenticationException(LOGIN_REJECTED);
    }

    private void remember(String fingerprint, Attempt attempt) {
        attempts.put(fingerprint, attempt);
        while (attempts.size() > MAX_REMEMBERED_REQUESTS) attempts.remove(attempts.keySet().iterator().next());
    }

    private static String fingerprint(String requestToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(requestToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private record Attempt(boolean succeeded, long generation) { }

    public record Status(boolean authenticated, String broker, boolean tokenAvailable,
                         String code, String loginUrl, boolean initializationReady) { }
}
