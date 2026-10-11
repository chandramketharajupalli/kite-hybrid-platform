package com.kitehybrid.synthetic;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static com.kitehybrid.synthetic.SyntheticSecurityTest.*;
import static com.kitehybrid.synthetic.SyntheticOwner.Outcome.*;
import static com.kitehybrid.synthetic.SyntheticOwner.Delivery.*;

class RecoveryOwnerIT {
    static PostgreSQLContainer<?> db;
    static DisposableLedger ledger;
    @BeforeAll static void start() throws Exception {db=DisposableLedger.container();db.start();ledger=DisposableLedger.initialize(db);}
    @AfterAll static void stop() throws Exception {if(db!=null)db.stop();if(ledger!=null)ledger.journal.destroyFixture();}

    @ParameterizedTest @EnumSource(value=SyntheticOwner.Fault.class,names={"AFTER_ACK_BEFORE_DISPATCH","BEFORE_SOCKET","AFTER_SOCKET_BEFORE_SEND","DURING_SEND","AFTER_SEND","DURING_PARSE","AFTER_RESPONSE","BEFORE_OUTCOME"})
    void dispatchCrashWindowsRemainSpentAndClassified(SyntheticOwner.Fault fault) throws Exception {
        try(var f=OwnerIT.peer(OwnerIT.response(200,OwnerIT.BODY))) {
            var p=f.peer();var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var r=request(p);var a=sign(r,k);
            var expected=switch(fault){case AFTER_ACK_BEFORE_DISPATCH,BEFORE_SOCKET,AFTER_SOCKET_BEFORE_SEND->NOT_DISPATCHED;case AFTER_RESPONSE,BEFORE_OUTCOME->OBSERVED_COMPLETED;default->MAY_HAVE_DISPATCHED;};
            try(var o=owner(ledger,h,p,c,()->SAFE)) {
                var result=o.observe(r,CALLER,a,fault);assertEquals(SYNTHETIC_READ_OUTCOME_UNKNOWN,result.outcome());assertEquals(expected,result.delivery());
            }
            assertEquals(expected==NOT_DISPATCHED?0:1,p.attemptedSends());
            assertEquals("CONSUMED",ledger.state(r.id()));assertEquals(a.fingerprint(),ledger.haltDigest(r.id()));
            // A fresh owner/verifier and different signed nonce do not revive the durable ID.
            try(var restarted=owner(ledger,new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic()),p,c,()->SAFE)) {
                assertEquals(SYNTHETIC_READ_DENIED,restarted.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
            }
            assertEquals(expected==NOT_DISPATCHED?0:1,p.attemptedSends());
            if(fault==SyntheticOwner.Fault.DURING_SEND) {
                await(()->!f.received.get().isEmpty());assertEquals("GET ",f.received.get());
            }
            if(expected==OBSERVED_COMPLETED)assertTrue(f.received.get().endsWith("\r\n\r\n"));
        }
    }
    @Test void fixtureConfirmedReceiptWithNoResponseRemainsUnknown() throws Exception {
        try(var f=OwnerIT.peer("")) {
            var p=f.peer();var c=new MutableClock();var k=key();var r=request(p);var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());
            try(var o=owner(ledger,h,p,c,()->SAFE)) {var result=o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE);assertEquals(SYNTHETIC_READ_OUTCOME_UNKNOWN,result.outcome());assertEquals(MAY_HAVE_DISPATCHED,result.delivery());}
            assertTrue(f.received.get().endsWith("\r\n\r\n"));assertEquals(1,p.attemptedSends());assertFalse(ledger.consume(r));
        }
    }
    @Test void connectionResetIsUnknownAndNeverRetries() throws Exception {
        try(var server=new ServerSocket(0,1,InetAddress.getByAddress(new byte[]{127,0,0,1}));var pool=Executors.newSingleThreadExecutor()) {
            var task=pool.submit(()->{try(var socket=server.accept()){socket.setSoLinger(true,0);}catch(IOException ex){throw new UncheckedIOException(ex);}});
            var p=new LoopbackPeer(server.getLocalPort(),500);var c=new MutableClock();var k=key();var r=request(p);var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());
            try(var o=owner(ledger,h,p,c,()->SAFE)){var result=o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE);assertEquals(SYNTHETIC_READ_OUTCOME_UNKNOWN,result.outcome());assertTrue(p.attemptedSends()<=1);}
            task.get(3,TimeUnit.SECONDS);assertFalse(ledger.consume(r));
        }
    }
    @Test void twoOwnerProcessesDifferentIncarnationsSendAtMostOnce() throws Exception {
        try(var f=OwnerIT.peer(OwnerIT.response(200,OwnerIT.BODY))) {
            UUID id=UUID.randomUUID();var a=worker(id,f.listener.getLocalPort(),"SYNTHETIC_INCARNATION_A",SyntheticOwner.Fault.NONE);
            var b=worker(id,f.listener.getLocalPort(),"SYNTHETIC_INCARNATION_B",SyntheticOwner.Fault.NONE);
            var first=finish(a);var second=finish(b);
            assertEquals(Set.of(17,18),Set.of(first.code,second.code));assertEquals(1,first.sends+second.sends);
            assertEquals(1,f.attempts.get());assertTrue(f.received.get().endsWith("\r\n\r\n"));
            var restarted=finish(worker(id,f.listener.getLocalPort(),"SYNTHETIC_INCARNATION_C",SyntheticOwner.Fault.NONE));
            assertEquals(18,restarted.code);assertEquals(0,restarted.sends);assertEquals(1,f.attempts.get());
        }
    }
    @Test void ownerProcessDiesAfterConsumptionWithoutSendAndRestartCannotRevive() throws Exception {
        try(var f=OwnerIT.peer(OwnerIT.response(200,OwnerIT.BODY))) {
            UUID id=UUID.randomUUID();var first=finish(worker(id,f.listener.getLocalPort(),"SYNTHETIC_INCARNATION_A",SyntheticOwner.Fault.AFTER_ACK_BEFORE_DISPATCH));
            assertEquals(19,first.code);assertEquals(0,first.sends);assertEquals("CONSUMED",ledger.state(id));
            var second=finish(worker(id,f.listener.getLocalPort(),"SYNTHETIC_INCARNATION_B",SyntheticOwner.Fault.NONE));
            assertEquals(18,second.code);assertEquals(0,second.sends);assertEquals(0,f.attempts.get());
        }
    }
    @Test void staleIncarnationCannotUseOldApprovalEvenWithNewSignature() throws Exception {
        try(var f=OwnerIT.peer(OwnerIT.response(200,OwnerIT.BODY))) {
            var p=f.peer();var c=new MutableClock();var k=key();var r=request(p);var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());
            try(var o=new SyntheticOwner("SYNTHETIC_NEW_INCARNATION",CALLER,RECIPIENT,ACCOUNT,SyntheticOwner.Credential.generatedSyntheticFixture(),c,ledger,h,p,()->SAFE)) {
                assertEquals(SyntheticOwner.Outcome.PROTOTYPE_PRECONDITION_FAILED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
            }
            assertEquals(0,p.attemptedSends());assertEquals("ABSENT",ledger.state(r.id()));
        }
    }
    @Test void failureContainmentHasNoSignatureCredentialOrPayloadLeak() throws Exception {
        try(var f=OwnerIT.peer(OwnerIT.response(500,"SYNTHETIC_PRIVATE_ACCOUNT_PAYLOAD"))) {
            var p=f.peer();var c=new MutableClock();var k=key();var r=request(p);var a=sign(r,k);var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());
            var stdout=System.out;var stderr=System.err;var captured=new ByteArrayOutputStream();SyntheticOwner.Result result;
            try(var o=owner(ledger,h,p,c,()->SAFE)) {
                try {System.setOut(new PrintStream(captured));System.setErr(new PrintStream(captured));result=o.observe(r,CALLER,a,SyntheticOwner.Fault.NONE);}
                finally {System.setOut(stdout);System.setErr(stderr);}
            }
            assertEquals(0,captured.size());assertEquals(SYNTHETIC_READ_DENIED,result.outcome());
            for(String marker:List.of("SYNTHETIC_PRIVATE_ACCOUNT_PAYLOAD",ACCOUNT,Base64.getEncoder().encodeToString(a.signature()),"Authorization","Cookie"))assertFalse(result.toString().contains(marker));
            assertThrows(NotSerializableException.class,()->{try(var stream=new ObjectOutputStream(new ByteArrayOutputStream())){stream.writeObject(result);}});
        }
    }
    record WorkerResult(int code,int sends) {}
    @Test void globalProxySelectorCannotDivertTheNumericLoopbackSocket() throws Exception {
        var original=ProxySelector.getDefault();var selections=new java.util.concurrent.atomic.AtomicInteger();
        try(var proxy=OwnerIT.peer(OwnerIT.response(200,OwnerIT.BODY));var target=OwnerIT.peer(OwnerIT.response(200,OwnerIT.BODY))) {
            var injected=new ProxySelector(){
                public List<Proxy> select(URI uri){selections.incrementAndGet();return List.of(new Proxy(Proxy.Type.SOCKS,new InetSocketAddress(InetAddress.getLoopbackAddress(),proxy.listener.getLocalPort())));}
                public void connectFailed(URI uri,SocketAddress address,IOException error){}
            };
            var p=target.peer();var c=new MutableClock();var k=key();var r=request(p);var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());
            var wrapper=new SyntheticOwner.Ledger(){
                public boolean consume(SyntheticOwner.Request value){return false;}
                public boolean consume(SyntheticOwner.Request value,String evidence){boolean won=ledger.consume(value,evidence);ProxySelector.setDefault(injected);return won;}
                public void terminal(UUID id,SyntheticOwner.Outcome outcome){ProxySelector.setDefault(original);ledger.terminal(id,outcome);}
            };
            try(var o=owner(wrapper,h,p,c,()->SAFE)){assertEquals(SYNTHETIC_READ_SUCCEEDED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());}
            assertEquals(0,selections.get());assertEquals(0,proxy.attempts.get());assertEquals(1,target.attempts.get());
        } finally {ProxySelector.setDefault(original);}
    }
    static Process worker(UUID id,int peerPort,String incarnation,SyntheticOwner.Fault fault) throws Exception {
        String executable=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        if(!Files.exists(Path.of(executable)))executable=Path.of(System.getProperty("java.home"),"bin","java").toString();
        return new ProcessBuilder(executable,"-Duser.timezone=UTC","-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),OwnerWorker.class.getName(),Integer.toString(db.getMappedPort(5432)),ledger.marker(),id.toString(),Integer.toString(peerPort),incarnation,fault.name()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    static WorkerResult finish(Process process) throws Exception {
        if(!process.waitFor(25,TimeUnit.SECONDS)){process.destroyForcibly();fail("SYNTHETIC_WORKER_TIMEOUT");}
        String output=new String(process.getInputStream().readNBytes(128),StandardCharsets.US_ASCII).trim();
        assertTrue(output.matches("SYNTHETIC_ATTEMPTED_SENDS=[01]"),"SYNTHETIC_WORKER_OUTPUT_INVALID");
        return new WorkerResult(process.exitValue(),Integer.parseInt(output.substring(output.length()-1)));
    }
    static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(10);
        assertTrue(condition.getAsBoolean(),"SYNTHETIC_PEER_OBSERVATION_MISSING");
    }
}
