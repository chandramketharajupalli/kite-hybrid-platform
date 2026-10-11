package com.kitehybrid.synthetic;

import java.io.DataInputStream;
import java.util.UUID;
import static com.kitehybrid.synthetic.SyntheticSecurityTest.*;

/** Parent-killed test process; never packaged. Bootstrap material travels only over stdin. */
public final class RootRecoveryWorker {
    public static void main(String[] args) throws Exception {
        var client = FenceWitness.Client.bootstrap(new DataInputStream(System.in));
        String stage = args[4];
        var local = new DisposableLedger(Integer.parseInt(args[0]), args[1], DisposableLedger.Fault.NONE,
                point -> { if (point.equals(stage)) barrier(stage); });
        var peer = new LoopbackPeer(Integer.parseInt(args[3]), 3000);
        var clock = new MutableClock(); var key = key(); var template = request(peer);
        var request = new SyntheticOwner.Request(UUID.fromString(args[2]), template.version(), template.owner(),
                template.caller(), template.recipient(), template.account(), template.purpose(), template.method(),
                template.endpoint(), template.issued(), template.expires(), template.haltEpoch(), template.authority());
        // Same witness-before-local ordering as FencedLedger, with an observable kill boundary.
        var ledger = new SyntheticOwner.Ledger() {
            public boolean consume(SyntheticOwner.Request ignored) { return false; }
            public boolean consume(SyntheticOwner.Request value, String digest) {
                if (!client.claim(value, digest)) return false;
                if (stage.equals("AFTER_WITNESS_ACK")) barrier(stage);
                return local.consume(value, digest);
            }
            public void terminal(UUID id, SyntheticOwner.Outcome outcome) {
                if (stage.equals("BEFORE_OUTCOME")) barrier(stage);
                local.terminal(id, outcome);
            }
        };
        try (var owner = owner(ledger, new SyntheticHalt(clock, 1, "SYNTHETIC_KEY", key.getPublic()), peer, clock, () -> SAFE)) {
            owner.observe(request, CALLER, sign(request, key), SyntheticOwner.Fault.NONE);
            // Expected barriers never return. Reaching here is an observable harness failure.
            Runtime.getRuntime().halt(31);
        }
    }
    static void barrier(String stage) {
        System.out.println("SYNTHETIC_STAGE=" + stage); System.out.flush();
        try { System.in.read(); } catch (Exception ignored) { }
        // EOF or unexpected control input never continues into persistence/dispatch.
        Runtime.getRuntime().halt(29);
    }
}
