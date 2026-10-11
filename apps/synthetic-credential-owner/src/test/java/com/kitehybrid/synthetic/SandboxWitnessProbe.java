package com.kitehybrid.synthetic;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only, separate container and volume. Forced files are NOT a hardware monotonic root. */
public final class SandboxWitnessProbe {
    static final int WITNESS = 18081, PEER = 18082;
    static final Path ROOT = Path.of("/witness");
    static final AtomicInteger RECEIPTS = new AtomicInteger();
    static final java.util.concurrent.CountDownLatch PEER_READY = new java.util.concurrent.CountDownLatch(1);
    static byte[] canary() throws Exception {
        return MessageDigest.getInstance("SHA-256").digest("SYNTHETIC_PHASE1311_ONLY".getBytes(StandardCharsets.US_ASCII));
    }
    static String authentication() throws Exception { return HexFormat.of().formatHex(canary()); }
    static KeyPair key() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(3072); return generator.generateKeyPair();
    }
    static void forced(Path path, byte[] bytes) throws Exception {
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
        try (var directory = FileChannel.open(path.getParent(), StandardOpenOption.READ)) { directory.force(true); }
    }
    static KeyPair load(boolean initialize) throws Exception {
        Path privateFile = ROOT.resolve("private.der"), publicFile = ROOT.resolve("public.der");
        if (initialize) {
            // Explicit fresh-fixture initialization only; never reset on recovery.
            if (Files.exists(privateFile) || Files.exists(publicFile)) throw new IOException();
            var pair = key(); forced(privateFile, pair.getPrivate().getEncoded()); forced(publicFile, pair.getPublic().getEncoded());
        }
        var factory = KeyFactory.getInstance("RSA");
        return new KeyPair(factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicFile))),
                factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(privateFile))));
    }
    static byte[] sign(byte[] payload, PrivateKey key) throws Exception {
        var signature = Signature.getInstance("SHA256withRSA"); signature.initSign(key); signature.update(payload); return signature.sign();
    }
    static ServerSocket server(int port) throws Exception {
        var server = new ServerSocket(); server.setReuseAddress(true);
        server.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}), port)); return server;
    }
    static Socket connect(int port) throws Exception {
        var socket = new Socket(Proxy.NO_PROXY); socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),port),3000);
        socket.setSoTimeout(3000); return socket;
    }
    static void peer() {
        try (var server = server(PEER)) {
            PEER_READY.countDown();
            for (;;) try (var socket = server.accept()) {
                socket.setSoTimeout(3000); var in = new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
                if (!("GET " + SyntheticOwner.PATH + " HTTP/1.1").equals(in.readLine())) throw new IOException();
                int bytes = 0; String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) { bytes += line.length(); if (bytes > 4096) throw new IOException(); }
                RECEIPTS.incrementAndGet();
                byte[] body = "{\"synthetic\":true,\"equity\":{\"available\":1}}".getBytes(StandardCharsets.US_ASCII);
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().write(body); socket.getOutputStream().flush();
            }
        } catch (Exception ignored) { Runtime.getRuntime().halt(41); }
    }
    public static void main(String[] args) {
        try {
            Thread.ofPlatform().daemon().start(() -> {
                try { Thread.sleep(120_000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                Runtime.getRuntime().halt(44);
            });
            KeyPair pair = load(args.length == 1 && args[0].equals("INITIALIZE"));
            Thread.ofPlatform().daemon().start(SandboxWitnessProbe::peer);
            if (!PEER_READY.await(3, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException();
            try (var server = server(WITNESS)) {
                System.out.println("READY=" + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
                for (;;) try (var socket = server.accept()) {
                    socket.setSoTimeout(3000); var in = new DataInputStream(socket.getInputStream()); var out = new DataOutputStream(socket.getOutputStream());
                    if (!MessageDigest.isEqual(in.readUTF().getBytes(StandardCharsets.US_ASCII), authentication().getBytes(StandardCharsets.US_ASCII))) continue;
                    String operation = in.readUTF();
                    if (operation.equals("STATS")) { out.writeInt(RECEIPTS.get()); out.flush(); continue; }
                    if (!Set.of("CLAIM", "LOSE_ACK").contains(operation)) continue;
                    UUID id = UUID.fromString(in.readUTF()); String binding = in.readUTF();
                    if (!binding.matches("[a-f0-9]{64}:[a-f0-9]{64}:[a-f0-9-]{36}")) continue;
                    Path marker = ROOT.resolve(id + ".retired");
                    if (Files.exists(marker)) { out.writeBoolean(false); out.flush(); continue; }
                    // Single coordinator loop + CREATE_NEW; retire even if response/ack is lost.
                    forced(marker, binding.getBytes(StandardCharsets.US_ASCII));
                    if (operation.equals("LOSE_ACK")) continue;
                    byte[] signed = sign((id + ":" + binding).getBytes(StandardCharsets.US_ASCII), pair.getPrivate());
                    out.writeBoolean(true); out.writeInt(signed.length); out.write(signed); out.flush();
                }
            }
        } catch (Exception ignored) { System.err.println("SYNTHETIC_WITNESS_FAILED_CLOSED"); System.exit(42); }
    }
}
