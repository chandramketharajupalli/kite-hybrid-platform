package com.kitehybrid.synthetic;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GateAHaltLedgerIT {
    String marker;AttemptJournal journal;UUID id;
    @BeforeEach void setup() throws Exception {marker=UUID.randomUUID().toString();journal=new AttemptJournal(marker);journal.initialize();id=UUID.randomUUID();}
    @AfterEach void cleanup() throws Exception {journal.destroyFixture();}
    Process start(String stage) throws Exception {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",
                System.getProperty("java.class.path"),GateAHaltProcessProbe.class.getName(),marker,id.toString(),stage)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    int finish(Process p) throws Exception {
        try {assertTrue(p.waitFor(30,TimeUnit.SECONDS));return p.exitValue();}
        finally {if(p.isAlive()){p.destroyForcibly();p.waitFor(5,TimeUnit.SECONDS);}}
    }
    @ParameterizedTest @ValueSource(strings={"AFTER_JOURNAL_CREATE","AFTER_JOURNAL_WRITE","AFTER_JOURNAL_FORCE","BEFORE_DISPATCH","DURING_RESULT_DELIVERY","AFTER_PEER_OBSERVED"})
    void killAfterReservationOrSendNeverReopensAttempt(String stage) throws Exception {
        assertEquals(23,finish(start(stage)));assertTrue(Files.exists(journal.path(id)));assertEquals(7,finish(start("NONE")));
    }
    @Test void killBeforeReservationHasNoSendAndAllowsOneNewAttempt() throws Exception {
        assertEquals(23,finish(start("BEFORE_JOURNAL_CREATE")));assertFalse(Files.exists(journal.path(id)));
        assertEquals(0,finish(start("NONE")));assertEquals(7,finish(start("NONE")));
    }
    @Test void twoOwnerProcessesHaveExactlyOneWinner() throws Exception {
        Process a=start("NONE"),b=start("NONE");var exits=List.of(finish(a),finish(b));assertTrue(exits.contains(0));assertTrue(exits.contains(7));
    }
    @Test void completeAuthoritativeRollbackStillPermitsReuseCounterexample() throws Exception {
        assertEquals(0,finish(start("NONE")));Files.delete(journal.path(id));
        // Child fixture reconstitutes all synthetic authority. No real grant or network occurs.
        assertEquals(0,finish(start("NONE")));
    }
}
