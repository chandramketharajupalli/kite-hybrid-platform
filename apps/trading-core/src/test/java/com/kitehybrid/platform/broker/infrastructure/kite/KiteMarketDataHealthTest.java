package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.marketdata.application.MarketDataGateway;
import com.kitehybrid.platform.marketdata.domain.StreamMode;
import java.time.Duration;
import java.time.Instant;
import java.nio.ByteBuffer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static com.kitehybrid.platform.broker.infrastructure.kite.KiteMarketDataTestSupport.*;
import static com.kitehybrid.platform.marketdata.application.MarketDataHealth.Reason.*;
import static com.kitehybrid.platform.marketdata.application.MarketDataHealth.Status.*;
import static org.assertj.core.api.Assertions.*;

class KiteMarketDataHealthTest {
    @Test void connectionAloneIsNotFreshAndProcessingMustCompleteBeforeFreshness() {
        try (var rig = new TestRig()) {
            assertThat(rig.gateway.health().status()).isEqualTo(STOPPED);
            rig.gateway.subscribe(Set.of(A.id()));
            rig.gateway.start();
            assertThat(rig.gateway.health().status()).isEqualTo(STARTING);
            rig.transport.open();
            assertThat(rig.gateway.health().status()).isEqualTo(NO_DATA);
            assertThat(rig.gateway.health().connectedAt()).contains(rig.clock.instant());
            assertThat(rig.gateway.health().lastTickAt()).isEmpty();
            rig.frame(frame(ltpPacket(408065, 12345)));
            assertThat(rig.gateway.health().status()).isEqualTo(NO_DATA);
            rig.worker.runAll();
            assertThat(rig.gateway.health().status()).isEqualTo(FRESH);
            assertThat(rig.gateway.health().lastTickAt()).contains(rig.clock.instant());
            assertThat(rig.gateway.health().desiredSubscriptions()).isEqualTo(1);
            assertThat(rig.gateway.health().activeSubscriptions()).isEqualTo(1);
        }
    }

    @Test void emptySubscriptionSetIsNoDataEvenWithConnectedSocketAndHeartbeats() {
        try (var rig = new TestRig()) {
            rig.gateway.start(); rig.transport.open();
            rig.frame(new byte[]{0});
            assertThat(rig.gateway.health().status()).isEqualTo(NO_DATA);
            assertThat(rig.gateway.health().framesReceived()).isEqualTo(1);
            assertThat(rig.gateway.health().ticksReceived()).isZero();
        }
    }

    @Test void everyDesiredInstrumentMustHaveDataBeforeFreshness() {
        try (var rig = new TestRig()) {
            rig.connect();
            rig.gateway.subscribe(Set.of(B.id()));
            rig.frame(frame(ltpPacket(408065, 100))); rig.worker.runAll();
            assertThat(rig.gateway.health().status()).isEqualTo(NO_DATA);
            rig.clock.now = rig.clock.instant().plusSeconds(31);
            rig.frame(frame(ltpPacket(408065, 101))); rig.worker.runAll();
            assertThat(rig.gateway.health().status()).isEqualTo(NO_DATA);
            rig.frame(frame(ltpPacket(884737, 200))); rig.worker.runAll();
            assertThat(rig.gateway.health().status()).isEqualTo(FRESH);
        }
    }

    @Test void oneStaleInstrumentCannotBeHiddenByAnotherFreshInstrument() {
        try (var rig = new TestRig()) {
            rig.connect(); rig.gateway.subscribe(Set.of(B.id()));
            rig.frame(frame(ltpPacket(408065, 100), ltpPacket(884737, 200))); rig.worker.runAll();
            assertThat(rig.gateway.health().status()).isEqualTo(FRESH);
            rig.clock.now = rig.clock.instant().plusSeconds(31);
            rig.frame(frame(ltpPacket(408065, 101))); rig.worker.runAll();
            assertThat(rig.gateway.health().status()).isEqualTo(STALE);
            rig.gateway.unsubscribe(Set.of(B.id()));
            assertThat(rig.gateway.health().status()).isEqualTo(FRESH);
        }
    }

