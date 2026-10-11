package com.kitehybrid.synthetic;

import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static com.kitehybrid.synthetic.SyntheticSecurityTest.*;
import static com.kitehybrid.synthetic.SyntheticOwner.Outcome.*;

class FenceOwnerIT {
    static PostgreSQLContainer<?> localDb,witnessDb;
    static DisposableLedger local;
    static FenceWitness witness;
    @BeforeAll static void start() throws Exception {
        localDb=DisposableLedger.container();localDb.start();local=DisposableLedger.initialize(localDb);
        witnessDb=DisposableLedger.container();witnessDb.start();witness=new FenceWitness(witnessDb,FenceWitness.freshRoot(),true);
    }
    @AfterAll static void stop() throws Exception {
        try {if(witness!=null)witness.close();} finally {
            if(witnessDb!=null)witnessDb.stop();if(localDb!=null)localDb.stop();if(local!=null)local.journal.destroyFixture();
        }
    }
    @Test void dualLocalRollbackCannotIncreaseObservedDispatchCountWithSurvivingWitness() throws Exception {
        try(var f=OwnerIT.peer(OwnerIT.response(200,OwnerIT.BODY))) {
            var p=f.peer();var c=new MutableClock();var k=key();var r=request(p);
            try(var o=owner(new FencedLedger(witness.client(),local,false),new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic()),p,c,()->SAFE)) {
                assertEquals(SYNTHETIC_READ_SUCCEEDED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
            }
            assertEquals(1,f.attempts.get());FenceLedgerIT.rollbackLocal(localDb,local,r.id(),"BOTH");
            try(var o=owner(new FencedLedger(witness.client(),local,false),new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic()),p,c,()->SAFE)) {
                assertEquals(SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
            }
            assertEquals(1,f.attempts.get());assertEquals(1,p.attemptedSends());
        }
    }
    @ParameterizedTest @EnumSource(value=SyntheticOwner.Fault.class,names={"AFTER_ACK_BEFORE_DISPATCH","BEFORE_SOCKET","AFTER_SOCKET_BEFORE_SEND","DURING_SEND","AFTER_SEND","DURING_PARSE","AFTER_RESPONSE","BEFORE_OUTCOME"})
    void fencedDispatchFaultsRemainRetired(SyntheticOwner.Fault fault) throws Exception {
        try(var f=OwnerIT.peer(OwnerIT.response(200,OwnerIT.BODY))) {
            var p=f.peer();var c=new MutableClock();var k=key();var r=request(p);
            var expected=switch(fault){case AFTER_ACK_BEFORE_DISPATCH,BEFORE_SOCKET,AFTER_SOCKET_BEFORE_SEND->SyntheticOwner.Delivery.NOT_DISPATCHED;
                case AFTER_RESPONSE,BEFORE_OUTCOME->SyntheticOwner.Delivery.OBSERVED_COMPLETED;default->SyntheticOwner.Delivery.MAY_HAVE_DISPATCHED;};
            try(var o=owner(new FencedLedger(witness.client(),local,false),new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic()),p,c,()->SAFE)) {
                var result=o.observe(r,CALLER,sign(r,k),fault);assertEquals(SYNTHETIC_READ_OUTCOME_UNKNOWN,result.outcome());assertEquals(expected,result.delivery());
            }
            try(var o=owner(new FencedLedger(witness.client(),local,false),new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic()),p,c,()->SAFE)) {
                assertEquals(SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
            }
            assertEquals(expected==SyntheticOwner.Delivery.NOT_DISPATCHED?0:1,p.attemptedSends());
        }
    }
    @Test void witnessAckLossBeforeLocalWriteNeverDispatches() throws Exception {
        try(var f=OwnerIT.peer(OwnerIT.response(200,OwnerIT.BODY))) {
            var p=f.peer();var c=new MutableClock();var k=key();var r=request(p);
            try(var o=owner(new FencedLedger(witness.client(),local,true),new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic()),p,c,()->SAFE)) {
                assertEquals(SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
            }
            assertEquals("ABSENT",local.state(r.id()));assertEquals(0,f.attempts.get());assertEquals(0,p.attemptedSends());
            assertFalse(new FencedLedger(witness.client(),local,false).consume(r,"0".repeat(64)));
        }
    }
    @Test void twoOwnerJvmRaceAndReplacementHaveOneObservedRequest() throws Exception {
        try(var f=OwnerIT.peer(OwnerIT.response(200,OwnerIT.BODY))) {
            UUID id=UUID.randomUUID();var ca=witness.client();var cb=witness.client();
            var a=FenceLedgerIT.worker(localDb,local,ca,id,"OWNER",Integer.toString(f.listener.getLocalPort()),"SYNTHETIC_FENCED_A");
            var b=FenceLedgerIT.worker(localDb,local,cb,id,"OWNER",Integer.toString(f.listener.getLocalPort()),"SYNTHETIC_FENCED_B");
            try {
                assertEquals(Set.of(17,18),Set.of(FenceLedgerIT.finish(a),FenceLedgerIT.finish(b)));
                int sends=readCount(a)+readCount(b);assertEquals(1,sends);assertEquals(1,f.attempts.get());
                String canary=witness.retainedRoot().authentication();
                for(Process child:List.of(a,b)) {
                    assertFalse(child.info().commandLine().orElse("").contains(canary));
                    assertFalse(new String(child.getErrorStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains(canary));
                }
            } finally {FenceLedgerIT.kill(a);FenceLedgerIT.kill(b);}
            FenceLedgerIT.rollbackLocal(localDb,local,id,"BOTH");
            var replacement=FenceLedgerIT.worker(localDb,local,witness.client(),id,"OWNER",Integer.toString(f.listener.getLocalPort()),"SYNTHETIC_FENCED_C");
            try {assertEquals(18,FenceLedgerIT.finish(replacement));assertEquals(0,readCount(replacement));}
            finally {FenceLedgerIT.kill(replacement);}
            assertEquals(1,f.attempts.get());
        }
    }
    @Test void witnessAuthenticationCanaryIsAbsentFromOwnerOutputsAndPeer() throws Exception {
        try(var f=OwnerIT.peer(OwnerIT.response(500,"SYNTHETIC_PRIVATE_ACCOUNT_PAYLOAD"))) {
            var p=f.peer();var c=new MutableClock();var k=key();var r=request(p);var capture=new ByteArrayOutputStream();
            var stdout=System.out;var stderr=System.err;SyntheticOwner.Result result;
            try(var o=owner(new FencedLedger(witness.client(),local,false),new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic()),p,c,()->SAFE);var sink=new PrintStream(capture)) {
                try {System.setOut(sink);System.setErr(sink);result=o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE);}
                finally {System.setOut(stdout);System.setErr(stderr);}
            }
            String canary=witness.retainedRoot().authentication();assertEquals(0,capture.size());
            assertFalse(result.toString().contains(canary));assertFalse(f.received.get().contains(canary));
            assertEquals(SYNTHETIC_READ_DENIED,result.outcome());
            assertEquals("SYNTHETIC_ROOT_REDACTED",witness.retainedRoot().toString());
        }
    }
    static int readCount(Process child) throws Exception {
        String text=new String(child.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.US_ASCII).trim();
        assertTrue(text.matches("SYNTHETIC_ATTEMPTED_SENDS=[01]"));return Integer.parseInt(text.substring(text.length()-1));
    }

    @Test void staleIncarnationAndLateAcknowledgmentCannotDispatch() throws Exception {
        try(var f=OwnerIT.peer(OwnerIT.response(200,OwnerIT.BODY))) {
            var p=f.peer();var c=new MutableClock();var k=key();var r=request(p);
            long before=witness.retainedRoot().checkpoint();
            try(var o=new SyntheticOwner("SYNTHETIC_REPLACEMENT",CALLER,RECIPIENT,ACCOUNT,SyntheticOwner.Credential.generatedSyntheticFixture(),c,
                new FencedLedger(witness.client(),local,false),new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic()),p,()->SAFE)) {
                assertEquals(PROTOTYPE_PRECONDITION_FAILED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
            }
            assertEquals(before,witness.retainedRoot().checkpoint());
            var fenced=new FencedLedger(witness.client(),local,false);
            var late=new SyntheticOwner.Ledger(){
                public boolean consume(SyntheticOwner.Request ignored){return false;}
                public boolean consume(SyntheticOwner.Request request,String digest){boolean granted=fenced.consume(request,digest);c.value.set(NOW.plusSeconds(20));return granted;}
                public void terminal(UUID id,SyntheticOwner.Outcome outcome){fenced.terminal(id,outcome);}
            };
            try(var o=owner(late,new SyntheticHalt(c,1,"SYNTHETIC_KEY",k.getPublic()),p,c,()->SAFE)) {
                assertEquals(SYNTHETIC_READ_DENIED,o.observe(r,CALLER,sign(r,k),SyntheticOwner.Fault.NONE).outcome());
            }
            assertEquals(0,p.attemptedSends());assertEquals(0,f.attempts.get());assertEquals("CONSUMED",local.state(r.id()));
        }
    }
}
