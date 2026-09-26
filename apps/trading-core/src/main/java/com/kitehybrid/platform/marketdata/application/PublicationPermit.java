package com.kitehybrid.platform.marketdata.application;

import java.util.concurrent.atomic.AtomicBoolean;

/** Revocation fences both delayed writes and reads of values from a retired stream/mode. */
public final class PublicationPermit {
    private final AtomicBoolean valid = new AtomicBoolean(true);
    public boolean valid() { return valid.get(); }
    public void revoke() { valid.set(false); }
}
