package com.kitehybrid.platform.broker.infrastructure.kite;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.domain.read.BrokerMargins;
import com.kitehybrid.platform.broker.domain.read.BrokerPositions;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.MarginSegment.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** All exchanges are intercepted in-process. Nothing here contacts Kite or a local socket. */
class KiteTradingReadAdapterTest {
    private static final String TEST_KEY = "syntheticTradingReadKey";
    private static final String TEST_TOKEN = "syntheticTradingReadToken";
    private static final String TEST_API_SECRET = "unusedSyntheticApiSecret";
    private static final String SENSITIVE_UPSTREAM = "syntheticTradingReadSecretNeverLog";
    private static final String MARGINS = """
            {"status":"success","data":{"equity":{"enabled":true,"net":123.45,
              "available":{"adhoc_margin":0,"cash":123.45,"opening_balance":123.45,
                "live_balance":123.45,"collateral":0,"intraday_payin":0},
              "utilised":{"debits":0,"exposure":0,"m2m_realised":0,"m2m_unrealised":0,
                "option_premium":0,"payout":0,"span":0,"holding_sales":0,"turnover":0,
                "liquid_collateral":0,"stock_collateral":0,"delivery":0}},
              "commodity":{"enabled":false,"net":0,
                "available":{"adhoc_margin":0,"cash":0,"opening_balance":0,
                  "live_balance":0,"collateral":0,"intraday_payin":0},
                "utilised":{"debits":0,"exposure":0,"m2m_realised":0,"m2m_unrealised":0,
                  "option_premium":0,"payout":0,"span":0,"holding_sales":0,"turnover":0,
                  "liquid_collateral":0,"stock_collateral":0,"delivery":0}}}}
            """;

    @ParameterizedTest
    @EnumSource(Read.class)
    void authenticatedReadsUseOnlyTheExpectedGetAndMeasureSuccess(Read read) {
        try (Fixture fixture = fixture()) {
            expectGet(fixture, read).andRespond(withSuccess(read.validBody(), MediaType.APPLICATION_JSON));

            Object value = read.call(fixture.adapter());

            if (value instanceof List<?> list) {
                assertThat(list).isEmpty();
                assertThrows(UnsupportedOperationException.class, () -> list.add(null));
            } else if (value instanceof BrokerPositions positions) {
                assertThat(positions.net()).isEmpty();
                assertThat(positions.day()).isEmpty();
                assertThrows(UnsupportedOperationException.class, () -> positions.net().add(null));
            } else if (value instanceof BrokerMargins margins) {
                assertThat(margins.segments()).containsOnlyKeys(EQUITY, COMMODITY);
                assertThat(margins.segments().get(EQUITY).net()).isEqualByComparingTo("123.45");
                assertThrows(UnsupportedOperationException.class, () -> margins.segments().clear());
            } else {
                throw new AssertionError("Unexpected normalized read model");
            }
            assertThat(fixture.session().state()).isEqualTo(KiteSession.State.AUTHENTICATED);
            assertMetrics(fixture, read, "success");
            fixture.server().verify();
        }
    }

    @ParameterizedTest
    @MethodSource("unavailableAuthentication")
    void everyReadRequiresTheExistingValidatedSessionBeforeHttp(Read read, String state) {
        KiteProperties properties = switch (state) {
            case "disabled" -> new KiteProperties(TEST_KEY, "", TEST_TOKEN, false);
            case "unconfigured" -> new KiteProperties("", "", "", true);
            case "absent" -> new KiteProperties(TEST_KEY, "", "", true);
            default -> enabledProperties();
        };
        try (Fixture fixture = fixture(properties, false)) {
            if (state.equals("invalidated")) fixture.session().invalidate();

            assertSafe(assertThrows(BrokerReadException.class, () -> read.call(fixture.adapter())), AUTHENTICATION);

            assertThat(fixture.session().authenticated()).isFalse();
            assertMetrics(fixture, read, "AUTHENTICATION");
            fixture.server().verify();
        }
    }

    @ParameterizedTest
    @MethodSource("authenticationRejections")
    void authenticationFailuresInvalidateTheSharedSessionWithoutRetry(Read read, int status) {
        try (Fixture fixture = fixture()) {
            expectGet(fixture, read).andRespond(withStatus(HttpStatus.valueOf(status)).body(SENSITIVE_UPSTREAM));

            BrokerReadException failure = assertThrows(BrokerReadException.class, () -> read.call(fixture.adapter()));

            assertSafe(failure, AUTHENTICATION);
            assertThat(failure.httpStatus()).isEqualTo(status);
            assertThat(fixture.session().state()).isEqualTo(KiteSession.State.INVALIDATED);
            // An authentication rejection in one read also closes the other four read paths.
            for (Read another : Read.values()) {
                assertSafe(assertThrows(BrokerReadException.class, () -> another.call(fixture.adapter())), AUTHENTICATION);
            }
            fixture.server().verify();
        }
    }

