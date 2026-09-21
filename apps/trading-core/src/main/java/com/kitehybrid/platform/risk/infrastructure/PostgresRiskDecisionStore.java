package com.kitehybrid.platform.risk.infrastructure;

import com.kitehybrid.platform.order.application.OrderRepository;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.OrderState;
import com.kitehybrid.platform.risk.application.*;
import com.kitehybrid.platform.risk.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.kitehybrid.platform.risk.domain.RiskReason.*;

/** Atomic audit + lifecycle checkpoint. There is deliberately no broker dispatch/outbox. */
public final class PostgresRiskDecisionStore implements RiskDecisionStore {
    /** One platform account/risk-policy scope; PostgreSQL coordinates all application instances. */
    private static final long ACCOUNT_RISK_LOCK_KEY = 606001L;
    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    private final TransactionTemplate transaction;

    public PostgresRiskDecisionStore(JdbcTemplate jdbc, OrderRepository orders) {
        this.jdbc = jdbc; this.orders = orders;
        transaction = new TransactionTemplate(new DataSourceTransactionManager(java.util.Objects.requireNonNull(jdbc.getDataSource())));
        // Return only after this checkpoint commits; never join a caller's uncommitted execution transaction.
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override public RiskDecision evaluate(OrderId id, Function<OrderRecord, RiskDecision> evaluator) {
        return transaction.execute(status -> {
            // Single-account platform. Serialize decisions, including across processes.
            jdbc.execute("SET LOCAL lock_timeout = '5s'");
            jdbc.execute("SELECT pg_advisory_xact_lock(" + ACCOUNT_RISK_LOCK_KEY + ")");
            // Lock the authoritative order row as well as the account risk scope. The CAS below
            // still guards against changes made outside this transaction's normal path.
            jdbc.query("SELECT order_id FROM trading.orders WHERE order_id=? FOR UPDATE",
                    rs -> { }, id.value());
            var existing = find(id);
            if (existing.isPresent()) return existing.get(); // Immutable replay, not a fresh approval.
            var order = orders.find(id).orElseThrow(() -> new RiskEvaluationException(ORDER_NOT_FOUND));
            if (order.state() != OrderState.VALIDATED) throw new RiskEvaluationException(ORDER_NOT_VALIDATED);
            var decision = evaluator.apply(order);
            if (!decision.orderId().equals(id) || decision.orderVersion() != order.version())
                throw new RiskEvaluationException(ORDER_CHANGED);
            // Without reconciled reservations, more than one approved/in-flight order cannot safely share cash.
            if (decision.approved() && Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM trading.orders
                      WHERE state NOT IN ('CREATED','VALIDATED','REJECTED','CANCELLED','FAILED'))
                    """, Boolean.class)))
                decision = new RiskDecision(id, order.version(), RiskDecision.Outcome.REJECTED,
                        CONCURRENT_EXPOSURE_UNAVAILABLE, decision.evaluatedAt(), decision.policyVersion());
            var target = decision.approved() ? OrderState.RISK_APPROVED : OrderState.REJECTED;
            order.state().transitionTo(target);
            int changed = jdbc.update("""
                    UPDATE trading.orders SET state=?, version=version+1, updated_at=?
                    WHERE order_id=? AND version=? AND state='VALIDATED'
                    """, target.name(), Timestamp.from(decision.evaluatedAt()), id.value(), order.version());
            if (changed != 1) throw new RiskEvaluationException(ORDER_CHANGED);
            jdbc.update("""
                    INSERT INTO trading.risk_decisions(order_id, order_version, outcome, reason, evaluated_at, policy_version)
                    VALUES (?,?,?,?,?,?)
                    """, id.value(), decision.orderVersion(), decision.outcome().name(), decision.reason().name(),
                    Timestamp.from(decision.evaluatedAt()), decision.policyVersion());
            return decision;
        });
    }

    @Override public Optional<RiskDecision> find(OrderId id) {
        return jdbc.query("SELECT * FROM trading.risk_decisions WHERE order_id=?", (rs, row) ->
                new RiskDecision(id, rs.getLong("order_version"), RiskDecision.Outcome.valueOf(rs.getString("outcome")),
                        RiskReason.valueOf(rs.getString("reason")), rs.getTimestamp("evaluated_at").toInstant(),
                        rs.getString("policy_version")), id.value()).stream().findFirst();
    }
}
