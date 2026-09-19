package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KiteConfigurationTest {
    private static final String TEST_KEY = "syntheticApiKey";
    private static final String TEST_SECRET = "syntheticSecretNeverLog";
    private static final String TEST_TOKEN = "syntheticAccessTokenNeverLog";

    @Test
    void absentPropertiesBindWithoutEnablingRestOrRequiringCredentials() {
        KiteProperties properties = bind(Map.of());

        assertThat(properties.restEnabled()).isFalse();
        assertThat(new KiteSession(properties).state()).isEqualTo(KiteSession.State.DISABLED);
        assertThat(assertThrows(BrokerReadException.class, properties::authorization).category())
                .isEqualTo(CONFIGURATION);
    }

    @Test
    void constructorBindingAcceptsAnExternallyObtainedTokenAndDoesNotNeedApiSecret() {
        KiteProperties properties = bind(Map.of(
                "kite.api-key", TEST_KEY,
                "kite.access-token", TEST_TOKEN,
                "kite.rest-enabled", "true"));

        assertThat(properties.authorization()).isEqualTo("token " + TEST_KEY + ":" + TEST_TOKEN);
        assertThat(new KiteSession(properties).state()).isEqualTo(KiteSession.State.UNVERIFIED);
    }

    @Test
    void bindingAndNormalObjectRenderingDoNotRevealCredentials() {
        KiteProperties properties = bind(Map.of(
                "kite.api-key", TEST_KEY,
                "kite.api-secret", TEST_SECRET,
                "kite.access-token", TEST_TOKEN,
                "kite.rest-enabled", "true"));

        assertThat(properties.toString()).contains("REDACTED")
                .doesNotContain(TEST_KEY, TEST_SECRET, TEST_TOKEN);
        assertThat(new KiteSession(properties).toString()).contains("UNVERIFIED", "REDACTED")
                .doesNotContain(TEST_KEY, TEST_SECRET, TEST_TOKEN);
    }

    @Test
    void populatedCredentialsDoNotImplicitlyEnableRest() {
        KiteProperties properties = KiteProperties.fromEnvironment(Map.of(
                "KITE_API_KEY", TEST_KEY,
                "KITE_API_SECRET", TEST_SECRET,
                "KITE_ACCESS_TOKEN", TEST_TOKEN));

        assertThat(new KiteSession(properties).state()).isEqualTo(KiteSession.State.DISABLED);
        assertThat(assertThrows(BrokerReadException.class, properties::authorization).category())
                .isEqualTo(CONFIGURATION);
    }

    @Test
    void explicitEnvironmentConfigurationCreatesAnUnverifiedSession() {
        KiteProperties properties = KiteProperties.fromEnvironment(Map.of(
                "KITE_API_KEY", TEST_KEY,
                "KITE_ACCESS_TOKEN", TEST_TOKEN,
                "KITE_REST_ENABLED", "true"));

        assertThat(new KiteSession(properties).state()).isEqualTo(KiteSession.State.UNVERIFIED);
        assertThat(properties.authorization()).isEqualTo("token " + TEST_KEY + ":" + TEST_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "true-but-not-really", "1", "yes", "syntheticSensitiveInput"})
    void malformedEnvironmentOptInFailsSafely(String value) {
        BrokerReadException failure = assertThrows(BrokerReadException.class,
                () -> KiteProperties.fromEnvironment(Map.of("KITE_REST_ENABLED", value)));

        assertThat(failure.category()).isEqualTo(CONFIGURATION);
        assertThat(failure.getMessage()).isEqualTo("Broker read failed: CONFIGURATION");
        assertThat(failure.getCause()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "synthetic:injected", "synthetic\r\nInjected", "synthetic/token"})
    void malformedAccessTokenCannotBecomeAnAuthorizationHeader(String value) {
        KiteProperties properties = new KiteProperties(TEST_KEY, TEST_SECRET, value, true);

        assertThat(new KiteSession(properties).state()).isEqualTo(KiteSession.State.NOT_CONFIGURED);
        BrokerReadException failure = assertThrows(BrokerReadException.class, properties::authorization);
        assertSafeConfigurationFailure(failure);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "synthetic:injected", "synthetic\nInjected"})
    void malformedApiKeyCannotBecomeAnAuthorizationHeader(String value) {
        KiteProperties properties = new KiteProperties(value, TEST_SECRET, TEST_TOKEN, true);

        assertThat(new KiteSession(properties).state()).isEqualTo(KiteSession.State.NOT_CONFIGURED);
        assertSafeConfigurationFailure(assertThrows(BrokerReadException.class, properties::authorization));
    }

    @Test
    void credentialLengthLimitsFailWithoutEchoingRejectedValues() {
        assertSafeConfigurationFailure(assertThrows(BrokerReadException.class,
                () -> new KiteProperties("a".repeat(129), TEST_SECRET, TEST_TOKEN, true).authorization()));
        assertSafeConfigurationFailure(assertThrows(BrokerReadException.class,
                () -> new KiteProperties(TEST_KEY, TEST_SECRET, "a".repeat(257), true).authorization()));
    }

    @Test
    void failedValidationCannotReviveAnInvalidatedSession() {
        KiteSession session = new KiteSession(new KiteProperties(TEST_KEY, TEST_SECRET, TEST_TOKEN, true));
        session.profileValidated();
        assertThat(session.state()).isEqualTo(KiteSession.State.VALIDATED);
        session.invalidate();
        session.profileValidated();

        assertThat(session.state()).isEqualTo(KiteSession.State.INVALIDATED);
        assertThat(assertThrows(BrokerReadException.class, session::authorization).category())
                .isEqualTo(BrokerReadException.Category.AUTHENTICATION);
    }

    private static KiteProperties bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("kite", Bindable.of(KiteProperties.class));
    }

    private static void assertSafeConfigurationFailure(BrokerReadException failure) {
        assertThat(failure.category()).isEqualTo(CONFIGURATION);
        assertThat(failure.getCause()).isNull();
        StringWriter stack = new StringWriter();
        failure.printStackTrace(new PrintWriter(stack));
        assertThat(stack.toString()).doesNotContain(TEST_KEY, TEST_SECRET, TEST_TOKEN);
    }
}
