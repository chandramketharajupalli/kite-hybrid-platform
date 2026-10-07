package com.kitehybrid.platform;

import com.kitehybrid.platform.historical.application.*;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoricalContinuityGateTest {
    @ParameterizedTest
    @ValueSource(strings = {"missing", "universe", "window", "policy", "fingerprint", "members",
            "IDENTITY_UNRESOLVED", "CORPORATE_ACTION_UNRESOLVED", "REQUIRES_SEGMENTATION",
            "boundary", "component", "certificatePolicy", "identity"})
    @SuppressWarnings("unchecked")
    void deniedBeforeCountingProviderAndRepository(String fault) {
        var plan = MultiInstrumentCorpusTest.plan();
        var calls = new AtomicInteger();
        HistoricalMarketDataProvider provider = window -> {
            calls.incrementAndGet(); throw new AssertionError("PROVIDER_MUST_NOT_BE_CALLED");
        };
        var repository = mock(HistoricalBarRepository.class);
        var registry = mock(InstrumentRegistry.class);
        var clock = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
        var delegate = new HistoricalCorpusAcquisition(
                new HistoricalDataIngestionService(registry, provider, repository, clock), repository, clock);
        var service = new MultiInstrumentCorpusAcquisition(delegate);
        var body = ContinuityFixtures.body(plan);
        String policy = ContinuityFixtures.policy();
        var members = (List<Map<String, Object>>) body.get("members");
        switch (fault) {
            case "universe" -> body.put("universe_fingerprint", "b".repeat(64));
            case "window" -> body.put("first", "2026-02-03");
            case "policy" -> policy = policy.replace("INVALID_FOR_ORDINARY_GAP", "IGNORE");
            case "members" -> members.removeLast();
            case "component" -> members.getFirst().put("series_continuity", "UNRESOLVED");
            case "certificatePolicy" -> body.put("policy_fingerprint", "b".repeat(64));
            case "identity" -> {
                var identity = new HashMap<>((Map<String, Object>) members.getFirst().get("identity"));
                identity.put("instrument_id", new UUID(0, 999).toString());
                members.getFirst().put("identity", identity);
            }
            case "boundary" -> members.getFirst().put("classification", "CERTIFIED_WITH_DIVIDEND_NOTE");
            case "IDENTITY_UNRESOLVED", "CORPORATE_ACTION_UNRESOLVED", "REQUIRES_SEGMENTATION" ->
                    members.getFirst().put("classification", fault);
            default -> { }
        }
        String artifact = ContinuityFixtures.envelope(body);
        if (fault.equals("missing")) artifact = null;
        if (fault.equals("fingerprint")) artifact = artifact.replace("SYNTHETIC1", "TAMPERED1");
        final String suppliedPolicy = policy, suppliedCertificate = artifact;
        assertThrows(IllegalArgumentException.class,
                () -> service.acquire(plan, suppliedPolicy, suppliedCertificate));
        assertEquals(0, calls.get());
        verifyNoInteractions(repository, registry);
    }

    @Test void legacySignatureCannotBypassAndMissingPolicyDenied() {
        var delegate = mock(HistoricalCorpusAcquisition.class);
        var service = new MultiInstrumentCorpusAcquisition(delegate);
        var plan = MultiInstrumentCorpusTest.plan();
        assertThrows(IllegalArgumentException.class, () -> service.acquire(plan));
        assertThrows(IllegalArgumentException.class,
                () -> service.acquire(plan, null, ContinuityFixtures.certificate(plan)));
        verifyNoInteractions(delegate);
    }

    @Test void validArtifactPreservesSequentialSyntheticAcquisition() {
        var delegate = mock(HistoricalCorpusAcquisition.class);
        var plan = MultiInstrumentCorpusTest.plan();
        for (var member : plan.members()) when(delegate.acquire(member))
                .thenReturn(MultiInstrumentCorpusTest.result(member, false));
        var result = new MultiInstrumentCorpusAcquisition(delegate).acquire(
                plan, ContinuityFixtures.policy(), ContinuityFixtures.certificate(plan));
        assertEquals(2, result.providerCalls());
        assertEquals(750, result.inserted());
    }

    @Test @SuppressWarnings("unchecked")
    void fiveMembersWithRepresentableDividendPassButMissingVolumeSemanticsDeny() {
        var plan = new MultiInstrumentCorpusPlan("a".repeat(64),
                java.util.stream.IntStream.rangeClosed(1, 5)
                        .mapToObj(MultiInstrumentCorpusTest::member).toList(), 5);
        var body = ContinuityFixtures.body(plan);
        var first = ((List<Map<String, Object>>) body.get("members")).getFirst();
        first.put("classification", "CERTIFIED_WITH_DIVIDEND_NOTE");
        first.put("cross_session_continuity", "CERTIFIED_WITH_NOTE");
        var boundary = new TreeMap<String, Object>();
        boundary.put("instrument_id", plan.members().getFirst().instrumentId().value().toString());
        boundary.put("date", "2026-02-02");
        boundary.put("action_id", "synthetic-dividend");
        boundary.put("action", "CASH_DIVIDEND");
        boundary.put("price_continuity", false);
        boundary.put("volume_continuity", true);
        boundary.put("intraday_session_valid", true);
        boundary.put("segment_reset", false);
        first.put("boundaries", List.of(boundary));
        assertDoesNotThrow(() -> HistoricalContinuityGate.require(plan, ContinuityFixtures.policy(),
                ContinuityFixtures.envelope(body)));
        boundary.remove("volume_continuity");
        assertThrows(IllegalArgumentException.class, () -> HistoricalContinuityGate.require(plan,
                ContinuityFixtures.policy(), ContinuityFixtures.envelope(body)));
    }
}