    @ParameterizedTest
    @MethodSource("tokenErrorStatuses")
    void tokenErrorEnvelopesInvalidateEvenAtAnUnexpectedHttpStatus(Read read, int status) {
        try (Fixture fixture = fixture()) {
            expectGet(fixture, read).andRespond(withStatus(HttpStatus.valueOf(status))
                    .contentType(MediaType.APPLICATION_JSON).body(error("TokenException")));

            assertSafe(assertThrows(BrokerReadException.class, () -> read.call(fixture.adapter())), AUTHENTICATION);
            assertThat(fixture.session().state()).isEqualTo(KiteSession.State.INVALIDATED);
            assertMetrics(fixture, read, "AUTHENTICATION");
            fixture.server().verify();
        }
    }

    @ParameterizedTest
    @MethodSource("brokerErrorStatuses")
    void broker4xxAnd5xxAreSafeFailuresWithoutRetriesOrSessionInvalidation(Read read, int status) {
        try (Fixture fixture = fixture()) {
            expectGet(fixture, read).andRespond(withStatus(HttpStatus.valueOf(status))
                    .contentType(MediaType.APPLICATION_JSON).body(error("NetworkException")));

            BrokerReadException failure = assertThrows(BrokerReadException.class, () -> read.call(fixture.adapter()));

            assertSafe(failure, BROKER_API);
            assertThat(failure.httpStatus()).isEqualTo(status);
            assertThat(fixture.session().state()).isEqualTo(KiteSession.State.AUTHENTICATED);
            assertMetrics(fixture, read, "BROKER_API");
            fixture.server().verify();
        }
    }

    @ParameterizedTest
    @EnumSource(Read.class)
    void brokerErrorAtHttpSuccessIsStillAnObservedFailure(Read read) {
        try (Fixture fixture = fixture()) {
            expectGet(fixture, read).andRespond(withSuccess(error("InputException"), MediaType.APPLICATION_JSON));

            assertSafe(assertThrows(BrokerReadException.class, () -> read.call(fixture.adapter())), BROKER_API);
            assertMetrics(fixture, read, "BROKER_API");
            fixture.server().verify();
        }
    }

    @ParameterizedTest
    @MethodSource("malformedResponses")
    void malformedOrMissingEnvelopeDataCannotProduceAnObservation(Read read, String body) {
        try (Fixture fixture = fixture()) {
            expectGet(fixture, read).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            assertSafe(assertThrows(BrokerReadException.class, () -> read.call(fixture.adapter())), INVALID_RESPONSE);
            assertMetrics(fixture, read, "INVALID_RESPONSE");
            fixture.server().verify();
        }
    }

    @ParameterizedTest
    @MethodSource("sizeBoundaries")
    void allRoutesBoundBothWireBodiesAndGzipExpansion(Read read, boolean compressed) throws IOException {
        try (Fixture fixture = fixture()) {
            String oversized = " ".repeat(read.endpoint.limit + 1);
            var response = withSuccess(compressed ? gzip(oversized) : oversized.getBytes(StandardCharsets.UTF_8),
                    MediaType.APPLICATION_JSON);
            if (compressed) response.header("Content-Encoding", "gzip");
            expectGet(fixture, read).andRespond(response);

            assertSafe(assertThrows(BrokerReadException.class, () -> read.call(fixture.adapter())), INVALID_RESPONSE);
            assertMetrics(fixture, read, "INVALID_RESPONSE");
            fixture.server().verify();
        }
    }

    @ParameterizedTest
    @EnumSource(Read.class)
    void validGzipResponsesUseTheSameNormalization(Read read) throws IOException {
        try (Fixture fixture = fixture()) {
            expectGet(fixture, read).andRespond(withSuccess(gzip(read.validBody()), MediaType.APPLICATION_JSON)
                    .header("Content-Encoding", "gzip"));

            assertThat(read.call(fixture.adapter())).isNotNull();
            assertMetrics(fixture, read, "success");
            fixture.server().verify();
        }
    }

