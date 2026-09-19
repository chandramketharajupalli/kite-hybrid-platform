package com.kitehybrid.platform.account.domain;

import java.util.Set;

/** Connectivity identity only; never trading permission or a full broker user record. */
public record BrokerProfile(String broker, String userId, Set<String> exchanges) {
    public BrokerProfile {
        if (broker == null || !broker.matches("[A-Z0-9_]{1,32}")
                || userId == null || !userId.matches("[A-Za-z0-9]{1,32}")
                || exchanges == null || exchanges.isEmpty()
                || exchanges.stream().anyMatch(e -> e == null || !e.matches("[A-Z0-9-]{1,16}")))
            throw new IllegalArgumentException("Invalid broker profile");
        exchanges = Set.copyOf(exchanges);
    }
    @Override public String toString() { return "BrokerProfile[broker=" + broker + ", identity=REDACTED]"; }
}
