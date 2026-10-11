package com.kitehybrid.synthetic;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FenceLedgerIT {
    static PostgreSQLContainer<?> localDb,witnessDb;
    static DisposableLedger local;
    static FenceWitness witness;
    @BeforeAll static void start() throws Exception {
        localDb=DisposableLedger.container();localDb.start();local=DisposableLedger.initialize(localDb);
        witnessDb=DisposableLedger.container();witnessDb.start();witness=new FenceWitness(witnessDb,FenceWitness.freshRoot(),true);
    }
    @AfterAll static void stop() throws Exception {
        try {if(witness!=null)witness.close();} finally {
            if(witnessDb!=null)witnessDb.stop();if(localDb!=null)localDb.stop();if(local!=null)local.journal.destroyFixture();
        }
    }
    static FencedLedger fenced(boolean lost){return new FencedLedger(witness.client(),local,lost);}

    @ParameterizedTest @ValueSource(strings={"JOURNAL","DATABASE","BOTH"}) @Order(1)
    void survivingIndependentWitnessDeniesLocalRollback(String stores) throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());assertTrue(fenced(false).consume(r,"0".repeat(64)));
        rollbackLocal(localDb,local,r.id(),stores);
        assertFalse(fenced(false).consume(r,"0".repeat(64))); // no HTTP in ledger gate
    }
    @ParameterizedTest @EnumSource(value=FenceWitness.Fault.class,names={"BEFORE_RESERVATION","DURING_RESERVATION","LOST_ACK","BAD_SIGNATURE"}) @Order(2)
    void witnessUncertaintyOrInvalidReceiptNeverGrantsOrRetries(FenceWitness.Fault fault) throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());witness.fault=fault;
        try {assertFalse(fenced(false).consume(r,"0".repeat(64)));}
        finally {witness.fault=FenceWitness.Fault.NONE;}
        assertEquals("ABSENT",local.state(r.id()));assertFalse(fenced(false).consume(r,"0".repeat(64)));
    }
    @Test @Order(3) void lostAfterWitnessAckAndLocalWriteFaultRemainSpent() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());assertFalse(fenced(true).consume(r,"0".repeat(64)));
        assertFalse(fenced(false).consume(r,"0".repeat(64)));assertEquals("ABSENT",local.state(r.id()));
        var next=LedgerWorker.request(UUID.randomUUID());
        var broken=new DisposableLedger(localDb.getMappedPort(5432),local.marker(),DisposableLedger.Fault.PARTIAL_JOURNAL);
        assertFalse(new FencedLedger(witness.client(),broken,false).consume(next,"0".repeat(64)));
        assertFalse(fenced(false).consume(next,"0".repeat(64)));
    }
    @Test @Order(4) void staleCheckpointEpochExpiryAndAuthorityLossDeny() throws Exception {
        var stale=witness.client();var r=LedgerWorker.request(UUID.randomUUID());assertTrue(fenced(false).consume(r,"0".repeat(64)));
        assertFalse(stale.claim(LedgerWorker.request(UUID.randomUUID()),"0".repeat(64)));
        for(int kind=0;kind<2;kind++) {
            var b=LedgerWorker.request(UUID.randomUUID());var altered=new SyntheticOwner.Request(b.id(),b.version(),b.owner(),b.caller(),b.recipient(),b.account(),b.purpose(),b.method(),b.endpoint(),b.issued(),kind==0?b.issued():b.expires(),kind==1?0:1,b.authority());
            assertFalse(fenced(false).consume(altered,"0".repeat(64)));
        }
        assertFalse(new FencedLedger(null,local,false).consume(LedgerWorker.request(UUID.randomUUID()),"0".repeat(64)));
        witness.available=false;
        try {assertFalse(fenced(false).consume(LedgerWorker.request(UUID.randomUUID()),"0".repeat(64)));}
        finally {witness.available=true;}
    }
    @Test @Order(5) void twoIndependentJvmClaimsHaveOneWinnerAndRestartCannotRevive() throws Exception {
        UUID id=UUID.randomUUID();var a=worker(localDb,local,witness.client(),id,"LEDGER");var b=worker(localDb,local,witness.client(),id,"LEDGER");
        try {assertEquals(Set.of(17,18),Set.of(finish(a),finish(b)));}
        finally {kill(a);kill(b);}
        var replay=worker(localDb,local,witness.client(),id,"LEDGER");try {assertEquals(18,finish(replay));}finally{kill(replay);}
    }
    @Test @Order(6) void witnessContainerCrashRestartWithRetainedRootPreservesFencing() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());assertTrue(fenced(false).consume(r,"0".repeat(64)));
        var docker=witnessDb.getDockerClient();docker.killContainerCmd(witnessDb.getContainerId()).withSignal("KILL").exec();
        docker.startContainerCmd(witnessDb.getContainerId()).exec();
        int port=Integer.parseInt(docker.inspectContainerCmd(witnessDb.getContainerId()).exec().getNetworkSettings().getPorts()
            .getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(5432))[0].getHostPortSpec());
        witness.mappedPortAfterRestart(port);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);boolean ready=false;
        while(System.nanoTime()<deadline){try(var c=connection(port)){ready=true;break;}catch(SQLException ignored){Thread.sleep(100);}}
        assertTrue(ready);assertFalse(fenced(false).consume(r,"0".repeat(64)));
        assertTrue(fenced(false).consume(LedgerWorker.request(UUID.randomUUID()),"0".repeat(64)));
    }
    @Test @Order(7) void rootAndSignatureCannotBeSubstitutedBySplitBrainPeer() throws Exception {
        var good=witness.client();var root=witness.retainedRoot();
        var wrong=new FenceWitness.Client(portOf(good),UUID.randomUUID(),root.key().getPublic(),root.authentication(),root.checkpoint());
        assertFalse(wrong.claim(LedgerWorker.request(UUID.randomUUID()),"0".repeat(64)));
        var unauth=new FenceWitness.Client(portOf(good),root.identity(),root.key().getPublic(),"SYNTHETIC_INVALID",root.checkpoint());
        assertFalse(unauth.claim(LedgerWorker.request(UUID.randomUUID()),"0".repeat(64)));
    }
    @Test @Order(8) void witnessRollbackIsDetectedBySurvivingCheckpointAndFailsClosed() throws Exception {
        var r=LedgerWorker.request(UUID.randomUUID());assertTrue(fenced(false).consume(r,"0".repeat(64)));
        var snapshot=witness.retainedRoot();
        // Port may have changed in the preceding actual restart; obtain current owned mapping.
        int port=currentWitnessPort();
        try(var c=connection(port);var s=c.createStatement()){s.executeUpdate("UPDATE witness_head SET seq=seq-1");}
        assertFalse(fenced(false).consume(LedgerWorker.request(UUID.randomUUID()),"0".repeat(64)));
        // Restoration here is only teardown of this adversarial case; quarantine must persist.
        try(var c=connection(port);var p=c.prepareStatement("UPDATE witness_head SET seq=?")){p.setLong(1,snapshot.checkpoint());p.executeUpdate();}
        assertFalse(fenced(false).consume(LedgerWorker.request(UUID.randomUUID()),"0".repeat(64)));
        var quarantinedRoot=witness.retainedRoot();assertTrue(quarantinedRoot.quarantined());witness.close();
        witness=new FenceWitness(witnessDb,quarantinedRoot,false);witness.mappedPortAfterRestart(port);
        assertFalse(fenced(false).consume(LedgerWorker.request(UUID.randomUUID()),"0".repeat(64)));
    }
    @Test @Order(99) void resettingEveryStoreAndTrustedRootDemonstratesUnresolvedUniversalRollback() throws Exception {
        // Fresh fixture root for this explicit negative control, not operational recovery.
        witness.close();int port=currentWitnessPort();
        try(var c=connection(port);var s=c.createStatement()){s.executeUpdate("DELETE FROM witness_claims");s.executeUpdate("DELETE FROM witness_head");}
        var before=FenceWitness.freshRoot();
        try(var c=connection(port);var p=c.prepareStatement("INSERT INTO witness_head VALUES (?,0)")){p.setObject(1,before.identity());p.executeUpdate();}
        witness=new FenceWitness(witnessDb,before,false);witness.mappedPortAfterRestart(port);
        var r=LedgerWorker.request(UUID.randomUUID());assertTrue(fenced(false).consume(r,"0".repeat(64)));
        rollbackLocal(localDb,local,r.id(),"BOTH");witness.close();
        try(var c=connection(port);var s=c.createStatement()){s.executeUpdate("DELETE FROM witness_claims");s.executeUpdate("UPDATE witness_head SET seq=0");}
        witness=new FenceWitness(witnessDb,before,false);witness.mappedPortAfterRestart(port);
        assertTrue(fenced(false).consume(r,"0".repeat(64))); // SECOND GRANT: UNRESOLVED / NO_GO; never HTTP.
    }
    static int portOf(FenceWitness.Client client) throws Exception {
        var f=FenceWitness.Client.class.getDeclaredField("port");f.setAccessible(true);return f.getInt(client);
    }
    static int currentWitnessPort(){return Integer.parseInt(witnessDb.getDockerClient().inspectContainerCmd(witnessDb.getContainerId()).exec().getNetworkSettings().getPorts().getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(5432))[0].getHostPortSpec());}
    static Connection connection(int port) throws SQLException {return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:"+port+"/synthetic_owner_only?connectTimeout=2&socketTimeout=3","synthetic_fixture","synthetic_fixture");}
    static void rollbackLocal(PostgreSQLContainer<?> db,DisposableLedger local,UUID id,String stores) throws Exception {
        if(!stores.equals("JOURNAL"))try(var c=connection(db.getMappedPort(5432));var p=c.prepareStatement("DELETE FROM attempts WHERE id=?")){p.setQueryTimeout(3);p.setObject(1,id);assertEquals(1,p.executeUpdate());}
        if(!stores.equals("DATABASE"))Files.delete(local.journal.path(id));
    }
    static Process worker(PostgreSQLContainer<?> db,DisposableLedger local,FenceWitness.Client client,UUID id,String mode,String... peer) throws Exception {
        var args=new ArrayList<>(List.of(IsolationSecurityTest.javaExecutable(),"-Duser.timezone=UTC","-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),FenceWorker.class.getName(),Integer.toString(db.getMappedPort(5432)),local.marker(),id.toString(),mode));args.addAll(List.of(peer));
        var process=new ProcessBuilder(args).start();
        try(var bootstrap=new DataOutputStream(process.getOutputStream())){client.bootstrap(bootstrap);}return process;
    }
    static int finish(Process process) throws Exception {assertTrue(process.waitFor(25,TimeUnit.SECONDS));return process.exitValue();}
    static void kill(Process process) throws Exception {if(process.isAlive())process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));}
}
