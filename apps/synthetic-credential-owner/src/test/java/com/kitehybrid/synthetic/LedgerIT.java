package com.kitehybrid.synthetic;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LedgerIT {
    static PostgreSQLContainer<?> db;
    static DisposableLedger ledger;
    @BeforeAll static void start() throws Exception { db=DisposableLedger.container();db.start();ledger=DisposableLedger.initialize(db); }
    @AfterAll static void stop() { if(db!=null)db.stop(); }
    @Test @Order(1) void durableSettingsAndBindingAndNoIdReuse() throws Exception {
        ledger.verifyDurabilitySettings(); var r=LedgerWorker.request(UUID.randomUUID());
        assertTrue(ledger.consume(r));assertEquals("CONSUMED",ledger.state(r.id()));assertEquals(r.fingerprint(),ledger.digest(r.id()));
        assertFalse(ledger.consume(r));
        var changed=new SyntheticOwner.Request(r.id(),r.version(),"SYNTHETIC_OTHER",r.caller(),r.recipient(),r.account(),r.purpose(),r.method(),r.endpoint(),r.issued(),r.expires(),2,r.authority());
        assertFalse(ledger.consume(changed));ledger.terminal(r.id(),SyntheticOwner.Outcome.SYNTHETIC_READ_OUTCOME_UNKNOWN);
        assertFalse(ledger.consume(r));
    }
    @Test @Order(2) void rollbackAfterReservationNeverReopensAttempt() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());var faulty=new DisposableLedger(db.getMappedPort(5432),ledger.marker(),DisposableLedger.Fault.BEFORE_COMMIT);
        assertFalse(faulty.consume(r));assertEquals("RESERVED",ledger.state(r.id()));assertFalse(ledger.consume(r));
    }
    @Test @Order(3) void lostCommitAckIsConsumedWithoutGrant() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());var faulty=new DisposableLedger(db.getMappedPort(5432),ledger.marker(),DisposableLedger.Fault.AFTER_COMMIT_BEFORE_ACK);
        assertFalse(faulty.consume(r));assertEquals("CONSUMED",ledger.state(r.id()));assertFalse(ledger.consume(r));
    }
    @Test @Order(4) void eightConcurrentConnectionsHaveOneWinner() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(8)) {
            var futures=new ArrayList<Future<Boolean>>();
            for(int i=0;i<8;i++)futures.add(pool.submit(()->{gate.await();return ledger.consume(r);}));
            gate.countDown();int wins=0;for(var f:futures)if(f.get(15,TimeUnit.SECONDS))wins++;
            assertEquals(1,wins);
        }
    }
    static Process worker(UUID id, DisposableLedger.Fault fault) throws Exception {
        String executable=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        if(!java.nio.file.Files.exists(Path.of(executable)))executable=Path.of(System.getProperty("java.home"),"bin","java").toString();
        return new ProcessBuilder(executable,"-Duser.timezone=UTC","-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                LedgerWorker.class.getName(),Integer.toString(db.getMappedPort(5432)),ledger.marker(),id.toString(),fault.name())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    static int await(Process p) throws Exception {
        if(!p.waitFor(25,TimeUnit.SECONDS)){p.destroyForcibly();fail("SYNTHETIC_WORKER_TIMEOUT");}return p.exitValue();
    }
    @Test @Order(5) void competingProcessesAndCrashAfterAckRemainSpentAcrossRestart() throws Exception {
        UUID id=UUID.randomUUID();Process a=worker(id,DisposableLedger.Fault.NONE), b=worker(id,DisposableLedger.Fault.NONE);
        assertEquals(Set.of(17,18),Set.of(await(a),await(b)));
        assertEquals(18,await(worker(id,DisposableLedger.Fault.NONE)));
        assertFalse(new DisposableLedger(db.getMappedPort(5432),ledger.marker(),DisposableLedger.Fault.NONE).consume(LedgerWorker.request(id)));
    }
    @Test @Order(6) void abruptWorkerWithUncertainAckCannotReclaim() throws Exception {
        UUID id=UUID.randomUUID();assertEquals(18,await(worker(id,DisposableLedger.Fault.AFTER_COMMIT_BEFORE_ACK)));
        assertEquals("CONSUMED",ledger.state(id));assertEquals(18,await(worker(id,DisposableLedger.Fault.NONE)));
    }
    @Test @Order(7) void wrongFixtureAndConnectionFailureDeny() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());
        assertFalse(new DisposableLedger(db.getMappedPort(5432),UUID.randomUUID().toString(),DisposableLedger.Fault.NONE).consume(r));
        try(var socket=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))){
            int port=socket.getLocalPort();socket.close();
            assertFalse(new DisposableLedger(port,ledger.marker(),DisposableLedger.Fault.NONE).consume(r));
        }
    }
    @Test @Order(8) void databaseCrashRecoveryRetainsAcknowledgedConsumption() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());assertTrue(ledger.consume(r));
        var docker=db.getDockerClient();docker.killContainerCmd(db.getContainerId()).withSignal("KILL").exec();
        assertFalse(ledger.consume(LedgerWorker.request(UUID.randomUUID())));
        docker.startContainerCmd(db.getContainerId()).exec();
        // Docker may allocate a different ephemeral host port after restart; never reuse cached mapping.
        var inspected=docker.inspectContainerCmd(db.getContainerId()).exec();
        int restartedPort=Integer.parseInt(inspected.getNetworkSettings().getPorts()
                .getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(5432))[0].getHostPortSpec());
        System.out.println("SYNTHETIC_RECOVERY_PORT_CHANGED="+(restartedPort!=db.getMappedPort(5432)));
        var recovered=new DisposableLedger(restartedPort,ledger.marker(),DisposableLedger.Fault.NONE);
        boolean ready=false;long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        while(System.nanoTime()<end){try{recovered.verifyDurabilitySettings();ready=true;break;}catch(Exception ignored){Thread.sleep(100);}}
        assertTrue(ready,"SYNTHETIC_DB_RECOVERY_TIMEOUT");assertEquals("CONSUMED",recovered.state(r.id()));assertFalse(recovered.consume(r));
    }
}
