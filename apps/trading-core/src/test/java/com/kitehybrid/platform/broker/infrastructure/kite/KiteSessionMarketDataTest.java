package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KiteSessionMarketDataTest {
    private static final Instant NOW = Instant.parse("2026-09-20T08:00:00Z");
    private static final KiteProperties PROPERTIES = new KiteProperties("syntheticKey", "syntheticSecret",
            "legacyEnvironmentToken", true);

    @Test void snapshotsRequireProfileValidationAndUseOnlyCurrentRuntimeToken() {
        var session = new KiteSession(PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(BrokerReadException.class, session::marketDataCredentials);
        long initial = session.marketDataGeneration();
        session.install(new KiteAccessToken("currentRuntimeToken", NOW, NOW.plusSeconds(60)));
        assertThrows(BrokerReadException.class, session::marketDataCredentials);
        session.profileValidated();

        var snapshot = session.marketDataCredentials();

        assertThat(snapshot.apiKey()).isEqualTo("syntheticKey");
        assertThat(snapshot.accessToken()).isEqualTo("currentRuntimeToken");
        assertThat(snapshot.generation()).isGreaterThan(initial);
        assertThat(snapshot.toString()).contains("REDACTED").doesNotContain("currentRuntimeToken", "syntheticKey");
    }

    @Test void oldSocketInvalidationCannotInvalidateReplacementLogin() {
        var session = new KiteSession(PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
        session.install(new KiteAccessToken("oldRuntimeToken", NOW, NOW.plusSeconds(60)));
        session.profileValidated();
        long oldGeneration = session.marketDataGeneration();
        session.install(new KiteAccessToken("newRuntimeToken", NOW, NOW.plusSeconds(60)));
        session.profileValidated();

        assertThat(session.invalidateMarketData(oldGeneration)).isFalse();
        assertThat(session.authenticated()).isTrue();
        assertThat(session.marketDataCredentials().accessToken()).isEqualTo("newRuntimeToken");
        long activeGeneration = session.marketDataGeneration();
        assertThat(session.invalidateMarketData(activeGeneration)).isTrue();
        assertThat(session.marketDataGeneration()).isGreaterThan(activeGeneration);
        assertThat(session.state()).isEqualTo(KiteSession.State.INVALIDATED);
        assertThat(session.invalidateMarketData(activeGeneration)).isFalse();
    }

    @Test void clearAndExpiryChangeGenerationOnceAndRequireNewAuthentication() {
        var clock = new MutableClock();
        var session = new KiteSession(PROPERTIES, clock);
        session.install(new KiteAccessToken("runtimeToken", NOW, NOW.plusSeconds(60)));
        session.profileValidated();
        long authenticated = session.marketDataGeneration();
        clock.now = NOW.plusSeconds(60);

        assertThat(session.authenticated()).isFalse();
        long expired = session.marketDataGeneration();
        assertThat(expired).isGreaterThan(authenticated);
        assertThat(session.marketDataGeneration()).isEqualTo(expired);
        assertThrows(BrokerReadException.class, session::marketDataCredentials);
        session.clear();
        assertThat(session.marketDataGeneration()).isGreaterThan(expired);
    }

    @Test void marketDataReadsAndRejectionNeverWaitForTheRestSessionMonitor() throws Exception {
        var session = new KiteSession(PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
        session.install(new KiteAccessToken("runtimeToken", NOW, NOW.plusSeconds(60)));
        session.profileValidated();
        long generation = session.marketDataStatus().generation();
        var holdingMonitor = new CountDownLatch(1);
        var releaseMonitor = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var blockedRest = threads.submit(() -> {
                synchronized (session) {
                    holdingMonitor.countDown();
                    try { assertThat(releaseMonitor.await(5, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException failure) { throw new AssertionError(failure); }
                }
            });
            try {
                assertThat(holdingMonitor.await(5, TimeUnit.SECONDS)).isTrue();
                var io = threads.submit(() -> {
                    assertThat(session.marketDataStatus().authenticated()).isTrue();
                    assertThat(session.marketDataCredentials().accessToken()).isEqualTo("runtimeToken");
                    assertThat(session.rejectMarketData(generation)).isTrue();
                    assertThat(session.marketDataStatus().authenticated()).isFalse();
                    assertThrows(BrokerReadException.class, session::marketDataCredentials);
                });
                io.get(1, TimeUnit.SECONDS);
            } finally { releaseMonitor.countDown(); }
            blockedRest.get(5, TimeUnit.SECONDS);
        }
        assertThat(session.state()).isEqualTo(KiteSession.State.INVALIDATED);
        assertThat(session.marketDataStatus().generation()).isGreaterThan(generation);
        session.install(new KiteAccessToken("replacementToken", NOW, NOW.plusSeconds(60)));
        session.profileValidated();
        assertThat(session.rejectMarketData(generation)).isFalse();
        assertThat(session.marketDataStatus().authenticated()).isTrue();
        assertThat(session.marketDataCredentials().accessToken()).isEqualTo("replacementToken");
    }

    @Test void lockFreeStatusAndCredentialsRejectExpiryWithoutWaitingForSynchronizedMutation() throws Exception {
        var clock = new MutableClock();
        var session = new KiteSession(PROPERTIES, clock);
        session.install(new KiteAccessToken("runtimeToken", NOW, NOW.plusSeconds(60)));
        session.profileValidated();
        var holdingMonitor = new CountDownLatch(1);
        var releaseMonitor = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var blockedRest = threads.submit(() -> {
                synchronized (session) {
                    holdingMonitor.countDown();
                    try { assertThat(releaseMonitor.await(5, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException failure) { throw new AssertionError(failure); }
                }
            });
            try {
                assertThat(holdingMonitor.await(5, TimeUnit.SECONDS)).isTrue();
                clock.now = NOW.plusSeconds(60);
                threads.submit(() -> {
                    assertThat(session.marketDataStatus().authenticated()).isFalse();
                    assertThat(session.marketDataStatus().expiresAt()).isEqualTo(clock.now);
                    assertThrows(BrokerReadException.class, session::marketDataCredentials);
                }).get(1, TimeUnit.SECONDS);
            } finally { releaseMonitor.countDown(); }
            blockedRest.get(5, TimeUnit.SECONDS);
        }
        assertThat(session.state()).isEqualTo(KiteSession.State.AUTH_REQUIRED);
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
