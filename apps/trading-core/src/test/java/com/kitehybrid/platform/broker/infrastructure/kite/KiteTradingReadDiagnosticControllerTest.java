package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class KiteTradingReadDiagnosticControllerTest {
    private static final String BASE = "/api/development/trading-read";
    private BrokerOrdersProvider orders;
    private BrokerTradesProvider trades;
    private BrokerPositionsProvider positions;
    private BrokerHoldingsProvider holdings;
    private BrokerMarginsProvider margins;
    private MockMvc http;

    @BeforeEach void setup() {
        orders = mock(BrokerOrdersProvider.class);
        trades = mock(BrokerTradesProvider.class);
        positions = mock(BrokerPositionsProvider.class);
        holdings = mock(BrokerHoldingsProvider.class);
        margins = mock(BrokerMarginsProvider.class);
        http = MockMvcBuilders.standaloneSetup(new KiteTradingReadDiagnosticController(
                orders, trades, positions, holdings, margins))
                .addDispatcherServletCustomizer(servlet -> servlet.setDispatchTraceRequest(true)).build();
    }

    @ParameterizedTest @ValueSource(strings = {"test", "paper", "production", "development,production"})
    void routesAreAbsentOutsideDevelopmentAndWhenProductionIsAlsoActive(String profiles) {
        context(profiles.split(",")).withPropertyValues(
                "kite.trading-read.enabled=true", "kite.trading-read.diagnostic-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(KiteTradingReadDiagnosticController.class);
                    MockMvcBuilders.webAppContextSetup(context).build().perform(local(get(BASE + "/orders")))
                            .andExpect(status().isNotFound());
                    verifyNoInteractions(orders, trades, positions, holdings, margins);
                });
    }

    @Test void diagnosticsAreAbsentByDefault() {
        context("development").run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(KiteTradingReadDiagnosticController.class));
        verifyNoInteractions(orders, trades, positions, holdings, margins);
    }

    @ParameterizedTest @CsvSource({"false,false", "true,false", "false,true"})
    void bothExplicitFlagsAreRequired(boolean enabled, boolean diagnosticEnabled) {
        context("development").withPropertyValues("kite.trading-read.enabled=" + enabled,
                "kite.trading-read.diagnostic-enabled=" + diagnosticEnabled)
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(KiteTradingReadDiagnosticController.class));
        verifyNoInteractions(orders, trades, positions, holdings, margins);
    }

    @Test void enabledDevelopmentContextSerializesPlatformTypesAndOptionalFields() {
        var instrumentId = new InstrumentId(UUID.fromString("8075a14e-6ec1-4f3f-bf6b-4dd7e324f055"));
        var order = new BrokerOrder("order-1", Optional.of("exchange-1"), Optional.empty(), instrumentId,
                Side.BUY, OrderType.LIMIT, Product.DELIVERY, Validity.DAY, Variety.REGULAR, OrderStatus.OPEN,
                1, 0, 1, 0, 0, new BigDecimal("100.25"), BigDecimal.ZERO, BigDecimal.ZERO,
                Instant.parse("2026-09-20T04:00:00Z"), Optional.empty(), Optional.empty());
        when(orders.orders()).thenReturn(List.of(order));
        context("development").withPropertyValues(
                "kite.trading-read.enabled=true", "kite.trading-read.diagnostic-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(KiteTradingReadDiagnosticController.class);
                    verifyNoInteractions(orders, trades, positions, holdings, margins);
                    MockMvcBuilders.webAppContextSetup(context).build().perform(local(get(BASE + "/orders")))
                            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                            .andExpect(jsonPath("$[0].instrumentId.value").value(instrumentId.value().toString()))
                            .andExpect(jsonPath("$[0].status").value("OPEN"))
                            .andExpect(jsonPath("$[0].price").value(100.25))
                            .andExpect(jsonPath("$[0].exchangeOrderId").value("exchange-1"))
                            .andExpect(jsonPath("$[0].parentOrderId").isEmpty())
                            .andExpect(jsonPath("$[0].orderedAt").value("2026-09-20T04:00:00Z"))
                            .andExpect(jsonPath("$[0].accessToken").doesNotExist())
                            .andExpect(jsonPath("$[0].instrument_token").doesNotExist());
                    verify(orders).orders();
                    verifyNoInteractions(trades, positions, holdings, margins);
                });
    }

    @Test void allFiveManualRoutesReturnProviderSnapshotsWithoutCachingOrPolling() throws Exception {
        when(orders.orders()).thenReturn(List.of());
        when(trades.trades()).thenReturn(List.of());
        when(positions.positions()).thenReturn(new BrokerPositions(List.of(), List.of()));
        when(holdings.holdings()).thenReturn(List.of());
        when(margins.margins()).thenReturn(emptyBalances());
        verifyNoInteractions(orders, trades, positions, holdings, margins);
        for (String route : List.of("orders", "trades", "positions", "holdings", "margins")) {
            http.perform(local(get(BASE + "/" + route)))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("Pragma", "no-cache"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
        verify(orders).orders();
        verify(trades).trades();
        verify(positions).positions();
        verify(holdings).holdings();
        verify(margins).margins();
        verifyNoMoreInteractions(orders, trades, positions, holdings, margins);
    }

    @ParameterizedTest @ValueSource(strings = {"127.0.0.1", "127.2.3.4", "::1", "0:0:0:0:0:0:0:1"})
    void acceptsNumericLoopbackWithoutDnsLookups(String address) throws Exception {
        when(orders.orders()).thenReturn(List.of());
        http.perform(get(BASE + "/orders").with(request -> { request.setRemoteAddr(address); return request; }))
                .andExpect(status().isOk());
        verify(orders).orders();
    }

    @ParameterizedTest @ValueSource(strings = {"192.0.2.1", "localhost", "127.0.0.1.example", "127.0.0.256",
            "127.0.0.01", "::", "2001:db8::1", ""})
    void rejectsNonloopbackAndMalformedPeersEvenWithForgedForwardedHeaders(String address) throws Exception {
        http.perform(get(BASE + "/orders").with(request -> { request.setRemoteAddr(address); return request; })
                        .header("X-Forwarded-For", "127.0.0.1").header("Forwarded", "for=127.0.0.1"))
                .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("LOOPBACK_REQUIRED"));
        verifyNoInteractions(orders, trades, positions, holdings, margins);
    }

    @ParameterizedTest @CsvSource({"localhost,localhost", "localhost,localhost:8080",
            "127.0.0.1,127.0.0.1:8080", "::1,[::1]", "[::1],[::1]:8080"})
    void permitsLocalHostNamesAndNumericLoopbackHosts(String serverName, String host) throws Exception {
        when(orders.orders()).thenReturn(List.of());
        http.perform(local(get(BASE + "/orders")).header("Host", host)
                        .with(request -> { request.setServerName(serverName); return request; }))
                .andExpect(status().isOk());
        verify(orders).orders();
    }

    @ParameterizedTest @ValueSource(strings = {"attacker.example", "localhost.attacker.example", "192.0.2.1"})
    void rejectsNonlocalServerNameOnLoopbackPeerEvenForSameOriginFetches(String serverName) throws Exception {
        // MockHttpServletRequest derives serverName from Host when present; omit Host to test this branch.
        http.perform(local(get(BASE + "/orders"))
                        .header("Sec-Fetch-Site", "same-origin")
                        .with(request -> {
                            request.setServerName(serverName);
                            assertThat(request.getServerName()).isEqualTo(serverName);
                            return request;
                        }))
                .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("LOCAL_HOST_REQUIRED"));
        verifyNoInteractions(orders, trades, positions, holdings, margins);
    }

    @ParameterizedTest @ValueSource(strings = {"attacker.example", "localhost.attacker.example:8080",
            "localhost:65536", "localhost:0", "localhost:", "[::1]:bad", "localhost,attacker.example"})
    void rejectsNonlocalOrMalformedRawHostOnLoopbackPeerEvenForSameOriginFetches(String host) throws Exception {
        http.perform(local(get(BASE + "/orders")).header("Host", host)
                        .header("Sec-Fetch-Site", "same-origin"))
                .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("LOCAL_HOST_REQUIRED"));
        verifyNoInteractions(orders, trades, positions, holdings, margins);
    }

    @Test void rejectsDuplicateHostHeaders() throws Exception {
        http.perform(local(get(BASE + "/orders")).header("Host", "localhost", "localhost"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("LOCAL_HOST_REQUIRED"));
        verifyNoInteractions(orders, trades, positions, holdings, margins);
    }

    @ParameterizedTest @ValueSource(strings = {"Forwarded", "X-Forwarded-For", "X-Forwarded-Host",
            "X-Forwarded-Proto", "X-Forwarded-Port"})
    void rejectsProxyHeadersEvenOnOtherwiseLocalRequests(String header) throws Exception {
        http.perform(local(get(BASE + "/orders")).header(header, "127.0.0.1"))
                .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("PROXY_NOT_ALLOWED"));
        verifyNoInteractions(orders, trades, positions, holdings, margins);
    }

    @ParameterizedTest @ValueSource(strings = {"https://example.invalid", "http://localhost", "null", ""})
    void rejectsEveryOriginHeader(String origin) throws Exception {
        http.perform(local(get(BASE + "/orders")).header("Origin", origin))
                .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
        verifyNoInteractions(orders, trades, positions, holdings, margins);
    }

    @ParameterizedTest @ValueSource(strings = {"cross-site", "same-site", "unexpected"})
    void rejectsCrossSiteBrowserRequests(String fetchSite) throws Exception {
        http.perform(local(get(BASE + "/orders")).header("Sec-Fetch-Site", fetchSite))
                .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("CROSS_SITE_NOT_ALLOWED"));
        verifyNoInteractions(orders, trades, positions, holdings, margins);
    }

    @ParameterizedTest @ValueSource(strings = {"none", "same-origin"})
    void permitsLocalBrowserNavigationWithoutOrigin(String fetchSite) throws Exception {
        when(orders.orders()).thenReturn(List.of());
        http.perform(local(get(BASE + "/orders")).header("Sec-Fetch-Site", fetchSite))
                .andExpect(status().isOk());
        verify(orders).orders();
    }

    @ParameterizedTest @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE"})
    void everyReadRouteRejectsOtherMethodsWithoutInvokingProviders(String method) throws Exception {
        for (String route : List.of("orders", "trades", "positions", "holdings", "margins"))
            http.perform(local(request(HttpMethod.valueOf(method), BASE + "/" + route)))
                    .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(orders, trades, positions, holdings, margins);
    }

    @ParameterizedTest @CsvSource({"CONFIGURATION,0,503", "AUTHENTICATION,403,401", "BROKER_API,429,502",
            "BROKER_API,500,502", "TRANSPORT,0,502", "INVALID_RESPONSE,0,502"})
    void failuresExposeOnlySafeCategoriesAndBrokerStatus(String category, int brokerStatus, int expected) throws Exception {
        when(orders.orders()).thenThrow(new BrokerReadException(BrokerReadException.Category.valueOf(category), brokerStatus));
        http.perform(local(get(BASE + "/orders"))).andExpect(status().is(expected))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"category\":\"" + category + "\",\"httpStatus\":" + brokerStatus + "}", true));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 200, 600})
    void invalidBrokerStatusIsNotExposed(int brokerStatus) throws Exception {
        when(orders.orders()).thenThrow(new BrokerReadException(BrokerReadException.Category.BROKER_API, brokerStatus));
        http.perform(local(get(BASE + "/orders"))).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.httpStatus").value(0));
    }

    @Test void unexpectedFailuresNeverExposeOrLogUpstreamMessages(CapturedOutput output) throws Exception {
        String sentinel = "sensitive-broker-response-sentinel";
        when(orders.orders()).thenThrow(new IllegalStateException(sentinel));
        http.perform(local(get(BASE + "/orders"))).andExpect(status().isBadGateway())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"category\":\"INVALID_RESPONSE\",\"httpStatus\":0}", true));
        assertThat(output.getAll()).doesNotContain(sentinel);
    }

    private WebApplicationContextRunner context(String... profiles) {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class,
                        JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class))
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
                .withUserConfiguration(KiteTradingReadDiagnosticController.class)
                .withBean(BrokerOrdersProvider.class, () -> orders)
                .withBean(BrokerTradesProvider.class, () -> trades)
                .withBean(BrokerPositionsProvider.class, () -> positions)
                .withBean(BrokerHoldingsProvider.class, () -> holdings)
                .withBean(BrokerMarginsProvider.class, () -> margins);
    }

    private static MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> { request.setRemoteAddr("127.0.0.1"); return request; });
    }

    private static BrokerMargins emptyBalances() {
        var zero = BigDecimal.ZERO;
        var available = new BrokerMargins.AvailableMargin(zero, zero, zero, zero, zero, zero);
        var utilised = new BrokerMargins.UtilisedMargin(zero, zero, zero, zero, zero, zero,
                zero, zero, zero, zero, zero, zero);
        var segment = new BrokerMargins.SegmentMargin(false, zero, available, utilised);
        return new BrokerMargins(Map.of(MarginSegment.EQUITY, segment, MarginSegment.COMMODITY, segment));
    }
}
