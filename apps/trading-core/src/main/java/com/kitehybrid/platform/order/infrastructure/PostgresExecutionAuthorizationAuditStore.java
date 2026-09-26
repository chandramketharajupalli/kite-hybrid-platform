package com.kitehybrid.platform.order.infrastructure;

import com.kitehybrid.platform.order.application.*;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Authorization commits independently of caller rollback; it never claims broker success. */
public final class PostgresExecutionAuthorizationAuditStore implements ExecutionAuthorizationAuditStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public PostgresExecutionAuthorizationAuditStore(JdbcTemplate jdbc) {
        this.jdbc=jdbc;
        transaction=new TransactionTemplate(new DataSourceTransactionManager(java.util.Objects.requireNonNull(jdbc.getDataSource())));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(5);
    }
    @Override public void record(ExecutionAuthorizationDecision d) {
        transaction.executeWithoutResult(status -> jdbc.update("INSERT INTO trading.execution_authorizations(authorization_id,order_id,allowed,reason,evaluated_at,order_version,policy_version) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),d.orderId().value(),d.allowed(),d.reason().name(),Timestamp.from(d.evaluatedAt()),d.orderVersion(),d.policyVersion()));
    }
}
