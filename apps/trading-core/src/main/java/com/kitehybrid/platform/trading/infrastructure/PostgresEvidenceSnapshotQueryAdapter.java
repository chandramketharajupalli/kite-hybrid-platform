package com.kitehybrid.platform.trading.infrastructure;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.strategy.domain.StrategyDefinition;
import com.kitehybrid.platform.trading.application.EvidenceSnapshotQueryPort;
import com.kitehybrid.platform.trading.domain.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.kitehybrid.platform.trading.infrastructure.EvidenceSnapshotRowMapper.Kind.*;

/** SELECT-only, manually constructed adapter. No component annotation, writer or service callbacks. */
public final class PostgresEvidenceSnapshotQueryAdapter implements EvidenceSnapshotQueryPort {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final EvidenceSnapshotReadPolicy policy;
    private final Clock clock;
    private final EvidenceSnapshotRowMapper mapper = new EvidenceSnapshotRowMapper();

    public PostgresEvidenceSnapshotQueryAdapter(JdbcTemplate template, EvidenceSnapshotReadPolicy policy, Clock clock) {
        var source = Objects.requireNonNull(template.getDataSource());
        this.jdbc = new JdbcTemplate(source);
        this.jdbc.setQueryTimeout(5);
        this.jdbc.setMaxRows(1);
        var manager = new DataSourceTransactionManager(source);
        manager.setEnforceReadOnly(true);
        this.transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        transaction.setTimeout(5);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.policy = Objects.requireNonNull(policy);
        this.clock = Objects.requireNonNull(clock);
    }
    @Override public EvidenceAvailability<InstrumentSnapshotEvidence> instrument(InstrumentId id, long version) {
        if (id == null || !policy.instruments().contains(id) || version < 1) return EvidenceAvailability.invalid();
        return select(INSTRUMENT, "evidence_instrument_snapshots", "instrument_id=? AND snapshot_version=?",
                List.of(id.value(), version), InstrumentSnapshotEvidence.class,
                e -> e.instrumentId().equals(id) && e.snapshotVersion() == version, e -> false);
    }
    @Override public EvidenceAvailability<StrategyDefinitionEvidence> strategy(StrategyDefinition definition) {
        if (definition == null || !policy.strategies().contains(definition)) return EvidenceAvailability.invalid();
        return select(STRATEGY, "evidence_strategy_snapshots", "strategy_id=? AND strategy_version=?",
                List.of(definition.id().value(), definition.version()), StrategyDefinitionEvidence.class,
                e -> e.strategy().equals(definition) && policy.instruments().containsAll(e.applicableUniverse()),
                e -> false);
    }
    @Override public EvidenceAvailability<StockStrategyQualificationEvidence> qualification(InstrumentId id,
            StrategyDefinition definition, Duration timeframe, Instant start, Instant end, String policyId, String policyVersion) {
        if (id == null || definition == null || !policy.instruments().contains(id) || !policy.strategies().contains(definition)
                || timeframe == null || timeframe.isNegative() || timeframe.isZero()
                || start == null || end == null || !start.isBefore(end)
                || policyId == null || policyVersion == null) return EvidenceAvailability.invalid();
        return select(QUALIFICATION, "evidence_qualification_snapshots",
                "instrument_id=? AND strategy_id=? AND strategy_version=? AND timeframe=? "
                        + "AND period_start=? AND period_end=? AND policy_id=? AND policy_version=?",
                List.of(id.value(), definition.id().value(), definition.version(), timeframe.toString(),
                        Timestamp.from(start), Timestamp.from(end), policyId, policyVersion),
                StockStrategyQualificationEvidence.class,
                e -> e.instrumentId().equals(id) && e.strategy().equals(definition)
                        && e.timeframe().equals(timeframe) && e.periodStart().equals(start) && e.periodEnd().equals(end)
                        && e.dataset().sourceIdentity().equals(policy.sourceIdentity())
                        && (e.policy().isEmpty() || (e.policy().get().policyId().equals(policyId)
                            && e.policy().get().version().equals(policyVersion))),
                e -> e.policy().isEmpty() || e.evaluatedTrades() < e.policy().get().minimumTrades());
    }
    @Override public EvidenceAvailability<AccountRiskSnapshotEvidence> accountRisk(String accountScope) {
        if (accountScope == null || !policy.accounts().contains(accountScope)) return EvidenceAvailability.invalid();
        return select(ACCOUNT_RISK, "evidence_account_risk_snapshots", "account_scope=?",
                List.of(accountScope), AccountRiskSnapshotEvidence.class,
                e -> e.accountScope().equals(accountScope), e -> false);
    }
    private <T extends EvidenceProvenance.Snapshot> EvidenceAvailability<T> select(
            EvidenceSnapshotRowMapper.Kind kind, String table, String predicate, List<Object> keys,
            Class<T> type, Predicate<T> matches, Predicate<T> incomplete) {
        Instant now = clock.instant();
        if (!policy.permits(now)) return EvidenceAvailability.invalid();
        var args = new ArrayList<>(keys);
        args.add(policy.sourceIdentity());
        args.add(Timestamp.from(now));
        // Identifiers/predicates are private literals, never caller-provided SQL.
        String sql = "SELECT * FROM trading." + table + " WHERE " + predicate
                + " AND source_identity=? AND observed_at<=?"
                + " ORDER BY observed_at DESC, revision DESC, evidence_id DESC LIMIT 1";
        // Database/connection failures propagate. Only malformed selected records become INVALID.
        return transaction.execute(status -> {
            List<EvidenceAvailability<T>> results = jdbc.query(sql, (rs, row) -> {
                try {
                    var mapped = mapper.map(rs, kind);
                    T value = type.cast(mapped.evidence());
                    if (!mapped.authorizationReference().equals(policy.authorizationReference())
                            || !value.provenance().sourceIdentity().equals(policy.sourceIdentity())
                            || !matches.test(value)) return EvidenceAvailability.<T>invalid();
                    var available = EvidenceAvailability.inspect(value, Clock.fixed(now, ZoneOffset.UTC));
                    if (available.status() != EvidenceAvailability.Status.AVAILABLE) return available;
                    if (Duration.between(value.provenance().observedAt(), now).compareTo(policy.maximumAge()) >= 0)
                        return EvidenceAvailability.inspect(value, Clock.fixed(value.provenance().expiresAt(), ZoneOffset.UTC));
                    return incomplete.test(value) ? EvidenceAvailability.<T>incomplete() : available;
                } catch (IllegalArgumentException | NullPointerException | java.time.DateTimeException | ArithmeticException e) {
                    return EvidenceAvailability.<T>invalid();
                }
            }, args.toArray());
            return results.isEmpty() ? EvidenceAvailability.<T>missing() : results.get(0);
        });
    }
}
