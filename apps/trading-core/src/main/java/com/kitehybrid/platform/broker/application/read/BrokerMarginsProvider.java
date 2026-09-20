package com.kitehybrid.platform.broker.application.read;

import com.kitehybrid.platform.broker.domain.read.BrokerMargins;

/** On-demand broker observations; no persistence, polling or trading authorization. */
@FunctionalInterface
public interface BrokerMarginsProvider {
    BrokerMargins margins();
}
