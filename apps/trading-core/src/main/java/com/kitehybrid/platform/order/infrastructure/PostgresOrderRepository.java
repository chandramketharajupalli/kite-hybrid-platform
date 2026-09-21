package com.kitehybrid.platform.order.infrastructure;

import com.kitehybrid.platform.order.application.OrderRepository;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.OrderState;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Durable order records and idempotency claims. No broker transport is reachable from this class. */
public final class PostgresOrderRepository implements OrderRepository {
    private final JdbcTemplate jdbc;
    public PostgresOrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public IdempotencyClaim createIfAbsent(OrderRecord record, String fingerprint) {
        int inserted = jdbc.update("""
                INSERT INTO trading.order_idempotency(idempotency_key, command_fingerprint, order_id)
                VALUES (?, ?, ?) ON CONFLICT (idempotency_key) DO NOTHING
                """, record.command().idempotencyKey(), fingerprint, record.id().value());
        if (inserted == 0) return existingClaim(record.command().idempotencyKey(), fingerprint, record.id());
        create(record);
        return IdempotencyClaim.CREATED;
    }
    @Override public IdempotencyClaim claimIdempotency(String key, String fingerprint, OrderId orderId) {
        int inserted = jdbc.update("""
                INSERT INTO trading.order_idempotency(idempotency_key, command_fingerprint, order_id)
                VALUES (?, ?, ?) ON CONFLICT (idempotency_key) DO NOTHING
                """, key, fingerprint, orderId.value());
        return inserted == 1 ? IdempotencyClaim.CREATED : existingClaim(key, fingerprint, orderId);
    }
    private IdempotencyClaim existingClaim(String key, String fingerprint, OrderId proposed) {
        var row = jdbc.queryForMap("SELECT command_fingerprint FROM trading.order_idempotency WHERE idempotency_key=?", key);
        return fingerprint.equals(row.get("command_fingerprint")) ? IdempotencyClaim.EXISTING : IdempotencyClaim.CONFLICT;
    }
    @Override public void create(OrderRecord r) {
        jdbc.update("""
                INSERT INTO trading.orders(order_id, idempotency_key, instrument_id, side, quantity, order_type,
                    product, validity, limit_price, trigger_price, disclosed_quantity, variety, state,
                    broker_order_id, failure_category, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, r.id().value(), r.command().idempotencyKey(), r.command().instrumentId().value(),
                r.command().side().name(), r.command().quantity(), r.command().orderType().name(),
                r.command().product().name(), r.command().validity().name(), r.command().limitPrice().orElse(null),
                r.command().triggerPrice().orElse(null), r.command().disclosedQuantity(), r.command().variety().name(),
                r.state().name(), r.brokerOrderId().orElse(null), r.failureCategory().orElse(null),
                Timestamp.from(r.createdAt()), Timestamp.from(r.updatedAt()), r.version());
    }
    @Override public Optional<OrderRecord> find(OrderId id) {
        return jdbc.query("SELECT * FROM trading.orders WHERE order_id=?", this::map, id.value()).stream().findFirst();
    }
    @Override public Optional<OrderRecord> findByIdempotencyKey(String key) {
        return jdbc.query("SELECT o.* FROM trading.orders o JOIN trading.order_idempotency i ON i.order_id=o.order_id WHERE i.idempotency_key=?", this::map, key).stream().findFirst();
    }
    @Override public boolean compareAndSet(OrderRecord expected, OrderRecord next) { return update(expected, next); }
    @Override public boolean attachBrokerOrderId(OrderRecord expected, OrderRecord next) { return update(expected, next); }
    private boolean update(OrderRecord expected, OrderRecord next) {
        return jdbc.update("""
                UPDATE trading.orders SET state=?, broker_order_id=?, failure_category=?, updated_at=?, version=?
                WHERE order_id=? AND version=?
                """, next.state().name(), next.brokerOrderId().orElse(null), next.failureCategory().orElse(null),
                Timestamp.from(next.updatedAt()), next.version(), expected.id().value(), expected.version()) == 1;
    }
    private OrderRecord map(ResultSet rs, int ignored) throws SQLException {
        var command = new PlaceOrder(rs.getString("idempotency_key"), new InstrumentId(rs.getObject("instrument_id", java.util.UUID.class)),
                OrderSide.valueOf(rs.getString("side")), rs.getLong("quantity"), OrderType.valueOf(rs.getString("order_type")),
                OrderProduct.valueOf(rs.getString("product")), OrderValidity.valueOf(rs.getString("validity")),
                optionalDecimal(rs, "limit_price"), optionalDecimal(rs, "trigger_price"), rs.getLong("disclosed_quantity"),
                OrderVariety.valueOf(rs.getString("variety")));
        return new OrderRecord(new OrderId(rs.getObject("order_id", java.util.UUID.class)), command,
                OrderState.valueOf(rs.getString("state")), Optional.ofNullable(rs.getString("broker_order_id")),
                Optional.ofNullable(rs.getString("failure_category")), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
    }
    private static Optional<BigDecimal> optionalDecimal(ResultSet rs, String name) throws SQLException {
        var value = rs.getBigDecimal(name); return Optional.ofNullable(value);
    }
}
