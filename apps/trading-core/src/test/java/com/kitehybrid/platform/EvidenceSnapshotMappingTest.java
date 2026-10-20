package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.domain.*;
import com.kitehybrid.platform.trading.infrastructure.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.kitehybrid.platform.trading.infrastructure.EvidenceSnapshotRowMapper.Kind.*;

class EvidenceSnapshotMappingTest {
    static String provenance() {
        return """
                {"sourceIdentity":"fixture","fingerprint":"%s","observedAt":"2026-10-20T00:00:00Z",
                 "expiresAt":"2026-10-20T01:00:00Z","origin":"SYNTHETIC_FIXTURE","retention":"SYNTHETIC_ONLY"}
                """.formatted("a".repeat(64));
    }
    static String account() {
        return """
                {"accountScope":"synthetic-account","currency":"INR","exposure":12.50,
                 "exposureLimit":100,"additionalLimits":{"daily":25.25},"provenance":%s}
                """.formatted(provenance());
    }
    private final EvidenceSnapshotRowMapper mapper = new EvidenceSnapshotRowMapper();

    @Test void decimalsAndImmutableLimitsSurviveMapping() {
        var value = (AccountRiskSnapshotEvidence) mapper.decode(ACCOUNT_RISK, account());
        assertEquals(0, value.exposure().compareTo(new java.math.BigDecimal("12.50")));
        assertThrows(UnsupportedOperationException.class, () -> value.additionalLimits().clear());
        assertFalse(value.tradingAuthorized());
    }
    @Test void canonicalObjectOrderAndDecimalScaleDoNotChangeHash() {
        assertEquals(EvidenceSnapshotFingerprint.sha256("{\"a\":1.00,\"b\":2}"),
                EvidenceSnapshotFingerprint.sha256("{\"b\":2,\"a\":1}"));
        assertNotEquals(EvidenceSnapshotFingerprint.sha256("{\"a\":[1,2]}"),
                EvidenceSnapshotFingerprint.sha256("{\"a\":[2,1]}"));
    }
    @Test void duplicateKeysTrailingInputAndOversizedPayloadAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EvidenceSnapshotFingerprint.parse("{\"a\":1,\"a\":2}"));
        assertThrows(IllegalArgumentException.class, () -> EvidenceSnapshotFingerprint.parse("{} {}"));
        assertThrows(IllegalArgumentException.class, () -> EvidenceSnapshotFingerprint.parse(" ".repeat(65537)));
    }
    @Test void nullNumbersAndUnknownFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> mapper.decode(ACCOUNT_RISK, account().replace("12.50", "null")));
        assertThrows(IllegalArgumentException.class, () -> mapper.decode(ACCOUNT_RISK, account().replace("\"currency\"", "\"extra\":1,\"currency\"")));
    }
    @Test void unauthorizedOriginIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.decode(ACCOUNT_RISK, account().replace("SYNTHETIC_FIXTURE", "LOCAL_UNVERIFIED")));
    }
    @Test void strategyParametersConditionsAndUniverseArePreserved() {
        String payload = """
                {"strategyId":"synthetic","strategyVersion":"v1","timeframe":"PT5M",
                 "parameters":{"threshold":"0.70","mode":"fixture"},"entryConditions":["a","b"],
                 "exitConditions":["c"],"universe":["00000000-0000-0001-0000-000000000001"],"provenance":%s}
                """.formatted(provenance());
        var value = (StrategyDefinitionEvidence) mapper.decode(STRATEGY, payload);
        assertEquals("0.70", value.parameters().get("threshold"));
        assertEquals(java.util.List.of("a", "b"), value.entryConditions());
        assertThrows(UnsupportedOperationException.class, () -> value.parameters().clear());
    }
    @Test void qualificationRemainsStockSpecificAndNeverProductionQualified() {
        String payload = """
                {"instrumentId":"00000000-0000-0001-0000-000000000001","strategyId":"synthetic",
                 "strategyVersion":"v1","timeframe":"PT5M","periodStart":"2026-10-18T00:00:00Z",
                 "periodEnd":"2026-10-19T00:00:00Z","evaluatedTrades":10,"wins":7,"losses":3,
                 "costModel":"synthetic","transactionCosts":1.5,"slippageBasisPoints":2,
                 "dataset":%s,"policy":{"policyId":"fixture","version":"v1","fingerprint":"%s",
                 "minimumTrades":10,"minimumWinPercentage":70,"remainingChecksPassed":true},"provenance":%s}
                """.formatted(provenance(), "b".repeat(64), provenance());
        var value = (StockStrategyQualificationEvidence) mapper.decode(QUALIFICATION, payload);
        assertEquals(0, value.winPercentage().orElseThrow().compareTo(new java.math.BigDecimal("70")));
        assertEquals(TradingProjection.QualificationState.INSUFFICIENT_EVIDENCE, value.decision());
        assertFalse(value.tradingAuthorized());
        assertThrows(IllegalArgumentException.class,
                () -> mapper.decode(QUALIFICATION, payload.replace("\"wins\":7", "\"wins\":8")));
    }
}
