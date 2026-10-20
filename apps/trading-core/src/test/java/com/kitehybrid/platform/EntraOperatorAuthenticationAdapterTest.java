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

/** Real in-process RSA signing/verification, mocked token transport and registry; no provider calls. */
class EntraOperatorAuthenticationAdapterTest {
    private static final Instant NOW=Instant.parse("2026-10-20T00:00:00Z");
    private static final String ISSUER="https://login.microsoftonline.com/00000000-0000-0000-0000-000000000001/v2.0";
    private static final class Fixture {
        final Clock clock=Clock.fixed(NOW,ZoneOffset.UTC);
        final OperatorSessionRegistryPort registry=mock(OperatorSessionRegistryPort.class);
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
            when(registry.establish(any())).thenAnswer(a->{established=a.getArgument(0);return true;});
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
        void denied(){assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,adapter().authenticate(callback).status());verifyNoInteractions(registry);}
    }
    @Test void validSignatureEstablishesOriginalGrant() throws Exception {var f=new Fixture();var r=f.adapter().authenticate(f.callback);
        assertEquals(EntraOperatorAuthenticationAdapter.Status.DIAGNOSTIC_SESSION_ESTABLISHED,r.status());assertFalse(r.productionAuthorized());
        assertEquals(f.policy.localSession().permissions(),f.established.permissions());assertEquals(f.policy.localSession().accounts(),f.established.accounts());
        assertEquals(OperatorSessionLifecycle.hashHandle(r.sessionHandle()),f.established.digest());assertEquals(NOW,f.established.event().authenticatedAt());}
    @Test void invalidSignatureIsRejected() throws Exception {var f=new Fixture();f.badSignature=true;f.denied();}
    @Test void unapprovedSigningAlgorithmIsRejected() throws Exception {var f=new Fixture();f.symmetric=true;f.denied();}
    @Test void issuerAudienceAndAuthorizedPartyAreExact() throws Exception {
        for(int i=0;i<3;i++){var f=new Fixture();int n=i;f.claims=b->{if(n==0)b.issuer("https://untrusted.invalid");if(n==1)b.audience("other");if(n==2)b.claim("azp","other");};f.denied();}}
    @Test void nonceAndStateAreBoundToSingleTransaction() throws Exception {var f=new Fixture();f.claims=b->b.claim("nonce","wrong");f.denied();
        var g=new Fixture();g.callback.setParameter("state","x".repeat(43));g.denied();assertEquals(0,g.exchanges);}
    @Test void unauthorizedSubjectCannotUseEmailAsAuthority() throws Exception {var f=new Fixture();f.claims=b->b.subject("other").claim("email","fixture-subject");f.denied();}
    @Test void authenticationStrengthRequiresApprovedContext() throws Exception {var f=new Fixture();f.claims=b->b.claim("acrs",List.of("c2")).claim("amr",List.of("mfa"));f.denied();}
    @Test void expiredAndFutureAuthenticationFailClosed() throws Exception {for(long seconds:new long[]{-301,1}){var f=new Fixture();f.claims=b->b.claim("auth_time",NOW.plusSeconds(seconds).getEpochSecond());f.denied();}
        var f=new Fixture();f.claims=b->b.expirationTime(Date.from(NOW));f.denied();}
    @Test void providerOutageConsumesTransactionWithoutSession() throws Exception {var f=new Fixture();f.outage=true;var a=f.adapter();assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,a.authenticate(f.callback).status());
        assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,a.authenticate(f.callback).status());assertEquals(1,f.exchanges);verifyNoInteractions(f.registry);}
    @Test void registryFailureNeverGrantsAccess() throws Exception {var f=new Fixture();when(f.registry.establish(any())).thenThrow(new IllegalStateException("private fixture detail"));
        var r=f.adapter().authenticate(f.callback);assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,r.status());assertNull(r.sessionHandle());assertFalse(r.productionAuthorized());assertFalse(r.toString().contains("private"));}
    @Test void callbackReplayCannotEstablishAnotherSession() throws Exception {var f=new Fixture();var a=f.adapter();assertEquals(EntraOperatorAuthenticationAdapter.Status.DIAGNOSTIC_SESSION_ESTABLISHED,a.authenticate(f.callback).status());
        assertEquals(EntraOperatorAuthenticationAdapter.Status.DENIED,a.authenticate(f.callback).status());verify(f.registry,times(1)).establish(any());assertEquals(1,f.exchanges);}
    @Test void policyAndDiagnosticResultsAreImmutableAndRedacted() throws Exception {var f=new Fixture();assertThrows(UnsupportedOperationException.class,()->f.policy.subjects().add("other"));
        String old=f.callback.getSession().getId();var result=f.adapter().authenticate(f.callback);assertNotEquals(old,f.callback.getSession().getId());
        assertFalse(result.toString().contains(result.sessionHandle()));assertFalse(f.policy.toString().contains("fixture-subject"));
        assertTrue(Arrays.stream(EntraOperatorAuthenticationAdapter.class.getMethods()).filter(m->m.getName().equals("authenticate"))
            .allMatch(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{jakarta.servlet.http.HttpServletRequest.class})));}
}
