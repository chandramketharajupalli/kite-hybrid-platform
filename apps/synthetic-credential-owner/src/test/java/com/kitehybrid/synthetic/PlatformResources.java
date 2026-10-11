package com.kitehybrid.synthetic;

import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Test coordinator only: cooperative host budget, never an operational scheduler. */
final class PlatformResources {
    static final String RUN=UUID.randomUUID().toString(), LABEL="phase1313.run";
    static final Path WORK=Path.of("target/phase1313",RUN).toAbsolutePath();
    private static FileChannel channel;
    private static FileLock lock;
    private static boolean uncertain;
    private PlatformResources() { }
    static synchronized void initialize() throws Exception {
        if(lock!=null)return;
        Files.createDirectories(WORK);
        channel=FileChannel.open(WORK.getParent().resolve("budget.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        lock=channel.tryLock();assertNotNull(lock,"ANOTHER_PLATFORM_COORDINATOR_ACTIVE");
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try{lock.close();channel.close();}catch(Exception ignored){}}));
    }
    static String command(String... args) throws Exception {
        initialize();Path file=Files.createTempFile(WORK,"resource-",".log");
        var p=new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(file.toFile()).start();
        if(!p.waitFor(45,TimeUnit.SECONDS)){p.destroyForcibly();uncertain=true;fail("RESOURCE_COMMAND_TIMEOUT");}
        assertEquals(0,p.exitValue(),"RESOURCE_COMMAND_FAILED_SEE_RETAINED_LOG");return Files.readString(file).strip();
    }
    static void requireBudget(int workloads) {assertTrue(workloads>=0&&workloads<3,"WORKLOAD_BUDGET_DENIED");}
    static synchronized void beforeCreate() {
        try {checkBudget(null);} catch(Exception problem){throw new AssertionError("RESOURCE_BUDGET_UNVERIFIED",problem);}
    }
    private static void checkBudget(String startingId) throws Exception {
        initialize();assertFalse(uncertain,"RESOURCE_CLEANUP_UNCERTAIN");int count=0;
        String listing=command("docker","ps","-a","--no-trunc","--format","{{.ID}}|{{.State}}|{{.Image}}");
        for(String line:listing.lines().toList()) {
            String[] f=line.split("\\|",-1);assertEquals(3,f.length);
            if(!f[0].equals(startingId)&&!f[2].equals("testcontainers/ryuk:0.12.0")
                    &&Set.of("running","created","restarting","paused").contains(f[1]))count++;
        }
        Files.writeString(WORK.resolve("budget.txt"),"BEFORE="+count+";START="+(startingId!=null)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        requireBudget(count);
    }
    static synchronized String create(List<String> args) throws Exception {
        beforeCreate();String id=command(args.toArray(String[]::new));assertTrue(id.matches("[a-f0-9]{64}"));return id;
    }
    static synchronized void beforeStart(String id) throws Exception {checkBudget(id);}
    static synchronized void remove(String id) throws Exception {
        try {
            assertTrue(id.matches("[a-f0-9]{64}"));
            String owned=command("docker","ps","-a","--no-trunc","--filter","id="+id,"--filter","label="+LABEL+"="+RUN,"--format","{{.ID}}");
            assertEquals(id,owned,"RESOURCE_OWNERSHIP_UNVERIFIED");
            command("docker","rm","--force",id);
            assertEquals("",command("docker","ps","-a","--no-trunc","--filter","id="+id,"--format","{{.ID}}"));
            Files.writeString(WORK.resolve("removed.txt"),id+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        } catch(Exception|AssertionError failure){uncertain=true;throw failure;}
    }
}
