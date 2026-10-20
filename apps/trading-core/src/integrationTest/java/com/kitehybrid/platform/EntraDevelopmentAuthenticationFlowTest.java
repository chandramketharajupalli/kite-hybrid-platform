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
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.lang.reflect.*;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import static com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission.RISK_READ;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual synthetic RSA callback to disposable PostgreSQL. No provider networking. */
class EntraDevelopmentAuthenticationFlowTest {
    private static final Instant NOW=Instant.parse("2026-10-20T00:00:00Z");
    private static final String ISSUER="https://login.microsoftonline.com/00000000-0000-0000-0000-000000000001/v2.0";
    private static final class ControlledClock extends Clock {
        private volatile Instant now=NOW;
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){
            if(!ZoneOffset.UTC.equals(zone))throw new IllegalArgumentException("UTC fixture only");
            return this;
        }
        @Override public Instant instant(){return now;}
        void advanceTo(Instant next){
            if(next.isBefore(now))throw new IllegalArgumentException("Forward-only fixture clock");
            now=next;
        }
    }
    private static final class Fixture {
        final ControlledClock clock=new ControlledClock();
        OperatorSessionRegistryPort registry=new PostgresOperatorSessionRegistry(source,clock);
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

        }
        EntraDevelopmentAuthenticationProperties settings(){return new EntraDevelopmentAuthenticationProperties(true,ISSUER,"fixture-client","fixture-subject",policy.callback().toString(),"c1","unused","a".repeat(64),"unused","jdbc:postgresql://127.0.0.1:5432/phase1447o_fixture","fixture","unused","v1","fixture-account",Set.of("fixture-account"),Set.of(RISK_READ),Duration.ofMinutes(30),Duration.ofMinutes(10),Duration.ofMinutes(5));}
        EntraDevelopmentAuthenticationController controller(){return new EntraDevelopmentAuthenticationController(settings(),requests,adapter(),registry,clock,new org.springframework.security.web.context.HttpSessionSecurityContextRepository());}
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
    }

    private static DriverManagerDataSource source;
    private static JdbcTemplate sql;
    private static Flyway flyway;
    @BeforeAll static void disposableDatabaseOnly(){
        if(!"true".equals(System.getProperty("evidence.integration.authorized")))throw new IllegalStateException("Explicit authorization required");
        String url=System.getProperty("evidence.integration.url","");
        if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/evidence_fixture_[a-z0-9]+"))throw new IllegalStateException("Disposable database required");
        source=new DriverManagerDataSource(url,"evidence_fixture_admin","");sql=new JdbcTemplate(source);sql.setQueryTimeout(5);
        assertEquals(0,sql.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema')",Integer.class));
        flyway=Flyway.configure().dataSource(source).defaultSchema("public").schemas("public")
            .locations("classpath:db/migration","classpath:db/historical","classpath:db/evidence","classpath:db/operator").cleanDisabled(true).load();
        assertEquals(14,flyway.migrate().migrationsExecuted);System.out.println("PHASE_O_MIGRATION_14_PUBLIC_HISTORY_PASS");
    }

    private static OperatorSessionRegistryPort.Session latest(Fixture f){
        String digest=sql.queryForObject("SELECT digest FROM trading.operator_sessions WHERE issuer=? AND subject=? AND NOT revoked",String.class,ISSUER,"fixture-subject");
        return f.registry.find(digest).orElseThrow().session();}
    @AfterEach void clearContext(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    @Test void syntheticCallbackEstablishesDurableOriginalGrant()throws Exception{var f=new Fixture();var c=f.controller();
        assertEquals(303,c.callback(f.callback,new MockHttpServletResponse()).getStatusCode().value());var row=latest(f);
        assertEquals(ISSUER,row.event().issuer());assertEquals("fixture-subject",row.event().subject());assertEquals(Set.of(RISK_READ),row.permissions());
        assertEquals(Set.of("fixture-account"),row.accounts());assertEquals(NOW.plusSeconds(1800),row.absoluteExpiresAt());
        assertEquals(200,c.status(f.callback).getStatusCode().value());assertEquals(false,c.status(f.callback).getBody().get("productionAuthorized"));}
    @Test void replayedCallbackCannotCreateAnotherSession()throws Exception{var f=new Fixture();var c=f.controller();
        assertEquals(303,c.callback(f.callback,new MockHttpServletResponse()).getStatusCode().value());long count=sql.queryForObject("SELECT count(*) FROM trading.operator_sessions",Long.class);
        assertEquals(401,c.callback(f.callback,new MockHttpServletResponse()).getStatusCode().value());assertEquals(1,f.exchanges);
        assertEquals(count,sql.queryForObject("SELECT count(*) FROM trading.operator_sessions",Long.class));}
    @Test void logoutRevocationSurvivesNewApplicationContext()throws Exception{var f=new Fixture();var c=f.controller();
        assertEquals(303,c.callback(f.callback,new MockHttpServletResponse()).getStatusCode().value());String digest=latest(f).digest();
        assertEquals(200,c.logout(f.callback,new MockHttpServletResponse()).getStatusCode().value());
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()){
            context.registerBean(OperatorSessionRegistryPort.class,()->new PostgresOperatorSessionRegistry(source,f.clock));
            context.refresh();var fresh=context.getBean(OperatorSessionRegistryPort.class);
            assertTrue(fresh.find(digest).orElseThrow().session().revoked());
            assertFalse(fresh.find(digest).orElseThrow().session().activeAt(NOW));}}
    @Test void absoluteAndInactivityExpiryPreventDiagnosticAccess()throws Exception{for(long seconds:new long[]{1801,601}){
        var f=new Fixture();var c=f.controller();assertEquals(303,c.callback(f.callback,new MockHttpServletResponse()).getStatusCode().value());var original=latest(f);
        f.clock.advanceTo(NOW.plusSeconds(seconds));assertEquals(401,c.status(f.callback).getStatusCode().value());
        assertEquals(false,c.status(f.callback).getBody().get("productionAuthorized"));assertEquals(original,f.registry.find(original.digest()).orElseThrow().session());
        assertEquals(401,c.callback(f.callback,new MockHttpServletResponse()).getStatusCode().value());assertEquals(1,f.exchanges);}
        System.out.println("PHASE_O_DURABLE_EXPIRY_PASS");}
}
