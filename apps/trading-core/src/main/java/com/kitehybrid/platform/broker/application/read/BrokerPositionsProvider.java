package com.kitehybrid.platform.broker.application.read;

import com.kitehybrid.platform.broker.domain.read.BrokerPositions;

/** On-demand broker observations; no persistence, polling or trading authorization. */
@FunctionalInterface
public interface BrokerPositionsProvider {
    BrokerPositions positions();
}
