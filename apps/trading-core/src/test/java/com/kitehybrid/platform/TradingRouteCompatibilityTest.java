package com.kitehybrid.platform;

import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationUseCase;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptUseCase;
import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteAuthenticationController;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteAuthenticationProperties;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteTradingReadDiagnosticController;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Existing controller controls with mocked ports; no broker or production application startup. */
class TradingRouteCompatibilityTest {
    @Test void kiteLoginRetainsNonceCookieAndFixedCallbackOrigin() throws Exception {
        try (var context = TradingRouteSecurityTest.context(true)) {
            var auth = mock(KiteAuthenticationUseCase.class);
            var state = mock(KiteAuthenticationUseCase.Status.class);
            when(auth.status()).thenReturn(state);
            when(state.code()).thenReturn("KITE_AUTH_REQUIRED");
            var attempts = mock(KiteLoginAttemptUseCase.class);
            when(attempts.start()).thenReturn("synthetic-nonce");
            when(auth.loginUrl("synthetic-nonce")).thenReturn("https://example.invalid/fixture-login");
            var controller = new KiteAuthenticationController(auth,
                    new KiteAuthenticationProperties("http://localhost/api/broker/kite/auth/callback", ""), attempts);
            var http = MockMvcBuilders.standaloneSetup(controller)
                    .apply(springSecurity(context.getBean(FilterChainProxy.class))).build();
            http.perform(get("/api/broker/kite/auth/login").servletPath("/api/broker/kite/auth/login")
                    .with(request -> { request.setServerPort(80); return request; }))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "https://example.invalid/fixture-login"))
                    .andExpect(cookie().httpOnly("KITE_LOGIN_NONCE", true));
            verify(attempts).start();
            verify(auth).loginUrl("synthetic-nonce");
        }
    }

    @Test void malformedKiteCallbackAndResetStillUseExistingControls() throws Exception {
        try (var context = TradingRouteSecurityTest.context(true)) {
            var auth = mock(KiteAuthenticationUseCase.class);
            var attempts = mock(KiteLoginAttemptUseCase.class);
            var controller = new KiteAuthenticationController(auth,
                    new KiteAuthenticationProperties("http://localhost/api/broker/kite/auth/callback", ""), attempts);
            var http = MockMvcBuilders.standaloneSetup(controller)
                    .apply(springSecurity(context.getBean(FilterChainProxy.class))).build();
            http.perform(get("/api/broker/kite/auth/callback").servletPath("/api/broker/kite/auth/callback"))
                    .andExpect(status().isBadRequest());
            http.perform(post("/api/broker/kite/auth/reset").servletPath("/api/broker/kite/auth/reset"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("KITE_RESET_HEADER_REQUIRED"));
            verifyNoInteractions(auth, attempts);
        }
    }

    @Test void diagnosticOriginAndMutationGuardsRemainResponsible() throws Exception {
        try (var context = TradingRouteSecurityTest.context(true)) {
            var orders = mock(BrokerOrdersProvider.class);
            var trades = mock(BrokerTradesProvider.class);
            var positions = mock(BrokerPositionsProvider.class);
            var holdings = mock(BrokerHoldingsProvider.class);
            var margins = mock(BrokerMarginsProvider.class);
            var controller = new KiteTradingReadDiagnosticController(orders, trades, positions, holdings, margins);
            var http = MockMvcBuilders.standaloneSetup(controller)
                    .apply(springSecurity(context.getBean(FilterChainProxy.class))).build();
            String path = "/api/development/trading-read/orders";
            http.perform(get(path).servletPath(path).header("Origin", "https://example.invalid"))
                    .andExpect(status().isForbidden());
            http.perform(post(path).servletPath(path)).andExpect(status().isMethodNotAllowed());
            verifyNoInteractions(orders, trades, positions, holdings, margins);
        }
    }
}
