package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.account.domain.BrokerProfile;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Every HTTP exchange is intercepted in-process; no Zerodha or local socket is contacted. */
class KiteRestReadTest {
    private static final String TEST_KEY = "syntheticKey";
    private static final String TEST_TOKEN = "syntheticToken";
    private static final String SENSITIVE_UPSTREAM = "syntheticUpstreamSecretNeverLog";
    private static final String PROFILE = """
            {"status":"success","data":{"user_id":"TEST123","broker":"ZERODHA",
            "exchanges":["NSE","BSE"],"email":"ignored@example.invalid",
            "user_name":"Not retained","unknown_future_attribute":"ignored"}}
            """;

    @Test
    void profileUsesAuthenticatedGetAndMapsOnlyInternalProfileFields() {
        Fixture fixture = fixture(enabledProperties());
        expectGet(fixture, "/user/profile").andRespond(withSuccess(PROFILE, MediaType.APPLICATION_JSON));

        BrokerProfile profile = fixture.adapter().currentProfile();

        assertThat(profile.broker()).isEqualTo("ZERODHA");
        assertThat(profile.userId()).isEqualTo("TEST123");
        assertThat(profile.exchanges()).containsExactlyInAnyOrder("NSE", "BSE");
        assertThrows(UnsupportedOperationException.class, () -> profile.exchanges().add("MCX"));
        assertThat(profile.toString()).doesNotContain("TEST123", "ignored@example.invalid", "Not retained");
        assertThat(fixture.session().state()).isEqualTo(KiteSession.State.VALIDATED);
        fixture.server().verify();
    }

    @Test
    void instrumentMasterUsesOnlyTheReadOnlyInstrumentPath() {
        Fixture fixture = fixture(enabledProperties());
        String csv = "instrument_token,tradingsymbol\n123,TEST\n";
        expectGet(fixture, "/instruments").andRespond(withSuccess(csv, MediaType.valueOf("text/csv")));

        assertThat(fixture.transport().get(KiteRestTransport.Endpoint.INSTRUMENTS)).isEqualTo(csv);
        assertThat(fixture.session().state()).isEqualTo(KiteSession.State.UNVERIFIED);
        fixture.server().verify();
    }

