package com.kitehybrid.platform.operator.infrastructure;

import com.kitehybrid.platform.operator.application.OperationalReadiness;
import java.util.function.BooleanSupplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** SELECT-only local probe. V10 and reconciliation evidence must be available at dispatch too. */
public final class PostgresOperationalReadiness implements OperationalReadiness {
    private final JdbcTemplate jdbc;
    private final BooleanSupplier readsAvailable;
    private final TransactionTemplate transaction;
    public PostgresOperationalReadiness(JdbcTemplate jdbc, BooleanSupplier readsAvailable) {
        this.jdbc=jdbc; this.readsAvailable=readsAvailable;
        transaction = new TransactionTemplate(new DataSourceTransactionManager(java.util.Objects.requireNonNull(jdbc.getDataSource())));
        transaction.setReadOnly(true);
        transaction.setTimeout(5);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Override public Evidence inspect() {
        try {
            return transaction.execute(status -> {
                boolean database = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM public.flyway_schema_history WHERE version='10' AND success)
                    AND (SELECT version FROM public.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1)='10'
                    AND (SELECT count(DISTINCT version) FROM public.flyway_schema_history WHERE success
                      AND version IN ('1','2','3','4','5','6','7','8','9','10'))=10
                    AND NOT EXISTS(SELECT 1 FROM public.flyway_schema_history WHERE NOT success)
                    AND EXISTS(SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
                      WHERE c.oid=to_regclass('trading.orders_broker_order_id_unique')
                      AND i.indrelid='trading.orders'::regclass AND i.indisunique AND i.indisvalid AND i.indisready
                      AND i.indnkeyatts=1 AND i.indnatts=1 AND i.indexprs IS NULL
                      AND pg_get_indexdef(i.indexrelid,1,true)='broker_order_id'
                      AND pg_get_expr(i.indpred,i.indrelid)='(broker_order_id IS NOT NULL)')
                    """, Boolean.class));
                // Reading both tables proves their local accessibility. No row data leaves this boundary.
                jdbc.queryForObject("SELECT count(*) FROM (SELECT 1 FROM trading.reconciliation_trades LIMIT 1) t", Long.class);
                boolean clear = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT NOT EXISTS(SELECT 1 FROM trading.reconciliation_decisions
                      WHERE outcome IN ('AMBIGUOUS','CONFLICT','BROKER_ORDER_MISSING','BROKER_STATE_UNAVAILABLE'))
                    """, Boolean.class));
                return new Evidence(database, true, clear, readsAvailable.getAsBoolean());
            });
        } catch (RuntimeException unavailable) { return new Evidence(false, false, false, false); }
    }
}
