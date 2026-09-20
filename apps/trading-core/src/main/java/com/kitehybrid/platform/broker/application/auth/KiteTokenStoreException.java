package com.kitehybrid.platform.broker.application.auth;

/** Safe storage boundary: database and cryptography failures never retain their original causes. */
public final class KiteTokenStoreException extends RuntimeException {
    public enum Category { CONFIGURATION, STORAGE, DECRYPTION }

    private final Category category;

    public KiteTokenStoreException(Category category) {
        super("Kite token storage failed: " + category.name(), null, false, true);
        this.category = category;
    }

    public Category category() { return category; }
}
