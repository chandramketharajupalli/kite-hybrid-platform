package com.kitehybrid.platform.broker.application.read;

import com.kitehybrid.platform.broker.domain.read.BrokerTrade;
import java.util.List;

/** On-demand broker observations; no persistence, polling or trading authorization. */
@FunctionalInterface
public interface BrokerTradesProvider {
    List<BrokerTrade> trades();
}
