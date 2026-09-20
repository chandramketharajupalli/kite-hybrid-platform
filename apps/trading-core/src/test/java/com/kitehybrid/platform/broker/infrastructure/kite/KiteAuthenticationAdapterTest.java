package com.kitehybrid.platform.broker.infrastructure.kite;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import static com.kitehybrid.platform.broker.application.auth.KiteAuthenticationException.Code.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Official API requests are intercepted in-process; never contacts Zerodha. */
class KiteAuthenticationAdapterTest {
    private static final String API_KEY = "syntheticApiKey";
    private static final String API_SECRET = "syntheticApiSecret";
    private static final String REQUEST_TOKEN = "syntheticRequestToken";
    private static final String ACCESS_TOKEN = "syntheticAccessToken";
    private static final String RETURNED_ACCESS_TOKEN = "syntheticUnknownReturnedAccessToken";
    private static final String CHECKSUM = "5ccaa0645f53bb5030dba49542d746cb989532096e2fff8c68623b324f1af048";
    private static final String UPSTREAM_SECRET = "syntheticSensitiveUpstreamBody";
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String REDIRECT_URL = "http://localhost:8080/api/broker/kite/auth/callback";
    private static final String STATE = "syntheticBrowserState12345678901234567890";
    private static final Instant NOW = Instant.parse("2026-09-20T04:00:00Z");
    private static final String SUCCESS = """
            {"status":"success","data":{"api_key":"syntheticApiKey",
            "access_token":"syntheticAccessToken","public_token":"ignoredPublicToken",
            "user_id":"ignoredUserId"}}
            """;