    @Test void heartbeatUpdatesLastMessageButNeverLastTickOrFreshness() {
        try (var rig = new TestRig()) {
            rig.connect();
            rig.frame(frame(ltpPacket(408065, 100))); rig.worker.runAll();
            var lastTick = rig.gateway.health().lastTickAt();
            rig.clock.now = rig.clock.instant().plusSeconds(31);
            rig.frame(new byte[]{0});
            assertThat(rig.gateway.health().connectionState()).isEqualTo(MarketDataGateway.State.CONNECTED);
            assertThat(rig.gateway.health().status()).isEqualTo(STALE);
            assertThat(rig.gateway.health().lastMessageAt()).contains(rig.clock.instant());
            assertThat(rig.gateway.health().lastTickAt()).isEqualTo(lastTick);
            assertThat(rig.metrics.get("marketdata.last.tick.age").gauge().value()).isEqualTo(31);
        }
    }

    @Test void oldExchangeTimeRemainsStaleEvenWhenReceivedJustNow() {
        try (var rig = new TestRig()) {
            connectFull(rig);
            rig.frame(frame(fullPacket(408065, 100, rig.clock.instant().minusSeconds(31))));
            rig.worker.runAll();
            assertThat(rig.gateway.health().lastTickAt()).contains(rig.clock.instant());
            assertThat(rig.gateway.health().status()).isEqualTo(STALE);
        }
    }

    @Test void rejectedOlderTickCannotRefreshAStaleInstrument() {
        try (var rig = new TestRig()) {
            connectFull(rig);
            var originalTime = rig.clock.instant();
            rig.frame(frame(fullPacket(408065, 100, originalTime))); rig.worker.runAll();
            rig.clock.now = originalTime.plusSeconds(6);
            rig.frame(frame(fullPacket(408065, 200, originalTime.minusSeconds(1)))); rig.worker.runAll();
            assertThat(rig.gateway.health().status()).isEqualTo(STALE);
            assertThat(rig.gateway.health().lastTickAt()).contains(originalTime);
            assertThat(rig.store.latest(A.id()).orElseThrow().lastPrice()).isEqualByComparingTo("1.00");
            assertThat(rig.metrics.get("marketdata.ticks.rejected").counter().count()).isEqualTo(1);
        }
    }

