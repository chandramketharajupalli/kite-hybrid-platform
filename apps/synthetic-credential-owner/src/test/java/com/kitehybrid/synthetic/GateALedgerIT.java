package com.kitehybrid.synthetic;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Disposable filesystem ledger integration. Processes never have a real transport. */
class GateALedgerIT {
    String marker; AttemptJournal journal; UUID id;
    @BeforeEach void setup() throws Exception {
        marker=UUID.randomUUID().toString();journal=new AttemptJournal(marker);journal.initialize();id=UUID.randomUUID();
    }
    @AfterEach void cleanup() throws Exception { journal.destroyFixture(); }
    Process start(String crashStage) throws Exception {
        String java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        return new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),GateAProcessProbe.class.getName(),
                marker,id.toString(),crashStage).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    int finish(Process process) throws Exception {
        try { assertTrue(process.waitFor(20,TimeUnit.SECONDS));return process.exitValue(); }
        finally { if(process.isAlive()) {process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);} }
    }
    @ParameterizedTest @ValueSource(strings={"AFTER_JOURNAL_CREATE","AFTER_JOURNAL_WRITE","AFTER_JOURNAL_FORCE"})
    void crashBeforeSendNeverReplaysAfterMarkerCreation(String stage) throws Exception {
        assertEquals(23,finish(start(stage)));assertTrue(Files.exists(journal.path(id)));
        assertEquals(7,finish(start("NONE")));assertEquals(7,finish(start("NONE")));
    }
    @Test void crashBeforeReservationAllowsSingleLaterAttemptWithNoPriorSend() throws Exception {
        assertEquals(23,finish(start("BEFORE_JOURNAL_CREATE")));assertFalse(Files.exists(journal.path(id)));
        assertEquals(0,finish(start("NONE")));assertEquals(7,finish(start("NONE")));
    }
    @Test void restartAndFailoverNeverRefundSuccessfulAttempt() throws Exception {
        assertEquals(0,finish(start("NONE")));assertEquals(7,finish(start("NONE")));assertEquals(7,finish(start("NONE")));
    }
    @Test void twoProcessesCannotBothConsumeSameAuthority() throws Exception {
        Process first=start("NONE"),second=start("NONE");
        var exits=List.of(finish(first),finish(second));assertTrue(exits.contains(0));assertTrue(exits.contains(7));
    }
    @Test void allAuthorityRollbackCounterexampleRemainsExplicit() throws Exception {
        assertEquals(0,finish(start("NONE")));
        // Restore the fixture's entire pre-attempt authority state. No independent witness exists.
        // This demonstrates why production anti-rollback is NOT established, not recovery guidance.
        Files.delete(journal.path(id));assertEquals(0,finish(start("NONE")));
    }
    @Test void uncertainForceAcknowledgmentConsumesWithoutGranting() throws Exception {
        var uncertain=new AttemptJournal(marker,stage->{if(stage.equals("AFTER_JOURNAL_FORCE"))throw new IllegalStateException("FIXTURE");});
        var boundary=new GateABoundary(uncertain,GateABoundaryTest.CLOCK);
        assertThrows(IllegalStateException.class,()->boundary.acquire("GET",GateABoundary.URL,null,
                GateABoundaryTest.approval(id),"fixture-owner",GateABoundaryTest.response()));
        assertEquals(0,boundary.fixtureDispatches());assertEquals(7,finish(start("NONE")));
    }
}
