package com.kitehybrid.gatea.adapters;

import com.kitehybrid.gatea.GateAOfflinePolicy;
import com.kitehybrid.gatea.GateAOfflinePolicy.Authorization;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/** Verifies supplied evidence, never issues it. Provisioning/freshness authority is external. */
public final class SignedHaltAuthority implements GateAOfflinePolicy.HaltAuthority {
    public record State(long epoch, boolean revoked, boolean quarantined, Instant observed,
                        Instant expires, String keyId, String process, String approvalReference) {
        @Override public String toString() { return "HALT_STATE_REDACTED"; }
    }
    public static final class Evidence<T> {
        private final T value;
        private final byte[] signature;
        public Evidence(T value, byte[] signature) {
            if (value == null || signature == null || signature.length > 1024) throw denied();
            this.value = value; this.signature = signature.clone();
        }
        @Override public String toString() { return "SIGNED_EVIDENCE_REDACTED"; }
    }
    private final PublicKey key;
    private final String keyId, process, approval;
    private final Evidence<Authorization> permit;
    private final Supplier<Evidence<State>> currentState;
    private long epoch;
    private Instant lastTime, lastStateTime;
    private boolean quarantined;

    public SignedHaltAuthority(PublicKey key, String keyId, String process, String approval,
                              long minimumEpoch, Evidence<Authorization> permit,
                              Supplier<Evidence<State>> currentState) {
        if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 3072
                || keyId == null || keyId.isBlank() || process == null || process.isBlank()
                || approval == null || approval.isBlank() || minimumEpoch < 1
                || permit == null || currentState == null) throw denied();
        this.key=key; this.keyId=keyId; this.process=process; this.approval=approval;
        this.epoch=minimumEpoch; this.permit=permit; this.currentState=currentState;
    }

    @Override public synchronized boolean permits(Authorization a, Instant now) {
        try {
            if (now == null || (lastTime != null && now.isBefore(lastTime))) quarantined=true;
            if (quarantined) return false;
            lastTime=now;
            if (a == null || !a.equals(permit.value) || !verify(authorizationBytes(a),permit.signature)
                    || !GateAOfflinePolicy.OPERATION.equals(a.operation()) || !"GET".equals(a.method())
                    || !GateAOfflinePolicy.ENDPOINT.equals(a.endpoint()) || a.attempt()==null
                    || !process.equals(a.process()) || !keyId.equals(a.keyId())
                    || !approval.equals(a.ownerApprovalReference()) || a.issued()==null || a.expires()==null
                    || now.isBefore(a.issued()) || !now.isBefore(a.expires())
                    || !a.expires().isAfter(a.issued())
                    || Duration.between(a.issued(),a.expires()).compareTo(Duration.ofSeconds(30))>0) return false;
            var signed=currentState.get();
            if (signed == null || !verify(stateBytes(signed.value),signed.signature)) return false;
            State s=signed.value;
            if (s.revoked() || s.quarantined()) { quarantined=true; return false; }
            if (s.epoch()<epoch || (lastStateTime!=null && s.observed().isBefore(lastStateTime))) {
                quarantined=true; return false;
            }
            epoch=s.epoch(); lastStateTime=s.observed();
            return !s.revoked() && !s.quarantined() && a.epoch()==epoch
                    && keyId.equals(s.keyId()) && process.equals(s.process()) && approval.equals(s.approvalReference())
                    && !now.isBefore(s.observed()) && now.isBefore(s.expires())
                    && s.expires().isAfter(s.observed())
                    && Duration.between(s.observed(),s.expires()).compareTo(Duration.ofSeconds(5))<=0;
        } catch (Exception failure) { return false; }
    }
    private boolean verify(byte[] payload, byte[] signature) throws Exception {
        Signature verifier=Signature.getInstance("SHA256withRSA"); verifier.initVerify(key);
        verifier.update(payload); return verifier.verify(signature);
    }
    public static byte[] authorizationBytes(Authorization a) {
        try { return encode("GATE_A_PERMIT_V1",a.attempt().toString(),a.operation(),a.method(),a.endpoint(),
                a.process(),a.keyId(),Long.toString(a.epoch()),a.issued().toString(),a.expires().toString(),a.ownerApprovalReference()); }
        catch (Exception invalid) { throw denied(); }
    }
    public static byte[] stateBytes(State s) {
        try { return encode("GATE_A_STATE_V1",Long.toString(s.epoch()),Boolean.toString(s.revoked()),
                Boolean.toString(s.quarantined()),s.observed().toString(),s.expires().toString(),
                s.keyId(),s.process(),s.approvalReference()); }
        catch (Exception invalid) { throw denied(); }
    }
    private static byte[] encode(String... values) throws Exception {
        var bytes=new ByteArrayOutputStream(); var out=new DataOutputStream(bytes);
        for (String value:values) { if (value==null || value.length()>512) throw denied(); out.writeUTF(value); }
        return bytes.toByteArray();
    }
    @Override public String toString() { return "HALT_AUTHORITY_REDACTED"; }
    private static IllegalStateException denied() { return new IllegalStateException("GATE_A_DENIED"); }
}
