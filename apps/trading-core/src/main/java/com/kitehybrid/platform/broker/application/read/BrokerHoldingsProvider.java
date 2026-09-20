package com.kitehybrid.platform.broker.application.read;

import com.kitehybrid.platform.broker.domain.read.BrokerHolding;
import java.util.List;

/** On-demand broker observations; no persistence, polling or trading authorization. */
@FunctionalInterface
public interface BrokerHoldingsProvider {
    List<BrokerHolding> holdings();
}
