package com.kitehybrid.platform.reconciliation.infrastructure;

import com.kitehybrid.platform.broker.domain.read.BrokerTrade;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.reconciliation.application.ReconciliationStore;
import com.kitehybrid.platform.reconciliation.domain.ReconciliationDecision;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Atomic audit, fill deduplication and optimistic lifecycle update. */
public final class PostgresReconciliationStore implements ReconciliationStore {
    private final JdbcTemplate jdbc;
    public PostgresReconciliationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public boolean apply(OrderRecord expected, OrderRecord next, ReconciliationDecision decision,
                         List<BrokerTrade> trades) {
        jdbc.query("SELECT order_id FROM trading.orders WHERE order_id=? FOR UPDATE", rs -> {}, expected.id().value());
        var currentVersion = jdbc.queryForObject("SELECT version FROM trading.orders WHERE order_id=?",
                Long.class, expected.id().value());
        if (currentVersion == null || currentVersion != expected.version()) return false;
        jdbc.update("""
                INSERT INTO trading.reconciliation_decisions(reconciliation_id, order_id, state_before, state_after,
                    outcome, reason, observed_at, local_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, decision.reconciliationId(), decision.orderId().value(), decision.stateBefore().name(),
                decision.stateAfter().map(Enum::name).orElse(null), decision.outcome().name(), decision.reason().name(),
                Timestamp.from(decision.observedAt()), decision.localVersion());
        for (var trade : trades) {
            jdbc.update("""
                    INSERT INTO trading.reconciliation_trades(reconciliation_id, broker_trade_id, broker_order_id,
                        quantity, price, filled_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (broker_trade_id, broker_order_id) DO NOTHING
                    """, decision.reconciliationId(), trade.brokerTradeId(), trade.brokerOrderId(), trade.quantity(),
                    trade.price(), Timestamp.from(trade.filledAt()));
        }
        if (expected.state() == next.state() && expected.version() == next.version()) return true;
        if (jdbc.update("""
                UPDATE trading.orders SET state=?, broker_order_id=?, failure_category=?, updated_at=?, version=?
                WHERE order_id=? AND version=?
                """, next.state().name(), next.brokerOrderId().orElse(null), next.failureCategory().orElse(null),
                Timestamp.from(next.updatedAt()), next.version(), expected.id().value(), expected.version()) != 1) {
            throw new IllegalStateException("Reconciliation lifecycle update lost optimistic lock");
        }
        return true;
    }
}
