package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** HTTP is intercepted in-process, including the deliberately delayed broker rejection. */
class KiteSessionConcurrencyTest {
    private static final Instant ISSUED = Instant.parse("2026-09-20T23:59:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-09-21T00:30:00Z");
    private static final KiteProperties PROPERTIES = new KiteProperties("syntheticKey", "syntheticSecret",
            "legacyEnvironmentToken", true);

    @Test void applicationSessionIgnoresLegacyEnvironmentAccessTokenAndWaitsForStoreRestore() {
        var session = new KiteSession(PROPERTIES, new MutableClock());

        assertThat(session.state()).isEqualTo(KiteSession.State.AUTH_REQUIRED);
        assertThat(session.tokenAvailable()).isFalse();
        assertThat(session.authenticated()).isFalse();
        assertThat(assertThrows(BrokerReadException.class, session::authorization).category())
                .isEqualTo(BrokerReadException.Category.AUTHENTICATION);
    }

    @Test void sixAmIndiaExpiryBlocksAuthorizationAndTransitionsToAuthenticationRequired() {
        var clock = new MutableClock();
        var session = new KiteSession(PROPERTIES, clock);
        session.install(new KiteAccessToken("syntheticStoredToken", ISSUED, EXPIRES));
        session.profileValidated();
        clock.now = EXPIRES.minusNanos(1);
        assertThat(session.authenticated()).isTrue();
        assertThat(session.authorization()).isEqualTo("token syntheticKey:syntheticStoredToken");

        clock.now = EXPIRES;

        assertThat(assertThrows(BrokerReadException.class, session::authorization).category())
                .isEqualTo(BrokerReadException.Category.AUTHENTICATION);
        assertThat(session.state()).isEqualTo(KiteSession.State.AUTH_REQUIRED);
        assertThat(session.tokenAvailable()).isFalse();
        assertThat(session.authenticated()).isFalse();
    }

    @Test void lateProfileValidationCannotReviveAnInvalidatedOrClearedSession() {
        var session = new KiteSession(PROPERTIES, new MutableClock());
        session.install(new KiteAccessToken("syntheticStoredToken", ISSUED, EXPIRES));
        session.invalidate();

        session.profileValidated();

        assertThat(session.state()).isEqualTo(KiteSession.State.INVALIDATED);
        assertThat(session.authenticated()).isFalse();
        assertThat(session.tokenAvailable()).isFalse();
        session.clear();
        session.profileValidated();
        assertThat(session.state()).isEqualTo(KiteSession.State.AUTH_REQUIRED);
    }

    @Test void delayedOldTokenRejectionFinishesBeforeReplacementCanBeInstalled() throws Exception {
        var session = new KiteSession(PROPERTIES, new MutableClock());
        session.install(new KiteAccessToken("syntheticOldToken", ISSUED, EXPIRES));
        var builder = RestClient.builder().baseUrl("https://api.kite.trade");
        var server = MockRestServiceServer.bindTo(builder).build();
        var transport = new KiteRestTransport(builder.build(), session);
        var oldRequestStarted = new CountDownLatch(1);
        var releaseOldResponse = new CountDownLatch(1);
        var replacementStarted = new CountDownLatch(1);
        server.expect(requestTo("https://api.kite.trade/user/profile"))
                .andExpect(header("Authorization", "token syntheticKey:syntheticOldToken"))
                .andRespond(request -> {
                    oldRequestStarted.countDown();
                    try {
                        if (!releaseOldResponse.await(5, TimeUnit.SECONDS))
                            throw new IOException("Synthetic response timed out");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Synthetic response interrupted");
                    }
                    return withStatus(HttpStatus.UNAUTHORIZED).createResponse(request);
                });
        server.expect(requestTo("https://api.kite.trade/instruments"))
                .andExpect(header("Authorization", "token syntheticKey:syntheticReplacementToken"))
                .andRespond(withSuccess("synthetic-instruments", MediaType.TEXT_PLAIN));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var rejectedRead = executor.submit(() -> assertThrows(BrokerReadException.class,
                    () -> transport.get(KiteRestTransport.Endpoint.PROFILE)));
            assertThat(oldRequestStarted.await(5, TimeUnit.SECONDS)).isTrue();
            var replacement = executor.submit(() -> {
                replacementStarted.countDown();
                session.install(new KiteAccessToken("syntheticReplacementToken", ISSUED, EXPIRES));
            });
            assertThat(replacementStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> replacement.get(100, TimeUnit.MILLISECONDS));

            releaseOldResponse.countDown();
            assertThat(rejectedRead.get(5, TimeUnit.SECONDS).category())
                    .isEqualTo(BrokerReadException.Category.AUTHENTICATION);
            replacement.get(5, TimeUnit.SECONDS);

            assertThat(session.state()).isEqualTo(KiteSession.State.UNVERIFIED);
            assertThat(session.tokenAvailable()).isTrue();
            assertThat(transport.get(KiteRestTransport.Endpoint.INSTRUMENTS)).isEqualTo("synthetic-instruments");
            session.profileValidated();
            assertThat(session.state()).isEqualTo(KiteSession.State.AUTHENTICATED);
            server.verify();
        } finally {
            releaseOldResponse.countDown();
            executor.shutdownNow();
        }
    }

    private static final class MutableClock extends Clock {
        Instant now = ISSUED;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
