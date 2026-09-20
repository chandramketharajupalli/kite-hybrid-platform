package com.kitehybrid.platform;

import com.kitehybrid.platform.broker.infrastructure.kite.KiteMarketDataConfiguration;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteMarketDataDiagnosticController;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteMarketDataProperties;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteProperties;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteSession;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.LatestMarketDataStore;
import com.kitehybrid.platform.marketdata.application.MarketDataGateway;
import com.kitehybrid.platform.marketdata.application.MarketDataHealth;
import com.kitehybrid.platform.marketdata.infrastructure.InMemoryLatestMarketDataStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class MarketDataConfigurationTest {
    @Test void defaultsAreDisabledAndBoundedWithoutAnyCredentials() {
        var properties = bind(Map.of());
        assertThat(properties.enabled()).isFalse();
        assertThat(properties.diagnosticEnabled()).isFalse();
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.staleAfter()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.idleTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.queueCapacity()).isEqualTo(4096);
        assertThat(properties.maxSubscriptions()).isEqualTo(3000);
        assertThat(properties.reconnect().initialDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.reconnect().maxDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.reconnect().maxAttempts()).isEqualTo(8);
    }

    @ParameterizedTest @CsvSource({
            "queue-capacity,0", "queue-capacity,1000001", "max-subscriptions,0", "max-subscriptions,3001",
            "connect-timeout,0s", "connect-timeout,-1s", "connect-timeout,2d", "stale-after,0s",
            "idle-timeout,0s", "reconnect.initial-delay,0s", "reconnect.initial-delay,31s",
            "reconnect.max-delay,0s", "reconnect.max-delay,2d", "reconnect.max-attempts,-1",
            "reconnect.max-attempts,101"
    })
    void invalidCapacityOrTimingIsRejectedDuringBinding(String key, String value) {
        assertThrows(BindException.class, () -> bind(Map.of("kite.market-data." + key, value)));
    }

    @Test void explicitSmallBoundsAndZeroReconnectAttemptsAreSupported() {
        var properties = bind(Map.of("kite.market-data.enabled", "true", "kite.market-data.queue-capacity", "1",
                "kite.market-data.max-subscriptions", "1", "kite.market-data.reconnect.max-attempts", "0",
                "kite.market-data.reconnect.initial-delay", "20ms", "kite.market-data.reconnect.max-delay", "20ms"));
        assertThat(properties.enabled()).isTrue();
        assertThat(properties.queueCapacity()).isEqualTo(1);
        assertThat(properties.maxSubscriptions()).isEqualTo(1);
        assertThat(properties.reconnect().maxAttempts()).isZero();
    }

    @Test void constructingProductionConfigurationAndStartingDisabledGatewayDoesNotOpenWebSocket() {
        new ApplicationContextRunner().withUserConfiguration(KiteMarketDataConfiguration.class)
                .withBean(KiteSession.class, () -> new KiteSession(new KiteProperties("", "", "", false), Clock.systemUTC()))
                .withBean(InstrumentRegistry.class, InMemoryInstrumentRegistry::new)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(MarketDataGateway.class)
                            .hasSingleBean(LatestMarketDataStore.class);
                    var gateway = context.getBean(MarketDataGateway.class);
                    gateway.start();
                    assertThat(gateway.state()).isEqualTo(MarketDataGateway.State.STOPPED);
                    assertThat(gateway.health().reason()).isEqualTo(MarketDataHealth.Reason.DISABLED);
                    assertThat(gateway.health().framesReceived()).isZero();
                    assertThat(gateway.health().reconnectAttempts()).isZero();
                });
    }

    @ParameterizedTest @ValueSource(strings = {"test", "production"})
    void diagnosticRoutesAreAbsentOutsideDevelopmentEvenWhenBothFlagsAreEnabled(String profile) {
        diagnosticContext(profile).withPropertyValues("kite.market-data.enabled=true", "kite.market-data.diagnostic-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(KiteMarketDataDiagnosticController.class);
                    var http = MockMvcBuilders.webAppContextSetup(context).build();
                    http.perform(post("/api/development/market-data/start").param("exchange", "NSE").param("symbol", "INFY"))
                            .andExpect(status().isNotFound());
                    http.perform(get("/api/development/market-data/status")).andExpect(status().isNotFound());
                });
    }

    @ParameterizedTest @CsvSource({"false,false", "true,false", "false,true"})
    void developmentDiagnosticsRequireBothExplicitFlags(boolean enabled, boolean diagnosticEnabled) {
        diagnosticContext("development").withPropertyValues("kite.market-data.enabled=" + enabled,
                        "kite.market-data.diagnostic-enabled=" + diagnosticEnabled)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(KiteMarketDataDiagnosticController.class));
    }

    @Test void explicitDevelopmentFlagsRegisterDiagnosticController() {
        diagnosticContext("development").withPropertyValues("kite.market-data.enabled=true", "kite.market-data.diagnostic-enabled=true")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(KiteMarketDataDiagnosticController.class));
    }

    @Test void manualDiagnosticReturnsNormalizedDataAndControlledLookupErrors() {
        diagnosticContext("development").withPropertyValues("kite.market-data.enabled=true", "kite.market-data.diagnostic-enabled=true")
                .run(context -> {
                    var instrument = com.kitehybrid.platform.instrument.domain.Instrument.create(
                            new com.kitehybrid.platform.instrument.domain.BrokerInstrumentId("ZERODHA", "408065"),
                            "INFY", "NSE", "CASH", com.kitehybrid.platform.instrument.domain.InstrumentType.CASH,
                            java.util.Optional.empty(), java.util.Optional.empty(), new java.math.BigDecimal("0.05"), 1);
                    var now = java.time.Instant.parse("2026-09-20T04:00:00Z");
                    context.getBean(InstrumentRegistry.class).replace(java.util.List.of(instrument), now);
                    context.getBean(LatestMarketDataStore.class).update(new com.kitehybrid.platform.marketdata.domain.Tick(
                            instrument.id(), new java.math.BigDecimal("123.45"), now));
                    var http = MockMvcBuilders.webAppContextSetup(context).build();
                    http.perform(get("/api/development/market-data/latest").param("exchange", "NSE").param("symbol", "INFY"))
                            .andExpect(status().isOk()).andExpect(jsonPath("$.lastPrice").value(123.45))
                            .andExpect(jsonPath("$.instrumentId.value").value(instrument.id().value().toString()))
                            .andExpect(jsonPath("$.receivedAt").exists()).andExpect(jsonPath("$.accessToken").doesNotExist());
                    for (String symbol : java.util.List.of("", " ", "MISSING")) {
                        http.perform(post("/api/development/market-data/start").param("exchange", "NSE").param("symbol", symbol))
                                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reason").value("UNRESOLVED_INSTRUMENT"));
                    }
                });
    }

    private static WebApplicationContextRunner diagnosticContext(String profile) {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class, JacksonAutoConfiguration.class))
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withUserConfiguration(KiteMarketDataDiagnosticController.class)
                .withBean(MarketDataGateway.class, () -> mock(MarketDataGateway.class))
                .withBean(InstrumentRegistry.class, InMemoryInstrumentRegistry::new)
                .withBean(LatestMarketDataStore.class, InMemoryLatestMarketDataStore::new);
    }

    private static KiteMarketDataProperties bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("kite.market-data", Bindable.of(KiteMarketDataProperties.class));
    }
}
