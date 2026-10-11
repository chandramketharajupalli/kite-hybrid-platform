package com.kitehybrid.synthetic;

import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Local test trust store; never claims an independent live issuer. */
final class SyntheticHalt {
    static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    record Assertion(String issuer, String keyId, String audience, String owner, String operation,
                     long epoch, Instant issued, Instant expires, UUID nonce, String requestDigest, byte[] signature) {
        Assertion { signature = signature == null ? new byte[0] : signature.clone(); }
        @Override public byte[] signature() { return signature.clone(); }
        byte[] payload() {
            try {
                var bytes = new java.io.ByteArrayOutputStream(); var out = new java.io.DataOutputStream(bytes);
                for (String s : new String[]{"SYNTHETIC_HALT_V2_RSA3072", issuer, keyId, audience, owner, operation,
                        Long.toString(epoch), issued.toString(), expires.toString(), nonce.toString(), requestDigest}) out.writeUTF(s);
                return bytes.toByteArray();
            } catch (Exception ignored) { throw SyntheticOwner.denied(); }
        }
        @Override public String toString() { return "SYNTHETIC_HALT_REDACTED"; }
        String fingerprint() {
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                digest.update(payload()); digest.update(signature);
                return java.util.HexFormat.of().formatHex(digest.digest());
            } catch (Exception ignored) { throw SyntheticOwner.denied(); }
        }
    }
    static final class Lease {
        private final Assertion assertion;
        private Lease(Assertion a) { assertion = a; }
        @Override public String toString() { return "SYNTHETIC_LEASE_REDACTED"; }
    }
    private final Clock clock;
    private final Map<String, PublicKey> keys = new ConcurrentHashMap<>();
    private final Set<String> revoked = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Lease> used = new ConcurrentHashMap<>();
    private volatile boolean available = true;
    private volatile long epoch;
    private Instant lastObserved;
    SyntheticHalt(Clock clock, long epoch, String keyId, PublicKey key) {
        this.clock = clock; this.epoch = epoch; rotate(keyId, key);
    }
    synchronized void rotate(String id, PublicKey key) {
        if (!SyntheticOwner.syntheticIdentity(id) || !(key instanceof java.security.interfaces.RSAPublicKey rsa)
                || rsa.getModulus().bitLength() < 3072) throw SyntheticOwner.denied();
        var previous = keys.putIfAbsent(id, key);
        if (previous != null && !java.util.Arrays.equals(previous.getEncoded(), key.getEncoded())) {
            available = false; throw SyntheticOwner.denied();
        }
    }
    void revoke(String id) { revoked.add(id); }
    void available(boolean value) { available = value; }
    synchronized void epoch(long value) {
        if (value < epoch || value <= 0) throw SyntheticOwner.denied();
        epoch = value;
    }
    Lease admit(Assertion a, SyntheticOwner.Request r) {
        if (!valid(a, r)) return null;
        Lease lease = new Lease(a);
        return used.putIfAbsent(a.nonce(), lease) == null ? lease : null;
    }
    boolean recheck(Lease lease, SyntheticOwner.Request r) {
        return lease != null && used.get(lease.assertion.nonce()) == lease && valid(lease.assertion, r);
    }
    private synchronized boolean valid(Assertion a, SyntheticOwner.Request r) {
        try {
            Instant now = clock.instant();
            if (lastObserved != null && now.isBefore(lastObserved)) { available = false; return false; }
            lastObserved = now;
            if (!available || a == null || r == null || !"SYNTHETIC_ISSUER".equals(a.issuer())
                    || a.nonce() == null || !a.audience().equals(r.recipient()) || !a.owner().equals(r.owner())
                    || !a.operation().equals(r.purpose()) || a.epoch() != epoch || a.epoch() != r.haltEpoch()
                    || !a.requestDigest().equals(r.fingerprint()) || now.isBefore(a.issued()) || !now.isBefore(a.expires())
                    || !a.expires().isAfter(a.issued()) || a.expires().isAfter(r.expires())
                    || Duration.between(a.issued(), a.expires()).compareTo(Duration.ofSeconds(30)) > 0
                    || revoked.contains(a.keyId()) || !keys.containsKey(a.keyId())) return false;
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM); verifier.initVerify(keys.get(a.keyId()));
            verifier.update(a.payload()); return verifier.verify(a.signature());
        } catch (Exception ignored) { return false; }
    }
}
