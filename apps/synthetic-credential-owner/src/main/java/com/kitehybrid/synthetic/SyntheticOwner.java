package com.kitehybrid.synthetic;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Package-private fixture boundary. No main, public API, token source or real mode. */
final class SyntheticOwner implements AutoCloseable {
    static final String VERSION = "SyntheticOwner.v2";
    static final String PROPOSAL = "5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7";
    static final String PURPOSE = "SYNTHETIC_EQUITY_SHAPE";
    static final String PATH = "/user/margins/equity";
    enum Outcome { SYNTHETIC_READ_SUCCEEDED, SYNTHETIC_READ_DENIED,
        SYNTHETIC_READ_OUTCOME_UNKNOWN, PROTOTYPE_PRECONDITION_FAILED }
    enum Delivery { NOT_DISPATCHED, MAY_HAVE_DISPATCHED, OBSERVED_COMPLETED }
    record Result(Outcome outcome, boolean equityFieldPresent, Delivery delivery) {}
    enum Fault { NONE, AFTER_ACK_BEFORE_DISPATCH, BEFORE_SOCKET, AFTER_SOCKET_BEFORE_SEND,
        DURING_SEND, AFTER_SEND, DURING_PARSE, AFTER_RESPONSE, BEFORE_OUTCOME }

    /** Minted only after the owner receives its durable claim acknowledgment. */
    static final class DispatchTicket {
        private final java.util.concurrent.atomic.AtomicBoolean taken = new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.function.BooleanSupplier authorization;
        private final String method, endpoint;
        private DispatchTicket(Request request, java.util.function.BooleanSupplier authorization) {
            method = request.method(); endpoint = request.endpoint(); this.authorization = authorization;
        }
        boolean take(String method, String endpoint) {
            return this.method.equals(method) && this.endpoint.equals(endpoint) && taken.compareAndSet(false, true);
        }
        boolean authorized() { return authorization.getAsBoolean(); }
        @Override public String toString() { return "SYNTHETIC_TICKET_REDACTED"; }
    }

    static final class Credential implements AutoCloseable {
        private final byte[] material = new byte[32];
        private boolean closed;
        private Credential() { new SecureRandom().nextBytes(material); }
        static Credential generatedSyntheticFixture() { return new Credential(); }
        synchronized boolean available() { return !closed; }
        @Override public synchronized void close() { closed = true; Arrays.fill(material, (byte) 0); }
        @Override public String toString() { return "SYNTHETIC_CREDENTIAL_REDACTED"; }
    }

    record Request(UUID id, String version, String owner, String caller, String recipient,
                   String account, String purpose, String method, String endpoint,
                   Instant issued, Instant expires, long haltEpoch, String authority) {
        String fingerprint() {
            try {
                // Every string is length framed; no delimiter ambiguity.
                var bytes = new java.io.ByteArrayOutputStream();
                var out = new java.io.DataOutputStream(bytes);
                for (String value : new String[]{PROPOSAL, id.toString(), version, owner, caller, recipient,
                        account, purpose, method, endpoint, issued.toString(), expires.toString(),
                        Long.toString(haltEpoch), authority}) out.writeUTF(value);
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
            } catch (Exception ignored) { throw denied(); }
        }
        @Override public String toString() { return "SYNTHETIC_REQUEST_REDACTED"; }
    }

    /** Implementations exist only in test sources; none ships with the JAR. */
    interface Ledger {
        boolean consume(Request request);
        default boolean consume(Request request, String haltDigest) { return false; }
        void terminal(UUID id, Outcome outcome);
    }
    record SinkPolicy(Set<String> allowed, boolean hostCaptureDisabled) {
        SinkPolicy { allowed = Set.copyOf(allowed); }
        boolean safe() { return hostCaptureDisabled && allowed.equals(Set.of("BOUNDED_RESULT")); }
        @Override public String toString() { return "SYNTHETIC_SINK_POLICY"; }
    }
    private final String owner, caller, recipient, account;
    private final Credential credential;
    private final Clock clock;
    private final Ledger ledger;
    private final SyntheticHalt halt;
    private final LoopbackPeer peer;
    private final Supplier<SinkPolicy> sinks;
    private volatile boolean closed;

    SyntheticOwner(String owner, String caller, String recipient, String account, Credential credential,
                   Clock clock, Ledger ledger, SyntheticHalt halt, LoopbackPeer peer, Supplier<SinkPolicy> sinks) {
        for (String identity : new String[]{owner, caller, recipient, account})
            if (!syntheticIdentity(identity)) throw denied();
        this.owner = owner; this.caller = caller; this.recipient = recipient; this.account = account;
        this.credential = java.util.Objects.requireNonNull(credential);
        this.clock = java.util.Objects.requireNonNull(clock); this.ledger = java.util.Objects.requireNonNull(ledger);
        this.halt = java.util.Objects.requireNonNull(halt); this.peer = java.util.Objects.requireNonNull(peer);
        this.sinks = java.util.Objects.requireNonNull(sinks);
        if (!safe()) throw denied();
    }

