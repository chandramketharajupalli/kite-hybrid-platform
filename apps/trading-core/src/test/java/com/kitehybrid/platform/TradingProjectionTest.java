package com.kitehybrid.platform;

import com.kitehybrid.platform.trading.application.TradingProjectionService;
import com.kitehybrid.platform.trading.domain.TradingProjection;
import com.kitehybrid.platform.trading.domain.TradingProjection.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradingProjectionTest {
    private static final String HASH = "a".repeat(64);
    private static final UUID INSTRUMENT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private Evidence fixtureEvidence() {
        return new Evidence(HASH, Instant.parse("2026-01-01T00:00:00Z"), EvidenceProvenance.SYNTHETIC_FIXTURE);
    }

    @Test void absentSourcesReturnExplicitUnavailableForEveryRead() {
        var service = TradingProjectionService.unavailable();
        var results = List.of(service.instruments(), service.strategies(),
                service.qualifications(), service.riskStatus());
        assertEquals(List.of(Kind.INSTRUMENTS, Kind.STRATEGIES, Kind.QUALIFICATIONS, Kind.RISK_STATUS),
                results.stream().map(TradingProjection::kind).toList());
        for (var result : results) {
            var unavailable = assertInstanceOf(Unavailable.class, result);
            assertEquals(UnavailableReason.EVIDENCE_UNAVAILABLE, unavailable.reason());
            assertFalse(result.executionAuthorized());
        }
    }

    @Test void availableRowsAreImmutableAndEvidenceIsRequired() {
        List<Entry> entries = new ArrayList<>();
        entries.add(new InstrumentView(INSTRUMENT, "NSE", "FIXTURE", HASH, HASH));
        var result = new Available(Kind.INSTRUMENTS, entries, fixtureEvidence());
        entries.clear();
        assertEquals(1, result.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> result.entries().clear());
        assertThrows(NullPointerException.class, () -> new Available(Kind.INSTRUMENTS, List.of(), null));
        assertFalse(result.executionAuthorized());
        assertEquals(EvidenceProvenance.SYNTHETIC_FIXTURE, result.evidence().provenance());
    }

    @Test void availableEmptyCatalogIsDifferentFromUnavailableEvidence() {
        TradingProjection empty = new Available(Kind.STRATEGIES, List.of(), fixtureEvidence());
        TradingProjection missing = new Unavailable(Kind.STRATEGIES, UnavailableReason.EVIDENCE_UNAVAILABLE);
        assertNotEquals(empty, missing);
        assertTrue(assertInstanceOf(Available.class, empty).entries().isEmpty());
        assertInstanceOf(Unavailable.class, missing);
    }

    @Test void mismatchedRowsAndOversizedProjectionReject() {
        var row = new InstrumentView(INSTRUMENT, "NSE", "FIXTURE", HASH, HASH);
        assertThrows(IllegalArgumentException.class,
                () -> new Available(Kind.STRATEGIES, List.of(row), fixtureEvidence()));
        var rows = java.util.Collections.<Entry>nCopies(1001, row);
        assertThrows(IllegalArgumentException.class,
                () -> new Available(Kind.INSTRUMENTS, rows, fixtureEvidence()));
        assertThrows(IllegalArgumentException.class,
                () -> new Available(Kind.RISK_STATUS, List.of(), fixtureEvidence()));
    }

    @Test void unknownQualificationDoesNotBecomeZeroPerformanceOrQualified() {
        var result = TradingProjectionService.unavailable().qualifications();
        assertInstanceOf(Unavailable.class, result);
        var fixtureRow = new QualificationView(INSTRUMENT, "S01", "1", HASH,
                QualificationState.INSUFFICIENT_EVIDENCE);
        var supplied = new Available(Kind.QUALIFICATIONS, List.of(fixtureRow), fixtureEvidence());
        assertEquals(QualificationState.INSUFFICIENT_EVIDENCE,
                ((QualificationView) supplied.entries().get(0)).state());
        assertFalse(supplied.executionAuthorized());
        assertThrows(IllegalArgumentException.class, () -> QualificationState.valueOf("QUALIFIED"));
    }

    @Test void riskSnapshotNeverGrantsExecutionEvenWhenNotHalted() {
        var result = new Available(Kind.RISK_STATUS, List.of(new RiskView(false, HASH)), fixtureEvidence());
        assertFalse(result.executionAuthorized());
        assertFalse(((RiskView) result.entries().get(0)).halted());
    }

    @Test void malformedEvidenceAndIdentifiersReject() {
        assertThrows(IllegalArgumentException.class,
                () -> new Evidence("not-a-digest", Instant.EPOCH, EvidenceProvenance.LOCAL_UNVERIFIED));
        assertThrows(NullPointerException.class, () -> new Evidence(HASH, null, EvidenceProvenance.SYNTHETIC_FIXTURE));
        assertThrows(IllegalArgumentException.class,
                () -> new StrategyView(" ", "1", HASH, "RESEARCH_EQUIVALENT"));
        assertThrows(NullPointerException.class, () -> new Unavailable(Kind.INSTRUMENTS, null));
    }
}
