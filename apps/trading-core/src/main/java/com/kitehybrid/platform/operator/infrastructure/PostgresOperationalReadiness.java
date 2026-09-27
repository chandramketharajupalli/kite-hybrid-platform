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
    private final String configuredHistorySchema;
    private final String historyTable;
    public PostgresOperationalReadiness(JdbcTemplate jdbc, BooleanSupplier readsAvailable) {
        this(jdbc, readsAvailable, null, "flyway_schema_history");
    }
    public PostgresOperationalReadiness(JdbcTemplate jdbc, BooleanSupplier readsAvailable,
            String configuredHistorySchema, String historyTable) {
        this.jdbc=jdbc; this.readsAvailable=readsAvailable;
        this.configuredHistorySchema=configuredHistorySchema;
        this.historyTable=java.util.Objects.requireNonNull(historyTable);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(java.util.Objects.requireNonNull(jdbc.getDataSource())));
        transaction.setReadOnly(true);
        transaction.setTimeout(5);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private static String quoteIdentifier(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    @Override public Evidence inspect() {
        try {
            return transaction.execute(status -> {
                // With no explicit Flyway schema, use the connection's current schema, not an
                // unqualified table lookup (which could fall through to a different history).
                String schema = configuredHistorySchema == null || configuredHistorySchema.isBlank()
                        ? jdbc.queryForObject("SELECT current_schema()", String.class) : configuredHistorySchema;
                if (schema == null || schema.isBlank() || schema.startsWith("pg_") || schema.equals("information_schema")
                        || !Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                              WHERE n.nspname=? AND c.relname=? AND c.relkind='r')
                            """, Boolean.class, schema, historyTable))) return new Evidence(false, false, false, false);
                String history = quoteIdentifier(schema) + "." + quoteIdentifier(historyTable);
                boolean database = Boolean.TRUE.equals(jdbc.queryForObject("""
                    WITH history AS (SELECT * FROM %s)
                    SELECT EXISTS(SELECT 1 FROM history WHERE version='10' AND success AND type='SQL'
                      AND script='V10__unique_broker_order_identity.sql' AND description='unique broker order identity')
                    AND (SELECT version FROM history WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1)='10'
                    AND (SELECT count(*) FROM history WHERE version IS NOT NULL)=10
                    AND (SELECT count(DISTINCT version) FROM history WHERE success
                      AND version IN ('1','2','3','4','5','6','7','8','9','10'))=10
                    AND NOT EXISTS(SELECT 1 FROM history WHERE NOT success OR (version IS NULL AND type<>'SCHEMA'))
                    AND NOT EXISTS(SELECT 1 FROM trading.orders WHERE broker_order_id IS NOT NULL
                      GROUP BY broker_order_id HAVING count(*)>1)
                    AND NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='trading.orders'::regclass AND NOT convalidated)
                    AND EXISTS(SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
                      WHERE c.oid=to_regclass('trading.orders_broker_order_id_unique')
                      AND i.indrelid='trading.orders'::regclass AND i.indisunique AND i.indisvalid AND i.indisready
                      AND i.indnkeyatts=1 AND i.indnatts=1 AND i.indexprs IS NULL
                      AND pg_get_indexdef(i.indexrelid,1,true)='broker_order_id'
                      AND pg_get_expr(i.indpred,i.indrelid)='(broker_order_id IS NOT NULL)')
                    """.formatted(history), Boolean.class));
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
