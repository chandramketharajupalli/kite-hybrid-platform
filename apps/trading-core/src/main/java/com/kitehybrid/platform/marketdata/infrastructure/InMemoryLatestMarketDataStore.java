package com.kitehybrid.platform.marketdata.infrastructure;

import com.kitehybrid.platform.marketdata.application.LatestMarketDataStore;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Atomic per-instrument publication of immutable ticks. No hot-path external I/O. */
public final class InMemoryLatestMarketDataStore implements LatestMarketDataStore {
    private final ConcurrentHashMap<InstrumentId, Tick> latest = new ConcurrentHashMap<>();

    @Override public boolean update(Tick tick) {
        Objects.requireNonNull(tick);
        boolean[] accepted = {false};
        latest.compute(tick.instrumentId(), (instrumentId, previous) -> {
            if (previous == null || (!tick.equals(previous) && compare(tick, previous) >= 0)) {
                accepted[0] = true;
                return tick;
            }
            return previous;
        });
        return accepted[0];
    }

    @Override public Optional<Tick> latest(InstrumentId instrumentId) {
        return Optional.ofNullable(latest.get(Objects.requireNonNull(instrumentId)));
    }

    @Override public Map<InstrumentId, Tick> snapshot(Set<InstrumentId> instruments) {
        Map<InstrumentId, Tick> result = new HashMap<>();
        for (InstrumentId instrumentId : Set.copyOf(instruments)) {
            Tick tick = latest.get(instrumentId);
            if (tick != null) result.put(instrumentId, tick);
        }
        return Map.copyOf(result);
    }

    private static int compare(Tick incoming, Tick previous) {
        if (incoming.exchangeTimestamp().isPresent() && previous.exchangeTimestamp().isPresent()) {
            int exchangeComparison = incoming.exchangeTimestamp().orElseThrow()
                    .compareTo(previous.exchangeTimestamp().orElseThrow());
            if (exchangeComparison != 0) return exchangeComparison;
        }
        return incoming.receivedAt().compareTo(previous.receivedAt());
    }
}
