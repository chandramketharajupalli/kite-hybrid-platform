package com.kitehybrid.platform.trading.infrastructure;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.strategy.domain.StrategyDefinition;
import com.kitehybrid.platform.trading.application.EvidenceSnapshotQueryPort;
import com.kitehybrid.platform.trading.application.TradingProjectionService;
import com.kitehybrid.platform.trading.domain.EvidenceAvailability;
import com.kitehybrid.platform.trading.domain.EvidenceProvenance;
import com.kitehybrid.platform.trading.domain.TradingProjection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import static com.kitehybrid.platform.trading.domain.TradingProjection.Kind;
import static com.kitehybrid.platform.trading.domain.TradingProjection.UnavailableReason;

/**
 * Manually constructed read integration, never a production bean or an access grant.
 * The query-port implementation remains responsible for persisted row authorization and
 * canonical payload integrity. Neither value is exposed by the port for independent authentication.
 * HTTP authorization belongs to the unchanged deny-only controller and filter chain.
 */
public final class EvidenceTradingProjectionAdapter implements TradingProjectionService {
    public record Scope(InstrumentId instrument, long instrumentVersion, StrategyDefinition strategy,
            Duration timeframe, Instant periodStart, Instant periodEnd, String policyId, String policyVersion,
            String account, String sourceIdentity, String authorizationReference) {
        public Scope {
            Objects.requireNonNull(instrument);
            Objects.requireNonNull(strategy);
            Objects.requireNonNull(timeframe);
            Objects.requireNonNull(periodStart);
            Objects.requireNonNull(periodEnd);
            if (instrumentVersion < 1 || timeframe.isZero() || timeframe.isNegative()
                    || !periodStart.isBefore(periodEnd)) throw new IllegalArgumentException("Invalid exact scope");
            policyId = EvidenceSnapshotReadPolicy.text(policyId);
            policyVersion = EvidenceSnapshotReadPolicy.text(policyVersion);
            account = EvidenceSnapshotReadPolicy.text(account);
            sourceIdentity = EvidenceSnapshotReadPolicy.text(sourceIdentity);
            authorizationReference = EvidenceSnapshotReadPolicy.text(authorizationReference);
        }
    }

    private final EvidenceSnapshotQueryPort queries;
    private final EvidenceSnapshotReadPolicy policy;
    private final Clock clock;
    private final Scope scope;

    public EvidenceTradingProjectionAdapter(EvidenceSnapshotQueryPort queries, EvidenceSnapshotReadPolicy policy,
            Clock clock, Scope scope) {
        this.queries = Objects.requireNonNull(queries);
        this.policy = Objects.requireNonNull(policy);
        this.clock = Objects.requireNonNull(clock);
        this.scope = Objects.requireNonNull(scope);
        if (!scope.sourceIdentity().equals(policy.sourceIdentity())
                || !scope.authorizationReference().equals(policy.authorizationReference()))
            throw new IllegalArgumentException("Independent source authorization scope mismatch");
    }

    @Override public TradingProjection instruments() {
        return read(Kind.INSTRUMENTS, policy.instruments().contains(scope.instrument()),
                () -> queries.instrument(scope.instrument(), scope.instrumentVersion()),
                e -> e.instrumentId().equals(scope.instrument()) && e.snapshotVersion() == scope.instrumentVersion(),
                e -> false, TradingProjection.InstrumentEvidenceView::new);
    }

    @Override public TradingProjection strategies() {
        return read(Kind.STRATEGIES, policy.strategies().contains(scope.strategy()),
                () -> queries.strategy(scope.strategy()),
                e -> e.strategy().equals(scope.strategy()) && e.timeframe().equals(scope.timeframe())
                        && policy.instruments().containsAll(e.applicableUniverse()),
                e -> false, TradingProjection.StrategyEvidenceView::new);
    }

