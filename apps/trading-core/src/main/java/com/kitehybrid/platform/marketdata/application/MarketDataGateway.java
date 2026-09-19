package com.kitehybrid.platform.marketdata.application;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.util.Set;

public interface MarketDataGateway extends AutoCloseable {
    enum State { STOPPED, STARTING, CONNECTED, RECONNECTING, DEGRADED, STOPPING }
    void start();
    void subscribe(Set<InstrumentId> instruments);
    State state();
    @Override void close();
}
