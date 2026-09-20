package com.kitehybrid.platform.marketdata;

import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.marketdata.infrastructure.InMemoryLatestMarketDataStore;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LatestMarketDataStoreTest {
    private static final InstrumentId FIRST = new InstrumentId(new UUID(0, 1));
    private static final InstrumentId SECOND = new InstrumentId(new UUID(0, 2));
    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    @Test void storesFirstTickAndReturnsEmptyForUnseenInstruments() {
        var store = new InMemoryLatestMarketDataStore();
        assertTrue(store.latest(FIRST).isEmpty());
        assertTrue(store.snapshot(Set.of(FIRST)).isEmpty());
        Tick first = tick(FIRST, 1);
        assertTrue(store.update(first));
        assertSame(first, store.latest(FIRST).orElseThrow());
        assertTrue(store.latest(SECOND).isEmpty());
    }

    @Test void receiveTimeOrdersTicksWithoutExchangeTimeAndDuplicatesAreRejected() {
        var store = new InMemoryLatestMarketDataStore();
        Tick latest = tick(FIRST, 3);
        assertTrue(store.update(tick(FIRST, 2)));
        assertTrue(store.update(latest));
        assertFalse(store.update(tick(FIRST, 1)));
        assertFalse(store.update(latest));
        assertSame(latest, store.latest(FIRST).orElseThrow());
    }

    @Test void exchangeTimeRejectsDelayedOldTicksDespiteNewerReceiveTime() {
        var store = new InMemoryLatestMarketDataStore();
        Tick current = timed(2, 2);
        assertTrue(store.update(current));
        assertFalse(store.update(timed(1, 3)));
        assertSame(current, store.latest(FIRST).orElseThrow());
        Tick newerExchange = timed(3, 1);
        assertTrue(store.update(newerExchange));
        assertSame(newerExchange, store.latest(FIRST).orElseThrow());
    }

    @Test void exchangeTimeTiesUseReceiveTimeThenPublicationOrderForDistinctTicks() {
        var store = new InMemoryLatestMarketDataStore();
        assertTrue(store.update(timed(2, 2)));
        Tick latest = timed(2, 3);
        assertTrue(store.update(latest));
        assertFalse(store.update(timed(2, 1)));
        Tick sameTimeLaterPrice = new Tick(FIRST, BigDecimal.TEN, latest.receivedAt(),
                latest.exchangeTimestamp(), Optional.empty(), Optional.empty());
        assertTrue(store.update(sameTimeLaterPrice));
        assertSame(sameTimeLaterPrice, store.latest(FIRST).orElseThrow());
        assertFalse(store.update(sameTimeLaterPrice));
    }

    @Test void twoLtpPacketsForSameInstrumentInOneFrameKeepLastPublishedTick() {
        var store = new InMemoryLatestMarketDataStore();
        Tick first = new Tick(FIRST, BigDecimal.ONE, NOW);
        Tick last = new Tick(FIRST, BigDecimal.TEN, NOW);
        assertTrue(store.update(first));
        assertTrue(store.update(last));
        assertSame(last, store.latest(FIRST).orElseThrow());
    }

    @Test void missingExchangeTimeOnEitherSideFallsBackToReceiveTime() {
        var store = new InMemoryLatestMarketDataStore();
        assertTrue(store.update(timed(1, 3)));
        assertFalse(store.update(tick(FIRST, 2)));
        assertTrue(store.update(tick(FIRST, 4)));
        assertFalse(store.update(timed(10, 3)));
        Tick latest = timed(2, 5);
        assertTrue(store.update(latest));
        assertSame(latest, store.latest(FIRST).orElseThrow());
    }

    @Test void snapshotContainsOnlyRequestedKnownInstrumentsAndIsImmutableAndDetached() {
        var store = new InMemoryLatestMarketDataStore();
        Tick first = tick(FIRST, 1);
        Tick second = tick(SECOND, 1);
        store.update(first);
        store.update(second);
        assertEquals(Map.of(FIRST, first), store.snapshot(Set.of(FIRST)));
        var snapshot = store.snapshot(Set.of(FIRST, SECOND, new InstrumentId(new UUID(0, 3))));
        assertEquals(Map.of(FIRST, first, SECOND, second), snapshot);
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        store.update(tick(FIRST, 2));
        assertSame(first, snapshot.get(FIRST));
        assertTrue(store.snapshot(Set.of()).isEmpty());
    }

    @Test void concurrentWritersCannotLoseNewerValuesAndReadersSeeWholeImmutableTicks() throws Exception {
        var store = new InMemoryLatestMarketDataStore();
        var start = new CountDownLatch(1);
        int writers = 4;
        int updates = 1000;
        try (var threads = Executors.newFixedThreadPool(writers + 1)) {
            var tasks = new ArrayList<Future<?>>();
            for (int writer = 0; writer < writers; writer++) {
                int offset = writer;
                tasks.add(threads.submit(() -> {
                    await(start);
                    for (int i = updates; i > 0; i--) {
                        int sequence = (i - 1) * writers + offset + 1;
                        store.update(tick(FIRST, sequence));
                        store.update(tick(SECOND, sequence));
                    }
                }));
            }
            var reader = threads.submit(() -> {
                await(start);
                Instant lastSeen = Instant.MIN;
                for (int i = 0; i < updates * writers; i++) {
                    var current = store.latest(FIRST);
                    if (current.isPresent()) {
                        Tick value = current.orElseThrow();
                        assertFalse(value.receivedAt().isBefore(lastSeen));
                        assertEquals(NOW.plusSeconds(value.lastPrice().longValueExact()), value.receivedAt());
                        lastSeen = value.receivedAt();
                    }
                    store.snapshot(Set.of(FIRST, SECOND)).forEach((instrumentId, value) -> {
                        assertEquals(instrumentId, value.instrumentId());
                        assertEquals(NOW.plusSeconds(value.lastPrice().longValueExact()), value.receivedAt());
                    });
                }
            });
            start.countDown();
            for (Future<?> task : tasks) task.get(10, TimeUnit.SECONDS);
            reader.get(10, TimeUnit.SECONDS);
        }
        assertEquals(tick(FIRST, writers * updates), store.latest(FIRST).orElseThrow());
        assertEquals(tick(SECOND, writers * updates), store.latest(SECOND).orElseThrow());
    }

    @Test void rejectsNullInput() {
        var store = new InMemoryLatestMarketDataStore();
        assertThrows(NullPointerException.class, () -> store.update(null));
        assertThrows(NullPointerException.class, () -> store.latest(null));
        assertThrows(NullPointerException.class, () -> store.snapshot(null));
    }

    private static Tick tick(InstrumentId instrumentId, int receivedOffset) {
        return new Tick(instrumentId, BigDecimal.valueOf(receivedOffset), NOW.plusSeconds(receivedOffset));
    }

    private static Tick timed(int exchangeOffset, int receivedOffset) {
        return new Tick(FIRST, BigDecimal.valueOf(receivedOffset), NOW.plusSeconds(receivedOffset),
                Optional.of(NOW.plusSeconds(exchangeOffset)), Optional.empty(), Optional.empty());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Test start timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test interrupted", interrupted);
        }
    }
}
