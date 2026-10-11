package com.kitehybrid.synthetic;

import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

class IsolationOwnerIT {
    static PostgreSQLContainer<?> db;
    static DisposableLedger ledger;
    @BeforeAll static void start() throws Exception {db=DisposableLedger.container();db.start();ledger=DisposableLedger.initialize(db);}
    @AfterAll static void stop() throws Exception {if(db!=null)db.stop();if(ledger!=null)ledger.journal.destroyFixture();}

    @ParameterizedTest @ValueSource(strings={"AFTER_OWNER_ACK","AFTER_RECEIPT","BEFORE_OUTCOME"})
    void parentKilledOwnerCannotDispatchAgainAfterRestart(String stage) throws Exception {
        var received=new CountDownLatch(1);var release=new CountDownLatch(1);
        Runnable afterReceive=()->{
            received.countDown();
            if(stage.equals("AFTER_RECEIPT"))try {
                if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("SYNTHETIC_RECEIPT_BARRIER_TIMEOUT");
            } catch(InterruptedException ex){Thread.currentThread().interrupt();}
        };
        try(var peer=new OwnerIT.FakePeer(OwnerIT.response(200,OwnerIT.BODY),afterReceive,0,false)) {
            UUID id=UUID.randomUUID();
            var process=IsolationLedgerIT.worker(db,ledger,id,stage,Integer.toString(peer.listener.getLocalPort()));
            try {
                if(stage.equals("AFTER_RECEIPT"))assertTrue(received.await(25,TimeUnit.SECONDS),"SYNTHETIC_RECEIPT_MISSING");
                else IsolationLedgerIT.awaitStage(process,stage);
                assertTrue(process.isAlive());assertEquals("CONSUMED",ledger.state(id));
            } finally {IsolationLedgerIT.kill(process);release.countDown();}
            int expected=stage.equals("AFTER_OWNER_ACK")?0:1;
            assertEquals(expected,peer.attempts.get());
            if(expected==1)assertTrue(peer.received.get().endsWith("\r\n\r\n"));
            var replacement=IsolationLedgerIT.worker(db,ledger,id,"NO_BARRIER",Integer.toString(peer.listener.getLocalPort()));
            try {assertTrue(replacement.waitFor(25,TimeUnit.SECONDS));assertEquals(18,replacement.exitValue());}
            finally {IsolationLedgerIT.kill(replacement);}
            assertEquals(expected,peer.attempts.get());assertEquals("CONSUMED",ledger.state(id));
        } finally {release.countDown();}
    }
}
