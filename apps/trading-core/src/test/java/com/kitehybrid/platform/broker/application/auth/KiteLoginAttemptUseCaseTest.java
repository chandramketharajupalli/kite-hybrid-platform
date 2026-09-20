package com.kitehybrid.platform.broker.application.auth;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptUseCase.Reason.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** No broker calls, database, servlet sessions, credentials, or network are used. */
class KiteLoginAttemptUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-09-20T04:00:00Z");
    private static final String NONCE = "A".repeat(43);
    private static final String OTHER_NONCE = "B".repeat(43);
    private static final String SENSITIVE = "synthetic-database-secret-never-expose";

    @Test
    void startCreatesFreshUrlSafeNoncesButPersistsOnlyTheirDigestsForTenMinutes() throws Exception {
        KiteLoginAttemptStore store = mock(KiteLoginAttemptStore.class);
        var useCase = new KiteLoginAttemptUseCase(store, Clock.fixed(NOW, ZoneOffset.UTC));

        String first = useCase.start();
        String second = useCase.start();

        assertThat(first).matches("[A-Za-z0-9_-]{43}").doesNotContain("=");
        assertThat(second).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(first);
        var attempts = ArgumentCaptor.forClass(KiteLoginAttempt.class);
        verify(store, times(2)).create(attempts.capture());
        verify(store, times(2)).deleteExpired(NOW, 100);
        assertThat(attempts.getAllValues()).hasSize(2);
        for (int index = 0; index < 2; index++) {
            var attempt = attempts.getAllValues().get(index);
            String nonce = index == 0 ? first : second;
            assertThat(attempt.nonceDigest()).isEqualTo(digest(nonce));
            assertThat(attempt.createdAt()).isEqualTo(NOW);
            assertThat(attempt.expiresAt()).isEqualTo(NOW.plusSeconds(600));
            assertThat(attempt.toString()).doesNotContain(nonce, digest(nonce));
        }
        verifyNoMoreInteractions(store);
    }

    @Test
    void matchingStateAndBrowserProofConsumeOneDigestAndExposeOnlySafeValidationData() throws Exception {
        KiteLoginAttemptStore store = mock(KiteLoginAttemptStore.class);
        when(store.consume(digest(NONCE), NOW)).thenReturn(true);
        var useCase = new KiteLoginAttemptUseCase(store, Clock.fixed(NOW, ZoneOffset.UTC));

        var validation = useCase.validateAndConsume(NONCE, NONCE);

        assertThat(validation.accepted()).isTrue();
        assertThat(validation.reason()).isEqualTo(ACCEPTED);
        assertThat(validation.statePresent()).isTrue();
        assertThat(validation.stateValid()).isTrue();
        assertThat(validation.browserNoncePresent()).isTrue();
        assertThat(validation.browserNonceValid()).isTrue();
        assertThat(validation.stateMatched()).isTrue();
        assertThat(validation.attemptConsumed()).isTrue();
        assertThat(validation.toString()).doesNotContain(NONCE, digest(NONCE));
        verify(store).consume(digest(NONCE), NOW);
        verifyNoMoreInteractions(store);
    }

    @ParameterizedTest(name = "invalid browser correlation #{index}")
    @MethodSource("invalidCorrelations")
    void missingMalformedOrMismatchedCorrelationCannotConsumeAnything(String state, String browserNonce,
                                                                      KiteLoginAttemptUseCase.Reason reason)
            throws Exception {
        KiteLoginAttemptStore store = mock(KiteLoginAttemptStore.class);
        var useCase = new KiteLoginAttemptUseCase(store, Clock.fixed(NOW, ZoneOffset.UTC));

        var validation = useCase.validateAndConsume(state, browserNonce);

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.reason()).isEqualTo(reason);
        assertThat(validation.statePresent()).isEqualTo(state != null);
        assertThat(validation.browserNoncePresent()).isEqualTo(browserNonce != null);
        assertThat(validation.stateMatched()).isFalse();
        assertThat(validation.attemptConsumed()).isFalse();
        assertThat(validation.toString()).doesNotContain(NONCE, OTHER_NONCE, digest(NONCE));
        verifyNoInteractions(store);
    }

    private static Stream<Arguments> invalidCorrelations() {
        return Stream.of(
                Arguments.of(null, null, STATE_MISSING),
                Arguments.of(null, NONCE, STATE_MISSING),
                Arguments.of("", NONCE, STATE_INVALID),
                Arguments.of("A".repeat(42), NONCE, STATE_INVALID),
                Arguments.of("A".repeat(44), NONCE, STATE_INVALID),
                Arguments.of("A".repeat(42) + "\n", NONCE, STATE_INVALID),
                Arguments.of("A".repeat(42) + "+", NONCE, STATE_INVALID),
                Arguments.of(NONCE, null, BROWSER_NONCE_MISSING),
                Arguments.of(NONCE, "", BROWSER_NONCE_INVALID),
                Arguments.of(NONCE, "A".repeat(42), BROWSER_NONCE_INVALID),
                Arguments.of(NONCE, "A".repeat(42) + "=", BROWSER_NONCE_INVALID),
                Arguments.of(NONCE, OTHER_NONCE, STATE_MISMATCH));
    }

    @Test
    void missingExpiredOrAlreadyConsumedDatabaseAttemptIsRejectedWithoutRevealingWhich() {
        KiteLoginAttemptStore store = mock(KiteLoginAttemptStore.class);
        var useCase = new KiteLoginAttemptUseCase(store, Clock.fixed(NOW, ZoneOffset.UTC));

        var validation = useCase.validateAndConsume(NONCE, NONCE);

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.reason()).isEqualTo(ATTEMPT_UNAVAILABLE);
        assertThat(validation.stateMatched()).isTrue();
        assertThat(validation.attemptConsumed()).isFalse();
    }

    @Test
    void newServiceReusesOutstandingAttemptAndAThirdServiceRejectsItsReplay() {
        var store = new MemoryStore();
        var firstClock = new MutableClock();
        String nonce = new KiteLoginAttemptUseCase(store, firstClock).start();
        var restartedClock = new MutableClock();
        restartedClock.now = NOW.plusSeconds(300);

        assertThat(new KiteLoginAttemptUseCase(store, restartedClock).validateAndConsume(nonce, nonce).accepted())
                .isTrue();
        var replay = new KiteLoginAttemptUseCase(store, restartedClock).validateAndConsume(nonce, nonce);

        assertThat(replay.accepted()).isFalse();
        assertThat(replay.reason()).isEqualTo(ATTEMPT_UNAVAILABLE);
        assertThat(store.attempts).isEmpty();
    }

    @Test
    void restartedServiceStillRejectsAttemptAtTheExactExpiryBoundary() {
        var store = new MemoryStore();
        var clock = new MutableClock();
        String nonce = new KiteLoginAttemptUseCase(store, clock).start();
        clock.now = NOW.plusSeconds(600);

        var validation = new KiteLoginAttemptUseCase(store, clock).validateAndConsume(nonce, nonce);

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.reason()).isEqualTo(ATTEMPT_UNAVAILABLE);
        assertThat(store.attempts).hasSize(1);
    }

    @Test
    void stateRemainsUsableJustBeforeExpiry() {
        var store = new MemoryStore();
        var clock = new MutableClock();
        var useCase = new KiteLoginAttemptUseCase(store, clock);
        String nonce = useCase.start();
        clock.now = NOW.plusSeconds(600).minusNanos(1);

        assertThat(useCase.validateAndConsume(nonce, nonce).accepted()).isTrue();
    }

    @Test
    void cancelInvalidatesOnlyTheCurrentBrowserAttempt() {
        var store = new MemoryStore();
        var useCase = new KiteLoginAttemptUseCase(store, Clock.fixed(NOW, ZoneOffset.UTC));
        String currentBrowser = useCase.start();
        String otherBrowser = useCase.start();

        useCase.cancel(currentBrowser);

        assertThat(useCase.validateAndConsume(currentBrowser, currentBrowser).reason()).isEqualTo(ATTEMPT_UNAVAILABLE);
        assertThat(useCase.validateAndConsume(otherBrowser, otherBrowser).accepted()).isTrue();
    }

    @ParameterizedTest(name = "invalid cancellation #{index}")
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "non-url-safe nonce", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
    void cancelWithoutAValidBrowserNonceDoesNotAccessStorage(String nonce) {
        KiteLoginAttemptStore store = mock(KiteLoginAttemptStore.class);

        new KiteLoginAttemptUseCase(store, Clock.fixed(NOW, ZoneOffset.UTC)).cancel(nonce);

        verifyNoInteractions(store);
    }

    @ParameterizedTest
    @EnumSource(FailingOperation.class)
    void storageErrorsFailClosedWithoutExposingNoncesDigestsOrOriginalCause(FailingOperation operation)
            throws Exception {
        KiteLoginAttemptStore store = mock(KiteLoginAttemptStore.class);
        var failure = new IllegalStateException(SENSITIVE + " " + NONCE + " " + digest(NONCE));
        var useCase = new KiteLoginAttemptUseCase(store, Clock.fixed(NOW, ZoneOffset.UTC));
        switch (operation) {
            case CLEANUP -> when(store.deleteExpired(any(), anyInt())).thenThrow(failure);
            case CREATE -> doThrow(failure).when(store).create(any());
            case CONSUME, CANCEL -> when(store.consume(anyString(), any())).thenThrow(failure);
        }

        KiteAuthenticationException safe = assertThrows(KiteAuthenticationException.class, () -> {
            switch (operation) {
                case CLEANUP, CREATE -> useCase.start();
                case CONSUME -> useCase.validateAndConsume(NONCE, NONCE);
                case CANCEL -> useCase.cancel(NONCE);
            }
        });

        assertThat(safe.code()).isEqualTo(KiteAuthenticationException.Code.STORAGE_UNAVAILABLE);
        assertThat(safe.getCause()).isNull();
        assertThat(safe.getSuppressed()).isEmpty();
        var stack = new StringWriter();
        safe.printStackTrace(new PrintWriter(stack));
        assertThat(stack.toString()).doesNotContain(SENSITIVE, NONCE, digest(NONCE), "IllegalStateException");
        if (operation == FailingOperation.CLEANUP) verify(store, never()).create(any());
    }

    private static String digest(String nonce) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(nonce.getBytes(StandardCharsets.US_ASCII)));
    }

    private enum FailingOperation { CLEANUP, CREATE, CONSUME, CANCEL }

    private static final class MutableClock extends Clock {
        private Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final class MemoryStore implements KiteLoginAttemptStore {
        private final Map<String, KiteLoginAttempt> attempts = new HashMap<>();
        @Override public void create(KiteLoginAttempt attempt) {
            if (attempts.putIfAbsent(attempt.nonceDigest(), attempt) != null) {
                throw new KiteLoginAttemptStoreException();
            }
        }
        @Override public boolean consume(String nonceDigest, Instant now) {
            KiteLoginAttempt attempt = attempts.get(nonceDigest);
            if (attempt == null || now.isBefore(attempt.createdAt()) || !now.isBefore(attempt.expiresAt())) {
                return false;
            }
            return attempts.remove(nonceDigest, attempt);
        }
        @Override public int deleteExpired(Instant now, int limit) {
            var expired = new ArrayList<>(attempts.values().stream()
                    .filter(attempt -> !now.isBefore(attempt.expiresAt())).limit(limit).toList());
            expired.forEach(attempt -> attempts.remove(attempt.nonceDigest()));
            return expired.size();
        }
    }
}
