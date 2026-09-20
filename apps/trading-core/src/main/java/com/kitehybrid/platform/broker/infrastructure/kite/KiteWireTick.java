package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.marketdata.domain.StreamMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Unsigned protocol values. Broker tokens and unscaled prices never leave infrastructure. */
public record KiteWireTick(long instrumentToken, StreamMode mode, long lastPrice,
                           OptionalLong exchangeTimestamp, Optional<Quote> quote, Optional<Depth> depth) {
    public KiteWireTick {
        unsigned(instrumentToken);
        if (instrumentToken == 0) throw new IllegalArgumentException("Missing instrument token");
        unsigned(lastPrice);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(exchangeTimestamp);
        Objects.requireNonNull(quote);
        Objects.requireNonNull(depth);
        exchangeTimestamp.ifPresent(KiteWireTick::unsigned);
    }

    public record Ohlc(long open, long high, long low, long close) {
        public Ohlc { unsigned(open); unsigned(high); unsigned(low); unsigned(close); }
    }

    public record Quote(OptionalLong lastQuantity, OptionalLong volume, Ohlc ohlc) {
        public Quote {
            Objects.requireNonNull(lastQuantity);
            Objects.requireNonNull(volume);
            Objects.requireNonNull(ohlc);
            lastQuantity.ifPresent(KiteWireTick::unsigned);
            volume.ifPresent(KiteWireTick::unsigned);
        }
    }

    public record Level(long price, long quantity, int orders) {
        public Level {
            unsigned(price);
            unsigned(quantity);
            if (orders < 0 || orders > 65535) throw new IllegalArgumentException("Invalid depth order count");
        }
    }

    public record Depth(List<Level> bids, List<Level> asks) {
        public Depth {
            bids = List.copyOf(bids);
            asks = List.copyOf(asks);
            if (bids.size() != 5 || asks.size() != 5)
                throw new IllegalArgumentException("Invalid Kite depth size");
        }
    }

    private static void unsigned(long value) {
        if (value < 0 || value > 0xffff_ffffL)
            throw new IllegalArgumentException("Invalid unsigned Kite field");
    }
}
