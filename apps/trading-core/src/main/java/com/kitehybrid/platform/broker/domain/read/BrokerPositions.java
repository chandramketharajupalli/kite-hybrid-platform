package com.kitehybrid.platform.broker.domain.read;

import java.util.List;

/** Net and intraday observations remain separate; neither is calculated from the other. */
public record BrokerPositions(List<BrokerPosition> net, List<BrokerPosition> day) {
    public BrokerPositions { net = List.copyOf(net); day = List.copyOf(day); }
}
