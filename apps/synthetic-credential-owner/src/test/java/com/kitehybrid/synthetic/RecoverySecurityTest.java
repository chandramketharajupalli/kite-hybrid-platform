package com.kitehybrid.synthetic;

import java.io.*;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.kitehybrid.synthetic.SyntheticSecurityTest.*;

class RecoverySecurityTest {
    @Test void signatureProviderAndRestrictedJvmNeedOnlyJavaBase() throws Exception {
        assertEquals("java.base",java.security.KeyPairGenerator.getInstance("RSA").getProvider().getClass().getModule().getName());
        assertEquals("java.base",java.security.Signature.getInstance(SyntheticHalt.SIGNATURE_ALGORITHM).getProvider().getClass().getModule().getName());
        String executable=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        if(!java.nio.file.Files.exists(java.nio.file.Path.of(executable)))executable=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString();
        var process=new ProcessBuilder(executable,"--limit-modules","java.base","-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),BaseOnlySignatureProbe.class.getName())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if(!process.waitFor(25,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();fail("SYNTHETIC_BASE_ONLY_PROBE_TIMEOUT");}
        assertEquals(0,process.exitValue());
    }
    @Test void undersizedOrWrongAlgorithmHaltKeysAreRejected() throws Exception {
        var weak=java.security.KeyPairGenerator.getInstance("RSA");weak.initialize(2048);
        var key=weak.generateKeyPair();assertThrows(IllegalArgumentException.class,()->new SyntheticHalt(new MutableClock(),1,"SYNTHETIC_KEY",key.getPublic()));
        var wrong=java.security.KeyPairGenerator.getInstance("EC").generateKeyPair();
        assertThrows(IllegalArgumentException.class,()->new SyntheticHalt(new MutableClock(),1,"SYNTHETIC_KEY",wrong.getPublic()));
    }
    /** Test-only process; deliberately references no JUnit/PG/runtime modules beyond java.base. */
    public static final class BaseOnlySignatureProbe {
        public static void main(String[] ignored) throws Exception {
            var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(3072);var key=generator.generateKeyPair();
            var now=java.time.Instant.parse("2000-01-01T00:00:00Z");
            var request=new SyntheticOwner.Request(UUID.randomUUID(),SyntheticOwner.VERSION,"SYNTHETIC_OWNER","SYNTHETIC_CALLER","SYNTHETIC_RECIPIENT","SYNTHETIC_ACCOUNT",SyntheticOwner.PURPOSE,"GET","http://127.0.0.1:1/user/margins/equity",now,now.plusSeconds(20),1,"SYNTHETIC_ONLY");
            var a=new SyntheticHalt.Assertion("SYNTHETIC_ISSUER","SYNTHETIC_KEY",request.recipient(),request.owner(),request.purpose(),1,now,now.plusSeconds(20),UUID.randomUUID(),request.fingerprint(),null);
            var signer=java.security.Signature.getInstance(SyntheticHalt.SIGNATURE_ALGORITHM);signer.initSign(key.getPrivate());signer.update(a.payload());
            var signed=new SyntheticHalt.Assertion(a.issuer(),a.keyId(),a.audience(),a.owner(),a.operation(),a.epoch(),a.issued(),a.expires(),a.nonce(),a.requestDigest(),signer.sign());
            var verifier=new SyntheticHalt(java.time.Clock.fixed(now,java.time.ZoneOffset.UTC),1,"SYNTHETIC_KEY",key.getPublic());
            if(verifier.admit(signed,request)==null)throw new IllegalStateException("SYNTHETIC_BASE_ONLY_VERIFY_FAILED");
        }
    }
    @Test void epochRollbackCannotReactivateAnOldLease() throws Exception {
        var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var r=request(new LoopbackPeer(1,100));
        var lease=h.admit(sign(r,k),r);assertNotNull(lease);h.epoch(2);
        assertThrows(IllegalArgumentException.class,()->h.epoch(1));assertFalse(h.recheck(lease,r));
    }
    @Test void reversedClockDeniesEvenIfTimeSubsequentlyRecovers() throws Exception {
        var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var r=request(new LoopbackPeer(1,100));
        var lease=h.admit(sign(r,k),r);c.value.set(NOW.plusSeconds(2));assertTrue(h.recheck(lease,r));
        c.value.set(NOW.plusSeconds(1));assertFalse(h.recheck(lease,r));c.value.set(NOW.plusSeconds(3));assertFalse(h.recheck(lease,r));
    }
    @Test void conflictingKeyIdentityFailsClosedWithoutReplacingTrustedKey() throws Exception {
        var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var r=request(new LoopbackPeer(1,100));
        var replacement=key();assertThrows(IllegalArgumentException.class,()->h.rotate("SYNTHETIC_KEY",replacement.getPublic()));
        assertNull(h.admit(sign(r,k),r));assertNull(h.admit(sign(r,replacement),r));
    }
    @Test void exactSignedEvidenceFingerprintIsStableButNonceBoundAndRedacted() throws Exception {
        var r=request(new LoopbackPeer(1,100));var k=key();var a=sign(r,k);var b=sign(r,k);
        assertEquals(a.fingerprint(),a.fingerprint());assertNotEquals(a.fingerprint(),b.fingerprint());
        String signed=Base64.getEncoder().encodeToString(a.signature());assertFalse(a.toString().contains(signed));
        assertThrows(NotSerializableException.class,()->{try(var out=new ObjectOutputStream(new ByteArrayOutputStream())){out.writeObject(a);}});
    }
    @Test void transportCannotDispatchWithoutAnOwnerTicket() {
        var p=new LoopbackPeer(1,100);var result=p.get("GET",p.endpoint(),null,SyntheticOwner.Fault.NONE);
        assertEquals(SyntheticOwner.Delivery.NOT_DISPATCHED,result.delivery());assertEquals(0,p.attemptedSends());
        for(var constructor:SyntheticOwner.DispatchTicket.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertFalse(java.io.Serializable.class.isAssignableFrom(SyntheticOwner.DispatchTicket.class));
    }
    @Test void oldLedgerWithoutExplicitEvidenceBindingCannotGrantOwnerDispatch() throws Exception {
        var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var p=new LoopbackPeer(1,100);var r=request(p);
        var legacy=new SyntheticOwner.Ledger(){public boolean consume(SyntheticOwner.Request ignored){throw new AssertionError("UNBOUND_METHOD_USED");}public void terminal(UUID id,SyntheticOwner.Outcome out){throw new AssertionError();}};
        try(var o=owner(legacy,h,p,c,()->SAFE)){assertEquals(SyntheticOwner.Outcome.SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());}
        assertEquals(0,p.attemptedSends());
    }
    @Test void syntheticTestOnlyTicketRaceAllowsOneUseAndCannotSerialize() throws Exception {
        // Reflection is confined to this negative fixture; no socket or durable grant is fabricated.
        var p=new LoopbackPeer(1,100);var request=request(p);
        var constructor=SyntheticOwner.DispatchTicket.class.getDeclaredConstructor(SyntheticOwner.Request.class,java.util.function.BooleanSupplier.class);
        constructor.setAccessible(true);
        var ticket=constructor.newInstance(request,(java.util.function.BooleanSupplier)()->false);
        assertFalse(ticket.take("POST",request.endpoint()));assertFalse(ticket.take("GET",request.endpoint()+"?changed"));
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var gate=new java.util.concurrent.CountDownLatch(1);var claims=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int i=0;i<8;i++)claims.add(workers.submit(()->{gate.await();return ticket.take("GET",request.endpoint());}));
            gate.countDown();int wins=0;for(var claim:claims)if(claim.get(3,java.util.concurrent.TimeUnit.SECONDS))wins++;
            assertEquals(1,wins);
        }
        assertEquals(SyntheticOwner.Delivery.NOT_DISPATCHED,p.get("GET",request.endpoint(),ticket,SyntheticOwner.Fault.NONE).delivery());
        assertEquals(0,p.attemptedSends());assertEquals("SYNTHETIC_TICKET_REDACTED",ticket.toString());
        assertThrows(NotSerializableException.class,()->{try(var out=new ObjectOutputStream(new ByteArrayOutputStream())){out.writeObject(ticket);}});
    }
}
