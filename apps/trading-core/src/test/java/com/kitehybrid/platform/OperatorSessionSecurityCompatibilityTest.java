package com.kitehybrid.platform;

import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationUseCase;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptUseCase;
import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteAuthenticationController;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteAuthenticationProperties;
import com.kitehybrid.platform.broker.infrastructure.kite.KiteTradingReadDiagnosticController;
import com.kitehybrid.platform.trading.application.*;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal;
import com.kitehybrid.platform.trading.infrastructure.TradingFoundationController;
import com.kitehybrid.platform.trading.infrastructure.TradingReadConfiguration;
import com.kitehybrid.platform.trading.infrastructure.TradingReadController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static com.kitehybrid.platform.trading.application.OperatorSessionVerificationPort.*;
import static com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission.RISK_READ;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fixture lifecycle only. No real credential verifier and no protected Trading read activation.
 * Fake session checks return empty diagnostic responses, never projections or authority.
 */
class OperatorSessionSecurityCompatibilityTest {
    private static final String ROOT = "/__phase_l_fixture";
    private static final Instant START = Instant.parse("2026-10-20T00:00:00Z");
    private static final List<String> READS = List.of("/api/trading/instruments", "/api/trading/strategies",
            "/api/trading/qualifications", "/api/trading/risk/status");

