package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.marketdata.domain.StreamMode;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static com.kitehybrid.platform.broker.infrastructure.kite.KiteMarketDataTestSupport.*;
import static com.kitehybrid.platform.marketdata.application.MarketDataGateway.State.*;
import static org.assertj.core.api.Assertions.*;

class KiteMarketDataAdapterTest {
    @Test void lifecycleAndDuplicateStartUseOneConnectionAndDefaultToLtp() {
        try (var rig = new TestRig()) {
            assertThat(rig.gateway.state()).isEqualTo(STOPPED);
            rig.gateway.subscribe(Set.of(A.id()));
            rig.gateway.start(); rig.gateway.start();
            assertThat(rig.gateway.state()).isEqualTo(STARTING);
            assertThat(rig.transport.attempts).hasSize(1);
            rig.transport.open();
            assertThat(rig.gateway.state()).isEqualTo(CONNECTED);
            assertThat(rig.transport.latest().socket.sent).hasSize(2).anyMatch(s -> s.contains("subscribe"))
                    .anyMatch(s -> s.contains("ltp"));
            rig.gateway.start();
            assertThat(rig.transport.attempts).hasSize(1);
            rig.gateway.stop();
            assertThat(rig.gateway.state()).isEqualTo(STOPPED);
            assertThat(rig.transport.latest().socket.closes).isEqualTo(1);
            rig.gateway.start(); rig.transport.open();
            assertThat(rig.gateway.state()).isEqualTo(CONNECTED);
        }
    }
    @Test void connectedSubscriptionsAreIdempotentAndModeChangesDoNotResubscribe() {
        try (var rig = new TestRig()) {
            rig.connect();
            var sent = rig.transport.latest().socket.sent;
            rig.gateway.subscribe(Set.of(A.id()));
            assertThat(sent).hasSize(2);
            rig.gateway.subscribe(Set.of(B.id()), StreamMode.QUOTE);
            assertThat(sent).hasSize(4);
            rig.gateway.subscribe(Set.of(A.id()), StreamMode.FULL);
            assertThat(sent).hasSize(5);
            assertThat(sent.getLast()).contains("mode", "full").doesNotContain("subscribe");
            rig.gateway.unsubscribe(Set.of(B.id()));
            assertThat(sent).hasSize(6);
            assertThat(sent.getLast()).contains("unsubscribe", "884737");
            rig.gateway.unsubscribe(Set.of(B.id()));
            assertThat(sent).hasSize(6);
            assertThat(rig.gateway.health().desiredSubscriptions()).isEqualTo(1);
            assertThat(rig.gateway.health().activeSubscriptions()).isEqualTo(1);
        }
    }
    @Test void connectedOnlyAfterSubscriptionsAndModesSentAndPendingRequestsCoalesce() {
        try (var rig = new TestRig()) {
            rig.gateway.subscribe(Set.of(A.id())); rig.gateway.start();
            var pending = new CompletableFuture<Void>();
            rig.transport.latest().socket.nextSend = pending;
            rig.transport.open();
            assertThat(rig.gateway.state()).isEqualTo(STARTING);
            assertThat(rig.gateway.health().activeSubscriptions()).isZero();
            rig.gateway.subscribe(Set.of(B.id()), StreamMode.FULL);
            rig.gateway.unsubscribe(Set.of(A.id()));
            assertThat(rig.transport.latest().socket.sent).hasSize(1);
            pending.complete(null);
            assertThat(rig.gateway.state()).isEqualTo(CONNECTED);
            assertThat(rig.gateway.health().activeSubscriptions()).isEqualTo(1);
            assertThat(rig.transport.latest().socket.sent).hasSize(5);
        }
    }
    @Test void reconnectRestoresDesiredModesAndResetsAttempts() {
        try (var rig = new TestRig()) {
            rig.gateway.subscribe(Set.of(A.id()), StreamMode.FULL);
            rig.gateway.subscribe(Set.of(B.id()), StreamMode.QUOTE);
            rig.gateway.start(); rig.transport.open();
            var first = rig.transport.latest();
            rig.transport.failure(KiteWebSocketTransport.Failure.CONNECTION);
            assertThat(rig.gateway.state()).isEqualTo(RECONNECTING);
            assertThat(rig.gateway.health().activeSubscriptions()).isZero();
            assertThat(rig.gateway.health().reconnectAttempts()).isEqualTo(1);
            rig.advance(Duration.ofMillis(499)); assertThat(rig.transport.attempts).hasSize(1);
            rig.advance(Duration.ofMillis(1)); assertThat(rig.transport.attempts).hasSize(2);
            rig.transport.open();
            assertThat(rig.transport.latest().socket.sent).isEqualTo(first.socket.sent);
            assertThat(rig.gateway.state()).isEqualTo(CONNECTED);
            assertThat(rig.gateway.health().reconnectAttempts()).isZero();
            first.listener.onFailure(KiteWebSocketTransport.Failure.AUTHENTICATION);
            first.listener.onBinary(ltp(408065, 1));
            assertThat(rig.gateway.state()).isEqualTo(CONNECTED);
            assertThat(rig.gateway.health().ticksReceived()).isZero();
        }
    }
    @Test void retriesExhaustThenRequireExplicitRestart() {
        try (var rig = new TestRig(16, 2, null)) {
            rig.connect();
            rig.transport.failure(KiteWebSocketTransport.Failure.CONNECTION);
            rig.advance(Duration.ofMillis(500));
            rig.transport.latest().future.completeExceptionally(new IllegalStateException());
            rig.advance(Duration.ofSeconds(1));
            rig.transport.latest().future.completeExceptionally(new IllegalStateException());
            assertThat(rig.gateway.state()).isEqualTo(DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(MarketDataHealth.Reason.RECONNECT_EXHAUSTED);
            rig.advance(Duration.ofHours(1));
            assertThat(rig.transport.attempts).hasSize(3);
            rig.gateway.start(); rig.transport.open();
            assertThat(rig.gateway.state()).isEqualTo(CONNECTED);
        }
    }
    @Test void missingAuthenticationDoesNotConnectOrRetryAndManualTokenCannotSubstitute() {
        try (var rig = new TestRig()) {
            rig.session.clear();
            rig.gateway.start();
            assertThat(rig.gateway.state()).isEqualTo(DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(MarketDataHealth.Reason.AUTH_REQUIRED);
            rig.advance(Duration.ofMinutes(5));
            assertThat(rig.transport.attempts).isEmpty();
            rig.authenticate("replacement"); rig.gateway.start(); rig.transport.open();
            assertThat(rig.gateway.state()).isEqualTo(CONNECTED);
        }
    }
    @Test void disabledGatewayNeverConnects() {
        try (var rig = new TestRig(properties(false, 16, 3), null)) {
            rig.gateway.start();
            assertThat(rig.gateway.state()).isEqualTo(STOPPED);
            assertThat(rig.gateway.health().reason()).isEqualTo(MarketDataHealth.Reason.DISABLED);
            assertThat(rig.transport.attempts).isEmpty();
        }
    }
    @Test void authenticationRejectionDoesNotRetry() {
        try (var rig = new TestRig()) {
            rig.connect();
            rig.transport.failure(KiteWebSocketTransport.Failure.AUTHENTICATION);
            rig.advance(Duration.ofMinutes(1));
            assertThat(rig.gateway.state()).isEqualTo(DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(MarketDataHealth.Reason.AUTH_REQUIRED);
            assertThat(rig.transport.attempts).hasSize(1);
        }
    }
    @Test void sessionResetExpiryAndRotationStopAnExistingSocket() {
        for (String action : List.of("reset", "expire", "rotate")) {
            try (var rig = new TestRig()) {
                rig.connect();
                switch (action) {
                    case "reset" -> rig.session.clear();
                    case "expire" -> rig.clock.now = rig.clock.now.plusSeconds(86401);
                    case "rotate" -> rig.authenticate("replacement");
                    default -> throw new AssertionError();
                }
                rig.advance(Duration.ofSeconds(1));
                assertThat(rig.gateway.state()).isEqualTo(DEGRADED);
                assertThat(rig.transport.latest().socket.aborts).isEqualTo(1);
                assertThat(rig.transport.attempts).hasSize(1);
                assertThat(rig.gateway.health().reason()).isEqualTo(action.equals("rotate")
                        ? MarketDataHealth.Reason.SESSION_CHANGED : MarketDataHealth.Reason.AUTH_REQUIRED);
            }
        }
    }
    @Test void stopsPendingConnectOrReconnectAndIgnoresLateCallbacks() {
        try (var rig = new TestRig()) {
            rig.gateway.start();
            var first = rig.transport.latest();
            rig.gateway.stop();
            assertThat(first.future).isCancelled();
            first.listener.onClosed(false);
            rig.advance(Duration.ofMinutes(1));
            assertThat(rig.gateway.state()).isEqualTo(STOPPED);
            assertThat(rig.transport.attempts).hasSize(1);
            rig.gateway.start(); rig.transport.open();
            rig.transport.failure(KiteWebSocketTransport.Failure.CONNECTION);
            rig.gateway.stop();
            rig.advance(Duration.ofMinutes(1));
            assertThat(rig.transport.attempts).hasSize(2);
            assertThat(rig.gateway.state()).isEqualTo(STOPPED);
        }
    }
    @Test void connectAndCommandTimeoutsEnterBoundedReconnect() {
        for (boolean connecting : List.of(true, false)) {
            try (var rig = new TestRig()) {
                rig.gateway.subscribe(Set.of(A.id())); rig.gateway.start();
                if (!connecting) {
                    rig.transport.latest().socket.nextSend = new CompletableFuture<>();
                    rig.transport.open();
                }
                rig.advance(Duration.ofSeconds(10));
                assertThat(rig.gateway.state()).isEqualTo(RECONNECTING);
                assertThat(rig.gateway.health().reconnectAttempts()).isEqualTo(1);
            }
        }
    }
    @Test void idleSocketReconnectsWithoutInventingMessageOrTickTimes() {
        try (var rig = new TestRig()) {
            rig.connect();
            assertThat(rig.gateway.health().lastMessageAt()).isEmpty();
            rig.advance(Duration.ofSeconds(31));
            assertThat(rig.gateway.state()).isEqualTo(RECONNECTING);
            assertThat(rig.gateway.health().lastMessageAt()).isEmpty();
            assertThat(rig.gateway.health().lastTickAt()).isEmpty();
            rig.advance(Duration.ofMillis(500)); rig.transport.open();
            rig.advance(Duration.ofSeconds(1));
            assertThat(rig.gateway.state()).isEqualTo(CONNECTED);
            rig.frame(new byte[]{0});
            assertThat(rig.gateway.health().lastMessageAt()).contains(rig.clock.instant());
            assertThat(rig.gateway.health().lastTickAt()).isEmpty();
        }
    }
    @Test void malformedOrUnresolvedSubscriptionsAreAtomicAndLimitsAreEnforced() {
        try (var rig = new TestRig()) {
            rig.gateway.subscribe(Set.of(A.id()));
            assertThatThrownBy(() -> rig.gateway.subscribe(Set.of(B.id(), new InstrumentId(UUID.randomUUID()))))
                    .isInstanceOf(MarketDataException.class).hasMessage("UNRESOLVED_INSTRUMENT");
            assertThat(rig.gateway.health().desiredSubscriptions()).isEqualTo(1);
            var ids = new HashSet<InstrumentId>();
            IntStream.range(0, 3001).forEach(i -> ids.add(new InstrumentId(UUID.randomUUID())));
            assertThatThrownBy(() -> rig.gateway.subscribe(ids)).hasMessage("SUBSCRIPTION_LIMIT");
            rig.registry.replace(List.of(instrument(1, "DIFFERENT")), rig.clock.instant());
            rig.gateway.start();
            assertThat(rig.gateway.state()).isEqualTo(DEGRADED);
            assertThat(rig.transport.attempts).isEmpty();
        }
    }
    @Test void registryRefreshCannotReuseOldSocketTokenMappings() {
        try (var rig = new TestRig()) {
            rig.connect();
            rig.registry.replace(List.of(instrument(408065, "OTHER")), rig.clock.instant());
            assertThat(rig.gateway.health().status()).isEqualTo(MarketDataHealth.Status.DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(MarketDataHealth.Reason.INSTRUMENT_REGISTRY_CHANGED);
            rig.tick(408065, 100);
            rig.worker.runAll();
            assertThat(rig.store.latest(A.id())).isEmpty();
            assertThat(rig.gateway.state()).isEqualTo(DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(MarketDataHealth.Reason.INSTRUMENT_REGISTRY_CHANGED);
        }
    }
    @Test void rotationImmediatelyBeforeSocketFailureDoesNotAutomaticallyConnectReplacementLogin() {
        try (var rig = new TestRig()) {
            rig.connect();
            rig.authenticate("replacement");
            rig.transport.failure(KiteWebSocketTransport.Failure.CONNECTION);
            rig.advance(Duration.ofSeconds(1));
            assertThat(rig.gateway.state()).isEqualTo(DEGRADED);
            assertThat(rig.gateway.health().reason()).isEqualTo(MarketDataHealth.Reason.SESSION_CHANGED);
            assertThat(rig.transport.attempts).hasSize(1);
        }
    }
    @Test void existingRestSessionMonitorCannotBlockReceiveHealthOrStop() throws Exception {
        try (var rig = new TestRig(); var threads = Executors.newFixedThreadPool(2)) {
            rig.connect();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var rest = threads.submit(() -> {
                synchronized (rig.session) {
                    entered.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test monitor timed out"); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
            });
            try {
                assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
                var receive = threads.submit(() -> {
                    rig.tick(408065, 100); rig.worker.runAll();
                    var status = rig.gateway.health();
                    rig.gateway.stop();
                    return status;
                });
                assertThat(receive.get(1, TimeUnit.SECONDS).status()).isEqualTo(MarketDataHealth.Status.FRESH);
            } finally { release.countDown(); }
            rest.get(1, TimeUnit.SECONDS);
        }
    }
    @Test void concurrentStartsAndSubscriptionsRemainDeduplicated() throws Exception {
        try (var rig = new TestRig(); var threads = Executors.newFixedThreadPool(4)) {
            var tasks = IntStream.range(0, 50).mapToObj(i -> (Callable<Void>) () -> {
                rig.gateway.subscribe(Set.of(A.id())); rig.gateway.start(); return null;
            }).toList();
            for (var result : threads.invokeAll(tasks)) result.get();
            assertThat(rig.transport.attempts).hasSize(1);
            rig.transport.open();
            assertThat(rig.transport.latest().socket.sent).hasSize(2);
        }
    }
    @Test void jitterIsPositiveExponentialAndCapped() {
        var config = properties(true, 16, 8).reconnect();
        var minimum = new KiteReconnectPolicy(config, () -> 0);
        assertThat(minimum.delay(1)).isEqualTo(Duration.ofMillis(500));
        assertThat(minimum.delay(2)).isEqualTo(Duration.ofSeconds(1));
        assertThat(minimum.delay(100)).isEqualTo(Duration.ofSeconds(4));
        var maximum = new KiteReconnectPolicy(config, () -> Math.nextDown(1.0));
        assertThat(maximum.delay(100)).isLessThanOrEqualTo(Duration.ofSeconds(8));
        assertThat(maximum.delay(100)).isGreaterThan(Duration.ofMillis(7999));
    }
}