    Result observe(Request request, String authenticatedSyntheticCaller, SyntheticHalt.Assertion assertion, Fault fault) {
        boolean consumed = false;
        Delivery delivery = Delivery.NOT_DISPATCHED;
        try {
            if (!valid(request, authenticatedSyntheticCaller)) return result(Outcome.PROTOTYPE_PRECONDITION_FAILED);
            var lease = halt.admit(assertion, request);
            if (lease == null) return result(Outcome.SYNTHETIC_READ_DENIED);
            if (!ledger.consume(request, assertion.fingerprint())) return result(Outcome.SYNTHETIC_READ_DENIED);
            consumed = true;
            if (fault == Fault.AFTER_ACK_BEFORE_DISPATCH) return finish(request, Outcome.SYNTHETIC_READ_OUTCOME_UNKNOWN, delivery);
            if (!valid(request, authenticatedSyntheticCaller) || !halt.recheck(lease, request))
                return finish(request, Outcome.SYNTHETIC_READ_DENIED, delivery);
            var ticket = new DispatchTicket(request, () -> valid(request, authenticatedSyntheticCaller) && halt.recheck(lease, request));
            var exchange = peer.get(request.method(), request.endpoint(), ticket, fault);
            delivery = exchange.delivery();
            Outcome outcome = exchange.outcome();
            if (fault == Fault.AFTER_RESPONSE) outcome = Outcome.SYNTHETIC_READ_OUTCOME_UNKNOWN;
            if (!valid(request, authenticatedSyntheticCaller) || !halt.recheck(lease, request))
                outcome = Outcome.SYNTHETIC_READ_DENIED;
            if (fault == Fault.BEFORE_OUTCOME) return result(Outcome.SYNTHETIC_READ_OUTCOME_UNKNOWN, delivery);
            return finish(request, outcome, delivery);
        } catch (Exception ignored) {
            return result(consumed ? Outcome.SYNTHETIC_READ_OUTCOME_UNKNOWN : Outcome.PROTOTYPE_PRECONDITION_FAILED,
                    consumed ? Delivery.MAY_HAVE_DISPATCHED : delivery);
        }
    }
    private Result finish(Request request, Outcome outcome, Delivery delivery) {
        try { ledger.terminal(request.id(), outcome); }
        catch (Exception ignored) { return result(Outcome.SYNTHETIC_READ_OUTCOME_UNKNOWN, delivery); }
        return result(outcome, delivery);
    }
    private static Result result(Outcome outcome) { return result(outcome, Delivery.NOT_DISPATCHED); }
    private static Result result(Outcome outcome, Delivery delivery) { return new Result(outcome, outcome == Outcome.SYNTHETIC_READ_SUCCEEDED, delivery); }
    private boolean valid(Request r, String principal) {
        if (!safe() || r == null || r.id() == null || r.issued() == null || r.expires() == null) return false;
        Instant now = clock.instant();
        return owner.equals(r.owner()) && caller.equals(r.caller()) && caller.equals(principal)
                && recipient.equals(r.recipient()) && account.equals(r.account()) && VERSION.equals(r.version())
                && PURPOSE.equals(r.purpose()) && "SYNTHETIC_ONLY".equals(r.authority()) && r.haltEpoch() > 0
                && !now.isBefore(r.issued()) && now.isBefore(r.expires()) && r.expires().isAfter(r.issued())
                && Duration.between(r.issued(), r.expires()).compareTo(Duration.ofSeconds(30)) <= 0
                && peer.accepts(r.method(), r.endpoint());
    }
    private boolean safe() {
        SinkPolicy policy = sinks.get();
        return !closed && credential.available() && policy != null && policy.safe() && environmentSafe();
    }
    static boolean environmentSafe() {
        for (String key : new String[]{"http.proxyHost", "https.proxyHost", "socksProxyHost", "java.net.useSystemProxies",
                "jdk.httpclient.HttpClient.log", "javax.net.debug"})
            if (System.getProperty(key) != null) return false;
        for (String key : new String[]{"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY"})
            if (System.getenv(key) != null && !System.getenv(key).isBlank()) return false;
        return true;
    }
    static boolean syntheticIdentity(String s) { return s != null && s.matches("SYNTHETIC_[A-Za-z0-9_-]{1,80}"); }
    static IllegalArgumentException denied() { return new IllegalArgumentException("SYNTHETIC_PRECONDITION_DENIED"); }
    @Override public void close() { closed = true; credential.close(); }
    @Override public String toString() { return "SYNTHETIC_OWNER_REDACTED"; }
}
