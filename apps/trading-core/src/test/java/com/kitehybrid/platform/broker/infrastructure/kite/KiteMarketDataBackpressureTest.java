package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.marketdata.application.LatestMarketDataStore;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.marketdata.infrastructure.InMemoryLatestMarketDataStore;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.util.Map;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static com.kitehybrid.platform.broker.infrastructure.kite.KiteMarketDataTestSupport.*;
import static com.kitehybrid.platform.marketdata.application.MarketDataHealth.Reason.*;
import static com.kitehybrid.platform.marketdata.application.MarketDataHealth.Status.*;
import static org.assertj.core.api.Assertions.*;

class KiteMarketDataBackpressureTest {
    @Test void boundedQueueRejectsNewestExcessAndCountsEveryDroppedTick() {
        try (var rig = new TestRig(2, 2, null)) {
            rig.connect();
            rig.frame(frame(ltpPacket(408065, 100), ltpPacket(408065, 101), ltpPacket(408065, 102), ltpPacket(408065, 103)));
            assertThat(rig.gateway.health().queuedEvents()).isEqualTo(2);
            assertThat(rig.gateway.health().eventsDropped()).isEqualTo(2);
            assertThat(rig.gateway.health().ticksReceived()).isEqualTo(4);
            assertThat(rig.gateway.health().status()).isEqualTo(DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(BACKPRESSURE);
            assertThat(rig.metrics.get("marketdata.events.dropped").counter().count()).isEqualTo(2);
            assertThat(rig.metrics.get("marketdata.queue.size").gauge().value()).isEqualTo(2);
            assertThat(rig.worker.tasks).hasSize(1);
            rig.worker.runAll();
            assertThat(rig.gateway.health().queuedEvents()).isZero();
            assertThat(rig.store.latest(A.id()).orElseThrow().lastPrice()).isEqualByComparingTo("1.01");
            assertThat(rig.gateway.health().status()).isEqualTo(DEGRADED);
        }
    }

    @Test void blockedConsumerCannotBlockReceiveThreadOrGrowQueue() throws Exception {
        var blocked = new BlockingStore();
        try (var rig = new TestRig(1, 2, blocked); var threads = Executors.newFixedThreadPool(2)) {
            rig.connect();
            rig.frame(frame(ltpPacket(408065, 100)));
            var processing = threads.submit(rig.worker::runAll);
            try {
                assertThat(blocked.entered.await(1, TimeUnit.SECONDS)).isTrue();
                var received = threads.submit(() -> {
                    rig.frame(frame(ltpPacket(408065, 101)));
                    rig.frame(frame(ltpPacket(408065, 102)));
                    return rig.gateway.health();
                });
                var health = received.get(1, TimeUnit.SECONDS);
                assertThat(health.queuedEvents()).isEqualTo(1);
                assertThat(health.eventsDropped()).isEqualTo(1);
                assertThat(health.reason()).isEqualTo(BACKPRESSURE);
                assertThat(rig.metrics.get("marketdata.events.dropped").counter().count()).isEqualTo(1);
            } finally { blocked.release.countDown(); }
            processing.get(1, TimeUnit.SECONDS);
            assertThat(blocked.latest(A.id()).orElseThrow().lastPrice()).isEqualByComparingTo("1.01");
        }
    }

    @Test void processingFailureIsObservableAndDoesNotKillSubsequentDrain() {
        var store = new InMemoryLatestMarketDataStore();
        LatestMarketDataStore onceFailing = new LatestMarketDataStore() {
            boolean first = true;
            @Override public boolean update(Tick tick) {
                if (first) { first = false; throw new IllegalStateException("simulated processing failure"); }
                return store.update(tick);
            }
            @Override public Optional<Tick> latest(InstrumentId id) { return store.latest(id); }
            @Override public Map<InstrumentId, Tick> snapshot(Set<InstrumentId> ids) { return store.snapshot(ids); }
        };
        try (var rig = new TestRig(2, 2, onceFailing)) {
            rig.connect();
            rig.frame(frame(ltpPacket(408065, 100), ltpPacket(408065, 101))); rig.worker.runAll();
            assertThat(rig.gateway.health().status()).isEqualTo(DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(PROCESSING_FAILURE);
            assertThat(store.latest(A.id()).orElseThrow().lastPrice()).isEqualByComparingTo("1.01");
            assertThat(rig.gateway.health().queuedEvents()).isZero();
        }
    }

    private static byte[] ltpPacket(int token, int price) {
        return ByteBuffer.allocate(8).putInt(token).putInt(price).array();
    }

    private static final class BlockingStore implements LatestMarketDataStore {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final InMemoryLatestMarketDataStore delegate = new InMemoryLatestMarketDataStore();
        @Override public boolean update(Tick tick) {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test consumer timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test consumer interrupted");
            }
            return delegate.update(tick);
        }
        @Override public Optional<Tick> latest(InstrumentId id) { return delegate.latest(id); }
        @Override public Map<InstrumentId, Tick> snapshot(Set<InstrumentId> ids) { return delegate.snapshot(ids); }
    }
}
