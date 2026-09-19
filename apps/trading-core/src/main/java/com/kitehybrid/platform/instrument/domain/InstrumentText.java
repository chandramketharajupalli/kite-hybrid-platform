package com.kitehybrid.platform.instrument.domain;

import java.util.Locale;

/** Shared canonicalization for identity construction and indexed lookups. */
final class InstrumentText {
    private InstrumentText() {}

    static String canonical(String value, int maxLength) {
        if (value == null || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Required instrument text missing or contains control characters");
        String canonical = value.trim().toUpperCase(Locale.ROOT);
        if (canonical.isBlank() || canonical.length() > maxLength)
            throw new IllegalArgumentException("Instrument text has invalid length");
        return canonical;
    }
}
