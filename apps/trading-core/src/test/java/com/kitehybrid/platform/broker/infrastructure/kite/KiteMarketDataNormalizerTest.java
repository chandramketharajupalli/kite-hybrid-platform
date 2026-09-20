package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.instrument.domain.BrokerInstrumentId;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.instrument.domain.InstrumentSnapshot;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.marketdata.application.MarketDataException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.kitehybrid.platform.broker.infrastructure.kite.KiteMarketDataDecoderTest.*;
import static org.assertj.core.api.Assertions.*;

class KiteMarketDataNormalizerTest {
    private static final Instant RECEIVED = Instant.parse("2026-09-20T08:00:00.123456Z");
    private final KiteMarketDataDecoder decoder = new KiteMarketDataDecoder();
    private final KiteMarketDataNormalizer normalizer = new KiteMarketDataNormalizer();

    @ParameterizedTest @CsvSource({"257, 123456789, 1234567.89", "259, 123456789, 12.3456789",
            "262, 123456789, 12345.6789", "268, 123456789, 12345.6789", "263, 123456789, 1234567.89"})
    void scalesPricesExactlyForCashCdsBcdNcoAndMcx(long token, long raw, String expected) {
        var instrument = instrument(token, "EXAMPLE");
        var wire = decoder.decode(frame(ltp(token, raw))).getFirst();
        var tick = normalizer.normalize(new KiteMarketDataNormalizer.Source(wire, snapshot(instrument), RECEIVED));
        assertThat(tick.instrumentId()).isEqualTo(instrument.id());
        assertThat(tick.lastPrice()).isEqualByComparingTo(expected);
        assertThat(tick.receivedAt()).isEqualTo(RECEIVED);
        assertThat(tick.exchangeTimestamp()).isEmpty();
        assertThat(tick.quote()).isEmpty();
    }

    @Test void normalizesFullQuoteAndBothDepthSidesAtSameExactScale() {
        var tick = normalizer.normalize(decoder.decode(frame(full(259))).getFirst(), snapshot(instrument(259, "CURRENCY")), RECEIVED);
        assertThat(tick.lastPrice()).isEqualByComparingTo("0.0012345");
        var quote = tick.quote().orElseThrow();
        assertThat(quote.lastQuantity()).hasValue(41);
        assertThat(quote.volume()).hasValue(9000);
        assertThat(quote.ohlc()).isEqualTo(new Tick.Ohlc(new BigDecimal("0.0010000"), new BigDecimal("0.0013000"),
                new BigDecimal("0.0009000"), new BigDecimal("0.0011000")));
        assertThat(tick.depth().orElseThrow().bids().getFirst().price()).isEqualByComparingTo("0.0012000");
        assertThat(tick.depth().orElseThrow().asks().getFirst().price()).isEqualByComparingTo("0.0012005");
        assertThat(tick.exchangeTimestamp()).contains(Instant.ofEpochSecond(1_790_000_000L));
    }

    @Test void timestampsUseUtcAndSupportUnsignedPost2038Values() {
        byte[] packet = full(257);
        ByteBuffer.wrap(packet).putInt(60, -1);
        var tick = normalizer.normalize(decoder.decode(frame(packet)).getFirst(), snapshot(instrument(257, "EXAMPLE")), RECEIVED);
        assertThat(tick.exchangeTimestamp()).contains(Instant.parse("2106-02-07T06:28:15Z"));
        assertThat(tick.receivedAt()).isEqualTo(RECEIVED);
    }

    @Test void indexFullModeDoesNotFabricateTradeQuantityOrDepth() {
        var tick = normalizer.normalize(decoder.decode(frame(index(32))).getFirst(), snapshot(instrument(265, "INDEX")), RECEIVED);
        assertThat(tick.lastPrice()).isEqualByComparingTo("22500");
        assertThat(tick.quote().orElseThrow().lastQuantity()).isEmpty();
        assertThat(tick.quote().orElseThrow().volume()).isEmpty();
        assertThat(tick.depth()).isEmpty();
    }

    @Test void unresolvedAndOtherBrokerTokensFailWithSafeControlledError() {
        var wire = decoder.decode(frame(ltp(257, 100))).getFirst();
        assertThatThrownBy(() -> normalizer.normalize(wire, InstrumentSnapshot.empty(), RECEIVED))
                .isInstanceOfSatisfying(MarketDataException.class, error ->
                        assertThat(error.reason()).isEqualTo(MarketDataException.Reason.UNRESOLVED_INSTRUMENT));
        var other = Instrument.create(new BrokerInstrumentId("OTHER", "257"), "ABC", "NSE", "CASH",
                InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);
        assertThatThrownBy(() -> normalizer.normalize(wire, snapshot(other), RECEIVED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void normalizationUsesProvidedImmutableRegistryGeneration() {
        var first = instrument(257, "ABC");
        var remapped = instrument(513, "ABC");
        var oldSnapshot = snapshot(first);
        var newSnapshot = snapshot(remapped);
        var tick = normalizer.normalize(decoder.decode(frame(ltp(257, 100))).getFirst(), oldSnapshot, RECEIVED);
        assertThat(tick.instrumentId()).isEqualTo(remapped.id());
        assertThatThrownBy(() -> normalizer.normalize(decoder.decode(frame(ltp(257, 100))).getFirst(), newSnapshot, RECEIVED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Instrument instrument(long token, String symbol) {
        boolean index = (token & 0xff) == 9;
        return Instrument.create(new BrokerInstrumentId(KiteBrokerIdentity.BROKER_ID, Long.toString(token)), symbol, "NSE", index ? "INDICES" : "CASH",
                index ? InstrumentType.INDEX : InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);
    }

    private static InstrumentSnapshot snapshot(Instrument instrument) {
        return InstrumentSnapshot.validated(List.of(instrument), 1, RECEIVED.minusSeconds(100));
    }
}
