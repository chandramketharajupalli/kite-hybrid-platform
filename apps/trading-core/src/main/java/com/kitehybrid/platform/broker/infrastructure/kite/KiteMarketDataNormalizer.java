package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.InstrumentSnapshot;
import com.kitehybrid.platform.marketdata.application.MarketDataNormalizer;
import com.kitehybrid.platform.marketdata.application.MarketDataException;
import com.kitehybrid.platform.marketdata.domain.Tick;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import static com.kitehybrid.platform.broker.infrastructure.kite.KiteBrokerIdentity.BROKER_ID;

/** Maps one immutable reference-data generation and broker wire values to the existing tick model. */
public final class KiteMarketDataNormalizer implements MarketDataNormalizer<KiteMarketDataNormalizer.Source> {
    public record Source(KiteWireTick tick, InstrumentSnapshot snapshot, Instant receivedAt) {
        public Source {
            Objects.requireNonNull(tick);
            Objects.requireNonNull(snapshot);
            Objects.requireNonNull(receivedAt);
        }
    }

    @Override public Tick normalize(Source source) {
        return normalize(source.tick(), source.snapshot(), source.receivedAt());
    }

    public Tick normalize(KiteWireTick wire, InstrumentSnapshot snapshot, Instant receivedAt) {
        Objects.requireNonNull(wire);
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(receivedAt);
        var instrument = snapshot.byBrokerId().get(new BrokerInstrumentId(BROKER_ID, Long.toString(wire.instrumentToken())));
        if (instrument == null) throw new MarketDataException(MarketDataException.Reason.UNRESOLVED_INSTRUMENT);
        int scale = priceScale(wire.instrumentToken());
        Optional<Tick.Quote> quote = wire.quote().map(value -> new Tick.Quote(value.lastQuantity(), value.volume(),
                new Tick.Ohlc(price(value.ohlc().open(), scale), price(value.ohlc().high(), scale),
                        price(value.ohlc().low(), scale), price(value.ohlc().close(), scale))));
        Optional<Tick.Depth> depth = wire.depth().map(value ->
                new Tick.Depth(levels(value.bids(), scale), levels(value.asks(), scale)));
        Optional<Instant> exchangeTimestamp = wire.exchangeTimestamp().isPresent()
                ? Optional.of(Instant.ofEpochSecond(wire.exchangeTimestamp().getAsLong())) : Optional.empty();
        return new Tick(instrument.id(), price(wire.lastPrice(), scale), receivedAt, exchangeTimestamp, quote, depth);
    }

    private List<Tick.Level> levels(List<KiteWireTick.Level> levels, int scale) {
        return levels.stream().map(level -> new Tick.Level(price(level.price(), scale),
                level.quantity(), level.orders())).toList();
    }

    private static BigDecimal price(long value, int scale) { return BigDecimal.valueOf(value, scale); }

    /** Segment divisors follow the official Kite SDK, including BCD and NCO's four decimals. */
    private static int priceScale(long token) {
        return switch ((int) (token & 0xff)) {
            case 3 -> 7; // CDS
            case 6, 12 -> 4; // BCD, NCO
            default -> 2;
        };
    }
}
