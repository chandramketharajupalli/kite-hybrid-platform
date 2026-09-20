package com.kitehybrid.platform.broker.infrastructure.kite;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kitehybrid.platform.account.application.BrokerProfileProvider;
import com.kitehybrid.platform.account.domain.BrokerProfile;
import com.kitehybrid.platform.bootstrap.TradingCoreApplication;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.broker.application.auth.KiteAccessTokenStore;
import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationException;
import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationGateway;
import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationUseCase;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttempt;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptStore;
import jakarta.servlet.http.Cookie;
import com.kitehybrid.platform.instrument.application.InstrumentMasterProvider;
import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring wiring, controller, authentication use case and shared session; all external ports mocked. */
@SpringBootTest(classes = TradingCoreApplication.class, properties = {
        "kite.rest-enabled=true", "kite.api-key=syntheticApiKey", "kite.api-secret=syntheticApiSecret",
        "kite.access-token=", "kite.auth.redirect-url=http://localhost:8080/api/broker/kite/auth/callback",
        "kite.auth.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class KiteAuthenticationHttpTest {
    private static final String BASE = "/api/broker/kite/auth";
    private static final String NONCE_COOKIE = "KITE_LOGIN_NONCE";
    private static final String ORIGIN = "http://localhost:8080";
    private static final String ACCESS_TOKEN = "syntheticAccessCredentialNeverExpose";
    private static final String SECRET = "syntheticApiSecret";
    private static final String ENCRYPTION_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final Instant NOW = Instant.parse("2026-09-20T04:00:00Z");
    private static final AtomicInteger REQUEST_SEQUENCE = new AtomicInteger();
    @Autowired MockMvc http;
    @Autowired KiteAuthenticationUseCase authentication;
    @Autowired KiteSession session;
    @MockitoSpyBean Clock clock;
    @MockitoBean KiteAccessTokenStore store;
    @MockitoBean KiteLoginAttemptStore attempts;
    private final Map<String, KiteLoginAttempt> persistedAttempts = new ConcurrentHashMap<>();
    @MockitoBean KiteAuthenticationGateway gateway;
    @MockitoBean BrokerProfileProvider profiles;
    @MockitoBean InstrumentMasterProvider instruments;
    private final AtomicReference<String> state = new AtomicReference<>();
    private KiteAccessToken token;
    private String requestToken;

    @BeforeEach void isolateAuthenticationState() {
        doReturn(NOW).when(clock).instant();
        authentication.reset();
        clearInvocations(store, gateway, profiles, instruments, attempts);
        persistedAttempts.clear();
        state.set(null);
        doAnswer(invocation -> {
            KiteLoginAttempt attempt = invocation.getArgument(0);
            persistedAttempts.put(attempt.nonceDigest(), attempt);
            return null;
        }).when(attempts).create(any());
        when(attempts.consume(anyString(), any())).thenAnswer(invocation -> {
            String digest = invocation.getArgument(0);
            Instant now = invocation.getArgument(1);
            var consumed = new AtomicBoolean();
            persistedAttempts.computeIfPresent(digest, (key, attempt) -> {
                if (!now.isBefore(attempt.createdAt()) && now.isBefore(attempt.expiresAt())) {
                    consumed.set(true);
                    return null;
                }
                return attempt;
            });
            return consumed.get();
        });
        requestToken = "syntheticRequestCredential" + REQUEST_SEQUENCE.incrementAndGet();
        token = new KiteAccessToken(ACCESS_TOKEN, clock.instant().minusSeconds(1), clock.instant().plusSeconds(3600));
        when(store.loadCurrent()).thenReturn(Optional.empty());
        when(gateway.loginUrl(anyString())).thenAnswer(invocation -> {
            state.set(invocation.getArgument(0));
            return "https://kite.zerodha.com/connect/login?v=3&api_key=syntheticApiKey"
                    + "&redirect_params=state%3D" + state.get();
        });
        when(gateway.exchange(anyString())).thenReturn(token);
        when(profiles.currentProfile()).thenAnswer(invocation -> {
            session.profileValidated();
            return new BrokerProfile("ZERODHA", "TEST123", Set.of("NSE"));
        });
        when(instruments.retrieve()).thenReturn(List.of(Instrument.create(
                new BrokerInstrumentId("KITE", "123"), "TEST", "NSE", "CASH", InstrumentType.CASH,
                Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1)));
    }

    @Test void fullApplicationStartsWithoutTokenAndExposesRequiredStatus() throws Exception {
        http.perform(get(ORIGIN + BASE + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.broker").value("KITE"))
                .andExpect(jsonPath("$.tokenAvailable").value(false))
                .andExpect(jsonPath("$.code").value("KITE_AUTH_REQUIRED"))
                .andExpect(jsonPath("$.loginUrl").value(BASE + "/login"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        http.perform(get("/actuator/health")).andExpect(status().isOk());
        verifyNoInteractions(gateway, profiles, instruments);
    }

    @Test void loginCanonicalizesBeforeCreatingDurableAttemptOrBrowserCookie() throws Exception {
        var canonical = http.perform(get("http://127.0.0.1:8080" + BASE + "/login"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", ORIGIN + BASE + "/login"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        assertThat(canonical.getRequest().getSession(false)).isNull();
        verifyNoInteractions(gateway, attempts);

        Cookie browser = startLogin();
        assertThat(browser.getValue()).matches("[A-Za-z0-9_-]{43}");
        verify(gateway).loginUrl(state.get());
        assertThat(persistedAttempts).containsOnlyKeys(KiteAuthenticationAdapter.digest(state.get()));
    }

    @Test void localhostCanonicalizationAcceptsCallbackWithoutAnyServletSession() throws Exception {
        var canonical = http.perform(get("http://127.0.0.1:8080" + BASE + "/login"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", ORIGIN + BASE + "/login"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        assertThat(canonical.getRequest().getSession(false)).isNull();
        verifyNoInteractions(gateway, attempts);

        Cookie browser = startLogin();
        safe(http.perform(callback().cookie(browser).param("state", state.get())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
        verify(gateway).exchange(requestToken);
    }

    @Test void staleServletSessionCookieDoesNotAffectDurableAttempt() throws Exception {
        Cookie browser = startLogin();
        safe(http.perform(callback().cookie(browser, new Cookie("JSESSIONID", "syntheticStaleSessionId"))
                        .param("state", state.get())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
        verify(gateway).exchange(requestToken);
    }

    @Test void callbackWithoutRequestTokenReturnsBadRequestWithoutCallingKite() throws Exception {
        safe(http.perform(get(ORIGIN + BASE + "/callback")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KITE_INVALID_CALLBACK"));
        verify(gateway, never()).exchange(anyString());
        verify(store, never()).save(any());
        verify(attempts, never()).consume(anyString(), any());
    }

    @Test void rejectedAuthorizationReturnsUnauthorizedWithoutSpendingRequestToken() throws Exception {
        safe(http.perform(get(ORIGIN + BASE + "/callback")
                .param("request_token", requestToken).param("status", "error")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("KITE_LOGIN_REJECTED"));
        verify(gateway, never()).exchange(anyString());
        verify(attempts, never()).consume(anyString(), any());
    }

    @Test void rejectedActionDoesNotConsumeTheAttempt() throws Exception {
        Cookie browser = startLogin();
        safe(http.perform(get(ORIGIN + BASE + "/callback").cookie(browser)
                        .param("request_token", requestToken).param("state", state.get())
                        .param("status", "success").param("action", "cancel")))
                .andExpect(status().isUnauthorized());
        verify(gateway, never()).exchange(anyString());
        verify(attempts, never()).consume(anyString(), any());
    }

    @Test void correctStateWithoutBrowserNonceCannotAuthenticate() throws Exception {
        Cookie browser = startLogin();
        try (var logs = new CallbackStateLogs(browser)) {
            stateRejected(callback().param("state", state.get()));
            logs.assertFailure("BROWSER_NONCE_MISSING", true, true, false, false, false, false);
        }
        verify(attempts, never()).consume(anyString(), any());
    }

    @Test void servletSessionCookieAloneCannotAuthenticate() throws Exception {
        Cookie browser = startLogin();
        try (var logs = new CallbackStateLogs(browser)) {
            stateRejected(callback().cookie(new Cookie("JSESSIONID", "syntheticStaleSessionId"))
                    .param("state", state.get()));
            logs.assertFailure("BROWSER_NONCE_MISSING", true, true, false, false, false, false);
            assertThat(logs.onlyEvent().getFormattedMessage()).doesNotContain("syntheticStaleSessionId");
        }
    }

    @Test void matchingBrowserNonceWithoutCallbackStateCannotAuthenticate() throws Exception {
        Cookie browser = startLogin();
        try (var logs = new CallbackStateLogs(browser)) {
            stateRejected(callback().cookie(browser));
            logs.assertFailure("STATE_MISSING", false, false, true, true, false, false);
        }
        verify(attempts, never()).consume(anyString(), any());
    }

    @Test void mismatchedBrowserStateCannotAuthenticateAndIsNotLogged() throws Exception {
        Cookie browser = startLogin();
        String differentState = "S".repeat(43);
        try (var logs = new CallbackStateLogs(browser)) {
            stateRejected(callback().cookie(browser).param("state", differentState));
            logs.assertFailure("STATE_MISMATCH", true, true, true, true, false, false);
            assertThat(logs.onlyEvent().getFormattedMessage()).doesNotContain(differentState);
        }
        verify(attempts, never()).consume(anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "invalid state with spaces", "invalid%state"})
    void malformedCallbackStateCannotAuthenticate(String invalidState) throws Exception {
        Cookie browser = startLogin();
        try (var logs = new CallbackStateLogs(browser)) {
            stateRejected(callback().cookie(browser).param("state", invalidState));
            logs.assertFailure("STATE_INVALID", true, false, true, true, false, false);
        }
        verify(attempts, never()).consume(anyString(), any());
    }

    @Test void malformedBrowserNonceCannotAuthenticate() throws Exception {
        Cookie browser = startLogin();
        Cookie malformed = new Cookie(NONCE_COOKIE, "syntheticInvalidNonce");
        try (var logs = new CallbackStateLogs(browser)) {
            stateRejected(callback().cookie(malformed).param("state", state.get()));
            logs.assertFailure("BROWSER_NONCE_INVALID", true, true, true, false, false, false);
            assertThat(logs.onlyEvent().getFormattedMessage()).doesNotContain(malformed.getValue());
        }
        verify(attempts, never()).consume(anyString(), any());
    }

    @Test void duplicateCallbackStateIsRejectedBeforeConsumption() throws Exception {
        Cookie browser = startLogin();
        try (var logs = new CallbackStateLogs(browser)) {
            stateRejected(callback().cookie(browser).param("state", state.get(), state.get()));
            logs.assertFailure("STATE_INVALID", true, false, true, true, false, false);
        }
        verify(attempts, never()).consume(anyString(), any());
    }

    @Test void duplicateBrowserNonceCookiesAreRejectedBeforeConsumption() throws Exception {
        Cookie browser = startLogin();
        try (var logs = new CallbackStateLogs(browser)) {
            stateRejected(callback().cookie(browser, new Cookie(NONCE_COOKIE, browser.getValue()))
                    .param("state", state.get()));
            logs.assertFailure("BROWSER_NONCE_INVALID", true, true, true, false, false, false);
        }
        verify(attempts, never()).consume(anyString(), any());
    }

    @Test void matchingNonceWithoutPersistedAttemptCannotAuthenticate() throws Exception {
        Cookie browser = startLogin();
        persistedAttempts.clear();
        try (var logs = new CallbackStateLogs(browser)) {
            stateRejected(callback().cookie(browser).param("state", state.get()));
            logs.assertFailure("ATTEMPT_UNAVAILABLE", true, true, true, true, true, false);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {600, 601})
    void matchingAttemptExpiresAtExactlyTenMinutes(long elapsedSeconds) throws Exception {
        Cookie browser = startLogin();
        doReturn(NOW.plusSeconds(elapsedSeconds)).when(clock).instant();
        try (var logs = new CallbackStateLogs(browser)) {
            stateRejected(callback().cookie(browser).param("state", state.get()));
            logs.assertFailure("ATTEMPT_UNAVAILABLE", true, true, true, true, true, false);
        }
    }

    @Test void matchingAttemptJustBeforeTenMinutesIsAccepted() throws Exception {
        Cookie browser = startLogin();
        doReturn(NOW.plusSeconds(600).minusNanos(1)).when(clock).instant();
        try (var logs = new CallbackStateLogs(browser)) {
            safe(http.perform(callback().cookie(browser).param("state", state.get())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true));
            assertThat(logs.appender.list).isEmpty();
        }
        verify(gateway).exchange(requestToken);
    }

    @Test void callbackWithOnlyRequestTokenAndCorrectStateIsAccepted() throws Exception {
        Cookie browser = startLogin();
        try (var logs = new CallbackStateLogs(browser)) {
            safe(http.perform(get(ORIGIN + BASE + "/callback").cookie(browser)
                    .param("request_token", requestToken).param("state", state.get())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true));
            assertThat(logs.appender.list).isEmpty();
        }
        verify(gateway).exchange(requestToken);
    }

    @Test void successfulCallbackUpdatesActualSessionAndNoCredentialIsReturnedOrLogged(CapturedOutput output)
            throws Exception {
        Cookie browser = startLogin();

        safe(http.perform(callback().cookie(browser).param("state", state.get())))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(NONCE_COOKIE, 0))
                .andExpect(cookie().value(NONCE_COOKIE, ""))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.tokenAvailable").value(true))
                .andExpect(jsonPath("$.initializationReady").value(true));
        safe(http.perform(get(ORIGIN + BASE + "/status")))
                .andExpect(jsonPath("$.authenticated").value(true));

        assertThat(session.state()).isEqualTo(KiteSession.State.AUTHENTICATED);
        verify(gateway).exchange(requestToken);
        verify(profiles).currentProfile();
        verify(store).save(token);
        verify(instruments).retrieve();
        assertThat(output.getAll()).doesNotContain(ACCESS_TOKEN, requestToken, SECRET);
    }

    @Test void duplicateSuccessfulCallbackIsRejectedWithoutSpendingRequestTokenAgain() throws Exception {
        Cookie browser = startLogin();
        safe(http.perform(callback().cookie(browser).param("state", state.get())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true));
        try (var logs = new CallbackStateLogs(browser)) {
            safe(http.perform(callback().cookie(browser).param("state", state.get())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("KITE_CALLBACK_STATE_INVALID"));
            logs.assertFailure("ATTEMPT_UNAVAILABLE", true, true, true, true, true, false);
        }
        verify(gateway, times(1)).exchange(requestToken);
        verify(store, times(1)).save(token);
        verify(profiles, times(1)).currentProfile();
    }

    @Test void authenticatedLoginReturnsSafeStatusWithoutAnotherRedirect() throws Exception {
        Cookie browser = startLogin();
        http.perform(callback().cookie(browser).param("state", state.get())).andExpect(status().isOk());

        safe(http.perform(get(ORIGIN + BASE + "/login").cookie(browser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(header().doesNotExist("Location"));
        verify(gateway, times(1)).loginUrl(anyString());
    }

    @Test void exchangeFailureIsSanitizedAndIsNotPersistedOrRetried(CapturedOutput output) throws Exception {
        Cookie browser = startLogin();
        when(gateway.exchange(requestToken)).thenThrow(new IllegalStateException(SECRET + ACCESS_TOKEN + requestToken));

        safe(http.perform(callback().cookie(browser).param("state", state.get())))
                .andExpect(status().isBadGateway())
                .andExpect(cookie().maxAge(NONCE_COOKIE, 0))
                .andExpect(jsonPath("$.code").value("KITE_EXCHANGE_FAILED"));
        safe(http.perform(callback().cookie(browser).param("state", state.get())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("KITE_CALLBACK_STATE_INVALID"));

        verify(gateway, times(1)).exchange(requestToken);
        verify(store, never()).save(any());
        assertThat(session.authenticated()).isFalse();
        assertThat(output.getAll()).doesNotContain(SECRET, ACCESS_TOKEN, requestToken);
    }

    @Test void actualKiteErrorIsLoggedButPublicCallbackKeepsGenericFailure(CapturedOutput output) throws Exception {
        Cookie browser = startLogin();
        MockRestServiceServer broker = exchangeThroughActualAdapter("""
                {"status":"error","error_type":"TokenException","message":"Invalid checksum.",
                 "data":{"access_token":"syntheticAccessCredentialNeverExpose"}}
                """);

        safe(http.perform(callback().cookie(browser).param("state", state.get())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("KITE_EXCHANGE_FAILED"))
                .andExpect(response -> assertThat(response.getResponse().getContentAsString())
                        .doesNotContain("TokenException", "Invalid checksum.", "error_type", "HTTP status=400"));

        assertThat(output.getAll()).contains("Kite session exchange failed: HTTP status=400, "
                        + "error_type=TokenException, message=Invalid checksum.")
                .doesNotContain(SECRET, ACCESS_TOKEN, requestToken, ENCRYPTION_KEY);
        assertThat(session.authenticated()).isFalse();
        verify(store, never()).save(any());
        verifyNoInteractions(profiles, instruments);
        broker.verify();
    }

    @Test void echoedSecretsNeverReachLogsOrPublicCallbackFromActualKiteError(CapturedOutput output)
            throws Exception {
        Cookie browser = startLogin();
        String checksum = KiteAuthenticationAdapter.digest("syntheticApiKey" + requestToken + SECRET);
        String returnedToken = "syntheticReturnedCredentialNeverExpose";
        String body = JsonMapper.builder().build().writeValueAsString(Map.of(
                "status", "error", "error_type", "TokenException " + SECRET,
                "message", String.join(" ", requestToken, checksum, ENCRYPTION_KEY, ACCESS_TOKEN, returnedToken,
                        "Authorization: token syntheticApiKey:" + ACCESS_TOKEN),
                "data", Map.of("access_token", returnedToken)));
        MockRestServiceServer broker = exchangeThroughActualAdapter(body);

        safe(http.perform(callback().cookie(browser).param("state", state.get())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("KITE_EXCHANGE_FAILED"))
                .andExpect(response -> assertThat(response.getResponse().getContentAsString())
                        .doesNotContain(checksum, returnedToken, "TokenException", "error_type", "Authorization"));

        assertThat(output.getAll()).contains("Kite session exchange failed: HTTP status=400,")
                .doesNotContain(SECRET, requestToken, ACCESS_TOKEN, checksum, ENCRYPTION_KEY, returnedToken,
                        "Authorization: token");
        assertThat(session.authenticated()).isFalse();
        verify(store, never()).save(any());
        verifyNoInteractions(profiles, instruments);
        broker.verify();
    }

    @Test void resetRequiresHeaderThenClearsLocalTokenWithoutBrokerLogout() throws Exception {
        Cookie browser = startLogin();
        http.perform(callback().cookie(browser).param("state", state.get())).andExpect(status().isOk());

        safe(http.perform(post(ORIGIN + BASE + "/reset").cookie(browser)))
                .andExpect(status().isForbidden());
        assertThat(session.authenticated()).isTrue();

        safe(http.perform(post(ORIGIN + BASE + "/reset").cookie(browser)
                .header("X-Kite-Auth-Reset", "true")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.code").value("KITE_AUTH_REQUIRED"));
        assertThat(session.tokenAvailable()).isFalse();
        verify(store, times(1)).clear();
        verify(gateway, times(1)).exchange(requestToken);

        safe(http.perform(callback().cookie(browser).param("state", state.get())))
                .andExpect(status().isForbidden());
    }

    @Test void persistenceFailureReturnsSafeUnavailableResponseAndLeavesSessionClosed() throws Exception {
        Cookie browser = startLogin();
        doThrow(new IllegalStateException(SECRET)).when(store).save(token);

        safe(http.perform(callback().cookie(browser).param("state", state.get())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KITE_STORAGE_UNAVAILABLE"));
        assertThat(session.authenticated()).isFalse();
        verify(instruments, never()).retrieve();
    }

    @Test void resetCancelsAnOutstandingAttemptAndClearsTheBrowserCookie() throws Exception {
        Cookie browser = startLogin();
        safe(http.perform(post(ORIGIN + BASE + "/reset").cookie(browser)
                        .header("X-Kite-Auth-Reset", "true")))
                .andExpect(status().isOk()).andExpect(cookie().maxAge(NONCE_COOKIE, 0));
        stateRejected(callback().cookie(browser).param("state", state.get()));
    }

    @Test void attemptDatabaseFailureDoesNotSpendBrokerTokenOrExposeError(CapturedOutput output) throws Exception {
        Cookie browser = startLogin();
        when(attempts.consume(anyString(), any()))
                .thenThrow(new IllegalStateException(SECRET + state.get() + requestToken));
        safe(http.perform(callback().cookie(browser).param("state", state.get())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KITE_STORAGE_UNAVAILABLE"));
        verify(gateway, never()).exchange(anyString());
        verify(store, never()).save(any());
        assertThat(output.getAll()).doesNotContain(SECRET, state.get(), requestToken);
    }

    @Test void loginConfigurationFailureReturnsSafeUnavailableResponse() throws Exception {
        doThrow(new KiteAuthenticationException(KiteAuthenticationException.Code.CONFIGURATION))
                .when(gateway).loginUrl(anyString());

        safe(http.perform(get(ORIGIN + BASE + "/login")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KITE_CONFIGURATION"));
    }

    private Cookie startLogin() throws Exception {
        var response = http.perform(get(ORIGIN + BASE + "/login"))
                .andExpect(status().isFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertThat(response.getResponse().getRedirectedUrl())
                .startsWith("https://kite.zerodha.com/connect/login?v=3&api_key=")
                .doesNotContain(ACCESS_TOKEN, SECRET, requestToken);
        assertThat(response.getRequest().getSession(false)).isNull();
        Cookie cookie = response.getResponse().getCookie(NONCE_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo(state.get());
        assertThat(cookie.getPath()).isEqualTo(BASE);
        assertThat(cookie.getMaxAge()).isEqualTo(600);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.getDomain()).isNull();
        return cookie;
    }

    private MockRestServiceServer exchangeThroughActualAdapter(String responseBody) {
        var builder = RestClient.builder().baseUrl("https://api.kite.trade");
        var broker = MockRestServiceServer.bindTo(builder).build();
        var adapter = new KiteAuthenticationAdapter(
                new KiteProperties("syntheticApiKey", SECRET, ACCESS_TOKEN, true),
                new KiteAuthenticationProperties(ORIGIN + BASE + "/callback", ENCRYPTION_KEY),
                builder.build(), clock);
        broker.expect(requestTo("https://api.kite.trade/session/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));
        doAnswer(invocation -> adapter.exchange(invocation.getArgument(0))).when(gateway).exchange(requestToken);
        return broker;
    }

    private MockHttpServletRequestBuilder callback() {
        return get(ORIGIN + BASE + "/callback").param("request_token", requestToken)
                .param("status", "success").param("action", "login").param("type", "login");
    }

    private void stateRejected(MockHttpServletRequestBuilder request) throws Exception {
        safe(http.perform(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.broker").value("KITE"))
                .andExpect(jsonPath("$.code").value("KITE_CALLBACK_STATE_INVALID"))
                .andExpect(jsonPath("$.length()").value(2));
        verify(gateway, never()).exchange(anyString());
        verify(store, never()).save(any());
        verifyNoInteractions(profiles, instruments);
    }

    private ResultActions safe(ResultActions result) throws Exception {
        return result.andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(response -> {
                    String body = response.getResponse().getContentAsString();
                    assertThat(body).doesNotContain("syntheticApiKey", ACCESS_TOKEN, requestToken, SECRET,
                            ENCRYPTION_KEY, KiteAuthenticationAdapter.digest("syntheticApiKey" + requestToken + SECRET),
                            "apiSecret", "accessToken", "requestToken");
                    if (state.get() != null) assertThat(body).doesNotContain(state.get());
                    var browser = response.getRequest().getSession(false);
                    if (browser != null) assertThat(body).doesNotContain(browser.getId());
                })
                .andExpect(response -> response.getResponse().getHeaderNames().forEach(name ->
                        response.getResponse().getHeaders(name).forEach(value -> assertThat(value)
                                .doesNotContain(ACCESS_TOKEN, requestToken, SECRET, ENCRYPTION_KEY))));
    }

    private final class CallbackStateLogs implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(KiteAuthenticationController.class);
        private final Level previousLevel = logger.getLevel();
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        private final String browserNonce;

        private CallbackStateLogs(Cookie browser) {
            browserNonce = browser.getValue();
            appender.start();
            logger.setLevel(Level.WARN);
            logger.addAppender(appender);
        }

        private void assertFailure(String reason, boolean statePresent, boolean stateValid,
                                   boolean browserNoncePresent, boolean browserNonceValid,
                                   boolean stateMatched, boolean attemptConsumed) {
            ILoggingEvent event = onlyEvent();
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getThrowableProxy()).isNull();
            Object[] arguments = event.getArgumentArray();
            assertThat(arguments).hasSize(7);
            assertThat(arguments[0]).isInstanceOf(Enum.class);
            assertThat(arguments[0].toString()).isEqualTo(reason);
            assertThat(Arrays.copyOfRange(arguments, 1, arguments.length)).containsExactly(statePresent,
                    stateValid, browserNoncePresent, browserNonceValid, stateMatched, attemptConsumed);
            assertThat(event.getFormattedMessage()).isEqualTo("Kite callback state validation failed: reason="
                    + reason + ", statePresent=" + statePresent + ", stateValid=" + stateValid
                    + ", browserNoncePresent=" + browserNoncePresent + ", browserNonceValid=" + browserNonceValid
                    + ", stateMatched=" + stateMatched + ", attemptConsumed=" + attemptConsumed);
            assertThat(event.getFormattedMessage()).doesNotContain("syntheticApiKey", SECRET, ACCESS_TOKEN,
                    requestToken, ENCRYPTION_KEY, browserNonce,
                    KiteAuthenticationAdapter.digest("syntheticApiKey" + requestToken + SECRET));
            if (state.get() != null) assertThat(event.getFormattedMessage()).doesNotContain(state.get());
        }

        private ILoggingEvent onlyEvent() {
            assertThat(appender.list).hasSize(1);
            return appender.list.getFirst();
        }

        @Override public void close() {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
