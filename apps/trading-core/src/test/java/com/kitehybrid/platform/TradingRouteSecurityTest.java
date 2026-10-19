package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.application.TradingApiAuthorization;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import com.kitehybrid.platform.trading.infrastructure.TradingFoundationController;
import com.kitehybrid.platform.trading.infrastructure.TradingReadConfiguration;
import com.kitehybrid.platform.trading.infrastructure.TradingReadController;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TradingRouteSecurityTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({TradingReadConfiguration.class, TradingReadController.class, TradingFoundationController.class})
    static class Web {}

    static AnnotationConfigWebApplicationContext context(boolean statusEnabled) {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture",
                Map.of("trading.foundation-api.enabled", Boolean.toString(statusEnabled))));
        context.register(Web.class);
        context.refresh();
        return context;
    }

    @Test void anonymousAndBrokerHeadersCannotReadProtectedRoutes() throws Exception {
        try (var context = context(true)) {
            var http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            for (String path : new String[]{"/api/trading/instruments", "/api/trading/strategies",
                    "/api/trading/qualifications", "/api/trading/risk/status"}) {
                http.perform(get(path).servletPath(path).header("Authorization", "Bearer synthetic-broker-token"))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("OPERATOR_AUTHENTICATION_REQUIRED"));
            }
        }
    }

    @Test void syntheticAndArbitraryAuthenticatedPrincipalsRemainDenied() throws Exception {
        try (var context = context(true)) {
            var http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var principal = new OperatorPrincipal("fixture", "subject",
                    OperatorPrincipal.Provenance.SYNTHETIC_FIXTURE,
                    Set.of(OperatorPrincipal.Permission.INSTRUMENTS_READ), Instant.EPOCH, Instant.MAX);
            var auth = new UsernamePasswordAuthenticationToken(principal, null,
                    java.util.List.of(new SimpleGrantedAuthority("trading:instruments:read")));
            http.perform(get("/api/trading/instruments").servletPath("/api/trading/instruments")
                    .with(authentication(auth))).andExpect(status().isForbidden());
            http.perform(get("/api/trading/instruments").servletPath("/api/trading/instruments")
                    .with(user("fixture").authorities(new SimpleGrantedAuthority[0]))).andExpect(status().isForbidden());
            assertThrows(org.springframework.security.core.AuthenticationException.class,
                    () -> context.getBean(AuthenticationManager.class).authenticate(auth));
        }
    }

    @Test void publicStatusRetainsOptInAndHeadIsNotPublic() throws Exception {
        try (var context = context(true)) {
            var http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            http.perform(get("/api/trading/status").servletPath("/api/trading/status"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
            http.perform(head("/api/trading/status").servletPath("/api/trading/status"))
                    .andExpect(status().isUnauthorized());
        }
        try (var context = context(false)) {
            MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                    .perform(get("/api/trading/status").servletPath("/api/trading/status"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test void mutationsAndNoncanonicalTradingPathsNeverGrant() throws Exception {
        try (var context = context(true)) {
            var http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            for (String path : new String[]{"/api/trading/orders", "/api/trading/status",
                    "/api/trading/instruments/", "/api/trading/unknown"}) {
                String servletPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
                http.perform(post(path).servletPath(servletPath).with(csrf()).with(user("fixture")))
                        .andDo(result -> {
                            assertEquals(path, result.getRequest().getRequestURI());
                            assertEquals(servletPath, result.getRequest().getServletPath());
                            if (path.endsWith("/")) assertEquals("/", result.getRequest().getPathInfo());
                            assertTrue(context.getBean(FilterChainProxy.class).getFilterChains().get(0)
                                    .matches(result.getRequest()));
                        })
                        .andExpect(status().isForbidden());
            }
            http.perform(get("/api/trading/instruments/").servletPath("/api/trading/instruments"))
                    .andDo(result -> {
                        assertEquals("/api/trading/instruments/", result.getRequest().getRequestURI());
                        assertEquals("/api/trading/instruments", result.getRequest().getServletPath());
                        assertEquals("/", result.getRequest().getPathInfo());
                        assertTrue(context.getBean(FilterChainProxy.class).getFilterChains().get(0)
                                .matches(result.getRequest()));
                    })
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test void chainMatchesOnlyTradingAndHasExplicitOrder() throws Exception {
        try (var context = context(true)) {
            var chains = context.getBean(FilterChainProxy.class).getFilterChains();
            assertEquals(1, chains.size());
            for (String path : new String[]{"/api/broker/kite/auth/login", "/api/broker/kite/auth/callback",
                    "/api/development/trading-read/orders", "/actuator/health"}) {
                var request = new MockHttpServletRequest("GET", path); request.setServletPath(path);
                assertFalse(chains.get(0).matches(request));
            }
            var method = TradingReadConfiguration.class.getDeclaredMethod("tradingBoundary",
                    org.springframework.security.config.annotation.web.builders.HttpSecurity.class,
                    AuthenticationManager.class);
            assertEquals(1, method.getAnnotation(org.springframework.core.annotation.Order.class).value());
        }
    }

    @Test void endpointPermissionsRemainDistinctWithoutProductionGrant() {
        var auth = new TradingApiAuthorization(Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC));
        var identity = new OperatorPrincipal("fixture", "subject", OperatorPrincipal.Provenance.SYNTHETIC_FIXTURE,
                Set.of(OperatorPrincipal.Permission.INSTRUMENTS_READ), Instant.EPOCH, Instant.MAX);
        assertEquals(TradingApiAuthorization.Reason.MISSING_PERMISSION,
                auth.evaluateSynthetic(identity, "GET", "/api/trading/risk/status").reason());
        assertEquals(OperatorPrincipal.Permission.QUALIFICATIONS_READ,
                TradingApiAuthorization.requiredPermission("GET", "/api/trading/qualifications").orElseThrow());
        assertFalse(auth.evaluateSynthetic(identity, "GET", "/api/trading/instruments").productionAuthorized());
    }
}
