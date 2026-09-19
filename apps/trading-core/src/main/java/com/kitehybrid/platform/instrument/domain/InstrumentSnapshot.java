package com.kitehybrid.platform.instrument.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** All indexes belong to one immutable, validated generation of reference data. */
public final class InstrumentSnapshot {
    private final long version;
    private final Instant refreshedAt;
    private final Map<InstrumentId, Instrument> byId;
    private final Map<BrokerInstrumentId, Instrument> byBrokerId;
    private final Map<ExchangeSymbol, Instrument> byExchangeAndSymbol;

    private InstrumentSnapshot(long version, Instant refreshedAt, Map<InstrumentId, Instrument> byId,
                               Map<BrokerInstrumentId, Instrument> byBrokerId,
                               Map<ExchangeSymbol, Instrument> byExchangeAndSymbol) {
        this.version = version;
        this.refreshedAt = Objects.requireNonNull(refreshedAt, "Snapshot timestamp required");
        this.byId = Map.copyOf(byId);
        this.byBrokerId = Map.copyOf(byBrokerId);
        this.byExchangeAndSymbol = Map.copyOf(byExchangeAndSymbol);
    }

    public static InstrumentSnapshot empty() {
        return new InstrumentSnapshot(0, Instant.EPOCH, Map.of(), Map.of(), Map.of());
    }

    /** Every malformed/conflicting record rejects the whole candidate; no rows are skipped. */
    public static InstrumentSnapshot validated(List<Instrument> instruments, long version, Instant refreshedAt) {
        if (version < 1) throw new IllegalArgumentException("Snapshot version must be positive");
        if (instruments == null || instruments.isEmpty())
            throw new IllegalArgumentException("Instrument snapshot must contain records");
        List<Instrument> candidate;
        try {
            candidate = List.copyOf(instruments);
        } catch (NullPointerException invalid) {
            throw new IllegalArgumentException("Instrument snapshot contains a missing record");
        }
        Map<InstrumentId, Instrument> byId = new HashMap<>();
        Map<BrokerInstrumentId, Instrument> byBrokerId = new HashMap<>();
        Map<ExchangeSymbol, Instrument> byExchangeAndSymbol = new HashMap<>();
        for (Instrument instrument : candidate) {
            if (byId.putIfAbsent(instrument.id(), instrument) != null)
                throw new IllegalArgumentException("Duplicate platform instrument ID in candidate snapshot");
            if (byBrokerId.putIfAbsent(instrument.brokerId(), instrument) != null)
                throw new IllegalArgumentException("Duplicate broker instrument ID in candidate snapshot");
            ExchangeSymbol key = new ExchangeSymbol(instrument.exchange(), instrument.tradingSymbol());
            if (byExchangeAndSymbol.putIfAbsent(key, instrument) != null)
                throw new IllegalArgumentException("Conflicting exchange and symbol in candidate snapshot");
        }
        return new InstrumentSnapshot(version, refreshedAt, byId, byBrokerId, byExchangeAndSymbol);
    }

    public long version() { return version; }
    public Instant refreshedAt() { return refreshedAt; }
    public int size() { return byId.size(); }
    public Map<InstrumentId, Instrument> byId() { return byId; }
    public Map<BrokerInstrumentId, Instrument> byBrokerId() { return byBrokerId; }
    public Map<ExchangeSymbol, Instrument> byExchangeAndSymbol() { return byExchangeAndSymbol; }
}
