package com.kitehybrid.platform.trading.domain;

import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.util.Objects;
import java.util.Optional;

/** One exact instrument in one immutable reference-data generation; no refresh/import behavior. */
public record InstrumentSnapshotEvidence(InstrumentId instrumentId, String exchange, String symbol,
        Optional<BrokerInstrumentId> instrumentToken, long snapshotVersion,
        EvidenceProvenance provenance) implements EvidenceProvenance.Snapshot {
    public InstrumentSnapshotEvidence {
        Objects.requireNonNull(instrumentId);
        if (instrumentId.value().getMostSignificantBits() == 0 && instrumentId.value().getLeastSignificantBits() == 0)
            throw new IllegalArgumentException("Nonzero instrument identity required");
        exchange = EvidenceProvenance.text(exchange);
        symbol = EvidenceProvenance.text(symbol);
        Objects.requireNonNull(instrumentToken);
        if (snapshotVersion < 1) throw new IllegalArgumentException("Positive snapshot version required");
        Objects.requireNonNull(provenance).requireSyntheticRetention();
        // A present token must therefore be a synthetic fixture, never an imported real mapping.
    }
}
