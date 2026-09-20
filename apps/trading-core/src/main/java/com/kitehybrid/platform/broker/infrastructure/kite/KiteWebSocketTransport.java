package com.kitehybrid.platform.broker.infrastructure.kite;

import java.util.concurrent.CompletableFuture;

/** Infrastructure-only transport. No credentials or JDK WebSocket objects cross this boundary. */
public interface KiteWebSocketTransport {
    CompletableFuture<Connection> connect(Listener listener);

    interface Connection {
        CompletableFuture<Void> send(String safeCommand);
        void close();
        void abort();
    }

    interface Listener {
        void onBinary(byte[] completeMessage);
        void onText(String completeMessage);
        void onClosed(boolean authenticationFailure);
        void onFailure(Failure failure);
    }

    enum Failure { AUTHENTICATION, CONNECTION, MESSAGE_TOO_LARGE }

    /** Deliberately discards upstream exceptions, whose text can contain authenticated URLs. */
    final class TransportException extends RuntimeException {
        private final Failure failure;
        public TransportException(Failure failure) {
            super("Market-data transport failed: " + failure.name(), null, false, true);
            this.failure = failure;
        }
        public Failure failure() { return failure; }
    }
}
