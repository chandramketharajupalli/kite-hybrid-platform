package com.kitehybrid.synthetic;

import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GateAHaltBoundaryTest {
    static final String KEY="SYNTHETIC_KEY", OWNER="SYNTHETIC_OWNER", PROCESS="SYNTHETIC_PROCESS";
    static final Instant NOW=GateABoundaryTest.NOW;
    static final class Time extends Clock {
        volatile Instant now=NOW;
        public java.time.ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(java.time.ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    static KeyPair keys;
    @BeforeAll static void key() throws Exception { keys=SyntheticSecurityTest.key(); }
    String marker;AttemptJournal journal;Time time;GateAHaltBoundary.Control control;GateAHaltBoundary boundary;
    GateABoundary.Approval approval;SyntheticHalt.Assertion assertion;
    @BeforeEach void setup() throws Exception {
        marker=UUID.randomUUID().toString();journal=new AttemptJournal(marker);journal.initialize();time=new Time();
        control=new GateAHaltBoundary.Control(time,1,KEY,keys.getPublic());boundary=new GateAHaltBoundary(marker,PROCESS,time,control);
        approval=approval(UUID.randomUUID());assertion=sign(approval,PROCESS,1);
    }
    @AfterEach void cleanup() throws Exception { journal.destroyFixture(); }
    static GateABoundary.Approval approval(UUID id) {return new GateABoundary.Approval(id,OWNER,NOW,NOW.plusSeconds(20),true,true,"A");}
    static SyntheticHalt.Assertion sign(GateABoundary.Approval a,String process,long epoch) throws Exception {
        return SyntheticSecurityTest.sign(GateAHaltBoundary.request(a,process,epoch,KEY),keys);
    }
    GateABoundary.Observation acquire(Consumer<String> hook) {
        return boundary.acquire("GET",GateABoundary.URL,null,approval,OWNER,1,assertion,GateABoundaryTest.response(),hook);
    }
    @Test void validHaltConsumesOnceAndNeverUsesRealNetwork() {
        assertTrue(acquire(s->{}).synthetic());assertEquals(1,boundary.sends());assertEquals(1,boundary.receipts());
        assertThrows(IllegalStateException.class,()->acquire(s->{}));assertEquals(1,boundary.sends());assertEquals(0,boundary.realRequests());
    }
    @ParameterizedTest @ValueSource(strings={"missing","signature","forged","attempt","operation","endpoint","process","key","epoch","nonce","owner","future","expired","issuer","duration"})
    void invalidHaltNeverDispatches(String mode) throws Exception {
        var a=assertion;
        if(mode.equals("expired"))time.now=NOW.plusSeconds(20);
        if(mode.equals("duration"))approval=new GateABoundary.Approval(approval.id(),OWNER,NOW,NOW.plusSeconds(31),true,true,"A");
        var request=GateAHaltBoundary.request(approval,PROCESS,1,KEY);
        if(mode.equals("attempt"))request=GateAHaltBoundary.request(approval(UUID.randomUUID()),PROCESS,1,KEY);
        if(mode.equals("endpoint"))request=new SyntheticOwner.Request(request.id(),request.version(),request.owner(),request.caller(),request.recipient(),request.account(),request.purpose(),"GET","/orders",request.issued(),request.expires(),1,KEY);
        var changed=new SyntheticHalt.Assertion(mode.equals("issuer")?"SYNTHETIC_OTHER":"SYNTHETIC_ISSUER",
                mode.equals("key")?"SYNTHETIC_OTHER":KEY,mode.equals("process")?"SYNTHETIC_OTHER":PROCESS,
                mode.equals("owner")?"SYNTHETIC_OTHER":OWNER,mode.equals("operation")?"SYNTHETIC_GATE_B":GateAHaltBoundary.OPERATION,
                mode.equals("epoch")?2:1,mode.equals("future")?NOW.plusSeconds(1):NOW,approval.expires(),
                mode.equals("nonce")?null:a.nonce(),request.fingerprint(),null);
        assertion=mode.equals("missing")?null:mode.equals("nonce")?changed:SyntheticSecurityTest.sign(changed,mode.equals("forged")?SyntheticSecurityTest.key():keys);
        if(mode.equals("signature")){byte[] bytes=assertion.signature();bytes[0]^=1;assertion=new SyntheticHalt.Assertion(assertion.issuer(),KEY,PROCESS,OWNER,GateAHaltBoundary.OPERATION,1,NOW,approval.expires(),assertion.nonce(),request.fingerprint(),bytes);}
        assertThrows(IllegalStateException.class,()->acquire(s->{}));assertEquals(0,boundary.sends());
    }
    @ParameterizedTest @ValueSource(strings={"BEFORE_AUTHORIZATION","BEFORE_RESERVATION","AFTER_JOURNAL_FORCE","BEFORE_DISPATCH","DURING_RESULT_DELIVERY","AFTER_PEER_OBSERVED"})
    void revocationAtEveryModeledBoundary(String stage) {
        assertThrows(IllegalStateException.class,()->acquire(s->{if(s.equals(stage))control.revoke();}));
        int expected=stage.equals("DURING_RESULT_DELIVERY")||stage.equals("AFTER_PEER_OBSERVED")?1:0;
        assertEquals(expected,boundary.sends());assertEquals(expected,boundary.receipts());
        assertThrows(IllegalStateException.class,()->acquire(s->{}));assertEquals(expected,boundary.sends());
    }
    @ParameterizedTest @ValueSource(strings={"unavailable","revoked","staleEpoch","conflictingKey","rollback"})
    void stateContradictionsDenyAndQuarantineCannotBeCleared(String mode) throws Exception {
        if(mode.equals("unavailable"))control.available(false);
        if(mode.equals("revoked"))control.revoke();
        if(mode.equals("staleEpoch"))control.epoch(2);
        if(mode.equals("conflictingKey"))assertThrows(IllegalArgumentException.class,()->control.conflictingKey(SyntheticSecurityTest.key().getPublic()));
        if(mode.equals("rollback")) {
            acquire(s->{});approval=approval(UUID.randomUUID());assertion=sign(approval,PROCESS,1);time.now=NOW.minusSeconds(1);
            // Keep owner validity broad enough for HALT itself to observe reversal.
            approval=new GateABoundary.Approval(approval.id(),OWNER,NOW.minusSeconds(2),NOW.plusSeconds(20),true,true,"A");
            assertion=sign(approval,PROCESS,1);
        }
        int prior=boundary.sends();assertThrows(IllegalStateException.class,()->acquire(s->{}));assertEquals(prior,boundary.sends());
        if(mode.equals("rollback")||mode.equals("conflictingKey")) {
            time.now=NOW;control.available(true);assertThrows(IllegalStateException.class,()->acquire(s->{}));assertEquals(prior,boundary.sends());
        }
    }
    @ParameterizedTest @ValueSource(strings={"missing","corruptIdentity","corruptMarker","lostAck"})
    void journalFailuresNeverDispatch(String mode) throws Exception {
        byte[] identity=Files.readAllBytes(journal.identityPath());
        if(mode.equals("missing"))boundary=new GateAHaltBoundary(UUID.randomUUID().toString(),PROCESS,time,control);
        if(mode.equals("corruptIdentity"))Files.writeString(journal.identityPath(),"corrupt");
        if(mode.equals("corruptMarker"))Files.writeString(journal.path(approval.id()),"partial");
        try {assertThrows(IllegalStateException.class,()->acquire(s->{if(mode.equals("lostAck")&&s.equals("AFTER_JOURNAL_FORCE"))throw GateABoundary.denied();}));assertEquals(0,boundary.sends());}
        finally {if(mode.equals("corruptIdentity"))Files.write(journal.identityPath(),identity);}
    }
    @ParameterizedTest @ValueSource(strings={"POST","proxy","historical","order","ws","fragment","query","port","relative","encoded","traversal","host"})
    void forbiddenRoutesMethodsAndProxies(String mode) {
        String url=switch(mode){case "historical"->"https://api.kite.trade/instruments/historical/1/5minute";case "order"->"https://api.kite.trade/orders";case "ws"->"wss://ws.kite.trade";case "fragment"->GateABoundary.URL+"#x";case "query"->GateABoundary.URL+"?x=1";case "port"->"https://api.kite.trade:443/instruments/NSE";case "relative"->"/instruments/NSE";case "encoded"->"https://api.kite.trade/instruments/%4eSE";case "traversal"->"https://api.kite.trade/instruments/./NSE";case "host"->"https://other.invalid/instruments/NSE";default->GateABoundary.URL;};
        assertThrows(IllegalStateException.class,()->boundary.acquire(mode.equals("POST")?"POST":"GET",url,mode.equals("proxy")?"DIRECT":null,approval,OWNER,1,assertion,GateABoundaryTest.response(),s->{}));assertEquals(0,boundary.sends());
    }
    @Test void concurrentRevocationDuringDeliveryDiscardsWithoutUndoingSend() throws Exception {
        var delivered=new CountDownLatch(1);var revokeDone=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var f=pool.submit(()->{delivered.await();control.revoke();revokeDone.countDown();return true;});
            assertThrows(IllegalStateException.class,()->acquire(s->{if(s.equals("DURING_RESULT_DELIVERY")){delivered.countDown();try{assertTrue(revokeDone.await(5,TimeUnit.SECONDS));}catch(InterruptedException ex){throw GateABoundary.denied();}}}));
            assertTrue(f.get(5,TimeUnit.SECONDS));assertEquals(1,boundary.sends());assertEquals(1,boundary.receipts());
        }
    }
    @Test void exceptionsAndOutputDoNotExposeAuthorization() {
        var buffer=new java.io.ByteArrayOutputStream();var out=System.out;var err=System.err;
        try(var stream=new java.io.PrintStream(buffer)) {
            System.setOut(stream);System.setErr(stream);control.revoke();
            var denied=assertThrows(IllegalStateException.class,()->acquire(s->{}));assertEquals("GATE_A_OFFLINE_DENIED",denied.getMessage());assertNull(denied.getCause());
            assertFalse(boundary.toString().contains(approval.id().toString()));assertEquals("SYNTHETIC_HALT_REDACTED",assertion.toString());
        }finally{System.setOut(out);System.setErr(err);}
        assertEquals(0,buffer.size());
    }
    @Test void localJournalRollbackCannotOverrideSurvivingControl() throws Exception {
        acquire(s->{});Files.delete(journal.path(approval.id()));assertion=sign(approval,PROCESS,1);
        assertThrows(IllegalStateException.class,()->acquire(s->{}));assertEquals(1,boundary.sends());
    }
    @ParameterizedTest @ValueSource(strings={"REDIRECT","TIMEOUT","UNCERTAIN","SERVER_ERROR"})
    void unknownDeliveryAndErrorsRemainSpent(String fault) throws Exception {
        var response=new GateABoundary.Response(GateABoundaryTest.CSV,"text/csv; charset=utf-8",GateABoundary.Outcome.valueOf(fault));
        assertThrows(IllegalStateException.class,()->boundary.acquire("GET",GateABoundary.URL,null,approval,OWNER,1,assertion,response,s->{}));
        assertEquals(1,boundary.sends());assertEquals(0,boundary.receipts());assertion=sign(approval,PROCESS,1);
        assertThrows(IllegalStateException.class,()->acquire(s->{}));assertEquals(1,boundary.sends());
    }
    @Test void missingOwnerGateBOrHaltServiceCannotDispatch() {
        assertThrows(IllegalStateException.class,()->boundary.acquire("GET",GateABoundary.URL,null,null,OWNER,1,assertion,GateABoundaryTest.response(),s->{}));
        approval=new GateABoundary.Approval(approval.id(),OWNER,NOW,NOW.plusSeconds(20),true,true,"B");
        assertThrows(IllegalStateException.class,()->acquire(s->{}));
        boundary=new GateAHaltBoundary(marker,PROCESS,time,null);assertThrows(IllegalStateException.class,()->acquire(s->{}));assertEquals(0,boundary.sends());
    }
    @ParameterizedTest @ValueSource(strings={"epoch","unavailable","expiry","rollback"})
    void stateChangesAfterReservationDenyBeforeSend(String mode) {
        assertThrows(IllegalStateException.class,()->acquire(s->{if(s.equals("BEFORE_DISPATCH")) {
            if(mode.equals("epoch"))control.epoch(2);
            if(mode.equals("unavailable"))control.available(false);
            if(mode.equals("expiry"))time.now=NOW.plusSeconds(20);
            if(mode.equals("rollback"))time.now=NOW.minusSeconds(1);
        }}));
        assertEquals(0,boundary.sends());assertTrue(Files.exists(journal.path(approval.id())));
    }
    @Test void concurrentAdmissionsWithSharedControlHaveOneWinner() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);Callable<Boolean> task=()->{start.await();try{acquire(s->{});return true;}catch(IllegalStateException denied){return false;}};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertNotEquals(a.get(),b.get());
        }
        assertEquals(1,boundary.sends());assertEquals(1,boundary.receipts());
    }
}
