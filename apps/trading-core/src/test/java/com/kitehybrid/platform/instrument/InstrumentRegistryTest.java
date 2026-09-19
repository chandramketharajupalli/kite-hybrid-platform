package com.kitehybrid.platform.instrument;

import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.ExchangeSymbol;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstrumentRegistryTest {
    private static final Instant NOW = Instant.parse("2026-09-19T10:15:30Z");

    @Test void startsWithAnExplicitEmptyUnrefreshedSnapshot() {
        var registry = new InMemoryInstrumentRegistry();
        assertEquals(0, registry.snapshot().size());
        assertEquals(0, registry.snapshot().version());
        assertEquals(Instant.EPOCH, registry.snapshot().refreshedAt());
        assertTrue(registry.findByExchangeAndSymbol("NSE", "INFY").isEmpty());
        assertTrue(registry.findById(InstrumentFixtures.cash("1", "INFY").id()).isEmpty());
        assertTrue(registry.resolve(new BrokerInstrumentId("KITE", "1")).isEmpty());
    }

    @Test void builds1114InstrumentUniverseAndResolvesEveryIndexThenRejectsDuplicate() {
        var registry = new InMemoryInstrumentRegistry();
        List<Instrument> universe = InstrumentFixtures.universe(1114);
        var snapshot = registry.replace(universe, NOW);
        assertEquals(1114, snapshot.size());
        assertEquals(1, snapshot.version());
        assertEquals(NOW, snapshot.refreshedAt());
        for (Instrument instrument : universe) {
            assertEquals(Optional.of(instrument), registry.findById(instrument.id()));
            assertEquals(Optional.of(instrument), registry.findByBrokerId(instrument.brokerId()));
            assertEquals(Optional.of(instrument), registry.findByExchangeAndSymbol(" nse ",
                    " " + instrument.tradingSymbol().toLowerCase(java.util.Locale.ROOT) + " "));
            assertEquals(Optional.of(instrument.id()), registry.resolve(instrument.brokerId()));
        }
        var duplicate = new ArrayList<>(universe);
        duplicate.add(universe.getLast());
        assertThrows(IllegalArgumentException.class, () -> registry.replace(duplicate, NOW.plusSeconds(1)));
        assertSame(snapshot, registry.snapshot());
    }

    @Test void rejectsAllThreeClassesOfConflictsWithoutPublishingAnyOfThem() {
        var registry = new InMemoryInstrumentRegistry();
        Instrument first = InstrumentFixtures.cash("1", "INFY");
        var previous = registry.replace(List.of(first), NOW);
        var duplicateId = assertThrows(IllegalArgumentException.class, () -> registry.replace(
                List.of(first, InstrumentFixtures.cash("2", "infy")), NOW));
        assertTrue(duplicateId.getMessage().contains("platform instrument ID"));
        var duplicateBroker = assertThrows(IllegalArgumentException.class, () -> registry.replace(
                List.of(first, InstrumentFixtures.cash("1", "TCS")), NOW));
        assertTrue(duplicateBroker.getMessage().contains("broker instrument ID"));
        Instrument conflictingSymbol = Instrument.create(new BrokerInstrumentId("KITE", "2"), "INFY", "NSE",
                "INDICES", InstrumentType.INDEX, Optional.empty(), Optional.empty(), BigDecimal.ZERO, 0);
        var conflict = assertThrows(IllegalArgumentException.class, () -> registry.replace(
                List.of(first, conflictingSymbol), NOW));
        assertTrue(conflict.getMessage().contains("exchange and symbol"));
        assertSame(previous, registry.snapshot());
    }

    @Test void brokerIdentifiersAreScopedByBroker() {
        Instrument first = InstrumentFixtures.cash("1", "INFY");
        Instrument second = Instrument.create(new BrokerInstrumentId("OTHER", "1"), "TCS", "NSE", "CASH",
                InstrumentType.CASH, Optional.empty(), Optional.empty(), BigDecimal.ONE, 1);
        var registry = new InMemoryInstrumentRegistry();
        registry.replace(List.of(first, second), NOW);
        assertEquals(Optional.of(first), registry.findByBrokerId(new BrokerInstrumentId("KITE", "1")));
        assertEquals(Optional.of(second), registry.findByBrokerId(new BrokerInstrumentId("OTHER", "1")));
    }

    @Test void invalidOrEmptyCandidateCannotClearLastValidSnapshot() {
        var registry = new InMemoryInstrumentRegistry();
        var previous = registry.replace(InstrumentFixtures.universe(2), NOW);
        assertThrows(IllegalArgumentException.class, () -> registry.replace(List.of(), NOW));
        assertThrows(IllegalArgumentException.class, () -> registry.replace(null, NOW));
        assertThrows(IllegalArgumentException.class, () -> registry.replace(Arrays.asList((Instrument) null), NOW));
        assertThrows(NullPointerException.class, () -> registry.replace(InstrumentFixtures.universe(1), null));
        assertSame(previous, registry.snapshot());
    }

    @Test void replacesAllIndexesAndRetainedSnapshotsAreImmutable() {
        var registry = new InMemoryInstrumentRegistry();
        Instrument first = InstrumentFixtures.cash("1", "INFY");
        var source = new ArrayList<>(List.of(first));
        var previous = registry.replace(source, NOW);
        source.clear();
        assertEquals(1, previous.size());
        assertThrows(UnsupportedOperationException.class, () -> previous.byId().clear());
        assertThrows(UnsupportedOperationException.class, () -> previous.byBrokerId().clear());
        assertThrows(UnsupportedOperationException.class, () -> previous.byExchangeAndSymbol().clear());
        Instrument second = InstrumentFixtures.cash("2", "TCS");
        var next = registry.replace(List.of(second), NOW.plusSeconds(1));
        assertEquals(2, next.version());
        assertEquals(NOW.plusSeconds(1), next.refreshedAt());
        assertEquals(first, previous.byId().get(first.id()));
        assertEquals(first, previous.byBrokerId().get(first.brokerId()));
        assertEquals(first, previous.byExchangeAndSymbol().get(new ExchangeSymbol("NSE", "INFY")));
        assertTrue(registry.findById(first.id()).isEmpty());
        assertTrue(registry.findByBrokerId(first.brokerId()).isEmpty());
        assertTrue(registry.findByExchangeAndSymbol("NSE", "INFY").isEmpty());
        assertEquals(Optional.of(second), registry.findById(second.id()));
    }

    @Test void concurrentPublicationHasNoLostVersionAndReadersObserveConsistentSnapshots() throws Exception {
        var registry = new InMemoryInstrumentRegistry();
        List<Instrument> large = InstrumentFixtures.universe(1114);
        List<Instrument> small = InstrumentFixtures.universe(3);
        var start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(3)) {
            var writerOne = threads.submit(() -> {
                await(start);
                for (int i = 0; i < 25; i++) registry.replace(large, NOW);
            });
            var writerTwo = threads.submit(() -> {
                await(start);
                for (int i = 0; i < 25; i++) registry.replace(small, NOW);
            });
            var reader = threads.submit(() -> {
                await(start);
                while (!writerOne.isDone() || !writerTwo.isDone()) {
                    var observed = registry.snapshot();
                    assertEquals(observed.size(), observed.byBrokerId().size());
                    assertEquals(observed.size(), observed.byExchangeAndSymbol().size());
                    for (Instrument instrument : observed.byId().values()) {
                        assertSame(instrument, observed.byBrokerId().get(instrument.brokerId()));
                        assertSame(instrument, observed.byExchangeAndSymbol().get(
                                new ExchangeSymbol(instrument.exchange(), instrument.tradingSymbol())));
                    }
                }
            });
            start.countDown();
            writerOne.get(10, TimeUnit.SECONDS);
            writerTwo.get(10, TimeUnit.SECONDS);
            reader.get(10, TimeUnit.SECONDS);
        }
        assertEquals(50, registry.snapshot().version());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test start");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test interrupted");
        }
    }
}
