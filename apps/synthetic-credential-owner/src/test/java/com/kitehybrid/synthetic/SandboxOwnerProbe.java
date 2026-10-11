package com.kitehybrid.synthetic;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.*;
import java.util.*;

/** Test entrypoint only. Invokes unchanged runtime classes; no JDBC, broker adapter or launcher in runtime JAR. */
public final class SandboxOwnerProbe {
    static String stage = "START";
    static void require(boolean condition) { if (!condition) throw new IllegalStateException("SYNTHETIC_PROBE_DENIED"); }
    static void isolation() throws Exception {
        stage = "IDENTITY";
        String status = Files.readString(Path.of("/proc/self/status"));
        require(status.matches("(?s).*Uid:\\s+65534\\s+65534\\s+65534\\s+65534.*"));
        require(status.matches("(?s).*Gid:\\s+65534\\s+65534\\s+65534\\s+65534.*"));
        require(status.contains("CapEff:\t0000000000000000") && status.contains("NoNewPrivs:\t1") && status.contains("Seccomp:\t2"));
        stage = "FILESYSTEM"; boolean denied = false;
        try { Files.writeString(Path.of("/forbidden-owner-write"), "SYNTHETIC"); } catch (FileSystemException expected) { denied = true; }
        require(denied); Files.writeString(Path.of("/scratch/allowed"), "SYNTHETIC");
        for (String path : new String[]{"/host", "/mnt/c", "/var/run/docker.sock", "/witness/private.der", "/root/.env"}) require(!Files.exists(Path.of(path)));
        stage = "PROCESS"; denied = false;
        try { new ProcessBuilder("/bin/true").start().waitFor(); } catch (IOException expected) {
            var errno = java.util.regex.Pattern.compile("error=([0-9]+)").matcher(expected.getMessage());
            boolean numeric = errno.find();
            require(numeric ? errno.group(1).equals("1") : expected.getMessage().contains("Operation not permitted"));
            System.out.println("PROCESS_DENIAL=EPERM"); denied = true;
        }
        require(denied);
        stage = "NETWORK"; require(Files.readAllLines(Path.of("/proc/net/route")).size() == 1);
        denied = false;
        try (var socket = new Socket(Proxy.NO_PROXY)) {
            socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte)192,0,2,1}),9),1000);
        } catch (SocketTimeoutException invalidProof) { throw new IllegalStateException("TIMEOUT_IS_NOT_DENIAL"); }
        catch (SocketException expected) { require(expected.getMessage().contains("Network is unreachable")); denied = true; }
        require(denied); stage = "DNS"; denied = false;
        try { InetAddress.getAllByName("synthetic-owner.invalid"); } catch (UnknownHostException expected) { denied = true; }
        require(denied);
        stage = "CGROUP";
        require(Files.readString(Path.of("/sys/fs/cgroup/pids.max")).strip().equals("64"));
        require(Files.readString(Path.of("/sys/fs/cgroup/memory.max")).strip().equals("268435456"));
        require(Files.readString(Path.of("/sys/fs/cgroup/cpu.max")).strip().equals("100000 100000"));
        System.out.println("CGROUP=PIDS64_MEMORY268435456_CPU1;JAVA=" + Runtime.version());
        System.out.println("OS=UID_GID_65534_CAPS_ZERO_NNP_SECCOMP2_ROOT_WRITE_DENIED_HOST_PATHS_ABSENT_FORK_EPERM_TCP_ENETUNREACH_DNS_UNKNOWN");
        stage = "LSM";
        String label;
        try { label = Files.readString(Path.of("/proc/self/attr/current")).strip(); }
        catch (IOException unsupported) { label = "UNAVAILABLE"; }
        System.out.println("LSM=" + (label.equals("docker-default (enforce)") ? "DOCKER_DEFAULT_ENFORCING" : "OTHER_OR_UNCONFINED"));
        System.out.println("USERNS=" + (Files.readString(Path.of("/proc/self/uid_map")).strip().matches("0\\s+0\\s+4294967295") ? "NOT_REMAPPED" : "MAPPED"));
    }
    static SyntheticHalt.Assertion assertion(SyntheticOwner.Request request, KeyPair pair, boolean expired) throws Exception {
        Instant issued = expired ? request.issued().minusSeconds(20) : request.issued();
        Instant expires = expired ? request.issued().minusSeconds(1) : request.expires();
        var unsigned = new SyntheticHalt.Assertion("SYNTHETIC_ISSUER", "SYNTHETIC_KEY", request.recipient(), request.owner(), request.purpose(),
                1, issued, expires, UUID.randomUUID(), request.fingerprint(), null);
        return new SyntheticHalt.Assertion(unsigned.issuer(), unsigned.keyId(), unsigned.audience(), unsigned.owner(), unsigned.operation(),
                unsigned.epoch(), issued, expires, unsigned.nonce(), unsigned.requestDigest(), SandboxWitnessProbe.sign(unsigned.payload(), pair.getPrivate()));
    }
    public static void main(String[] args) {
        if (args[0].equals("UNCAUGHT")) {
            Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> System.err.println("SYNTHETIC_UNCAUGHT_REDACTED"));
            String canary;
            try { canary = SandboxWitnessProbe.authentication(); } catch (Exception ignored) { throw new IllegalStateException("SYNTHETIC_INIT_FAILED"); }
            throw new IllegalStateException(canary);
        }
        try {
            if (args[0].equals("OS")) { isolation(); return; }
            if (args[0].equals("STATS")) {
                try (var socket = SandboxWitnessProbe.connect(SandboxWitnessProbe.WITNESS)) {
                    var out = new DataOutputStream(socket.getOutputStream()); out.writeUTF(SandboxWitnessProbe.authentication()); out.writeUTF("STATS"); out.flush();
                    System.out.println("RECEIPTS=" + new DataInputStream(socket.getInputStream()).readInt()); return;
                }
            }
            String mode = args[0]; UUID id = UUID.fromString(args[1]);
            PublicKey pin = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(args[2])));
            var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
            String incarnation = "SYNTHETIC_" + UUID.randomUUID().toString().replace('-','_');
            var peer = new LoopbackPeer(SandboxWitnessProbe.PEER,3000); var keys = SandboxWitnessProbe.key();
            var halt = new SyntheticHalt(clock,1,"SYNTHETIC_KEY",keys.getPublic());
            var request = new SyntheticOwner.Request(id,SyntheticOwner.VERSION,incarnation,"SYNTHETIC_CALLER","SYNTHETIC_OWNER","SYNTHETIC_ACCOUNT",
                    SyntheticOwner.PURPOSE,"GET",peer.endpoint(),clock.instant(),clock.instant().plusSeconds(20),1,"SYNTHETIC_ONLY");
            var ledger = new SyntheticOwner.Ledger() {
                public boolean consume(SyntheticOwner.Request ignored) { return false; }
                public boolean consume(SyntheticOwner.Request r, String haltDigest) {
                    try (var socket = SandboxWitnessProbe.connect(SandboxWitnessProbe.WITNESS)) {
                        String binding = r.fingerprint() + ":" + haltDigest + ":" + UUID.randomUUID();
                        var out = new DataOutputStream(socket.getOutputStream()); out.writeUTF(SandboxWitnessProbe.authentication());
                        out.writeUTF(mode.equals("LOSE_ACK") ? "LOSE_ACK" : "CLAIM"); out.writeUTF(r.id().toString()); out.writeUTF(binding); out.flush();
                        var in = new DataInputStream(socket.getInputStream()); if (!in.readBoolean()) return false;
                        int length = in.readInt(); if (length != 384) return false; byte[] signed = in.readNBytes(length); if (signed.length != length) return false;
                        var verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(pin);
                        verifier.update((r.id() + ":" + binding).getBytes(StandardCharsets.US_ASCII)); return verifier.verify(signed);
                    } catch (Exception ignored) { return false; }
                }
                public void terminal(UUID ignored, SyntheticOwner.Outcome outcome) { }
            };
            var assertion = assertion(request, keys, mode.equals("EXPIRED"));
            if (mode.equals("REVOKED")) halt.revoke("SYNTHETIC_KEY");
            if (mode.equals("CONFLICT")) { try { halt.rotate("SYNTHETIC_KEY",SandboxWitnessProbe.key().getPublic()); } catch (IllegalArgumentException expected) { } }
            try (var credential = SyntheticOwner.Credential.generatedSyntheticFixture();
                 var owner = new SyntheticOwner(incarnation,request.caller(),request.recipient(),request.account(),credential,clock,ledger,halt,peer,
                         () -> new SyntheticOwner.SinkPolicy(Set.of("BOUNDED_RESULT"),true))) {
                require(credential.toString().equals("SYNTHETIC_CREDENTIAL_REDACTED"));
                require(assertion.toString().equals("SYNTHETIC_HALT_REDACTED"));
                var result = owner.observe(request,request.caller(),assertion,SyntheticOwner.Fault.NONE);
                // Only explicitly allowlisted result fields, never full request/assertion/response.
                System.out.println("OUTCOME=" + result.outcome() + ";SENDS=" + peer.attemptedSends() + ";DELIVERY=" + result.delivery());
                String canary = SandboxWitnessProbe.authentication(); require(!result.toString().contains(canary));
            }
        } catch (Exception ignored) { System.err.println("SYNTHETIC_OWNER_PROBE_FAILED_CLOSED_STAGE=" + stage); System.exit(43); }
    }
}
