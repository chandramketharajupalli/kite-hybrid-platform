package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.marketdata.domain.StreamMode;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class KiteMarketDataDecoderTest {
    private final KiteMarketDataDecoder decoder = new KiteMarketDataDecoder();

    @Test void decodesLtpWithoutFabricatingQuoteOrTimestamp() {
        var tick = decoder.decode(frame(ltp(257, 12345))).getFirst();
        assertThat(tick.instrumentToken()).isEqualTo(257);
        assertThat(tick.mode()).isEqualTo(StreamMode.LTP);
        assertThat(tick.lastPrice()).isEqualTo(12345);
        assertThat(tick.quote()).isEmpty();
        assertThat(tick.depth()).isEmpty();
        assertThat(tick.exchangeTimestamp()).isEmpty();
    }

    @Test void decodesQuoteVolumeQuantityAndOhlc() {
        var tick = decoder.decode(frame(quote(44, 257))).getFirst();
        assertThat(tick.mode()).isEqualTo(StreamMode.QUOTE);
        assertThat(tick.quote().orElseThrow().lastQuantity()).hasValue(41);
        assertThat(tick.quote().orElseThrow().volume()).hasValue(9000);
        assertThat(tick.quote().orElseThrow().ohlc()).isEqualTo(new KiteWireTick.Ohlc(10000, 13000, 9000, 11000));
        assertThat(tick.exchangeTimestamp()).isEmpty();
        assertThat(tick.depth()).isEmpty();
    }

    @Test void decodesFullDepthAndExchangeTimestampWithoutUsingLastTradeTime() {
        var tick = decoder.decode(frame(full(257))).getFirst();
        assertThat(tick.mode()).isEqualTo(StreamMode.FULL);
        assertThat(tick.exchangeTimestamp()).hasValue(1_790_000_000L);
        var depth = tick.depth().orElseThrow();
        assertThat(depth.bids()).hasSize(5);
        assertThat(depth.asks()).hasSize(5);
        assertThat(depth.bids().getFirst()).isEqualTo(new KiteWireTick.Level(12000, 100, 1));
        assertThat(depth.asks().getFirst()).isEqualTo(new KiteWireTick.Level(12005, 105, 6));
        assertThat(depth.asks().getLast()).isEqualTo(new KiteWireTick.Level(12009, 109, 10));
        assertThatThrownBy(() -> depth.bids().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest @ValueSource(ints = {28, 32})
    void decodesIndexPacketWithoutInventingVolumeOrMarketDepth(int length) {
        var tick = decoder.decode(frame(index(length))).getFirst();
        assertThat(tick.mode()).isEqualTo(length == 28 ? StreamMode.QUOTE : StreamMode.FULL);
        assertThat(tick.quote().orElseThrow().ohlc()).isEqualTo(new KiteWireTick.Ohlc(2100000, 2300000, 2000000, 2200000));
        assertThat(tick.quote().orElseThrow().lastQuantity()).isEmpty();
        assertThat(tick.quote().orElseThrow().volume()).isEmpty();
        assertThat(tick.depth()).isEmpty();
        if (length == 32) assertThat(tick.exchangeTimestamp()).hasValue(1_790_000_000L);
        else assertThat(tick.exchangeTimestamp()).isEmpty();
    }

    @Test void multiplePacketsPreserveWireOrderAndReturnImmutableBatch() {
        var ticks = decoder.decode(frame(ltp(257, 101), quote(44, 513), full(769), index(32)));
        assertThat(ticks).extracting(KiteWireTick::instrumentToken).containsExactly(257L, 513L, 769L, 265L);
        assertThatThrownBy(() -> ticks.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void singleByteHeartbeatIsNotATick() {
        assertThat(decoder.decode(new byte[]{0})).isEmpty();
        assertThat(decoder.decode(new byte[]{1})).isEmpty();
    }

    @Test void handlesUnsigned32BitAndUnsignedDepthOrderFields() {
        byte[] packet = full(0xffff_ff01L);
        ByteBuffer.wrap(packet).putInt(4, -1).putInt(8, -1).putInt(16, -1)
                .putInt(60, -1).putInt(64, -1).putInt(68, -1).putShort(72, (short) -1);
        var tick = decoder.decode(frame(packet)).getFirst();
        assertThat(tick.instrumentToken()).isEqualTo(0xffff_ff01L);
        assertThat(tick.lastPrice()).isEqualTo(0xffff_ffffL);
        assertThat(tick.quote().orElseThrow().volume()).hasValue(0xffff_ffffL);
        assertThat(tick.quote().orElseThrow().lastQuantity()).hasValue(0xffff_ffffL);
        assertThat(tick.exchangeTimestamp()).hasValue(0xffff_ffffL);
        assertThat(tick.depth().orElseThrow().bids().getFirst())
                .isEqualTo(new KiteWireTick.Level(0xffff_ffffL, 0xffff_ffffL, 65535));
    }

    @Test void zeroExchangeTimestampsRemainAbsent() {
        byte[] tradable = full(257);
        ByteBuffer.wrap(tradable).putInt(60, 0);
        byte[] index = index(32);
        ByteBuffer.wrap(index).putInt(28, 0);
        assertThat(decoder.decode(frame(tradable, index))).allSatisfy(t -> assertThat(t.exchangeTimestamp()).isEmpty());
    }

    @Test void ignoresUnspecifiedDepthPadding() {
        byte[] packet = full(257);
        ByteBuffer.wrap(packet).putShort(74, (short) 0xfffe);
        assertThat(decoder.decode(frame(packet)).getFirst().depth().orElseThrow().bids().getFirst().orders()).isEqualTo(1);
    }

    @Test void rejectsMalformedCountsAndTrailingBytes() {
        invalid(new byte[0]);
        invalid(new byte[]{0, 0});
        invalid(new byte[]{-1, -1});
        byte[] tooMany = frame(ltp(257, 1));
        ByteBuffer.wrap(tooMany).putShort(0, (short) 2);
        invalid(tooMany);
        byte[] unclaimed = frame(ltp(257, 1), ltp(513, 2));
        ByteBuffer.wrap(unclaimed).putShort(0, (short) 1);
        invalid(unclaimed);
        invalid(Arrays.copyOf(frame(ltp(257, 1)), 13));
        assertThatThrownBy(() -> decoder.decode(null)).isInstanceOf(KiteMarketDataDecoder.DecodeException.class);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 7, 9, 27, 29, 31, 33, 43, 45, 183, 185, 65535})
    void rejectsUnknownPacketLengths(int length) {
        byte[] malformed = frame(full(257));
        ByteBuffer.wrap(malformed).putShort(2, (short) length);
        invalid(malformed);
    }

    @Test void rejectsTruncatedDepthAndDoesNotReturnAnEarlierValidTick() {
        byte[] complete = frame(ltp(257, 1), full(513));
        for (int missing = 1; missing <= 120; missing++) {
            invalid(Arrays.copyOf(complete, complete.length - missing));
        }
    }

    @Test void rejectsPacketShapeThatDoesNotMatchIndexSegment() {
        invalid(frame(quote(44, 265)));
        byte[] nonIndex = index(28);
        ByteBuffer.wrap(nonIndex).putInt(0, 257);
        invalid(frame(nonIndex));
        invalid(frame(ltp(0, 100)));
    }

    @Test void boundsPacketCountAndAllocation() {
        invalid(new byte[KiteMarketDataDecoder.MAX_FRAME_BYTES + 1]);
        byte[][] maximum = new byte[KiteMarketDataDecoder.MAX_PACKETS][];
        Arrays.fill(maximum, full(257));
        assertThat(decoder.decode(frame(maximum))).hasSize(KiteMarketDataDecoder.MAX_PACKETS);
        byte[][] excessive = new byte[KiteMarketDataDecoder.MAX_PACKETS + 1][];
        Arrays.fill(excessive, ltp(257, 100));
        invalid(frame(excessive));
    }

    @Test void arbitraryMalformedInputsNeverEscapeAsBufferOrIndexExceptions() {
        Random random = new Random(1937);
        for (int i = 0; i < 1000; i++) {
            byte[] frame = new byte[random.nextInt(512)];
            random.nextBytes(frame);
            try { decoder.decode(frame); }
            catch (RuntimeException error) { assertThat(error).isInstanceOf(KiteMarketDataDecoder.DecodeException.class); }
        }
    }

    private void invalid(byte[] frame) {
        assertThatThrownBy(() -> decoder.decode(frame)).isInstanceOf(KiteMarketDataDecoder.DecodeException.class);
    }

    static byte[] ltp(long token, long price) {
        return ByteBuffer.allocate(8).putInt((int) token).putInt((int) price).array();
    }

    static byte[] quote(int size, long token) {
        return ByteBuffer.allocate(size).putInt((int) token).putInt(12345).putInt(41).putInt(12222)
                .putInt(9000).putInt(1000).putInt(2000).putInt(10000).putInt(13000).putInt(9000).putInt(11000).array();
    }

    static byte[] full(long token) {
        ByteBuffer buffer = ByteBuffer.wrap(quote(184, token));
        buffer.position(44);
        buffer.putInt(1_789_999_990).putInt(999).putInt(1000).putInt(888).putInt(1_790_000_000);
        for (int i = 0; i < 10; i++) buffer.putInt(100 + i).putInt(12000 + i).putShort((short) (i + 1)).putShort((short) 0);
        return buffer.array();
    }

    static byte[] index(int length) {
        ByteBuffer packet = ByteBuffer.allocate(length).putInt(265).putInt(2250000).putInt(2300000)
                .putInt(2000000).putInt(2100000).putInt(2200000).putInt(50000);
        if (length == 32) packet.putInt(1_790_000_000);
        return packet.array();
    }

    static byte[] frame(byte[]... packets) {
        int size = 2;
        for (byte[] packet : packets) size += 2 + packet.length;
        ByteBuffer frame = ByteBuffer.allocate(size).putShort((short) packets.length);
        for (byte[] packet : packets) frame.putShort((short) packet.length).put(packet);
        return frame.array();
    }
}
