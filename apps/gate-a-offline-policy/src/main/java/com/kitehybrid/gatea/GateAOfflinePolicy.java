package com.kitehybrid.gatea;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/** Production-intended sequencing policy. No transport, launcher or production authority adapter. */
public final class GateAOfflinePolicy {
    public static final String ENDPOINT = "https://api.kite.trade/instruments/NSE";
    public static final String OPERATION = "GATE_A_INSTRUMENT_MASTER";
    public static final int LIMIT = 32 * 1024 * 1024;
    public record Authorization(UUID attempt, String operation, String method, String endpoint,
            String process, String keyId, long epoch, Instant issued, Instant expires,
            String ownerApprovalReference) {
        @Override public String toString() { return "GATE_A_AUTHORIZATION_REDACTED"; }
    }
    /** Adapter must authenticate the complete authorization and its owner reference, including signature,
     * current epoch, revocation and quarantine. No accepting/default implementation is shipped. */
    public interface HaltAuthority { boolean permits(Authorization authorization, Instant observedAt); }
    public enum Consumption { ACKNOWLEDGED, DENIED, UNCERTAIN }
    /** Shared authority must atomically consume once across processes and retain it after any outcome. */
    public interface AttemptAuthority {
        boolean available(Authorization authorization);
        Consumption consume(Authorization authorization);
        void recordOutcome(Authorization authorization, String outcome);
    }
    /** Reuse the existing offline master parser/resolver in a separately reviewed adapter. */
    public interface MasterValidator { boolean valid(byte[] utf8Csv); }
    public enum FixtureOutcome { OK, REDIRECT, TIMEOUT, UNCERTAIN, SERVER_ERROR }
    public static final class FixtureResponse {
        private final byte[] bytes;
        private final FixtureOutcome outcome;
        public FixtureResponse(byte[] bytes, FixtureOutcome outcome) {
            if (bytes == null || bytes.length > LIMIT || outcome == null) throw denied();
            this.bytes = bytes.clone(); this.outcome = outcome;
        }
        @Override public String toString() { return "GATE_A_FIXTURE_REDACTED"; }
    }
    public record Receipt(String sha256, Instant observedAt, boolean synthetic) { }
    private final String process, keyId;
    private final Clock clock;
    private final HaltAuthority halt;
    private final AttemptAuthority attempts;
    private final MasterValidator validator;
    private final Set<UUID> retired = new HashSet<>();
    private Instant lastObserved;
    private boolean quarantined;
    private int syntheticSends;

    public GateAOfflinePolicy(String process, String keyId, Clock clock, HaltAuthority halt,
            AttemptAuthority attempts, MasterValidator validator) {
        if (process == null || process.isBlank() || keyId == null || keyId.isBlank()
                || clock == null || halt == null || attempts == null || validator == null) throw denied();
        this.process=process; this.keyId=keyId; this.clock=clock; this.halt=halt;
        this.attempts=attempts; this.validator=validator;
    }
    private Instant check(Authorization a) {
        Instant now=clock.instant();
        if (lastObserved != null && now.isBefore(lastObserved)) quarantined=true;
        lastObserved=now;
        if (quarantined || a == null || a.attempt()==null || !OPERATION.equals(a.operation())
                || !"GET".equals(a.method()) || !ENDPOINT.equals(a.endpoint())
                || !process.equals(a.process()) || !keyId.equals(a.keyId()) || a.epoch()<=0
                || a.issued()==null || a.expires()==null || a.ownerApprovalReference()==null
                || a.ownerApprovalReference().isBlank() || now.isBefore(a.issued())
                || !now.isBefore(a.expires()) || !a.expires().isAfter(a.issued())
                || Duration.between(a.issued(),a.expires()).compareTo(Duration.ofSeconds(30))>0
                || !halt.permits(a,now)) throw denied();
        return now;
    }
    /** Only closed memory fixtures can be observed. There is no callback capable of dispatching HTTP. */
    public synchronized Receipt observeFixture(String method, String endpoint, String proxy,
            Authorization authorization, FixtureResponse response) {
        boolean consumed=false;
        try {
            if (!"GET".equals(method) || !ENDPOINT.equals(endpoint) || proxy!=null || response==null) throw denied();
            check(authorization);
            if (!retired.add(authorization.attempt()) || !attempts.available(authorization)) throw denied();
            if (attempts.consume(authorization)!=Consumption.ACKNOWLEDGED) throw denied();
            consumed=true;
            Instant observed=check(authorization); // Final HALT check after durable authority ACK.
            syntheticSends++;
            if (response.outcome!=FixtureOutcome.OK) throw denied();
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.bytes)).toString();
            if (text.isEmpty() || text.indexOf('\0')>=0 || text.indexOf('\ufeff')>=0
                    || !validator.valid(response.bytes.clone())) throw denied();
            check(authorization); // Revocation during validation discards the result, not the send.
            var result=new Receipt(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(response.bytes)),observed,true);
            attempts.recordOutcome(authorization,"SYNTHETIC_COMPLETED");
            return result;
        } catch (Exception failure) {
            if (consumed) try { attempts.recordOutcome(authorization,"UNKNOWN_OR_DENIED"); }
                catch (Exception ignored) { /* No refund or retry after uncertain outcome. */ }
            throw denied();
        }
    }
    public synchronized int syntheticSends() { return syntheticSends; }
    @Override public String toString() { return "GATE_A_OFFLINE_POLICY_REDACTED"; }
    private static IllegalStateException denied() { return new IllegalStateException("GATE_A_DENIED"); }
}
