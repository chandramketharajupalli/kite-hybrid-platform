package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.MarketDataGateway;
import com.kitehybrid.platform.marketdata.application.MarketDataHealth;
import com.kitehybrid.platform.marketdata.domain.StreamMode;
import com.kitehybrid.platform.marketdata.infrastructure.InMemoryLatestMarketDataStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A real JDK WebSocket against a tiny loopback RFC6455 peer; never contacts a broker. */
class JdkKiteWebSocketTransportTest {
    private static final Instant NOW = Instant.parse("2026-09-20T08:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Test void actualTransportGatewayDecoderStoreAndHealthRecoverTogetherAfterLoopbackReconnect() throws Exception {
        var disconnectFirst = new CountDownLatch(1);
        var round = new AtomicInteger();
        var metrics = new SimpleMeterRegistry();
        try (var server = new LocalPeer(2, socket -> {
            int currentRound = round.incrementAndGet();
            handshake(socket);
            assertSubscriptionAndMode(socket, "quote");
            sendFrame(socket, 2, true, quoteFrame(currentRound == 1 ? 12345 : 12346));
            if (currentRound == 1) {
                assertThat(disconnectFirst.await(5, TimeUnit.SECONDS)).isTrue();
                sendClose(socket, 1001, "synthetic reconnect");
            } else {
                assertThat(readFrame(socket.getInputStream()).opcode).isEqualTo(8);
                sendClose(socket, 1000, "stop");
            }
        })) {
            var session = session();
            var instruments = new InMemoryInstrumentRegistry();
            var instrument = Instrument.create(new BrokerInstrumentId(KiteBrokerIdentity.BROKER_ID, "408065"), "INFY", "NSE", "CASH",
                    InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);
            instruments.replace(List.of(instrument), NOW);
            var store = new InMemoryLatestMarketDataStore();
            var settings = new KiteMarketDataProperties(true, false, TIMEOUT, Duration.ofSeconds(30),
                    Duration.ofSeconds(30), 16, 10,
                    new KiteMarketDataProperties.Reconnect(Duration.ofMillis(20), Duration.ofMillis(20), 2));
            try (var gateway = new KiteMarketDataAdapter(session, instruments, store, transport(session, server, 1024),
                    settings, Clock.fixed(NOW, ZoneOffset.UTC), metrics)) {
                gateway.subscribe(Set.of(instrument.id()), StreamMode.QUOTE);
                gateway.start();
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    assertThat(gateway.state()).isEqualTo(MarketDataGateway.State.CONNECTED);
                    assertThat(store.latest(instrument.id()).orElseThrow().lastPrice()).isEqualByComparingTo("123.45");
                    assertThat(gateway.health().status()).isEqualTo(MarketDataHealth.Status.FRESH);
                });
                disconnectFirst.countDown();
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    assertThat(round.get()).isEqualTo(2);
                    assertThat(gateway.state()).isEqualTo(MarketDataGateway.State.CONNECTED);
                    assertThat(gateway.health().status()).isEqualTo(MarketDataHealth.Status.FRESH);
                    assertThat(gateway.health().activeSubscriptions()).isEqualTo(1);
                    assertThat(gateway.health().reconnectAttempts()).isZero();
                    assertThat(store.latest(instrument.id()).orElseThrow().lastPrice()).isEqualByComparingTo("123.46");
                });
                assertThat(store.latest(instrument.id()).orElseThrow().quote().orElseThrow().volume()).hasValue(100);
                assertThat(metrics.get("marketdata.reconnects").counter().count()).isEqualTo(1);
                assertThat(metrics.get("marketdata.ticks.received").counter().count()).isEqualTo(2);
                gateway.stop();
                assertThat(gateway.health().status()).isEqualTo(MarketDataHealth.Status.STOPPED);
                server.await();
            }
        } finally { disconnectFirst.countDown(); metrics.close(); }
    }

    private static void assertSubscriptionAndMode(Socket socket, String mode) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        Frame subscribe = readFrame(socket.getInputStream());
        Frame selectMode = readFrame(socket.getInputStream());
        assertThat(subscribe.opcode).isEqualTo(1);
        assertThat(selectMode.opcode).isEqualTo(1);
        assertThat(json.readTree(subscribe.payload)).isEqualTo(json.readTree("{\"a\":\"subscribe\",\"v\":[408065]}"));
        assertThat(json.readTree(selectMode.payload)).isEqualTo(json.readTree("{\"a\":\"mode\",\"v\":[\"" + mode + "\",[408065]]}"));
    }

    private static byte[] quoteFrame(int lastPrice) {
        return ByteBuffer.allocate(48).putShort((short) 1).putShort((short) 44).putInt(408065).putInt(lastPrice)
                .putInt(7).putInt(12300).putInt(100).putInt(20).putInt(30)
                .putInt(12000).putInt(12400).putInt(11900).putInt(12200).array();
    }

    @Test void usesAuthenticatedRuntimeSessionAndAssemblesFragmentsWhileHandlingPingHeartbeatAndClose() throws Exception {
        byte[] expected = {0, 1, 0, 8, 0, 6, 58, 1, 0, 0, 0, 100};
        var listener = new RecordingListener();
        try (var server = new LocalPeer(socket -> {
            String request = handshake(socket);
            assertThat(request).contains("api_key=syntheticKey", "access_token=runtimeToken")
                    .doesNotContain("legacyEnvironmentToken");
            sendFrame(socket, 9, true, new byte[]{4});
            sendFrame(socket, 2, true, new byte[]{0});
            sendFrame(socket, 2, false, java.util.Arrays.copyOfRange(expected, 0, 5));
            sendFrame(socket, 0, true, java.util.Arrays.copyOfRange(expected, 5, expected.length));
            sendFrame(socket, 1, false, "{\"type\":\"order\",".getBytes(StandardCharsets.UTF_8));
            sendFrame(socket, 0, true, "\"data\":{}}".getBytes(StandardCharsets.UTF_8));
            boolean pong = false;
            boolean command = false;
            while (!pong || !command) {
                Frame received = readFrame(socket.getInputStream());
                if (received.opcode == 10) {
                    assertThat(received.payload).containsExactly((byte) 4);
                    pong = true;
                } else if (received.opcode == 1) {
                    assertThat(new String(received.payload, StandardCharsets.UTF_8))
                            .isEqualTo("{\"a\":\"subscribe\",\"v\":[408065]}");
                    command = true;
                }
            }
            sendClose(socket, 1000, "done");
            assertThat(readFrame(socket.getInputStream()).opcode).isEqualTo(8);
        })) {
            var session = session();
            var connection = transport(session, server, 1024).connect(listener).get(5, TimeUnit.SECONDS);
            assertThat(connection.toString()).contains("REDACTED").doesNotContain("runtimeToken");
            connection.send("{\"a\":\"subscribe\",\"v\":[408065]}").get(5, TimeUnit.SECONDS);
            assertThat(listener.binary.poll(5, TimeUnit.SECONDS)).containsExactly((byte) 0);
            assertThat(listener.binary.poll(5, TimeUnit.SECONDS)).containsExactly(expected);
            assertThat(listener.text.poll(5, TimeUnit.SECONDS)).isEqualTo("{\"type\":\"order\",\"data\":{}}");
            assertThat(listener.closed.poll(5, TimeUnit.SECONDS)).isFalse();
            assertThat(listener.failures).isEmpty();
            assertThat(session.authenticated()).isTrue();
            server.await();
        }
    }

    @Test void unavailableOrUnverifiedAuthenticationNeverOpensNetworkConnection() {
        var properties = new KiteProperties("syntheticKey", "syntheticSecret", "legacyEnvironmentToken", true);
        var session = new KiteSession(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        try (var client = HttpClient.newHttpClient()) {
            var transport = new JdkKiteWebSocketTransport(session, TIMEOUT, 1024,
                    client, URI.create("ws://127.0.0.1:1"));
            assertFailure(transport.connect(new RecordingListener()), KiteWebSocketTransport.Failure.AUTHENTICATION);
            session.install(new KiteAccessToken("runtimeToken", NOW, NOW.plusSeconds(60)));
            assertFailure(transport.connect(new RecordingListener()), KiteWebSocketTransport.Failure.AUTHENTICATION);
        }
    }

    @ParameterizedTest @ValueSource(ints = {401, 403})
    void authenticationHandshakeRejectionIsSanitizedAndInvalidatesOnlyTheUsedSession(int status) throws Exception {
        try (var server = new LocalPeer(socket -> {
            readHeaders(socket.getInputStream());
            respond(socket, "HTTP/1.1 " + status + " Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        })) {
            var session = session();
            assertFailure(transport(session, server, 1024).connect(new RecordingListener()),
                    KiteWebSocketTransport.Failure.AUTHENTICATION);
            assertThat(session.state()).isEqualTo(KiteSession.State.INVALIDATED);
            server.await();
        }
    }

    @Test void delayedAuthenticationRejectionDoesNotInvalidateReplacementSession() throws Exception {
        var handshakeStarted = new CountDownLatch(1);
        var reject = new CountDownLatch(1);
        try (var server = new LocalPeer(socket -> {
            readHeaders(socket.getInputStream());
            handshakeStarted.countDown();
            assertThat(reject.await(5, TimeUnit.SECONDS)).isTrue();
            respond(socket, "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        })) {
            var session = session();
            var pending = transport(session, server, 1024).connect(new RecordingListener());
            assertThat(handshakeStarted.await(5, TimeUnit.SECONDS)).isTrue();
            session.install(new KiteAccessToken("replacementToken", NOW, NOW.plusSeconds(60)));
            session.profileValidated();
            reject.countDown();
            assertFailure(pending, KiteWebSocketTransport.Failure.AUTHENTICATION);
            assertThat(session.authenticated()).isTrue();
            assertThat(session.marketDataCredentials().accessToken()).isEqualTo("replacementToken");
            server.await();
        } finally { reject.countDown(); }
    }

    @Test void errorEnvelopeIsClassifiedAndInvalidatesSessionWithoutExposingBrokerText() throws Exception {
        var send = new CountDownLatch(1);
        try (var server = new LocalPeer(socket -> {
            handshake(socket);
            assertThat(send.await(5, TimeUnit.SECONDS)).isTrue();
            sendFrame(socket, 1, true,
                    "{\"type\":\"error\",\"data\":\"Invalid api_key or access_token. runtimeToken\"}"
                            .getBytes(StandardCharsets.UTF_8));
            assertThat(socket.getInputStream().read()).isEqualTo(-1);
        })) {
            var listener = new RecordingListener();
            var session = session();
            transport(session, server, 1024).connect(listener).get(5, TimeUnit.SECONDS);
            send.countDown();
            assertThat(listener.failures.poll(5, TimeUnit.SECONDS)).isEqualTo(KiteWebSocketTransport.Failure.AUTHENTICATION);
            assertThat(listener.text).isEmpty();
            assertThat(session.state()).isEqualTo(KiteSession.State.INVALIDATED);
            server.await();
        } finally { send.countDown(); }
    }

    @Test void recognizedAuthenticationCloseInvalidatesSession() throws Exception {
        var send = new CountDownLatch(1);
        try (var server = new LocalPeer(socket -> {
            handshake(socket);
            assertThat(send.await(5, TimeUnit.SECONDS)).isTrue();
            sendClose(socket, 1008, "Invalid access_token");
            assertThat(readFrame(socket.getInputStream()).opcode).isEqualTo(8);
        })) {
            var session = session();
            var listener = new RecordingListener();
            transport(session, server, 1024).connect(listener).get(5, TimeUnit.SECONDS);
            send.countDown();
            assertThat(listener.closed.poll(5, TimeUnit.SECONDS)).isTrue();
            assertThat(session.state()).isEqualTo(KiteSession.State.INVALIDATED);
            server.await();
        } finally { send.countDown(); }
    }

    @ParameterizedTest @ValueSource(ints = {1, 2})
    void fragmentedTextAndBinaryMessagesCannotExceedBound(int opcode) throws Exception {
        var send = new CountDownLatch(1);
        try (var server = new LocalPeer(socket -> {
            handshake(socket);
            assertThat(send.await(5, TimeUnit.SECONDS)).isTrue();
            sendFrame(socket, opcode, false, "12345".getBytes(StandardCharsets.UTF_8));
            sendFrame(socket, 0, true, "6789".getBytes(StandardCharsets.UTF_8));
            assertThat(socket.getInputStream().read()).isEqualTo(-1);
        })) {
            var listener = new RecordingListener();
            var session = session();
            transport(session, server, 8).connect(listener).get(5, TimeUnit.SECONDS);
            send.countDown();
            assertThat(listener.failures.poll(5, TimeUnit.SECONDS)).isEqualTo(KiteWebSocketTransport.Failure.MESSAGE_TOO_LARGE);
            assertThat(listener.binary).isEmpty();
            assertThat(listener.text).isEmpty();
            assertThat(session.authenticated()).isTrue();
            server.await();
        } finally { send.countDown(); }
    }

    @Test void gracefulCloseUsesNormalCloseFrameAndRejectsLaterSends() throws Exception {
        try (var server = new LocalPeer(socket -> {
            handshake(socket);
            Frame close = readFrame(socket.getInputStream());
            assertThat(close.opcode).isEqualTo(8);
            assertThat(close.payload[0]).isEqualTo((byte) 3);
            assertThat(close.payload[1]).isEqualTo((byte) 232);
            sendClose(socket, 1000, "stop");
        })) {
            var listener = new RecordingListener();
            var connection = transport(session(), server, 1024).connect(listener).get(5, TimeUnit.SECONDS);
            connection.close();
            connection.close();
            assertFailure(connection.send("{}"), KiteWebSocketTransport.Failure.CONNECTION);
            assertThat(listener.closed.poll(5, TimeUnit.SECONDS)).isFalse();
            server.await();
        }
    }

    @Test void gracefulCloseAbortsWithinDeadlineIfPeerNeverAcknowledges() throws Exception {
        try (var server = new LocalPeer(socket -> {
            handshake(socket);
            assertThat(readFrame(socket.getInputStream()).opcode).isEqualTo(8);
            assertThat(socket.getInputStream().read()).isEqualTo(-1);
        })) {
            var connection = transport(session(), server, 1024).connect(new RecordingListener()).get(5, TimeUnit.SECONDS);
            long started = System.nanoTime();
            connection.close();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
            server.await();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        }
    }

    @Test void connectionHandshakeFailureIsSanitizedAndDoesNotInvalidateAuthentication() throws Exception {
        try (var server = new LocalPeer(socket -> {
            readHeaders(socket.getInputStream());
            respond(socket, "HTTP/1.1 503 Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        })) {
            var session = session();
            assertFailure(transport(session, server, 1024).connect(new RecordingListener()),
                    KiteWebSocketTransport.Failure.CONNECTION);
            assertThat(session.authenticated()).isTrue();
            server.await();
        }
    }

    @Test void cancellingPendingHandshakeClosesItsSocket() throws Exception {
        var started = new CountDownLatch(1);
        try (var server = new LocalPeer(socket -> {
            readHeaders(socket.getInputStream());
            started.countDown();
            assertThat(socket.getInputStream().read()).isEqualTo(-1);
        })) {
            var listener = new RecordingListener();
            var pending = transport(session(), server, 1024).connect(listener);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(pending.cancel(true)).isTrue();
            server.await();
            assertThat(listener.failures).isEmpty();
        }
    }

    private static void assertFailure(CompletableFuture<?> future, KiteWebSocketTransport.Failure failure) {
        var thrown = assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertThat(thrown.getCause()).isInstanceOf(KiteWebSocketTransport.TransportException.class);
        var safe = (KiteWebSocketTransport.TransportException) thrown.getCause();
        assertThat(safe.failure()).isEqualTo(failure);
        assertThat(safe.getCause()).isNull();
        assertThat(safe.toString()).doesNotContain("runtimeToken", "syntheticKey", "ws://", "access_token");
    }
    private static KiteSession session() {
        var session = new KiteSession(new KiteProperties("syntheticKey", "syntheticSecret", "legacyEnvironmentToken", true),
                Clock.fixed(NOW, ZoneOffset.UTC));
        session.install(new KiteAccessToken("runtimeToken", NOW, NOW.plusSeconds(60)));
        session.profileValidated();
        return session;
    }
    private static JdkKiteWebSocketTransport transport(KiteSession session, LocalPeer peer, int maxBytes) {
        return new JdkKiteWebSocketTransport(session, TIMEOUT, maxBytes, peer.client,
                URI.create("ws://127.0.0.1:" + peer.server.getLocalPort()));
    }

    private static final class RecordingListener implements KiteWebSocketTransport.Listener {
        final BlockingQueue<byte[]> binary = new LinkedBlockingQueue<>();
        final BlockingQueue<String> text = new LinkedBlockingQueue<>();
        final BlockingQueue<Boolean> closed = new LinkedBlockingQueue<>();
        final BlockingQueue<KiteWebSocketTransport.Failure> failures = new LinkedBlockingQueue<>();
        @Override public void onBinary(byte[] message) { binary.add(message); }
        @Override public void onText(String message) { text.add(message); }
        @Override public void onClosed(boolean authenticationFailure) { closed.add(authenticationFailure); }
        @Override public void onFailure(KiteWebSocketTransport.Failure failure) { failures.add(failure); }
    }

    @FunctionalInterface private interface PeerScript { void run(Socket socket) throws Exception; }
    private static final class LocalPeer implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        final HttpClient client = HttpClient.newHttpClient();
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        volatile Socket accepted;
        LocalPeer(PeerScript script) throws IOException { this(1, script); }
        LocalPeer(int connections, PeerScript script) throws IOException {
            Thread.ofVirtual().name("synthetic-kite-websocket-peer").start(() -> {
                try {
                    for (int count = 0; count < connections; count++) {
                        try (Socket socket = server.accept()) {
                            accepted = socket;
                            socket.setSoTimeout(5000);
                            script.run(socket);
                        }
                    }
                    completion.complete(null);
                } catch (Throwable failure) { completion.completeExceptionally(failure); }
            });
        }
        void await() throws Exception { completion.get(5, TimeUnit.SECONDS); }
        @Override public void close() throws Exception {
            server.close();
            if (accepted != null) accepted.close();
            client.shutdownNow();
        }
    }

    private static String handshake(Socket socket) throws Exception {
        String request = readHeaders(socket.getInputStream());
        String key = request.lines().filter(line -> line.toLowerCase(java.util.Locale.ROOT).startsWith("sec-websocket-key:"))
                .findFirst().orElseThrow().split(":", 2)[1].trim();
        String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
        respond(socket, "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n");
        return request;
    }
    private static String readHeaders(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        while (bytes.size() < 8192) {
            int value = input.read();
            if (value == -1) throw new EOFException("Synthetic handshake ended early");
            bytes.write(value);
            if (value == '\n' && bytes.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n"))
                return bytes.toString(StandardCharsets.US_ASCII);
        }
        throw new IOException("Synthetic handshake limit exceeded");
    }
    private static void respond(Socket socket, String response) throws IOException {
        socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }
    private static void sendFrame(Socket socket, int opcode, boolean last, byte[] payload) throws IOException {
        if (payload.length >= 126) throw new IllegalArgumentException("Synthetic payload too large");
        var out = socket.getOutputStream();
        out.write((last ? 128 : 0) | opcode);
        out.write(payload.length);
        out.write(payload);
        out.flush();
    }
    private static void sendClose(Socket socket, int status, String reason) throws IOException {
        var payload = new ByteArrayOutputStream();
        payload.write(status >>> 8);
        payload.write(status & 255);
        payload.writeBytes(reason.getBytes(StandardCharsets.UTF_8));
        sendFrame(socket, 8, true, payload.toByteArray());
    }
    private record Frame(int opcode, byte[] payload) {}
    private static Frame readFrame(InputStream input) throws IOException {
        int first = input.read();
        int second = input.read();
        if (first < 0 || second < 0) throw new EOFException("Synthetic frame ended early");
        int length = second & 127;
        if (length == 126) length = (input.read() << 8) | input.read();
        if (length > 4096) throw new IOException("Synthetic frame limit exceeded");
        byte[] mask = (second & 128) == 0 ? null : input.readNBytes(4);
        byte[] payload = input.readNBytes(length);
        if (payload.length != length) throw new EOFException("Synthetic payload ended early");
        if (mask != null) for (int index = 0; index < payload.length; index++) payload[index] ^= mask[index % 4];
        return new Frame(first & 15, payload);
    }
}
