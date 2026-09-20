package com.kitehybrid.platform.marketdata.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Immutable broker-independent market state. Missing quote, depth, or exchange time remains absent;
 * a last-price update never invents values from an earlier quote. Quantities use instrument units.
 * Broker tokens and wire-only fields belong to the infrastructure adapter.
 */
public record Tick(InstrumentId instrumentId, BigDecimal lastPrice, Instant receivedAt,
                   Optional<Instant> exchangeTimestamp, Optional<Quote> quote, Optional<Depth> depth) {
    public Tick {
        Objects.requireNonNull(instrumentId);
        requirePrice(lastPrice);
        Objects.requireNonNull(receivedAt);
        Objects.requireNonNull(exchangeTimestamp);
        Objects.requireNonNull(quote);
        Objects.requireNonNull(depth);
    }

    public Tick(InstrumentId instrumentId, BigDecimal lastPrice, Instant receivedAt) {
        this(instrumentId, lastPrice, receivedAt, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Compatibility accessor for the original receive-time-only tick. */
    public Instant observedAt() {
        return receivedAt;
    }

    public record Ohlc(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        public Ohlc {
            requirePrice(open);
            requirePrice(high);
            requirePrice(low);
            requirePrice(close);
        }
    }

    /** Index quotes can contain OHLC without traded quantity or volume. */
    public record Quote(OptionalLong lastQuantity, OptionalLong volume, Ohlc ohlc) {
        public Quote {
            Objects.requireNonNull(lastQuantity);
            Objects.requireNonNull(volume);
            Objects.requireNonNull(ohlc);
            lastQuantity.ifPresent(Tick::requireQuantity);
            volume.ifPresent(Tick::requireQuantity);
        }
    }

    public record Level(BigDecimal price, long quantity, int orders) {
        public Level {
            requirePrice(price);
            requireQuantity(quantity);
            if (orders < 0) throw new IllegalArgumentException("Negative order count");
        }
    }

    /** Best-first bid and ask levels; an empty side means no available liquidity on that side. */
    public record Depth(List<Level> bids, List<Level> asks) {
        public Depth {
            bids = List.copyOf(bids);
            asks = List.copyOf(asks);
        }
    }

    private static void requirePrice(BigDecimal price) {
        Objects.requireNonNull(price);
        if (price.signum() < 0) throw new IllegalArgumentException("Negative price");
    }

    private static void requireQuantity(long quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Negative quantity");
    }
}