    @Test
    void missingCredentialsRejectBeforeAnyHttpRequest() {
        Fixture fixture = fixture(new KiteProperties("", "", "", true));

        assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile), CONFIGURATION);
        assertThat(fixture.session().state()).isEqualTo(KiteSession.State.NOT_CONFIGURED);
        fixture.server().verify();
    }

    @Test
    void disabledRestRejectsBeforeAnyHttpRequestEvenWhenCredentialsExist() {
        Fixture fixture = fixture(new KiteProperties(TEST_KEY, "", TEST_TOKEN, false));

        assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile), CONFIGURATION);
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void authenticationRejectionInvalidatesSessionAndPreventsAnotherRequest(int status) {
        Fixture fixture = fixture(enabledProperties());
        expectGet(fixture, "/user/profile").andRespond(withStatus(HttpStatus.valueOf(status))
                .body(SENSITIVE_UPSTREAM));

        BrokerReadException failure = assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile);
        assertSafe(failure, AUTHENTICATION);
        assertThat(failure.httpStatus()).isEqualTo(status);
        assertThat(fixture.session().state()).isEqualTo(KiteSession.State.INVALIDATED);
        assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile), AUTHENTICATION);
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 500})
    void tokenErrorEnvelopeInvalidatesSessionEvenWithAnUnexpectedHttpStatus(int status) {
        Fixture fixture = fixture(enabledProperties());
        expectGet(fixture, "/user/profile").andRespond(withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":\"error\",\"error_type\":\"TokenException\",\"message\":\""
                        + SENSITIVE_UPSTREAM + "\"}"));

        assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile), AUTHENTICATION);
        assertThat(fixture.session().state()).isEqualTo(KiteSession.State.INVALIDATED);
        assertSafe(assertThrows(BrokerReadException.class,
                () -> fixture.transport().get(KiteRestTransport.Endpoint.INSTRUMENTS)), AUTHENTICATION);
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 502, 503})
    void brokerFailuresAreDistinctFromLocalTransportFailures(int status) {
        Fixture fixture = fixture(enabledProperties());
        expectGet(fixture, "/user/profile").andRespond(withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":\"error\",\"error_type\":\"NetworkException\",\"message\":\""
                        + SENSITIVE_UPSTREAM + "\"}"));

        BrokerReadException failure = assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile);
        assertSafe(failure, BROKER_API);
        assertThat(failure.httpStatus()).isEqualTo(status);
        assertThat(fixture.session().state()).isEqualTo(KiteSession.State.UNVERIFIED);
        fixture.server().verify();
    }

    @Test
    void localIoFailureDoesNotRetainSensitiveMessageOrCause() {
        Fixture fixture = fixture(enabledProperties());
        expectGet(fixture, "/user/profile").andRespond(request -> {
            throw new IOException(SENSITIVE_UPSTREAM);
        });

        assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile), TRANSPORT);
        assertThat(fixture.session().state()).isEqualTo(KiteSession.State.UNVERIFIED);
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void gzipIsDecodedWithAHeaderOrRecognizedByItsMagicBytes(boolean headerPresent) throws IOException {
        Fixture fixture = fixture(enabledProperties());
        var response = withSuccess(gzip(PROFILE), MediaType.APPLICATION_JSON);
        if (headerPresent) response.header("Content-Encoding", "gzip");
        expectGet(fixture, "/user/profile").andRespond(response);

        assertThat(fixture.adapter().currentProfile().userId()).isEqualTo("TEST123");
        fixture.server().verify();
    }

    @Test
    void corruptGzipFailsWithoutReturningPartialContent() {
        Fixture fixture = fixture(enabledProperties());
        expectGet(fixture, "/user/profile").andRespond(withSuccess(SENSITIVE_UPSTREAM, MediaType.APPLICATION_JSON)
                .header("Content-Encoding", "gzip"));

        assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile), INVALID_RESPONSE);
        fixture.server().verify();
    }

    @Test
    void unknownContentEncodingFailsWithoutTryingToMapTheBody() {
        Fixture fixture = fixture(enabledProperties());
        expectGet(fixture, "/user/profile").andRespond(withSuccess(PROFILE, MediaType.APPLICATION_JSON)
                .header("Content-Encoding", "br"));

        assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile), INVALID_RESPONSE);
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void responseSizeLimitAppliesToCompressedAndUncompressedBodies(boolean compressed) throws IOException {
        Fixture fixture = fixture(enabledProperties());
        String oversized = "x".repeat(KiteRestTransport.Endpoint.PROFILE.limit + 1);
        var response = withSuccess(compressed ? gzip(oversized) : oversized.getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON);
        if (compressed) response.header("Content-Encoding", "gzip");
        expectGet(fixture, "/user/profile").andRespond(response);

        assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile), INVALID_RESPONSE);
        fixture.server().verify();
    }

    @Test
    void malformedUtf8IsRejectedInsteadOfSilentlyReplacingBytes() {
        Fixture fixture = fixture(enabledProperties());
        expectGet(fixture, "/user/profile").andRespond(withSuccess(new byte[]{(byte) 0xc3, (byte) 0x28},
                MediaType.APPLICATION_JSON));

        assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile), INVALID_RESPONSE);
        fixture.server().verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "not-json", "{", "{}", "[]", "null", "{\"status\":\"unknown\"}",
            "{\"status\":\"success\",\"data\":null}",
            "{\"status\":\"success\",\"data\":{\"user_id\":123,\"broker\":\"ZERODHA\",\"exchanges\":[\"NSE\"]}}",
            "{\"status\":\"success\",\"data\":{\"user_id\":\"TEST123\",\"broker\":\"OTHER\",\"exchanges\":[\"NSE\"]}}",
            "{\"status\":\"success\",\"data\":{\"user_id\":\"TEST123\",\"broker\":\"ZERODHA\",\"exchanges\":[\"NSE\",\"NSE\"]}}",
            "{\"status\":\"success\",\"data\":{\"user_id\":\"TEST123\",\"broker\":\"ZERODHA\",\"exchanges\":[1]}}",
            "{\"status\":\"success\",\"data\":{\"user_id\":\"TEST123\",\"broker\":\"ZERODHA\",\"exchanges\":[]}}"
    })
    void malformedOrUnexpectedProfileResponseDoesNotValidateTheSession(String body) {
        Fixture fixture = fixture(enabledProperties());
        expectGet(fixture, "/user/profile").andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile), INVALID_RESPONSE);
        assertThat(fixture.session().state()).isEqualTo(KiteSession.State.UNVERIFIED);
        fixture.server().verify();
    }

    @Test
    void trailingJsonCannotTurnAnInvalidResponseIntoAValidProfile() {
        assertSafe(assertThrows(BrokerReadException.class,
                () -> KiteProfileAdapter.map(PROFILE + " {\"unexpected\":true}")), INVALID_RESPONSE);
    }

    @Test
    void duplicateJsonIdentityFieldsAreRejectedRatherThanTakingTheLastValue() {
        String body = PROFILE.replace("\"user_id\":\"TEST123\"",
                "\"user_id\":\"FIRST123\",\"user_id\":\"TEST123\"");

        assertSafe(assertThrows(BrokerReadException.class, () -> KiteProfileAdapter.map(body)), INVALID_RESPONSE);
    }

    @Test
    void errorEnvelopeAtHttpSuccessIsABrokerFailureAndNeverValidatesTheSession() {
        Fixture fixture = fixture(enabledProperties());
        expectGet(fixture, "/user/profile").andRespond(withSuccess(
                "{\"status\":\"error\",\"message\":\"" + SENSITIVE_UPSTREAM + "\"}", MediaType.APPLICATION_JSON));

        assertSafe(assertThrows(BrokerReadException.class, fixture.adapter()::currentProfile), BROKER_API);
        assertThat(fixture.session().state()).isEqualTo(KiteSession.State.UNVERIFIED);
        fixture.server().verify();
    }

    private static KiteProperties enabledProperties() {
        return new KiteProperties(TEST_KEY, "unusedSyntheticApiSecret", TEST_TOKEN, true);
    }

    private static Fixture fixture(KiteProperties properties) {
        var builder = RestClient.builder().baseUrl("https://api.kite.trade");
        var server = MockRestServiceServer.bindTo(builder).build();
        var session = new KiteSession(properties);
        var transport = new KiteRestTransport(builder.build(), session);
        return new Fixture(server, session, transport, new KiteProfileAdapter(transport, session));
    }

    private static ResponseActions expectGet(Fixture fixture, String path) {
        return fixture.server().expect(requestTo("https://api.kite.trade" + path))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Kite-Version", "3"))
                .andExpect(header("Authorization", "token " + TEST_KEY + ":" + TEST_TOKEN))
                .andExpect(header("Accept-Encoding", "gzip"))
                .andExpect(content().string(""));
    }

    private static byte[] gzip(String body) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static void assertSafe(BrokerReadException failure, BrokerReadException.Category category) {
        assertThat(failure.category()).isEqualTo(category);
        assertThat(failure.getCause()).isNull();
        assertThat(failure.getSuppressed()).isEmpty();
        StringWriter stack = new StringWriter();
        failure.printStackTrace(new PrintWriter(stack));
        assertThat(stack.toString()).doesNotContain(TEST_KEY, TEST_TOKEN, SENSITIVE_UPSTREAM);
    }

    private record Fixture(MockRestServiceServer server, KiteSession session,
                           KiteRestTransport transport, KiteProfileAdapter adapter) { }
}
