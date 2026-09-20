package com.kitehybrid.platform.marketdata.application;

import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-process latest values, without persistence or broker-specific identifiers. */
public interface LatestMarketDataStore {
    /**
     * Atomically replaces an instrument's value only when newer. Compare exchange timestamps when
     * both are available, then receive times on an exchange-time tie or missing exchange time.
     * When timestamps tie, distinct ticks follow publication order because older/newer cannot be
     * established. Identical records are duplicates. False means an old/duplicate value was rejected.
     */
    boolean update(Tick tick);

    Optional<Tick> latest(InstrumentId instrumentId);

    /**
     * Immutable requested values, omitting instruments not yet seen. Each tick is read atomically;
     * values for different instruments may be observed at different instants during this call.
     */
    Map<InstrumentId, Tick> snapshot(Set<InstrumentId> instruments);
}
