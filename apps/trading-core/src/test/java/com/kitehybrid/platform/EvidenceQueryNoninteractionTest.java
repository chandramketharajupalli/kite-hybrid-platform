package com.kitehybrid.platform;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.shared.domain.Identifiers.StrategyId;
import com.kitehybrid.platform.strategy.domain.StrategyDefinition;
import com.kitehybrid.platform.trading.domain.EvidenceAvailability;
import com.kitehybrid.platform.trading.infrastructure.EvidenceSnapshotFingerprint;
import com.kitehybrid.platform.trading.infrastructure.EvidenceSnapshotReadPolicy;
import com.kitehybrid.platform.trading.infrastructure.EvidenceSnapshotRowMapper;
import com.kitehybrid.platform.trading.infrastructure.PostgresEvidenceSnapshotQueryAdapter;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Runtime JDBC allowlist and separate structural evidence; no disconnected operational mocks. */
class EvidenceQueryNoninteractionTest {
    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
    private static final InstrumentId ID = new InstrumentId(new UUID(1, 1));
    private static final StrategyDefinition STRATEGY = new StrategyDefinition(new StrategyId("synthetic"), "v1");
    private static final Instant START = NOW.minusSeconds(7200), END = NOW.minusSeconds(3600);
    private static final String ACCOUNT = "synthetic-account'--";
    private static final String PROVENANCE = """
            {"sourceIdentity":"fixture","fingerprint":"%s","observedAt":"%s","expiresAt":"%s",
             "origin":"SYNTHETIC_FIXTURE","retention":"SYNTHETIC_ONLY"}
            """.formatted("a".repeat(64), NOW, NOW.plusSeconds(600));

