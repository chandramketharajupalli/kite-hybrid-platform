package com.kitehybrid.platform.reconciliation.application;

import com.kitehybrid.platform.broker.domain.read.BrokerTrade;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.reconciliation.domain.ReconciliationDecision;
import java.util.List;

/** PostgreSQL is the authority for the audit and lifecycle checkpoint. */
public interface ReconciliationStore {
    boolean apply(OrderRecord expected, OrderRecord next, ReconciliationDecision decision,
                  List<BrokerTrade> observedTrades);
}
