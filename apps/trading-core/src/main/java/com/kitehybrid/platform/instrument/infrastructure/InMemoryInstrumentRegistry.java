package com.kitehybrid.platform.instrument.infrastructure;

import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.InstrumentSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Reference-data cache only; not a durable ledger or an execution authorization source. */
public final class InMemoryInstrumentRegistry implements InstrumentRegistry {
    private final AtomicReference<InstrumentSnapshot> current = new AtomicReference<>(InstrumentSnapshot.empty());

    @Override public InstrumentSnapshot snapshot() {
        return current.get();
    }

    @Override public synchronized InstrumentSnapshot replace(List<Instrument> instruments, Instant refreshedAt) {
        long nextVersion = Math.incrementExact(current.get().version());
        InstrumentSnapshot candidate = InstrumentSnapshot.validated(instruments, nextVersion, refreshedAt);
        current.set(candidate);
        return candidate;
    }
}
