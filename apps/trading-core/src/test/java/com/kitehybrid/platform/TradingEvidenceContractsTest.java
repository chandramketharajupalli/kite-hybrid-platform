package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.domain.*;
import com.kitehybrid.platform.trading.application.EvidenceSnapshotQueryPort;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.strategy.domain.StrategyDefinition;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TradingEvidenceContractsTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final InstrumentId ID = new InstrumentId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final StrategyDefinition STRATEGY = new StrategyDefinition(new StrategyId("fixture"), "v1");
    private static EvidenceProvenance evidence() {
        return new EvidenceProvenance("synthetic-fixture", "a".repeat(64), NOW, NOW.plusSeconds(3600),
                EvidenceProvenance.Origin.SYNTHETIC_FIXTURE, EvidenceProvenance.Retention.SYNTHETIC_ONLY);
    }
    private static InstrumentSnapshotEvidence instrument() {
        return new InstrumentSnapshotEvidence(ID, "NSE", "FIXTURE", Optional.empty(), 1, evidence());
    }
    private static StockStrategyQualificationEvidence result(long count, long wins, long losses,
            Optional<StockStrategyQualificationEvidence.PolicyEvidence> policy) {
        return new StockStrategyQualificationEvidence(ID, STRATEGY, Duration.ofMinutes(5),
                NOW.minusSeconds(86400), NOW, count, wins, losses, "synthetic-flat-cost",
                BigDecimal.ONE, new BigDecimal("2"), evidence(), policy, evidence());
    }
    private static StockStrategyQualificationEvidence.PolicyEvidence policy(long minimum, boolean checks) {
        return new StockStrategyQualificationEvidence.PolicyEvidence("fixture-policy", "v1", "b".repeat(64),
                minimum, new BigDecimal("70"), checks);
    }

    @Test void instrumentIdentityVersionAndTimestampArePreserved() {
        var x = instrument();
        assertEquals(ID, x.instrumentId()); assertEquals(1, x.snapshotVersion());
        assertEquals(NOW, x.provenance().observedAt()); assertEquals("NSE", x.exchange());
        assertEquals("FIXTURE", x.symbol()); assertTrue(x.instrumentToken().isEmpty());
    }
    @Test void invalidInstrumentAndVersionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new InstrumentSnapshotEvidence(
                new InstrumentId(new UUID(0, 0)), "NSE", "FIXTURE", Optional.empty(), 1, evidence()));
        assertThrows(IllegalArgumentException.class, () -> new InstrumentSnapshotEvidence(
                ID, "NSE", "FIXTURE", Optional.empty(), 0, evidence()));
        assertThrows(IllegalArgumentException.class, () -> new InstrumentSnapshotEvidence(
                ID, "", "FIXTURE", Optional.empty(), 1, evidence()));
    }
    @Test void missingRetentionCannotPromoteRealInstruments() {
        var unapproved = new EvidenceProvenance("unverified", "a".repeat(64), NOW, NOW.plusSeconds(1),
                EvidenceProvenance.Origin.LOCAL_UNVERIFIED, EvidenceProvenance.Retention.NOT_AUTHORIZED);
        assertFalse(unapproved.realDataIngestionAuthorized());
        assertThrows(IllegalArgumentException.class, () -> new InstrumentSnapshotEvidence(
                ID, "NSE", "FIXTURE", Optional.of(new BrokerInstrumentId("FIXTURE", "123")), 1, unapproved));
    }
    @Test void originCannotBePromotedBySyntheticRetentionFlag() {
        var unapproved = new EvidenceProvenance("unverified", "a".repeat(64), NOW, NOW.plusSeconds(1),
                EvidenceProvenance.Origin.LOCAL_UNVERIFIED, EvidenceProvenance.Retention.SYNTHETIC_ONLY);
        assertFalse(unapproved.syntheticRetentionPermitted());
        assertThrows(IllegalArgumentException.class, unapproved::requireSyntheticRetention);
    }
    @Test void invalidTimeAndMissingProvenanceReject() {
        assertThrows(IllegalArgumentException.class, () -> new EvidenceProvenance("fixture", "a".repeat(64),
                NOW, NOW, EvidenceProvenance.Origin.SYNTHETIC_FIXTURE, EvidenceProvenance.Retention.SYNTHETIC_ONLY));
        assertThrows(NullPointerException.class, () -> new InstrumentSnapshotEvidence(ID, "NSE", "FIXTURE", Optional.empty(), 1, null));
        assertThrows(IllegalArgumentException.class, () -> new EvidenceProvenance("fixture", "bad", NOW,
                NOW.plusSeconds(1), EvidenceProvenance.Origin.SYNTHETIC_FIXTURE, EvidenceProvenance.Retention.SYNTHETIC_ONLY));
    }
    @Test void strategyCollectionsAreDefensiveAndTimeframePreserved() {
        var parameters = new HashMap<String, String>(); parameters.put("lookback", "14");
        var entries = new ArrayList<>(List.of("rsi < threshold"));
        var universe = new HashSet<>(Set.of(ID));
        var x = new StrategyDefinitionEvidence(STRATEGY, Duration.ofMinutes(5), parameters, entries,
                List.of("end of day"), universe, evidence());
        parameters.put("lookback", "99"); entries.clear(); universe.clear();
        assertEquals("14", x.parameters().get("lookback")); assertEquals(1, x.entryConditions().size());
        assertEquals(Set.of(ID), x.applicableUniverse()); assertEquals(Duration.ofMinutes(5), x.timeframe());
        assertThrows(UnsupportedOperationException.class, () -> x.parameters().put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> x.exitConditions().clear());
    }
    @Test void strategyVersionsRemainDistinct() {
        var v2 = new StrategyDefinition(new StrategyId("fixture"), "v2");
        assertNotEquals(STRATEGY, v2);
        var x = new StrategyDefinitionEvidence(v2, Duration.ofMinutes(1), Map.of(), List.of("entry"),
                List.of("exit"), Set.of(ID), evidence());
        assertEquals("v2", x.strategy().version()); assertFalse(x.tradingAuthorized());
    }
    @Test void incompleteStrategyDefinitionRejects() {
        assertThrows(IllegalArgumentException.class, () -> new StrategyDefinitionEvidence(STRATEGY,
                Duration.ZERO, Map.of(), List.of("entry"), List.of("exit"), Set.of(ID), evidence()));
        assertThrows(IllegalArgumentException.class, () -> new StrategyDefinitionEvidence(STRATEGY,
                Duration.ofMinutes(1), Map.of(), List.of(), List.of("exit"), Set.of(ID), evidence()));
    }
    @Test void qualificationCountsCannotOverflowOrDisagree() {
        assertThrows(IllegalArgumentException.class, () -> result(10, 8, 3, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> result(1, Long.MAX_VALUE, 1, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> result(-1, 0, 0, Optional.empty()));
    }
    @Test void calculatedWinRateUsesActualCounts() {
        var x = result(20, 14, 6, Optional.of(policy(10, true)));
        assertEquals(0, new BigDecimal("70").compareTo(x.winPercentage().orElseThrow()));
        assertEquals(0, new BigDecimal("33.33333333").compareTo(result(3, 1, 2, Optional.empty()).winPercentage().orElseThrow()));
        assertEquals(ID, x.instrumentId()); assertEquals(STRATEGY, x.strategy());
        assertEquals(BigDecimal.ONE, x.transactionCosts());
    }
    @Test void noTradesHasNoFabricatedPercentage() {
        assertTrue(result(0, 0, 0, Optional.empty()).winPercentage().isEmpty());
    }
    @Test void missingPolicyOrSampleCannotQualify() {
        assertEquals(TradingProjection.QualificationState.INSUFFICIENT_EVIDENCE, result(10, 10, 0, Optional.empty()).decision());
        assertEquals(TradingProjection.QualificationState.INSUFFICIENT_EVIDENCE, result(10, 10, 0, Optional.of(policy(20, true))).decision());
    }
    @Test void thresholdAloneNeverQualifiesSyntheticPerformance() {
        var x = result(20, 20, 0, Optional.of(policy(10, true)));
        assertEquals(TradingProjection.QualificationState.INSUFFICIENT_EVIDENCE, x.decision());
        assertFalse(x.tradingAuthorized());
        assertEquals(TradingProjection.QualificationState.REJECTED, result(20, 20, 0, Optional.of(policy(10, false))).decision());
        assertEquals(TradingProjection.QualificationState.REJECTED, result(20, 10, 10, Optional.of(policy(10, true))).decision());
    }
    @Test void staleEvidenceRetainsProvenanceWithoutAvailability() {
        var x = instrument();
        var stale = EvidenceAvailability.inspect(x, Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC));
        assertEquals(EvidenceAvailability.Status.STALE, stale.status());
        assertEquals(x.provenance(), stale.evidence().orElseThrow().provenance());
        assertFalse(stale.tradingAuthorized());
    }
    @Test void futureObservationIsInvalid() {
        assertEquals(EvidenceAvailability.Status.INVALID,
                EvidenceAvailability.inspect(instrument(), Clock.fixed(NOW.minusSeconds(1), ZoneOffset.UTC)).status());
    }
    @Test void accountRiskRequiresCompleteExposureAndLimit() {
        assertThrows(IllegalArgumentException.class, () -> new AccountRiskSnapshotEvidence("fixture-account", "INR",
                null, BigDecimal.TEN, Map.of(), evidence()));
        assertThrows(IllegalArgumentException.class, () -> new AccountRiskSnapshotEvidence("fixture-account", "INR",
                BigDecimal.ONE, BigDecimal.ZERO, Map.of(), evidence()));
        assertThrows(IllegalArgumentException.class, () -> new AccountRiskSnapshotEvidence("", "INR",
                BigDecimal.ONE, BigDecimal.TEN, Map.of(), evidence()));
    }
    @Test void riskUtilizationIsCalculatedAndOverLimitIsNotHidden() {
        var limits = new HashMap<String, BigDecimal>(); limits.put("drawdown", BigDecimal.ONE);
        var x = new AccountRiskSnapshotEvidence("fixture-account", "INR", new BigDecimal("12"), BigDecimal.TEN, limits, evidence());
        limits.clear(); assertEquals(1, x.additionalLimits().size());
        assertEquals(0, new BigDecimal("120").compareTo(x.utilizationPercentage()));
        assertFalse(x.tradingAuthorized());
    }
    @Test void defaultQueriesReturnMissingWithoutFallbacks() {
        var q = EvidenceSnapshotQueryPort.unavailable();
        assertEquals(EvidenceAvailability.Status.MISSING, q.instrument(ID, 1).status());
        assertTrue(q.strategy(STRATEGY).evidence().isEmpty());
        assertTrue(q.qualification(ID, STRATEGY, Duration.ofMinutes(5), NOW.minusSeconds(10), NOW, "fixture", "v1").evidence().isEmpty());
        assertTrue(q.accountRisk("fixture").evidence().isEmpty());
        assertFalse(q.accountRisk("fixture").tradingAuthorized());
    }
    @Test void queryBoundaryExposesNoWriteRefreshOrBrokerMethods() {
        var names = Arrays.stream(EvidenceSnapshotQueryPort.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("instrument", "strategy", "qualification", "accountRisk", "unavailable"), names);
        assertEquals(0, EvidenceSnapshotQueryPort.unavailable().getClass().getDeclaredFields().length);
    }
    @Test void availabilityNeverImpliesProductionAuthorization() {
        var x = EvidenceAvailability.inspect(instrument(), CLOCK);
        assertEquals(EvidenceAvailability.Status.AVAILABLE, x.status());
        assertFalse(x.tradingAuthorized()); assertFalse(x.evidence().orElseThrow().tradingAuthorized());
        assertEquals(EvidenceAvailability.Status.INVALID, EvidenceAvailability.invalid().status());
        assertEquals(EvidenceAvailability.Status.MISSING, EvidenceAvailability.inspect(null, CLOCK).status());
    }
}
