package com.kitehybrid.synthetic;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/** Fixture-only immutable retirement markers; never packaged or used as a trading store. */
final class AttemptJournal {
    private final Path directory;
    private final String marker;

    AttemptJournal(String marker) {
        UUID.fromString(marker);
        this.marker = marker;
        directory = Path.of(System.getProperty("java.io.tmpdir"), "synthetic-owner-ledger-" + marker);
    }

    void initialize() throws Exception {
        Files.createDirectory(directory); // Never recreate an existing/replaced store.
        try (var channel = FileChannel.open(directory.resolve("identity"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            write(channel, marker);
            channel.force(true);
        }
    }

    boolean retire(UUID id, String digest, boolean partialWrite) {
        try {
            if (!validStore()) return false;
            try (var channel = FileChannel.open(path(id), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                if (!partialWrite) write(channel, digest);
                channel.force(true);
                return !partialWrite;
            }
        } catch (Exception ignored) {
            return false; // Existing, partial, uncertain force or missing store never grants reuse.
        }
    }

    boolean validStore() throws Exception {
        return Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(directory.resolve("identity"), LinkOption.NOFOLLOW_LINKS)
                && Files.size(directory.resolve("identity")) == marker.length()
                && Files.readString(directory.resolve("identity"), StandardCharsets.US_ASCII).equals(marker);
    }

    Path path(UUID id) { return directory.resolve(id.toString() + ".spent"); }
    Path identityPath() { return directory.resolve("identity"); }

    private static void write(FileChannel channel, String value) throws Exception {
        var buffer = ByteBuffer.wrap(value.getBytes(StandardCharsets.US_ASCII));
        while (buffer.hasRemaining()) channel.write(buffer);
    }

    // Only after fixture termination. Not a ledger recovery operation.
    void destroyFixture() throws Exception {
        if (!validStore()) throw SyntheticOwner.denied();
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw SyntheticOwner.denied();
                Files.delete(file);
            }
        }
        Files.delete(directory);
    }
    @Override public String toString() { return "SYNTHETIC_JOURNAL_REDACTED"; }
}