    private static final class JdbcBoundary implements InvocationHandler {
        final Map<Integer, Object> bindings = new TreeMap<>();
        final List<String> events = new ArrayList<>();
        final String payload;
        final boolean present, malformed, stale;
        String sql;
        int connections, executions, next, maxRows, timeout;
        boolean readOnly, autoCommit = true;
        JdbcBoundary(String payload, boolean present, boolean malformed) {
            this(payload, present, malformed, false);
        }
        JdbcBoundary(String payload, boolean present, boolean malformed, boolean stale) {
            this.payload = stale ? payload.replace(NOW.toString(), NOW.minusSeconds(20).toString())
                    .replace(NOW.plusSeconds(600).toString(), NOW.minusSeconds(1).toString()) : payload;
            this.present = present; this.malformed = malformed; this.stale = stale;
        }
        @SuppressWarnings("unchecked") private <T> T proxy(Class<T> type) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, this);
        }
        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) return switch (name) {
                case "toString" -> "synthetic-jdbc-boundary";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError(name);
            };
            events.add(name);
            if (proxy instanceof DataSource) {
                if (name.equals("getConnection") && (args == null || args.length == 0)) { connections++; return proxy(Connection.class); }
            } else if (proxy instanceof Connection) {
                switch (name) {
                    case "getAutoCommit": return autoCommit;
                    case "setAutoCommit": autoCommit = (boolean) args[0]; return null;
                    case "isReadOnly": return readOnly;
                    case "setReadOnly": readOnly = (boolean) args[0]; return null;
                    case "createStatement": return proxy(Statement.class);
                    case "prepareStatement":
                        assertTrue(readOnly); assertFalse(autoCommit); assertNull(sql, "No second candidate query");
                        sql = (String) args[0]; assertTrue(sql.startsWith("SELECT * FROM trading.evidence_"));
                        return proxy(PreparedStatement.class);
                    case "commit", "rollback", "close", "clearWarnings": return null;
                    case "isClosed": return false;
                    case "getWarnings": return null;
                    default: break;
                }
            } else if (proxy instanceof PreparedStatement) {
                switch (name) {
                    case "setObject", "setString", "setLong", "setTimestamp": bindings.put((Integer) args[0], args[1]); return null;
                    case "setMaxRows": maxRows = (int) args[0]; return null;
                    case "setQueryTimeout": timeout = (int) args[0]; assertTrue(timeout > 0 && timeout <= 5); return null;
                    case "executeQuery": assertTrue(readOnly); assertFalse(autoCommit); executions++; return proxy(ResultSet.class);
                    case "getWarnings": return null;
                    case "close", "clearWarnings": return null;
                    default: break;
                }
            } else if (proxy instanceof Statement) {
                if (name.equals("executeUpdate")) { assertEquals("SET TRANSACTION READ ONLY", args[0]); assertTrue(readOnly); return 0; }
                if (name.equals("close")) return null;
            } else if (proxy instanceof ResultSet) {
                switch (name) {
                    case "next": return present && next++ == 0;
                    case "close": return null;
                    case "getObject": assertEquals("schema_version", args[0]); assertEquals(Integer.class, args[1]); return malformed ? 2 : 1;
                    case "getTimestamp": return switch ((String) args[0]) {
                        case "observed_at" -> Timestamp.from(stale ? NOW.minusSeconds(20) : NOW);
                        case "expires_at" -> Timestamp.from(stale ? NOW.minusSeconds(1) : NOW.plusSeconds(600));
                        default -> throw new AssertionError(Arrays.toString(args));
                    };
                    case "getString": return switch ((String) args[0]) {
                        case "payload" -> payload;
                        case "payload_sha256" -> EvidenceSnapshotFingerprint.sha256(payload);
                        case "source_identity" -> "fixture";
                        case "authorization_reference" -> "fixture-grant";
                        case "source_fingerprint" -> "a".repeat(64);
                        case "origin" -> "SYNTHETIC_FIXTURE";
                        case "retention" -> "SYNTHETIC_ONLY";
                        default -> throw new AssertionError(Arrays.toString(args));
                    };
                    default: break;
                }
            }
            throw new AssertionError("Unapproved JDBC interaction: " + method + " " + Arrays.toString(args));
        }
        PostgresEvidenceSnapshotQueryAdapter adapter() {
            return new PostgresEvidenceSnapshotQueryAdapter(new JdbcTemplate(proxy(DataSource.class)),
                    new EvidenceSnapshotReadPolicy("fixture", "fixture-grant", NOW.minusSeconds(60), NOW.plusSeconds(600),
                            Duration.ofMinutes(5), Set.of(ID), Set.of(STRATEGY), Set.of(ACCOUNT)), Clock.fixed(NOW, ZoneOffset.UTC));
        }
        void verify(String predicate, List<Object> expected) {
            assertEquals(1, connections); assertEquals(1, executions); assertEquals(1, maxRows);
            assertTrue(timeout > 0 && timeout <= 5);
            assertTrue(sql.contains(" WHERE " + predicate + " AND source_identity=? AND observed_at<=?"));
            assertTrue(sql.endsWith("ORDER BY observed_at DESC, revision DESC, evidence_id DESC LIMIT 1"));
            var parameters = new ArrayList<>(expected); parameters.add("fixture"); parameters.add(Timestamp.from(NOW));
            assertEquals(parameters, new ArrayList<>(bindings.values()));
            assertEquals(parameters.size(), sql.chars().filter(c -> c == '?').count());
            assertFalse(sql.contains(ACCOUNT));
            assertTrue(events.containsAll(List.of("setReadOnly", "setAutoCommit", "executeUpdate", "commit", "close")));
            assertTrue(events.indexOf("setReadOnly") < events.indexOf("executeQuery"));
        }
    }
    private static void exercise(String payload, String predicate, List<Object> keys,
            Function<PostgresEvidenceSnapshotQueryAdapter, EvidenceAvailability<?>> call, EvidenceAvailability.Status valid) {
        for (int mode = 0; mode < 4; mode++) {
            JdbcBoundary jdbc = new JdbcBoundary(payload, mode != 0, mode == 1, mode == 3);
            var result = call.apply(jdbc.adapter());
            assertEquals(mode == 0 ? EvidenceAvailability.Status.MISSING : mode == 1 ? EvidenceAvailability.Status.INVALID : mode == 3 ? EvidenceAvailability.Status.STALE : valid, result.status());
            assertFalse(result.tradingAuthorized()); jdbc.verify(predicate, keys);
        }
    }
    @Test void instrumentQueryUsesOnlyApprovedReadInteractions() {
        String payload = """
                {"instrumentId":"%s","snapshotVersion":1,"exchange":"FIXTURE","symbol":"SYNTHETIC","token":null,"provenance":%s}
                """.formatted(ID.value(), PROVENANCE);
        exercise(payload, "instrument_id=? AND snapshot_version=?", List.of(ID.value(), 1L), r -> r.instrument(ID, 1), EvidenceAvailability.Status.AVAILABLE);
    }
    @Test void strategyQueryUsesOnlyApprovedReadInteractions() {
        String payload = """
                {"strategyId":"synthetic","strategyVersion":"v1","timeframe":"PT5M","parameters":{"threshold":"0.70"},
                "entryConditions":["synthetic-entry"],"exitConditions":["synthetic-exit"],"universe":["%s"],"provenance":%s}
                """.formatted(ID.value(), PROVENANCE);
        exercise(payload, "strategy_id=? AND strategy_version=?", List.of("synthetic", "v1"), r -> r.strategy(STRATEGY), EvidenceAvailability.Status.AVAILABLE);
    }
    @Test void qualificationQueryUsesOnlyApprovedReadInteractions() {
        String payload = """
                {"instrumentId":"%s","strategyId":"synthetic","strategyVersion":"v1","timeframe":"PT5M",
                "periodStart":"%s","periodEnd":"%s","evaluatedTrades":10,"wins":7,"losses":3,"costModel":"fixture",
                "transactionCosts":1.25,"slippageBasisPoints":2.125,"dataset":%s,"policy":null,"provenance":%s}
                """.formatted(ID.value(), START, END, PROVENANCE, PROVENANCE);
        exercise(payload, "instrument_id=? AND strategy_id=? AND strategy_version=? AND timeframe=? AND period_start=? AND period_end=? AND policy_id=? AND policy_version=?",
                List.of(ID.value(), "synthetic", "v1", "PT5M", Timestamp.from(START), Timestamp.from(END), "fixture-policy", "v1"),
                r -> r.qualification(ID, STRATEGY, Duration.ofMinutes(5), START, END, "fixture-policy", "v1"), EvidenceAvailability.Status.INCOMPLETE);
    }
    @Test void accountRiskQueryUsesOnlyApprovedReadInteractions() {
        String payload = """
                {"accountScope":"%s","currency":"INR","exposure":12.50,"exposureLimit":100,"additionalLimits":{},"provenance":%s}
                """.formatted(ACCOUNT, PROVENANCE);
        exercise(payload, "account_scope=?", List.of(ACCOUNT), r -> r.accountRisk(ACCOUNT), EvidenceAvailability.Status.AVAILABLE);
    }
    @Test void deniedReadPolicyDoesNotAcquireConnection() {
        JdbcBoundary jdbc = new JdbcBoundary("{}", false, false); var adapter = jdbc.adapter();
        assertEquals(EvidenceAvailability.Status.INVALID, adapter.accountRisk("outside-policy").status());
        assertEquals(EvidenceAvailability.Status.INVALID, adapter.instrument(new InstrumentId(new UUID(2, 2)), 1).status());
        assertEquals(EvidenceAvailability.Status.INVALID, adapter.strategy(new StrategyDefinition(new StrategyId("other"), "v1")).status());
        assertEquals(EvidenceAvailability.Status.INVALID, adapter.qualification(ID, STRATEGY, Duration.ZERO, START, END, "fixture-policy", "v1").status());
        assertEquals(0, jdbc.connections); assertTrue(jdbc.events.isEmpty());
    }
    @Test void reachableAdapterDependenciesExcludeOperationalServices() {
        // Structural assertion only. Runtime interactions are established by the five tests above.
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.kitehybrid.platform");
        Deque<JavaClass> pending = new ArrayDeque<>();
        pending.add(classes.get(PostgresEvidenceSnapshotQueryAdapter.class));
        Set<String> visited = new TreeSet<>();
        while (!pending.isEmpty()) {
            JavaClass current = pending.removeFirst();
            if (!visited.add(current.getName())) continue;
            for (var dependency : current.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass(); String name = target.getName();
                if (!name.startsWith("com.kitehybrid.platform.")) continue;
                assertFalse(name.contains(".broker.application.") || name.contains(".broker.infrastructure.")
                        || name.contains("RiskService") || name.contains("RefreshInstrumentRegistry")
                        || name.contains("OrderExecution") || name.contains("EvidenceWriter"), dependency.toString());
                pending.add(target);
            }
        }
        assertTrue(visited.contains(EvidenceSnapshotRowMapper.class.getName()));
        assertTrue(visited.contains(EvidenceSnapshotFingerprint.class.getName()));
        System.out.println("STRUCTURAL_APPLICATION_DEPENDENCIES " + visited);
        Set<Class<?>> expected = Set.of(JdbcTemplate.class, TransactionTemplate.class, EvidenceSnapshotReadPolicy.class, Clock.class, EvidenceSnapshotRowMapper.class);
        Set<Class<?>> actual = new HashSet<>();
        for (Field field : PostgresEvidenceSnapshotQueryAdapter.class.getDeclaredFields()) {
            if (!field.isSynthetic()) { assertTrue(Modifier.isFinal(field.getModifiers())); actual.add(field.getType()); }
        }
        assertEquals(expected, actual);
        assertEquals(1, PostgresEvidenceSnapshotQueryAdapter.class.getDeclaredConstructors().length);
        assertArrayEquals(new Class<?>[]{JdbcTemplate.class, EvidenceSnapshotReadPolicy.class, Clock.class},
                PostgresEvidenceSnapshotQueryAdapter.class.getDeclaredConstructors()[0].getParameterTypes());
        assertEquals(0, PostgresEvidenceSnapshotQueryAdapter.class.getDeclaredAnnotations().length);
        for (Class<?> type : expected) assertFalse(type.getName().matches(".*(Broker|Kite|RiskService|Refresh|OrderExecution|Writer).*"));
    }
}
