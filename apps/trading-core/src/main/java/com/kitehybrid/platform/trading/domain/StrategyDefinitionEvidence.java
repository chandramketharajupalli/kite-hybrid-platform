package com.kitehybrid.platform.trading.domain;

import com.kitehybrid.platform.strategy.domain.StrategyDefinition;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Complete immutable definition; evaluation records cannot substitute for missing definition fields. */
public record StrategyDefinitionEvidence(StrategyDefinition strategy, Duration timeframe,
        Map<String, String> parameters, List<String> entryConditions, List<String> exitConditions,
        Set<InstrumentId> applicableUniverse, EvidenceProvenance provenance) implements EvidenceProvenance.Snapshot {
    public StrategyDefinitionEvidence {
        Objects.requireNonNull(strategy);
        Objects.requireNonNull(timeframe);
        if (timeframe.isZero() || timeframe.isNegative()) throw new IllegalArgumentException("Positive timeframe required");
        parameters = Map.copyOf(Objects.requireNonNull(parameters));
        parameters.forEach((key, value) -> { EvidenceProvenance.text(key); EvidenceProvenance.text(value); });
        entryConditions = List.copyOf(Objects.requireNonNull(entryConditions));
        exitConditions = List.copyOf(Objects.requireNonNull(exitConditions));
        if (entryConditions.isEmpty() || exitConditions.isEmpty()) throw new IllegalArgumentException("Conditions required");
        entryConditions.forEach(EvidenceProvenance::text);
        exitConditions.forEach(EvidenceProvenance::text);
        applicableUniverse = Set.copyOf(Objects.requireNonNull(applicableUniverse));
        if (applicableUniverse.isEmpty()) throw new IllegalArgumentException("Explicit universe required");
        Objects.requireNonNull(provenance).requireSyntheticRetention();
    }
}
