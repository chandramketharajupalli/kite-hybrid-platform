package com.kitehybrid.synthetic;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Conditional recovery evidence on the host; explicitly NOT Java-owner OS sandbox evidence. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RootRecoveryIT {
    static PostgreSQLContainer<?> localDb, witnessDb;
    static DisposableLedger local;
    static FenceWitness witness;

    @BeforeAll static void start() throws Exception {
        localDb = DisposableLedger.container(); localDb.start(); local = DisposableLedger.initialize(localDb);
        witnessDb = DisposableLedger.container(); witnessDb.start();
        witness = new FenceWitness(witnessDb, FenceWitness.freshRoot(), true);
    }
    @AfterAll static void stop() throws Exception {
        try { if (witness != null) witness.close(); }
        finally {
            if (witnessDb != null) witnessDb.stop(); if (localDb != null) localDb.stop();
            if (local != null) local.journal.destroyFixture();
        }
    }

    @ParameterizedTest @Order(1)
    @ValueSource(strings = {"AFTER_WITNESS_ACK", "BEFORE_CONSUMPTION_COMMIT", "AFTER_CONSUMPTION_COMMIT", "AFTER_RECEIPT", "BEFORE_OUTCOME"})
    void parentKilledFencedOwnerCannotReissueAcrossLocalStateWindows(String stage) throws Exception {
        var received = new CountDownLatch(1); var release = new CountDownLatch(1);
        Runnable observation = () -> {
            received.countDown();
            if (stage.equals("AFTER_RECEIPT")) try {
                if (!release.await(25, TimeUnit.SECONDS)) throw new AssertionError("SYNTHETIC_BARRIER_TIMEOUT");
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        };
        try (var peer = new OwnerIT.FakePeer(OwnerIT.response(200, OwnerIT.BODY), observation, 0, false)) {
            UUID id = UUID.randomUUID();
            var child = worker(id, peer.listener.getLocalPort(), stage);
            try {
                if (stage.equals("AFTER_RECEIPT")) assertTrue(received.await(25, TimeUnit.SECONDS));
                else IsolationLedgerIT.awaitStage(child, stage);
                assertTrue(child.isAlive()); assertTrue(witness.retainedRoot().retired().contains(id));
                String expected = switch (stage) {
                    case "AFTER_WITNESS_ACK" -> "ABSENT";
                    case "BEFORE_CONSUMPTION_COMMIT" -> "RESERVED";
                    default -> "CONSUMED";
                };
                assertEquals(expected, local.state(id));
            } finally { IsolationLedgerIT.kill(child); release.countDown(); }
            String stderr = new String(child.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTestDiagnostics(stderr);
            int expectedReceipts = Set.of("AFTER_RECEIPT", "BEFORE_OUTCOME").contains(stage) ? 1 : 0;
            assertEquals(expectedReceipts, peer.attempts.get());
            if (expectedReceipts == 1) assertTrue(peer.received.get().endsWith("\r\n\r\n"));
            var replacement = FenceLedgerIT.worker(localDb, local, witness.client(), id, "OWNER",
                    Integer.toString(peer.listener.getLocalPort()), "SYNTHETIC_REPLACEMENT");
            try {
                assertEquals(18, FenceLedgerIT.finish(replacement));
                assertEquals(0, FenceOwnerIT.readCount(replacement));
                assertTestDiagnostics(new String(replacement.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            } finally { FenceLedgerIT.kill(replacement); }
            assertEquals(expectedReceipts, peer.attempts.get());
        } finally { release.countDown(); }
    }

    @Test @Order(2) void lostAckRetirementSurvivesCoordinatorReconstruction() throws Exception {
        var request = LedgerWorker.request(UUID.randomUUID()); witness.fault = FenceWitness.Fault.LOST_ACK;
        assertFalse(new FencedLedger(witness.client(), local, false).consume(request, "0".repeat(64)));
        var retained = witness.retainedRoot(); assertTrue(retained.retired().contains(request.id()));
        assertEquals("ABSENT", local.state(request.id())); witness.close();
        witness = new FenceWitness(witnessDb, retained, false);
        assertFalse(new FencedLedger(witness.client(), local, false).consume(request, "0".repeat(64)));
        assertEquals("ABSENT", local.state(request.id())); // Ledger-only: no HTTP involved.
    }

    @Test @Order(3) void conflictingPinnedSigningKeyCannotProduceUsableGrant() throws Exception {
        var root = witness.retainedRoot(); var wrongKey = SyntheticSecurityTest.key();
        var client = new FenceWitness.Client(FenceLedgerIT.portOf(witness.client()), root.identity(),
                wrongKey.getPublic(), root.authentication(), root.checkpoint());
        var request = LedgerWorker.request(UUID.randomUUID());
        assertFalse(new FencedLedger(client, local, false).consume(request, "0".repeat(64)));
        assertTrue(witness.retainedRoot().retired().contains(request.id()));
        assertEquals("ABSENT", local.state(request.id()));
        assertFalse(new FencedLedger(witness.client(), local, false).consume(request, "0".repeat(64)));
    }

    @Test @Order(4) void lostControlPipeAfterWitnessClaimCannotAdvanceToLocalOrSend() throws Exception {
        try (var peer = OwnerIT.peer(OwnerIT.response(200, OwnerIT.BODY))) {
            UUID id = UUID.randomUUID(); var child = worker(id, peer.listener.getLocalPort(), "AFTER_WITNESS_ACK");
            try {
                IsolationLedgerIT.awaitStage(child, "AFTER_WITNESS_ACK");
                child.getOutputStream().close(); assertTrue(child.waitFor(5, TimeUnit.SECONDS));
                assertEquals(29, child.exitValue()); assertEquals("ABSENT", local.state(id));
                assertEquals(0, peer.attempts.get());
                assertFalse(new FencedLedger(witness.client(), local, false).consume(LedgerWorker.request(id), "0".repeat(64)));
                assertTestDiagnostics(new String(child.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            } finally { IsolationLedgerIT.kill(child); }
        }
    }

    @Test @Order(99) void lostCheckpointCannotBeReplacedByFreshRootOrClearQuarantine() throws Exception {
        var request = LedgerWorker.request(UUID.randomUUID());
        assertFalse(new FencedLedger(null, local, false).consume(request, "0".repeat(64)));
        witness.close();
        // Adversarial fresh-root substitution, NOT a recovery or genesis procedure.
        witness = new FenceWitness(witnessDb, FenceWitness.freshRoot(), false);
        assertFalse(new FencedLedger(witness.client(), local, false).consume(request, "0".repeat(64)));
        assertTrue(witness.retainedRoot().quarantined());
        witness.available = false; witness.available = true;
        assertFalse(witness.client().claim(LedgerWorker.request(UUID.randomUUID()), "0".repeat(64)));
        var retained = witness.retainedRoot(); witness.close();
        witness = new FenceWitness(witnessDb, retained, false);
        assertFalse(witness.client().claim(LedgerWorker.request(UUID.randomUUID()), "0".repeat(64)));
        assertEquals("ABSENT", local.state(request.id()));
    }

    static void assertTestDiagnostics(String text) {
        String normalized = text.replace("\r\n", "\n");
        String expected = "SLF4J(W): No SLF4J providers were found.\n"
                + "SLF4J(W): Defaulting to no-operation (NOP) logger implementation\n"
                + "SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.\n";
        assertFalse(text.contains(witness.retainedRoot().authentication()), "SYNTHETIC_CANARY_LEAK");
        assertTrue(normalized.isEmpty() || normalized.equals(expected), "SYNTHETIC_UNEXPECTED_CHILD_DIAGNOSTIC");
    }

    static Process worker(UUID id, int peerPort, String stage) throws Exception {
        var child = new ProcessBuilder(IsolationSecurityTest.javaExecutable(), "-Duser.timezone=UTC", "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                RootRecoveryWorker.class.getName(), Integer.toString(localDb.getMappedPort(5432)), local.marker(),
                id.toString(), Integer.toString(peerPort), stage).start();
        var bootstrap = new DataOutputStream(child.getOutputStream()); witness.client().bootstrap(bootstrap);
        // Keep stdin open for the parent-controlled barrier; no synthetic secret in argv/environment.
        return child;
    }
}
