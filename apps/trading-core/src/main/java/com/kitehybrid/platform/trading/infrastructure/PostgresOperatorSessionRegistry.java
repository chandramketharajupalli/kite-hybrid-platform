package com.kitehybrid.platform.trading.infrastructure;

import com.kitehybrid.platform.trading.application.OperatorSessionRegistryPort;
import com.kitehybrid.platform.trading.domain.OperatorPrincipal.Permission;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import static com.kitehybrid.platform.trading.application.OperatorSessionRegistryPort.*;

/** Inactive, bounded JDBC registry. No Spring registration, credential loading or broker dependency. */
public final class PostgresOperatorSessionRegistry implements OperatorSessionRegistryPort {
    private final DataSource source;
    private final Clock clock;
    public PostgresOperatorSessionRegistry(DataSource source, Clock clock) {
        this.source = Objects.requireNonNull(source); this.clock = Objects.requireNonNull(clock);
    }
    @FunctionalInterface private interface Work<T> { T run(Connection c) throws SQLException; }
    private <T> T transaction(boolean readOnly, Work<T> work) {
        try (Connection c = source.getConnection()) {
            c.setReadOnly(readOnly); c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED); c.setAutoCommit(false);
            try (var timeout = c.createStatement()) {
                timeout.setQueryTimeout(5); timeout.execute("SET LOCAL statement_timeout = '5s'");
                timeout.execute("SET LOCAL lock_timeout = '3s'");
            }
            try { T value = work.run(c); c.commit(); return value; }
            catch (SQLException | RuntimeException failure) { c.rollback(); throw failure; }
        } catch (SQLException | RuntimeException failure) {
            // No SQL, connection details, session identifiers or underlying message escape.
            throw new IllegalStateException("Operator registry unavailable");
        }
    }
    private static PreparedStatement statement(Connection c, String sql, Object... values) throws SQLException {
        PreparedStatement p = c.prepareStatement(sql); p.setQueryTimeout(5);
        for (int i = 0; i < values.length; i++) p.setObject(i + 1,
                values[i] instanceof Instant instant ? Timestamp.from(instant) : values[i]);
        return p;
    }
    private static int update(Connection c, String sql, Object... values) throws SQLException {
        try (var p = statement(c, sql, values)) { return p.executeUpdate(); }
    }
    private static Set<String> array(ResultSet r, String column) throws SQLException {
        var value = r.getArray(column);
        if (value == null) throw new IllegalArgumentException("Missing grant");
        try { String[] entries = (String[]) value.getArray(); Set<String> set = Set.of(entries);
            set.forEach(OperatorSessionRegistryPort::text); return set;
        } finally { value.free(); }
    }
    private static Session row(ResultSet r) throws SQLException {
        Set<Permission> permissions = new HashSet<>();
        for (String name : array(r, "permissions")) permissions.add(Permission.valueOf(name));
        return new Session(r.getString("digest"), new Event(r.getString("issuer"), r.getString("subject"),
                r.getString("audience"), r.getObject("authentication_event", UUID.class),
                r.getTimestamp("authenticated_at").toInstant(), Trust.VERIFIED_OPERATOR_ASSERTION),
                r.getTimestamp("absolute_expires_at").toInstant(), r.getTimestamp("last_activity_at").toInstant(),
                Duration.ofSeconds(r.getLong("inactivity_seconds")), permissions, array(r, "accounts"),
                r.getString("policy_version"), r.getBoolean("revoked"), r.getLong("revision"));
    }
    private static Optional<Session> select(Connection c, String digest, boolean lock) throws SQLException {
        try (var p = statement(c, "SELECT * FROM trading.operator_sessions WHERE digest=? LIMIT 1"
                + (lock ? " FOR UPDATE" : ""), OperatorSessionRegistryPort.digest(digest)); var r = p.executeQuery()) {
            return r.next() ? Optional.of(row(r)) : Optional.empty();
        }
    }
    @Override public Optional<Observation> find(String digest) {
        return transaction(true, c -> select(c, digest, false).map(s -> new Observation(s, clock.instant())));
    }
    private static void ownerLock(Connection c, Event e) throws SQLException {
        update(c, "INSERT INTO trading.operator_session_owners(issuer,subject) VALUES (?,?) ON CONFLICT DO NOTHING",
                e.issuer(), e.subject());
        try (var p = statement(c, "SELECT issuer FROM trading.operator_session_owners WHERE issuer=? AND subject=? FOR UPDATE",
                e.issuer(), e.subject()); var r = p.executeQuery()) {
            if (!r.next()) throw new IllegalStateException("Missing owner");
        }
    }
    private static void insert(Connection c, Session s) throws SQLException {
        var e = s.event();
        try (var p = statement(c, """
                INSERT INTO trading.operator_sessions(digest,issuer,subject,audience,authentication_event,authenticated_at,
                  absolute_expires_at,last_activity_at,inactivity_seconds,permissions,accounts,policy_version,revoked,revision)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, s.digest(), e.issuer(), e.subject(), e.audience(), e.authenticationEvent(), e.authenticatedAt(),
                s.absoluteExpiresAt(), s.lastActivityAt(), s.inactivity().getSeconds(), null, null,
                s.policyVersion(), s.revoked(), s.revision())) {
            var permissions = c.createArrayOf("text", s.permissions().stream().map(Enum::name).sorted().toArray(String[]::new));
            var accounts = c.createArrayOf("text", s.accounts().stream().sorted().toArray(String[]::new));
            try { p.setArray(10, permissions); p.setArray(11, accounts); p.executeUpdate(); }
            finally { permissions.free(); accounts.free(); }
        }
    }
    private static void audit(Connection c, String action, Instant now) throws SQLException {
        update(c, "INSERT INTO trading.operator_session_audit(event_id,action,observed_at) VALUES (?,?,?)",
                UUID.randomUUID(), action, now);
    }
    @Override public boolean establish(Session s) {
        return transaction(false, c -> {
            if (s.revoked() || s.revision() != 1 || !s.activeAt(clock.instant())) return false;
            ownerLock(c, s.event());
            try (var p = statement(c, "SELECT 1 FROM trading.operator_sessions WHERE issuer=? AND subject=? AND authentication_event=? LIMIT 1",
                    s.event().issuer(), s.event().subject(), s.event().authenticationEvent()); var r = p.executeQuery()) {
                if (r.next()) return false;
            }
            update(c, "UPDATE trading.operator_sessions SET revoked=true,revision=revision+1 WHERE issuer=? AND subject=? AND NOT revoked",
                    s.event().issuer(), s.event().subject());
            insert(c, s); audit(c, "ESTABLISH", clock.instant()); return true;
        });
    }
    private static Optional<Session> locked(Connection c, String digest) throws SQLException {
        var found = select(c, digest, false);
        if (found.isEmpty()) return found;
        ownerLock(c, found.get().event()); return select(c, digest, true);
    }
    @Override public boolean rotate(String digest, long revision, String nextDigest, Instant now) {
        OperatorSessionRegistryPort.digest(nextDigest);
        return transaction(false, c -> {
            var found = locked(c, digest);
            if (found.isEmpty() || digest.equals(nextDigest)) return false;
            var s = found.get();
            if (s.revision() != revision || !s.activeAt(now) || !s.activeAt(clock.instant()) || now.isAfter(clock.instant())) return false;
            update(c, "UPDATE trading.operator_sessions SET revoked=true,revision=revision+1 WHERE digest=?", digest);
            insert(c, s.changed(nextDigest, now, false)); audit(c, "ROTATE", now); return true;
        });
    }
    @Override public boolean touch(String digest, long revision, Instant now) {
        return transaction(false, c -> {
            var found = locked(c, digest);
            if (found.isEmpty() || found.get().revision() != revision || !found.get().activeAt(now) || !found.get().activeAt(clock.instant())
                    || now.isAfter(clock.instant())) return false;
            update(c, "UPDATE trading.operator_sessions SET last_activity_at=?,revision=revision+1 WHERE digest=?", now, digest);
            audit(c, "ACTIVITY", now); return true;
        });
    }
    @Override public boolean revoke(String digest, Instant now) {
        return transaction(false, c -> {
            var found = locked(c, digest);
            if (found.isEmpty() || now.isAfter(clock.instant()) || now.isBefore(found.get().event().authenticatedAt())) return false;
            if (!found.get().revoked()) {
                update(c, "UPDATE trading.operator_sessions SET revoked=true,revision=revision+1 WHERE digest=?", digest);
                audit(c, "REVOKE", now);
            }
            return true;
        });
    }
}
