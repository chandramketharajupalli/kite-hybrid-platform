package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.kitehybrid.platform.broker.application.BrokerReadException.Category.CONFIGURATION;

/** SELECT only, caller-owned isolated connection. No role/transaction changes or credential loading. */
final class KiteEquityReadIntegrity {
    private final Connection connection;
    private static final Set<String> REQUIRED = Set.of("kite_access_tokens", "kite_login_attempts", "orders",
            "order_idempotency", "risk_decisions", "execution_authorizations", "reconciliation_decisions", "reconciliation_trades");
    KiteEquityReadIntegrity(Connection connection) { this.connection = Objects.requireNonNull(connection); }
    static final class Fingerprint {
        private final Map<String,String> tables;
        private Fingerprint(Map<String,String> tables) { this.tables = Map.copyOf(tables); }
        boolean matches(Fingerprint other) { return other != null && tables.equals(other.tables); }
        @Override public String toString() { return "EquityReadIntegrity[REDACTED]"; }
    }
    Fingerprint capture() {
        try {
            // Autocommit gives each observation a fresh READ COMMITTED snapshot, not a stale transaction view.
            if (!connection.getAutoCommit() || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED
                    || !"on".equals(scalar("SHOW transaction_read_only"))) throw denied();
            if (!"0".equals(scalar("SELECT count(*) FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls)"))) throw denied();
            if (!"true".equals(scalar("SELECT pg_has_role(current_user,'pg_read_all_stats','USAGE')::text"))) throw denied();
            if (!"0".equals(scalar("SELECT count(*) FROM pg_roles WHERE rolname<>current_user AND rolname<>'pg_read_all_stats' AND pg_has_role(current_user,oid,'MEMBER')"))) throw denied();
            if (!"0".equals(scalar("SELECT count(*) FROM pg_database WHERE datname=current_database() AND has_database_privilege(oid,'CREATE,TEMP')"))) throw denied();
            if (!"0".equals(scalar("SELECT count(*) FROM pg_namespace WHERE left(nspname,3)<>'pg_' AND nspname<>'information_schema' AND has_schema_privilege(oid,'CREATE')"))) throw denied();
            // Conservative: no executable application-defined function, including SECURITY DEFINER.
            if (!"0".equals(scalar("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE left(n.nspname,3)<>'pg_' AND n.nspname<>'information_schema' AND has_function_privilege(p.oid,'EXECUTE')"))) throw denied();
            if (!"0".equals(scalar("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND backend_type='client backend' AND pid<>pg_backend_pid()"))) throw denied();
            if (!"0".equals(scalar("SELECT count(*) FROM pg_prepared_xacts WHERE database=current_database()"))) throw denied();
            if (!"0".equals(scalar("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='trading' AND c.relkind IN ('r','p') AND (has_table_privilege(c.oid,'INSERT,UPDATE,DELETE,TRUNCATE,TRIGGER') OR NOT has_table_privilege(c.oid,'SELECT'))"))) throw denied();
            if (!"0".equals(scalar("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='trading' AND CASE WHEN c.relkind='S' THEN has_sequence_privilege(c.oid,'USAGE,UPDATE') ELSE false END"))) throw denied();
            if (!"0".equals(scalar("SELECT count(*) FROM pg_namespace WHERE nspname='trading' AND has_schema_privilege(oid,'CREATE')"))) throw denied();
            if (!"0".equals(scalar("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='trading' AND c.relrowsecurity"))) throw denied();
            var names = new TreeSet<String>();
            try (var statement = statement(); var rows = statement.executeQuery("SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='trading' AND c.relkind IN ('r','p')")) {
                while (rows.next()) { var name=rows.getString(1); if (!name.matches("[a-z_]+")) throw denied(); names.add(name); }
            }
            if (!names.containsAll(REQUIRED) || !"1".equals(scalar("SELECT count(*) FROM trading.kite_access_tokens"))
                    || !"0".equals(scalar("SELECT count(*) FROM trading.execution_authorizations WHERE allowed"))) throw denied();
            var result = new TreeMap<String,String>();
            String schema=scalar("SELECT coalesce(string_agg(c.relname||':'||a.attnum||':'||a.attname||':'||a.atttypid||':'||a.atttypmod||':'||a.attnotnull,',' ORDER BY c.relname,a.attnum),'') FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='trading' AND c.relkind IN ('r','p') AND a.attnum>0 AND NOT a.attisdropped");
            result.put("@schema",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(schema.getBytes(StandardCharsets.UTF_8))));
            for (var name : names) {
                var hash = MessageDigest.getInstance("SHA-256"); long count=0;
                try (var statement = statement(); var rows = statement.executeQuery("SELECT row_to_json(t)::text FROM trading.\""+name+"\" t ORDER BY row_to_json(t)::text")) {
                    while (rows.next()) {
                        if (++count > 100000) throw denied();
                        byte[] bytes=rows.getString(1).getBytes(StandardCharsets.UTF_8);
                        if (bytes.length > 1024*1024) throw denied();
                        hash.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); hash.update(bytes);
                        Arrays.fill(bytes,(byte)0);
                    }
                }
                result.put(name,count+":"+HexFormat.of().formatHex(hash.digest()));
            }
            if (!"0".equals(scalar("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND backend_type='client backend' AND pid<>pg_backend_pid()"))) throw denied();
            return new Fingerprint(result);
        } catch (Exception failure) { throw denied(); }
    }
    private Statement statement() throws SQLException {
        var statement=connection.createStatement(); statement.setQueryTimeout(10); return statement;
    }
    private String scalar(String sql) throws SQLException {
        try (var statement=statement(); var rows=statement.executeQuery(sql)) {
            if (!rows.next()) throw denied(); return rows.getString(1);
        }
    }
    private static BrokerReadException denied() { return new BrokerReadException(CONFIGURATION); }
}
