package com.kitehybrid.gatea.adapters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static com.kitehybrid.gatea.adapters.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class StoreProcessIT {
    @TempDir Path temp;
    Path store(String name) { Path root=temp.resolve(name);new FileAttemptAuthority(root,STORE,AUTH).initializeFixture();return root; }
    Process start(Path root,String event) throws Exception {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx128m","-cp",
            System.getProperty("java.class.path"),StoreProcessProbe.class.getName(),root.toString(),event).redirectErrorStream(true).start();
    }
    int finish(Process child) throws Exception {
        try { assertTrue(child.waitFor(30,TimeUnit.SECONDS),"Child exceeded resource budget");
            String output=new String(child.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(output.contains("fixture-owner-reference"));assertFalse(output.contains(AUTH.attempt().toString()));
            assertFalse(output.contains("fixture-key"));return child.exitValue();
        } finally { if(child.isAlive()){child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);} }
    }
    @Test void concurrentOwnersExactlyOneSyntheticObservation() throws Exception {
        Path root=store("competing");Process a=start(root,"NONE"),b=start(root,"NONE");
        int x,y;try{x=finish(a);y=finish(b);}finally{if(a.isAlive())a.destroyForcibly();if(b.isAlive())b.destroyForcibly();}
        assertTrue((x==0&&y==42)||(x==42&&y==0));assertEquals(42,finish(start(root,"NONE")));
    }
    @ParameterizedTest @ValueSource(strings={"AFTER_CREATE","AFTER_FORCE","AFTER_OBSERVATION"})
    void crashAfterReservationRemainsConsumed(String event) throws Exception {
        Path root=store("crash");assertEquals(23,finish(start(root,event)));assertEquals(42,finish(start(root,"NONE")));
    }
    @Test void crashBeforeReservationHasNoObservationAndMayResume() throws Exception {
        Path root=store("before");assertEquals(23,finish(start(root,"BEFORE_CREATE")));
        assertFalse(Files.exists(root.resolve("spent")));assertEquals(0,finish(start(root,"NONE")));
    }
    @Test void corruptRetirementNeverReopens() throws Exception {
        Path root=store("corrupt");Files.writeString(root.resolve("spent"),"corrupt");assertEquals(42,finish(start(root,"NONE")));
    }
    @Test void restartAfterLocalPolicyRollbackStillDeniedByStore() throws Exception {
        Path root=store("rollback");assertEquals(0,finish(start(root,"NONE")));assertEquals(42,finish(start(root,"NONE")));
    }
    @Test void allAuthorityRollbackCounterexampleIsRetained() throws Exception {
        Path root=store("all-rollback");byte[] identity=Files.readAllBytes(root.resolve("identity"));
        assertEquals(0,finish(start(root,"NONE")));
        // Only this test-owned directory. Restoring all trusted local state models an administrative rollback.
        Files.delete(root.resolve("spent"));Files.delete(root.resolve("outcome"));assertArrayEquals(identity,Files.readAllBytes(root.resolve("identity")));
        assertEquals(0,finish(start(root,"NONE"))); // Explicit limitation, never a production guarantee.
    }
}
