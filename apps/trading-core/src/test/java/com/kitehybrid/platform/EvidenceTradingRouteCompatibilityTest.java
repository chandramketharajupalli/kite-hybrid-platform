package com.kitehybrid.platform;

import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationUseCase;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptUseCase;
import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteAuthenticationController;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteAuthenticationProperties;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteTradingReadDiagnosticController;
import com.kitehybrid.platform.trading.application.TradingProjectionService;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import com.kitehybrid.platform.trading.domain.TradingProjection;
import com.kitehybrid.platform.trading.infrastructure.EvidenceTradingProjectionAdapter;
import com.kitehybrid.platform.trading.infrastructure.TradingReadController;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Synthetic application context only. Broker controllers use mocks, never a live transport. */
class EvidenceTradingRouteCompatibilityTest {
    private static final List<String> READS = List.of("/api/trading/instruments", "/api/trading/strategies",
            "/api/trading/qualifications", "/api/trading/risk/status");

    private static AnnotationConfigWebApplicationContext context(EvidenceTradingProjectionAdapterTest.Fixture fixture) {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("synthetic",
                Map.of("trading.foundation-api.enabled", "true")));
        context.register(TradingRouteSecurityTest.Web.class);
        // Test-only injection under the existing service bean name. No production registration.
        context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("tradingProjectionService", fixture.adapter));
        context.refresh();
        assertSame(fixture.adapter, context.getBean(TradingProjectionService.class));
        return context;
    }

    @Test void directHandlersDenyBeforeEvidenceQuery() {
        var f = new EvidenceTradingProjectionAdapterTest.Fixture();
        var controller = new TradingReadController(f.adapter);
        for (var response : List.of(controller.instruments(), controller.strategies(), controller.qualifications(), controller.riskStatus())) {
            assertEquals(401, response.getStatusCode().value());
            assertEquals("no-store", response.getHeaders().getCacheControl());
        }
        verifyNoInteractions(f.queries);
    }
    @Test void anonymousAndBrokerHeadersRemainDenied() throws Exception {
        var f = new EvidenceTradingProjectionAdapterTest.Fixture();
        try (var context = context(f)) {
            var http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            for (String path : READS) http.perform(get(path).servletPath(path).header("Authorization", "Bearer synthetic-broker-token"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("OPERATOR_AUTHENTICATION_REQUIRED"));
            verifyNoInteractions(f.queries);
        }
    }
    @Test void syntheticPrincipalsRemainDenied() throws Exception {
        var f = new EvidenceTradingProjectionAdapterTest.Fixture();
        var principal = new OperatorPrincipal("fixture", "synthetic-subject", OperatorPrincipal.Provenance.SYNTHETIC_FIXTURE,
                Set.of(OperatorPrincipal.Permission.values()), Instant.EPOCH, Instant.MAX);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("trading:instruments:read")));
        try (var context = context(f)) {
            var http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            for (String path : READS) http.perform(get(path).servletPath(path).with(authentication(auth)))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(f.queries);
        }
    }
    @Test void validSnapshotsCannotGrantEndpointAccess() throws Exception {
        var f = new EvidenceTradingProjectionAdapterTest.Fixture();
        f.reads().forEach(p -> { assertInstanceOf(TradingProjection.Available.class, p); assertFalse(p.executionAuthorized()); });
        clearInvocations(f.queries);
        try (var context = context(f)) {
            var http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            for (String path : READS) http.perform(get(path).servletPath(path).with(user("synthetic-operator").roles("ADMIN")))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(f.queries);
        }
    }
    @Test void publicStatusRetainsOptInBehavior() throws Exception {
        try (var context = TradingRouteSecurityTest.context(true)) {
            var http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            http.perform(get("/api/trading/status").servletPath("/api/trading/status"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.operationalReadiness").value("NO_GO"))
                    .andExpect(jsonPath("$.liveOrderPlacement").value("DISABLED"));
            http.perform(head("/api/trading/status").servletPath("/api/trading/status")).andExpect(status().isUnauthorized());
        }
        try (var context = TradingRouteSecurityTest.context(false)) {
            MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                    .perform(get("/api/trading/status").servletPath("/api/trading/status")).andExpect(status().isNotFound());
        }
    }
    @Test void tradingMutationsRemainDenied() throws Exception {
        var f = new EvidenceTradingProjectionAdapterTest.Fixture();
        try (var context = context(f)) {
            var http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            for (String path : List.of("/api/trading/orders", "/api/trading/instruments", "/api/trading/risk/status")) {
                http.perform(post(path).servletPath(path).with(user("synthetic-operator")).with(csrf())).andExpect(status().isForbidden());
                http.perform(delete(path).servletPath(path).with(user("synthetic-operator")).with(csrf())).andExpect(status().isForbidden());
            }
            verifyNoInteractions(f.queries);
        }
    }
    @Test void mockedKiteCallbackAndResetRetainControls() throws Exception {
        try (var context = TradingRouteSecurityTest.context(true)) {
            var auth = mock(KiteAuthenticationUseCase.class); var attempts = mock(KiteLoginAttemptUseCase.class);
            var controller = new KiteAuthenticationController(auth,
                    new KiteAuthenticationProperties("http://localhost/api/broker/kite/auth/callback", ""), attempts);
            var http = MockMvcBuilders.standaloneSetup(controller).apply(springSecurity(context.getBean(FilterChainProxy.class))).build();
            http.perform(get("/api/broker/kite/auth/callback").servletPath("/api/broker/kite/auth/callback"))
                    .andExpect(status().isBadRequest());
            http.perform(post("/api/broker/kite/auth/reset").servletPath("/api/broker/kite/auth/reset"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("KITE_RESET_HEADER_REQUIRED"));
            verifyNoInteractions(auth, attempts);
        }
    }
    @Test void mockedKiteDiagnosticsRetainGuards() throws Exception {
        try (var context = TradingRouteSecurityTest.context(true)) {
            var orders = mock(BrokerOrdersProvider.class); var trades = mock(BrokerTradesProvider.class);
            var positions = mock(BrokerPositionsProvider.class); var holdings = mock(BrokerHoldingsProvider.class);
            var margins = mock(BrokerMarginsProvider.class);
            var controller = new KiteTradingReadDiagnosticController(orders, trades, positions, holdings, margins);
            var http = MockMvcBuilders.standaloneSetup(controller).apply(springSecurity(context.getBean(FilterChainProxy.class))).build();
            String path = "/api/development/trading-read/orders";
            http.perform(get(path).servletPath(path).header("Origin", "https://example.invalid")).andExpect(status().isForbidden());
            http.perform(post(path).servletPath(path)).andExpect(status().isMethodNotAllowed());
            verifyNoInteractions(orders, trades, positions, holdings, margins);
        }
    }
    @Test void adapterIsNotRegisteredByProductionConfiguration() {
        try (var context = TradingRouteSecurityTest.context(true)) {
            assertTrue(context.getBeansOfType(EvidenceTradingProjectionAdapter.class).isEmpty());
            var service = context.getBean(TradingProjectionService.class);
            for (var projection : List.of(service.instruments(), service.strategies(), service.qualifications(), service.riskStatus()))
                assertInstanceOf(TradingProjection.Unavailable.class, projection);
        }
        assertEquals(0, EvidenceTradingProjectionAdapter.class.getDeclaredAnnotations().length);
    }
    @Test void adapterDependenciesExcludeOperationalServices() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.kitehybrid.platform");
        Deque<JavaClass> pending = new ArrayDeque<>(); Set<String> visited = new HashSet<>();
        pending.add(classes.get(EvidenceTradingProjectionAdapter.class));
        while (!pending.isEmpty()) {
            var current = pending.removeFirst(); if (!visited.add(current.getName())) continue;
            for (var dependency : current.getDirectDependenciesFromSelf()) {
                var target = dependency.getTargetClass(); String name = target.getName();
                if (!name.startsWith("com.kitehybrid.platform.")) continue;
                assertFalse(name.contains(".broker.application.") || name.contains(".broker.infrastructure.")
                        || name.contains("RiskService") || name.contains("RefreshInstrumentRegistry")
                        || name.contains("HistoricalData") || name.contains("OrderExecution") || name.contains("EvidenceWriter"), dependency.toString());
                pending.add(target);
            }
        }
        assertTrue(visited.contains("com.kitehybrid.platform.trading.application.EvidenceSnapshotQueryPort"));
        assertFalse(visited.contains("com.kitehybrid.platform.trading.infrastructure.PostgresEvidenceSnapshotQueryAdapter"));
    }
}