    private static final class MutableClock extends Clock {
        Instant value = START;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(value, zone); }
        @Override public Instant instant() { return value; }
    }
    private record Lease(String event, Instant authenticatedAt, Instant originalExpiry, Instant lastActivity) {}
    private static final class FixtureState {
        final MutableClock clock = new MutableClock();
        final Map<String, Lease> sessions = new HashMap<>();
        final HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        final TradingProjectionService projections = mock(TradingProjectionService.class);
        final KiteAuthenticationUseCase broker = mock(KiteAuthenticationUseCase.class);
        Set<OperatorPrincipal.Permission> permissions = Set.of(RISK_READ);
        Set<String> accounts = Set.of("fixture-account");
        String selectedAccount = "fixture-account", endpoint = "/api/trading/risk/status";
        OperatorIdentityProvider.Revocation revocation = OperatorIdentityProvider.Revocation.NOT_REVOKED;
        boolean extendAbsolute;
        int renewals;
        // Deliberate fixture marker, not a password or independently verified credential.
        final AuthenticationManager fixtureVerifier = request -> {
            if (!"fixture-operator".equals(request.getName()) || !"fixture-accepted".equals(request.getCredentials()))
                throw new BadCredentialsException("Fixture rejected");
            return UsernamePasswordAuthenticationToken.authenticated("fixture-operator", null, List.of());
        };
        void remove(HttpServletRequest request) {
            var session = request.getSession(false);
            if (session != null) sessions.remove(session.getId());
        }
        OperatorSessionReadiness.Result assess(HttpServletRequest request) {
            var session = request.getSession(false);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            Lease lease = session == null ? null : sessions.get(session.getId());
            if (lease == null || auth == null || !auth.isAuthenticated() || !"fixture-operator".equals(auth.getName()))
                return new OperatorSessionReadiness.Result(OperatorSessionReadiness.Reason.SESSION_UNAVAILABLE);
            var binding = new SessionBinding(session.getId(), lease.event());
            var principal = new OperatorPrincipal("fixture-issuer", "fixture-subject",
                    OperatorPrincipal.Provenance.UNVERIFIED_CLAIM, permissions, lease.authenticatedAt(), lease.originalExpiry());
            var captured = new OperatorIdentityProvider.SessionEvidence(principal, "a".repeat(64),
                    lease.authenticatedAt(), lease.originalExpiry(), revocation, clock.instant());
            var original = new OriginalGrant(lease.event(), lease.authenticatedAt(), lease.originalExpiry(),
                    Set.of(RISK_READ), Set.of("fixture-account"));
            var verification = new Verification(binding, principal.issuer(), principal.subject(), "fixture-audience",
                    captured.evidenceSha256(), Trust.VERIFIED_ASSERTION, CredentialKind.OPERATOR, lease.authenticatedAt(),
                    extendAbsolute ? lease.originalExpiry().plusSeconds(1) : lease.originalExpiry(),
                    lease.lastActivity(), lease.lastActivity().plusSeconds(120), clock.instant(),
                    revocation, "fixture-revocation", clock.instant(), permissions, accounts, original);
            var policy = new OperatorSessionReadiness.Policy("fixture-issuer", "fixture-audience", "fixture-revocation",
                    "GET", endpoint, selectedAccount, Set.of("fixture-account"), Set.of(RISK_READ),
                    Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(120));
            return new OperatorSessionReadiness(() -> Optional.of(captured),
                    supplied -> binding.equals(supplied) ? Optional.of(verification) : Optional.empty(),
                    clock, policy, binding).assess();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({TradingReadConfiguration.class, TradingReadController.class, TradingFoundationController.class})
    private static class FixtureWeb {
        @Bean FixtureController fixtureController(FixtureState state) { return new FixtureController(state); }
        @Bean @Order(0)
        SecurityFilterChain fixtureLifecycle(HttpSecurity http, FixtureState state) throws Exception {
            http.securityMatcher(new AntPathRequestMatcher(ROOT + "/**"));
            // Only the fixture controller's state machine can return a diagnostic match.
            http.authorizeHttpRequests(rules -> rules.anyRequest().permitAll());
            http.securityContext(context -> context.securityContextRepository(state.repository));
            http.httpBasic(basic -> basic.disable()); http.formLogin(form -> form.disable());
            http.requestCache(cache -> cache.disable());
            http.logout(logout -> logout.logoutUrl(ROOT + "/logout")
                    .addLogoutHandler((request, response, authentication) -> state.remove(request))
                    .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("operator_session")
                    .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
            // CSRF remains on for every unsafe fixture request, including login/renewal/logout.
            return http.build();
        }
    }

    @RestController
    private static final class FixtureController {
        private final FixtureState state;
        FixtureController(FixtureState state) { this.state = state; }
        @PostMapping(ROOT + "/login")
        ResponseEntity<Void> login(@RequestParam("fixtureCredential") String marker,
                                   HttpServletRequest request, HttpServletResponse response) {
            Authentication auth;
            try {
                auth = state.fixtureVerifier.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated("fixture-operator", marker));
            } catch (BadCredentialsException rejected) { return ResponseEntity.status(401).build(); }
            var session = request.getSession(true);
            state.sessions.remove(session.getId());
            new ChangeSessionIdAuthenticationStrategy().onAuthentication(auth, request, response);
            Instant now = state.clock.instant();
            state.sessions.put(session.getId(), new Lease(UUID.randomUUID().toString(), now, now.plusSeconds(600), now));
            var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            state.repository.saveContext(context, request, response);
            return ResponseEntity.noContent().build();
        }
        @GetMapping(ROOT + "/probe")
        ResponseEntity<Void> probe(HttpServletRequest request, HttpServletResponse response) {
            return evaluate(request, response, false);
        }
        @PostMapping(ROOT + "/renew")
        ResponseEntity<Void> renew(HttpServletRequest request, HttpServletResponse response) {
            return evaluate(request, response, true);
        }
        private ResponseEntity<Void> evaluate(HttpServletRequest request, HttpServletResponse response, boolean renew) {
            var outcome = state.assess(request);
            if (outcome.productionAuthorized()) throw new AssertionError("Diagnostic result granted production access");
            if (!outcome.diagnosticReady()) {
                state.remove(request);
                new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
                return ResponseEntity.status(403).build();
            }
            if (renew) {
                var session = request.getSession(false); var lease = state.sessions.get(session.getId());
                // Only last activity changes. Authentication event, entitlements and absolute deadline cannot expand.
                state.sessions.put(session.getId(), new Lease(lease.event(), lease.authenticatedAt(),
                        lease.originalExpiry(), state.clock.instant()));
                state.renewals++;
            }
            return ResponseEntity.noContent().build();
        }
    }
    private static final class Harness implements AutoCloseable {
        final FixtureState state = new FixtureState();
        final AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        final MockMvc http;
        Harness(boolean publicStatus) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture",
                    Map.of("trading.foundation-api.enabled", Boolean.toString(publicStatus))));
            context.register(FixtureWeb.class);
            context.addBeanFactoryPostProcessor(factory -> {
                factory.registerSingleton("fixtureState", state);
                factory.registerSingleton("tradingProjectionService", state.projections);
            });
            context.refresh();
            http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        }
        MockHttpSession login() throws Exception {
            var session = new MockHttpSession(context.getServletContext());
            http.perform(post(ROOT + "/login").servletPath(ROOT + "/login").session(session).with(csrf())
                    .param("fixtureCredential", "fixture-accepted")).andExpect(status().isNoContent());
            return session;
        }
        @Override public void close() { context.close(); SecurityContextHolder.clearContext(); }
    }
    private static MockHttpSession replay(Harness h, String previousId) {
        // MockMvc does not resolve browser cookies to container sessions. Recreate the old
        // identifier and even a forged saved context: authoritative registry removal must win.
        var replay = new MockHttpSession(h.context.getServletContext(), previousId);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("fixture-operator", null, List.of()));
        replay.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return replay;
    }

    @Test void anonymousRequestsDenyBeforeProjectionQuery() throws Exception {
        try (var h = new Harness(true)) {
            for (String path : READS) h.http.perform(get(path).servletPath(path)).andExpect(status().isUnauthorized());
            var direct = h.context.getBean(TradingReadController.class);
            assertEquals(401, direct.instruments().getStatusCode().value());
            assertEquals(401, direct.strategies().getStatusCode().value());
            assertEquals(401, direct.qualifications().getStatusCode().value());
            assertEquals(401, direct.riskStatus().getStatusCode().value());
            verifyNoInteractions(h.state.projections);
        }
    }
    @Test void invalidCredentialsCannotEstablishFixtureSession() throws Exception {
        try (var h = new Harness(true)) {
            var session = new MockHttpSession();
            h.http.perform(post(ROOT + "/login").servletPath(ROOT + "/login").session(session).with(csrf())
                    .param("fixtureCredential", "fixture-rejected")).andExpect(status().isUnauthorized());
            assertTrue(h.state.sessions.isEmpty());
            assertNull(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
            verifyNoInteractions(h.state.projections);
        }
    }
    @Test void forgedSpringAuthenticationCannotGrantTradingAccess() throws Exception {
        try (var h = new Harness(true)) {
            var forged = UsernamePasswordAuthenticationToken.authenticated("fixture-operator", null, List.of());
            for (String path : READS) h.http.perform(get(path).servletPath(path).with(authentication(forged)))
                    .andExpect(status().isForbidden());
            h.http.perform(get(ROOT + "/probe").servletPath(ROOT + "/probe").with(authentication(forged)))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(h.state.projections);
        }
    }
    @Test void brokerTokensAndSyntheticPrincipalsRemainDenied() throws Exception {
        try (var h = new Harness(true)) {
            var synthetic = new OperatorPrincipal("fixture", "fixture-subject", OperatorPrincipal.Provenance.SYNTHETIC_FIXTURE,
                    Set.of(OperatorPrincipal.Permission.values()), START.minusSeconds(1), START.plusSeconds(600));
            var auth = UsernamePasswordAuthenticationToken.authenticated(synthetic, null, List.of());
            for (String path : READS) {
                h.http.perform(get(path).servletPath(path).header("Authorization", "Bearer fixture-broker"))
                        .andExpect(status().isUnauthorized());
                h.http.perform(get(path).servletPath(path).with(authentication(auth))).andExpect(status().isForbidden());
            }
            verifyNoInteractions(h.state.projections);
        }
    }
    @Test void fixtureSessionRotatesIdentifierAndPersistsOnlyVerifiedBinding() throws Exception {
        try (var h = new Harness(true)) {
            var session = new MockHttpSession(h.context.getServletContext()); String oldId = session.getId();
            h.http.perform(post(ROOT + "/login").servletPath(ROOT + "/login").session(session).with(csrf())
                    .param("fixtureCredential", "fixture-accepted")).andExpect(status().isNoContent());
            assertNotEquals(oldId, session.getId()); assertFalse(h.state.sessions.containsKey(oldId));
            assertNotNull(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
            SecurityContextHolder.clearContext();
            h.http.perform(get(ROOT + "/probe").servletPath(ROOT + "/probe").session(session)).andExpect(status().isNoContent());
            for (String path : READS) h.http.perform(get(path).servletPath(path).session(session)).andExpect(status().isForbidden());
            verifyNoInteractions(h.state.projections);
        }
    }
    @Test void fixtureSessionExpiryAndRevocationInvalidateAccess() throws Exception {
        for (String mode : List.of("absolute", "idle", "revoked", "unknown", "permission", "account")) {
            try (var h = new Harness(true)) {
                var session = h.login(); String id = session.getId();
                switch (mode) {
                    case "absolute" -> h.state.clock.value = START.plusSeconds(600);
                    case "idle" -> h.state.clock.value = START.plusSeconds(120);
                    case "revoked" -> h.state.revocation = OperatorIdentityProvider.Revocation.REVOKED;
                    case "unknown" -> h.state.revocation = OperatorIdentityProvider.Revocation.UNKNOWN;
                    case "permission" -> h.state.endpoint = "/api/trading/instruments";
                    case "account" -> h.state.selectedAccount = "other-account";
                    default -> fail(mode);
                }
                h.http.perform(get(ROOT + "/probe").servletPath(ROOT + "/probe").session(session)).andExpect(status().isForbidden());
                assertTrue(session.isInvalid(), mode); assertTrue(h.state.sessions.isEmpty(), mode);
                var replayed = replay(h, id);
                h.http.perform(get(ROOT + "/probe").servletPath(ROOT + "/probe").session(replayed)
                        .cookie(new jakarta.servlet.http.Cookie("operator_session", id)))
                        .andExpect(status().isForbidden());
                assertTrue(replayed.isInvalid(), mode);
                verifyNoInteractions(h.state.projections);
            }
        }
    }
    @Test void csrfFailurePreventsFixtureSessionMutation() throws Exception {
        try (var h = new Harness(true)) {
            h.http.perform(post(ROOT + "/login").servletPath(ROOT + "/login")
                    .param("fixtureCredential", "fixture-accepted")).andExpect(status().isForbidden());
            assertTrue(h.state.sessions.isEmpty());
            var session = h.login();
            for (String path : List.of(ROOT + "/renew", ROOT + "/logout"))
                h.http.perform(post(path).servletPath(path).session(session).with(csrf().useInvalidToken()))
                        .andExpect(status().isForbidden());
            assertEquals(0, h.state.renewals); assertFalse(session.isInvalid()); assertEquals(1, h.state.sessions.size());
        }
    }
    @Test void logoutInvalidatesFixtureSessionWithoutResettingKite() throws Exception {
        try (var h = new Harness(true)) {
            var session = h.login(); String id = session.getId();
            h.http.perform(post(ROOT + "/logout").servletPath(ROOT + "/logout").session(session).with(csrf()))
                    .andExpect(status().isNoContent());
            assertTrue(session.isInvalid()); assertFalse(h.state.sessions.containsKey(id));
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            var replayed = replay(h, id);
            h.http.perform(get(ROOT + "/probe").servletPath(ROOT + "/probe").session(replayed)
                    .cookie(new jakarta.servlet.http.Cookie("operator_session", id))).andExpect(status().isForbidden());
            assertTrue(replayed.isInvalid());
            verifyNoInteractions(h.state.broker, h.state.projections);
        }
    }
    @Test void renewalCannotEscalateFixtureSessionAuthority() throws Exception {
        try (var h = new Harness(true)) {
            var session = h.login(); Lease original = h.state.sessions.get(session.getId());
            h.state.clock.value = START.plusSeconds(30);
            h.http.perform(post(ROOT + "/renew").servletPath(ROOT + "/renew").session(session).with(csrf()))
                    .andExpect(status().isNoContent());
            Lease renewed = h.state.sessions.get(session.getId());
            assertEquals(original.event(), renewed.event()); assertEquals(original.originalExpiry(), renewed.originalExpiry());
            assertEquals(START.plusSeconds(30), renewed.lastActivity());
        }
        for (String expansion : List.of("permission", "account", "expiry")) {
            try (var h = new Harness(true)) {
                var session = h.login();
                switch (expansion) {
                    case "permission" -> h.state.permissions = Set.of(RISK_READ, OperatorPrincipal.Permission.ORDERS_READ);
                    case "account" -> h.state.accounts = Set.of("fixture-account", "other-account");
                    case "expiry" -> h.state.extendAbsolute = true;
                    default -> fail(expansion);
                }
                h.http.perform(post(ROOT + "/renew").servletPath(ROOT + "/renew").session(session).with(csrf()))
                        .andExpect(status().isForbidden());
                assertEquals(0, h.state.renewals); assertTrue(session.isInvalid()); verifyNoInteractions(h.state.projections);
            }
        }
    }
    @Test void publicStatusRetainsExistingBehavior() throws Exception {
        try (var h = new Harness(true)) {
            h.http.perform(get("/api/trading/status").servletPath("/api/trading/status")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.operationalReadiness").value("NO_GO"))
                    .andExpect(jsonPath("$.liveOrderPlacement").value("DISABLED"));
            h.http.perform(head("/api/trading/status").servletPath("/api/trading/status")).andExpect(status().isUnauthorized());
            verifyNoInteractions(h.state.projections);
        }
        try (var h = new Harness(false)) {
            h.http.perform(get("/api/trading/status").servletPath("/api/trading/status")).andExpect(status().isNotFound());
        }
    }
    @Test void mockedKiteCallbackAndDiagnosticsRetainControls() throws Exception {
        try (var h = new Harness(true)) {
            var attempts = mock(KiteLoginAttemptUseCase.class);
            var auth = new KiteAuthenticationController(h.state.broker,
                    new KiteAuthenticationProperties("http://localhost/api/broker/kite/auth/callback", ""), attempts);
            var filters = h.context.getBean(FilterChainProxy.class);
            var http = MockMvcBuilders.standaloneSetup(auth).apply(springSecurity(filters)).build();
            http.perform(get("/api/broker/kite/auth/callback").servletPath("/api/broker/kite/auth/callback"))
                    .andExpect(status().isBadRequest());
            http.perform(post("/api/broker/kite/auth/reset").servletPath("/api/broker/kite/auth/reset"))
                    .andExpect(status().isForbidden());
            var orders = mock(BrokerOrdersProvider.class); var trades = mock(BrokerTradesProvider.class);
            var positions = mock(BrokerPositionsProvider.class); var holdings = mock(BrokerHoldingsProvider.class);
            var margins = mock(BrokerMarginsProvider.class);
            var diagnostics = MockMvcBuilders.standaloneSetup(new KiteTradingReadDiagnosticController(
                    orders, trades, positions, holdings, margins)).apply(springSecurity(filters)).build();
            String path = "/api/development/trading-read/orders";
            diagnostics.perform(get(path).servletPath(path).header("Origin", "https://example.invalid")).andExpect(status().isForbidden());
            diagnostics.perform(post(path).servletPath(path)).andExpect(status().isMethodNotAllowed());
            verifyNoInteractions(h.state.broker, attempts, orders, trades, positions, holdings, margins, h.state.projections);
        }
    }
    @Test void productionConfigurationRegistersNoOperatorProviderAndDeniesMutations() throws Exception {
        // The existing production configuration is inspected without FixtureWeb.
        try (var context = TradingRouteSecurityTest.context(true)) {
            assertTrue(context.getBeansOfType(OperatorIdentityProvider.class).isEmpty());
            assertTrue(context.getBeansOfType(OperatorSessionVerificationPort.class).isEmpty());
            assertTrue(context.getBeansOfType(OperatorSessionReadiness.class).isEmpty());
            assertTrue(context.getBeansOfType(FixtureController.class).isEmpty());
            var http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            for (String path : READS) {
                http.perform(post(path).servletPath(path).with(user("fixture").roles("ADMIN")).with(csrf())).andExpect(status().isForbidden());
                http.perform(delete(path).servletPath(path).with(user("fixture").roles("ADMIN")).with(csrf())).andExpect(status().isForbidden());
            }
        }
        assertEquals(0, OperatorSessionReadiness.class.getDeclaredAnnotations().length);
        assertTrue(Arrays.stream(OperatorSessionVerificationPort.class.getDeclaredAnnotations())
                .noneMatch(a -> a.annotationType().getName().startsWith("org.springframework.")));
    }
}
