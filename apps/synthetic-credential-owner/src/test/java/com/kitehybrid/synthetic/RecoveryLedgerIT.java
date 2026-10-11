package com.kitehybrid.synthetic;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryLedgerIT {
    static PostgreSQLContainer<?> db;
    static DisposableLedger ledger;
    @BeforeAll static void start() throws Exception {db=DisposableLedger.container();db.start();ledger=DisposableLedger.initialize(db);}
    @AfterAll static void stop() throws Exception {if(db!=null)db.stop();if(ledger!=null)ledger.journal.destroyFixture();}

    @ParameterizedTest @EnumSource(value=DisposableLedger.Fault.class,names={"PARTIAL_JOURNAL","BEFORE_RESERVATION","DURING_RESERVATION","AFTER_RESERVATION_BEFORE_ACK","BEFORE_COMMIT","AFTER_COMMIT_BEFORE_ACK"})
    void everyPersistenceWindowIsRetiredAcrossAdapterRestart(DisposableLedger.Fault fault) throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());var broken=new DisposableLedger(db.getMappedPort(5432),ledger.marker(),fault);
        assertFalse(broken.consume(r));assertTrue(Files.exists(ledger.journal.path(r.id())));
        String expected=switch(fault){case AFTER_RESERVATION_BEFORE_ACK,BEFORE_COMMIT->"RESERVED";case AFTER_COMMIT_BEFORE_ACK->"CONSUMED";default->"ABSENT";};
        assertEquals(expected,ledger.state(r.id()));
        var restarted=new DisposableLedger(db.getMappedPort(5432),ledger.marker(),DisposableLedger.Fault.NONE);
        assertFalse(restarted.consume(r));assertEquals(expected,ledger.state(r.id()));
    }
    @Test void connectionFailureBeforeReservationCannotBeReissued() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());int port;
        try(var socket=new java.net.ServerSocket(0,1,java.net.InetAddress.getByAddress(new byte[]{127,0,0,1}))){port=socket.getLocalPort();}
        assertFalse(new DisposableLedger(port,ledger.marker(),DisposableLedger.Fault.NONE).consume(r));
        assertEquals("ABSENT",ledger.state(r.id()));assertFalse(ledger.consume(r));
    }
    @Test void missingOrConflictingJournalIdentityNeverRecreatesStore() throws Exception {
        var journal=new AttemptJournal(UUID.randomUUID().toString());var id=UUID.randomUUID();assertFalse(journal.retire(id,"fixture",false));
        journal.initialize();byte[] identity=Files.readAllBytes(journal.identityPath());
        try {
            Files.writeString(journal.identityPath(),"SYNTHETIC_CONFLICT");assertFalse(journal.retire(id,"fixture",false));assertFalse(Files.exists(journal.path(id)));
        } finally {Files.write(journal.identityPath(),identity);journal.destroyFixture();}
    }
    @Test void alteredRequestOrHaltEvidenceCannotReuseAnIdentifier() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());assertTrue(ledger.consume(r,"a".repeat(64)));assertEquals("a".repeat(64),ledger.haltDigest(r.id()));
        assertFalse(ledger.consume(r,"b".repeat(64)));
        var changed=new SyntheticOwner.Request(r.id(),r.version(),"SYNTHETIC_NEW_INCARNATION",r.caller(),r.recipient(),r.account(),r.purpose(),r.method(),r.endpoint(),r.issued(),r.expires(),r.haltEpoch(),r.authority());
        assertFalse(ledger.consume(changed,"a".repeat(64)));assertEquals(r.fingerprint(),ledger.digest(r.id()));
    }
    @Test void illegalOrRepeatedOutcomeTransitionsAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class,()->ledger.terminal(UUID.randomUUID(),SyntheticOwner.Outcome.SYNTHETIC_READ_SUCCEEDED));
        var reserved=LedgerWorker.request(UUID.randomUUID());assertFalse(new DisposableLedger(db.getMappedPort(5432),ledger.marker(),DisposableLedger.Fault.BEFORE_COMMIT).consume(reserved));
        assertThrows(IllegalArgumentException.class,()->ledger.terminal(reserved.id(),SyntheticOwner.Outcome.SYNTHETIC_READ_SUCCEEDED));
        var consumed=LedgerWorker.request(UUID.randomUUID());assertTrue(ledger.consume(consumed));ledger.terminal(consumed.id(),SyntheticOwner.Outcome.SYNTHETIC_READ_OUTCOME_UNKNOWN);
        assertThrows(IllegalArgumentException.class,()->ledger.terminal(consumed.id(),SyntheticOwner.Outcome.SYNTHETIC_READ_SUCCEEDED));assertFalse(ledger.consume(consumed));
    }
}
