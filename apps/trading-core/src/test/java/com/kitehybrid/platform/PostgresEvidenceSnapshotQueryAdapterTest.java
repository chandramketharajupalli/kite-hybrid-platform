package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.domain.EvidenceAvailability;
import com.kitehybrid.platform.trading.infrastructure.*;
import java.sql.*;
import java.time.*;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PostgresEvidenceSnapshotQueryAdapterTest {
    private final Instant now = Instant.parse("2026-10-20T00:00:00Z");
    private DataSource source;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet rows;
    private PostgresEvidenceSnapshotQueryAdapter adapter;
    @BeforeEach void setup() throws Exception {
        source = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        var policy = new EvidenceSnapshotReadPolicy("fixture", "fixture-grant", now.minusSeconds(60),
                now.plusSeconds(600), Duration.ofMinutes(5), Set.of(), Set.of(), Set.of("synthetic-account"));
        adapter = new PostgresEvidenceSnapshotQueryAdapter(new JdbcTemplate(source), policy, Clock.fixed(now, ZoneOffset.UTC));
    }
    private void row(String payload) throws Exception {
        when(rows.next()).thenReturn(true, false);
        when(rows.getObject("schema_version", Integer.class)).thenReturn(1);
        when(rows.getString("payload")).thenReturn(payload);
        when(rows.getString("payload_sha256")).thenReturn(EvidenceSnapshotFingerprint.sha256(payload));
        when(rows.getString("source_identity")).thenReturn("fixture");
        when(rows.getString("source_fingerprint")).thenReturn("a".repeat(64));
        when(rows.getString("origin")).thenReturn("SYNTHETIC_FIXTURE");
        when(rows.getString("retention")).thenReturn("SYNTHETIC_ONLY");
        when(rows.getString("authorization_reference")).thenReturn("fixture-grant");
        when(rows.getTimestamp("observed_at")).thenReturn(Timestamp.from(now));
        when(rows.getTimestamp("expires_at")).thenReturn(Timestamp.from(now.plusSeconds(3600)));
    }
    @Test void accountOutsidePolicyNeverTouchesDatabase() {
        assertEquals(EvidenceAvailability.Status.INVALID, adapter.accountRisk("other-account").status());
        verifyNoInteractions(source);
    }
    @Test void missingIsNotAvailableAndQueryIsBoundedReadOnly() throws Exception {
        assertEquals(EvidenceAvailability.Status.MISSING, adapter.accountRisk("synthetic-account").status());
        verify(connection).prepareStatement(contains("account_scope=? AND source_identity=? AND observed_at<=?"));
        verify(connection).prepareStatement(contains("ORDER BY observed_at DESC, revision DESC, evidence_id DESC LIMIT 1"));
        verify(connection).setReadOnly(true);
        verify(statement).setMaxRows(1);
        verify(statement).setQueryTimeout(anyInt());
        verify(statement, never()).executeUpdate();
    }
    @Test void validFixtureIsAvailableButNeverTradingAuthorized() throws Exception {
        row(EvidenceSnapshotMappingTest.account());
        var result = adapter.accountRisk("synthetic-account");
        assertEquals(EvidenceAvailability.Status.AVAILABLE, result.status());
        assertFalse(result.tradingAuthorized());
    }
    @Test void tamperedFingerprintIsInvalidWithoutFallback() throws Exception {
        row(EvidenceSnapshotMappingTest.account());
        when(rows.getString("payload_sha256")).thenReturn("0".repeat(64));
        assertEquals(EvidenceAvailability.Status.INVALID, adapter.accountRisk("synthetic-account").status());
        verify(statement, times(1)).executeQuery();
    }
    @Test void rowAuthorizationCannotGrantItselfPermission() throws Exception {
        row(EvidenceSnapshotMappingTest.account());
        when(rows.getString("authorization_reference")).thenReturn("unapproved");
        assertEquals(EvidenceAvailability.Status.INVALID, adapter.accountRisk("synthetic-account").status());
    }
    @Test void payloadAccountMismatchIsInvalid() throws Exception {
        row(EvidenceSnapshotMappingTest.account().replace("synthetic-account", "other-account"));
        assertEquals(EvidenceAvailability.Status.INVALID, adapter.accountRisk("synthetic-account").status());
    }
    @Test void malformedTimestampFailsClosed() throws Exception {
        row(EvidenceSnapshotMappingTest.account().replace("2026-10-20T00:00:00Z", "not-a-time"));
        assertEquals(EvidenceAvailability.Status.INVALID, adapter.accountRisk("synthetic-account").status());
    }
    @Test void databaseFailureIsNotMissing() throws Exception {
        when(statement.executeQuery()).thenThrow(new SQLException("synthetic database failure", "08006"));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> adapter.accountRisk("synthetic-account"));
    }
    @Test void permissionExpiryRejectsBeforeConnecting() {
        var expired = new EvidenceSnapshotReadPolicy("fixture", "fixture-grant", now.minusSeconds(60),
                now, Duration.ofMinutes(5), Set.of(), Set.of(), Set.of("synthetic-account"));
        var query = new PostgresEvidenceSnapshotQueryAdapter(new JdbcTemplate(source), expired, Clock.fixed(now, ZoneOffset.UTC));
        assertEquals(EvidenceAvailability.Status.INVALID, query.accountRisk("synthetic-account").status());
        verifyNoInteractions(source);
    }
    @Test void freshnessPolicyRejectsOtherwiseUnexpiredEvidence() throws Exception {
        String payload = EvidenceSnapshotMappingTest.account().replace("2026-10-20T00:00:00Z", "2026-10-19T23:55:00Z");
        row(payload);
        when(rows.getTimestamp("observed_at")).thenReturn(Timestamp.from(now.minusSeconds(300)));
        assertEquals(EvidenceAvailability.Status.STALE, adapter.accountRisk("synthetic-account").status());
        verify(statement, times(1)).executeQuery();
    }
    @Test void missingQualificationPolicyIsIncompleteNotQualified() throws Exception {
        var id = new com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId(new java.util.UUID(1, 1));
        var definition = new com.kitehybrid.platform.strategy.domain.StrategyDefinition(
                new com.kitehybrid.platform.shared.domain.Identifiers.StrategyId("synthetic"), "v1");
        String payload = """
                {"instrumentId":"%s","strategyId":"synthetic","strategyVersion":"v1","timeframe":"PT5M",
                 "periodStart":"2026-10-18T00:00:00Z","periodEnd":"2026-10-19T00:00:00Z",
                 "evaluatedTrades":10,"wins":7,"losses":3,"costModel":"fixture","transactionCosts":1,
                 "slippageBasisPoints":2,"dataset":%s,"policy":null,"provenance":%s}
                """.formatted(id.value(), EvidenceSnapshotMappingTest.provenance(), EvidenceSnapshotMappingTest.provenance());
        row(payload);
        var query = new PostgresEvidenceSnapshotQueryAdapter(new JdbcTemplate(source),
                new EvidenceSnapshotReadPolicy("fixture", "fixture-grant", now.minusSeconds(60), now.plusSeconds(600),
                        Duration.ofMinutes(5), Set.of(id), Set.of(definition), Set.of()), Clock.fixed(now, ZoneOffset.UTC));
        var result = query.qualification(id, definition, Duration.ofMinutes(5),
                Instant.parse("2026-10-18T00:00:00Z"), Instant.parse("2026-10-19T00:00:00Z"), "fixture", "v1");
        assertEquals(EvidenceAvailability.Status.INCOMPLETE, result.status());
        assertFalse(result.tradingAuthorized());
    }
}
