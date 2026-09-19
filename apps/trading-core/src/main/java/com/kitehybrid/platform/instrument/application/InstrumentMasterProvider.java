package com.kitehybrid.platform.instrument.application;

import com.kitehybrid.platform.instrument.domain.Instrument;
import java.util.List;

/** Broker adapters return only normalized internal reference data; malformed data fails retrieval. */
@FunctionalInterface
public interface InstrumentMasterProvider {
    List<Instrument> retrieve();
}
