package com.kitehybrid.platform.broker.application.auth;

/** Storage failures never retain database messages, query values, or their original causes. */
public final class KiteLoginAttemptStoreException extends RuntimeException {
    public KiteLoginAttemptStoreException() {
        super("Kite login attempt storage failed", null, false, true);
    }
}
