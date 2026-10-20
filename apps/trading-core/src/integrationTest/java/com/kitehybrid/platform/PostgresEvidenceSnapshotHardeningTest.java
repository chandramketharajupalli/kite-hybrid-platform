package com.kitehybrid.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.shared.domain.Identifiers.StrategyId;
import com.kitehybrid.platform.strategy.domain.StrategyDefinition;
import com.kitehybrid.platform.trading.domain.EvidenceAvailability;
import com.kitehybrid.platform.trading.domain.StockStrategyQualificationEvidence;
import com.kitehybrid.platform.trading.infrastructure.EvidenceSnapshotFingerprint;
import com.kitehybrid.platform.trading.infrastructure.EvidenceSnapshotReadPolicy;
import com.kitehybrid.platform.trading.infrastructure.PostgresEvidenceSnapshotQueryAdapter;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic, explicitly authorized disposable PostgreSQL only; never discovers host credentials. */
class PostgresEvidenceSnapshotHardeningTest {
    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
    private static final Instant START = NOW.minusSeconds(7200), END = NOW.minusSeconds(3600);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> TABLES = List.of("instrument", "strategy", "qualification", "account_risk");
    private static DriverManagerDataSource source;
    private static JdbcTemplate db;
    private static Flyway flyway;

    @BeforeAll static void migrateDisposableFixture() {
        assertEquals("true", System.getProperty("evidence.integration.authorized"));
        String url = System.getProperty("evidence.integration.url", "");
        assertTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/evidence_fixture_[a-z0-9]+"));
        source = new DriverManagerDataSource(url, "evidence_fixture_admin", "");
        db = new JdbcTemplate(source);
        assertEquals(0, db.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema')", Integer.class));
        flyway = Flyway.configure().dataSource(source).defaultSchema("public").schemas("public")
                .locations("classpath:db/migration", "classpath:db/historical", "classpath:db/evidence")
                .cleanDisabled(true).load();
        assertEquals(13, flyway.migrate().migrationsExecuted);
    }
    private static ObjectNode provenance(Instant observed, Instant expires) {
        return JSON.createObjectNode().put("sourceIdentity", "fixture").put("fingerprint", "a".repeat(64))
                .put("observedAt", observed.toString()).put("expiresAt", expires.toString())
                .put("origin", "SYNTHETIC_FIXTURE").put("retention", "SYNTHETIC_ONLY");
    }
    private static final class Row {
        final String kind;
        final LinkedHashMap<String, Object> columns = new LinkedHashMap<>();
        final ObjectNode payload = JSON.createObjectNode();
        boolean nullPayload;
        String rawPayload;
        Row(String kind, Instant observed) {
            assertTrue(TABLES.contains(kind)); this.kind = kind;
            columns.put("evidence_id", UUID.randomUUID()); columns.put("schema_version", 1);
            columns.put("revision", 1L); columns.put("source_identity", "fixture");
            columns.put("authorization_reference", "fixture-grant"); columns.put("source_fingerprint", "a".repeat(64));
            columns.put("payload_sha256", ""); columns.put("origin", "SYNTHETIC_FIXTURE");
            columns.put("retention", "SYNTHETIC_ONLY");
            columns.put("observed_at", Timestamp.from(observed)); columns.put("expires_at", Timestamp.from(NOW.plusSeconds(600)));
            payload.set("provenance", provenance(observed, NOW.plusSeconds(600)));
        }
        Row field(String column, String key, Object value) {
            columns.put(column, value);
            if (value instanceof Timestamp t) payload.put(key, t.toInstant().toString());
            else payload.set(key, JSON.valueToTree(value));
            return this;
        }
        Row observed(Instant time) {
            columns.put("observed_at", Timestamp.from(time));
            ((ObjectNode) payload.get("provenance")).put("observedAt", time.toString()); return this;
        }
        Row expires(Instant time) {
            columns.put("expires_at", Timestamp.from(time));
            ((ObjectNode) payload.get("provenance")).put("expiresAt", time.toString()); return this;
        }
        String table() { return "trading.evidence_" + kind + "_snapshots"; }
        void insert(Connection c) throws SQLException {
            String text = rawPayload == null ? payload.toString() : rawPayload;
            if (Objects.equals(columns.get("payload_sha256"), "")) columns.put("payload_sha256", EvidenceSnapshotFingerprint.sha256(text));
            String sql = "INSERT INTO " + table() + " (" + String.join(",", columns.keySet())
                    + ",payload) VALUES (" + String.join(",", columns.keySet().stream().map(k -> Set.of("observed_at", "expires_at", "period_start", "period_end").contains(k) ? "?::timestamptz" : Set.of("exposure", "exposure_limit", "transaction_costs", "slippage_basis_points").contains(k) ? "?::numeric" : "?").toList()) + ",?::jsonb)";
            try (PreparedStatement s = c.prepareStatement(sql)) {
                s.setQueryTimeout(5); int i = 1;
                for (Object value : columns.values()) s.setObject(i++, value);
                s.setString(i, nullPayload ? null : text); s.executeUpdate();
            }
        }
        void persist() throws SQLException { try (Connection c = source.getConnection()) { insert(c); } }
    }
    private static final class Fixture {
        final InstrumentId id = new InstrumentId(UUID.randomUUID());
        final StrategyDefinition definition = new StrategyDefinition(new StrategyId("s" + UUID.randomUUID().toString().replace("-", "")), "v1");
        final String account = "synthetic-" + UUID.randomUUID();
        final Row instrument = new Row("instrument", START.minusSeconds(60));
        final Row strategy = new Row("strategy", START.minusSeconds(60));
        Fixture() {
            instrument.field("instrument_id", "instrumentId", id.value()).field("snapshot_version", "snapshotVersion", 1L);
            instrument.payload.put("exchange", "FIXTURE").put("symbol", "SYNTHETIC").putNull("token");
            strategy.field("strategy_id", "strategyId", definition.id().value()).field("strategy_version", "strategyVersion", "v1");
            strategy.payload.put("timeframe", "PT5M");
            strategy.payload.putObject("parameters").put("threshold", "0.70").put("cost", "1.2500");
            strategy.payload.putArray("entryConditions").add("synthetic-entry");
            strategy.payload.putArray("exitConditions").add("synthetic-exit");
            strategy.payload.putArray("universe").add(id.value().toString());
        }
        void references() throws SQLException { instrument.persist(); strategy.persist(); }
        Row qualification() {
            Row q = new Row("qualification", NOW);
            q.field("instrument_id", "instrumentId", id.value());
            q.columns.put("instrument_evidence_id", instrument.columns.get("evidence_id"));
            q.field("strategy_id", "strategyId", definition.id().value()).field("strategy_version", "strategyVersion", "v1");
            q.columns.put("strategy_evidence_id", strategy.columns.get("evidence_id"));
            q.field("timeframe", "timeframe", "PT5M").field("period_start", "periodStart", Timestamp.from(START))
                    .field("period_end", "periodEnd", Timestamp.from(END));
            q.columns.put("policy_id", "fixture-policy"); q.columns.put("policy_version", "v1");
            q.field("evaluated_trades", "evaluatedTrades", 10L).field("wins", "wins", 7L).field("losses", "losses", 3L)
                    .field("transaction_costs", "transactionCosts", new BigDecimal("1.2500"))
                    .field("slippage_basis_points", "slippageBasisPoints", new BigDecimal("2.125"));
            q.payload.put("costModel", "synthetic-cost-model");
            q.payload.set("dataset", provenance(START.minusSeconds(60), NOW.plusSeconds(3600)));
            q.payload.putObject("policy").put("policyId", "fixture-policy").put("version", "v1")
                    .put("fingerprint", "b".repeat(64)).put("minimumTrades", 10).put("minimumWinPercentage", 70)
                    .put("remainingChecksPassed", true);
            return q;
        }
        Row account() {
            Row a = new Row("account_risk", NOW);
            a.field("account_scope", "accountScope", account).field("currency", "currency", "INR")
                    .field("exposure", "exposure", new BigDecimal("12.50"))
                    .field("exposure_limit", "exposureLimit", new BigDecimal("100"));
            a.payload.putObject("additionalLimits").put("daily", new BigDecimal("25.25")); return a;
        }
        PostgresEvidenceSnapshotQueryAdapter reader(String grant, String origin) {
            return new PostgresEvidenceSnapshotQueryAdapter(db,
                    new EvidenceSnapshotReadPolicy(origin, grant, NOW.minusSeconds(86400), NOW.plusSeconds(3600),
                            Duration.ofDays(1), Set.of(id), Set.of(definition), Set.of(account)), Clock.fixed(NOW, ZoneOffset.UTC));
        }
        PostgresEvidenceSnapshotQueryAdapter reader() { return reader("fixture-grant", "fixture"); }
        EvidenceAvailability<StockStrategyQualificationEvidence> read() { return read(reader()); }
        EvidenceAvailability<StockStrategyQualificationEvidence> read(PostgresEvidenceSnapshotQueryAdapter r) {
            return r.qualification(id, definition, Duration.ofMinutes(5), START, END, "fixture-policy", "v1");
        }
    }
    @FunctionalInterface private interface SqlAction { void run(Connection c) throws SQLException; }
    private static void rejected(String label, String state, SqlAction action) throws SQLException {
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false); Savepoint save = c.setSavepoint();
            SQLException failure = assertThrows(SQLException.class, () -> action.run(c), label);
            assertEquals(state, failure.getSQLState(), label);
            if (failure instanceof PSQLException pg && pg.getServerErrorMessage() != null) {
                var d = pg.getServerErrorMessage();
                System.out.printf("CONSTRAINT_CASE %s SQLSTATE=%s table=%s constraint=%s column=%s%n",
                        label, state, d.getTable(), d.getConstraint(), d.getColumn());
            }
            c.rollback(save);
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT 1")) {
                assertTrue(rs.next()); assertEquals(1, rs.getInt(1));
            }
            c.rollback();
        }
    }
    private static void reject(Row row, String label, Consumer<Row> change) throws SQLException {
        change.accept(row); rejected(row.kind + ":" + label, "23514", row::insert);
    }
    private static Row fresh(String kind) throws SQLException {
        Fixture f = new Fixture();
        if (kind.equals("qualification")) { f.references(); return f.qualification(); }
        return switch (kind) { case "instrument" -> f.instrument; case "strategy" -> f.strategy; default -> f.account(); };
    }
    private static void commonConstraints(String kind) throws SQLException {
        Row baseline = fresh(kind); baseline.persist();
        Row noPayload = fresh(kind); noPayload.nullPayload = true;
        rejected(kind + ":null:payload", "23502", noPayload::insert);
        reject(fresh(kind), "payload-object", r -> { r.rawPayload = "[]"; r.columns.put("payload_sha256", "0".repeat(64)); });
        for (String column : baseline.columns.keySet()) {
            Row r = fresh(kind); r.columns.put(column, null);
            // Qualification's BEFORE trigger intercepts null references/authorization/chronology.
            boolean intercepted = kind.equals("qualification") && Set.of("authorization_reference", "expires_at", "instrument_evidence_id", "strategy_evidence_id", "period_start", "timeframe", "instrument_id").contains(column);
            rejected(kind + ":null:" + column + (intercepted ? ":trigger-intercepted" : ""), intercepted ? "23514" : "23502", r::insert);
        }
        reject(fresh(kind), "blank-source", r -> { r.columns.put("source_identity", " "); ((ObjectNode) r.payload.get("provenance")).put("sourceIdentity", " ");
            if (r.kind.equals("qualification")) ((ObjectNode) r.payload.get("dataset")).put("sourceIdentity", " "); });
        reject(fresh(kind), "blank-authorization" + (kind.equals("qualification") ? ":trigger-intercepted" : ""), r -> r.columns.put("authorization_reference", " "));
        reject(fresh(kind), "source-fingerprint-format", r -> { r.columns.put("source_fingerprint", "bad"); ((ObjectNode) r.payload.get("provenance")).put("fingerprint", "bad"); });
        for (String[] time : List.of(new String[]{"observed_at", "observedAt"}, new String[]{"expires_at", "expiresAt"})) {
            reject(fresh(kind), "timestamp-mirror:" + time[0], r -> ((ObjectNode) r.payload.get("provenance")).put(time[1], NOW.minusSeconds(86400).toString()));
            reject(fresh(kind), "timestamp-finite:overlapping-chronology:" + time[0] + (kind.equals("qualification") && time[0].equals("expires_at") ? ":trigger-intercepted" : ""), r -> {
                r.columns.put(time[0], "infinity"); ((ObjectNode) r.payload.get("provenance")).put(time[1], "infinity"); });
        }
        reject(fresh(kind), "payload-size", r -> {
            r.payload.put("oversizedSyntheticField", "x".repeat(65536));
            // Rejected fixture only: reach PostgreSQL's size CHECK without the Java fingerprint size guard.
            r.columns.put("payload_sha256", "0".repeat(64));
        });
        reject(fresh(kind), "schema-version", r -> r.columns.put("schema_version", 2));
        reject(fresh(kind), "revision", r -> r.columns.put("revision", 0L));
        reject(fresh(kind), "zero-evidence-id", r -> r.columns.put("evidence_id", new UUID(0, 0)));
        reject(fresh(kind), "payload-digest-format", r -> r.columns.put("payload_sha256", "bad"));
        for (String[] pair : List.of(new String[]{"source_identity", "sourceIdentity"}, new String[]{"source_fingerprint", "fingerprint"},
                new String[]{"origin", "origin"}, new String[]{"retention", "retention"})) {
            reject(fresh(kind), "payload-column:" + pair[0], r -> ((ObjectNode) r.payload.get("provenance")).put(pair[1], "mismatch"));
        }
        reject(fresh(kind), "synthetic-origin", r -> { r.columns.put("origin", "LOCAL_UNVERIFIED"); ((ObjectNode) r.payload.get("provenance")).put("origin", "LOCAL_UNVERIFIED"); });
        reject(fresh(kind), "synthetic-retention", r -> { r.columns.put("retention", "NOT_AUTHORIZED"); ((ObjectNode) r.payload.get("provenance")).put("retention", "NOT_AUTHORIZED"); });
        reject(fresh(kind), "expiry-order", r -> r.expires(((Timestamp) r.columns.get("observed_at")).toInstant()));
        List<String> keys = new ArrayList<>(); baseline.payload.fieldNames().forEachRemaining(keys::add);
        for (String key : keys) {
            reject(fresh(kind), "required-payload:" + key, r -> r.payload.remove(key));
            if (!key.equals("token") && !key.equals("policy")) reject(fresh(kind), "null-payload:" + key, r -> r.payload.putNull(key));
        }
    }

    @Test void migrationHistoryAndConstraintInventoryMatchV13() {
        flyway.validate(); assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("13", flyway.info().current().getVersion().toString());
        assertNull(db.queryForObject("SELECT to_regclass('trading.flyway_schema_history')", String.class));
        assertEquals(13, db.queryForObject("SELECT count(*) FROM public.flyway_schema_history WHERE success", Integer.class));
        for (String kind : TABLES) {
            var constraints = db.queryForList("SELECT conname, contype, pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE conrelid=cast(? AS regclass) ORDER BY conname", "trading.evidence_" + kind + "_snapshots");
            assertFalse(constraints.isEmpty()); constraints.forEach(c -> System.out.println("CONSTRAINT_CATALOG " + c));
        }
        // Composite reference UNIQUE keys include the primary key and cannot be violated independently.
    }
    @Test void persistedQualificationRoundTripPreservesExactEvidence() throws Exception {
        Fixture f = new Fixture(); f.references(); Row row = f.qualification(); row.persist();
        var result = f.read(); assertEquals(EvidenceAvailability.Status.AVAILABLE, result.status());
        var q = result.evidence().orElseThrow(); assertEquals(f.id, q.instrumentId()); assertEquals(f.definition, q.strategy());
        assertEquals(Duration.ofMinutes(5), q.timeframe()); assertEquals(START, q.periodStart()); assertEquals(END, q.periodEnd());
        assertEquals(10, q.evaluatedTrades()); assertEquals(7, q.wins()); assertEquals(3, q.losses());
        assertEquals(0, new BigDecimal("70").compareTo(q.winPercentage().orElseThrow()));
        assertEquals(0, new BigDecimal("1.25").compareTo(q.transactionCosts()));
        assertEquals(0, new BigDecimal("2.125").compareTo(q.slippageBasisPoints()));
        assertEquals("fixture", q.dataset().sourceIdentity()); assertEquals(START.minusSeconds(60), q.dataset().observedAt());
        assertEquals("a".repeat(64), q.provenance().fingerprint()); assertEquals(NOW, q.provenance().observedAt());
        assertEquals("fixture-policy", q.policy().orElseThrow().policyId()); assertEquals("v1", q.policy().orElseThrow().version());
        assertFalse(result.tradingAuthorized()); assertNotEquals("QUALIFIED", q.decision().name());
        assertEquals(f.instrument.columns.get("evidence_id"), db.queryForObject("SELECT instrument_evidence_id FROM trading.evidence_qualification_snapshots WHERE evidence_id=?", UUID.class, row.columns.get("evidence_id")));
        assertEquals(f.strategy.columns.get("evidence_id"), db.queryForObject("SELECT strategy_evidence_id FROM trading.evidence_qualification_snapshots WHERE evidence_id=?", UUID.class, row.columns.get("evidence_id")));
        String persisted = db.queryForObject("SELECT payload::text FROM trading.evidence_qualification_snapshots WHERE evidence_id=?", String.class, row.columns.get("evidence_id"));
        assertEquals(row.columns.get("payload_sha256"), EvidenceSnapshotFingerprint.sha256(persisted));
        var instrument = f.reader().instrument(f.id, 1).evidence().orElseThrow();
        assertEquals(f.id, instrument.instrumentId()); assertEquals(1, instrument.snapshotVersion());
        var strategy = f.reader().strategy(f.definition).evidence().orElseThrow();
        assertEquals(Map.of("threshold", "0.70", "cost", "1.2500"), strategy.parameters());
        assertThrows(UnsupportedOperationException.class, () -> strategy.parameters().put("threshold", "0"));
        assertEquals(q, f.read().evidence().orElseThrow()); assertTrue(q.getClass().isRecord());
    }
    @Test void qualificationReadPolicyAndCompletenessAreEnforced() throws Exception {
        Fixture f = new Fixture(); f.references(); f.qualification().persist();
        assertEquals(EvidenceAvailability.Status.INVALID, f.read(f.reader("other-grant", "fixture")).status());
        assertEquals(EvidenceAvailability.Status.MISSING, f.read(f.reader("fixture-grant", "other-source")).status());
        Row incomplete = f.qualification(); incomplete.columns.put("revision", 2L); incomplete.payload.putNull("policy"); incomplete.persist();
        assertEquals(EvidenceAvailability.Status.INCOMPLETE, f.read().status()); assertFalse(f.read().tradingAuthorized());
    }
    private static void assertExposure(Fixture f, String value) {
        assertEquals(0, new BigDecimal(value).compareTo(f.reader().accountRisk(f.account).evidence().orElseThrow().exposure()));
    }
    @Test void newestEligibleObservationWins() throws Exception {
        Fixture f = new Fixture(); Row old = f.account().observed(NOW.minusSeconds(10)); old.columns.put("revision", 99L); old.persist();
        f.account().field("exposure", "exposure", new BigDecimal("20")).persist(); assertExposure(f, "20");
    }
    @Test void equalTimeAccountSnapshotsSelectHighestRevision() throws Exception {
        Fixture f = new Fixture(); f.account().persist(); Row row = f.account().field("exposure", "exposure", new BigDecimal("20")); row.columns.put("revision", 2L); row.persist(); assertExposure(f, "20");
    }
    @Test void equalTimeQualificationSnapshotsSelectHighestRevision() throws Exception {
        Fixture f = new Fixture(); f.references(); f.qualification().persist(); Row row = f.qualification(); row.columns.put("revision", 2L);
        row.field("transaction_costs", "transactionCosts", new BigDecimal("3.75")); row.persist();
        assertEquals(0, new BigDecimal("3.75").compareTo(f.read().evidence().orElseThrow().transactionCosts()));
    }
    @Test void equalTimeEqualRevisionDuplicatesAreRejected() throws Exception {
        Fixture f = new Fixture(); f.references(); f.qualification().persist(); f.account().persist();
        rejected("qualification-equal-tie", "23505", f.qualification()::insert); rejected("account-equal-tie", "23505", f.account()::insert);
    }
    @Test void exactReplayCannotOverwriteHistory() throws Exception {
        for (String kind : TABLES) {
            Row r = fresh(kind); r.persist(); rejected(kind + ":exact-replay:overlapping-unique", "23505", r::insert);
            Object identity = r.columns.get("evidence_id");
            r.columns.put("evidence_id", UUID.randomUUID()); rejected(kind + ":natural-identity", "23505", r::insert);
            r.columns.put("evidence_id", identity);
            if (kind.equals("instrument")) r.field("snapshot_version", "snapshotVersion", 2L);
            else if (kind.equals("strategy")) r.field("strategy_version", "strategyVersion", "v2");
            else r.columns.put("revision", 2L);
            r.columns.put("payload_sha256", ""); rejected(kind + ":primary-key", "23505", r::insert);
        }
    }
    @Test void conflictingFingerprintCannotOverwriteIdentity() throws Exception {
        Fixture f = new Fixture(); Row first = f.account(); first.persist(); Row conflict = f.account().field("exposure", "exposure", new BigDecimal("88"));
        rejected("conflicting-natural-key", "23505", conflict::insert); assertExposure(f, "12.50");
        assertEquals(first.columns.get("payload_sha256"), db.queryForObject("SELECT payload_sha256 FROM trading.evidence_account_risk_snapshots WHERE evidence_id=?", String.class, first.columns.get("evidence_id")));
    }
    @Test void staleNewestSnapshotDoesNotFallBack() throws Exception {
        Fixture f = new Fixture(); f.account().observed(NOW.minusSeconds(20)).persist(); f.account().observed(NOW.minusSeconds(10)).expires(NOW).persist();
        assertEquals(EvidenceAvailability.Status.STALE, f.reader().accountRisk(f.account).status());
    }
    @Test void invalidNewestSnapshotDoesNotFallBack() throws Exception {
        Fixture f = new Fixture(); f.account().observed(NOW.minusSeconds(20)).persist(); Row bad = f.account(); bad.columns.put("payload_sha256", "0".repeat(64)); bad.persist();
        assertEquals(EvidenceAvailability.Status.INVALID, f.reader().accountRisk(f.account).status());
    }
    @Test void futureObservationIsExcluded() throws Exception {
        Fixture f = new Fixture(); f.account().persist(); f.account().observed(NOW.plusSeconds(1)).field("exposure", "exposure", new BigDecimal("99")).persist(); assertExposure(f, "12.50");
    }
    @Test void instrumentConstraintsRejectIsolatedViolations() throws Exception {
        commonConstraints("instrument"); reject(fresh("instrument"), "version", r -> r.field("snapshot_version", "snapshotVersion", 0L));
        reject(fresh("instrument"), "zero-instrument", r -> r.field("instrument_id", "instrumentId", new UUID(0, 0)));
        reject(fresh("instrument"), "instrument-mirror", r -> r.payload.put("instrumentId", UUID.randomUUID().toString()));
        reject(fresh("instrument"), "version-mirror", r -> r.payload.put("snapshotVersion", 2));
        reject(fresh("instrument"), "exchange", r -> r.payload.put("exchange", "")); reject(fresh("instrument"), "symbol", r -> r.payload.put("symbol", ""));
    }
    @Test void strategyConstraintsRejectIsolatedViolations() throws Exception {
        commonConstraints("strategy");
        reject(fresh("strategy"), "strategy-id", r -> r.field("strategy_id", "strategyId", "!"));
        reject(fresh("strategy"), "version", r -> r.field("strategy_version", "strategyVersion", "!"));
        reject(fresh("strategy"), "id-mirror", r -> r.payload.put("strategyId", "other"));
        reject(fresh("strategy"), "version-mirror", r -> r.payload.put("strategyVersion", "v2"));
        reject(fresh("strategy"), "parameters", r -> r.payload.put("parameters", "bad"));
        for (String key : List.of("entryConditions", "exitConditions", "universe")) reject(fresh("strategy"), key, r -> r.payload.putArray(key));
    }
    @Test void qualificationConstraintsRejectIsolatedViolations() throws Exception {
        commonConstraints("qualification");
        reject(fresh("qualification"), "blank-timeframe:trigger-intercepted", r -> r.field("timeframe", "timeframe", " "));
        for (String[] pair : List.of(new String[]{"policy_id", "policyId"}, new String[]{"policy_version", "version"}))
            reject(fresh("qualification"), pair[0], r -> { r.columns.put(pair[0], " "); ((ObjectNode) r.payload.get("policy")).put(pair[1], " "); });
        // Negative total necessarily overlaps the wins/losses partition constraints; label it explicitly.
        reject(fresh("qualification"), "negative-total:overlapping-count-invariants", r -> r.field("evaluated_trades", "evaluatedTrades", -1L));
        reject(fresh("qualification"), "observation-before-evaluation", r -> r.observed(END.minusSeconds(1)));
        for (String key : List.of("instrumentId", "strategyId", "strategyVersion", "timeframe", "periodStart", "periodEnd")) {
            reject(fresh("qualification"), "identity-mirror:" + key, r -> r.payload.put(key, switch (key) {
                case "instrumentId" -> UUID.randomUUID().toString();
                case "periodStart", "periodEnd" -> NOW.minusSeconds(10000).toString();
                default -> "other";
            }));
        }
        for (String key : List.of("evaluatedTrades", "wins", "losses", "transactionCosts", "slippageBasisPoints"))
            reject(fresh("qualification"), "number-mirror:" + key, r -> r.payload.put(key, 99));
        for (String key : List.of("origin", "sourceIdentity"))
            reject(fresh("qualification"), "dataset:" + key, r -> ((ObjectNode) r.payload.get("dataset")).put(key, "other"));
        reject(fresh("qualification"), "dataset-observation", r -> ((ObjectNode) r.payload.get("dataset")).put("observedAt", NOW.plusSeconds(1).toString()));
        reject(fresh("qualification"), "dataset-expiry", r -> ((ObjectNode) r.payload.get("dataset")).put("expiresAt", NOW.plusSeconds(599).toString()));
        for (String key : List.of("sourceIdentity", "fingerprint", "observedAt", "expiresAt", "origin", "retention"))
            reject(fresh("qualification"), "dataset-required:" + key, r -> ((ObjectNode) r.payload.get("dataset")).remove(key));
        for (String key : List.of("policyId", "version", "fingerprint", "minimumTrades", "minimumWinPercentage", "remainingChecksPassed"))
            reject(fresh("qualification"), "policy-required:" + key, r -> ((ObjectNode) r.payload.get("policy")).remove(key));
        for (String[] pair : List.of(new String[]{"transaction_costs", "transactionCosts"}, new String[]{"slippage_basis_points", "slippageBasisPoints"}))
            for (String value : List.of("NaN", "Infinity")) reject(fresh("qualification"), pair[0] + ":" + value, r -> r.field(pair[0], pair[1], value));
        reject(fresh("qualification"), "period", r -> r.field("period_end", "periodEnd", Timestamp.from(START)));
        reject(fresh("qualification"), "wins", r -> { r.field("wins", "wins", -1L); r.field("losses", "losses", 11L); });
        reject(fresh("qualification"), "losses", r -> r.field("losses", "losses", 4L));
        reject(fresh("qualification"), "cost", r -> r.field("transaction_costs", "transactionCosts", new BigDecimal("-1")));
        reject(fresh("qualification"), "slippage", r -> r.field("slippage_basis_points", "slippageBasisPoints", new BigDecimal("-1")));
        reject(fresh("qualification"), "dataset-retention", r -> ((ObjectNode) r.payload.get("dataset")).put("retention", "NOT_AUTHORIZED"));
        reject(fresh("qualification"), "policy-identity", r -> ((ObjectNode) r.payload.get("policy")).put("policyId", "other"));
    }
    @Test void accountRiskConstraintsRejectIsolatedViolations() throws Exception {
        commonConstraints("account_risk");
        reject(fresh("account_risk"), "account", r -> r.field("account_scope", "accountScope", ""));
        reject(fresh("account_risk"), "currency", r -> r.field("currency", "currency", "inr"));
        reject(fresh("account_risk"), "exposure", r -> r.field("exposure", "exposure", new BigDecimal("-1")));
        reject(fresh("account_risk"), "limit", r -> r.field("exposure_limit", "exposureLimit", BigDecimal.ZERO));
        for (String[] pair : List.of(new String[]{"exposure", "exposure"}, new String[]{"exposure_limit", "exposureLimit"}))
            for (String value : List.of("NaN", "Infinity")) reject(fresh("account_risk"), pair[0] + ":" + value, r -> r.field(pair[0], pair[1], value));
        for (String key : List.of("accountScope", "currency")) reject(fresh("account_risk"), "mirror:" + key, r -> r.payload.put(key, "OTHER"));
        for (String key : List.of("exposure", "exposureLimit")) reject(fresh("account_risk"), "mirror:" + key, r -> r.payload.put(key, 99));
        reject(fresh("account_risk"), "additional-limits", r -> r.payload.put("additionalLimits", "bad"));
    }
    @Test void qualificationLinkChecksRejectIsolatedViolations() throws Exception {
        reject(fresh("qualification"), "missing-reference-trigger", r -> r.columns.put("instrument_evidence_id", UUID.randomUUID()));
        reject(fresh("qualification"), "reference-grant", r -> r.columns.put("authorization_reference", "other"));
        reject(fresh("qualification"), "reference-timeframe", r -> r.field("timeframe", "timeframe", "PT1M"));
        reject(fresh("qualification"), "reference-chronology", r -> r.field("period_start", "periodStart", Timestamp.from(START.minusSeconds(120))));
        reject(fresh("qualification"), "reference-expiry", r -> r.expires(NOW.plusSeconds(601)));
        reject(fresh("qualification"), "reference-universe", r -> r.field("instrument_id", "instrumentId", UUID.randomUUID()));
    }
    @Test void qualificationForeignKeysRejectMismatchedRelationships() throws Exception {
        Row strategy = fresh("qualification"); strategy.field("strategy_version", "strategyVersion", "v2");
        rejected("strategy-composite-fk", "23503", strategy::insert);
        Fixture f = new Fixture(); f.references(); Fixture other = new Fixture(); other.instrument.persist();
        Row instrument = f.qualification(); instrument.columns.put("instrument_evidence_id", other.instrument.columns.get("evidence_id"));
        rejected("instrument-composite-fk", "23503", instrument::insert);
    }
    private static void mutation(String verb) throws SQLException {
        for (String kind : TABLES) {
            Row row = fresh(kind); row.persist();
            String sql = switch (verb) {
                case "UPDATE" -> "UPDATE " + row.table() + " SET revision=revision WHERE evidence_id='" + row.columns.get("evidence_id") + "'";
                case "DELETE" -> "DELETE FROM " + row.table() + " WHERE evidence_id='" + row.columns.get("evidence_id") + "'";
                default -> "TRUNCATE " + row.table() + " CASCADE";
            };
            rejected(kind + ":" + verb + (verb.equals("TRUNCATE") && Set.of("instrument", "strategy").contains(kind) ? ":cascade-trigger-order-not-isolated" : ""), "55000", c -> { try (Statement s = c.createStatement()) { s.setQueryTimeout(5); s.execute(sql); } });
            assertEquals(1, db.queryForObject("SELECT count(*) FROM " + row.table() + " WHERE evidence_id=?", Integer.class, row.columns.get("evidence_id")));
        }
    }
    @Test void updatesAreRejectedOnEveryEvidenceTable() throws Exception { mutation("UPDATE"); }
    @Test void deletesAreRejectedOnEveryEvidenceTable() throws Exception { mutation("DELETE"); }
    @Test void truncatesAreRejectedOnEveryEvidenceTable() throws Exception { mutation("TRUNCATE"); }
    @Test void sourceAndAccountScopesRemainIsolated() throws Exception {
        Fixture f = new Fixture(); f.account().persist();
        assertEquals(EvidenceAvailability.Status.MISSING, f.reader("fixture-grant", "other").accountRisk(f.account).status());
        assertEquals(EvidenceAvailability.Status.INVALID, f.reader().accountRisk("other-account").status());
        assertExposure(f, "12.50");
    }
}
