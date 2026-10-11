package com.kitehybrid.synthetic;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit offline Docker fixture; never a runtime launcher. Uses one provenance-pinned local base. */
@TestMethodOrder(MethodOrderer.MethodName.class)
@Timeout(120)
class JavaSandboxIT {
    static final String BASE = "docker.io/library/eclipse-temurin@sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf";
    static final String IMAGE = "synthetic-phase1311-test:local";
    static String fixtureImageId;
    static final Path WORK = Path.of("target/phase1313-owner",PlatformResources.RUN).toAbsolutePath();
    static final Path POLICY = Path.of("src/test/resources/phase1311/owner-seccomp.json").toAbsolutePath();
    static final Map<String,String> CONTAINERS = new ConcurrentHashMap<>();
    static final Set<String> VOLUMES = ConcurrentHashMap.newKeySet();
    record Command(int exit, String output) { }
    static Command command(String... args) throws Exception {
        Path file = Files.createTempFile(WORK,"command-",".log");
        var process = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(file.toFile()).start();
        if (!process.waitFor(30,TimeUnit.SECONDS)) { process.destroyForcibly(); fail("SYNTHETIC_COMMAND_TIMEOUT"); }
        String output = Files.readString(file,StandardCharsets.UTF_8);
        assertFalse(output.contains(SandboxWitnessProbe.authentication()),"SYNTHETIC_CANARY_LEAK");
        assertFalse(output.contains(Base64.getEncoder().encodeToString(SandboxWitnessProbe.canary())),"SYNTHETIC_CANARY_LEAK");
        return new Command(process.exitValue(),output.strip());
    }
    static String ok(String... args) throws Exception { var result=command(args); assertEquals(0,result.exit(),result.output()); return result.output(); }
    @BeforeAll static void buildOfflineFixture() throws Exception {
        Files.createDirectories(WORK); Path context=WORK.resolve("context"); Files.createDirectories(context.resolve("classes/com/kitehybrid/synthetic"));
        Files.createDirectories(context.resolve("witness")); Files.writeString(context.resolve("witness/fixture-only"),"SYNTHETIC_ONLY\n");
        Files.copy(Path.of("target/synthetic-credential-owner-0.1.0-SNAPSHOT.jar"),context.resolve("owner.jar"),StandardCopyOption.REPLACE_EXISTING);
        try(var classes=Files.list(Path.of("target/test-classes/com/kitehybrid/synthetic"))) {
            for(Path file:classes.filter(p->p.getFileName().toString().startsWith("SandboxOwnerProbe")||p.getFileName().toString().startsWith("SandboxWitnessProbe")).toList())
                Files.copy(file,context.resolve("classes/com/kitehybrid/synthetic").resolve(file.getFileName()),StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(context.resolve("Dockerfile"),"FROM "+BASE+"\nCOPY owner.jar /fixture/owner.jar\nCOPY classes /fixture/classes\nCOPY --chown=65534:65534 witness /witness\nUSER 65534:65534\nENTRYPOINT [\"/opt/java/openjdk/bin/java\"]\n");
        // No RUN, package manager, mutable base tag, or networked build step.
        ok("docker","build","--pull=false","--network=none","--tag",IMAGE,context.toString());
        fixtureImageId=ok("docker","image","inspect","--format","{{.Id}}",IMAGE);
        assertTrue(fixtureImageId.matches("sha256:[a-f0-9]{64}"));
        Files.writeString(WORK.resolve("image-inspect.json"),ok("docker","image","inspect",IMAGE));
    }
    static List<String> create(String name,String network,String main,String... args) {
        var command=new ArrayList<>(List.of("docker","create","--pull=never","--name",name,"--label="+PlatformResources.LABEL+"="+PlatformResources.RUN,"--user","65534:65534",
                "--network",network,"--cap-drop=ALL","--security-opt=no-new-privileges:true","--security-opt=seccomp="+POLICY,
                "--read-only","--memory=256m","--cpus=1","--pids-limit=64","--ipc=private",
                "--tmpfs","/scratch:rw,noexec,nosuid,nodev,size=8m,mode=1777","--entrypoint","/opt/java/openjdk/bin/java"));
        command.add(fixtureImageId); command.addAll(List.of("-Xmx96m","-XX:ActiveProcessorCount=1","-Djava.io.tmpdir=/scratch","--limit-modules","java.base",
                "-cp","/fixture/owner.jar:/fixture/classes","com.kitehybrid.synthetic."+main)); command.addAll(List.of(args)); return command;
    }
    static void inspect(String name, String network, boolean witness) throws Exception {
        assertEquals(fixtureImageId,ok("docker","inspect","--format","{{.Image}}",name));
        if(network.startsWith("container:")) network="container:"+ok("docker","inspect","--format","{{.Id}}",network.substring(10));
        String format="{{.Config.User}}|{{.HostConfig.Privileged}}|{{.HostConfig.ReadonlyRootfs}}|{{.HostConfig.NetworkMode}}|{{.HostConfig.PidMode}}|{{.HostConfig.IpcMode}}|{{.HostConfig.Memory}}|{{.HostConfig.NanoCpus}}|{{.HostConfig.PidsLimit}}|{{json .HostConfig.CapDrop}}";
        assertEquals("65534:65534|false|true|"+network+"||private|268435456|1000000000|64|[\"ALL\"]",ok("docker","inspect","--format",format,name));
        String mounts=ok("docker","inspect","--format","{{json .Mounts}}",name);
        if(witness) { assertTrue(mounts.contains("\"Type\":\"volume\"")); assertFalse(mounts.contains("\"Type\":\"bind\"")); }
        else assertEquals("[]",mounts);
        String security=ok("docker","inspect","--format","{{json .HostConfig.SecurityOpt}}",name);
        assertTrue(security.contains("no-new-privileges:true") && security.contains("seccomp="));
        Files.writeString(WORK.resolve(name+"-inspect.json"),ok("docker","inspect",name));
    }
    static String owner(String network,String mode,UUID id,String publicKey) throws Exception {
        String name="phase1313-owner-"+UUID.randomUUID();
        var create=create(name,network,"SandboxOwnerProbe",mode,id.toString(),publicKey);
        String containerId=PlatformResources.create(create);CONTAINERS.put(name,containerId);
        try {
            inspect(containerId,network,false);PlatformResources.beforeStart(containerId);
            int expectedExit=mode.equals("UNCAUGHT")?1:0;
            var result=command("docker","start","--attach",containerId);assertEquals(expectedExit,result.exit(),result.output());
            assertEquals(Integer.toString(expectedExit),ok("docker","inspect","--format","{{.State.ExitCode}}",containerId));
            assertEquals(result.output(),ok("docker","logs",containerId));
            Files.writeString(WORK.resolve(name+"-result.txt"),result.output()+"\n");return result.output();
        } finally {remove(name);}
    }
    static void remove(String name) throws Exception {
        String id=CONTAINERS.get(name);if(id==null)return;
        PlatformResources.remove(id);CONTAINERS.remove(name,id);
    }
    static final class Witness implements AutoCloseable {
        final String name="phase1313-witness-"+UUID.randomUUID(),volume="phase1313-volume-"+UUID.randomUUID(); String pin;
        Witness() throws Exception {
            ok("docker","volume","create","--label="+PlatformResources.LABEL+"="+PlatformResources.RUN,volume);VOLUMES.add(volume);
            try {launch(true);} catch(Exception|AssertionError failure){try{close();}catch(Exception|AssertionError cleanup){failure.addSuppressed(cleanup);}throw failure;}
        }
        void launch(boolean initialize) throws Exception {
            var args=create(name,"none","SandboxWitnessProbe",initialize?"INITIALIZE":"RECOVER");
            args.add(2,"--mount=type=volume,src="+volume+",dst=/witness"); String id=PlatformResources.create(args);CONTAINERS.put(name,id);inspect(id,"none",true);PlatformResources.beforeStart(id);ok("docker","start",id);
            long until=System.nanoTime()+Duration.ofSeconds(20).toNanos(); String logs="";
            while(System.nanoTime()<until) { logs=ok("docker","logs",name); if(logs.startsWith("READY="))break; Thread.sleep(100); }
            assertTrue(logs.startsWith("READY="),"SYNTHETIC_WITNESS_NOT_READY"); String candidate=logs.substring(6);
            if(pin!=null)assertEquals(pin,candidate);pin=candidate;
        }
        String network() { return "container:"+name; }
        int receipts() throws Exception { return Integer.parseInt(owner(network(),"STATS",UUID.randomUUID(),pin).substring("RECEIPTS=".length())); }
        void restart() throws Exception { ok("docker","kill",CONTAINERS.get(name));remove(name);launch(false); }
        public void close() throws Exception {remove(name);removeVolume(volume);}
    }
    static void removeVolume(String volume) throws Exception {
        assertEquals(volume,ok("docker","volume","ls","--filter","label="+PlatformResources.LABEL+"="+PlatformResources.RUN,"--filter","name=^"+volume+"$","--format","{{.Name}}"));
        ok("docker","volume","rm",volume);VOLUMES.remove(volume);
        assertEquals("",ok("docker","volume","ls","--filter","name=^"+volume+"$","--format","{{.Name}}"));
    }
    @AfterEach void removeOwners() throws Exception {
        Throwable failure=null;
        for(String name:List.copyOf(CONTAINERS.keySet()))try{remove(name);}catch(Exception|AssertionError e){if(failure==null)failure=e;else failure.addSuppressed(e);}
        for(String volume:List.copyOf(VOLUMES))try{removeVolume(volume);}catch(Exception|AssertionError e){if(failure==null)failure=e;else failure.addSuppressed(e);}
        if(failure instanceof Exception e)throw e;if(failure instanceof Error e)throw e;
    }
    @Test void aJavaProcessObservesEnforcedIsolation() throws Exception {
        String result=owner("none","OS",UUID.randomUUID(),"");
        assertTrue(result.contains("FORK_EPERM_TCP_ENETUNREACH_DNS_UNKNOWN"));
    }
    @Test void bActualOwnerOneSendReplacementAndWitnessRestartDenyReplay() throws Exception {
        try(var witness=new Witness()) {
            UUID id=UUID.randomUUID(); String result=owner(witness.network(),"READ",id,witness.pin);
            assertTrue(result.contains("SYNTHETIC_READ_SUCCEEDED;SENDS=1;DELIVERY=OBSERVED_COMPLETED")); assertEquals(1,witness.receipts());
            assertTrue(owner(witness.network(),"READ",id,witness.pin).contains("SYNTHETIC_READ_DENIED;SENDS=0")); assertEquals(1,witness.receipts());
            witness.restart(); assertTrue(owner(witness.network(),"READ",id,witness.pin).contains("SYNTHETIC_READ_DENIED;SENDS=0")); assertEquals(0,witness.receipts());
        }
    }
    @Test void cExpiredRevokedConflictingHaltDenyBeforeRetirementOrSend() throws Exception {
        try(var witness=new Witness()) {
            for(String mode:List.of("EXPIRED","REVOKED","CONFLICT")) {
                UUID id=UUID.randomUUID(); assertTrue(owner(witness.network(),mode,id,witness.pin).contains("SYNTHETIC_READ_DENIED;SENDS=0"));
            }
            assertEquals(0,witness.receipts());
        }
    }
    @Test void dLostAcknowledgmentRetiresWithoutDispatchAndCannotRetry() throws Exception {
        try(var witness=new Witness()) {
            UUID id=UUID.randomUUID(); assertTrue(owner(witness.network(),"LOSE_ACK",id,witness.pin).contains("SYNTHETIC_READ_DENIED;SENDS=0"));
            assertTrue(owner(witness.network(),"READ",id,witness.pin).contains("SYNTHETIC_READ_DENIED;SENDS=0")); assertEquals(0,witness.receipts());
        }
    }
    @Test void eTwoIndependentOwnerJvmsHaveOneWinner() throws Exception {
        try(var witness=new Witness();var pool=Executors.newFixedThreadPool(2)) {
            UUID id=UUID.randomUUID();var first=pool.submit(()->owner(witness.network(),"READ",id,witness.pin));var second=pool.submit(()->owner(witness.network(),"READ",id,witness.pin));
            var results=List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(s->s.contains("SYNTHETIC_READ_SUCCEEDED;SENDS=1")).count());
            assertEquals(1,results.stream().filter(s->s.contains("SYNTHETIC_READ_DENIED;SENDS=0")).count());assertEquals(1,witness.receipts());
        }
    }
    @Test void fUncaughtSyntheticSecretIsRedactedFromContainerAndCommandLogs() throws Exception {
        assertEquals("SYNTHETIC_UNCAUGHT_REDACTED",owner("none","UNCAUGHT",UUID.randomUUID(),""));
    }
}