    @Test void futureExchangeTimeIsRejectedWithoutPoisoningLatestState() {
        try (var rig = new TestRig()) {
            connectFull(rig);
            rig.frame(frame(fullPacket(408065, 999, rig.clock.instant().plusSeconds(3600))));
            rig.worker.runAll();
            assertThat(rig.store.latest(A.id())).isEmpty();
            assertThat(rig.gateway.health().decodeFailures()).isEqualTo(1);
            assertThat(rig.gateway.health().status()).isEqualTo(DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(MALFORMED_DATA);
            rig.frame(frame(fullPacket(408065, 100, rig.clock.instant()))); rig.worker.runAll();
            assertThat(rig.store.latest(A.id()).orElseThrow().lastPrice()).isEqualByComparingTo("1.00");
            assertThat(rig.gateway.health().status()).isEqualTo(DEGRADED);
        }
    }

    @Test void changingModeFencesQueuedOldModeAndRejectsFurtherOldModeFrames() {
        try (var rig = new TestRig()) {
            rig.connect();
            rig.frame(frame(ltpPacket(408065, 100)));
            rig.gateway.subscribe(Set.of(A.id()), StreamMode.FULL);
            rig.worker.runAll();
            assertThat(rig.store.latest(A.id())).isEmpty();
            assertThat(rig.gateway.health().status()).isEqualTo(NO_DATA);
            rig.frame(frame(ltpPacket(408065, 101))); rig.worker.runAll();
            assertThat(rig.store.latest(A.id())).isEmpty();
            rig.frame(frame(fullPacket(408065, 102, rig.clock.instant()))); rig.worker.runAll();
            assertThat(rig.gateway.health().status()).isEqualTo(FRESH);
            assertThat(rig.store.latest(A.id()).orElseThrow().depth()).isPresent();
        }
    }

    @Test void unsubscribeThenResubscribeSameModeFencesAlreadyQueuedTick() {
        try (var rig = new TestRig()) {
            rig.connect();
            rig.frame(frame(ltpPacket(408065, 100)));
            rig.gateway.unsubscribe(Set.of(A.id()));
            rig.gateway.subscribe(Set.of(A.id()));
            rig.worker.runAll();
            assertThat(rig.store.latest(A.id())).isEmpty();
            assertThat(rig.gateway.health().status()).isEqualTo(NO_DATA);
        }
    }

    @Test void unresolvablePacketRejectsWholeNormalizedBatch() {
        try (var rig = new TestRig()) {
            rig.connect();
            rig.frame(frame(ltpPacket(408065, 100), ltpPacket(257, 200)));
            rig.worker.runAll();
            assertThat(rig.store.latest(A.id())).isEmpty();
            assertThat(rig.gateway.health().queuedEvents()).isZero();
            assertThat(rig.gateway.health().ticksReceived()).isZero();
            assertThat(rig.gateway.health().decodeFailures()).isEqualTo(1);
            assertThat(rig.metrics.get("marketdata.decode.failures").counter().count()).isEqualTo(1);
        }
    }

    @Test void orderAndUnknownTextNeverBecomeTicksOrOrderActions() {
        try (var rig = new TestRig()) {
            rig.connect();
            rig.transport.latest().listener.onText("{\"type\":\"order\",\"data\":{\"status\":\"COMPLETE\"}}");
            rig.transport.latest().listener.onText("{\"type\":\"message\",\"data\":\"notice\"}");
            rig.transport.latest().listener.onText("not JSON");
            rig.worker.runAll();
            assertThat(rig.gateway.health().ignoredTextMessages()).isEqualTo(3);
            assertThat(rig.gateway.health().ticksReceived()).isZero();
            assertThat(rig.gateway.health().status()).isEqualTo(NO_DATA);
            assertThat(rig.store.latest(A.id())).isEmpty();
            assertThat(rig.metrics.get("marketdata.text.ignored").counter().count()).isEqualTo(3);
        }
    }

    @Test void malformedQualityIsStickyUntilExplicitStopAndRestart() {
        try (var rig = new TestRig()) {
            rig.connect();
            rig.frame(new byte[]{0, 1});
            rig.frame(frame(ltpPacket(408065, 100))); rig.worker.runAll();
            assertThat(rig.gateway.health().status()).isEqualTo(DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(MALFORMED_DATA);
            rig.gateway.stop(); rig.gateway.start(); rig.transport.open();
            assertThat(rig.gateway.health().status()).isEqualTo(NO_DATA);
            rig.frame(frame(ltpPacket(408065, 101))); rig.worker.runAll();
            assertThat(rig.gateway.health().status()).isEqualTo(FRESH);
            assertThat(rig.gateway.health().reason()).isEqualTo(NONE);
        }
    }

    @Test void reconnectingAndExhaustionRemainDistinctFromStaleness() {
        try (var rig = new TestRig()) {
            rig.connect();
            rig.frame(frame(ltpPacket(408065, 100))); rig.worker.runAll();
            rig.transport.latest().listener.onFailure(KiteWebSocketTransport.Failure.CONNECTION);
            assertThat(rig.gateway.health().status()).isEqualTo(RECONNECTING);
            assertThat(rig.gateway.health().activeSubscriptions()).isZero();
            assertThat(rig.gateway.health().desiredSubscriptions()).isEqualTo(1);
        }
        try (var rig = new TestRig(8, 0, null)) {
            rig.connect();
            rig.transport.latest().listener.onFailure(KiteWebSocketTransport.Failure.CONNECTION);
            assertThat(rig.gateway.health().status()).isEqualTo(DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(RECONNECT_EXHAUSTED);
        }
    }

    @Test void invalidatedSessionCanNeverRemainFreshOrPublishQueuedTicks() {
        try (var rig = new TestRig()) {
            rig.connect();
            rig.frame(frame(ltpPacket(408065, 100)));
            rig.session.clear();
            rig.worker.runAll();
            assertThat(rig.store.latest(A.id())).isEmpty();
            assertThat(rig.gateway.health().status()).isEqualTo(DEGRADED);
            rig.advance(Duration.ofSeconds(1));
            assertThat(rig.gateway.health().reason()).isEqualTo(AUTH_REQUIRED);
        }
    }

    private static void connectFull(TestRig rig) {
        rig.gateway.subscribe(Set.of(A.id()), StreamMode.FULL);
        rig.gateway.start(); rig.transport.open();
    }

    private static byte[] ltpPacket(int token, int price) {
        return ByteBuffer.allocate(8).putInt(token).putInt(price).array();
    }

    private static byte[] fullPacket(int token, int price, Instant exchangeTime) {
        return ByteBuffer.allocate(184).putInt(token).putInt(price)
                .putInt(60, (int) exchangeTime.getEpochSecond()).array();
    }
}
