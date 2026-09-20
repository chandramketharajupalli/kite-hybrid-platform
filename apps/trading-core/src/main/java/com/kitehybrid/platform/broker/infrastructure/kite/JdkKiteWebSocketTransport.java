package com.kitehybrid.platform.broker.infrastructure.kite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kitehybrid.platform.broker.application.BrokerReadException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Official Kite endpoint, asynchronous JDK 21 I/O, bounded message assembly and sanitized errors. */
public final class JdkKiteWebSocketTransport implements KiteWebSocketTransport {
    private static final URI ENDPOINT = URI.create("wss://ws.kite.trade");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final KiteSession session;
    private final Duration connectTimeout;
    private final int maxMessageBytes;
    private final HttpClient client;
    private final URI endpoint;

    public JdkKiteWebSocketTransport(KiteSession session, Duration connectTimeout, int maxMessageBytes) {
        this(session, connectTimeout, maxMessageBytes,
                HttpClient.newBuilder().connectTimeout(connectTimeout)
                        .followRedirects(HttpClient.Redirect.NEVER).build(), ENDPOINT);
    }

    /** Endpoint injection is package-private and restricted to loopback transport tests. */
    JdkKiteWebSocketTransport(KiteSession session, Duration connectTimeout, int maxMessageBytes,
                             HttpClient client, URI endpoint) {
        this.session = Objects.requireNonNull(session);
        this.connectTimeout = Objects.requireNonNull(connectTimeout);
        if (connectTimeout.isNegative() || connectTimeout.isZero() || maxMessageBytes < 1)
            throw new IllegalArgumentException("Invalid market-data transport limits");
        if (!ENDPOINT.equals(endpoint) && !("ws".equals(endpoint.getScheme())
                && ("127.0.0.1".equals(endpoint.getHost()) || "localhost".equals(endpoint.getHost()))
                && endpoint.getRawQuery() == null && endpoint.getRawUserInfo() == null))
            throw new IllegalArgumentException("Unsupported market-data endpoint");
        this.maxMessageBytes = maxMessageBytes;
        this.client = Objects.requireNonNull(client);
        this.endpoint = endpoint;
    }