    @Test
    void loginUsesOfficialBrowserUrlAndPassesStateThroughEncodedRedirectParameters() {
        Fixture fixture = fixture();

        String url = fixture.adapter().loginUrl(STATE);

        assertThat(url).isEqualTo("https://kite.zerodha.com/connect/login?v=3&api_key=" + API_KEY
                + "&redirect_params=state%3D" + STATE);
        assertThat(url).doesNotContain(API_SECRET, REQUEST_TOKEN, ACCESS_TOKEN, ENCRYPTION_KEY);
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 251, 255})
    void redirectParametersContainOnlyOneUrlSafeStateAndRequireExactlyOneDecode(int byteValue) {
        Fixture fixture = fixture();
        byte[] stateBytes = new byte[32];
        Arrays.fill(stateBytes, (byte) byteValue);
        String generatedState = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);

        URI loginUri = URI.create(fixture.adapter().loginUrl(generatedState));

        assertThat(generatedState).matches("[A-Za-z0-9_-]{43}").doesNotContain("+", "/", "=");
        if (byteValue == 251) assertThat(generatedState).contains("-", "_");
        assertThat(loginUri.getScheme()).isEqualTo("https");
        assertThat(loginUri.getHost()).isEqualTo("kite.zerodha.com");
        assertThat(loginUri.getPath()).isEqualTo("/connect/login");
        var redirectParameters = Arrays.stream(loginUri.getRawQuery().split("&"))
                .filter(parameter -> URLDecoder.decode(parameter.split("=", 2)[0], StandardCharsets.UTF_8)
                        .equals("redirect_params"))
                .toList();
        assertThat(redirectParameters).hasSize(1);
        String encoded = redirectParameters.getFirst().split("=", 2)[1];
        assertThat(encoded).startsWith("state%3D").doesNotContain("%253D");
        String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("state=" + generatedState);
        assertThat(decoded.split("&", -1)).containsExactly("state=" + generatedState);
        assertThat(decoded.split("=", 2)).containsExactly("state", generatedState);
        assertThat(decoded).doesNotContain(API_KEY, API_SECRET, REQUEST_TOKEN, ACCESS_TOKEN, CHECKSUM,
                ENCRYPTION_KEY, "api_key", "api_secret", "request_token", "access_token", "checksum",
                "encryption_key");
        fixture.server().verify();
    }

    @Test
    void successfulExchangePostsOfficialFormAndReturnsCredentialWithAuthenticationDayExpiry() {
        Fixture fixture = fixture();
        expectExchange(fixture).andRespond(withSuccess(SUCCESS, MediaType.APPLICATION_JSON));

        try (var logs = new ExchangeLogs()) {
            KiteAccessToken token = fixture.adapter().exchange(REQUEST_TOKEN);

            assertThat(token.value()).isEqualTo(ACCESS_TOKEN);
            assertThat(token.issuedAt()).isEqualTo(NOW);
            assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-09-21T00:30:00Z"));
            assertThat(token.toString()).doesNotContain(ACCESS_TOKEN, REQUEST_TOKEN, API_SECRET);
            assertThat(logs.appender.list).isEmpty();
        }
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 401, 403, 429, 500, 503})
    void rejectedExchangeNeverRetainsUpstreamBodyAndIsNeverRetried(int status) {
        Fixture fixture = fixture();
        expectExchange(fixture).andRespond(withStatus(HttpStatus.valueOf(status))
                .header("Location", "https://untrusted.invalid/" + UPSTREAM_SECRET)
                .body(UPSTREAM_SECRET + API_SECRET + REQUEST_TOKEN + ACCESS_TOKEN));

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().exchange(REQUEST_TOKEN)), EXCHANGE_FAILED);

        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 401, 403, 429, 500, 503})
    void rejectedExchangeLogsOnlyStatusAndDocumentedErrorFields(int status) {
        Fixture fixture = fixture();
        expectExchange(fixture).andRespond(withStatus(HttpStatus.valueOf(status))
                .header("Authorization", "token " + API_KEY + ":" + ACCESS_TOKEN)
                .header("Location", "https://untrusted.invalid/" + REQUEST_TOKEN)
                .body("""
                        {"status":"error","error_type":"TokenException","message":"Invalid checksum.",
                        "data":{"access_token":"syntheticUnknownReturnedAccessToken"},
                        "api_secret":"syntheticApiSecret","request_token":"syntheticRequestToken"}
                        """));

        try (var logs = new ExchangeLogs()) {
            assertSafe(assertThrows(KiteAuthenticationException.class,
                    () -> fixture.adapter().exchange(REQUEST_TOKEN)), EXCHANGE_FAILED);

            assertThat(logs.onlyMessage()).isEqualTo("Kite session exchange failed: HTTP status=" + status
                    + ", error_type=TokenException, message=Invalid checksum.");
        }
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "not-json syntheticApiSecret", "{", "null", "[]", "{}",
            "{\"status\":\"success\",\"error_type\":\"TokenException\",\"message\":\"syntheticApiSecret\"}",
            "{\"status\":\"error\",\"message\":\"syntheticApiSecret\"}",
            "{\"status\":\"error\",\"error_type\":\"TokenException\"}",
            "{\"status\":\"error\",\"error_type\":null,\"message\":\"syntheticApiSecret\"}",
            "{\"status\":\"error\",\"error_type\":\"TokenException\",\"message\":123}",
            "{\"status\":\"error\",\"error_type\":{},\"message\":\"syntheticApiSecret\"}",
            "{\"status\":\"error\",\"error_type\":\"TokenException\",\"message\":\"safe\",\"message\":\"syntheticApiSecret\"}",
            "{\"status\":\"error\",\"error_type\":\"TokenException\",\"message\":\"safe\"} {}"
    })
    void unparseableErrorBodyProducesOnlyStatusAndSafeFallback(String body) {
        Fixture fixture = fixture();
        expectExchange(fixture).andRespond(withStatus(HttpStatus.BAD_REQUEST).body(body));

        try (var logs = new ExchangeLogs()) {
            assertSafe(assertThrows(KiteAuthenticationException.class,
                    () -> fixture.adapter().exchange(REQUEST_TOKEN)), EXCHANGE_FAILED);

            assertThat(logs.onlyMessage()).isEqualTo(
                    "Kite session exchange failed: HTTP status=400, unparseable Kite error response");
        }
        fixture.server().verify();
    }

    @Test
    void oversizedErrorResponseIsBoundedAndProducesOnlySafeFallback() {
        Fixture fixture = fixture();
        var readBytes = new java.util.concurrent.atomic.AtomicInteger();
        expectExchange(fixture).andRespond(request -> new MockClientHttpResponse(new InputStream() {
            @Override public int read() {
                if (readBytes.incrementAndGet() > 64 * 1024 + 1)
                    throw new AssertionError("Error response exceeded the bounded read");
                return ' ';
            }
        }, HttpStatus.BAD_GATEWAY));

        try (var logs = new ExchangeLogs()) {
            assertSafe(assertThrows(KiteAuthenticationException.class,
                    () -> fixture.adapter().exchange(REQUEST_TOKEN)), EXCHANGE_FAILED);

            assertThat(logs.onlyMessage()).isEqualTo(
                    "Kite session exchange failed: HTTP status=502, unparseable Kite error response");
            assertThat(readBytes.get()).isEqualTo(64 * 1024 + 1);
        }
        fixture.server().verify();
    }

    @Test
    void unreadableErrorBodyLogsStatusWithoutTheIOExceptionOrItsSensitiveMessage() {
        Fixture fixture = fixture();
        expectExchange(fixture).andRespond(request -> new MockClientHttpResponse(new InputStream() {
            @Override public int read() throws IOException {
                throw new IOException(API_SECRET + REQUEST_TOKEN + ACCESS_TOKEN + CHECKSUM);
            }
        }, HttpStatus.BAD_GATEWAY));

        try (var logs = new ExchangeLogs()) {
            assertSafe(assertThrows(KiteAuthenticationException.class,
                    () -> fixture.adapter().exchange(REQUEST_TOKEN)), EXCHANGE_FAILED);

            assertThat(logs.onlyMessage()).isEqualTo(
                    "Kite session exchange failed: HTTP status=502, unparseable Kite error response");
        }
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"message", "error_type"})
    void sensitiveValuesEchoedInEitherDocumentedFieldAreNeverLogged(String field) throws Exception {
        for (String credential : new String[] {API_SECRET, REQUEST_TOKEN, CHECKSUM, ENCRYPTION_KEY,
                ACCESS_TOKEN, RETURNED_ACCESS_TOKEN, "Authorization: token " + API_KEY + ":" + ACCESS_TOKEN,
                "Authorization: Bearer unknownHeaderCredential", "Authorization Basic dGVzdA==",
                "access_token is shortValue", "request_token=shortValue", "api_secret: shortValue",
                java.net.URLEncoder.encode(ENCRYPTION_KEY, java.nio.charset.StandardCharsets.UTF_8)}) {
            Fixture fixture = fixture(new KiteProperties(API_KEY, API_SECRET, ACCESS_TOKEN, true), authProperties());
            String echoed = "Rejected value: " + credential;
            String body = JsonMapper.builder().build().writeValueAsString(Map.of(
                    "status", "error", "error_type", field.equals("error_type") ? echoed : "TokenException",
                    "message", field.equals("message") ? echoed : "Invalid checksum.",
                    "data", Map.of("access_token", RETURNED_ACCESS_TOKEN)));
            expectExchange(fixture).andRespond(withStatus(HttpStatus.FORBIDDEN).body(body));

            try (var logs = new ExchangeLogs()) {
                assertSafe(assertThrows(KiteAuthenticationException.class,
                        () -> fixture.adapter().exchange(REQUEST_TOKEN)), EXCHANGE_FAILED);

                assertThat(logs.onlyMessage()).startsWith("Kite session exchange failed: HTTP status=403,")
                        .contains("[REDACTED]").doesNotContain(credential, "Authorization: token");
            }
            fixture.server().verify();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"message", "error_type"})
    void documentedErrorFieldsCannotInjectAdditionalLogLinesOrTerminalControls(String field) throws Exception {
        Fixture fixture = fixture();
        String injected = "Invalid\r\nforged entry\t\u001b[31m\u2028second line\u2029third line";
        String body = JsonMapper.builder().build().writeValueAsString(Map.of(
                "status", "error", "error_type", field.equals("error_type") ? injected : "TokenException",
                "message", field.equals("message") ? injected : "Invalid checksum."));
        expectExchange(fixture).andRespond(withStatus(HttpStatus.FORBIDDEN).body(body));

        try (var logs = new ExchangeLogs()) {
            assertSafe(assertThrows(KiteAuthenticationException.class,
                    () -> fixture.adapter().exchange(REQUEST_TOKEN)), EXCHANGE_FAILED);

            assertThat(logs.onlyMessage()).isEqualTo(
                    "Kite session exchange failed: HTTP status=403, unparseable Kite error response");
        }
        fixture.server().verify();
    }

    @Test
    void ioFailureIsSafeAndSingleUseRequestTokenIsNotRetried() {
        Fixture fixture = fixture();
        expectExchange(fixture).andRespond(request -> {
            throw new IOException(UPSTREAM_SECRET + API_SECRET + REQUEST_TOKEN);
        });

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().exchange(REQUEST_TOKEN)), EXCHANGE_FAILED);

        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "not-json", "{", "null", "[]", "{}",
            "{\"status\":\"error\",\"message\":\"syntheticSensitiveUpstreamBody\"}",
            "{\"status\":\"success\",\"data\":null}",
            "{\"status\":\"success\",\"data\":{\"api_key\":\"syntheticApiKey\"}}",
            "{\"status\":\"success\",\"data\":{\"api_key\":\"syntheticApiKey\",\"access_token\":null}}",
            "{\"status\":\"success\",\"data\":{\"api_key\":\"syntheticApiKey\",\"access_token\":123}}",
            "{\"status\":\"success\",\"data\":{\"api_key\":\"syntheticApiKey\",\"access_token\":\"\"}}",
            "{\"status\":\"success\",\"data\":{\"api_key\":\"syntheticApiKey\",\"access_token\":\"bad token\"}}",
            "{\"status\":\"success\",\"data\":{\"api_key\":\"wrongApiKey\",\"access_token\":\"syntheticAccessToken\"}}",
            "{\"status\":\"success\",\"data\":{\"access_token\":\"syntheticAccessToken\"}}",
            "{\"status\":\"success\",\"data\":{\"api_key\":\"syntheticApiKey\",\"access_token\":\"first\",\"access_token\":\"second\"}}"
    })
    void malformedOrMissingCredentialIsRejectedWithoutRetainingResponse(String response) {
        Fixture fixture = fixture();
        expectExchange(fixture).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().exchange(REQUEST_TOKEN)), EXCHANGE_FAILED);

        fixture.server().verify();
    }

    @Test
    void trailingJsonCannotProduceSuccessfulExchange() {
        Fixture fixture = fixture();
        expectExchange(fixture).andRespond(withSuccess(SUCCESS + " {}", MediaType.APPLICATION_JSON));

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().exchange(REQUEST_TOKEN)), EXCHANGE_FAILED);

        fixture.server().verify();
    }

    @Test
    void oversizedResponseIsRejectedWithoutRetainingPayload() {
        Fixture fixture = fixture();
        expectExchange(fixture).andRespond(withSuccess(" ".repeat(64 * 1024) + SUCCESS,
                MediaType.APPLICATION_JSON));

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().exchange(REQUEST_TOKEN)), EXCHANGE_FAILED);

        fixture.server().verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bad token", "syntheticRequestToken\r\nsecret", "bad&injection=true"})
    void invalidRequestTokenIsRejectedBeforeHttp(String token) {
        Fixture fixture = fixture();

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().exchange(token)), INVALID_CALLBACK);

        fixture.server().verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short-state", "syntheticStateWith&Injected=Query1234567890"})
    void invalidBrowserStateIsRejectedBeforeProducingLoginUrl(String state) {
        Fixture fixture = fixture();

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().loginUrl(state)), INVALID_CALLBACK);

        fixture.server().verify();
    }

    @ParameterizedTest
    @CsvSource({
            "2026-09-19T18:30:00Z,2026-09-20T00:30:00Z",
            "2026-09-20T00:29:59Z,2026-09-20T00:30:00Z",
            "2026-09-20T00:30:00Z,2026-09-21T00:30:00Z",
            "2026-09-20T15:30:00Z,2026-09-21T00:30:00Z"
    })
    void expiryUsesNextSixAmInIndiaInsteadOfCalendarMidnight(String issuedAt, String expiresAt) {
        assertThat(KiteAuthenticationAdapter.expiresAt(Instant.parse(issuedAt))).isEqualTo(Instant.parse(expiresAt));
    }

    @Test
    void absentApiCredentialsAreValidatedOnlyWhenUsed() {
        var properties = new KiteProperties(null, null, null, true);
        Fixture fixture = fixture(properties, authProperties());

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().loginUrl(STATE)), CONFIGURATION);
        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().exchange(REQUEST_TOKEN)), CONFIGURATION);
        fixture.server().verify();
    }

    @Test
    void invalidApiSecretDoesNotEnterFailureOrDiagnostics() {
        var properties = new KiteProperties(API_KEY, API_SECRET + "\r\n" + UPSTREAM_SECRET, "", true);
        Fixture fixture = fixture(properties, authProperties());

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().loginUrl(STATE)), CONFIGURATION);
        assertThat(properties.toString()).doesNotContain(API_KEY, API_SECRET, UPSTREAM_SECRET);
        fixture.server().verify();
    }

    @Test
    void disabledRestPreventsLoginAndExchange() {
        Fixture fixture = fixture(new KiteProperties(API_KEY, API_SECRET, "", false), authProperties());

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().loginUrl(STATE)), DISABLED);
        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().exchange(REQUEST_TOKEN)), DISABLED);
        fixture.server().verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"syntheticSensitiveUpstreamBody", "AA=="})
    void missingOrInvalidEncryptionKeyRejectsBeforeLoginWithoutLeakingConfiguration(String key) {
        var auth = new KiteAuthenticationProperties(REDIRECT_URL, key);
        Fixture fixture = fixture(properties(), auth);

        assertSafe(assertThrows(KiteAuthenticationException.class,
                () -> fixture.adapter().loginUrl(STATE)), CONFIGURATION);
        assertThat(auth.toString()).doesNotContain(UPSTREAM_SECRET, ENCRYPTION_KEY);
        fixture.server().verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "syntheticSensitiveUpstreamBody", "http://example.invalid/api/broker/kite/auth/callback",
            "https://example.invalid/wrong-path", "http://localhost:8080/api/broker/kite/auth/callback?secret=syntheticApiSecret",
            "http://localhost:8080/api/broker/kite/auth/callback#syntheticApiSecret",
            "https://syntheticApiSecret@example.invalid/api/broker/kite/auth/callback"
    })
    void unsafeRedirectConfigurationIsRejectedWithoutRetainingItsValue(String redirectUrl) {
        var auth = new KiteAuthenticationProperties(redirectUrl, ENCRYPTION_KEY);

        assertSafe(assertThrows(KiteAuthenticationException.class, auth::requireConfigured), CONFIGURATION);
        assertThat(auth.toString()).doesNotContain(API_SECRET, UPSTREAM_SECRET, ENCRYPTION_KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            REDIRECT_URL, "http://127.0.0.1:8080/api/broker/kite/auth/callback",
            "http://[::1]:8080/api/broker/kite/auth/callback", "https://example.invalid/api/broker/kite/auth/callback"
    })
    void configuredLoopbackOrHttpsRedirectCanBeUsed(String redirectUrl) {
        var auth = new KiteAuthenticationProperties(redirectUrl, ENCRYPTION_KEY);

        auth.requireConfigured();

        assertThat(auth.redirectUri().toString()).isEqualTo(redirectUrl);
    }

    private static Fixture fixture() {
        return fixture(properties(), authProperties());
    }

    private static Fixture fixture(KiteProperties properties, KiteAuthenticationProperties auth) {
        var builder = RestClient.builder().baseUrl("https://api.kite.trade");
        var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = new KiteAuthenticationAdapter(properties, auth, builder.build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(server, adapter);
    }

    private static KiteProperties properties() {
        return new KiteProperties(API_KEY, API_SECRET, "", true);
    }

    private static KiteAuthenticationProperties authProperties() {
        return new KiteAuthenticationProperties(REDIRECT_URL, ENCRYPTION_KEY);
    }

    private static ResponseActions expectExchange(Fixture fixture) {
        return fixture.server().expect(requestTo("https://api.kite.trade/session/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Kite-Version", "3"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("api_key=syntheticApiKey&request_token=syntheticRequestToken"
                        + "&checksum=5ccaa0645f53bb5030dba49542d746cb989532096e2fff8c68623b324f1af048"));
    }

    private static void assertSafe(KiteAuthenticationException error, KiteAuthenticationException.Code code) {
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.getCause()).isNull();
        assertThat(error.getSuppressed()).isEmpty();
        var stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        assertThat(stack.toString()).doesNotContain(API_KEY, API_SECRET, ACCESS_TOKEN, REQUEST_TOKEN,
                UPSTREAM_SECRET, ENCRYPTION_KEY, CHECKSUM, RETURNED_ACCESS_TOKEN);
    }

    private static final class ExchangeLogs implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(KiteAuthenticationAdapter.class);
        private final Level previousLevel = logger.getLevel();
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private ExchangeLogs() {
            appender.start();
            logger.setLevel(Level.WARN);
            logger.addAppender(appender);
        }

        private String onlyMessage() {
            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage()).doesNotContain(API_KEY, API_SECRET, ACCESS_TOKEN,
                    REQUEST_TOKEN, UPSTREAM_SECRET, ENCRYPTION_KEY, CHECKSUM, RETURNED_ACCESS_TOKEN);
            for (Object argument : event.getArgumentArray())
                assertThat(String.valueOf(argument)).doesNotContain(API_KEY, API_SECRET, ACCESS_TOKEN,
                        REQUEST_TOKEN, UPSTREAM_SECRET, ENCRYPTION_KEY, CHECKSUM, RETURNED_ACCESS_TOKEN);
            return event.getFormattedMessage();
        }

        @Override public void close() {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    private record Fixture(MockRestServiceServer server, KiteAuthenticationAdapter adapter) { }
}
