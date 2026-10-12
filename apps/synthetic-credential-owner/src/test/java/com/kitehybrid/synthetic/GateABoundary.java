package com.kitehybrid.synthetic;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline fixture only. No credential, network, storage-of-response or production entry point. */
final class GateABoundary {
    static final String URL = "https://api.kite.trade/instruments/NSE";
    static final int LIMIT = 32 * 1024 * 1024;
    record Approval(UUID id, String owner, Instant issued, Instant expires,
                    boolean synthetic, boolean retentionAllowed, String gate) {
        @Override public String toString() { return "SYNTHETIC_APPROVAL_REDACTED"; }
    }
    enum Outcome { OK, TIMEOUT, UNCERTAIN, REDIRECT, SERVER_ERROR }
    static final class Response {
        private final byte[] bytes;
        private final String contentType;
        private final Outcome outcome;
        Response(byte[] bytes, String contentType, Outcome outcome) {
            if (bytes == null || bytes.length > LIMIT) throw denied();
            this.bytes = bytes.clone(); this.contentType = contentType; this.outcome = outcome;
        }
        @Override public String toString() { return "SYNTHETIC_RESPONSE_REDACTED"; }
    }
    record Observation(byte[] csv, String rawSha256, Instant observedAt, boolean synthetic) {
        Observation { csv = csv.clone(); }
        @Override public byte[] csv() { return csv.clone(); }
        @Override public String toString() { return "SYNTHETIC_OBSERVATION_REDACTED"; }
    }
    private final AttemptJournal journal;
    private final Clock clock;
    private final AtomicInteger fixtureDispatches = new AtomicInteger();
    GateABoundary(AttemptJournal journal, Clock clock) { this.journal = journal; this.clock = clock; }

    Observation acquire(String method, String url, String proxy, Approval approval,
                        String syntheticOwner, Response response) {
        try {
            Instant now = clock.instant();
            if (!"GET".equals(method) || !URL.equals(url) || proxy != null
                    || approval == null || approval.id() == null || approval.owner() == null
                    || !approval.owner().equals(syntheticOwner) || !approval.synthetic()
                    || !approval.retentionAllowed() || !"A".equals(approval.gate())
                    || approval.issued() == null || approval.expires() == null
                    || now.isBefore(approval.issued()) || !now.isBefore(approval.expires())
                    || !approval.issued().isBefore(approval.expires())
                    || Duration.between(approval.issued(), approval.expires()).compareTo(Duration.ofMinutes(5)) > 0
                    || response == null) throw denied();
            // CREATE_NEW retirement + force happen before even the closed fixture is observed.
            // Marker is never removed/refunded by this boundary, including partial/uncertain failures.
            if (!journal.retire(approval.id(), digest(("GET " + URL).getBytes(StandardCharsets.US_ASCII)), false))
                throw denied();
            fixtureDispatches.incrementAndGet();
            if (response.outcome != Outcome.OK || !"text/csv; charset=utf-8".equals(response.contentType)) throw denied();
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.bytes)).toString();
            if (text.isEmpty() || text.indexOf('\0') >= 0 || text.indexOf('\ufeff') >= 0) throw denied();
            // CSV schema/resolution belongs to the existing offline importer, not this boundary.
            return new Observation(response.bytes, digest(response.bytes), now, true);
        } catch (Exception failure) { throw denied(); } // No raw values, causes, bodies or headers retained.
    }
    int fixtureDispatches() { return fixtureDispatches.get(); }
    int realRequests() { return 0; } // There is no networking implementation or callback.
    static IllegalStateException denied() { return new IllegalStateException("GATE_A_OFFLINE_DENIED"); }
    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
