package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.infrastructure.*;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.http.*;
import static com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission.RISK_READ;
import static org.junit.jupiter.api.Assertions.*;

/** Static configuration and intercepted synthetic transport only; no provider request. */
class EntraDevelopmentAuthenticationConfigurationTest {
    @TempDir Path directory;
    private static final String ISSUER = "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000001/v2.0";
    private EntraDevelopmentAuthenticationProperties properties(String callback) throws Exception {
        byte[] approval = ("{\"issuer\":\"" + ISSUER + "\",\"clientId\":\"fixture-client\",\"subject\":\"fixture-subject\","
                + "\"authenticationContext\":\"c1\",\"policyVersion\":\"fixture-v1\",\"enforcedAuthenticationStrengthReviewed\":true}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path file = directory.resolve("approval.json"); Files.write(file, approval);
        return new EntraDevelopmentAuthenticationProperties(true, ISSUER, "fixture-client", "fixture-subject", callback,
                "c1", file.toString(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(approval)),
                directory.resolve("external-client").toString(), "jdbc:postgresql://127.0.0.1:5432/phase1447o_fixture",
                "fixture_user", directory.resolve("external-db").toString(), "fixture-v1", "fixture-account",
                Set.of("fixture-account"), Set.of(RISK_READ), Duration.ofMinutes(30), Duration.ofMinutes(10), Duration.ofMinutes(5));
    }
    private MockEnvironment environment() {
        var env = new MockEnvironment().withProperty("server.address","127.0.0.1")
                .withProperty("server.ssl.enabled","true").withProperty("server.port","8443")
                .withProperty("server.servlet.session.cookie.secure","true")
                .withProperty("server.servlet.session.cookie.http-only","true")
                .withProperty("server.servlet.session.cookie.same-site","lax")
                .withProperty("server.forward-headers-strategy","none").withProperty("spring.flyway.enabled","false");
        env.setActiveProfiles("development","entra-development"); return env;
    }
    private String callback() { return "https://localhost:8443" + EntraDevelopmentAuthenticationController.ROOT + "/callback"; }
    @Test void disabledByDefaultRegistersNoAuthenticationComponents() {
        new ApplicationContextRunner().withUserConfiguration(EntraDevelopmentAuthenticationConfiguration.class)
                .run(c -> { assertNull(c.getStartupFailure()); assertEquals(0,c.getBeansOfType(EntraOperatorAuthenticationAdapter.class).size()); });
    }
    @Test void productionProfileRejectsDevelopmentActivation() throws Exception {
        var p=properties(callback());var e=environment();e.setActiveProfiles("development","entra-development","production");
        assertThrows(IllegalArgumentException.class,()->p.validate(e));
        new ApplicationContextRunner().withUserConfiguration(EntraDevelopmentAuthenticationConfiguration.class)
                .withPropertyValues("operator.entra-development.enabled=true")
                .withInitializer(c->c.getEnvironment().setActiveProfiles("production"))
                .run(c->assertNotNull(c.getStartupFailure()));
    }
    @Test void missingOrMismatchedTenantApprovalFailsClosed() throws Exception {
        var p=properties(callback());p.validate(environment());Files.writeString(Path.of(p.approvalFile()),"{}");
        assertThrows(IllegalArgumentException.class,()->p.validate(environment()));
        Files.delete(Path.of(p.approvalFile()));assertThrows(IllegalArgumentException.class,()->p.validate(environment()));
    }
    @Test void registrationRequiresExactIssuerClientAndHttpsCallback() throws Exception {
        var p=properties(callback());var t=new EntraDevelopmentOidcTransport(p.policy(),Clock.systemUTC());
        var r=t.registration("synthetic-only");assertEquals(ISSUER,r.getProviderDetails().getIssuerUri());
        assertEquals("fixture-client",r.getClientId());assertEquals(callback(),r.getRedirectUri());
        var bad=properties("http://localhost:8443"+EntraDevelopmentAuthenticationController.ROOT+"/callback");
        assertThrows(IllegalArgumentException.class,()->bad.validate(environment()));
    }
    @Test void clientCredentialReferenceIsExternalAndRedacted() throws Exception {
        var p=properties(callback());assertFalse(Files.exists(Path.of(p.clientCredentialFile())));
        assertFalse(p.toString().contains(p.clientCredentialFile()));assertFalse(p.toString().contains(p.subject()));
        assertThrows(UnsupportedOperationException.class,()->p.accounts().add("other"));
        // Validation checks references/policy without loading a client credential or touching the provider.
        p.validate(environment());
    }
    @Test void tokenTransportEnforcesEndpointTimeoutAndRedirectPolicy() throws Exception {
        URI endpoint=URI.create(ISSUER.replace("/v2.0","/oauth2/v2.0/token"));
        var guard=new EntraDevelopmentOidcTransport.Guard(endpoint,HttpMethod.POST,Clock.systemUTC(),Duration.ZERO,32);
        var request=new MockClientHttpRequest(HttpMethod.POST,endpoint);
        assertThrows(java.io.IOException.class,()->guard.intercept(request,new byte[0],
                (r,b)->new MockClientHttpResponse(new byte[0],HttpStatus.FOUND)));
        assertThrows(java.io.IOException.class,()->guard.intercept(request,new byte[0],
                (r,b)->new MockClientHttpResponse(new byte[33],HttpStatus.OK)));
        assertThrows(java.io.IOException.class,()->guard.intercept(new MockClientHttpRequest(HttpMethod.POST,
                URI.create("https://example.invalid/token")),new byte[0],(r,b)->{fail("Unexpected request");return null;}));
    }
    @Test void jwksRefreshIsBoundedAndUnknownKeysFailClosed() throws Exception {
        URI endpoint=URI.create(ISSUER.replace("/v2.0","/discovery/v2.0/keys"));
        var guard=new EntraDevelopmentOidcTransport.Guard(endpoint,HttpMethod.GET,Clock.systemUTC(),Duration.ofSeconds(30),128);
        var request=new MockClientHttpRequest(HttpMethod.GET,endpoint);int[] calls={0};
        assertThrows(java.io.IOException.class,()->guard.intercept(request,new byte[0],(r,b)->{calls[0]++;throw new java.io.IOException("outage");}));
        assertThrows(java.io.IOException.class,()->guard.intercept(request,new byte[0],(r,b)->{calls[0]++;return null;}));assertEquals(1,calls[0]);
        var rest=new org.springframework.web.client.RestTemplate();
        var server=org.springframework.test.web.client.MockRestServiceServer.bindTo(rest).build();
        server.expect(org.springframework.test.web.client.ExpectedCount.manyTimes(),
                org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(endpoint.toString()))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{\"keys\":[]}",MediaType.APPLICATION_JSON));
        var key=java.security.KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var jwt=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID("unknown-fixture-key").build(),
                new com.nimbusds.jwt.JWTClaimsSet.Builder().subject("fixture").expirationTime(Date.from(Instant.now().plusSeconds(60))).build());
        jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner((java.security.interfaces.RSAPrivateKey)key.getPrivate()));
        var decoder=org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withJwkSetUri(endpoint.toString()).restOperations(rest).build();
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class,()->decoder.decode(jwt.serialize()));
        server.verify();
    }
    @Test void realProviderSmokePrerequisitesAreCheckedWithoutNetwork() throws Exception {
        var p=properties(callback());p.validate(environment());
        for(String key:List.of("server.ssl.enabled","server.servlet.session.cookie.secure","server.servlet.session.cookie.http-only")){
            var e=environment().withProperty(key,"false");assertThrows(IllegalArgumentException.class,()->p.validate(e));}
        assertThrows(IllegalArgumentException.class,()->p.validate(environment().withProperty("server.forward-headers-strategy","framework")));
        assertThrows(IllegalArgumentException.class,()->p.validate(environment().withProperty("spring.flyway.enabled","true")));
    }
}
