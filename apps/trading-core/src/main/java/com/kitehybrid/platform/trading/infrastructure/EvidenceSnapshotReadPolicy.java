package com.kitehybrid.platform.trading.infrastructure;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.strategy.domain.StrategyDefinition;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Independently supplied permission, never inferred from a stored row. No production bean. */
public record EvidenceSnapshotReadPolicy(String sourceIdentity, String authorizationReference,
        Instant validFrom, Instant validUntil, Duration maximumAge,
        Set<InstrumentId> instruments, Set<StrategyDefinition> strategies, Set<String> accounts) {
    public EvidenceSnapshotReadPolicy {
        sourceIdentity = text(sourceIdentity);
        authorizationReference = text(authorizationReference);
        Objects.requireNonNull(validFrom);
        Objects.requireNonNull(validUntil);
        Objects.requireNonNull(maximumAge);
        if (!validFrom.isBefore(validUntil) || maximumAge.isNegative() || maximumAge.isZero())
            throw new IllegalArgumentException("Invalid permission interval");
        instruments = Set.copyOf(instruments);
        strategies = Set.copyOf(strategies);
        accounts = Set.copyOf(accounts);
        accounts.forEach(EvidenceSnapshotReadPolicy::text);
    }
    public boolean permits(Instant now) {
        return !now.isBefore(validFrom) && now.isBefore(validUntil);
    }
    public static String text(String value) {
        if (value == null || value.isBlank() || value.length() > 256
                || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid bounded text");
        return value;
    }
}
