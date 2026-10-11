package com.kitehybrid.synthetic;

import java.io.*;
import java.nio.file.Path;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SyntheticSecurityTest {
    static final Instant NOW=Instant.parse("2000-01-01T00:00:00Z");
    static final String OWNER="SYNTHETIC_OWNER_1", CALLER="SYNTHETIC_CALLER", RECIPIENT="SYNTHETIC_RECIPIENT", ACCOUNT="SYNTHETIC_ACCOUNT";
    static final SyntheticOwner.SinkPolicy SAFE=new SyntheticOwner.SinkPolicy(Set.of("BOUNDED_RESULT"),true);
    static final SyntheticOwner.Ledger DENY=new SyntheticOwner.Ledger(){
        public boolean consume(SyntheticOwner.Request r){return false;}
        public void terminal(UUID id,SyntheticOwner.Outcome o){throw new AssertionError("NO_DISPATCH_ALLOWED");}
    };
    static final class MutableClock extends Clock {
        final AtomicReference<Instant> value=new AtomicReference<>(NOW);
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId z){if(!ZoneOffset.UTC.equals(z))throw new IllegalArgumentException();return this;}
        public Instant instant(){return value.get();}
    }
    static KeyPair key() throws Exception {return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();}
    static SyntheticOwner.Request request(LoopbackPeer peer) {
        return new SyntheticOwner.Request(UUID.randomUUID(),SyntheticOwner.VERSION,OWNER,CALLER,RECIPIENT,ACCOUNT,
                SyntheticOwner.PURPOSE,"GET",peer.endpoint(),NOW,NOW.plusSeconds(20),1,"SYNTHETIC_ONLY");
    }
    static SyntheticHalt.Assertion sign(SyntheticOwner.Request r, KeyPair k) throws Exception {
        return sign(new SyntheticHalt.Assertion("SYNTHETIC_ISSUER","SYNTHETIC_KEY",r.recipient(),r.owner(),r.purpose(),
                r.haltEpoch(),r.issued(),r.expires(),UUID.randomUUID(),r.fingerprint(),null),k);
    }
    static SyntheticHalt.Assertion sign(SyntheticHalt.Assertion a,KeyPair k) throws Exception {
        Signature s=Signature.getInstance("Ed25519");s.initSign(k.getPrivate());s.update(a.payload());
        return new SyntheticHalt.Assertion(a.issuer(),a.keyId(),a.audience(),a.owner(),a.operation(),a.epoch(),
                a.issued(),a.expires(),a.nonce(),a.requestDigest(),s.sign());
    }
    static SyntheticOwner owner(SyntheticOwner.Ledger ledger,SyntheticHalt halt,LoopbackPeer peer,Clock clock,
                                java.util.function.Supplier<SyntheticOwner.SinkPolicy> sinks) {
        return new SyntheticOwner(OWNER,CALLER,RECIPIENT,ACCOUNT,SyntheticOwner.Credential.generatedSyntheticFixture(),clock,ledger,halt,peer,sinks);
    }

    @Test void validHaltIsOneUseAndRechecked() throws Exception {
        var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());
        var r=request(new LoopbackPeer(1,100));var a=sign(r,k);var lease=h.admit(a,r);
        assertNotNull(lease);assertTrue(h.recheck(lease,r));assertNull(h.admit(a,r));
        c.value.set(NOW.plusSeconds(20));assertFalse(h.recheck(lease,r));
    }
    @ParameterizedTest @ValueSource(strings={"missing","unsigned","forged","expired","future","audience","owner","operation","epoch","issuer","digest","key","duration"})
    void invalidAttestationsDeny(String kind) throws Exception {
        var c=new MutableClock();var k=key();var r=request(new LoopbackPeer(1,100));var a=sign(r,k);
        var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());
        if(kind.equals("missing")){assertNull(h.admit(null,r));return;}
        if(kind.equals("expired"))c.value.set(NOW.plusSeconds(20));
        var changed=new SyntheticHalt.Assertion(kind.equals("issuer")?"SYNTHETIC_OTHER":a.issuer(),kind.equals("key")?"SYNTHETIC_OTHER":a.keyId(),
                kind.equals("audience")?"SYNTHETIC_OTHER":a.audience(),kind.equals("owner")?"SYNTHETIC_OTHER":a.owner(),
                kind.equals("operation")?"SYNTHETIC_OTHER":a.operation(),kind.equals("epoch")?2:a.epoch(),
                kind.equals("future")?NOW.plusSeconds(1):kind.equals("duration")?NOW.minusSeconds(20):a.issued(),a.expires(),a.nonce(),
                kind.equals("digest")?"SYNTHETIC_WRONG":a.requestDigest(),null);
        if(!kind.equals("unsigned"))changed=sign(changed,kind.equals("forged")?key():k);
        assertNull(h.admit(changed,r));
    }
    @Test void revocationRotationEpochAndIssuerLossInvalidateLeases() throws Exception {
        var c=new MutableClock();var k=key();var r=request(new LoopbackPeer(1,100));var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());
        var lease=h.admit(sign(r,k),r);assertNotNull(lease);h.available(false);assertFalse(h.recheck(lease,r));
        h.available(true);h.epoch(2);assertFalse(h.recheck(lease,r));h.epoch(1);assertTrue(h.recheck(lease,r));
        h.revoke("SYNTHETIC_KEY");assertFalse(h.recheck(lease,r));h.rotate("SYNTHETIC_KEY",k.getPublic());assertNull(h.admit(sign(r,k),r));
        var replacement=key();h.rotate("SYNTHETIC_ROTATED",replacement.getPublic());var a=sign(r,k);
        var next=sign(new SyntheticHalt.Assertion(a.issuer(),"SYNTHETIC_ROTATED",a.audience(),a.owner(),a.operation(),a.epoch(),a.issued(),a.expires(),UUID.randomUUID(),a.requestDigest(),null),replacement);
        assertNotNull(h.admit(next,r));
    }
    @ParameterizedTest @ValueSource(strings={"version","owner","caller","principal","recipient","account","purpose","method","route","authority","expired","future","duration"})
    void scopeAndIdentityDenyBeforeLedger(String field) throws Exception {
        var c=new MutableClock();var k=key();var p=new LoopbackPeer(1,100);var r=request(p);var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());
        var x=new SyntheticOwner.Request(r.id(),field.equals("version")?"REAL":r.version(),field.equals("owner")?"SYNTHETIC_OTHER":r.owner(),
                field.equals("caller")?"SYNTHETIC_OTHER":r.caller(),field.equals("recipient")?"SYNTHETIC_OTHER":r.recipient(),
                field.equals("account")?"SYNTHETIC_OTHER":r.account(),field.equals("purpose")?"SYNTHETIC_OTHER":r.purpose(),
                field.equals("method")?"POST":r.method(),field.equals("route")?p.endpoint()+"/orders":r.endpoint(),
                field.equals("future")?NOW.plusSeconds(1):r.issued(),field.equals("duration")?NOW.plusSeconds(31):r.expires(),1,
                field.equals("authority")?"BROKER_VERIFIED":r.authority());
        if(field.equals("expired"))c.value.set(NOW.plusSeconds(20));
        var never=new SyntheticOwner.Ledger(){public boolean consume(SyntheticOwner.Request ignored){throw new AssertionError("LEDGER_REACHED");}public void terminal(UUID id,SyntheticOwner.Outcome o){throw new AssertionError();}};
        try(var o=owner(never,h,p,c,()->SAFE)) {
            assertEquals(SyntheticOwner.Outcome.PROTOTYPE_PRECONDITION_FAILED,o.observe(x,field.equals("principal")?"SYNTHETIC_OTHER":CALLER,sign(x,k),SyntheticOwner.Fault.NONE).outcome());
        }
    }
    @ParameterizedTest @ValueSource(strings={"http://api.kite.trade/user/margins/equity","https://api.kite.trade/user/margins/equity","http://localhost:1234/user/margins/equity","http://127.0.0.1:1235/user/margins/equity","http://127.0.0.1:1234/user/margins/equity?x=1","http://127.0.0.1:1234/user/profile","http://127.0.0.1:1234/orders","http://2130706433:1234/user/margins/equity","http://[::1]:1234/user/margins/equity","http://127.0.0.1:1234/user/../user/margins/equity"})
    void endpointAliasesAndAlternateRoutesAreDeniedWithoutNetwork(String endpoint) {
        var p=new LoopbackPeer(1234,100);assertFalse(p.accepts("GET",endpoint));assertFalse(p.accepts("POST",p.endpoint()));
    }
    @ParameterizedTest @ValueSource(strings={"STDOUT","STDERR","FILE","SLF4J","WIRE","TRACE","METRICS","ACTUATOR","EXCEPTION","HEAP_DUMP","CRASH_DUMP","WINDOWS_EVENT","DOCKER_LOG","IDE","PROXY","CI_ARTIFACT","DEBUG"})
    void everyNonAllowlistedSinkDeniesStartup(String sink) throws Exception {
        var k=key();var c=new MutableClock();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());
        assertThrows(IllegalArgumentException.class,()->owner(DENY,h,new LoopbackPeer(1,100),c,()->new SyntheticOwner.SinkPolicy(Set.of("BOUNDED_RESULT",sink),true)));
        assertFalse(new SyntheticOwner.SinkPolicy(Set.of("BOUNDED_RESULT"),false).safe());
    }
    @ParameterizedTest @ValueSource(strings={"http.proxyHost","https.proxyHost","socksProxyHost","java.net.useSystemProxies","jdk.httpclient.HttpClient.log","javax.net.debug"})
    void systemPropertiesFailClosed(String property) {
        String original=System.getProperty(property);
        try{System.setProperty(property,"SYNTHETIC_INJECTION");assertFalse(SyntheticOwner.environmentSafe());}
        finally{if(original==null)System.clearProperty(property);else System.setProperty(property,original);}
    }
    @ParameterizedTest @ValueSource(strings={"HTTP_PROXY","HTTPS_PROXY","ALL_PROXY"})
    void inheritedProxyEnvironmentFailsClosedInSeparateJvm(String variable) throws Exception {
        String executable=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        if(!java.nio.file.Files.exists(Path.of(executable)))executable=Path.of(System.getProperty("java.home"),"bin","java").toString();
        var b=new ProcessBuilder(executable,"-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),EnvironmentProbe.class.getName());
        b.environment().put(variable,"SYNTHETIC_INJECTION");var p=b.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(p.waitFor(10,TimeUnit.SECONDS));assertEquals(23,p.exitValue());
    }
    public static final class EnvironmentProbe {public static void main(String[] ignored){System.exit(SyntheticOwner.environmentSafe()?24:23);}}
    @Test void secretObjectsCannotBeNativelySerializedOrPrintedAndCloseErasesMaterial() throws Exception {
        var credential=SyntheticOwner.Credential.generatedSyntheticFixture();var r=request(new LoopbackPeer(1,100));var a=sign(r,key());
        var field=credential.getClass().getDeclaredField("material");field.setAccessible(true);byte[] material=(byte[])field.get(credential);
        String marker=HexFormat.of().formatHex(material);
        for(Object value:List.of(credential,r,a)) {
            assertFalse(value.toString().contains(marker));assertTrue(value.toString().endsWith("REDACTED"));
            assertThrows(NotSerializableException.class,()->{try(var out=new ObjectOutputStream(new ByteArrayOutputStream())){out.writeObject(value);}});
        }
        assertEquals(Set.of("outcome","equityFieldPresent"),Arrays.stream(SyntheticOwner.Result.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet()));
        assertEquals("SYNTHETIC_PRECONDITION_DENIED",SyntheticOwner.denied().getMessage());credential.close();assertFalse(credential.available());
        for(byte b:material)assertEquals(0,b);
    }
    @Test void fingerprintIsStableScopeBoundAndLengthFramed() {
        var r=request(new LoopbackPeer(1,100));assertEquals(r.fingerprint(),r.fingerprint());assertEquals(64,r.fingerprint().length());
        var x=new SyntheticOwner.Request(r.id(),r.version(),r.owner(),r.caller(),r.recipient(),"SYNTHETIC_OTHER",r.purpose(),r.method(),r.endpoint(),r.issued(),r.expires(),r.haltEpoch(),r.authority());
        assertNotEquals(r.fingerprint(),x.fingerprint());
    }
}
