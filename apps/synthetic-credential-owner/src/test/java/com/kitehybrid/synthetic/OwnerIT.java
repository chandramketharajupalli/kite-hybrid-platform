package com.kitehybrid.synthetic;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static com.kitehybrid.synthetic.SyntheticSecurityTest.*;
import static com.kitehybrid.synthetic.SyntheticOwner.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

/** Disposable ledger + numeric loopback fixtures only. Never loaded by the runtime artifact. */
class OwnerIT {
    static PostgreSQLContainer<?> db;
    static DisposableLedger ledger;
    static final String BODY="{\"synthetic\":true,\"equity\":{\"available\":123.45}}";
    @BeforeAll static void start() throws Exception {db=DisposableLedger.container();db.start();ledger=DisposableLedger.initialize(db);ledger.verifyDurabilitySettings();}
    @AfterAll static void stop(){if(db!=null)db.stop();}

    static final class FakePeer implements AutoCloseable {
        final ServerSocket listener=new ServerSocket(0,8,InetAddress.getByAddress(new byte[]{127,0,0,1}));
        final ExecutorService executor=Executors.newSingleThreadExecutor();
        final AtomicInteger attempts=new AtomicInteger();
        final AtomicReference<String> received=new AtomicReference<>("");
        final AtomicReference<Throwable> failure=new AtomicReference<>();
        volatile boolean stopped;
        final Future<?> job;
        FakePeer(String response,Runnable afterReceive,int delay,boolean drip) throws Exception {
            listener.setSoTimeout(100);
            job=executor.submit(()->{
                while(!stopped) {
                    try(var socket=listener.accept()) {
                        attempts.incrementAndGet();socket.setSoTimeout(2000);var input=socket.getInputStream();var header=new ByteArrayOutputStream();
                        int state=0;
                        while(state!=4&&header.size()<2048){int ch=input.read();if(ch<0)break;header.write(ch);state=switch(state){case 0->ch==13?1:0;case 1->ch==10?2:0;case 2->ch==13?3:0;case 3->ch==10?4:0;default->0;};}
                        received.set(header.toString(StandardCharsets.US_ASCII));afterReceive.run();
                        if(delay>0)Thread.sleep(delay);
                        if(drip){for(byte b:response.getBytes(StandardCharsets.US_ASCII)){socket.getOutputStream().write(b);socket.getOutputStream().flush();Thread.sleep(40);}}
                        else {socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();}
                    }catch(SocketTimeoutException ignored){}catch(SocketException ignored){/* Deliberate peer close/timeout. */}
                    catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}catch(Throwable ex){failure.set(ex);return;}
                }
            });
        }
        LoopbackPeer peer(){return new LoopbackPeer(listener.getLocalPort(),500);}
        public void close() throws Exception {stopped=true;listener.close();executor.shutdownNow();assertTrue(executor.awaitTermination(3,TimeUnit.SECONDS));assertNull(failure.get(),"SYNTHETIC_FIXTURE_FAILED");}
    }
    static String response(int status,String body){return "HTTP/1.1 "+status+" Synthetic\r\nContent-Length: "+body.length()+"\r\nConnection: close\r\n\r\n"+body;}
    static FakePeer peer(String response) throws Exception {return new FakePeer(response,()->{},0,false);}
    @Test void successIsShapeOnlyOneUseAndCredentialNeverLeavesOwner() throws Exception {
        try(var f=peer(response(200,BODY))) {
            var p=f.peer();var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var r=request(p);
            var credential=SyntheticOwner.Credential.generatedSyntheticFixture();var secret=credential.getClass().getDeclaredField("material");secret.setAccessible(true);
            String marker=HexFormat.of().formatHex((byte[])secret.get(credential));
            var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();PrintStream stdout=System.out,stderr=System.err;
            SyntheticOwner.Result result;
            try(var o=new SyntheticOwner(OWNER,CALLER,RECIPIENT,ACCOUNT,credential,c,ledger,h,p,()->SAFE)) {
                try{System.setOut(new PrintStream(out));System.setErr(new PrintStream(err));result=o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE);}
                finally{System.setOut(stdout);System.setErr(stderr);}
                assertEquals(SYNTHETIC_READ_SUCCEEDED,result.outcome());assertTrue(result.equityFieldPresent());
                assertEquals(SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
            }
            assertEquals(0,out.size());assertEquals(0,err.size());assertFalse(result.toString().contains(marker));assertFalse(result.toString().contains("123.45"));
            String wire=f.received.get();assertTrue(wire.startsWith("GET /user/margins/equity HTTP/1.1\r\n"));
            for(String forbidden:List.of(marker,"Authorization:","Cookie:",ACCOUNT,CALLER))assertFalse(wire.contains(forbidden));
            assertEquals(1,f.attempts.get());assertEquals("CONSUMED",ledger.state(r.id()));
        }
    }
    @ParameterizedTest @ValueSource(ints={201,204,401,403,429,500,503})
    void statusHandlingNeverRetries(int status) throws Exception {
        try(var f=peer(response(status,status==204?"":BODY))) {
            var p=f.peer();var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var r=request(p);
            try(var o=owner(ledger,h,p,c,()->SAFE)) {
                assertEquals(status==201?SYNTHETIC_READ_SUCCEEDED:SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
                assertEquals(SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
            }assertEquals(1,f.attempts.get());
        }
    }
    @ParameterizedTest @ValueSource(ints={301,302,307,308})
    void redirectsNeverFollowEvenToAnotherLoopbackPort(int status) throws Exception {
        try(var target=peer(response(200,BODY));var f=peer("HTTP/1.1 "+status+" Redirect\r\nLocation: "+target.peer().endpoint()+"\r\nContent-Length: 0\r\n\r\n")) {
            var p=f.peer();var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var r=request(p);
            try(var o=owner(ledger,h,p,c,()->SAFE)){assertEquals(SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());}
            assertEquals(1,f.attempts.get());assertEquals(0,target.attempts.get());
        }
    }
    @ParameterizedTest @ValueSource(strings={"malformed","oversized","headerOversized","duplicateLength","chunked","compressed","realShape","truncated","noLength"})
    void invalidBodiesAndFramingAreBounded(String kind) throws Exception {
        String wire=switch(kind){
            case "malformed"->response(200,"{broken");case "oversized"->response(200,"x".repeat(4097));
            case "headerOversized"->"HTTP/1.1 200 OK\r\nX: "+"x".repeat(4097)+"\r\n\r\n";
            case "duplicateLength"->"HTTP/1.1 200 OK\r\nContent-Length: 1\r\nContent-Length: 1\r\n\r\nx";
            case "chunked"->"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n";
            case "compressed"->"HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: 1\r\n\r\nx";
            case "realShape"->response(200,"{\"equity\":{\"available\":123.45}}");
            case "truncated"->"HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nx";
            default->"HTTP/1.1 200 OK\r\n\r\nx";
        };
        try(var f=peer(wire)) {
            var p=f.peer();var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var r=request(p);
            try(var o=owner(ledger,h,p,c,()->SAFE)) {
                assertEquals(kind.equals("truncated")?SYNTHETIC_READ_OUTCOME_UNKNOWN:SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
                assertEquals(SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
            }assertEquals(1,f.attempts.get());
        }
    }
    @ParameterizedTest @ValueSource(strings={"timeout","drip","lostResponse"})
    void timeoutSlowPeerAndLostResponseStayConsumed(String kind) throws Exception {
        try(var f=new FakePeer(kind.equals("lostResponse")?"":response(200,BODY),()->{},kind.equals("timeout")?1000:0,kind.equals("drip"))) {
            var p=f.peer();var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var r=request(p);
            long start=System.nanoTime();
            try(var o=owner(ledger,h,p,c,()->SAFE)){assertEquals(SYNTHETIC_READ_OUTCOME_UNKNOWN,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());assertEquals(SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());}
            assertTrue(Duration.ofNanos(System.nanoTime()-start).compareTo(Duration.ofSeconds(3))<0);assertEquals("CONSUMED",ledger.state(r.id()));assertEquals(1,f.attempts.get());
        }
    }
    @ParameterizedTest @ValueSource(strings={"beforeCommit","lostAck","afterAck","afterResponse"})
    void injectedFailureBoundariesNeverDispatchTwice(String point) throws Exception {
        try(var f=peer(response(200,BODY))) {
            var p=f.peer();var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var r=request(p);
            var dbFault=point.equals("beforeCommit")?DisposableLedger.Fault.BEFORE_COMMIT:point.equals("lostAck")?DisposableLedger.Fault.AFTER_COMMIT_BEFORE_ACK:DisposableLedger.Fault.NONE;
            var runtimeFault=point.equals("afterAck")?SyntheticOwner.Fault.AFTER_ACK_BEFORE_DISPATCH:point.equals("afterResponse")?SyntheticOwner.Fault.AFTER_RESPONSE:SyntheticOwner.Fault.NONE;
            var adapter=new DisposableLedger(db.getMappedPort(5432),ledger.marker(),dbFault);
            try(var o=owner(adapter,h,p,c,()->SAFE)){assertEquals(runtimeFault==SyntheticOwner.Fault.NONE?SYNTHETIC_READ_DENIED:SYNTHETIC_READ_OUTCOME_UNKNOWN,o.observe(r,CALLER,sign(r,k),runtimeFault).outcome());}
            var restarted=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());
            try(var o=owner(ledger,restarted,p,c,()->SAFE)){assertEquals(SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());}
            assertEquals(point.equals("afterResponse")?1:0,f.attempts.get());assertEquals(point.equals("beforeCommit")?"RESERVED":"CONSUMED",ledger.state(r.id()));
        }
    }
    @ParameterizedTest @ValueSource(strings={"halt","logging","expiry","epoch"})
    void postDispatchSecurityChangesRejectResult(String kind) throws Exception {
        var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var sinks=new AtomicReference<>(SAFE);
        Runnable mutate=()->{switch(kind){case "halt"->h.revoke("SYNTHETIC_KEY");case "logging"->sinks.set(new SyntheticOwner.SinkPolicy(Set.of("STDOUT"),true));case "expiry"->c.value.set(NOW.plusSeconds(20));default->h.epoch(2);}};
        try(var f=new FakePeer(response(200,BODY),mutate,0,false)) {
            var p=f.peer();var r=request(p);
            try(var o=owner(ledger,h,p,c,sinks::get)){var result=o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE);assertEquals(SYNTHETIC_READ_DENIED,result.outcome());assertFalse(result.equityFieldPresent());}
            assertEquals(1,f.attempts.get());assertEquals("CONSUMED",ledger.state(r.id()));
        }
    }
    @ParameterizedTest @ValueSource(strings={"halt","logging","closed"})
    void changesAfterDurableClaimDenyBeforeDispatch(String kind) throws Exception {
        try(var f=peer(response(200,BODY))) {
            var p=f.peer();var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var sinks=new AtomicReference<>(SAFE);
            var holder=new AtomicReference<SyntheticOwner>();var r=request(p);
            var wrapper=new SyntheticOwner.Ledger(){
                public boolean consume(SyntheticOwner.Request request){boolean result=ledger.consume(request);switch(kind){case "halt"->h.available(false);case "logging"->sinks.set(new SyntheticOwner.SinkPolicy(Set.of("STDERR"),true));default->holder.get().close();}return result;}
                public void terminal(UUID id,SyntheticOwner.Outcome outcome){ledger.terminal(id,outcome);}
            };
            try(var o=owner(wrapper,h,p,c,sinks::get)){holder.set(o);assertEquals(SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());}
            assertEquals(0,f.attempts.get());assertEquals("CONSUMED",ledger.state(r.id()));
        }
    }
    @Test void connectionRefusalAndTerminalWriteFailureDoNotReopenAttempt() throws Exception {
        int port;try(var socket=new ServerSocket(0,1,InetAddress.getByAddress(new byte[]{127,0,0,1}))){port=socket.getLocalPort();}
        var p=new LoopbackPeer(port,500);var c=new MutableClock();var k=key();var h=new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic());var r=request(p);
        try(var o=owner(ledger,h,p,c,()->SAFE)){assertEquals(SYNTHETIC_READ_OUTCOME_UNKNOWN,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());}
        assertFalse(ledger.consume(r));
        try(var f=peer(response(200,BODY))) {
            var next=request(f.peer());var badTerminal=new SyntheticOwner.Ledger(){public boolean consume(SyntheticOwner.Request value){return ledger.consume(value);}public void terminal(UUID id,SyntheticOwner.Outcome outcome){throw SyntheticOwner.denied();}};
            try(var o=owner(badTerminal,h,f.peer(),c,()->SAFE)){assertEquals(SYNTHETIC_READ_OUTCOME_UNKNOWN,o.observe(next,CALLER,sign(next,k),SyntheticOwner.Fault.NONE).outcome());}
            assertFalse(ledger.consume(next));assertEquals(1,f.attempts.get());
        }
    }
}
