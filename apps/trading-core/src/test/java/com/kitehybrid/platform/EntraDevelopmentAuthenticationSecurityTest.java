package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.application.*;
import com.kitehybrid.platform.trading.infrastructure.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.net.URI;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import static com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission.RISK_READ;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Real RSA verification, mocked token exchange/registry, actual development controller and filter chain. */
class EntraDevelopmentAuthenticationSecurityTest {
    private static final Instant NOW=Instant.parse("2026-10-20T00:00:00Z");
    private static final String ISSUER="https://login.microsoftonline.com/00000000-0000-0000-0000-000000000001/v2.0";
    private static final String ROOT=EntraDevelopmentAuthenticationController.ROOT;
    private static final class MutableClock extends Clock {
        Instant now=NOW;public Instant instant(){return now;}public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
    }
    private static final class Fixture {
        final MutableClock clock=new MutableClock();
        final OperatorSessionRegistryPort registry=mock(OperatorSessionRegistryPort.class);
        final EntraOperatorAuthenticationPolicy policy=new EntraOperatorAuthenticationPolicy(URI.create(ISSUER),"fixture-client",
            URI.create("https://localhost:8443"+EntraDevelopmentAuthenticationController.ROOT+"/callback"),Set.of("fixture-subject"),Set.of("RS256"),
            Duration.ofMinutes(5),Duration.ZERO,Duration.ofMinutes(2),16,"c1","a".repeat(64),
            new OperatorSessionRegistryPort.Policy(ISSUER,"fixture-subject","fixture-client","v1","fixture-registry",
                Duration.ofMinutes(30),Duration.ofMinutes(10),Duration.ofSeconds(5),Set.of(RISK_READ),Set.of("fixture-account")));
        final OperatorOidcAuthorizationRequestRepository requests=new OperatorOidcAuthorizationRequestRepository(policy,clock,new SecureRandom());
        final MockHttpServletRequest callback=new MockHttpServletRequest();
        final KeyPair key;
        final ClientRegistration registration;
        int exchanges;
        OperatorSessionRegistryPort.Session established;
        Consumer<JWTClaimsSet.Builder> claims=b->{};
        boolean badSignature, symmetric, outage;
        Fixture() throws Exception {
            var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);key=generator.generateKeyPair();
            registration=ClientRegistration.withRegistrationId("fixture").clientId(policy.clientId())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(policy.callback().toString()).scope("openid").authorizationUri(policy.authorizationUri())
                .tokenUri(policy.tokenUri()).issuerUri(ISSUER).userNameAttributeName("sub").build();
            callback.setScheme("https");callback.setServerName("localhost");callback.setServerPort(8443);callback.setSecure(true);callback.setRemoteAddr("127.0.0.1");
            callback.setRequestURI(EntraDevelopmentAuthenticationController.ROOT+"/callback");callback.setMethod("GET");
            var request=requests.begin(callback);callback.setParameter("state",request.getState());callback.setParameter("code","synthetic-code");
            when(registry.establish(any())).thenAnswer(a->{established=a.getArgument(0);return true;});
            when(registry.find(anyString())).thenAnswer(a->established==null?Optional.empty():Optional.of(new OperatorSessionRegistryPort.Observation(established,clock.instant())));
            when(registry.revoke(anyString(),any())).thenAnswer(a->{if(established==null)return false;established=established.changed(established.digest(),established.lastActivityAt(),true);return true;});
        }
        EntraOperatorAuthenticationAdapter adapter() {
            return new EntraOperatorAuthenticationAdapter(policy,requests,registration,grant->{
                exchanges++;if(outage)throw new IllegalStateException("fixture outage");
                try {
                    var b=new JWTClaimsSet.Builder().issuer(ISSUER).subject("fixture-subject").audience("fixture-client")
                        .issueTime(Date.from(NOW)).expirationTime(Date.from(NOW.plusSeconds(120)))
                        .claim("auth_time",NOW.getEpochSecond()).claim("acrs",List.of("c1"))
                        .claim("nonce",grant.getAuthorizationExchange().getAuthorizationRequest().getAttributes().get("nonce"));
                    claims.accept(b);
                    var jwt=new SignedJWT(new JWSHeader(symmetric?JWSAlgorithm.HS256:JWSAlgorithm.RS256),b.build());
                    if(symmetric)jwt.sign(new MACSigner(new byte[32]));
                    else {KeyPair signing=key;if(badSignature){var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);signing=g.generateKeyPair();}
                        jwt.sign(new RSASSASigner((RSAPrivateKey)signing.getPrivate()));}
                    return OAuth2AccessTokenResponse.withToken("synthetic-access-token").tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .additionalParameters(Map.of("id_token",jwt.serialize())).build();
                }catch(Exception failure){throw new IllegalStateException("Synthetic signing failure",failure);}
            },NimbusJwtDecoder.withPublicKey((RSAPublicKey)key.getPublic()).build(),registry,clock,new SecureRandom());
        }
        EntraDevelopmentAuthenticationProperties settings(){return new EntraDevelopmentAuthenticationProperties(true,ISSUER,"fixture-client","fixture-subject",policy.callback().toString(),"c1","unused","a".repeat(64),"unused","jdbc:postgresql://127.0.0.1:5432/phase1447o_fixture","fixture","unused","v1","fixture-account",Set.of("fixture-account"),Set.of(RISK_READ),Duration.ofMinutes(30),Duration.ofMinutes(10),Duration.ofMinutes(5));}
        void denied(){assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,adapter().authenticate(callback).status());verifyNoInteractions(registry);}
    }

    @TestConfiguration(proxyBeanMethods=false) @EnableWebMvc
    @Import({TradingReadConfiguration.class,TradingReadController.class,TradingFoundationController.class})
    static class Web {
        @Bean HttpSessionSecurityContextRepository contexts(){return new HttpSessionSecurityContextRepository();}
        @Bean EntraDevelopmentAuthenticationController controller(Fixture f,HttpSessionSecurityContextRepository contexts){
            return new EntraDevelopmentAuthenticationController(f.settings(),f.requests,f.adapter(),f.registry,f.clock,contexts);}
        @Bean @Order(0) SecurityFilterChain development(HttpSecurity http,Fixture f,HttpSessionSecurityContextRepository contexts)throws Exception{
            return new EntraDevelopmentAuthenticationConfiguration().entraDevelopmentChain(http,f.settings(),contexts);}
    }
    private static final class Harness implements AutoCloseable {
        final Fixture f;final AnnotationConfigWebApplicationContext context=new AnnotationConfigWebApplicationContext();final MockMvc http;
        final TradingProjectionService projections=mock(TradingProjectionService.class);
        Harness(Fixture f){this.f=f;context.setServletContext(new MockServletContext());
            context.getEnvironment().setActiveProfiles("development","entra-development");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture",Map.of("trading.foundation-api.enabled","true","operator.entra-development.enabled","true")));
            context.register(Web.class);context.addBeanFactoryPostProcessor(b->{b.registerSingleton("fixture",f);b.registerSingleton("tradingProjectionService",projections);});
            context.refresh();http=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path,boolean post){
            return (post?post("https://localhost:8443"+path):get("https://localhost:8443"+path)).servletPath(path).secure(true)
                .with(r->{r.setRemoteAddr("127.0.0.1");return r;}).session((MockHttpSession)f.callback.getSession());}
        ResultActions callback()throws Exception{return http.perform(request(ROOT+"/callback",false).param("state",f.callback.getParameter("state")).param("code","synthetic-code"));}
        public void close(){context.close();SecurityContextHolder.clearContext();}
    }
    @Test void syntheticLoginUsesStateNonceAndPkce()throws Exception{try(var h=new Harness(new Fixture())){
        var r=h.http.perform(h.request(ROOT+"/login",true).with(csrf())).andExpect(status().isSeeOther()).andReturn();String url=r.getResponse().getHeader("Location");
        assertTrue(url.startsWith(h.f.policy.authorizationUri()));assertTrue(url.contains("code_challenge_method=S256"));assertTrue(url.contains("nonce="));assertTrue(url.contains("state="));}}
    @Test void invalidIssuerAudienceOrSignatureCannotEstablishSession()throws Exception{for(int i=0;i<3;i++){var f=new Fixture();int n=i;
        f.claims=b->{if(n==0)b.issuer("https://invalid.example");if(n==1)b.audience("other");};f.badSignature=i==2;
        try(var h=new Harness(f)){h.callback().andExpect(status().isUnauthorized());assertNull(f.established);}}}
    @Test void wrongSubjectOrMissingAuthenticationContextIsDenied()throws Exception{for(int i=0;i<2;i++){var f=new Fixture();int n=i;
        f.claims=b->{if(n==0)b.subject("other");else b.claim("acrs",List.of());};try(var h=new Harness(f)){h.callback().andExpect(status().isUnauthorized());assertNull(f.established);}}}
    @Test void invalidStateNonceAndCallbackReplayAreDenied()throws Exception{var f=new Fixture();f.claims=b->b.claim("nonce","wrong");
        try(var h=new Harness(f)){h.callback().andExpect(status().isUnauthorized());assertNull(f.established);}
        try(var h=new Harness(new Fixture())){h.f.callback.setParameter("state","x".repeat(43));h.callback().andExpect(status().isUnauthorized());assertEquals(0,h.f.exchanges);}
        try(var h=new Harness(new Fixture())){h.callback().andExpect(status().isSeeOther());h.callback().andExpect(status().isUnauthorized());assertEquals(1,h.f.exchanges);}}
    @Test void successfulCallbackRotatesSessionAndPersistsDiagnosticContext()throws Exception{try(var h=new Harness(new Fixture())){
        String old=h.f.callback.getSession().getId();h.callback().andExpect(status().isSeeOther());assertNotEquals(old,h.f.callback.getSession().getId());
        var ctx=(org.springframework.security.core.context.SecurityContext)h.f.callback.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(ctx);assertTrue(ctx.getAuthentication().getAuthorities().isEmpty());h.http.perform(h.request(ROOT+"/status",false)).andExpect(status().isOk()).andExpect(jsonPath("$.productionAuthorized").value(false));}}
    @Test void loginAndLogoutRequireCsrf()throws Exception{try(var h=new Harness(new Fixture())){
        h.http.perform(h.request(ROOT+"/login",true)).andExpect(status().isForbidden());h.http.perform(h.request(ROOT+"/logout",true)).andExpect(status().isForbidden());assertNull(h.f.established);}}
    @Test void secureCookiesAndExactCallbackOriginAreEnforced()throws Exception{try(var h=new Harness(new Fixture())){
        h.callback().andExpect(status().isSeeOther());h.http.perform(h.request(ROOT+"/logout",true).with(csrf())).andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Secure; HttpOnly; SameSite=Lax")));}
        try(var h=new Harness(new Fixture())){h.http.perform(h.request(ROOT+"/callback",false).header("Forwarded","host=evil.invalid")).andExpect(status().isServiceUnavailable());assertEquals(0,h.f.exchanges);}}
    @Test void logoutRevokesRegistryAndInvalidatesContext()throws Exception{try(var h=new Harness(new Fixture())){
        h.callback().andExpect(status().isSeeOther());var session=(MockHttpSession)h.f.callback.getSession();
        h.http.perform(h.request(ROOT+"/logout",true).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.localRevocationConfirmed").value(true));assertTrue(h.f.established.revoked());assertTrue(session.isInvalid());}}
    @Test void expiredRevokedOrForgedSessionCannotReadDiagnostics()throws Exception{for(int i=0;i<3;i++){try(var h=new Harness(new Fixture())){
        if(i<2)h.callback().andExpect(status().isSeeOther());if(i==0)h.f.clock.now=NOW.plusSeconds(1801);
        if(i==1)h.f.established=h.f.established.changed(h.f.established.digest(),NOW,true);
        h.http.perform(h.request(ROOT+"/status",false)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.productionAuthorized").value(false));}}}
    @Test void providerAndRegistryOutagesFailClosedWithoutSensitiveOutput()throws Exception{var f=new Fixture();f.outage=true;try(var h=new Harness(f)){h.callback().andExpect(status().isUnauthorized()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-code"))));}
        try(var h=new Harness(new Fixture())){h.callback().andExpect(status().isSeeOther());when(h.f.registry.revoke(anyString(),any())).thenThrow(new IllegalStateException("private"));
            h.http.perform(h.request(ROOT+"/logout",true).with(csrf())).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.localRevocationConfirmed").value(false));}}
    @Test void publicStatusAndMockedKiteRoutesRemainCompatible()throws Exception{try(var h=new Harness(new Fixture())){
        h.http.perform(get("/api/trading/status")).andExpect(status().isOk()).andExpect(jsonPath("$.operationalReadiness").value("NO_GO"));
        var broker=mock(com.kitehybrid.platform.broker.application.auth.KiteAuthenticationUseCase.class);
        var attempts=mock(com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptUseCase.class);
        var http=MockMvcBuilders.standaloneSetup(new com.kitehybrid.platform.broker.infrastructure.kite.KiteAuthenticationController(broker,
            new com.kitehybrid.platform.broker.infrastructure.kite.KiteAuthenticationProperties("http://localhost/api/broker/kite/auth/callback",""),attempts))
            .apply(springSecurity(h.context.getBean(FilterChainProxy.class))).build();
        http.perform(get("/api/broker/kite/auth/callback")).andExpect(status().isBadRequest());http.perform(post("/api/broker/kite/auth/reset")).andExpect(status().isForbidden());verifyNoInteractions(broker,attempts);}}
    @Test void protectedReadsAndTradingMutationsRemainDeniedBeforeQuery()throws Exception{try(var h=new Harness(new Fixture())){
        h.callback().andExpect(status().isSeeOther());for(String path:List.of("/api/trading/instruments","/api/trading/strategies","/api/trading/qualifications","/api/trading/risk/status"))
            h.http.perform(h.request(path,false)).andExpect(status().isForbidden());
        h.http.perform(h.request("/api/trading/orders",true).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(h.projections);}}
}
