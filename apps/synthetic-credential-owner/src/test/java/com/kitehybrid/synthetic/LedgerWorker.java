package com.kitehybrid.synthetic;

import java.time.Instant;
import java.util.UUID;

/** Disposable integration-test subprocess only; absent from production artifact. */
public final class LedgerWorker {
    public static void main(String[] args) {
        var ledger = new DisposableLedger(Integer.parseInt(args[0]), args[1], DisposableLedger.Fault.valueOf(args[3]));
        var request = request(UUID.fromString(args[2]));
        boolean won = ledger.consume(request);
        // Abrupt process termination immediately after acknowledgment, no cleanup/recovery grant.
        Runtime.getRuntime().halt(won ? 17 : 18);
    }
    static SyntheticOwner.Request request(UUID id) {
        return new SyntheticOwner.Request(id, SyntheticOwner.VERSION, "SYNTHETIC_OWNER", "SYNTHETIC_CALLER",
                "SYNTHETIC_RECIPIENT", "SYNTHETIC_ACCOUNT", SyntheticOwner.PURPOSE, "GET",
                "http://127.0.0.1:1/user/margins/equity", Instant.parse("2000-01-01T00:00:00Z"),
                Instant.parse("2000-01-01T00:00:20Z"), 1, "SYNTHETIC_ONLY");
    }
}
