package com.kitehybrid.platform.trading.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable query result. AVAILABLE means a current permitted fixture, never production trust. */
public final class EvidenceAvailability<T extends EvidenceProvenance.Snapshot> {
    public enum Status { AVAILABLE, MISSING, STALE, INVALID, INCOMPLETE }
    private final Status status;
    private final Optional<T> evidence;
    private EvidenceAvailability(Status status, Optional<T> evidence) {
        this.status = status;
        this.evidence = evidence;
    }
    public Status status() { return status; }
    /** Retained on stale/invalid results for provenance; consumers must first check status. */
    public Optional<T> evidence() { return evidence; }
    public boolean tradingAuthorized() { return false; }
    public static <T extends EvidenceProvenance.Snapshot> EvidenceAvailability<T> missing() {
        return new EvidenceAvailability<>(Status.MISSING, Optional.empty());
    }
    public static <T extends EvidenceProvenance.Snapshot> EvidenceAvailability<T> invalid() {
        return new EvidenceAvailability<>(Status.INVALID, Optional.empty());
    }
    public static <T extends EvidenceProvenance.Snapshot> EvidenceAvailability<T> incomplete() {
        return new EvidenceAvailability<>(Status.INCOMPLETE, Optional.empty());
    }
    public static <T extends EvidenceProvenance.Snapshot> EvidenceAvailability<T> inspect(T value, Clock clock) {
        Objects.requireNonNull(clock);
        if (value == null) return missing();
        Instant now = clock.instant();
        var provenance = value.provenance();
        Status status = !provenance.syntheticRetentionPermitted() || now.isBefore(provenance.observedAt())
                ? Status.INVALID : !now.isBefore(provenance.expiresAt()) ? Status.STALE : Status.AVAILABLE;
        return new EvidenceAvailability<>(status, Optional.of(value));
    }
}
