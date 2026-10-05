package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.instrument.domain.InstrumentSnapshot;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.util.List;
import java.util.Map;

/** Transient normalized observations. Never stored as a broker payload. */
public record OrderRiskInput(InstrumentSnapshot instruments, Map<InstrumentId, Tick> ticks,
                             boolean marketHealthy, BrokerPositions positions,
                             List<BrokerHolding> holdings, BrokerMargins margins,
                             List<BrokerOrder> orders, java.util.Optional<OrderMarginQuote> marginQuote) {
    public OrderRiskInput(InstrumentSnapshot instruments, Map<InstrumentId, Tick> ticks, boolean marketHealthy,
            BrokerPositions positions, List<BrokerHolding> holdings, BrokerMargins margins, List<BrokerOrder> orders) {
        this(instruments,ticks,marketHealthy,positions,holdings,margins,orders,java.util.Optional.empty());
    }
    public OrderRiskInput { java.util.Objects.requireNonNull(marginQuote); }
}
