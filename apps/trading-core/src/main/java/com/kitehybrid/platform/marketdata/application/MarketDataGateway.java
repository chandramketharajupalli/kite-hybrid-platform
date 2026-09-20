package com.kitehybrid.platform.marketdata.application;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.marketdata.domain.StreamMode;
import java.util.Set;

public interface MarketDataGateway extends AutoCloseable {
    enum State { STOPPED, STARTING, CONNECTED, RECONNECTING, DEGRADED, STOPPING }
    void start();
    void subscribe(Set<InstrumentId> instruments);
    void subscribe(Set<InstrumentId> instruments, StreamMode mode);
    void unsubscribe(Set<InstrumentId> instruments);
    State state();
    MarketDataHealth health();
    void stop();
    @Override void close();
}
