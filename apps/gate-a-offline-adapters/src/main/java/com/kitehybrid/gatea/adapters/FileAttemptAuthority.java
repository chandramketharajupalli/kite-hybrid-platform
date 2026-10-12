package com.kitehybrid.gatea.adapters;

import com.kitehybrid.gatea.GateAOfflinePolicy;
import com.kitehybrid.gatea.GateAOfflinePolicy.Authorization;
import com.kitehybrid.gatea.GateAOfflinePolicy.Consumption;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Consumer;

/** One explicitly initialized directory per attempt. No recovery/reinitialization or deletion API. */
public final class FileAttemptAuthority implements GateAOfflinePolicy.AttemptAuthority {
    private final Path root;
    private final String identity, binding;
    private final Consumer<String> probe;
    public FileAttemptAuthority(Path root, UUID storeId, Authorization a) {
        this(root,storeId,a,ignored -> { });
    }
    FileAttemptAuthority(Path root, UUID storeId, Authorization a, Consumer<String> probe) {
        if (root==null || storeId==null || probe==null) throw denied();
        this.root=root.toAbsolutePath().normalize(); this.binding=digest(SignedHaltAuthority.authorizationBytes(a));
        this.identity="GATE_A_STORE_V1\n"+storeId+"\n"+binding+"\n"; this.probe=probe;
    }
    /** Explicit fixture provisioning only; never called by consume/constructor/restart. */
    public void initializeFixture() {
        try {
            Files.createDirectory(root);
            writeNew(root.resolve("identity"),identity);
        } catch (Exception failure) { throw denied(); }
    }
    private boolean storeValid() throws Exception {
        Path current=root;
        while (current!=null) { if (Files.isSymbolicLink(current)) return false; current=current.getParent(); }
        Path id=root.resolve("identity");
        return Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(id,LinkOption.NOFOLLOW_LINKS)
                && Files.size(id)==identity.length() && Files.readString(id,StandardCharsets.US_ASCII).equals(identity);
    }
    private boolean bound(Authorization a) { return binding.equals(digest(SignedHaltAuthority.authorizationBytes(a))); }
    @Override public boolean available(Authorization a) {
        try { return bound(a) && storeValid() && Files.notExists(root.resolve("spent"),LinkOption.NOFOLLOW_LINKS); }
        catch (Exception failure) { return false; }
    }
    @Override public Consumption consume(Authorization a) {
        try {
            if (!bound(a) || !storeValid()) return Consumption.DENIED;
            probe.accept("BEFORE_CREATE");
            try (var channel=FileChannel.open(root.resolve("spent"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                // Creation is the linearization point. Even empty/partial markers irrevocably retire this store.
                probe.accept("AFTER_CREATE");
                write(channel,"GATE_A_SPENT_V1\n"+binding+"\n"); channel.force(true);
                probe.accept("AFTER_FORCE");
                if (!storeValid()) return Consumption.UNCERTAIN;
                return Consumption.ACKNOWLEDGED;
            }
        } catch (FileAlreadyExistsException spent) { return Consumption.DENIED; }
        catch (Exception uncertain) { return Consumption.UNCERTAIN; }
    }
    @Override public void recordOutcome(Authorization a, String outcome) {
        try {
            if (!bound(a) || !storeValid() || !Files.isRegularFile(root.resolve("spent"),LinkOption.NOFOLLOW_LINKS)
                    || !("SYNTHETIC_COMPLETED".equals(outcome) || "UNKNOWN_OR_DENIED".equals(outcome))) throw denied();
            // No rewrite of a result and no retry/refund. Duplicate outcome recording is denied.
            writeNew(root.resolve("outcome"),outcome+"\n");
        } catch (Exception failure) { throw denied(); }
    }
    private static void writeNew(Path path,String text) throws Exception {
        try (var ch=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) { write(ch,text); ch.force(true); }
    }
    private static void write(FileChannel channel,String text) throws Exception {
        var b=ByteBuffer.wrap(text.getBytes(StandardCharsets.US_ASCII)); while(b.hasRemaining()) channel.write(b);
    }
    private static String digest(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
        catch (Exception failure) { throw denied(); }
    }
    @Override public String toString() { return "ATTEMPT_STORE_REDACTED"; }
    private static IllegalStateException denied() { return new IllegalStateException("GATE_A_DENIED"); }
}
