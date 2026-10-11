package com.kitehybrid.synthetic;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

class IsolationLedgerIT {
    static PostgreSQLContainer<?> db;
    static DisposableLedger ledger;
    @BeforeAll static void start() throws Exception {db=DisposableLedger.container();db.start();ledger=DisposableLedger.initialize(db);}
    @AfterAll static void stop() throws Exception {if(db!=null)db.stop();if(ledger!=null)ledger.journal.destroyFixture();}

    @ParameterizedTest @ValueSource(strings={"BEFORE_JOURNAL_CREATE","AFTER_JOURNAL_CREATE","AFTER_JOURNAL_WRITE",
        "AFTER_JOURNAL_FORCE","BEFORE_RESERVATION_COMMIT","AFTER_RESERVATION_COMMIT",
        "BEFORE_CONSUMPTION_COMMIT","AFTER_CONSUMPTION_COMMIT"})
    void parentKillAtPersistenceBoundaryNeverRevivesRetiredId(String stage) throws Exception {
        UUID id=UUID.randomUUID();var process=worker(db,ledger,id,stage);
        try {awaitStage(process,stage);}
        finally {kill(process);}
        boolean unissued=stage.equals("BEFORE_JOURNAL_CREATE");
        assertEquals(!unissued,Files.exists(ledger.journal.path(id)));
        String expected=switch(stage){case "AFTER_RESERVATION_COMMIT","BEFORE_CONSUMPTION_COMMIT"->"RESERVED";
            case "AFTER_CONSUMPTION_COMMIT"->"CONSUMED";default->"ABSENT";};
        assertEquals(expected,ledger.state(id));
        var restarted=new DisposableLedger(db.getMappedPort(5432),ledger.marker(),DisposableLedger.Fault.NONE);
        assertEquals(unissued,restarted.consume(LedgerWorker.request(id)));
        assertFalse(restarted.consume(LedgerWorker.request(id)));
    }

    @Test void truncatedOrCorruptSpentContentsNeverRestoreAvailability() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());assertTrue(ledger.consume(r));
        Files.write(ledger.journal.path(r.id()),new byte[0],StandardOpenOption.TRUNCATE_EXISTING);
        assertFalse(ledger.consume(r));
        Files.writeString(ledger.journal.path(r.id()),"SYNTHETIC_STALE_CONTENT",StandardOpenOption.TRUNCATE_EXISTING);
        assertFalse(ledger.consume(r));assertEquals("CONSUMED",ledger.state(r.id()));
    }

    @Test void createErrorAndInjectedWriteExceptionGrantNothing() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());Path path=ledger.journal.path(r.id());
        Files.createDirectory(path);
        try {assertFalse(ledger.consume(r));assertEquals("ABSENT",ledger.state(r.id()));}
        finally {Files.delete(path);} // Only this test-owned obstruction, never a spent marker.
        var next=LedgerWorker.request(UUID.randomUUID());
        var faulty=new DisposableLedger(db.getMappedPort(5432),ledger.marker(),DisposableLedger.Fault.NONE,
            point->{if(point.equals("AFTER_JOURNAL_CREATE"))throw new UncheckedIOException(new IOException("SYNTHETIC_DISK_ERROR"));});
        assertFalse(faulty.consume(next));assertEquals(0,Files.size(ledger.journal.path(next.id())));
        assertFalse(ledger.consume(next));assertEquals("ABSENT",ledger.state(next.id()));
    }

    @Test void databaseOnlyLogicalRollbackIsFencedBySurvivingJournal() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());assertTrue(ledger.consume(r));eraseFixtureRow(r.id());
        assertFalse(ledger.consume(r));assertEquals("ABSENT",ledger.state(r.id()));
    }

    @Test void journalOnlyLogicalRollbackIsFencedBySurvivingDatabase() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());assertTrue(ledger.consume(r));
        Files.delete(ledger.journal.path(r.id())); // Deliberate privileged fixture rollback, not recovery.
        assertFalse(ledger.consume(r));assertEquals("CONSUMED",ledger.state(r.id()));
    }

    @Test void wholeStoreLogicalRollbackCounterexampleRemainsAnOpenSecurityGap() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());assertTrue(ledger.consume(r));
        eraseFixtureRow(r.id());Files.delete(ledger.journal.path(r.id()));
        // Both stores now present their pre-attempt state. No external monotonic anchor exists.
        var restarted=new DisposableLedger(db.getMappedPort(5432),ledger.marker(),DisposableLedger.Fault.NONE);
        assertTrue(restarted.consume(r),"SYNTHETIC_COUNTEREXAMPLE_CHANGED_REVIEW_REQUIRED");
        assertFalse(restarted.consume(r)); // NO HTTP: this demonstrates a second grant, not a safe recovery.
    }

    static void eraseFixtureRow(UUID id) throws Exception {
        try(var c=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:"+db.getMappedPort(5432)
            +"/synthetic_owner_only?connectTimeout=2&socketTimeout=3","synthetic_fixture","synthetic_fixture");
            var p=c.prepareStatement("DELETE FROM attempts WHERE id=?")) {
            p.setQueryTimeout(3);p.setObject(1,id);assertEquals(1,p.executeUpdate());
        }
    }

    static Process worker(PostgreSQLContainer<?> fixture,DisposableLedger store,UUID id,String stage,String... peerPort) throws Exception {
        var command=new ArrayList<>(List.of(IsolationSecurityTest.javaExecutable(),"-Duser.timezone=UTC","-cp",
            System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),IsolationWorker.class.getName(),
            Integer.toString(fixture.getMappedPort(5432)),store.marker(),id.toString(),stage));
        command.addAll(List.of(peerPort));
        return new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    static void awaitStage(Process process,String stage) throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var line=pool.submit(()->new BufferedReader(new InputStreamReader(process.getInputStream(),java.nio.charset.StandardCharsets.US_ASCII)).readLine());
            try {assertEquals("SYNTHETIC_STAGE="+stage,line.get(25,TimeUnit.SECONDS));}
            catch(Exception failure){kill(process);throw failure;}
        }
        assertTrue(process.isAlive(),"SYNTHETIC_WORKER_LEFT_BARRIER");
    }
    static void kill(Process process) throws Exception {
        if(process.isAlive())process.destroyForcibly();
        assertTrue(process.waitFor(5,TimeUnit.SECONDS),"SYNTHETIC_WORKER_KILL_TIMEOUT");
    }
}
