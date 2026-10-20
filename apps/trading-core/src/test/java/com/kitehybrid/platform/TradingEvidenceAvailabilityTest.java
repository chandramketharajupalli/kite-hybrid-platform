package com.kitehybrid.platform;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.trading.domain.*;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TradingEvidenceAvailabilityTest {
    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
    private static InstrumentSnapshotEvidence fixture() {
        return new InstrumentSnapshotEvidence(new InstrumentId(new UUID(1, 1)), "FIXTURE", "SYNTHETIC",
                Optional.empty(), 1, new EvidenceProvenance("fixture", "a".repeat(64), NOW, NOW.plusSeconds(60),
                EvidenceProvenance.Origin.SYNTHETIC_FIXTURE, EvidenceProvenance.Retention.SYNTHETIC_ONLY));
    }
    @Test void incompleteIsExplicitAndNeverAuthorizesTrading() {
        var result = EvidenceAvailability.incomplete();
        assertEquals(EvidenceAvailability.Status.INCOMPLETE, result.status());
        assertTrue(result.evidence().isEmpty());
        assertFalse(result.tradingAuthorized());
    }
    @Test void existingStatusesRemainDistinct() {
        assertEquals(EvidenceAvailability.Status.MISSING, EvidenceAvailability.missing().status());
        assertEquals(EvidenceAvailability.Status.INVALID, EvidenceAvailability.invalid().status());
        var current = EvidenceAvailability.inspect(fixture(), Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(EvidenceAvailability.Status.AVAILABLE, current.status());
        assertFalse(current.tradingAuthorized());
    }
    @Test void exactExpiryIsStaleAndPreservesEvidence() {
        var result = EvidenceAvailability.inspect(fixture(), Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
        assertEquals(EvidenceAvailability.Status.STALE, result.status());
        assertTrue(result.evidence().isPresent());
    }
    @Test void futureObservationIsInvalid() {
        assertEquals(EvidenceAvailability.Status.INVALID,
                EvidenceAvailability.inspect(fixture(), Clock.fixed(NOW.minusNanos(1), ZoneOffset.UTC)).status());
    }
}
