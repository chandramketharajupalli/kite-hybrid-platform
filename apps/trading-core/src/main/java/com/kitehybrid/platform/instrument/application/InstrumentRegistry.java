package com.kitehybrid.platform.instrument.application;

import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.util.Optional;

/** Mapping must be refreshed by trading date; broker tokens may be reused. */
public interface InstrumentRegistry {
    Optional<InstrumentId> resolve(BrokerInstrumentId brokerId);
}
