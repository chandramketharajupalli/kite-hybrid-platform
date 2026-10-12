package com.kitehybrid.synthetic;

import java.security.PublicKey;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** HALT-enforced composition. Test scope only; the old boundary is never exposed to callers. */
final class GateAHaltBoundary {
    static final String OPERATION = "SYNTHETIC_GATE_A_INSTRUMENT_MASTER";
    static final String VERSION = "SYNTHETIC_GATE_A_V1";
    static final class Control {
        private final SyntheticHalt halt;
        private final String keyId;
        private final java.util.Set<UUID> retired = new java.util.HashSet<>();
        Control(Clock clock, long epoch, String keyId, PublicKey key) {
            this.halt = new SyntheticHalt(clock, epoch, keyId, key); this.keyId = keyId;
        }
        synchronized void revoke() { halt.revoke(keyId); }
        synchronized void available(boolean value) { halt.available(value); }
        synchronized void epoch(long value) { halt.epoch(value); }
        synchronized void conflictingKey(PublicKey key) { halt.rotate(keyId, key); }
        @Override public String toString() { return "GATE_A_CONTROL_REDACTED"; }
    }
    private final String journalIdentity, incarnation;
    private final Clock clock;
    private final Control control;
    private final AtomicInteger sends = new AtomicInteger(), receipts = new AtomicInteger();
    GateAHaltBoundary(String journalIdentity, String incarnation, Clock clock, Control control) {
        UUID.fromString(journalIdentity);
        if (!SyntheticOwner.syntheticIdentity(incarnation)) throw GateABoundary.denied();
        this.journalIdentity=journalIdentity; this.incarnation=incarnation; this.clock=clock; this.control=control;
    }
    static SyntheticOwner.Request request(GateABoundary.Approval approval, String incarnation, long epoch, String keyId) {
        return new SyntheticOwner.Request(approval.id(),VERSION,approval.owner(),incarnation,incarnation,
                "SYNTHETIC_NO_ACCOUNT",OPERATION,"GET",GateABoundary.URL,approval.issued(),approval.expires(),epoch,keyId);
    }
    GateABoundary.Observation acquire(String method, String url, String proxy, GateABoundary.Approval approval,
            String owner, long epoch, SyntheticHalt.Assertion assertion, GateABoundary.Response response,
            Consumer<String> observer) {
        try {
            if (!"GET".equals(method) || !GateABoundary.URL.equals(url) || proxy != null || control == null
                    || approval == null || !approval.synthetic() || !approval.retentionAllowed() || !"A".equals(approval.gate())
                    || owner == null || !owner.equals(approval.owner()) || !SyntheticOwner.syntheticIdentity(owner)
                    || approval.id() == null || approval.issued() == null || approval.expires() == null
                    || clock.instant().isBefore(approval.issued()) || !clock.instant().isBefore(approval.expires())
                    || assertion == null || !control.keyId.equals(assertion.keyId()) || response == null || observer == null)
                throw GateABoundary.denied();
            var request=request(approval,incarnation,epoch,control.keyId);
            observer.accept("BEFORE_AUTHORIZATION");
            GateABoundary.Observation result;
            SyntheticHalt.Lease lease;
            synchronized(control) {
                lease=control.halt.admit(assertion,request);
                if(lease==null) throw GateABoundary.denied();
                if(!control.retired.add(approval.id())) throw GateABoundary.denied();
                observer.accept("BEFORE_RESERVATION");
                if(!control.halt.recheck(lease,request)) throw GateABoundary.denied();
                var journal=new AttemptJournal(journalIdentity,stage->{
                    observer.accept(stage);
                    if(stage.equals("AFTER_JOURNAL_FORCE")) {
                        observer.accept("BEFORE_DISPATCH");
                        // Linearization point: state mutations share this monitor until closed-fixture dispatch finishes.
                        if(!control.halt.recheck(lease,request)) throw GateABoundary.denied();
                    }
                });
                var delegate=new GateABoundary(journal,clock);
                try { result=delegate.acquire(method,url,proxy,approval,owner,response); }
                finally { sends.addAndGet(delegate.fixtureDispatches()); }
                receipts.incrementAndGet(); // Only a completed fixture response establishes a receipt.
            }
            // Delivery is still in flight to the caller. A revocation can discard the result, not undo the send.
            observer.accept("DURING_RESULT_DELIVERY");
            synchronized(control) {
                if(!control.halt.recheck(lease,request)) throw GateABoundary.denied();
            }
            observer.accept("AFTER_PEER_OBSERVED");
            synchronized(control) {
                if(!control.halt.recheck(lease,request)) throw GateABoundary.denied();
                return result;
            }
        } catch(Exception failure) { throw GateABoundary.denied(); }
    }
    int sends() { return sends.get(); }
    int receipts() { return receipts.get(); }
    int realRequests() { return 0; }
    @Override public String toString() { return "GATE_A_HALT_BOUNDARY_REDACTED"; }
}
