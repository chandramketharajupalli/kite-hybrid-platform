package com.kitehybrid.platform;

import com.kitehybrid.platform.historical.application.*;
import com.kitehybrid.platform.historical.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiInstrumentCorpusTest {
    static final LocalDate FIRST = LocalDate.of(2026, 2, 2);
    static HistoricalCorpusPlan member(int id) {
        return new HistoricalCorpusPlan(new InstrumentId(new UUID(0, id)), FIRST, FIRST.plusDays(1),
                HistoricalCorpusPlanTest.calendar(Map.of(FIRST, HistoricalCorpusPlanTest.NORMAL)), 1);
    }
    static MultiInstrumentCorpusPlan plan() {
        return new MultiInstrumentCorpusPlan("a".repeat(64), List.of(member(2), member(1)), 2);
    }
    static HistoricalCorpusAcquisition.Result result(HistoricalCorpusPlan member, boolean reused) {
        var chunk = member.chunks().getFirst();
        var query = new HistoricalBarRepository.Query(chunk.window(), chunk.window().to(), chunk.window().to());
        var dataset = new HistoricalBarRepository.Dataset(query, List.of(), List.of(), "b".repeat(64));
        return new HistoricalCorpusAcquisition.Result(member.fingerprint(),
                List.of(new HistoricalCorpusAcquisition.Session(chunk, dataset, reused)),
                reused ? 0 : 1, reused ? 0 : 375, "b".repeat(64));
    }
    @Test void canonicalPlanIsBoundedImmutableAndSensitiveToMembership() {
        var p = plan();
        assertEquals(2, p.expectedRequests());
        assertEquals(p.fingerprint(), new MultiInstrumentCorpusPlan("a".repeat(64), List.of(member(1), member(2)), 2).fingerprint());
        assertNotEquals(p.fingerprint(), new MultiInstrumentCorpusPlan("a".repeat(64), List.of(member(1), member(3)), 2).fingerprint());
        assertThrows(UnsupportedOperationException.class, () -> p.members().clear());
        assertThrows(IllegalArgumentException.class, () -> new MultiInstrumentCorpusPlan("a".repeat(64), List.of(member(1), member(1)), 2));
        assertThrows(IllegalArgumentException.class, () -> new MultiInstrumentCorpusPlan("a".repeat(64), List.of(member(1), member(2)), 1));
    }
    @Test void mismatchedCalendarOrWindowAndJulyDeniedBeforeAcquisition() {
        var original = member(2);
        var changed = new HistoricalCorpusPlan(original.instrumentId(), FIRST, FIRST.plusDays(1),
                new TradingCalendar("changed", "synthetic", Map.of(FIRST, HistoricalCorpusPlanTest.NORMAL)), 1);
        assertThrows(IllegalArgumentException.class, () -> new MultiInstrumentCorpusPlan("a".repeat(64), List.of(member(1), changed), 2));
        var july = LocalDate.of(2026, 7, 1);
        var late = new HistoricalCorpusPlan(original.instrumentId(), july, july.plusDays(1),
                HistoricalCorpusPlanTest.calendar(Map.of(july, HistoricalCorpusPlanTest.NORMAL)), 1);
        assertThrows(IllegalArgumentException.class, () -> new MultiInstrumentCorpusPlan("a".repeat(64), List.of(member(1), late), 2));
    }
    @Test void sequentialFailureStopsWithoutDroppingMemberAndRestartReusesEarlierWork() {
        var delegate = mock(HistoricalCorpusAcquisition.class);
        var service = new MultiInstrumentCorpusAcquisition(delegate);
        var p = plan();
        var first = p.members().getFirst(); var second = p.members().getLast();
        when(delegate.acquire(first)).thenReturn(result(first, false), result(first, true));
        when(delegate.acquire(second)).thenThrow(new IllegalStateException("PARTIAL_SESSION"))
                .thenReturn(result(second, false));
        var policy = ContinuityFixtures.policy(); var certificate = ContinuityFixtures.certificate(p);
        assertThrows(IllegalStateException.class, () -> service.acquire(p, policy, certificate));
        var resumed = service.acquire(p, policy, certificate);
        assertEquals(1, resumed.providerCalls()); assertEquals(375, resumed.inserted());
        assertEquals(2, resumed.members().size());
        when(delegate.acquire(second)).thenReturn(result(second, true));
        var replay = service.acquire(p, policy, certificate);
        assertEquals(0, replay.providerCalls()); assertEquals(0, replay.inserted());
        assertEquals(resumed.aggregateFingerprint(), replay.aggregateFingerprint());
        var order = inOrder(delegate);
        order.verify(delegate).acquire(first); order.verify(delegate).acquire(second);
        order.verify(delegate).acquire(first); order.verify(delegate).acquire(second);
        order.verify(delegate).acquire(first); order.verify(delegate).acquire(second);
    }
}
