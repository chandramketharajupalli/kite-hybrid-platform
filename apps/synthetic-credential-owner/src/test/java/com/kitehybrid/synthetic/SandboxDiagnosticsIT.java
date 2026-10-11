package com.kitehybrid.synthetic;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded diagnostic artifacts stay in disposable tmpfs; only metadata leaves it. */
@TestMethodOrder(MethodOrderer.MethodName.class)
@Timeout(100)
class SandboxDiagnosticsIT {
    static final String RUN=UUID.randomUUID().toString();
    static final Path WORK=Path.of("target/phase1312-recovery",RUN).toAbsolutePath();
    static final String TMPFS="rw,noexec,nosuid,nodev,size=48m,mode=1777";
    static FileChannel lockChannel;
    static FileLock lock;
    static boolean cleanupUncertain;
    static String image;
    static final List<String> owned=new ArrayList<>();
    record Command(int exit,String output) { }
    static Command command(String... args) throws Exception {
        Path log=Files.createTempFile(WORK,"command-",".log");
        var p=new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if(!p.waitFor(45,TimeUnit.SECONDS)){p.destroyForcibly();fail("DIAGNOSTIC_COMMAND_TIMEOUT");}
        String output=Files.readString(log,StandardCharsets.UTF_8).strip();
        assertFalse(DiagnosticsProbe.contains(log,DiagnosticsProbe.canary()),"DIAGNOSTIC_CANARY_LEAK");
        assertFalse(output.contains(HexFormat.of().formatHex(DiagnosticsProbe.canary())),"DIAGNOSTIC_CANARY_LEAK");
        assertFalse(output.contains(Base64.getEncoder().encodeToString(DiagnosticsProbe.canary())),"DIAGNOSTIC_CANARY_LEAK");
        assertFalse(output.contains(SandboxWitnessProbe.authentication()),"OWNER_CANARY_LEAK");
        return new Command(p.exitValue(),output);
    }
    static String ok(String... args) throws Exception {var r=command(args);assertEquals(0,r.exit(),r.output());return r.output();}
    @BeforeAll static void build() throws Exception {
        Files.createDirectories(WORK);
        lockChannel=FileChannel.open(WORK.getParent().resolve("budget.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        lock=lockChannel.tryLock();assertNotNull(lock,"ANOTHER_DIAGNOSTIC_COORDINATOR_ACTIVE");
        assertEquals("",ok("docker","ps","-a","--filter","name=phase1312-diagnostic-","--format","{{.Names}}"),"PRIOR_DIAGNOSTIC_REQUIRES_RECONCILIATION");
        Path context=WORK.resolve("context");Files.createDirectories(context);
        Files.copy(Path.of("target/test-classes/com/kitehybrid/synthetic/DiagnosticsProbe.class"),context.resolve("DiagnosticsProbe.class"),StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(context.resolve("Dockerfile"),"FROM "+JavaSandboxIT.BASE+"\nCOPY DiagnosticsProbe.class /fixture/com/kitehybrid/synthetic/DiagnosticsProbe.class\nUSER 65534:65534\nENTRYPOINT [\"/opt/java/openjdk/bin/java\"]\n");
        ok("docker","image","inspect",JavaSandboxIT.BASE);
        ok("docker","build","--pull=false","--network=none","-t","synthetic-phase1312-diagnostics:local",context.toString());
        image=ok("docker","image","inspect","--format","{{.Id}}","synthetic-phase1312-diagnostics:local");
        Files.writeString(WORK.resolve("image-inspect.json"),ok("docker","image","inspect",image));
    }
    static List<String> javaArgs(String mode,String... flags) {
        var list=new ArrayList<>(List.of("/opt/java/openjdk/bin/java","-Xmx24m","-XX:ActiveProcessorCount=1","-XX:+DisableAttachMechanism",
                "-XX:-CreateCoredumpOnCrash","-Djava.io.tmpdir=/scratch","--limit-modules","java.base"));
        list.addAll(List.of(flags));list.addAll(List.of("-cp","/fixture","com.kitehybrid.synthetic.DiagnosticsProbe",mode));return list;
    }
    static void event(String value) throws Exception {
        Files.writeString(WORK.resolve("lifecycle.txt"),value+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    static void requireBudget(int slots,int workloads) {
        assertTrue(slots<3 && workloads<3,"WORKLOAD_BUDGET_DENIED");
    }
    static void budget(boolean creating) throws Exception {
        assertFalse(cleanupUncertain,"CLEANUP_UNCERTAIN_NO_NEW_FIXTURE");
        String running=ok("docker","ps","--format","{{.ID}}|{{.Image}}");int workloads=0;
        for(String line:running.lines().toList()) {
            String[] fields=line.split("\\|",-1);assertEquals(2,fields.length);
            if(!fields[1].equals("testcontainers/ryuk:0.12.0"))workloads++;
        }
        event("BUDGET;OWNED="+owned.size()+";RUNNING_WORKLOADS="+workloads+";CREATING="+creating);
        requireBudget(creating?owned.size():owned.size()-1,workloads);
    }
    static synchronized String container(String mode,String... flags) throws Exception {
        budget(true);
        String name="phase1312-diagnostic-"+UUID.randomUUID();owned.add(name);
        var args=new ArrayList<>(List.of("docker","create","--pull=never","--name",name,"--label=phase1312.run="+RUN,"--user=65534:65534","--network=none",
                "--cap-drop=ALL","--security-opt=no-new-privileges:true","--security-opt=seccomp="+JavaSandboxIT.POLICY,
                "--read-only","--memory=256m","--cpus=1","--pids-limit=64","--ipc=private","--ulimit=core=0:0",
                "--tmpfs=/scratch:"+TMPFS,"--entrypoint=/opt/java/openjdk/bin/java",image));
        args.addAll(javaArgs(mode,flags).subList(1,javaArgs(mode,flags).size()));ok(args.toArray(String[]::new));
        assertEquals(image,ok("docker","inspect","--format","{{.Image}}",name));
        assertEquals("65534:65534|false|true|none||private|268435456|1000000000|64|[]",
                ok("docker","inspect","--format","{{.Config.User}}|{{.HostConfig.Privileged}}|{{.HostConfig.ReadonlyRootfs}}|{{.HostConfig.NetworkMode}}|{{.HostConfig.PidMode}}|{{.HostConfig.IpcMode}}|{{.HostConfig.Memory}}|{{.HostConfig.NanoCpus}}|{{.HostConfig.PidsLimit}}|{{json .Mounts}}",name));
        checkScratch(name);requireDiff(ok("docker","diff",name));
        Files.writeString(WORK.resolve(name+"-inspect.json"),ok("docker","inspect",name));event("CREATED;"+name);return name;
    }
    static void checkScratch(String name) throws Exception {
        assertEquals("{\"/scratch\":\""+TMPFS+"\"}",ok("docker","inspect","--format","{{json .HostConfig.Tmpfs}}",name));
        assertEquals("true|[]",ok("docker","inspect","--format","{{.HostConfig.ReadonlyRootfs}}|{{json .Mounts}}",name));
    }
    static void requireDiff(String diff) {
        assertTrue(diff.isEmpty()||diff.equals("A /scratch"),"DUMP_ESCAPED_TMPFS");
    }
    static synchronized Command start(String name,boolean attach) throws Exception {
        assertTrue(owned.contains(name));budget(false);event("STARTING;"+name);
        return attach?command("docker","start","--attach",name):command("docker","start",name);
    }
    static String hold() throws Exception {
        String name=container("HOLD");var started=start(name,false);assertEquals(0,started.exit(),started.output());
        for(int i=0;i<40;i++){if(ok("docker","logs",name).contains("DIAGNOSTIC_READY"))return name;Thread.sleep(100);}
        fail("DIAGNOSTIC_NOT_READY");return name;
    }
    static Command exec(String name,String mode,String... flags) throws Exception {
        var args=new ArrayList<>(List.of("docker","exec",name));args.addAll(javaArgs(mode,flags));return command(args.toArray(String[]::new));
    }
    static String scan(String name) throws Exception {
        var r=exec(name,"SCAN");assertEquals(0,r.exit(),r.output());Files.writeString(WORK.resolve(name+"-artifacts.txt"),r.output()+"\n");return r.output();
    }
    static synchronized void disposeAll(boolean injectAssertion) throws Exception {
        Throwable failure=null;
        for(String name:List.copyOf(owned)) {
            try {
                ok("docker","logs",name);checkScratch(name);String diff=ok("docker","diff",name);
                Files.writeString(WORK.resolve(name+"-diff.txt"),diff);requireDiff(diff);
                if(injectAssertion)fail("SYNTHETIC_INJECTED_AUDIT_ASSERTION");
            } catch(Throwable problem) {if(failure==null)failure=problem;else failure.addSuppressed(problem);}
            finally {
                try {
                    // Only this run's names and ownership label can be removed. Daemon errors fail closed.
                    String existing=ok("docker","ps","-a","--filter","name=^/"+name+"$","--format","{{.Names}}");
                    if(!existing.isEmpty()) {
                        assertEquals(name,existing);
                        assertEquals(name,ok("docker","ps","-a","--filter","label=phase1312.run="+RUN,"--filter","name=^/"+name+"$","--format","{{.Names}}"));
                        Files.writeString(WORK.resolve(name+"-final-inspect.json"),ok("docker","inspect",name));
                        ok("docker","rm","--force",name);
                    }
                    assertEquals("",ok("docker","ps","-a","--filter","name=^/"+name+"$","--format","{{.Names}}"));
                    owned.remove(name);event("REMOVED_VERIFIED;"+name);
                } catch(Throwable problem) {
                    cleanupUncertain=true;if(failure==null)failure=problem;else failure.addSuppressed(problem);
                }
            }
        }
        if(failure instanceof Error error)throw error;
        if(failure instanceof Exception exception)throw exception;
    }
    @AfterEach void dispose() throws Exception {disposeAll(false);}
    @AfterAll static void finish() throws Exception {
        try {disposeAll(false);assertTrue(owned.isEmpty());event("FINAL_OWNED=0");}
        finally {if(lock!=null)lock.close();if(lockChannel!=null)lockChannel.close();}
    }
    @Test void aMeasureLsmAndNamespaceWithoutMislabelingAbsence() throws Exception {
        String name=container("MEASURE");var started=start(name,true);assertEquals(0,started.exit(),started.output());String result=started.output();
        assertTrue(result.contains("UID_MAP=")&&result.contains("GID_MAP=")&&result.contains("LSM_LABEL="));
        assertTrue(result.contains("JFR_MODULE=ABSENT_FROM_RESTRICTED_RUNTIME"));
        Files.writeString(WORK.resolve("effective-controls.txt"),result+"\n");
    }
    @Test void bHeapCanaryIsConfinedAndNeverExported() throws Exception {
        String name=hold();var r=exec(name,"OOM_ALLOWED","-XX:+HeapDumpOnOutOfMemoryError","-XX:HeapDumpPath=/scratch/heap.hprof");
        assertEquals(0,r.exit(),r.output());assertTrue(r.output().contains("BOUNDED_OOM_OBSERVED"));
        String result=scan(name);assertTrue(result.contains("ARTIFACT=heap.hprof;")&&result.contains("CANARY_PRESENT=true"));
        assertTrue(result.contains("ARTIFACT_COUNT=1"));
    }
    @Test void cChangedHeapSinkToRootIsDenied() throws Exception {
        String name=hold();var r=exec(name,"OOM_DENIED","-XX:+HeapDumpOnOutOfMemoryError","-XX:HeapDumpPath=/forbidden.hprof");
        assertEquals(0,r.exit(),r.output());assertTrue(r.output().contains("Read-only file system"));
        assertEquals("ARTIFACT_COUNT=0",scan(name));
    }
    @Test void dNativeCrashErrorLogConfinedWithoutCoreExport() throws Exception {
        String name=hold();var r=exec(name,"CRASH","--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED","-XX:ErrorFile=/scratch/hs_err.log");
        assertNotEquals(0,r.exit());assertTrue(r.output().contains("SIGSEGV"),r.output());
        String result=scan(name);assertTrue(result.contains("ARTIFACT=hs_err.log;")&&result.contains("CANARY_PRESENT=false"));
        assertTrue(result.contains("ARTIFACT_COUNT=1"));
    }
    @Test void eChangedNativeErrorSinkCannotWriteRootOrFallback() throws Exception {
        String name=hold();var r=exec(name,"CRASH","--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED","-XX:ErrorFile=/forbidden.log");
        assertNotEquals(0,r.exit());assertTrue(r.output().contains("SIGSEGV"),r.output());assertEquals("ARTIFACT_COUNT=0",scan(name));
    }
    static void requireAttachFilesystemDenial(Command result) {
        assertEquals(1,result.exit());
        assertEquals(List.of("1:","java.io.IOException: Read-only file system",
                "at java.base/java.io.UnixFileSystem.createFileExclusively0(Native Method)",
                "at java.base/java.io.UnixFileSystem.createFileExclusively(UnixFileSystem.java:258)",
                "at java.base/java.io.File.createNewFile(File.java:1045)",
                "at jdk.attach/sun.tools.attach.VirtualMachineImpl.createAttachFile(VirtualMachineImpl.java:243)",
                "at jdk.attach/sun.tools.attach.VirtualMachineImpl.<init>(VirtualMachineImpl.java:84)",
                "at jdk.attach/sun.tools.attach.AttachProviderImpl.attachVirtualMachine(AttachProviderImpl.java:58)",
                "at jdk.attach/com.sun.tools.attach.VirtualMachine.attach(VirtualMachine.java:207)",
                "at jdk.jcmd/sun.tools.jcmd.JCmd.executeCommandForPid(JCmd.java:113)",
                "at jdk.jcmd/sun.tools.jcmd.JCmd.main(JCmd.java:97)"),result.output().lines().map(String::strip).toList());
    }
    @Test void fAttachMarkerFilesystemDenialPreventsDiagnosticExport() throws Exception {
        String name=hold();var r=command("docker","exec",name,"/opt/java/openjdk/bin/jcmd","1","VM.flags");
        requireAttachFilesystemDenial(r);
        assertEquals("ARTIFACT_COUNT=0",scan(name));
    }
    @Test void gAssertionFailureStillRemovesEveryOwnedContainer() throws Exception {
        String a=hold(),b=hold();var failure=assertThrows(AssertionError.class,()->disposeAll(true));
        assertTrue(failure.getMessage().contains("SYNTHETIC_INJECTED_AUDIT_ASSERTION"));assertEquals(1,failure.getSuppressed().length);
        assertTrue(owned.isEmpty());assertFalse(cleanupUncertain);
        for(String name:List.of(a,b))assertEquals("",ok("docker","ps","-a","--filter","name=^/"+name+"$","--format","{{.Names}}"));
    }
    @Test void hFourthWorkloadDeniedBeforeCreation() throws Exception {
        hold();hold();hold();assertEquals(3,owned.size());
        var failure=assertThrows(AssertionError.class,()->container("HOLD"));
        assertTrue(failure.getMessage().contains("WORKLOAD_BUDGET_DENIED"));assertEquals(3,owned.size());
        assertEquals(3,ok("docker","ps","--filter","label=phase1312.run="+RUN,"--format","{{.Names}}").lines().count());
        event("FOURTH_DENIED_BEFORE_CREATE;OWNED=3");
    }
    @Test void iUnexpectedDiffAndAttachErrorsNeverAccepted() {
        for(String diff:List.of("A /scratch/heap.hprof","C /scratch","A /other","A /scratch\nA /other"))assertThrows(AssertionError.class,()->requireDiff(diff));
        for(Command r:List.of(new Command(0,""),new Command(1,"java.io.IOException: Read-only file system"),new Command(1,"AttachNotSupportedException"),new Command(1,"timeout")))
            assertThrows(AssertionError.class,()->requireAttachFilesystemDenial(r));
        assertThrows(AssertionError.class,()->requireBudget(0,3));assertThrows(AssertionError.class,()->requireBudget(3,0));
    }
}
