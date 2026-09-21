package com.kitehybrid.platform.strategy.infrastructure;

import com.kitehybrid.platform.order.domain.Signal;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import com.kitehybrid.platform.strategy.application.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL authority for immutable strategy evaluation identity and replay. */
public final class PostgresStrategyEvaluationStore implements StrategyEvaluationStore {
    private final JdbcTemplate jdbc;
    public PostgresStrategyEvaluationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override @Transactional
    public Claim claim(StrategyEvaluation evaluation) {
        int inserted = jdbc.update("""
                INSERT INTO trading.strategy_evaluations(strategy_id, strategy_version, event_key, signal_id,
                    instrument_id, action, quantity, reference_price, reason, evaluated_at, intent_id, order_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (strategy_id, strategy_version, event_key) DO NOTHING
                """, evaluation.strategyId().value(), evaluation.strategyVersion(), evaluation.eventKey(),
                evaluation.signal().id().value(), evaluation.signal().instrumentId().value(), evaluation.signal().side().name(),
                evaluation.signal().quantity(), evaluation.signal().referencePrice(), evaluation.signal().reason().name(),
                Timestamp.from(evaluation.evaluatedAt()), evaluation.intentId().map(OrderIntentId::value).orElse(null),
                evaluation.orderId().map(OrderId::value).orElse(null));
        if (inserted == 1) return Claim.CREATED;
        var existing = find(evaluation.eventKey(), evaluation.strategyId().value(), evaluation.strategyVersion()).orElseThrow();
        return existing.signal().id().equals(evaluation.signal().id()) ? Claim.EXISTING : Claim.CONFLICT;
    }
    @Override public Optional<StrategyEvaluation> find(String eventKey, String strategyId, String strategyVersion) {
        return jdbc.query("SELECT * FROM trading.strategy_evaluations WHERE event_key=? AND strategy_id=? AND strategy_version=?",
                (rs, row) -> new StrategyEvaluation(rs.getString("event_key"), new StrategyId(rs.getString("strategy_id")),
                        rs.getString("strategy_version"), new Signal(new SignalId(rs.getObject("signal_id", UUID.class)),
                        new StrategyId(rs.getString("strategy_id")), new InstrumentId(rs.getObject("instrument_id", UUID.class)),
                        Signal.Side.valueOf(rs.getString("action")), rs.getInt("quantity"), rs.getBigDecimal("reference_price"),
                        rs.getTimestamp("evaluated_at").toInstant(), rs.getString("strategy_version"), Signal.Reason.valueOf(rs.getString("reason"))),
                        Optional.ofNullable(rs.getObject("intent_id", UUID.class)).map(OrderIntentId::new),
                        Optional.ofNullable(rs.getObject("order_id", UUID.class)).map(OrderId::new), rs.getTimestamp("evaluated_at").toInstant()),
                eventKey, strategyId, strategyVersion).stream().findFirst();
    }
    @Override @Transactional
    public boolean attachOrder(StrategyEvaluation expected, StrategyEvaluation completed) {
        if (completed.orderId().isEmpty()) return false;
        int updated = jdbc.update("""
                UPDATE trading.strategy_evaluations SET intent_id=?, order_id=?
                WHERE strategy_id=? AND strategy_version=? AND event_key=? AND order_id IS NULL
                """, completed.intentId().map(OrderIntentId::value).orElse(null), completed.orderId().map(OrderId::value).orElse(null),
                expected.strategyId().value(), expected.strategyVersion(), expected.eventKey());
        if (updated == 1) return true;
        return find(expected.eventKey(), expected.strategyId().value(), expected.strategyVersion())
                .flatMap(StrategyEvaluation::orderId).equals(completed.orderId());
    }
}
