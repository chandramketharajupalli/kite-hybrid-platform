package com.kitehybrid.synthetic;

import java.sql.*;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;

/** Test-only adapter. No JDBC, schema DDL or connection configuration in runtime JAR. */
final class DisposableLedger implements SyntheticOwner.Ledger {
    enum Fault { NONE, BEFORE_COMMIT, AFTER_COMMIT_BEFORE_ACK }
    private final String url, marker;
    private final Fault fault;
    static PostgreSQLContainer<?> container() {
        return new PostgreSQLContainer<>("postgres:17.6").withDatabaseName("synthetic_owner_only")
                .withUsername("synthetic_fixture").withPassword("synthetic_fixture")
                .withCommand("postgres", "-c", "fsync=on", "-c", "full_page_writes=on", "-c", "synchronous_commit=on");
    }
    static DisposableLedger initialize(PostgreSQLContainer<?> container) throws Exception {
        String marker = UUID.randomUUID().toString();
        var ledger = new DisposableLedger(container.getMappedPort(5432), marker, Fault.NONE);
        try (var c = ledger.open(); var s = c.createStatement()) {
            s.execute("CREATE TABLE fixture_identity (id text PRIMARY KEY)");
            try (var p = c.prepareStatement("INSERT INTO fixture_identity VALUES (?)")) { p.setString(1, marker); p.executeUpdate(); }
            s.execute("CREATE TABLE attempts (id uuid PRIMARY KEY, digest text NOT NULL, state text NOT NULL CHECK(state IN ('RESERVED','CONSUMED')), outcome text)");
        }
        return ledger;
    }
    DisposableLedger(int port, String marker, Fault fault) {
        if (port < 1 || port > 65535 || marker == null) throw SyntheticOwner.denied();
        UUID.fromString(marker);
        url = "jdbc:postgresql://127.0.0.1:" + port + "/synthetic_owner_only?connectTimeout=2&socketTimeout=3";
        this.marker = marker; this.fault = fault;
    }
    String marker() { return marker; }
    private Connection open() throws SQLException { return DriverManager.getConnection(url, "synthetic_fixture", "synthetic_fixture"); }
    private void validate(Connection c) throws SQLException {
        try (var s = c.createStatement()) {
            s.setQueryTimeout(3);
            for (String setting : new String[]{"fsync", "full_page_writes", "synchronous_commit"}) {
                try (var r = s.executeQuery("SHOW " + setting)) { if (!r.next() || !"on".equals(r.getString(1))) throw new SQLException(); }
            }
            try (var r = s.executeQuery("SELECT id FROM fixture_identity")) {
                if (!r.next() || !marker.equals(r.getString(1)) || r.next()) throw new SQLException();
            }
        }
    }
    @Override public boolean consume(SyntheticOwner.Request request) {
        // Unique reservation has its own durable commit. Even a rolled-back consume is spent.
        try (var c = open()) {
            validate(c);
            try (var p = c.prepareStatement("INSERT INTO attempts VALUES (?,?,'RESERVED',NULL) ON CONFLICT DO NOTHING")) {
                p.setQueryTimeout(3); p.setObject(1, request.id()); p.setString(2, request.fingerprint());
                if (p.executeUpdate() != 1) return false;
            }
            c.setAutoCommit(false);
            try (var p = c.prepareStatement("UPDATE attempts SET state='CONSUMED' WHERE id=? AND digest=? AND state='RESERVED'")) {
                p.setQueryTimeout(3); p.setObject(1, request.id()); p.setString(2, request.fingerprint());
                if (p.executeUpdate() != 1) { c.rollback(); return false; }
            }
            if (fault == Fault.BEFORE_COMMIT) { c.rollback(); return false; }
            c.commit();
            return fault != Fault.AFTER_COMMIT_BEFORE_ACK;
        } catch (Exception ignored) { return false; } // Unknown reservation/commit: no grant, never retry.
    }
    @Override public void terminal(UUID id, SyntheticOwner.Outcome outcome) {
        try (var c = open()) {
            validate(c);
            try (var p = c.prepareStatement("UPDATE attempts SET outcome=? WHERE id=? AND state='CONSUMED' AND outcome IS NULL")) {
                p.setQueryTimeout(3); p.setString(1, outcome.name()); p.setObject(2, id); p.executeUpdate();
            }
        } catch (Exception ignored) { throw SyntheticOwner.denied(); }
    }
    String state(UUID id) throws Exception {
        try (var c = open()) {
            validate(c);
            try (var p = c.prepareStatement("SELECT state FROM attempts WHERE id=?")) {
                p.setObject(1,id); try (var r=p.executeQuery()) { return r.next()?r.getString(1):"ABSENT"; }
            }
        }
    }
    String digest(UUID id) throws Exception {
        try(var c=open();var p=c.prepareStatement("SELECT digest FROM attempts WHERE id=?")) {
            p.setObject(1,id);try(var r=p.executeQuery()){return r.next()?r.getString(1):"ABSENT";}
        }
    }
    void verifyDurabilitySettings() throws Exception { try (var c = open()) { validate(c); } }
}
