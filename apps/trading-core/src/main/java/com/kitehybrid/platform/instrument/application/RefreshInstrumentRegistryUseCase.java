package com.kitehybrid.platform.instrument.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Manual refresh; serialize retrieval and publication to avoid out-of-order refresh completion. */
public final class RefreshInstrumentRegistryUseCase {
    private final InstrumentMasterProvider provider;
    private final InstrumentRegistry registry;
    private final Clock clock;

    public RefreshInstrumentRegistryUseCase(InstrumentMasterProvider provider, InstrumentRegistry registry,
                                            Clock clock) {
        this.provider = Objects.requireNonNull(provider);
        this.registry = Objects.requireNonNull(registry);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized RefreshResult refresh() {
        var candidate = provider.retrieve();
        var snapshot = registry.replace(candidate, clock.instant());
        return new RefreshResult(snapshot.size(), snapshot.size(), 0, snapshot.version(), snapshot.refreshedAt());
    }

    /** Failed refreshes throw and preserve the previous snapshot; no partial acceptance is reported. */
    public record RefreshResult(int retrievedCount, int acceptedCount, int rejectedCount,
                                long snapshotVersion, Instant refreshedAt) {}
}
