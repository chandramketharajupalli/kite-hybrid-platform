package com.kitehybrid.platform.marketdata;

import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TickTest {
    private static final InstrumentId ID = new InstrumentId(new UUID(0, 1));
    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
    private static final BigDecimal PRICE = new BigDecimal("83.1234567");

    @Test void minimalTickRetainsExactPriceAndDoesNotFabricateOptionalFields() {
        Tick tick = new Tick(ID, PRICE, NOW);
        assertSame(ID, tick.instrumentId());
        assertEquals(PRICE, tick.lastPrice());
        assertEquals(NOW, tick.receivedAt());
        assertEquals(NOW, tick.observedAt());
        assertTrue(tick.exchangeTimestamp().isEmpty());
        assertTrue(tick.quote().isEmpty());
        assertTrue(tick.depth().isEmpty());
    }

    @Test void indexQuoteCanHaveOhlcWithoutTradeVolumeOrQuantity() {
        Tick.Quote quote = new Tick.Quote(OptionalLong.empty(), OptionalLong.empty(), ohlc());
        Tick tick = new Tick(ID, PRICE, NOW, Optional.of(NOW.minusSeconds(1)), Optional.of(quote), Optional.empty());
        assertEquals(NOW.minusSeconds(1), tick.exchangeTimestamp().orElseThrow());
        assertTrue(tick.quote().orElseThrow().lastQuantity().isEmpty());
        assertTrue(tick.quote().orElseThrow().volume().isEmpty());
        assertEquals(PRICE, tick.quote().orElseThrow().ohlc().open());
    }

    @Test void depthDefensivelyCopiesBothSidesAndRejectsNullLevels() {
        Tick.Level level = new Tick.Level(PRICE, Long.MAX_VALUE, 65535);
        var bids = new ArrayList<>(List.of(level));
        var asks = new ArrayList<>(List.of(level));
        Tick.Depth depth = new Tick.Depth(bids, asks);
        bids.clear();
        asks.clear();
        assertEquals(List.of(level), depth.bids());
        assertEquals(List.of(level), depth.asks());
        assertThrows(UnsupportedOperationException.class, () -> depth.bids().clear());
        assertThrows(UnsupportedOperationException.class, () -> depth.asks().clear());
        assertThrows(NullPointerException.class, () -> new Tick.Depth(Arrays.asList((Tick.Level) null), List.of()));
    }

    @Test void negativePricesQuantitiesAndOrderCountsAreRejected() {
        BigDecimal negative = new BigDecimal("-0.0000001");
        assertThrows(IllegalArgumentException.class, () -> new Tick(ID, negative, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Tick.Ohlc(negative, PRICE, PRICE, PRICE));
        assertThrows(IllegalArgumentException.class, () -> new Tick.Ohlc(PRICE, negative, PRICE, PRICE));
        assertThrows(IllegalArgumentException.class, () -> new Tick.Ohlc(PRICE, PRICE, negative, PRICE));
        assertThrows(IllegalArgumentException.class, () -> new Tick.Ohlc(PRICE, PRICE, PRICE, negative));
        assertThrows(IllegalArgumentException.class, () -> new Tick.Quote(OptionalLong.of(-1), OptionalLong.empty(), ohlc()));
        assertThrows(IllegalArgumentException.class, () -> new Tick.Quote(OptionalLong.empty(), OptionalLong.of(-1), ohlc()));
        assertThrows(IllegalArgumentException.class, () -> new Tick.Level(negative, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tick.Level(PRICE, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tick.Level(PRICE, 0, -1));
    }

    @Test void zeroAndUnknownValuesAreDifferentAndZeroIsValid() {
        var zeros = new Tick.Ohlc(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        var quote = new Tick.Quote(OptionalLong.of(0), OptionalLong.of(0), zeros);
        var depth = new Tick.Depth(List.of(new Tick.Level(BigDecimal.ZERO, 0, 0)), List.of());
        Tick tick = new Tick(ID, BigDecimal.ZERO, NOW, Optional.empty(), Optional.of(quote), Optional.of(depth));
        assertEquals(0, tick.quote().orElseThrow().volume().orElseThrow());
        assertTrue(tick.depth().orElseThrow().asks().isEmpty());
    }

    @Test void nullMandatoryValuesAndNullOptionalContainersAreRejected() {
        assertThrows(NullPointerException.class, () -> new Tick(null, PRICE, NOW));
        assertThrows(NullPointerException.class, () -> new Tick(ID, null, NOW));
        assertThrows(NullPointerException.class, () -> new Tick(ID, PRICE, null));
        assertThrows(NullPointerException.class, () -> new Tick(ID, PRICE, NOW, null, Optional.empty(), Optional.empty()));
        assertThrows(NullPointerException.class, () -> new Tick(ID, PRICE, NOW, Optional.empty(), null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new Tick(ID, PRICE, NOW, Optional.empty(), Optional.empty(), null));
        assertThrows(NullPointerException.class, () -> new Tick.Quote(null, OptionalLong.empty(), ohlc()));
        assertThrows(NullPointerException.class, () -> new Tick.Quote(OptionalLong.empty(), null, ohlc()));
        assertThrows(NullPointerException.class, () -> new Tick.Quote(OptionalLong.empty(), OptionalLong.empty(), null));
    }

    private static Tick.Ohlc ohlc() {
        return new Tick.Ohlc(PRICE, PRICE, PRICE, PRICE);
    }
}
