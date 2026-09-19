package com.kitehybrid.platform.instrument;

import com.kitehybrid.platform.instrument.application.RefreshInstrumentRegistryUseCase;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefreshInstrumentRegistryTest {
    private static final Instant NOW = Instant.parse("2026-09-19T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test void refreshPublishesCompleteCandidateAndReturnsAccurateCountsAndInjectedTime() {
        var registry = new InMemoryInstrumentRegistry();
        var calls = new AtomicInteger();
        var refresh = new RefreshInstrumentRegistryUseCase(() -> {
            calls.incrementAndGet();
            return InstrumentFixtures.universe(1114);
        }, registry, CLOCK);
        var result = refresh.refresh();
        assertEquals(1, calls.get());
        assertEquals(1114, result.retrievedCount());
        assertEquals(1114, result.acceptedCount());
        assertEquals(0, result.rejectedCount());
        assertEquals(1, result.snapshotVersion());
        assertEquals(NOW, result.refreshedAt());
        assertEquals(1114, registry.snapshot().size());
        assertEquals(2, refresh.refresh().snapshotVersion());
    }

    @Test void providerFailurePreservesPreviousSnapshot() {
        var registry = new InMemoryInstrumentRegistry();
        var previous = registry.replace(InstrumentFixtures.universe(2), NOW.minusSeconds(60));
        var refresh = new RefreshInstrumentRegistryUseCase(() -> {
            throw new IllegalStateException("Instrument provider unavailable");
        }, registry, CLOCK);
        assertThrows(IllegalStateException.class, refresh::refresh);
        assertSame(previous, registry.snapshot());
    }

    @Test void candidateValidationFailurePreservesPreviousSnapshotAndNextSuccessIncrementsOnce() {
        var registry = new InMemoryInstrumentRegistry();
        var previous = registry.replace(InstrumentFixtures.universe(2), NOW.minusSeconds(60));
        Instrument duplicate = InstrumentFixtures.cash("1", "INFY");
        var refresh = new RefreshInstrumentRegistryUseCase(() -> List.of(duplicate, duplicate), registry, CLOCK);
        assertThrows(IllegalArgumentException.class, refresh::refresh);
        assertSame(previous, registry.snapshot());
        var retry = new RefreshInstrumentRegistryUseCase(() -> List.of(duplicate), registry, CLOCK);
        assertEquals(2, retry.refresh().snapshotVersion());
    }

    @Test void nullOrEmptyProviderResponseFailsClosed() {
        var registry = new InMemoryInstrumentRegistry();
        var previous = registry.replace(InstrumentFixtures.universe(2), NOW);
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshInstrumentRegistryUseCase(() -> null, registry, CLOCK).refresh());
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshInstrumentRegistryUseCase(List::of, registry, CLOCK).refresh());
        assertSame(previous, registry.snapshot());
    }
}
