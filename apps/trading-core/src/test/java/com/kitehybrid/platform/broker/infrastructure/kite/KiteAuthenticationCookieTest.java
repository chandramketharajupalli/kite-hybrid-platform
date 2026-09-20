package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kitehybrid.platform.account.application.BrokerProfileProvider;
import com.kitehybrid.platform.account.domain.BrokerProfile;
import com.kitehybrid.platform.bootstrap.TradingCoreApplication;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.broker.application.auth.KiteAccessTokenStore;
import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationGateway;
import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationUseCase;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttempt;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptStore;
import com.kitehybrid.platform.instrument.application.InstrumentMasterProvider;
import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests real Tomcat nonce cookies without creating a servlet session or contacting Zerodha. */
@SpringBootTest(classes = TradingCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "kite.rest-enabled=true",
                "kite.api-key=syntheticCookieApiKey", "kite.api-secret=syntheticCookieApiSecret", "kite.access-token="})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class KiteAuthenticationCookieTest {
    private static final String BASE = "/api/broker/kite/auth";
    private static final String NONCE_COOKIE = "KITE_LOGIN_NONCE";
    private static final String ACCESS_TOKEN = "syntheticCookieAccessCredential";
    private static final AtomicInteger REQUEST_SEQUENCE = new AtomicInteger();
    @LocalServerPort int port;
    @Autowired KiteAuthenticationUseCase authentication;
    @Autowired KiteSession session;
    @Autowired Clock clock;
    @MockitoBean KiteAuthenticationProperties properties;
    @MockitoBean KiteAccessTokenStore store;
    @MockitoBean KiteLoginAttemptStore attempts;
    private final Map<String, KiteLoginAttempt> persistedAttempts = new ConcurrentHashMap<>();
    @MockitoBean KiteAuthenticationGateway gateway;
    @MockitoBean BrokerProfileProvider profiles;
    @MockitoBean InstrumentMasterProvider instruments;
    private final AtomicReference<String> state = new AtomicReference<>();
    private String requestToken;
    private KiteAccessToken token;

    @BeforeEach void prepareAuthentication() {
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
        requestToken = "syntheticCookieRequestCredential" + REQUEST_SEQUENCE.incrementAndGet();
        token = new KiteAccessToken(ACCESS_TOKEN, clock.instant().minusSeconds(1), clock.instant().plusSeconds(3600));
        when(properties.redirectUri()).thenReturn(localhost(BASE + "/callback"));
        when(store.loadCurrent()).thenReturn(Optional.empty());
        when(gateway.loginUrl(anyString())).thenAnswer(invocation -> {
            state.set(invocation.getArgument(0));
            return "https://kite.zerodha.com/connect/login?v=3&api_key=syntheticCookieApiKey"
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

    @Test void actualLoginCookieUsesHttpOnlyHostOnlyLaxPolicyForLocalHttp() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (HttpClient browser = client(cookies)) {
            var login = browser.send(HttpRequest.newBuilder(localhost(BASE + "/login")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(login.statusCode()).isEqualTo(302);
            assertCookiePolicy(login);
            assertThat(cookies.getCookieStore().get(localhost(BASE + "/callback")))
                    .extracting(HttpCookie::getName).containsExactly(NONCE_COOKIE);
        }
    }

    @Test void canonicalLocalhostNonceSurvivesTopLevelCallbackGetWithoutServletSession(CapturedOutput output) throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (HttpClient outgoingBrowser = client(cookies)) {
            var canonical = outgoingBrowser.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + BASE + "/login")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(canonical.statusCode()).isEqualTo(302);
            assertThat(canonical.headers().firstValue("Location"))
                    .contains(localhost(BASE + "/login").toString());
            assertThat(canonical.headers().allValues("Set-Cookie")).isEmpty();
            assertThat(cookies.getCookieStore().getCookies()).isEmpty();
            verifyNoInteractions(gateway);

            var login = outgoingBrowser.send(HttpRequest.newBuilder(
                            URI.create(canonical.headers().firstValue("Location").orElseThrow())).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).isEqualTo(302);
            assertCookiePolicy(login);
            assertThat(login.headers().firstValue("Location")).get()
                    .asString().startsWith("https://kite.zerodha.com/connect/login?");
        }

        String browserNonce = cookies.getCookieStore().getCookies().getFirst().getValue();
        assertThat(browserNonce).isEqualTo(state.get());
        // A separate HTTP client models a return navigation using the browser's retained cookie jar.
        // Java HttpClient does not enforce browser SameSite policy; the actual Lax header is checked above.
        try (HttpClient returningBrowser = client(cookies)) {
            var callback = returningBrowser.send(HttpRequest.newBuilder(localhost(BASE + "/callback"
                            + "?request_token=" + requestToken + "&status=success&action=login&type=login"
                            + "&state=" + state.get()))
                    .header("Sec-Fetch-Site", "cross-site")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Dest", "document")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());

            assertThat(callback.statusCode()).isEqualTo(200);
            var status = JsonMapper.builder().build().readTree(callback.body());
            assertThat(status.path("authenticated").asBoolean()).isTrue();
            assertThat(status.path("initializationReady").asBoolean()).isTrue();
            assertThat(session.authenticated()).isTrue();
            verify(gateway).exchange(requestToken);
            verify(store).save(token);
            assertThat(callback.headers().allValues("Set-Cookie")).hasSize(1);
            assertThat(callback.headers().firstValue("Set-Cookie").orElseThrow())
                    .startsWith(NONCE_COOKIE + "=;").contains("Max-Age=0");
            assertThat(cookies.getCookieStore().getCookies()).isEmpty();
            assertThat(callback.body()).doesNotContain(ACCESS_TOKEN, requestToken, state.get(), browserNonce,
                    "syntheticCookieApiKey", "syntheticCookieApiSecret");
            assertThat(output.getAll()).doesNotContain(ACCESS_TOKEN, requestToken, state.get(), browserNonce,
                    "syntheticCookieApiKey", "syntheticCookieApiSecret");
        }
    }

    @ParameterizedTest(name = "stale servlet session cookie supplied: {0}")
    @ValueSource(booleans = {false, true})
    void servletSessionCookieDoesNotDetermineWhetherCorrectNonceIsAccepted(boolean staleSessionCookie,
                                                                           CapturedOutput output) throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (HttpClient browser = client(cookies)) {
            var login = browser.send(HttpRequest.newBuilder(localhost(BASE + "/login")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).isEqualTo(302);
            var callbackRequest = HttpRequest.newBuilder(callbackUri(true)).GET();
            if (staleSessionCookie) callbackRequest.header("Cookie", "JSESSIONID=syntheticStaleSessionId");
            var callback = browser.send(callbackRequest.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(callback.statusCode()).isEqualTo(200);
            verify(gateway).exchange(requestToken);
            assertSafe(callback, output);
            assertThat(output.getAll()).doesNotContain("syntheticStaleSessionId");
        }
    }

    @ParameterizedTest(name = "stale servlet session cookie supplied without nonce: {0}")
    @ValueSource(booleans = {false, true})
    void absentNonceCookieRejectsEvenWhenServletSessionCookieIsPresent(boolean staleSessionCookie,
                                                                     CapturedOutput output) throws Exception {
        try (HttpClient browser = client(new CookieManager(null, CookiePolicy.ACCEPT_ALL))) {
            assertThat(browser.send(HttpRequest.newBuilder(localhost(BASE + "/login")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(302);
        }
        try (HttpClient callbackBrowser = client(new CookieManager(null, CookiePolicy.ACCEPT_ALL))) {
            var callbackRequest = HttpRequest.newBuilder(callbackUri(true)).GET();
            if (staleSessionCookie) callbackRequest.header("Cookie", "JSESSIONID=syntheticStaleSessionId");
            var callback = callbackBrowser.send(callbackRequest.build(), HttpResponse.BodyHandlers.ofString());
            assertStateRejected(callback);
            assertSafe(callback, output);
            assertThat(output.getAll()).contains("Kite callback state validation failed: reason=BROWSER_NONCE_MISSING, "
                            + "statePresent=true, stateValid=true, browserNoncePresent=false, browserNonceValid=false, "
                            + "stateMatched=false, attemptConsumed=false")
                    .doesNotContain("syntheticStaleSessionId");
            verify(attempts, never()).consume(anyString(), any());
        }
    }

    @Test void missingBrokerStateRejectsEvenWithCorrectDedicatedCookie(CapturedOutput output) throws Exception {
        try (HttpClient browser = client(new CookieManager(null, CookiePolicy.ACCEPT_ALL))) {
            assertThat(browser.send(HttpRequest.newBuilder(localhost(BASE + "/login")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(302);
            var callback = browser.send(HttpRequest.newBuilder(callbackUri(false)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertStateRejected(callback);
            assertSafe(callback, output);
            assertThat(output.getAll()).contains("Kite callback state validation failed: reason=STATE_MISSING, "
                    + "statePresent=false, stateValid=false, browserNoncePresent=true, browserNonceValid=true, "
                    + "stateMatched=false, attemptConsumed=false");
            verify(attempts, never()).consume(anyString(), any());
        }
    }

    @Test void oneTimeAttemptRejectsReplayedNonceEvenIfCookieIsManuallyRetained(CapturedOutput output) throws Exception {
        try (HttpClient browser = client(new CookieManager(null, CookiePolicy.ACCEPT_ALL))) {
            assertThat(browser.send(HttpRequest.newBuilder(localhost(BASE + "/login")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(302);
            var callback = browser.send(HttpRequest.newBuilder(callbackUri(true)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(callback.statusCode()).isEqualTo(200);
        }
        try (HttpClient replayBrowser = client(new CookieManager(null, CookiePolicy.ACCEPT_ALL))) {
            var replay = replayBrowser.send(HttpRequest.newBuilder(callbackUri(true))
                    .header("Cookie", NONCE_COOKIE + "=" + state.get()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(replay.statusCode()).isEqualTo(403);
            assertThat(JsonMapper.builder().build().readTree(replay.body()).path("code").asText())
                    .isEqualTo("KITE_CALLBACK_STATE_INVALID");
            assertSafe(replay, output);
            assertThat(output.getAll()).contains("reason=ATTEMPT_UNAVAILABLE");
            verify(gateway).exchange(requestToken);
        }
    }

    private URI callbackUri(boolean includeState) {
        return localhost(BASE + "/callback?request_token=" + requestToken
                + "&status=success&action=login&type=login" + (includeState ? "&state=" + state.get() : ""));
    }

    private void assertStateRejected(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(403);
        var body = JsonMapper.builder().build().readTree(response.body());
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.path("broker").asText()).isEqualTo("KITE");
        assertThat(body.path("code").asText()).isEqualTo("KITE_CALLBACK_STATE_INVALID");
        verify(gateway, never()).exchange(anyString());
        verify(store, never()).save(any());
    }

    private void assertSafe(HttpResponse<String> response, CapturedOutput output) {
        assertThat(response.body()).doesNotContain(ACCESS_TOKEN, requestToken, state.get(),
                "syntheticCookieApiKey", "syntheticCookieApiSecret");
        assertThat(output.getAll()).doesNotContain(ACCESS_TOKEN, requestToken, state.get(),
                "syntheticCookieApiKey", "syntheticCookieApiSecret");
    }

    private static void assertCookiePolicy(HttpResponse<?> response) {
        var headers = response.headers().allValues("Set-Cookie");
        assertThat(headers).hasSize(1);
        String cookie = headers.getFirst();
        assertThat(cookie).startsWith(NONCE_COOKIE + "=");
        assertThat(cookie.toLowerCase(Locale.ROOT)).contains("; path=" + BASE, "; max-age=600", "; httponly", "; samesite=lax")
                .doesNotContain("; secure", "; domain=");
    }

    private static HttpClient client(CookieManager cookies) {
        return HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    private URI localhost(String path) { return URI.create("http://localhost:" + port + path); }
}
