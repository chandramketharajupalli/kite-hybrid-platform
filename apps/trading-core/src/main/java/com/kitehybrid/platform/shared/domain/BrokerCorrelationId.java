package com.kitehybrid.platform.shared.domain;

import java.security.SecureRandom;
import java.util.Objects;

/** Broker-neutral, bounded identity used only for deterministic order recovery. */
public record BrokerCorrelationId(String value) {
    private static final SecureRandom RANDOM = new SecureRandom();
    public BrokerCorrelationId {
        Objects.requireNonNull(value);
        if (!value.matches("[A-Za-z0-9]{20}")) throw new IllegalArgumentException("Invalid broker correlation identity");
    }
    public static BrokerCorrelationId generate() {
        var bytes = new byte[10]; RANDOM.nextBytes(bytes);
        var hex = new StringBuilder(20);
        for (byte b : bytes) hex.append(String.format("%02x", b & 0xff));
        return new BrokerCorrelationId(hex.toString());
    }
    @Override public String toString() { return value; }
}
