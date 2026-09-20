package com.kitehybrid.platform.broker.application.auth;

import java.util.Objects;

/** Safe boundary failure: upstream messages, request tokens and credentials are never retained. */
public final class KiteAuthenticationException extends RuntimeException {
    public enum Code {
        INVALID_CALLBACK, LOGIN_REJECTED, REQUEST_TOKEN_ALREADY_USED, AUTHENTICATION_FAILED,
        EXCHANGE_FAILED, STORAGE_UNAVAILABLE, BROKER_UNAVAILABLE, CONFIGURATION, DISABLED
    }

    private final Code code;

    public KiteAuthenticationException(Code code) {
        super("Kite authentication failed: " + Objects.requireNonNull(code).name(), null, false, true);
        this.code = code;
    }

    public Code code() { return code; }
}
