package com.kitehybrid.platform.instrument.application;

import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.ExchangeSymbol;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.InstrumentSnapshot;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read one snapshot when multiple lookups must observe the same refresh generation.
 * Broker tokens can be reused; callers must enforce the required reference-data freshness.
 */
public interface InstrumentRegistry {
    default Optional<InstrumentId> resolve(BrokerInstrumentId brokerId) {
        return findByBrokerId(brokerId).map(Instrument::id);
    }

    default Optional<Instrument> findById(InstrumentId id) {
        return Optional.ofNullable(snapshot().byId().get(Objects.requireNonNull(id)));
    }

    default Optional<Instrument> findByBrokerId(BrokerInstrumentId brokerId) {
        return Optional.ofNullable(snapshot().byBrokerId().get(Objects.requireNonNull(brokerId)));
    }

    default Optional<Instrument> findByExchangeAndSymbol(String exchange, String symbol) {
        return Optional.ofNullable(snapshot().byExchangeAndSymbol().get(new ExchangeSymbol(exchange, symbol)));
    }

    InstrumentSnapshot snapshot();

    /** Reject invalid candidates without changing any of the currently published indexes. */
    InstrumentSnapshot replace(List<Instrument> instruments, Instant refreshedAt);
}
