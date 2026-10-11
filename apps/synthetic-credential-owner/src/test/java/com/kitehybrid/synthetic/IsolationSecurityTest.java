package com.kitehybrid.synthetic;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.kitehybrid.synthetic.SyntheticSecurityTest.*;

class IsolationSecurityTest {
    @Test void availabilityRecoveryCannotClearClockRollbackQuarantine() throws Exception {
        var clock = new MutableClock(); var key = key(); var request = request(new LoopbackPeer(1,100));
        var halt = new SyntheticHalt(clock,1,"SYNTHETIC_KEY",key.getPublic());
        var lease = halt.admit(sign(request,key),request); assertNotNull(lease);
        clock.value.set(NOW.minusSeconds(1)); assertFalse(halt.recheck(lease,request));
        clock.value.set(NOW.plusSeconds(1)); halt.available(true);
        assertFalse(halt.recheck(lease,request));
        assertNull(halt.admit(sign(request,key),request));
    }

    @Test void availabilityRecoveryCannotClearConflictingKeyQuarantine() throws Exception {
        var clock = new MutableClock(); var key = key(); var request = request(new LoopbackPeer(1,100));
        var halt = new SyntheticHalt(clock,1,"SYNTHETIC_KEY",key.getPublic());
        var other = key();
        assertThrows(IllegalArgumentException.class,()->halt.rotate("SYNTHETIC_KEY",other.getPublic()));
        halt.available(true);
        assertNull(halt.admit(sign(request,key),request));
    }

    @ParameterizedTest
    @ValueSource(strings={"http://[::1]:1234", "http://[::ffff:127.0.0.1]:1234",
        "http://127.1:1234", "http://0177.0.0.1:1234", "http://127.0.0.2:1234",
        "http://0.0.0.0:1234", "http://192.0.2.1:1234", "http://localhost:1234",
        "http://127.0.0.1.:1234", "http://127.0.0.1:1234@192.0.2.1",
        "https://127.0.0.1:1234", "file://127.0.0.1:1234"})
    void alternateAddressFormsAreRejectedWithoutResolutionOrConnection(String prefix) {
        var peer = new LoopbackPeer(1234,100);
        assertFalse(peer.accepts("GET",prefix+SyntheticOwner.PATH));
        assertEquals(0,peer.attemptedSends());
    }

    @Test void nestedFixtureExceptionsCannotEscapeIntoResultOrConsole() throws Exception {
        var peer = new LoopbackPeer(1,100); var clock = new MutableClock(); var key = key();
        var request = request(peer); var halt = new SyntheticHalt(clock,1,"SYNTHETIC_KEY",key.getPublic());
        String canary = "SYNTHETIC_ERROR_CANARY_"+UUID.randomUUID();
        var broken = new SyntheticOwner.Ledger() {
            public boolean consume(SyntheticOwner.Request r) { throw new AssertionError(); }
            public boolean consume(SyntheticOwner.Request r,String digest) {
                var error = new IllegalStateException(canary,new IOException(canary));
                error.addSuppressed(new IOException(canary)); throw error;
            }
            public void terminal(UUID id,SyntheticOwner.Outcome outcome) { throw new AssertionError(); }
        };
        var capture = new ByteArrayOutputStream(); var stdout = System.out; var stderr = System.err;
        SyntheticOwner.Result result;
        try(var owner = owner(broken,halt,peer,clock,()->SAFE); var sink = new PrintStream(capture)) {
            try { System.setOut(sink); System.setErr(sink);
                result = owner.observe(request,CALLER,sign(request,key),SyntheticOwner.Fault.NONE);
            } finally {System.setOut(stdout); System.setErr(stderr);}
        }
        assertEquals(0,capture.size()); assertEquals(0,peer.attemptedSends());
        assertEquals(SyntheticOwner.Outcome.PROTOTYPE_PRECONDITION_FAILED,result.outcome());
        assertFalse(result.toString().contains(canary));
        assertFalse(Thread.currentThread().getName().contains(canary));
    }

    @Test void javaBaseRestrictionIsNotAnOsFilesystemOrProcessSandbox() throws Exception {
        Path file = Files.createTempFile("synthetic-isolation-canary-",".txt");
        try {
            Files.writeString(file,"SYNTHETIC_TEST_ONLY");
            var process = new ProcessBuilder(javaExecutable(),"--limit-modules","java.base","-cp",
                System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                BaseOnlyPermissionsProbe.class.getName(),file.toString()).redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            try {assertTrue(process.waitFor(25,TimeUnit.SECONDS)); assertEquals(0,process.exitValue());}
            finally {if(process.isAlive())process.destroyForcibly().waitFor(5,TimeUnit.SECONDS);}
        } finally {Files.delete(file);}
    }

    static String javaExecutable() {
        Path executable = Path.of(System.getProperty("java.home"),"bin","java.exe");
        return Files.exists(executable)?executable.toString():Path.of(System.getProperty("java.home"),"bin","java").toString();
    }

    @Test void lostHarnessControlPipeTerminatesWorkerWithoutContinuingToDatabase() throws Exception {
        var journal=new AttemptJournal(UUID.randomUUID().toString());journal.initialize();
        UUID id=UUID.randomUUID();
        String marker=Files.readString(journal.identityPath());
        Process process=null;
        try(var readers=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                process=new ProcessBuilder(javaExecutable(),"-Duser.timezone=UTC","-cp",
                    System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                    IsolationWorker.class.getName(),"1",marker,id.toString(),"AFTER_JOURNAL_CREATE")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                var child=process;
                var line=readers.submit(()->new BufferedReader(new InputStreamReader(child.getInputStream(),java.nio.charset.StandardCharsets.US_ASCII)).readLine());
                assertEquals("SYNTHETIC_STAGE=AFTER_JOURNAL_CREATE",line.get(25,TimeUnit.SECONDS));
                process.getOutputStream().close();
                assertTrue(process.waitFor(5,TimeUnit.SECONDS));assertEquals(29,process.exitValue());
                assertEquals(0,Files.size(journal.path(id)));assertFalse(journal.retire(id,"SYNTHETIC",false));
            } finally {if(process!=null&&process.isAlive())process.destroyForcibly().waitFor(5,TimeUnit.SECONDS);}
        } finally {journal.destroyFixture();}
    }

    /** A counterexample, not an isolation certification. Reads only its fixture canary. */
    public static final class BaseOnlyPermissionsProbe {
        public static void main(String[] args) throws Exception {
            if(!Files.readString(Path.of(args[0])).equals("SYNTHETIC_TEST_ONLY"))throw new IllegalStateException("SYNTHETIC_PROBE_FAILED");
            if(args.length==2)return;
            Path executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");
            var child=new ProcessBuilder(executable.toString(),"--limit-modules","java.base","-cp",System.getProperty("java.class.path"),
                BaseOnlyPermissionsProbe.class.getName(),args[0],"CHILD").redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            try {if(!child.waitFor(10,TimeUnit.SECONDS)||child.exitValue()!=0)throw new IllegalStateException("SYNTHETIC_CHILD_FAILED");}
            finally {if(child.isAlive())child.destroyForcibly().waitFor(5,TimeUnit.SECONDS);}
        }
    }
}
