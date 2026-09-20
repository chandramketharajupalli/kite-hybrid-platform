package com.kitehybrid.platform.broker.application.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

import static com.kitehybrid.platform.broker.application.auth.KiteAuthenticationException.Code.STORAGE_UNAVAILABLE;

/** Browser-bound, one-time authorization attempts; no servlet session or broker credential is retained. */
public final class KiteLoginAttemptUseCase {
    public static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final int CLEANUP_LIMIT = 100;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final KiteLoginAttemptStore store;
    private final Clock clock;

    public KiteLoginAttemptUseCase(KiteLoginAttemptStore store, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
    }

    public String start() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var now = clock.instant();
        try {
            store.deleteExpired(now, CLEANUP_LIMIT);
            store.create(new KiteLoginAttempt(digest(nonce), now, now.plus(LIFETIME)));
        } catch (RuntimeException failure) {
            throw new KiteAuthenticationException(STORAGE_UNAVAILABLE);
        }
        return nonce;
    }

    /** Both the broker round-trip state and this browser's independent cookie are mandatory. */
    public Validation validateAndConsume(String state, String browserNonce) {
        boolean statePresent = state != null;
        boolean stateValid = validNonce(state);
        boolean browserNoncePresent = browserNonce != null;
        boolean browserNonceValid = validNonce(browserNonce);
        boolean stateMatched = stateValid && browserNonceValid && MessageDigest.isEqual(
                state.getBytes(StandardCharsets.US_ASCII), browserNonce.getBytes(StandardCharsets.US_ASCII));
        Reason reason = !statePresent ? Reason.STATE_MISSING
                : !stateValid ? Reason.STATE_INVALID
                : !browserNoncePresent ? Reason.BROWSER_NONCE_MISSING
                : !browserNonceValid ? Reason.BROWSER_NONCE_INVALID
                : !stateMatched ? Reason.STATE_MISMATCH : Reason.ACCEPTED;
        boolean consumed = false;
        if (reason == Reason.ACCEPTED) {
            try {
                consumed = store.consume(digest(state), clock.instant());
            } catch (RuntimeException failure) {
                throw new KiteAuthenticationException(STORAGE_UNAVAILABLE);
            }
            // Missing, expired and already-used attempts all fail closed without exposing database state.
            if (!consumed) reason = Reason.ATTEMPT_UNAVAILABLE;
        }
        return new Validation(reason, statePresent, stateValid, browserNoncePresent, browserNonceValid,
                stateMatched, consumed);
    }

    /** Local reset invalidates the current browser's outstanding attempt without trusting a servlet session. */
    public void cancel(String browserNonce) {
        if (!validNonce(browserNonce)) return;
        try {
            store.consume(digest(browserNonce), clock.instant());
        } catch (RuntimeException failure) {
            throw new KiteAuthenticationException(STORAGE_UNAVAILABLE);
        }
    }

    private static boolean validNonce(String nonce) {
        return nonce != null && nonce.matches("[A-Za-z0-9_-]{43}");
    }

    private static String digest(String nonce) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(nonce.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    public enum Reason {
        ACCEPTED, STATE_MISSING, STATE_INVALID, BROWSER_NONCE_MISSING, BROWSER_NONCE_INVALID,
        STATE_MISMATCH, ATTEMPT_UNAVAILABLE
    }

    /** Safe diagnostic data only; never contains a nonce, digest, session ID or request token. */
    public record Validation(Reason reason, boolean statePresent, boolean stateValid,
                             boolean browserNoncePresent, boolean browserNonceValid,
                             boolean stateMatched, boolean attemptConsumed) {
        public boolean accepted() { return reason == Reason.ACCEPTED; }
    }
}