    @Test
    void missingMarginSegmentsAreNotFabricatedAsZeroBalances() {
        try (Fixture fixture = fixture()) {
            expectGet(fixture, Read.MARGINS).andRespond(withSuccess(
                    "{\"status\":\"success\",\"data\":{}}", MediaType.APPLICATION_JSON));

            assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::margins), INVALID_RESPONSE);
            assertMetrics(fixture, Read.MARGINS, "INVALID_RESPONSE");
            fixture.server().verify();
        }
    }

    @Test
    void logsExceptionsAndMetricLabelsNeverRetainCredentialsOrBrokerMessages() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        root.addAppender(capture);
        try (Fixture fixture = fixture()) {
            expectGet(fixture, Read.ORDERS).andRespond(request -> { throw new IOException(SENSITIVE_UPSTREAM); });
            expectGet(fixture, Read.TRADES).andRespond(withSuccess(error("InputException"), MediaType.APPLICATION_JSON));
            expectGet(fixture, Read.POSITIONS).andRespond(withSuccess("{\"" + SENSITIVE_UPSTREAM, MediaType.APPLICATION_JSON));
            expectGet(fixture, Read.HOLDINGS).andRespond(request -> { throw new IllegalStateException(SENSITIVE_UPSTREAM); });
            expectGet(fixture, Read.MARGINS).andRespond(withSuccess(MARGINS, MediaType.APPLICATION_JSON));

            assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::orders), TRANSPORT);
            assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::trades), BROKER_API);
            assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::positions), INVALID_RESPONSE);
            assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::holdings), INVALID_RESPONSE);
            fixture.adapter().margins();

            assertThat(capture.list).isNotEmpty();
            capture.list.forEach(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(TEST_KEY, TEST_TOKEN, TEST_API_SECRET, SENSITIVE_UPSTREAM);
                assertThat(event.getThrowableProxy()).isNull();
            });
            fixture.metrics().getMeters().forEach(meter -> {
                assertThat(meter.getId().getTags()).extracting("key")
                        .containsExactlyInAnyOrder("operation", "result");
                assertThat(meter.getId().toString()).doesNotContain(TEST_KEY, TEST_TOKEN, TEST_API_SECRET, SENSITIVE_UPSTREAM);
            });
            fixture.server().verify();
        } finally {
            root.detachAppender(capture);
            capture.stop();
        }
    }

    @Test
    void holdingsValidationLogsOnlyBoundedReasonAndField() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        root.addAppender(capture);
        try (Fixture fixture = fixture()) {
            expectGet(fixture, Read.HOLDINGS).andRespond(withSuccess(
                    "{\"status\":\"success\",\"data\":[{\"instrument_token\":\"bad-token\","
                            + "\"exchange\":\"NSE\",\"tradingsymbol\":\"INFY\","
                            + "\"isin\":\"INE009A01021\",\"product\":\"CNC\","
                            + "\"quantity\":1.25}]}", MediaType.APPLICATION_JSON));

            assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::holdings), INVALID_RESPONSE);
            assertThat(capture.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                    .isEqualTo("Kite holdings normalization failed: reason=INVALID_NUMERIC_FIELD, field=instrument_token"));
            capture.list.forEach(event -> assertThat(event.getFormattedMessage())
                    .doesNotContain("1.25", "INE009A01021", "256265", "INFY"));
            fixture.server().verify();
        } finally {
            root.detachAppender(capture);
            capture.stop();
        }
    }

    @Test
    void fixedReadOnlyRoutesHaveExplicitBoundedResponseBudgets() {
        for (Read read : Read.values()) {
            assertThat(read.endpoint.path).isEqualTo(read.path);
            assertThat(read.endpoint.limit).isEqualTo(read == Read.MARGINS ? 64 * 1024 : 4 * 1024 * 1024);
        }
    }

    private static Stream<Arguments> unavailableAuthentication() {
        return Stream.of(Read.values()).flatMap(read -> Stream.of("disabled", "unconfigured", "absent",
                "unverified", "invalidated").map(state -> Arguments.of(read, state)));
    }
    private static Stream<Arguments> authenticationRejections() { return statuses(401, 403); }
    private static Stream<Arguments> tokenErrorStatuses() { return statuses(200, 400, 500); }
    private static Stream<Arguments> brokerErrorStatuses() { return statuses(400, 404, 429, 500, 502, 503); }
    private static Stream<Arguments> statuses(Integer... values) {
        return Stream.of(Read.values()).flatMap(read -> Stream.of(values).map(status -> Arguments.of(read, status)));
    }
    private static Stream<Arguments> malformedResponses() {
        return Stream.of(Read.values()).flatMap(read -> Stream.of("", "not-json", "{", "{}", "[]", "null",
                "{\"status\":\"unknown\",\"data\":[]}", "{\"status\":\"success\"}",
                "{\"status\":\"success\",\"data\":null}", read.validBody() + " {}")
                .map(body -> Arguments.of(read, body)));
    }
    private static Stream<Arguments> sizeBoundaries() {
        return Stream.of(Read.values()).flatMap(read -> Stream.of(false, true).map(gzip -> Arguments.of(read, gzip)));
    }
    private static String error(String type) {
        return "{\"status\":\"error\",\"error_type\":\"" + type + "\",\"message\":\"" + SENSITIVE_UPSTREAM + "\"}";
    }
    private static KiteProperties enabledProperties() {
        return new KiteProperties(TEST_KEY, TEST_API_SECRET, TEST_TOKEN, true);
    }
    private static Fixture fixture() { return fixture(enabledProperties(), true); }
    private static Fixture fixture(KiteProperties properties, boolean authenticated) {
        var builder = RestClient.builder().baseUrl("https://api.kite.trade");
        var server = MockRestServiceServer.bindTo(builder).build();
        var session = new KiteSession(properties);
        if (authenticated) session.profileValidated();
        var metrics = new SimpleMeterRegistry();
        var transport = new KiteRestTransport(builder.build(), session);
        return new Fixture(server, session, metrics, new KiteTradingReadAdapter(transport, session,
                new KiteTradingReadMapper(new InMemoryInstrumentRegistry()), metrics));
    }
    private static ResponseActions expectGet(Fixture fixture, Read read) {
        return fixture.server().expect(requestTo("https://api.kite.trade" + read.path))
                .andExpect(method(HttpMethod.GET)).andExpect(header("X-Kite-Version", "3"))
                .andExpect(header("Authorization", "token " + TEST_KEY + ":" + TEST_TOKEN))
                .andExpect(header("Accept-Encoding", "gzip")).andExpect(content().string(""));
    }
    private static void assertMetrics(Fixture fixture, Read read, String result) {
        String operation = read.name().toLowerCase(java.util.Locale.ROOT);
        assertThat(fixture.metrics().get("kite.rest.operations").tags("operation", operation, "result", result)
                .counter().count()).isEqualTo(1);
        var timer = fixture.metrics().get("kite.rest.duration").tags("operation", operation, "result", result).timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isGreaterThanOrEqualTo(0);
        assertThat(fixture.metrics().getMeters()).hasSize(2);
    }
    private static byte[] gzip(String body) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) { gzip.write(body.getBytes(StandardCharsets.UTF_8)); }
        return output.toByteArray();
    }
    private static void assertSafe(BrokerReadException failure, BrokerReadException.Category category) {
        assertThat(failure.category()).isEqualTo(category);
        assertThat(failure.getCause()).isNull();
        assertThat(failure.getSuppressed()).isEmpty();
        var stack = new StringWriter();
        failure.printStackTrace(new PrintWriter(stack));
        assertThat(stack.toString()).doesNotContain(TEST_KEY, TEST_TOKEN, TEST_API_SECRET, SENSITIVE_UPSTREAM);
    }
    private record Fixture(MockRestServiceServer server, KiteSession session, SimpleMeterRegistry metrics,
                           KiteTradingReadAdapter adapter) implements AutoCloseable {
        @Override public void close() { metrics.close(); }
    }
    private enum Read {
        ORDERS(KiteRestTransport.Endpoint.ORDERS, "/orders"), TRADES(KiteRestTransport.Endpoint.TRADES, "/trades"),
        POSITIONS(KiteRestTransport.Endpoint.POSITIONS, "/portfolio/positions"),
        HOLDINGS(KiteRestTransport.Endpoint.HOLDINGS, "/portfolio/holdings"),
        MARGINS(KiteRestTransport.Endpoint.MARGINS, "/user/margins");
        private final KiteRestTransport.Endpoint endpoint;
        private final String path;
        Read(KiteRestTransport.Endpoint endpoint, String path) { this.endpoint = endpoint; this.path = path; }
        String validBody() {
            return switch (this) {
                case POSITIONS -> "{\"status\":\"success\",\"data\":{\"net\":[],\"day\":[]}}";
                case MARGINS -> KiteTradingReadAdapterTest.MARGINS;
                default -> "{\"status\":\"success\",\"data\":[]}";
            };
        }
        Object call(KiteTradingReadAdapter adapter) {
            return switch (this) {
                case ORDERS -> adapter.orders(); case TRADES -> adapter.trades();
                case POSITIONS -> adapter.positions(); case HOLDINGS -> adapter.holdings();
                case MARGINS -> adapter.margins();
            };
        }
    }
}