    @Override public CompletableFuture<Connection> connect(Listener listener) {
        Objects.requireNonNull(listener);
        final KiteSession.MarketDataCredentials credentials;
        try { credentials = session.marketDataCredentials(); }
        catch (BrokerReadException unavailable) {
            return CompletableFuture.failedFuture(new TransportException(Failure.AUTHENTICATION));
        }
        var result = new CompletableFuture<Connection>();
        var receiver = new Receiver(listener, credentials.generation(), result);
        try {
            URI authenticatedUri = URI.create(endpoint + "?api_key=" + encode(credentials.apiKey())
                    + "&access_token=" + encode(credentials.accessToken()));
            var pending = client.newWebSocketBuilder().connectTimeout(connectTimeout)
                    .buildAsync(authenticatedUri, receiver);
            result.whenComplete((ignored, failure) -> {
                if (result.isCancelled()) {
                    receiver.abort();
                    pending.cancel(true);
                }
            });
            pending.whenComplete((socket, failure) -> {
                if (failure != null) {
                    Failure category = classify(failure);
                    if (category == Failure.AUTHENTICATION) session.rejectMarketData(credentials.generation());
                    result.completeExceptionally(new TransportException(category));
                } else if (result.isCancelled() || receiver.terminal.get()) {
                    socket.abort();
                    result.completeExceptionally(new TransportException(Failure.CONNECTION));
                } else if (!result.complete(receiver)) socket.abort();
            });
        } catch (RuntimeException ignored) {
            result.completeExceptionally(new TransportException(Failure.CONNECTION));
            receiver.abort();
        }
        return result;
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static Failure classify(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        if (failure instanceof WebSocketHandshakeException handshake
                && (handshake.getResponse().statusCode() == 401 || handshake.getResponse().statusCode() == 403))
            return Failure.AUTHENTICATION;
        return Failure.CONNECTION;
    }

    private static boolean authenticationText(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("tokenexception")
                || ((normalized.contains("invalid") || normalized.contains("expired")
                        || normalized.contains("unauthoriz") || normalized.contains("unauthenticat"))
                    && (normalized.contains("token") || normalized.contains("api_key")
                        || normalized.contains("api key") || normalized.contains("session")))
                || normalized.equals("authentication required");
    }

    private static boolean authenticationMessage(String message) {
        try {
            JsonNode root = JSON.readTree(message);
            if (root == null || !("error".equals(root.path("type").asText())
                    || "error".equals(root.path("status").asText()))) return false;
            return authenticationText(root.path("error_type").asText())
                    || authenticationText(root.path("data").asText())
                    || authenticationText(root.path("message").asText());
        } catch (Exception ignored) { return false; }
    }

    private final class Receiver implements WebSocket.Listener, Connection {
        private final Listener downstream;
        private final long generation;
        private final CompletableFuture<Connection> opening;
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean closing = new AtomicBoolean();
        private final ByteArrayOutputStream binary = new ByteArrayOutputStream();
        private final StringBuilder text = new StringBuilder();
        private int textBytes;
        private volatile WebSocket socket;

        Receiver(Listener downstream, long generation, CompletableFuture<Connection> opening) {
            this.downstream = downstream;
            this.generation = generation;
            this.opening = opening;
        }

        @Override public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            if (opening.isCancelled() || terminal.get()) webSocket.abort();
            else webSocket.request(1);
        }

        @Override public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            if (terminal.get()) return null;
            if (data.remaining() > maxMessageBytes - binary.size()) {
                fail(Failure.MESSAGE_TOO_LARGE);
                return null;
            }
            byte[] fragment = new byte[data.remaining()];
            data.get(fragment);
            binary.writeBytes(fragment);
            if (last) {
                byte[] complete = binary.toByteArray();
                binary.reset();
                try { downstream.onBinary(complete); }
                catch (RuntimeException ignored) { fail(Failure.CONNECTION); }
            }
            requestNext(webSocket);
            return null;
        }

        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (terminal.get()) return null;
            if (data.length() > maxMessageBytes - textBytes) {
                fail(Failure.MESSAGE_TOO_LARGE);
                return null;
            }
            String fragment = data.toString();
            int bytes = fragment.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maxMessageBytes - textBytes) {
                fail(Failure.MESSAGE_TOO_LARGE);
                return null;
            }
            textBytes += bytes;
            text.append(fragment);
            if (last) {
                String complete = text.toString();
                text.setLength(0);
                textBytes = 0;
                if (authenticationMessage(complete)) fail(Failure.AUTHENTICATION);
                else {
                    try { downstream.onText(complete); }
                    catch (RuntimeException ignored) { fail(Failure.CONNECTION); }
                }
            }
            requestNext(webSocket);
            return null;
        }

        @Override public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            // The JDK automatically replies with pong; requesting demand keeps later ticks flowing.
            requestNext(webSocket);
            return null;
        }
        @Override public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            requestNext(webSocket);
            return null;
        }
        private void requestNext(WebSocket webSocket) { if (!terminal.get()) webSocket.request(1); }

        @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            boolean authentication = authenticationText(reason);
            if (terminal.compareAndSet(false, true)) {
                if (authentication) session.rejectMarketData(generation);
                downstream.onClosed(authentication);
            }
            return null;
        }
        @Override public void onError(WebSocket webSocket, Throwable error) { fail(Failure.CONNECTION); }

        private void fail(Failure failure) {
            if (terminal.compareAndSet(false, true)) {
                if (failure == Failure.AUTHENTICATION) session.rejectMarketData(generation);
                if (socket != null) socket.abort();
                opening.completeExceptionally(new TransportException(failure));
                downstream.onFailure(failure);
            }
        }

        @Override public CompletableFuture<Void> send(String safeCommand) {
            Objects.requireNonNull(safeCommand);
            if (terminal.get() || closing.get() || socket == null)
                return CompletableFuture.failedFuture(new TransportException(Failure.CONNECTION));
            var result = new CompletableFuture<Void>();
            try {
                socket.sendText(safeCommand, true).whenComplete((ignored, failure) -> {
                    if (failure == null) result.complete(null);
                    else result.completeExceptionally(new TransportException(Failure.CONNECTION));
                });
            } catch (RuntimeException ignored) {
                result.completeExceptionally(new TransportException(Failure.CONNECTION));
            }
            return result;
        }

        @Override public void close() {
            if (!closing.compareAndSet(false, true) || terminal.get()) return;
            WebSocket current = socket;
            if (current == null) { abort(); return; }
            try {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "stop").exceptionally(failure -> {
                    abort();
                    return null;
                });
            } catch (RuntimeException ignored) { abort(); }
            CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(this::abort);
        }
        @Override public void abort() {
            closing.set(true);
            terminal.set(true);
            WebSocket current = socket;
            if (current != null) current.abort();
        }
        @Override public String toString() { return "KiteWebSocketConnection[credentials=REDACTED]"; }
    }
}
