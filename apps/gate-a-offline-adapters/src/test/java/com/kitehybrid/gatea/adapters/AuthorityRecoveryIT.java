package com.kitehybrid.gatea.adapters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static com.kitehybrid.gatea.adapters.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthorityRecoveryIT {
    @TempDir Path root;
    void init(){new AuthorityCheckpointJournal(root.resolve("local"),root.resolve("witness"),AUTH).initializeFixture();new FileAttemptAuthority(root.resolve("attempt"),STORE,AUTH).initializeFixture();}
    Process start(String action)throws Exception{return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx128m","-cp",System.getProperty("java.class.path"),AuthorityRecoveryProbe.class.getName(),root.toString(),action).redirectErrorStream(true).start();}
    int finish(Process p)throws Exception{try{assertTrue(p.waitFor(30,TimeUnit.SECONDS));String output=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(output.contains(AUTH.attempt().toString()));assertFalse(output.contains("fixture-key"));assertFalse(output.contains("fixture-owner-reference"));return p.exitValue();
        }finally{if(p.isAlive()){p.destroyForcibly();p.waitFor(5,TimeUnit.SECONDS);}}}
    @Test void twoProcessesCompareAndAdvanceSingleWinner()throws Exception{init();Process a=start("CAS"),b=start("CAS");try{int x=finish(a),y=finish(b);assertTrue(x==0&&y==42||x==42&&y==0);}finally{if(a.isAlive())a.destroyForcibly();if(b.isAlive())b.destroyForcibly();}assertEquals(42,finish(start("CHECK")));}
    @Test void revocationSurvivesRealProcessRestart()throws Exception{init();assertEquals(0,finish(start("REVOKE")));assertEquals(42,finish(start("CHECK")));}
    @Test void integratedObservationRemainsConsumedAfterRestart()throws Exception{init();assertEquals(0,finish(start("OBSERVE")));assertEquals(42,finish(start("OBSERVE")));}
    @ParameterizedTest @ValueSource(strings={"AFTER_WITNESS","AFTER_LOCAL"})
    void crashAfterReservationCannotResume(String point)throws Exception{init();assertEquals(23,finish(start(point)));assertEquals(42,finish(start("CHECK")));}
    @Test void beforeReservationCrashDoesNotConsume()throws Exception{init();assertEquals(23,finish(start("BEFORE_RESERVATION")));assertEquals(0,finish(start("OBSERVE")));}
    @Test void witnessSurvivesLocalRollback()throws Exception{init();assertEquals(0,finish(start("REVOKE")));Files.delete(root.resolve("local/00000000000000000001.record"));assertEquals(42,finish(start("CHECK")));}
    @Test void completeAuthorityRollbackCounterexample()throws Exception{init();assertEquals(0,finish(start("OBSERVE")));
        // Only owned temporary fixtures: restore every authority to its initial snapshot.
        for(String dir:new String[]{"local","witness"})try(var files=Files.list(root.resolve(dir))){for(Path p:files.toList())if(p.getFileName().toString().endsWith(".record")&&!p.getFileName().toString().equals("00000000000000000000.record"))Files.delete(p);}
        Files.delete(root.resolve("attempt/spent"));Files.delete(root.resolve("attempt/outcome"));
        assertEquals(0,finish(start("OBSERVE"))); // Explicit two-grant negative control.
    }
}
