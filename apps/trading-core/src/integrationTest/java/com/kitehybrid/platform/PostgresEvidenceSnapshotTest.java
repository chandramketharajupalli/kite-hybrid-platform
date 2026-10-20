package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.domain.EvidenceAvailability;
import com.kitehybrid.platform.trading.infrastructure.*;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit external disposable fixture only. Never starts Docker or discovers DB_URL.
 * The separately authorized supervisor must create a fresh, isolated database and
 * synthetic admin role, then supply these properties. Missing authorization FAILS.
 */
class PostgresEvidenceSnapshotTest {
    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
    private static DriverManagerDataSource source;
    private static JdbcTemplate admin;
    private static Flyway flyway;
    @BeforeAll static void migrateDisposableFixture() {
        if (!"true".equals(System.getProperty("evidence.integration.authorized")))
            throw new IllegalStateException("Separate disposable execution authorization required");
        String url = System.getProperty("evidence.integration.url", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/evidence_fixture_[a-z0-9]+"))
            throw new IllegalStateException("Explicit loopback disposable database required");
        source = new DriverManagerDataSource(url, "evidence_fixture_admin", "");
        admin = new JdbcTemplate(source);
        assertEquals(0, admin.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema NOT IN ('pg_catalog','information_schema')
                """, Integer.class));
        // Fixed history schema from migration zero avoids the historical $user/search_path split.
        flyway = Flyway.configure().dataSource(source).defaultSchema("public").schemas("public")
                .locations("classpath:db/migration", "classpath:db/historical", "classpath:db/evidence")
                .cleanDisabled(true).load();
        assertEquals(13, flyway.migrate().migrationsExecuted);
    }
    private static String provenance(Instant observed, Instant expires) {
        return """
                {"sourceIdentity":"fixture","fingerprint":"%s","observedAt":"%s","expiresAt":"%s",
                 "origin":"SYNTHETIC_FIXTURE","retention":"SYNTHETIC_ONLY"}
                """.formatted("a".repeat(64), observed, expires);
    }
    private static String payload(String account, Instant observed, Instant expires) {
        return """
                {"accountScope":"%s","currency":"INR","exposure":12.50,"exposureLimit":100,
                 "additionalLimits":{"daily":25.25},"provenance":%s}
                """.formatted(account, provenance(observed, expires));
    }
    private static UUID insert(String account, long revision, Instant observed, Instant expires) {
        String payload = payload(account, observed, expires);
        UUID id = UUID.randomUUID();
        admin.update("""
                INSERT INTO trading.evidence_account_risk_snapshots
                (evidence_id,schema_version,revision,source_identity,authorization_reference,source_fingerprint,
                 payload_sha256,origin,retention,observed_at,expires_at,payload,account_scope,currency,exposure,exposure_limit)
                VALUES (?,1,?,'fixture','fixture-grant',?,?,'SYNTHETIC_FIXTURE','SYNTHETIC_ONLY',?,?,?::jsonb,?,'INR',12.50,100)
                """, id, revision, "a".repeat(64), EvidenceSnapshotFingerprint.sha256(payload),
                Timestamp.from(observed), Timestamp.from(expires), payload, account);
        return id;
    }
    private static PostgresEvidenceSnapshotQueryAdapter reader(JdbcTemplate jdbc, String account) {
        return new PostgresEvidenceSnapshotQueryAdapter(jdbc,
                new EvidenceSnapshotReadPolicy("fixture", "fixture-grant", NOW.minusSeconds(3600),
                        NOW.plusSeconds(3600), Duration.ofMinutes(5), Set.of(), Set.of(), Set.of(account)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
    private static String account() { return "synthetic-" + UUID.randomUUID(); }

    @Test void migrationHistoryIsExplicitAndReplayDoesNothing() {
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("13", flyway.info().current().getVersion().toString());
        assertNull(admin.queryForObject("SELECT to_regclass('trading.flyway_schema_history')", String.class));
        assertEquals(13, admin.queryForObject("SELECT count(*) FROM public.flyway_schema_history WHERE success", Integer.class));
    }
    @Test void exactReplayAndConflictingPayloadCannotOverwriteHistory() {
        String account = account();
        UUID id = insert(account, 1, NOW, NOW.plusSeconds(600));
        assertThrows(DataAccessException.class, () -> insert(account, 1, NOW, NOW.plusSeconds(600)));
        assertThrows(DataAccessException.class, () -> insert(account, 1, NOW, NOW.plusSeconds(900)));
        assertEquals(id, admin.queryForObject("SELECT evidence_id FROM trading.evidence_account_risk_snapshots WHERE account_scope=?",
                UUID.class, account));
        assertEquals(EvidenceSnapshotFingerprint.sha256(payload(account, NOW, NOW.plusSeconds(600))),
                admin.queryForObject("SELECT payload_sha256 FROM trading.evidence_account_risk_snapshots WHERE evidence_id=?",
                        String.class, id));
    }
    @Test void updatesDeletesAndTruncateAreRejected() {
        UUID id = insert(account(), 1, NOW, NOW.plusSeconds(600));
        assertThrows(DataAccessException.class, () -> admin.update(
                "UPDATE trading.evidence_account_risk_snapshots SET revision=2 WHERE evidence_id=?", id));
        assertThrows(DataAccessException.class, () -> admin.update(
                "DELETE FROM trading.evidence_account_risk_snapshots WHERE evidence_id=?", id));
        assertThrows(DataAccessException.class, () -> admin.execute("TRUNCATE trading.evidence_account_risk_snapshots"));
    }
    @Test void concurrentDuplicateInsertHasOneWinner() throws Exception {
        String account = account();
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> work = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try { insert(account, 1, NOW, NOW.plusSeconds(600)); return true; }
            catch (org.springframework.dao.DuplicateKeyException expected) { return false; }
        };
        try {
            Future<Boolean> first = pool.submit(work);
            Future<Boolean> second = pool.submit(work);
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, admin.queryForObject(
                    "SELECT count(*) FROM trading.evidence_account_risk_snapshots WHERE account_scope=?", Integer.class, account));
        } finally { pool.shutdownNow(); }
    }
    @Test void futureSnapshotsAreExcludedAndAccountsAreIsolated() {
        String account = account();
        insert(account, 1, NOW.plusSeconds(1), NOW.plusSeconds(600));
        var query = reader(admin, account);
        assertEquals(EvidenceAvailability.Status.MISSING, query.accountRisk(account).status());
        assertEquals(EvidenceAvailability.Status.INVALID, query.accountRisk(account()).status());
    }
    @Test void newestStaleSnapshotDoesNotFallBack() {
        String account = account();
        insert(account, 1, NOW.minusSeconds(60), NOW.plusSeconds(600));
        insert(account, 2, NOW.minusSeconds(30), NOW);
        assertEquals(EvidenceAvailability.Status.STALE, reader(admin, account).accountRisk(account).status());
    }
    @Test void validReadPreservesDecimalAndDoesNotWrite() {
        String account = account();
        insert(account, 1, NOW, NOW.plusSeconds(600));
        var result = reader(admin, account).accountRisk(account);
        assertEquals(EvidenceAvailability.Status.AVAILABLE, result.status());
        assertEquals(0, result.evidence().orElseThrow().exposure().compareTo(new java.math.BigDecimal("12.50")));
        assertFalse(result.tradingAuthorized());
        assertEquals(1, admin.queryForObject(
                "SELECT count(*) FROM trading.evidence_account_risk_snapshots WHERE account_scope=?", Integer.class, account));
    }
    @Test void selectOnlyRoleCanReadButCannotWrite() throws Exception {
        String account = account();
        insert(account, 1, NOW, NOW.plusSeconds(600));
        admin.execute("CREATE ROLE evidence_fixture_reader NOLOGIN");
        admin.execute("GRANT USAGE ON SCHEMA trading TO evidence_fixture_reader");
        admin.execute("GRANT SELECT ON trading.evidence_account_risk_snapshots TO evidence_fixture_reader");
        try (Connection connection = source.getConnection()) {
            try (var statement = connection.createStatement()) { statement.execute("SET ROLE evidence_fixture_reader"); }
            var restricted = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            assertEquals(EvidenceAvailability.Status.AVAILABLE, reader(restricted, account).accountRisk(account).status());
            assertThrows(DataAccessException.class, () -> restricted.update(
                    "INSERT INTO trading.evidence_account_risk_snapshots DEFAULT VALUES"));
        }
    }
    @Test void databaseRejectsUnauthorizedRetentionAndMissingRequiredValues() {
        String account = account();
        assertThrows(DataAccessException.class, () -> admin.update("""
                INSERT INTO trading.evidence_account_risk_snapshots
                (evidence_id,schema_version,revision,source_identity,authorization_reference,source_fingerprint,
                 payload_sha256,origin,retention,observed_at,expires_at,payload,account_scope,currency,exposure,exposure_limit)
                VALUES (?,1,1,'fixture','fixture-grant',?,?,'LOCAL_UNVERIFIED','NOT_AUTHORIZED',?,?,?::jsonb,?,'INR',NULL,100)
                """, UUID.randomUUID(), "a".repeat(64), "b".repeat(64), Timestamp.from(NOW),
                Timestamp.from(NOW.plusSeconds(600)), payload(account, NOW, NOW.plusSeconds(600)), account));
    }
    private static UUID reference(String table, String extraColumns, String extraValues, String payload,
            Object... extraArguments) {
        UUID id = UUID.randomUUID();
        var args = new ArrayList<Object>(List.of(id, "a".repeat(64), EvidenceSnapshotFingerprint.sha256(payload),
                Timestamp.from(NOW.minusSeconds(86400)), Timestamp.from(NOW.plusSeconds(600)), payload));
        args.addAll(Arrays.asList(extraArguments));
        admin.update("INSERT INTO trading." + table
                + " (evidence_id,schema_version,revision,source_identity,authorization_reference,source_fingerprint,"
                + "payload_sha256,origin,retention,observed_at,expires_at,payload," + extraColumns + ")"
                + " VALUES (?,1,1,'fixture','fixture-grant',?,?,'SYNTHETIC_FIXTURE','SYNTHETIC_ONLY',?,?,?::jsonb,"
                + extraValues + ")", args.toArray());
        return id;
    }
    @Test void instrumentAndStrategySnapshotsMapExactVersionsAndParameters() {
        UUID instrument = UUID.randomUUID();
        String strategy = "s" + UUID.randomUUID().toString().replace("-", "");
        String p = provenance(NOW.minusSeconds(86400), NOW.plusSeconds(600));
        String instrumentPayload = """
                {"instrumentId":"%s","exchange":"FIXTURE","symbol":"SYNTHETIC","token":null,
                 "snapshotVersion":1,"provenance":%s}
                """.formatted(instrument, p);
        reference("evidence_instrument_snapshots", "instrument_id,snapshot_version", "?,1", instrumentPayload, instrument);
        String strategyPayload = """
                {"strategyId":"%s","strategyVersion":"v1","timeframe":"PT5M","parameters":{"threshold":"0.70"},
                 "entryConditions":["synthetic-entry"],"exitConditions":["synthetic-exit"],
                 "universe":["%s"],"provenance":%s}
                """.formatted(strategy, instrument, p);
        reference("evidence_strategy_snapshots", "strategy_id,strategy_version", "?,'v1'", strategyPayload, strategy);
        var id = new com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId(instrument);
        var definition = new com.kitehybrid.platform.strategy.domain.StrategyDefinition(
                new com.kitehybrid.platform.shared.domain.Identifiers.StrategyId(strategy), "v1");
        var query = new PostgresEvidenceSnapshotQueryAdapter(admin,
                new EvidenceSnapshotReadPolicy("fixture", "fixture-grant", NOW.minusSeconds(172800),
                        NOW.plusSeconds(3600), Duration.ofDays(2), Set.of(id), Set.of(definition), Set.of()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(EvidenceAvailability.Status.AVAILABLE, query.instrument(id, 1).status());
        assertEquals(EvidenceAvailability.Status.MISSING, query.instrument(id, 2).status());
        assertEquals("0.70", query.strategy(definition).evidence().orElseThrow().parameters().get("threshold"));
        assertThrows(DataAccessException.class, () ->
                reference("evidence_strategy_snapshots", "strategy_id,strategy_version", "?,'v1'",
                        strategyPayload.replace("0.70", "0.80"), strategy));
    }
    @Test void qualificationForeignKeysAndChronologyAreEnforced() {
        // No matching immutable instrument/strategy references exist for these synthetic identities.
        UUID instrument = UUID.randomUUID();
        String p = provenance(NOW, NOW.plusSeconds(600));
        String payload = """
                {"instrumentId":"%s","strategyId":"synthetic","strategyVersion":"v1","timeframe":"PT5M",
                 "periodStart":"2026-10-18T00:00:00Z","periodEnd":"2026-10-19T00:00:00Z",
                 "evaluatedTrades":10,"wins":7,"losses":3,"costModel":"fixture",
                 "transactionCosts":1,"slippageBasisPoints":2,"dataset":%s,"policy":null,"provenance":%s}
                """.formatted(instrument, p, p);
        assertThrows(DataAccessException.class, () -> admin.update("""
                INSERT INTO trading.evidence_qualification_snapshots
                (evidence_id,schema_version,revision,source_identity,authorization_reference,source_fingerprint,
                payload_sha256,origin,retention,observed_at,expires_at,payload,instrument_id,instrument_evidence_id,
                strategy_id,strategy_version,strategy_evidence_id,timeframe,period_start,period_end,policy_id,policy_version,
                evaluated_trades,wins,losses,transaction_costs,slippage_basis_points)
                VALUES (?,1,1,'fixture','fixture-grant',?,?,'SYNTHETIC_FIXTURE','SYNTHETIC_ONLY',?,?,?::jsonb,?,?,
                'synthetic','v1',?,'PT5M','2026-10-18T00:00:00Z','2026-10-19T00:00:00Z','fixture','v1',10,7,3,1,2)
                """, UUID.randomUUID(), "a".repeat(64), EvidenceSnapshotFingerprint.sha256(payload),
                Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(600)), payload, instrument,
                UUID.randomUUID(), UUID.randomUUID()));
    }
}
