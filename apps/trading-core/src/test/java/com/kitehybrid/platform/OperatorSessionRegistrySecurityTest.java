package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.application.*;
import com.kitehybrid.platform.trading.infrastructure.*;
import com.kitehybrid.platform.broker.application.auth.*;
import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.broker.infrastructure.kite.*;
import jakarta.servlet.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static com.kitehybrid.platform.trading.application.OperatorSessionRegistryPort.*;
import static com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission.RISK_READ;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Synthetic lifecycle paths only. Cookie assertions cover fixture responses, not deployed servlet configuration. */
class OperatorSessionRegistrySecurityTest {
    private static final String ROOT="/__phase_m_fixture";
    private static final Instant NOW=Instant.parse("2026-10-20T00:00:00Z");
    private static final class State {
        final Map<String,Session> rows=new HashMap<>();
        final OperatorSessionRegistryPort registry=mock(OperatorSessionRegistryPort.class);
        final HttpSessionSecurityContextRepository contexts=new HttpSessionSecurityContextRepository();
        final TradingProjectionService projections=mock(TradingProjectionService.class);
        final OperatorSessionLifecycle lifecycle;
        State() {
            when(registry.establish(any())).thenAnswer(a->{Session s=a.getArgument(0);rows.put(s.digest(),s);return true;});
            when(registry.find(anyString())).thenAnswer(a->Optional.ofNullable(rows.get(a.getArgument(0))).map(s->new Observation(s,NOW)));
            when(registry.revoke(anyString(),any())).thenAnswer(a->{Session s=rows.get(a.getArgument(0));if(s==null)return false;
                rows.put(s.digest(),s.changed(s.digest(),s.lastActivityAt(),true));return true;});
            lifecycle=new OperatorSessionLifecycle(registry,Clock.fixed(NOW,ZoneOffset.UTC),new Policy("fixture-issuer","fixture-subject",
                "fixture-audience","v1","local-registry",Duration.ofMinutes(30),Duration.ofMinutes(10),Duration.ofSeconds(5),
                Set.of(RISK_READ),Set.of("fixture-account")));
        }
        static String handle(HttpSession session) { return (String)session.getAttribute("fixtureHandle"); }
    }
    @TestConfiguration(proxyBeanMethods=false) @EnableWebMvc
    @Import({TradingReadConfiguration.class,TradingReadController.class,TradingFoundationController.class})
    private static class Web {
        @Bean Controller controller(State state) { return new Controller(state); }
        @Bean @Order(0) SecurityFilterChain fixtureChain(HttpSecurity http,State state) throws Exception {
            http.securityMatcher(new AntPathRequestMatcher(ROOT+"/**"));
            http.authorizeHttpRequests(a->a.anyRequest().permitAll());
            http.securityContext(c->c.securityContextRepository(state.contexts));
            http.httpBasic(b->b.disable());http.formLogin(f->f.disable());http.requestCache(c->c.disable());
            http.logout(l->l.logoutUrl(ROOT+"/logout").addLogoutHandler((req,res,auth)->{
                var s=req.getSession(false);if(s!=null)state.lifecycle.logout(State.handle(s));
            }).invalidateHttpSession(true).clearAuthentication(true).deleteCookies("operator_session")
                .logoutSuccessHandler((req,res,auth)->res.setStatus(204)));
            return http.build();
        }
    }
    @RestController private static class Controller {
        private final State state;
        Controller(State state){this.state=state;}
        @PostMapping(ROOT+"/login") ResponseEntity<Void> login(HttpServletRequest req,HttpServletResponse res) {
            // Test-only verified-event assertion, never credential verification.
            var old=req.getSession(true);String previous=State.handle(old);
            if(previous!=null)state.lifecycle.logout(previous);
            var auth=UsernamePasswordAuthenticationToken.authenticated("fixture-subject",null,List.of());
            new ChangeSessionIdAuthenticationStrategy().onAuthentication(auth,req,res);
            String handle=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
            var result=state.lifecycle.establish(handle,new Event("fixture-issuer","fixture-subject","fixture-audience",
                UUID.randomUUID(),NOW,Trust.VERIFIED_OPERATOR_ASSERTION));
            if(!result.diagnosticReady())return ResponseEntity.status(403).build();
            req.getSession().setAttribute("fixtureHandle",handle);
            var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);
            state.contexts.saveContext(context,req,res);
            return ResponseEntity.noContent().header("Set-Cookie",ResponseCookie.from("operator_session",handle)
                .secure(true).httpOnly(true).sameSite("Lax").path("/").build().toString()).build();
        }
        @GetMapping(ROOT+"/probe") ResponseEntity<Void> probe(HttpServletRequest req) {
            var s=req.getSession(false);String handle=null;int count=0;
            if(req.getCookies()!=null)for(Cookie cookie:req.getCookies())if(cookie.getName().equals("operator_session")){count++;handle=cookie.getValue();}
            if(s==null||count!=1||!Objects.equals(handle,State.handle(s)))return ResponseEntity.status(403).build();
            var auth=SecurityContextHolder.getContext().getAuthentication();
            if(auth==null||!auth.isAuthenticated()||!"fixture-subject".equals(auth.getName()))return ResponseEntity.status(403).build();
            var outcome=state.lifecycle.assess(handle,"GET","/api/trading/risk/status","fixture-account");
            if(outcome.productionAuthorized())throw new AssertionError("Unexpected authority");
            return ResponseEntity.status(outcome.diagnosticReady()?204:403).build();
        }
    }
    private static final class Harness implements AutoCloseable {
        final State state=new State();final AnnotationConfigWebApplicationContext context=new AnnotationConfigWebApplicationContext();
        final MockMvc http;
        Harness(){context.setServletContext(new MockServletContext());context.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("fixture",Map.of("trading.foundation-api.enabled","true")));
            context.register(Web.class);context.addBeanFactoryPostProcessor(f->{f.registerSingleton("fixtureState",state);
                f.registerSingleton("tradingProjectionService",state.projections);});context.refresh();
            http=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
        MockHttpSession login() throws Exception {var s=new MockHttpSession(context.getServletContext());
            http.perform(post(ROOT+"/login").servletPath(ROOT+"/login").session(s).with(csrf())).andExpect(status().isNoContent());return s;}
        ResultActions probe(MockHttpSession s) throws Exception {return http.perform(get(ROOT+"/probe").servletPath(ROOT+"/probe")
            .session(s).cookie(new Cookie("operator_session",State.handle(s))));}
        public void close(){context.close();SecurityContextHolder.clearContext();}
    }
    @Test void forgedOrReplayedSessionIsDenied() throws Exception {
        try(var h=new Harness()) {var s=h.login();String handle=State.handle(s);h.state.lifecycle.logout(handle);
            h.probe(s).andExpect(status().isForbidden());
            var forged=new MockHttpSession();forged.setAttribute("fixtureHandle","Z".repeat(48));
            var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("fixture-subject",null,List.of()));
            forged.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
            h.probe(forged).andExpect(status().isForbidden());verifyNoInteractions(h.state.projections);}
    }
    @Test void sessionFixationIsPrevented() throws Exception {
        try(var h=new Harness()){var s=new MockHttpSession(h.context.getServletContext());String old=s.getId();
            h.http.perform(post(ROOT+"/login").servletPath(ROOT+"/login").session(s).with(csrf())).andExpect(status().isNoContent());
            assertNotEquals(old,s.getId());h.probe(s).andExpect(status().isNoContent());
            String oldHandle=State.handle(s);h.http.perform(post(ROOT+"/login").servletPath(ROOT+"/login").session(s).with(csrf()));
            assertTrue(h.state.rows.get(OperatorSessionLifecycle.hashHandle(oldHandle)).revoked());}
    }
    @Test void contextPersistenceRequiresRegistryBinding() throws Exception {
        try(var h=new Harness()){var s=h.login();assertNotNull(s.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
            SecurityContextHolder.clearContext();h.probe(s).andExpect(status().isNoContent());
            h.state.rows.clear();h.probe(s).andExpect(status().isForbidden());}
    }
    @Test void csrfFailurePreventsLifecycleMutation() throws Exception {
        try(var h=new Harness()){h.http.perform(post(ROOT+"/login").servletPath(ROOT+"/login")).andExpect(status().isForbidden());
            assertTrue(h.state.rows.isEmpty());var s=h.login();h.http.perform(post(ROOT+"/logout").servletPath(ROOT+"/logout").session(s))
                .andExpect(status().isForbidden());h.probe(s).andExpect(status().isNoContent());}
    }
    @Test void logoutInvalidatesRegistryAndContext() throws Exception {
        try(var h=new Harness()){var s=h.login();String handle=State.handle(s);
            h.http.perform(post(ROOT+"/logout").servletPath(ROOT+"/logout").session(s).with(csrf())).andExpect(status().isNoContent());
            assertTrue(s.isInvalid());assertTrue(h.state.rows.get(OperatorSessionLifecycle.hashHandle(handle)).revoked());
            assertNull(SecurityContextHolder.getContext().getAuthentication());verifyNoInteractions(h.state.projections);}
    }
    @Test void cookieSecurityAndDuplicateHandlingAreEnforced() throws Exception {
        try(var h=new Harness()){var s=new MockHttpSession(h.context.getServletContext());
            String cookie=h.http.perform(post(ROOT+"/login").servletPath(ROOT+"/login").session(s).with(csrf()))
                .andExpect(status().isNoContent()).andReturn().getResponse().getHeader("Set-Cookie");
            assertNotNull(cookie);for(String expected:List.of("Secure","HttpOnly","SameSite=Lax","Path=/"))assertTrue(cookie.contains(expected));
            assertFalse(cookie.contains("Domain="));
            h.http.perform(get(ROOT+"/probe").servletPath(ROOT+"/probe").session(s)
                .cookie(new Cookie("operator_session",State.handle(s)),new Cookie("operator_session",State.handle(s))))
                .andExpect(status().isForbidden());}
    }
    @Test void denialPrecedesProjectionAndMutationsRemainDenied() throws Exception {
        try(var h=new Harness()){var s=h.login();for(String path:List.of("/api/trading/instruments","/api/trading/strategies",
                "/api/trading/qualifications","/api/trading/risk/status")){
            h.http.perform(get(path).servletPath(path).session(s)).andExpect(status().isForbidden());
            h.http.perform(post(path).servletPath(path).session(s).with(csrf())).andExpect(status().isForbidden());}
            verifyNoInteractions(h.state.projections);assertTrue(h.context.getBeansOfType(OperatorIdentityProvider.class).isEmpty());}
        for(Class<?> type:List.of(OperatorSessionLifecycle.class,RegistryOperatorIdentityAdapter.class,PostgresOperatorSessionRegistry.class))
            assertEquals(0,type.getDeclaredAnnotations().length);
    }
    @Test void publicStatusAndMockedKiteCompatibilityRemainUnchanged() throws Exception {
        try(var h=new Harness()){
            h.http.perform(get("/api/trading/status").servletPath("/api/trading/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalReadiness").value("NO_GO"));
            var broker=mock(KiteAuthenticationUseCase.class);var attempts=mock(KiteLoginAttemptUseCase.class);
            var filters=h.context.getBean(FilterChainProxy.class);
            var http=MockMvcBuilders.standaloneSetup(new KiteAuthenticationController(broker,
                new KiteAuthenticationProperties("http://localhost/api/broker/kite/auth/callback",""),attempts))
                .apply(springSecurity(filters)).build();
            http.perform(get("/api/broker/kite/auth/callback").servletPath("/api/broker/kite/auth/callback")).andExpect(status().isBadRequest());
            http.perform(post("/api/broker/kite/auth/reset").servletPath("/api/broker/kite/auth/reset")).andExpect(status().isForbidden());
            var orders=mock(BrokerOrdersProvider.class);var trades=mock(BrokerTradesProvider.class);
            var positions=mock(BrokerPositionsProvider.class);var holdings=mock(BrokerHoldingsProvider.class);var margins=mock(BrokerMarginsProvider.class);
            var diagnostics=MockMvcBuilders.standaloneSetup(new KiteTradingReadDiagnosticController(orders,trades,positions,holdings,margins))
                .apply(springSecurity(filters)).build();String path="/api/development/trading-read/orders";
            diagnostics.perform(get(path).servletPath(path).header("Origin","https://example.invalid")).andExpect(status().isForbidden());
            verifyNoInteractions(broker,attempts,orders,trades,positions,holdings,margins,h.state.projections);
        }
    }
}