    @Override public TradingProjection qualifications() {
        return read(Kind.QUALIFICATIONS, policy.instruments().contains(scope.instrument())
                        && policy.strategies().contains(scope.strategy()),
                () -> queries.qualification(scope.instrument(), scope.strategy(), scope.timeframe(),
                        scope.periodStart(), scope.periodEnd(), scope.policyId(), scope.policyVersion()),
                e -> e.instrumentId().equals(scope.instrument()) && e.strategy().equals(scope.strategy())
                        && e.timeframe().equals(scope.timeframe()) && e.periodStart().equals(scope.periodStart())
                        && e.periodEnd().equals(scope.periodEnd())
                        && e.dataset().sourceIdentity().equals(scope.sourceIdentity())
                        && e.dataset().syntheticRetentionPermitted()
                        && (e.policy().isEmpty() || (e.policy().get().policyId().equals(scope.policyId())
                            && e.policy().get().version().equals(scope.policyVersion()))),
                e -> e.policy().isEmpty() || e.evaluatedTrades() < e.policy().get().minimumTrades(),
                TradingProjection.QualificationEvidenceView::new);
    }

    @Override public TradingProjection riskStatus() {
        return read(Kind.RISK_STATUS, policy.accounts().contains(scope.account()),
                () -> queries.accountRisk(scope.account()), e -> e.accountScope().equals(scope.account()),
                e -> false, TradingProjection.AccountRiskEvidenceView::new);
    }

    private <T extends EvidenceProvenance.Snapshot> TradingProjection read(Kind kind, boolean permitted,
            Supplier<EvidenceAvailability<T>> query, Predicate<T> matches, Predicate<T> incomplete,
            Function<T, TradingProjection.Entry> entry) {
        EvidenceAvailability<T> supplied;
        try {
            if (!permitted || !policy.permits(clock.instant()))
                return unavailable(kind, UnavailableReason.UNVERIFIED_EVIDENCE);
            supplied = query.get(); // One exact read; no retries, enumeration or fallback.
        } catch (RuntimeException unavailable) {
            return unavailable(kind, UnavailableReason.SOURCE_UNAVAILABLE);
        }
        if (supplied == null) return unavailable(kind, UnavailableReason.UNVERIFIED_EVIDENCE);
        return switch (supplied.status()) {
            case MISSING -> unavailable(kind, UnavailableReason.EVIDENCE_UNAVAILABLE);
            case STALE -> unavailable(kind, UnavailableReason.STALE_EVIDENCE);
            case INCOMPLETE -> unavailable(kind, UnavailableReason.INCOMPLETE_EVIDENCE);
            case INVALID -> unavailable(kind, UnavailableReason.UNVERIFIED_EVIDENCE);
            case AVAILABLE -> {
                try {
                    T value = supplied.evidence().orElseThrow();
                    var provenance = value.provenance();
                    Instant now = clock.instant();
                    if (!policy.permits(now) || !provenance.syntheticRetentionPermitted()
                            || !provenance.sourceIdentity().equals(scope.sourceIdentity())
                            || now.isBefore(provenance.observedAt()) || !matches.test(value))
                        yield unavailable(kind, UnavailableReason.UNVERIFIED_EVIDENCE);
                    if (!now.isBefore(provenance.expiresAt())
                            || Duration.between(provenance.observedAt(), now).compareTo(policy.maximumAge()) >= 0)
                        yield unavailable(kind, UnavailableReason.STALE_EVIDENCE);
                    if (incomplete.test(value)) yield unavailable(kind, UnavailableReason.INCOMPLETE_EVIDENCE);
                    yield new TradingProjection.Available(kind, List.of(entry.apply(value)),
                            new TradingProjection.Evidence(provenance.fingerprint(), provenance.observedAt(),
                                    TradingProjection.EvidenceProvenance.valueOf(provenance.origin().name())));
                } catch (RuntimeException malformed) {
                    yield unavailable(kind, UnavailableReason.UNVERIFIED_EVIDENCE);
                }
            }
        };
    }

    private static TradingProjection unavailable(Kind kind, UnavailableReason reason) {
        return new TradingProjection.Unavailable(kind, reason);
    }
}
