package com.kitehybrid.platform.order.infrastructure;
import com.kitehybrid.platform.order.application.*; import java.sql.Timestamp; import java.util.UUID; import org.springframework.jdbc.core.JdbcTemplate;
/** Durable bounded authorization audit; no credentials or raw broker payloads. */
public final class PostgresExecutionAuthorizationAuditStore implements ExecutionAuthorizationAuditStore {
 private final JdbcTemplate jdbc; public PostgresExecutionAuthorizationAuditStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Override public void record(ExecutionAuthorizationDecision d){jdbc.update("INSERT INTO trading.execution_authorizations(authorization_id,order_id,allowed,reason,evaluated_at,order_version,policy_version) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),d.orderId().value(),d.allowed(),d.reason().name(),Timestamp.from(d.evaluatedAt()),d.orderVersion(),d.policyVersion());}
}
