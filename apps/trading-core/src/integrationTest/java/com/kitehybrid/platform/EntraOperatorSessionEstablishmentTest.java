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

/** Real synthetic RSA verification and disposable PostgreSQL; token transport remains mocked. */
class EntraOperatorSessionEstablishmentTest {
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
            URI.create("https://localhost/__oidc_fixture/callback"),Set.of("fixture-subject"),Set.of("RS256"),
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
            callback.setScheme("https");callback.setServerName("localhost");callback.setServerPort(443);
            callback.setRequestURI("/__oidc_fixture/callback");callback.setMethod("GET");
            var request=requests.begin(callback);callback.setParameter("state",request.getState());callback.setParameter("code","synthetic-code");

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
        assertEquals(14,flyway.migrate().migrationsExecuted);System.out.println("PHASE_N_MIGRATION_14_PUBLIC_HISTORY_PASS");
    }
    private static OperatorSessionRegistryPort.Session stored(String handle){
        return new PostgresOperatorSessionRegistry(source,Clock.fixed(NOW,ZoneOffset.UTC)).find(OperatorSessionLifecycle.hashHandle(handle)).orElseThrow().session();}
    /** Test-only transparent JDBC coordination: both real transactions must reach owner acquisition.
     * The production owner lock remains responsible for serialization, not this fixture. */
    private static final class ConcurrentConnections {
        final CountDownLatch atOwnerBoundary=new CountDownLatch(2);
        final Set<Integer> backendPids=ConcurrentHashMap.newKeySet();
        final AtomicInteger opened=new AtomicInteger(), closed=new AtomicInteger(), arrivals=new AtomicInteger();
        final AtomicInteger invariantObservations=new AtomicInteger();
        final javax.sql.DataSource coordinated=mock(javax.sql.DataSource.class);
        ConcurrentConnections() throws SQLException {
            when(coordinated.getConnection()).thenAnswer(invocation->open());
        }
        private Connection open() throws SQLException {
            Connection raw=source.getConnection();opened.incrementAndGet();
            try(var query=raw.createStatement()){
                query.setQueryTimeout(5);
                try(var rows=query.executeQuery("SELECT pg_backend_pid()")){
                    assertTrue(rows.next());assertTrue(backendPids.add(rows.getInt(1)),"Independent backend required");
                }
            }catch(SQLException | RuntimeException | Error failure){
                try{raw.close();}finally{closed.incrementAndGet();}
                throw failure;
            }
            AtomicBoolean released=new AtomicBoolean();
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},
                (proxy,method,args)->{
                    if(method.getName().equals("close")){
                        try{return invoke(raw,method,args);}
                        finally{
                            assertTrue(raw.isClosed(),"Physical JDBC connection must be closed");
                            if(released.compareAndSet(false,true))closed.incrementAndGet();
                        }
                    }
                    if(method.getName().equals("commit")){
                        assertOneActive(raw);
                        Object result=invoke(raw,method,args);
                        assertOneActive(raw);
                        return result;
                    }
                    Object result=invoke(raw,method,args);
                    if(method.getName().equals("prepareStatement") && args[0] instanceof String query
                            && query.startsWith("INSERT INTO trading.operator_session_owners(")){
                        PreparedStatement statement=(PreparedStatement)result;
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                            new Class<?>[]{PreparedStatement.class},(statementProxy,operation,values)->{
                                if(operation.getName().equals("executeUpdate")){
                                    assertFalse(raw.getAutoCommit(),"Real transaction must already be open");
                                    assertOneActive(raw);
                                    assertTrue(arrivals.incrementAndGet()<=2,"Only two establishment boundaries");
                                    atOwnerBoundary.countDown();
                                    try{assertTrue(atOwnerBoundary.await(3,TimeUnit.SECONDS),"Both transactions must overlap");}
                                    catch(InterruptedException interrupted){
                                        Thread.currentThread().interrupt();throw new SQLException("Fixture coordination interrupted");
                                    }
                                }
                                return invoke(statement,operation,values);
                            });
                    }
                    return result;
                });
        }
        private static Object invoke(Object target,Method method,Object[] args) throws Throwable {
            try{return method.invoke(target,args);}
            catch(InvocationTargetException failure){throw failure.getCause();}
        }
        private void assertOneActive(Connection connection) throws SQLException {
            try(var query=connection.prepareStatement(
                    "SELECT count(*) FROM trading.operator_sessions WHERE issuer=? AND subject=? AND NOT revoked")){
                query.setQueryTimeout(5);query.setString(1,ISSUER);query.setString(2,"fixture-subject");
                try(var rows=query.executeQuery()){assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
            }
            invariantObservations.incrementAndGet();
        }
    }
    private static long usableSessionsAt(Instant instant){
        return sql.queryForObject("""
            SELECT count(*) FROM trading.operator_sessions
            WHERE issuer=? AND subject=? AND NOT revoked AND absolute_expires_at>?
            AND last_activity_at + inactivity_seconds * interval '1 second'>?
            """,Long.class,ISSUER,"fixture-subject",Timestamp.from(instant),Timestamp.from(instant));
    }
    @Test void migrationUsesExistingV14AndPublicHistory(){flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
        assertEquals("14",flyway.info().current().getVersion().toString());}
    @Test void verifiedExchangeEstablishesDurableExactSession() throws Exception {var f=new Fixture();var result=f.adapter().authenticate(f.callback);
        assertEquals(EntraOperatorAuthenticationAdapter.Status.DIAGNOSTIC_SESSION_ESTABLISHED,result.status());assertFalse(result.productionAuthorized());
        var row=stored(result.sessionHandle());assertEquals(ISSUER,row.event().issuer());assertEquals("fixture-subject",row.event().subject());
        assertEquals("fixture-client",row.event().audience());assertEquals(NOW,row.event().authenticatedAt());assertFalse(row.revoked());}
    @Test void consumedCallbackCannotCreateSecondRegistryRow() throws Exception {var f=new Fixture();var adapter=f.adapter();var first=adapter.authenticate(f.callback);
        long before=sql.queryForObject("SELECT count(*) FROM trading.operator_sessions",Long.class);
        assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,adapter.authenticate(f.callback).status());
        assertEquals(before,sql.queryForObject("SELECT count(*) FROM trading.operator_sessions",Long.class));assertFalse(stored(first.sessionHandle()).revoked());

        // Labelled subcase: consumed callback after original absolute expiry cannot revive its grant.
        var original=stored(first.sessionHandle());Instant absoluteExpiry=original.absoluteExpiresAt();
        f.clock.advanceTo(absoluteExpiry.plusSeconds(1));
        var replay=adapter.authenticate(f.callback);
        assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,replay.status());
        assertNull(replay.sessionHandle());assertFalse(replay.productionAuthorized());assertEquals(1,f.exchanges);
        var lifecycle=new OperatorSessionLifecycle(f.registry,f.clock,f.policy.localSession());
        var renewal=lifecycle.renew(first.sessionHandle());
        assertFalse(renewal.diagnosticReady());assertFalse(renewal.productionAuthorized());
        var access=lifecycle.assess(first.sessionHandle(),"GET","/api/trading/risk/status","fixture-account");
        assertFalse(access.diagnosticReady());assertFalse(access.productionAuthorized());
        var afterExpiry=stored(first.sessionHandle());
        assertEquals(original,afterExpiry);assertEquals(absoluteExpiry,afterExpiry.absoluteExpiresAt());
        assertFalse(afterExpiry.activeAt(f.clock.instant()));assertEquals(0,usableSessionsAt(f.clock.instant()));
        assertEquals(before,sql.queryForObject("SELECT count(*) FROM trading.operator_sessions",Long.class));
        System.out.println("PHASE_N_ABSOLUTE_EXPIRY_REPLAY_RENEWAL_PASS");

        // Labelled subcase: inactivity expires independently, before the absolute-expiry boundary.
        var inactive=new Fixture();var inactiveAdapter=inactive.adapter();var login=inactiveAdapter.authenticate(inactive.callback);
        assertEquals(EntraOperatorAuthenticationAdapter.Status.DIAGNOSTIC_SESSION_ESTABLISHED,login.status());
        var originalInactive=stored(login.sessionHandle());
        long beforeInactivity=sql.queryForObject("SELECT count(*) FROM trading.operator_sessions",Long.class);
        inactive.clock.advanceTo(originalInactive.lastActivityAt().plus(originalInactive.inactivity()).plusSeconds(1));
        assertTrue(inactive.clock.instant().isBefore(originalInactive.absoluteExpiresAt()));
        assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,inactiveAdapter.authenticate(inactive.callback).status());
        assertEquals(1,inactive.exchanges);
        var inactiveLifecycle=new OperatorSessionLifecycle(inactive.registry,inactive.clock,inactive.policy.localSession());
        assertFalse(inactiveLifecycle.renew(login.sessionHandle()).diagnosticReady());
        var inactiveAccess=inactiveLifecycle.assess(login.sessionHandle(),"GET","/api/trading/risk/status","fixture-account");
        assertFalse(inactiveAccess.diagnosticReady());assertFalse(inactiveAccess.productionAuthorized());
        assertEquals(originalInactive,stored(login.sessionHandle()));assertFalse(stored(login.sessionHandle()).activeAt(inactive.clock.instant()));
        assertEquals(0,usableSessionsAt(inactive.clock.instant()));
        assertEquals(beforeInactivity,sql.queryForObject("SELECT count(*) FROM trading.operator_sessions",Long.class));
        System.out.println("PHASE_N_INACTIVITY_REPLAY_RENEWAL_PASS");
    }
    @Test void freshAuthenticationRevokesPreviousSessionAtomically() throws Exception {var first=new Fixture();var one=first.adapter().authenticate(first.callback);
        var second=new Fixture();var two=second.adapter().authenticate(second.callback);assertTrue(stored(one.sessionHandle()).revoked());assertFalse(stored(two.sessionHandle()).revoked());
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM trading.operator_sessions WHERE issuer=? AND subject=? AND NOT revoked",Integer.class,ISSUER,"fixture-subject"));

        // Labelled subcase: two verified attempts overlap at owner acquisition on independent transactions.
        assertTrue(sql.queryForObject("""
            SELECT indisunique AND indisvalid AND indisready
            FROM pg_index WHERE indexrelid='trading.operator_one_active_session'::regclass
            """,Boolean.class));
        assertTrue(sql.queryForObject("""
            SELECT pg_get_expr(indpred,indrelid) FROM pg_index
            WHERE indexrelid='trading.operator_one_active_session'::regclass
            """,String.class).contains("NOT revoked"));
        var connections=new ConcurrentConnections();var left=new Fixture();var right=new Fixture();
        left.registry=new PostgresOperatorSessionRegistry(connections.coordinated,left.clock);
        right.registry=new PostgresOperatorSessionRegistry(connections.coordinated,right.clock);
        var leftAdapter=left.adapter();var rightAdapter=right.adapter();
        long before=sql.queryForObject("SELECT count(*) FROM trading.operator_sessions",Long.class);
        var pool=Executors.newFixedThreadPool(2);
        EntraOperatorAuthenticationAdapter.Result leftResult,rightResult;
        try{
            var leftFuture=pool.submit(()->leftAdapter.authenticate(left.callback));
            var rightFuture=pool.submit(()->rightAdapter.authenticate(right.callback));
            leftResult=leftFuture.get(12,TimeUnit.SECONDS);rightResult=rightFuture.get(12,TimeUnit.SECONDS);
        }finally{
            pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS),"No unfinished fixture threads");
            assertEquals(connections.opened.get(),connections.closed.get(),"No leaked JDBC connections");
        }
        assertEquals(2,connections.opened.get());assertEquals(2,connections.backendPids.size());
        assertEquals(2,connections.arrivals.get());assertEquals(0,connections.atOwnerBoundary.getCount());
        assertEquals(6,connections.invariantObservations.get(),"Both boundaries and both sides of each commit observed");
        assertEquals(EntraOperatorAuthenticationAdapter.Status.DIAGNOSTIC_SESSION_ESTABLISHED,leftResult.status());
        assertEquals(EntraOperatorAuthenticationAdapter.Status.DIAGNOSTIC_SESSION_ESTABLISHED,rightResult.status());
        assertFalse(leftResult.productionAuthorized());assertFalse(rightResult.productionAuthorized());
        var leftRow=stored(leftResult.sessionHandle());var rightRow=stored(rightResult.sessionHandle());
        assertNotEquals(leftRow.revoked(),rightRow.revoked());
        for(var row:List.of(leftRow,rightRow)){
            assertEquals(Set.of(RISK_READ),row.permissions());assertEquals(Set.of("fixture-account"),row.accounts());
            assertEquals(NOW.plusSeconds(1800),row.absoluteExpiresAt());assertEquals(ISSUER,row.event().issuer());
            assertEquals("fixture-subject",row.event().subject());assertEquals("fixture-client",row.event().audience());
        }
        String superseded=leftRow.revoked()?leftResult.sessionHandle():rightResult.sessionHandle();
        String active=leftRow.revoked()?rightResult.sessionHandle():leftResult.sessionHandle();
        var lifecycle=new OperatorSessionLifecycle(new PostgresOperatorSessionRegistry(source,first.clock),first.clock,first.policy.localSession());
        assertFalse(lifecycle.assess(superseded,"GET","/api/trading/risk/status","fixture-account").diagnosticReady());
        assertFalse(lifecycle.renew(superseded).diagnosticReady());
        assertFalse(lifecycle.rotate(superseded,"x".repeat(48)).diagnosticReady());
        var permitted=lifecycle.assess(active,"GET","/api/trading/risk/status","fixture-account");
        assertTrue(permitted.diagnosticReady());assertFalse(permitted.productionAuthorized());
        assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,leftAdapter.authenticate(left.callback).status());
        assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,rightAdapter.authenticate(right.callback).status());
        assertEquals(1,left.exchanges);assertEquals(1,right.exchanges);
        assertEquals(before+2,sql.queryForObject("SELECT count(*) FROM trading.operator_sessions",Long.class));
        assertEquals(1,usableSessionsAt(NOW));assertTrue(stored(two.sessionHandle()).revoked());
        assertTrue(stored(superseded).revoked());
        System.out.println("PHASE_N_CONCURRENT_ESTABLISHMENT_PASS");
    }
    @Test void tokenClaimsCannotExpandOriginalGrantOrExpiry() throws Exception {var f=new Fixture();f.claims=b->b.claim("roles",List.of("ORDER_EXECUTION")).claim("accounts",List.of("other-account"));
        var result=f.adapter().authenticate(f.callback);var row=stored(result.sessionHandle());assertEquals(Set.of(RISK_READ),row.permissions());assertEquals(Set.of("fixture-account"),row.accounts());
        assertEquals(NOW.plusSeconds(1800),row.absoluteExpiresAt());var lifecycle=new OperatorSessionLifecycle(f.registry,f.clock,f.policy.localSession());
        assertFalse(lifecycle.assess(result.sessionHandle(),"GET","/api/trading/risk/status","other-account").diagnosticReady());
        assertTrue(lifecycle.logout(result.sessionHandle()).diagnosticReady());assertTrue(stored(result.sessionHandle()).revoked());}
    @Test void registryFailureAndInvalidSignatureCreateNoSession() throws Exception {long before=sql.queryForObject("SELECT count(*) FROM trading.operator_sessions",Long.class);
        var invalid=new Fixture();invalid.badSignature=true;assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,invalid.adapter().authenticate(invalid.callback).status());
        var f=new Fixture();var unavailable=mock(javax.sql.DataSource.class);when(unavailable.getConnection()).thenThrow(new java.sql.SQLException("private fixture failure"));
        f.registry=new PostgresOperatorSessionRegistry(unavailable,f.clock);assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,f.adapter().authenticate(f.callback).status());
        assertEquals(before,sql.queryForObject("SELECT count(*) FROM trading.operator_sessions",Long.class));}
}
