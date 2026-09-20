package com.kitehybrid.platform.broker.application.auth;

import java.time.Instant;

/** Durable, single-use login correlation, independent of servlet sessions. */
public interface KiteLoginAttemptStore {
    void create(KiteLoginAttempt attempt);

    /** Atomically removes a currently valid attempt. Exactly one concurrent caller may succeed. */
    boolean consume(String nonceDigest, Instant now);

    /** Removes at most {@code limit} expired attempts from this store's namespace. */
    int deleteExpired(Instant now, int limit);
}
