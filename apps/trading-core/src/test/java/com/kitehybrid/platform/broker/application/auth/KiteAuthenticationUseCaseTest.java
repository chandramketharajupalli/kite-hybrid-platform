package com.kitehybrid.platform.broker.application.auth;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kitehybrid.platform.account.application.ValidateBrokerProfileUseCase;
import com.kitehybrid.platform.account.domain.BrokerProfile;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.instrument.application.RefreshInstrumentRegistryUseCase;
import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.kitehybrid.platform.broker.application.auth.KiteAuthenticationException.Code.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Every broker operation is an in-process fake; no real credentials or network calls are used. */
class KiteAuthenticationUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-09-20T06:00:00Z");
    private static final KiteAccessToken TOKEN = new KiteAccessToken("syntheticAccessCredential", NOW,
            Instant.parse("2026-09-21T00:30:00Z"));
    private static final String REQUEST = "syntheticRequestCredential";
    private static final String SENSITIVE = "syntheticSecretNeverExpose";

    @Test void noTokenRequiresInteractiveAuthenticationWithoutCallingBroker() {
        Fixture fixture = new Fixture();

        var result = fixture.useCase.restore();

        assertThat(result.authenticated()).isFalse();
        assertThat(result.tokenAvailable()).isFalse();
        assertThat(result.code()).isEqualTo("KITE_AUTH_REQUIRED");
        assertThat(result.loginUrl()).isEqualTo(KiteAuthenticationUseCase.LOGIN_ENDPOINT);
        assertThat(fixture.profileCalls).isZero();
        assertThat(fixture.refreshCalls).isZero();
        assertThat(fixture.gateway.exchanges).isZero();
    }

    @Test void storedValidTokenIsValidatedReusedAndInitializesInstruments() {
        Fixture fixture = new Fixture();
        fixture.store.token = TOKEN;

        var result = fixture.useCase.restore();

        assertThat(result.authenticated()).isTrue();
        assertThat(result.initializationReady()).isTrue();
        assertThat(result.code()).isEqualTo("KITE_AUTHENTICATED");
        assertThat(result.loginUrl()).isNull();
        assertThat(fixture.session.token).isSameAs(TOKEN);
        assertThat(fixture.gateway.exchanges).isZero();
        assertThat(fixture.profileCalls).isEqualTo(1);
        assertThat(fixture.refreshCalls).isEqualTo(1);
    }

    @Test void rejectedStoredTokenIsClearedAndStartupContinues() {
        Fixture fixture = new Fixture();
        fixture.store.token = TOKEN;
        fixture.profileFailure = new BrokerReadException(BrokerReadException.Category.AUTHENTICATION);

        var result = fixture.useCase.restore();

        assertThat(result.code()).isEqualTo("KITE_AUTH_REQUIRED");
        assertThat(result.authenticated()).isFalse();
        assertThat(fixture.store.token).isNull();
        assertThat(fixture.session.token).isNull();
        assertThat(fixture.refreshCalls).isZero();
    }

    @Test void expiredStoredTokenIsClearedBeforeAnyBrokerCall() {
        Fixture fixture = new Fixture();
        fixture.store.token = TOKEN;
        fixture.clock.now = TOKEN.expiresAt();

        assertThat(fixture.useCase.restore().code()).isEqualTo("KITE_AUTH_REQUIRED");

        assertThat(fixture.store.token).isNull();
        assertThat(fixture.profileCalls).isZero();
    }

    @Test void transientProfileFailurePreservesStoredTokenForRetryAndFailsClosed() {
        Fixture fixture = new Fixture();
        fixture.store.token = TOKEN;
        fixture.profileFailure = new BrokerReadException(BrokerReadException.Category.TRANSPORT);

        assertThat(fixture.useCase.restore().code()).isEqualTo("KITE_AUTH_UNAVAILABLE");
        assertThat(fixture.useCase.status().authenticated()).isFalse();
        assertThat(fixture.session.token).isNull();
        assertThat(fixture.store.token).isSameAs(TOKEN);

        fixture.profileFailure = null;
        assertThat(fixture.useCase.restore().authenticated()).isTrue();
        assertThat(fixture.gateway.exchanges).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "bad token", "line\nbreak", "token?query=value"})
    void missingOrMalformedRequestTokenIsRejectedBeforeExchange(String token) {
        Fixture fixture = new Fixture();

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.useCase.complete(token, "success", "login", "unused")), INVALID_CALLBACK);

        assertThat(fixture.gateway.exchanges).isZero();
        assertThat(fixture.store.saves).isZero();
    }

    @Test void oversizedRequestTokenIsRejectedBeforeSpendingIt() {
        Fixture fixture = new Fixture();
        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.useCase.complete("x".repeat(257), "success", "login", null)), INVALID_CALLBACK);
        assertThat(fixture.gateway.exchanges).isZero();
        assertThat(fixture.store.saves).isZero();
    }

    @Test void unsuccessfulCallbackDoesNotExchangeOrPersistToken() {
        Fixture fixture = new Fixture();

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.useCase.complete(REQUEST, "error", "login", null)), LOGIN_REJECTED);

        assertThat(fixture.gateway.exchanges).isZero();
        assertThat(fixture.store.token).isNull();
    }

    @Test void successfulCallbackExchangesValidatesPersistsAndUpdatesSessionBeforeInitialization() {
        Fixture fixture = new Fixture();

        var result = fixture.complete();

        assertThat(result.authenticated()).isTrue();
        assertThat(result.initializationReady()).isTrue();
        assertThat(fixture.session.token).isSameAs(TOKEN);
        assertThat(fixture.store.token).isSameAs(TOKEN);
        assertThat(fixture.gateway.exchanges).isEqualTo(1);
        assertThat(fixture.events).containsExactly("exchange", "install", "validate", "persist", "refresh");
    }

    @Test void optionalCallbackMetadataMayBeAbsent() {
        Fixture fixture = new Fixture();
        assertThat(fixture.useCase.complete(REQUEST, null, null, null).authenticated()).isTrue();
    }

    @Test void exchangeFailureDoesNotPersistAndSameSingleUseTokenIsNotExchangedAgain() {
        Fixture fixture = new Fixture();
        fixture.gateway.failure = new IllegalStateException(SENSITIVE);

        assertSafe(assertThrows(KiteAuthenticationException.class, fixture::complete), EXCHANGE_FAILED);
        assertSafe(assertThrows(KiteAuthenticationException.class, fixture::complete), REQUEST_TOKEN_ALREADY_USED);

        assertThat(fixture.gateway.exchanges).isEqualTo(1);
        assertThat(fixture.store.saves).isZero();
        assertThat(fixture.session.token).isNull();
    }

    @Test void profileRejectionOfExchangedTokenPreventsPersistenceAndInitialization() {
        Fixture fixture = new Fixture();
        fixture.profileFailure = new BrokerReadException(BrokerReadException.Category.AUTHENTICATION);

        assertSafe(assertThrows(KiteAuthenticationException.class, fixture::complete), AUTHENTICATION_FAILED);

        assertThat(fixture.store.saves).isZero();
        assertThat(fixture.session.token).isNull();
        assertThat(fixture.refreshCalls).isZero();
    }

    @Test void storageFailureFailsClosedAfterValidation() {
        Fixture fixture = new Fixture();
        fixture.store.failSave = true;

        assertSafe(assertThrows(KiteAuthenticationException.class, fixture::complete), STORAGE_UNAVAILABLE);

        assertThat(fixture.session.token).isNull();
        assertThat(fixture.useCase.status().authenticated()).isFalse();
        assertThat(fixture.refreshCalls).isZero();
    }

    @Test void restartLoadsThePreviouslySavedTokenWithoutExchangingAnotherRequestToken() {
        Fixture firstProcess = new Fixture();
        firstProcess.complete();
        Fixture restartedProcess = new Fixture(firstProcess.store);

        var restored = restartedProcess.useCase.restore();

        assertThat(restored.authenticated()).isTrue();
        assertThat(restored.initializationReady()).isTrue();
        assertThat(restartedProcess.gateway.exchanges).isZero();
        assertThat(restartedProcess.session.token).isSameAs(TOKEN);
    }

    @Test void simultaneousDuplicateCallbacksExchangeOnlyOnce() throws Exception {
        Fixture fixture = new Fixture();
        var executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<KiteAuthenticationUseCase.Status>> callbacks = new ArrayList<>();
            for (int index = 0; index < 12; index++) callbacks.add(fixture::complete);
            for (var result : executor.invokeAll(callbacks)) assertThat(result.get().authenticated()).isTrue();
        } finally { executor.shutdownNow(); }

        assertThat(fixture.gateway.exchanges).isEqualTo(1);
        assertThat(fixture.store.saves).isEqualTo(1);
        assertThat(fixture.profileCalls).isEqualTo(1);
        assertThat(fixture.refreshCalls).isEqualTo(1);
    }

    @Test void resetClearsLocalStateAndOldCallbackCannotResurrectIt() {
        Fixture fixture = new Fixture();
        fixture.complete();

        assertThat(fixture.useCase.reset().code()).isEqualTo("KITE_AUTH_REQUIRED");
        assertThat(fixture.store.token).isNull();
        assertThat(fixture.session.token).isNull();
        assertSafe(assertThrows(KiteAuthenticationException.class, fixture::complete), REQUEST_TOKEN_ALREADY_USED);
        assertThat(fixture.gateway.exchanges).isEqualTo(1);
    }

    @Test void expiryAfterAuthenticationClearsStateAndDuplicateCannotReviveSession() {
        Fixture fixture = new Fixture();
        fixture.complete();
        fixture.clock.now = TOKEN.expiresAt();

        assertThat(fixture.useCase.status().code()).isEqualTo("KITE_AUTH_REQUIRED");
        assertThat(fixture.store.token).isNull();
        assertSafe(assertThrows(KiteAuthenticationException.class, fixture::complete), REQUEST_TOKEN_ALREADY_USED);
    }

    @Test void runtimeBrokerRejectionClearsTheDurableTokenOnStatus() {
        Fixture fixture = new Fixture();
        fixture.complete();
        fixture.session.validated = false;

        assertThat(fixture.useCase.status().code()).isEqualTo("KITE_AUTH_REQUIRED");
        assertThat(fixture.store.token).isNull();
    }

    @Test void transientInstrumentFailureKeepsAuthenticationAndRetryFinishesInitialization() {
        Fixture fixture = new Fixture();
        fixture.refreshFailure = new BrokerReadException(BrokerReadException.Category.BROKER_API);

        var pending = fixture.complete();
        assertThat(pending.authenticated()).isTrue();
        assertThat(pending.initializationReady()).isFalse();
        assertThat(pending.code()).isEqualTo("KITE_INITIALIZATION_PENDING");
        assertThat(fixture.store.token).isSameAs(TOKEN);

        fixture.refreshFailure = null;
        assertThat(fixture.useCase.restore().initializationReady()).isTrue();
        assertThat(fixture.gateway.exchanges).isEqualTo(1);
        assertThat(fixture.profileCalls).isEqualTo(1);
    }

    @Test void disabledBrokerStartupDoesNotTouchStorageOrBrokerApis() {
        Fixture fixture = new Fixture();
        fixture.session.enabled = false;
        fixture.store.failLoad = true;

        assertThat(fixture.useCase.restore().code()).isEqualTo("KITE_AUTH_DISABLED");
        assertThat(fixture.useCase.status().loginUrl()).isNull();
        assertSafe(assertThrows(KiteAuthenticationException.class, fixture::complete), DISABLED);
        assertThat(fixture.store.loads).isZero();
        assertThat(fixture.store.clears).isZero();
        assertThat(fixture.gateway.exchanges).isZero();
        assertThat(fixture.profileCalls).isZero();
    }

    @Test void explicitResetStillClearsDurableCredentialWhenBrokerIsDisabled() {
        Fixture fixture = new Fixture();
        fixture.session.enabled = false;
        fixture.store.token = TOKEN;

        assertThat(fixture.useCase.reset().code()).isEqualTo("KITE_AUTH_DISABLED");

        assertThat(fixture.store.clears).isEqualTo(1);
        assertThat(fixture.store.token).isNull();
        assertThat(fixture.gateway.exchanges).isZero();
        assertThat(fixture.profileCalls).isZero();
    }

    @Test void storageUnavailableDoesNotCrashStartupOrExposeItsMessage() {
        Fixture fixture = new Fixture();
        fixture.store.failLoad = true;

        var result = fixture.useCase.restore();

        assertThat(result.code()).isEqualTo("KITE_AUTH_UNAVAILABLE");
        assertThat(result.toString()).doesNotContain(SENSITIVE);
    }

    @Test void statusSerializesOnlySafeFieldsAndLoginUrlDelegatesOpaqueState() throws Exception {
        Fixture fixture = new Fixture();
        assertThat(fixture.useCase.loginUrl("syntheticState")).isEqualTo("https://kite.zerodha.com/connect/login?v=3");
        assertThat(fixture.gateway.state).isEqualTo("syntheticState");

        String json = JsonMapper.builder().build().writeValueAsString(fixture.complete());

        assertThat(json).contains("\"authenticated\":true", "\"broker\":\"KITE\"", "\"tokenAvailable\":true")
                .doesNotContain(TOKEN.value(), REQUEST, SENSITIVE, "apiSecret", "accessToken", "requestToken");
    }

    private static void assertSafe(KiteAuthenticationException failure, KiteAuthenticationException.Code code) {
        assertThat(failure.code()).isEqualTo(code);
        assertThat(failure.getCause()).isNull();
        assertThat(failure.getSuppressed()).isEmpty();
        assertThat(failure.toString()).doesNotContain(TOKEN.value(), REQUEST, SENSITIVE);
    }

    private static final class Fixture {
        final List<String> events = new ArrayList<>();
        final FakeStore store;
        final FakeSession session = new FakeSession(events);
        final FakeGateway gateway = new FakeGateway(events);
        final MutableClock clock = new MutableClock();
        final KiteAuthenticationUseCase useCase;
        RuntimeException profileFailure;
        RuntimeException refreshFailure;
        int profileCalls;
        int refreshCalls;

        Fixture() { this(new FakeStore()); }

        Fixture(FakeStore store) {
            this.store = store;
            this.store.events = events;
            var profiles = new ValidateBrokerProfileUseCase(() -> {
                profileCalls++;
                events.add("validate");
                if (profileFailure != null) throw profileFailure;
                session.validated = true;
                return new BrokerProfile("ZERODHA", "TEST123", Set.of("NSE"));
            });
            var instruments = new RefreshInstrumentRegistryUseCase(() -> {
                refreshCalls++;
                events.add("refresh");
                if (refreshFailure != null) throw refreshFailure;
                return List.of(Instrument.create(new BrokerInstrumentId("KITE", "123"), "TEST", "NSE",
                        "CASH", InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1));
            }, new InMemoryInstrumentRegistry(), clock);
            useCase = new KiteAuthenticationUseCase(gateway, session, store, profiles, instruments, clock);
        }

        KiteAuthenticationUseCase.Status complete() { return useCase.complete(REQUEST, "success", "login", "login"); }
    }

    private static final class FakeGateway implements KiteAuthenticationGateway {
        final List<String> events;
        RuntimeException failure;
        String state;
        int exchanges;
        FakeGateway(List<String> events) { this.events = events; }
        @Override public String loginUrl(String state) {
            this.state = state;
            return "https://kite.zerodha.com/connect/login?v=3";
        }
        @Override public KiteAccessToken exchange(String requestToken) {
            exchanges++;
            events.add("exchange");
            if (failure != null) throw failure;
            return TOKEN;
        }
    }

    private static final class FakeSession implements KiteAuthenticationSession {
        final List<String> events;
        boolean enabled = true;
        boolean validated;
        KiteAccessToken token;
        FakeSession(List<String> events) { this.events = events; }
        @Override public boolean enabled() { return enabled; }
        @Override public boolean authenticated() { return validated && token != null; }
        @Override public boolean tokenAvailable() { return token != null; }
        @Override public void install(KiteAccessToken token) {
            events.add("install");
            this.token = token;
            validated = false;
        }
        @Override public void clear() { token = null; validated = false; }
    }

    private static final class FakeStore implements KiteAccessTokenStore {
        List<String> events = new ArrayList<>();
        KiteAccessToken token;
        boolean failLoad;
        boolean failSave;
        int loads;
        int saves;
        int clears;
        @Override public Optional<KiteAccessToken> loadCurrent() {
            loads++;
            if (failLoad) throw new IllegalStateException(SENSITIVE);
            return Optional.ofNullable(token);
        }
        @Override public void save(KiteAccessToken token) {
            if (failSave) throw new IllegalStateException(SENSITIVE);
            saves++;
            events.add("persist");
            this.token = token;
        }
        @Override public void clear() { clears++; token = null; }
    }

    private static final class MutableClock extends Clock {
        Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
